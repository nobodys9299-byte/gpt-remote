package com.gptremote.mitv.wifi

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.gptremote.mitv.model.TransferProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.CopyOnWriteArrayList

private data class LocalSharedUri(
    val id: Int,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val addedMs: Long
)

/**
 * Enables direct Phone-to-Phone file sharing over local Wi-Fi or Hotspot with QR pairing:
 * - Host mode runs an embedded HTTP server on port 58770 so another phone can scan the QR
 *   to download shared files or upload files straight to Downloads/GptRemote.
 * - Client mode connects to another phone's scanned QR URL to browse, upload, and download
 *   files with real-time TransferProgress updates.
 */
class PhoneShareServer(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val hostedItems = CopyOnWriteArrayList<LocalSharedUri>()
    private var nextId = 1
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    private val _hostQrUrl = MutableStateFlow<String?>(null)
    val hostQrUrl: StateFlow<String?> = _hostQrUrl.asStateFlow()

    private val _hostedFiles = MutableStateFlow<List<SharedPcFile>>(emptyList())
    val hostedFiles: StateFlow<List<SharedPcFile>> = _hostedFiles.asStateFlow()

    private val _peerName = MutableStateFlow<String?>(null)
    val peerName: StateFlow<String?> = _peerName.asStateFlow()

    private val _peerFiles = MutableStateFlow<List<SharedPcFile>>(emptyList())
    val peerFiles: StateFlow<List<SharedPcFile>> = _peerFiles.asStateFlow()

    private val _transferProgress = MutableStateFlow<TransferProgress?>(null)
    val transferProgress: StateFlow<TransferProgress?> = _transferProgress.asStateFlow()

    private var peerHost: String? = null
    private var peerPort: Int = DEFAULT_PORT

    fun startHostIfNeeded() {
        val ip = findLocalIpv4()
        if (ip != null) {
            val encodedName = URLEncoder.encode(Build.MODEL, "UTF-8")
            _hostQrUrl.value = "gptremote://phone?host=$ip&port=$DEFAULT_PORT&name=$encodedName"
        } else {
            _hostQrUrl.value = null
        }

        if (serverSocket != null && serverSocket?.isClosed == false) return

        acceptJob = scope.launch {
            runCatching {
                val ss = ServerSocket(DEFAULT_PORT).apply { reuseAddress = true }
                serverSocket = ss
                while (isActive && !ss.isClosed) {
                    val client = ss.accept()
                    launch { handleHttpClient(client) }
                }
            }
        }
    }

    fun addFileToShare(uri: Uri) {
        val name = resolveFileName(uri)
        val size = resolveFileSize(uri).coerceAtLeast(0L)
        val item = LocalSharedUri(nextId++, uri, name, size, System.currentTimeMillis())
        hostedItems.add(0, item)
        _hostedFiles.value = hostedItems.map {
            SharedPcFile(it.name, it.sizeBytes, it.addedMs)
        }
        startHostIfNeeded()
    }

    fun connectPeer(host: String, port: Int, name: String, onStatus: (String) -> Unit) {
        peerHost = host
        peerPort = port
        _peerName.value = name.ifBlank { host }
        refreshPeerFiles(onStatus)
    }

    fun refreshPeerFiles(onError: (String) -> Unit = {}) {
        val host = peerHost ?: return
        scope.launch {
            runCatching {
                val conn = (URL("http://$host:$peerPort/files").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3500
                    readTimeout = 3500
                }
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                conn.disconnect()
                val arr = json.getJSONArray("files")
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        add(
                            SharedPcFile(
                                name = obj.getString("name"),
                                sizeBytes = obj.optLong("size", 0L),
                                modifiedMs = obj.optLong("modifiedMs", 0L)
                            )
                        )
                    }
                }
            }.onSuccess { list ->
                _peerFiles.value = list
            }.onFailure { err ->
                onError("Could not reach other phone at $host:$peerPort (${err.message ?: "check Wi-Fi/Hotspot"})")
            }
        }
    }

    fun sendFileToPeer(uri: Uri, onComplete: (String) -> Unit) {
        val host = peerHost
        if (host == null) {
            onComplete("Scan the receiver phone's QR code first.")
            return
        }
        scope.launch {
            try {
                val name = resolveFileName(uri)
                val total = resolveFileSize(uri).coerceAtLeast(1L)
                _transferProgress.value = TransferProgress(name, 0L, total, isUpload = true)

                val encodedName = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
                val conn = (URL("http://$host:$peerPort/upload").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5000
                    readTimeout = 60_000
                    doOutput = true
                    setRequestProperty("X-Filename", encodedName)
                    if (total > 0) setFixedLengthStreamingMode(total) else setChunkedStreamingMode(32768)
                }

                var sent = 0L
                val buf = ByteArray(32768)
                appContext.contentResolver.openInputStream(uri)?.use { inp ->
                    conn.outputStream.use { out ->
                        while (true) {
                            val n = inp.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            sent += n
                            _transferProgress.value = TransferProgress(name, sent, maxOf(total, sent), isUpload = true)
                        }
                        out.flush()
                    }
                } ?: throw IllegalStateException("Cannot read selected file")

                val code = conn.responseCode
                conn.disconnect()
                if (code !in 200..299) throw IllegalStateException("HTTP $code")
                _transferProgress.value = TransferProgress(name, maxOf(total, sent), maxOf(total, sent), isUpload = true, isComplete = true)
                delay(1400)
                _transferProgress.value = null
                refreshPeerFiles()
                onComplete("Sent '$name' to ${_peerName.value}")
            } catch (e: Exception) {
                _transferProgress.value = null
                onComplete("Phone send failed: ${e.message ?: "error"}")
            }
        }
    }

    fun downloadFileFromPeer(fileName: String, onComplete: (String) -> Unit) {
        val host = peerHost
        if (host == null) {
            onComplete("Scan sender phone's QR code first.")
            return
        }
        scope.launch {
            try {
                val encoded = URLEncoder.encode(fileName, "UTF-8")
                val conn = (URL("http://$host:$peerPort/download?name=$encoded").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 60_000
                }
                if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong.coerceAtLeast(0L)
                _transferProgress.value = TransferProgress(fileName, 0L, total, isUpload = false)

                saveStreamToDownloads(fileName, conn.inputStream) { done ->
                    _transferProgress.value = TransferProgress(fileName, done, maxOf(total, done), isUpload = false)
                }
                conn.disconnect()
                _transferProgress.value = TransferProgress(fileName, total, total, isUpload = false, isComplete = true)
                delay(1400)
                _transferProgress.value = null
                onComplete("Saved '$fileName' to Downloads/GptRemote")
            } catch (e: Exception) {
                _transferProgress.value = null
                onComplete("Download failed: ${e.message ?: "error"}")
            }
        }
    }

    private fun handleHttpClient(socket: Socket) {
        try {
            val inp = BufferedInputStream(socket.getInputStream())
            val out = BufferedOutputStream(socket.getOutputStream())
            val headers = readHttpHeaders(inp) ?: return
            val requestLine = headers.firstOrNull() ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val fullPath = parts[1]
            val path = fullPath.substringBefore('?')
            val query = fullPath.substringAfter('?', "")

            val headerMap = HashMap<String, String>()
            for (i in 1 until headers.size) {
                val idx = headers[i].indexOf(':')
                if (idx > 0) {
                    headerMap[headers[i].substring(0, idx).trim().lowercase()] =
                        headers[i].substring(idx + 1).trim()
                }
            }

            when {
                method == "GET" && path == "/files" -> {
                    val arr = JSONArray()
                    hostedItems.forEach { item ->
                        arr.put(
                            JSONObject()
                                .put("name", item.name)
                                .put("size", item.sizeBytes)
                                .put("modifiedMs", item.addedMs)
                        )
                    }
                    val body = JSONObject().put("files", arr).toString().toByteArray(Charsets.UTF_8)
                    val head = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                    out.write(head.toByteArray(Charsets.UTF_8))
                    out.write(body)
                    out.flush()
                }

                method == "GET" && path == "/download" -> {
                    val reqName = query.split('&')
                        .firstOrNull { it.startsWith("name=") }
                        ?.substringAfter("name=")
                        ?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
                    val item = hostedItems.firstOrNull { it.name == reqName }
                    if (item == null) {
                        out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".toByteArray())
                        out.flush()
                        return
                    }
                    val lenHeader = if (item.sizeBytes > 0) "Content-Length: ${item.sizeBytes}\r\n" else ""
                    val head = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\n${lenHeader}Connection: close\r\n\r\n"
                    out.write(head.toByteArray(Charsets.UTF_8))
                    var sent = 0L
                    val buf = ByteArray(32768)
                    _transferProgress.value = TransferProgress(item.name, 0L, item.sizeBytes, isUpload = true)
                    appContext.contentResolver.openInputStream(item.uri)?.use { fis ->
                        while (true) {
                            val n = fis.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            sent += n
                            _transferProgress.value = TransferProgress(item.name, sent, maxOf(item.sizeBytes, sent), isUpload = true)
                        }
                    }
                    out.flush()
                    _transferProgress.value = null
                }

                method == "POST" && path == "/upload" -> {
                    val rawName = headerMap["x-filename"]?.let { URLDecoder.decode(it, "UTF-8") } ?: "shared_file.bin"
                    val cleanName = rawName.substringAfterLast('/').substringAfterLast('\\').ifBlank { "shared_file.bin" }
                    val contentLength = headerMap["content-length"]?.toLongOrNull() ?: 0L
                    _transferProgress.value = TransferProgress(cleanName, 0L, contentLength, isUpload = false)

                    val limitedInput = if (contentLength > 0) {
                        BoundedInputStream(inp, contentLength)
                    } else {
                        inp
                    }
                    saveStreamToDownloads(cleanName, limitedInput) { done ->
                        _transferProgress.value = TransferProgress(cleanName, done, maxOf(contentLength, done), isUpload = false)
                    }
                    _transferProgress.value = TransferProgress(cleanName, contentLength, contentLength, isUpload = false, isComplete = true)
                    scope.launch {
                        delay(1200)
                        _transferProgress.value = null
                    }
                    val resp = """{"ok":true}""".toByteArray(Charsets.UTF_8)
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${resp.size}\r\n\r\n".toByteArray())
                    out.write(resp)
                    out.flush()
                }

                else -> {
                    out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".toByteArray())
                    out.flush()
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun saveStreamToDownloads(
        fileName: String,
        input: java.io.InputStream,
        onProgress: (Long) -> Unit
    ) {
        val buf = ByteArray(32768)
        var done = 0L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/GptRemote")
            }
            val resolver = appContext.contentResolver
            val outUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create Download entry")
            resolver.openOutputStream(outUri)?.use { out ->
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    done += n
                    onProgress(done)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "GptRemote"
            ).apply { mkdirs() }
            FileOutputStream(File(dir, fileName)).use { out ->
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    done += n
                    onProgress(done)
                }
            }
        }
    }

    private fun readHttpHeaders(inp: BufferedInputStream): List<String>? {
        val baos = ByteArrayOutputStream()
        var prev3 = 0
        var prev2 = 0
        var prev1 = 0
        while (baos.size() < 16384) {
            val b = inp.read()
            if (b == -1) return null
            baos.write(b)
            if (prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && b == '\n'.code) {
                break
            }
            prev3 = prev2
            prev2 = prev1
            prev1 = b
        }
        return baos.toString(Charsets.UTF_8.name()).split("\r\n").filter { it.isNotBlank() }
    }

    private fun findLocalIpv4(): String? {
        val candidates = mutableListOf<String>()
        runCatching {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
            for (iface in ifaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.interfaceAddresses) {
                    val inet = addr.address as? Inet4Address ?: continue
                    val ip = inet.hostAddress ?: continue
                    if (!ip.startsWith("127.")) candidates += ip
                }
            }
        }
        return candidates.firstOrNull {
            it.startsWith("192.168.") || it.startsWith("10.") || it.startsWith("172.")
        } ?: candidates.firstOrNull()
    }

    private fun resolveFileName(uri: Uri): String {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx)?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "shared_file.bin"
    }

    private fun resolveFileSize(uri: Uri): Long {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && !cursor.isNull(idx)) return cursor.getLong(idx)
            }
        }
        return -1L
    }

    fun release() {
        acceptJob?.cancel()
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private class BoundedInputStream(
        private val delegate: java.io.InputStream,
        private var remaining: Long
    ) : java.io.InputStream() {
        override fun read(): Int {
            if (remaining <= 0) return -1
            val b = delegate.read()
            if (b != -1) remaining--
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val toRead = minOf(len.toLong(), remaining).toInt()
            val n = delegate.read(b, off, toRead)
            if (n > 0) remaining -= n
            return n
        }
    }

    companion object {
        const val DEFAULT_PORT = 58770
    }
}
