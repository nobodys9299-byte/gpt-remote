package com.gptremote.mitv.wifi

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.gptremote.mitv.model.TransferHistoryItem
import com.gptremote.mitv.model.TransferProgress
import java.util.Locale
import kotlin.math.max
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

data class HostedMediaItem(
    val id: Int,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val viewUrl: String,
    val downloadUrl: String
)

private data class LocalSharedUri(
    val id: Int,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val addedMs: Long,
    val mimeType: String
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

    private val _browserShareUrl = MutableStateFlow<String?>(null)
    val browserShareUrl: StateFlow<String?> = _browserShareUrl.asStateFlow()

    private val _hostedFiles = MutableStateFlow<List<SharedPcFile>>(emptyList())
    val hostedFiles: StateFlow<List<SharedPcFile>> = _hostedFiles.asStateFlow()

    private val _hostedMediaItems = MutableStateFlow<List<HostedMediaItem>>(emptyList())
    val hostedMediaItems: StateFlow<List<HostedMediaItem>> = _hostedMediaItems.asStateFlow()

    private val _peerName = MutableStateFlow<String?>(null)
    val peerName: StateFlow<String?> = _peerName.asStateFlow()

    private val _peerFiles = MutableStateFlow<List<SharedPcFile>>(emptyList())
    val peerFiles: StateFlow<List<SharedPcFile>> = _peerFiles.asStateFlow()

    private val _transferProgress = MutableStateFlow<TransferProgress?>(null)
    val transferProgress: StateFlow<TransferProgress?> = _transferProgress.asStateFlow()

    private val _transferHistory = MutableStateFlow<List<TransferHistoryItem>>(emptyList())
    val transferHistory: StateFlow<List<TransferHistoryItem>> = _transferHistory.asStateFlow()

    private val _hotspotSsid = MutableStateFlow<String?>(null)
    val hotspotSsid: StateFlow<String?> = _hotspotSsid.asStateFlow()

    private val _hotspotPassword = MutableStateFlow<String?>(null)
    val hotspotPassword: StateFlow<String?> = _hotspotPassword.asStateFlow()

    private val _hotspotError = MutableStateFlow<String?>(null)
    val hotspotError: StateFlow<String?> = _hotspotError.asStateFlow()

    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null

    private var peerHost: String? = null
    private var peerPort: Int = DEFAULT_PORT

    private fun appendHistory(item: TransferHistoryItem) {
        _transferHistory.value = listOf(item) + _transferHistory.value.take(99)
    }

    fun startLocalHotspot() {
        _hotspotError.value = null
        val wifiMgr = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiMgr == null) {
            _hotspotError.value = "Wi-Fi service unavailable."
            return
        }
        val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    appContext,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
        if (!hasPerm) {
            _hotspotError.value = "Permission needed for quick Hotspot. Turn on Mobile Hotspot manually or grant permission."
            return
        }
        if (hotspotReservation != null) {
            startHostIfNeeded()
            return
        }
        try {
            wifiMgr.startLocalOnlyHotspot(
                object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                        hotspotReservation = reservation
                        val ssid: String
                        val pwd: String
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val config = reservation.softApConfiguration
                            ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                config?.wifiSsid?.toString()?.trim('"') ?: "GPT-Share"
                            } else {
                                @Suppress("DEPRECATION")
                                config?.ssid?.trim('"') ?: "GPT-Share"
                            }
                            pwd = config?.passphrase.orEmpty()
                        } else {
                            @Suppress("DEPRECATION")
                            val wc = reservation.wifiConfiguration
                            @Suppress("DEPRECATION")
                            ssid = wc?.SSID?.trim('"') ?: "GPT-Share"
                            @Suppress("DEPRECATION")
                            pwd = wc?.preSharedKey.orEmpty()
                        }
                        _hotspotSsid.value = ssid
                        _hotspotPassword.value = pwd
                        _hotspotError.value = null
                        scope.launch {
                            delay(700L)
                            startHostIfNeeded()
                        }
                    }

                    override fun onFailed(reason: Int) {
                        _hotspotError.value = "Hotspot busy (error $reason). Turn off existing Hotspot or open Settings -> Hotspot."
                    }

                    override fun onStopped() {
                        hotspotReservation = null
                        _hotspotSsid.value = null
                        _hotspotPassword.value = null
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (sec: SecurityException) {
            _hotspotError.value = "Location / Nearby Wi-Fi permission required for quick Hotspot."
        } catch (e: Exception) {
            _hotspotError.value = "Could not start Hotspot (${e.message ?: "use system Hotspot"})."
        }
    }

    fun stopLocalHotspot() {
        runCatching { hotspotReservation?.close() }
        hotspotReservation = null
        _hotspotSsid.value = null
        _hotspotPassword.value = null
        scope.launch {
            delay(400L)
            startHostIfNeeded()
        }
    }

    fun startHostIfNeeded() {
        val ip = findLocalIpv4()
        if (ip != null) {
            val encodedName = URLEncoder.encode(Build.MODEL, "UTF-8")
            _hostQrUrl.value = "gptremote://phone?host=$ip&port=$DEFAULT_PORT&name=$encodedName"
            _browserShareUrl.value = "http://$ip:$DEFAULT_PORT/"
        } else {
            _hostQrUrl.value = null
            _browserShareUrl.value = null
        }
        publishHostedItems(ip)

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

    fun addFileToShare(uri: Uri) = addFilesToShare(listOf(uri))

    fun addFilesToShare(uris: List<Uri>) {
        val now = System.currentTimeMillis()
        for (uri in uris) {
            val name = resolveFileName(uri)
            val size = resolveFileSize(uri).coerceAtLeast(0L)
            val mime = resolveMimeType(uri, name)
            val item = LocalSharedUri(nextId++, uri, name, size, now, mime)
            hostedItems.add(0, item)
        }
        publishHostedItems(findLocalIpv4())
        startHostIfNeeded()
    }

    private fun publishHostedItems(ip: String?) {
        _hostedFiles.value = hostedItems.map {
            SharedPcFile(it.name, it.sizeBytes, it.addedMs)
        }
        val base = ip?.let { "http://$it:$DEFAULT_PORT" } ?: ""
        _hostedMediaItems.value = hostedItems.map { item ->
            val enc = URLEncoder.encode(item.name, "UTF-8")
            HostedMediaItem(
                id = item.id,
                name = item.name,
                sizeBytes = item.sizeBytes,
                mimeType = item.mimeType,
                viewUrl = if (base.isNotEmpty()) "$base/view?id=${item.id}" else "/view?id=${item.id}",
                downloadUrl = if (base.isNotEmpty()) "$base/download?name=$enc" else "/download?name=$enc"
            )
        }
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

    fun sendFileToPeer(uri: Uri, onComplete: (String) -> Unit) =
        sendFilesToPeer(listOf(uri), onComplete)

    fun sendFilesToPeer(uris: List<Uri>, onComplete: (String) -> Unit) {
        val host = peerHost
        if (host == null) {
            onComplete("Scan the receiver phone's QR code first.")
            return
        }
        if (uris.isEmpty()) return
        scope.launch {
            var successCount = 0
            var lastName = ""
            try {
                for (uri in uris) {
                    val name = resolveFileName(uri)
                    lastName = name
                    val total = resolveFileSize(uri).coerceAtLeast(1L)
                    val startedMs = System.currentTimeMillis()
                    _transferProgress.value = TransferProgress(name, 0L, total, isUpload = true, bytesPerSec = 0L)

                    val encodedName = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
                    val conn = (URL("http://$host:$peerPort/upload").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 5000
                        readTimeout = 60_000
                        doOutput = true
                        setRequestProperty("X-Filename", encodedName)
                        if (total > 0) setFixedLengthStreamingMode(total) else setChunkedStreamingMode(65536)
                    }

                    var sent = 0L
                    var lastEmit = startedMs
                    val buf = ByteArray(65536)
                    appContext.contentResolver.openInputStream(uri)?.use { inp ->
                        conn.outputStream.use { out ->
                            while (true) {
                                val n = inp.read(buf)
                                if (n <= 0) break
                                out.write(buf, 0, n)
                                sent += n
                                val now = System.currentTimeMillis()
                                if (now - lastEmit >= 160L || sent >= total) {
                                    lastEmit = now
                                    val spd = (sent * 1000L) / max(1L, now - startedMs)
                                    _transferProgress.value = TransferProgress(
                                        fileName = name,
                                        bytesDone = sent,
                                        totalBytes = maxOf(total, sent),
                                        isUpload = true,
                                        bytesPerSec = spd
                                    )
                                }
                            }
                            out.flush()
                        }
                    } ?: throw IllegalStateException("Cannot read selected file $name")

                    val code = conn.responseCode
                    conn.disconnect()
                    if (code !in 200..299) throw IllegalStateException("HTTP $code")

                    val finalSpd = (sent * 1000L) / max(1L, System.currentTimeMillis() - startedMs)
                    _transferProgress.value = TransferProgress(
                        fileName = name,
                        bytesDone = maxOf(total, sent),
                        totalBytes = maxOf(total, sent),
                        isUpload = true,
                        isComplete = true,
                        bytesPerSec = finalSpd
                    )
                    appendHistory(
                        TransferHistoryItem(
                            id = "ph_sent_${System.nanoTime()}",
                            fileName = name,
                            sizeBytes = maxOf(total, sent),
                            isSent = true,
                            bytesPerSec = finalSpd,
                            timestampMs = System.currentTimeMillis(),
                            localUriString = uri.toString()
                        )
                    )
                    successCount++
                }
                delay(1200)
                _transferProgress.value = null
                refreshPeerFiles()
                val msg = if (successCount == 1) {
                    "Sent '$lastName' to ${_peerName.value}"
                } else {
                    "Sent $successCount file(s) to ${_peerName.value}"
                }
                onComplete(msg)
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
                val startedMs = System.currentTimeMillis()
                var lastEmit = startedMs
                var doneBytes = 0L
                _transferProgress.value = TransferProgress(fileName, 0L, total, isUpload = false, bytesPerSec = 0L)

                val savedUri = saveStreamToDownloads(fileName, conn.inputStream) { done ->
                    doneBytes = done
                    val now = System.currentTimeMillis()
                    if (now - lastEmit >= 160L || (total > 0 && done >= total)) {
                        lastEmit = now
                        val spd = (done * 1000L) / max(1L, now - startedMs)
                        _transferProgress.value = TransferProgress(
                            fileName = fileName,
                            bytesDone = done,
                            totalBytes = maxOf(total, done),
                            isUpload = false,
                            bytesPerSec = spd
                        )
                    }
                }
                conn.disconnect()
                val finalSize = maxOf(total, doneBytes)
                val finalSpd = (finalSize * 1000L) / max(1L, System.currentTimeMillis() - startedMs)
                _transferProgress.value = TransferProgress(
                    fileName = fileName,
                    bytesDone = finalSize,
                    totalBytes = finalSize,
                    isUpload = false,
                    isComplete = true,
                    bytesPerSec = finalSpd
                )
                appendHistory(
                    TransferHistoryItem(
                        id = "ph_dl_${System.nanoTime()}",
                        fileName = fileName,
                        sizeBytes = finalSize,
                        isSent = false,
                        bytesPerSec = finalSpd,
                        timestampMs = System.currentTimeMillis(),
                        localUriString = savedUri?.toString()
                    )
                )
                delay(1200)
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
                method == "GET" && (path == "/" || path == "/index.html") -> {
                    val html = buildTvBrowserHtml().toByteArray(Charsets.UTF_8)
                    val head = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${html.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
                    out.write(head.toByteArray(Charsets.UTF_8))
                    out.write(html)
                    out.flush()
                }

                (method == "GET" || method == "HEAD") && path == "/view" -> {
                    val reqId = query.split('&')
                        .firstOrNull { it.startsWith("id=") }
                        ?.substringAfter("id=")
                        ?.toIntOrNull()
                    val reqName = query.split('&')
                        .firstOrNull { it.startsWith("name=") }
                        ?.substringAfter("name=")
                        ?.let { URLDecoder.decode(it, "UTF-8") }
                    val item = (reqId?.let { id -> hostedItems.firstOrNull { it.id == id } })
                        ?: (reqName?.let { n -> hostedItems.firstOrNull { it.name == n } })
                    if (item == null) {
                        out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".toByteArray())
                        out.flush()
                        return
                    }
                    serveMediaWithRange(item, headerMap["range"], isHead = (method == "HEAD"), out = out)
                }

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
                    val startedMs = System.currentTimeMillis()
                    var lastEmit = startedMs
                    val buf = ByteArray(65536)
                    _transferProgress.value = TransferProgress(item.name, 0L, item.sizeBytes, isUpload = true, bytesPerSec = 0L)
                    appContext.contentResolver.openInputStream(item.uri)?.use { fis ->
                        while (true) {
                            val n = fis.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            sent += n
                            val now = System.currentTimeMillis()
                            if (now - lastEmit >= 160L || sent >= item.sizeBytes) {
                                lastEmit = now
                                val spd = (sent * 1000L) / max(1L, now - startedMs)
                                _transferProgress.value = TransferProgress(
                                    fileName = item.name,
                                    bytesDone = sent,
                                    totalBytes = maxOf(item.sizeBytes, sent),
                                    isUpload = true,
                                    bytesPerSec = spd
                                )
                            }
                        }
                    }
                    out.flush()
                    val finalSpd = (sent * 1000L) / max(1L, System.currentTimeMillis() - startedMs)
                    appendHistory(
                        TransferHistoryItem(
                            id = "ph_host_sent_${System.nanoTime()}",
                            fileName = item.name,
                            sizeBytes = sent,
                            isSent = true,
                            bytesPerSec = finalSpd,
                            timestampMs = System.currentTimeMillis(),
                            localUriString = item.uri.toString()
                        )
                    )
                    _transferProgress.value = null
                }

                method == "POST" && path == "/upload" -> {
                    val rawName = headerMap["x-filename"]?.let { URLDecoder.decode(it, "UTF-8") } ?: "shared_file.bin"
                    val cleanName = rawName.substringAfterLast('/').substringAfterLast('\\').ifBlank { "shared_file.bin" }
                    val contentLength = headerMap["content-length"]?.toLongOrNull() ?: 0L
                    val startedMs = System.currentTimeMillis()
                    var lastEmit = startedMs
                    var received = 0L
                    _transferProgress.value = TransferProgress(cleanName, 0L, contentLength, isUpload = false, bytesPerSec = 0L)

                    val limitedInput = if (contentLength > 0) {
                        BoundedInputStream(inp, contentLength)
                    } else {
                        inp
                    }
                    val savedUri = saveStreamToDownloads(cleanName, limitedInput) { done ->
                        received = done
                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= 160L || (contentLength > 0 && done >= contentLength)) {
                            lastEmit = now
                            val spd = (done * 1000L) / max(1L, now - startedMs)
                            _transferProgress.value = TransferProgress(
                                fileName = cleanName,
                                bytesDone = done,
                                totalBytes = maxOf(contentLength, done),
                                isUpload = false,
                                bytesPerSec = spd
                            )
                        }
                    }
                    val finalSize = maxOf(contentLength, received)
                    val finalSpd = (finalSize * 1000L) / max(1L, System.currentTimeMillis() - startedMs)
                    appendHistory(
                        TransferHistoryItem(
                            id = "ph_host_recv_${System.nanoTime()}",
                            fileName = cleanName,
                            sizeBytes = finalSize,
                            isSent = false,
                            bytesPerSec = finalSpd,
                            timestampMs = System.currentTimeMillis(),
                            localUriString = savedUri?.toString()
                        )
                    )
                    _transferProgress.value = TransferProgress(cleanName, finalSize, finalSize, isUpload = false, isComplete = true, bytesPerSec = finalSpd)
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

    private fun serveMediaWithRange(
        item: LocalSharedUri,
        rangeHeader: String?,
        isHead: Boolean,
        out: BufferedOutputStream
    ) {
        val total = item.sizeBytes
        val parsedRange = if (total > 0L) parseByteRange(rangeHeader, total) else null

        if (parsedRange != null) {
            val (start, end) = parsedRange
            val len = (end - start + 1L).coerceAtLeast(0L)
            val head = "HTTP/1.1 206 Partial Content\r\n" +
                "Content-Type: ${item.mimeType}\r\n" +
                "Accept-Ranges: bytes\r\n" +
                "Content-Range: bytes $start-$end/$total\r\n" +
                "Content-Length: $len\r\n" +
                "transferMode.dlna.org: Streaming\r\n" +
                "Connection: close\r\n\r\n"
            out.write(head.toByteArray(Charsets.UTF_8))
            if (!isHead && len > 0L) {
                appContext.contentResolver.openInputStream(item.uri)?.use { fis ->
                    skipFully(fis, start)
                    var remaining = len
                    val buf = ByteArray(65536)
                    while (remaining > 0L) {
                        val n = fis.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        remaining -= n
                    }
                }
            }
            out.flush()
        } else {
            val lenLine = if (total > 0L) "Content-Length: $total\r\n" else ""
            val head = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: ${item.mimeType}\r\n" +
                "Accept-Ranges: bytes\r\n" +
                lenLine +
                "transferMode.dlna.org: Streaming\r\n" +
                "Connection: close\r\n\r\n"
            out.write(head.toByteArray(Charsets.UTF_8))
            if (!isHead) {
                appContext.contentResolver.openInputStream(item.uri)?.use { fis ->
                    val buf = ByteArray(65536)
                    while (true) {
                        val n = fis.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                    }
                }
            }
            out.flush()
        }
    }

    private fun skipFully(inp: java.io.InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        val scratch = ByteArray(16384)
        while (remaining > 0L) {
            val skipped = inp.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                val r = inp.read(scratch, 0, minOf(scratch.size.toLong(), remaining).toInt())
                if (r <= 0) break
                remaining -= r
            }
        }
    }

    private fun resolveMimeType(uri: Uri, name: String): String {
        val fromResolver = runCatching { appContext.contentResolver.getType(uri) }.getOrNull()
        if (!fromResolver.isNullOrBlank() && fromResolver != "application/octet-stream") {
            return fromResolver
        }
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        return when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "aac", "m4a" -> "audio/mp4"
            "pdf" -> "application/pdf"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
    }

    private fun buildTvBrowserHtml(): String {
        val cards = if (hostedItems.isEmpty()) {
            """<div class="empty">No files shared from phone yet.<br/>Open GPT Remote &rarr; Smart TV &rarr; Share to TV and pick photos, videos, or files.</div>"""
        } else {
            hostedItems.joinToString("\n") { item ->
                val escName = htmlEscape(item.name)
                val sizeLabel = formatBytesReadable(item.sizeBytes)
                val viewUrl = "/view?id=${item.id}"
                val dlUrl = "/download?name=${URLEncoder.encode(item.name, "UTF-8")}"
                val mediaPreview = when {
                    item.mimeType.startsWith("image/") ->
                        """<a href="$viewUrl" target="_blank"><img class="thumb" src="$viewUrl" alt="$escName"/></a>"""
                    item.mimeType.startsWith("video/") ->
                        """<video class="player" controls preload="metadata" src="$viewUrl"></video>"""
                    item.mimeType.startsWith("audio/") ->
                        """<audio class="audio" controls preload="metadata" src="$viewUrl"></audio>"""
                    else -> ""
                }
                """
                <div class="card" tabindex="0">
                  <div class="title">$escName</div>
                  <div class="meta">$sizeLabel &bull; ${htmlEscape(item.mimeType)}</div>
                  $mediaPreview
                  <div class="actions">
                    <a class="btn primary" href="$viewUrl" target="_blank">Open / Play</a>
                    <a class="btn" href="$dlUrl" download="$escName">Download</a>
                  </div>
                </div>
                """.trimIndent()
            }
        }

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="utf-8"/>
          <meta name="viewport" content="width=device-width,initial-scale=1"/>
          <title>GPT Remote TV Share</title>
          <style>
            body{margin:0;padding:24px;background:#090d16;color:#f8fafc;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;}
            .hdr{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:12px;margin-bottom:20px;padding-bottom:14px;border-bottom:1px solid #1e293b;}
            h1{margin:0;font-size:24px;color:#22d3ee;}
            .sub{color:#94a3b8;font-size:14px;}
            .grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:16px;}
            .card{background:#111a2e;border:2px solid #1e293b;border-radius:16px;padding:16px;display:flex;flex-direction:column;gap:10px;}
            .card:focus,.btn:focus{outline:none;border-color:#22d3ee;box-shadow:0 0 0 3px rgba(34,211,238,.35);}
            .title{font-weight:700;font-size:17px;word-break:break-word;}
            .meta{color:#94a3b8;font-size:13px;}
            .thumb{width:100%;max-height:220px;object-fit:contain;border-radius:10px;background:#020617;}
            .player{width:100%;max-height:240px;border-radius:10px;background:#000;}
            .audio{width:100%;}
            .actions{display:flex;gap:10px;margin-top:4px;}
            .btn{display:inline-block;padding:10px 14px;border-radius:10px;background:#1e293b;color:#f8fafc;text-decoration:none;font-weight:600;font-size:14px;border:1px solid #334155;}
            .btn.primary{background:#ff6b00;color:#fff;border-color:#ff6b00;}
            .empty{background:#111a2e;border:1px solid #1e293b;border-radius:16px;padding:28px;color:#cbd5e1;font-size:16px;text-align:center;}
          </style>
        </head>
        <body>
          <div class="hdr">
            <div>
              <h1>GPT Remote &bull; TV Media &amp; File Share</h1>
              <div class="sub">From ${htmlEscape(Build.MODEL)} &bull; Refresh page if you add new files on phone</div>
            </div>
            <a class="btn primary" href="/" onclick="location.reload();return false;">Refresh List</a>
          </div>
          <div class="grid">$cards</div>
        </body>
        </html>
        """.trimIndent()
    }

    private fun htmlEscape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun formatBytesReadable(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        else -> "${maxOf(1L, bytes / 1024L)} KB"
    }

    private fun saveStreamToDownloads(
        fileName: String,
        input: java.io.InputStream,
        onProgress: (Long) -> Unit
    ): Uri? {
        val buf = ByteArray(65536)
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
            return outUri
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "GptRemote"
            ).apply { mkdirs() }
            val outFile = File(dir, fileName)
            FileOutputStream(outFile).use { out ->
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    done += n
                    onProgress(done)
                }
            }
            return runCatching {
                FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", outFile)
            }.getOrNull()
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
        runCatching { hotspotReservation?.close() }
        hotspotReservation = null
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

        fun parseByteRange(rangeHeader: String?, totalLength: Long): Pair<Long, Long>? {
            if (rangeHeader.isNullOrBlank() || totalLength <= 0L) return null
            val clean = rangeHeader.trim()
            if (!clean.startsWith("bytes=", ignoreCase = true)) return null
            val spec = clean.substring(6).substringBefore(',').trim()
            val dash = spec.indexOf('-')
            if (dash < 0) return null
            val startPart = spec.substring(0, dash).trim()
            val endPart = spec.substring(dash + 1).trim()
            val start: Long
            val end: Long
            if (startPart.isEmpty()) {
                val suffix = endPart.toLongOrNull() ?: return null
                if (suffix <= 0L) return null
                start = (totalLength - suffix).coerceAtLeast(0L)
                end = totalLength - 1L
            } else {
                start = startPart.toLongOrNull() ?: return null
                end = if (endPart.isEmpty()) {
                    totalLength - 1L
                } else {
                    (endPart.toLongOrNull() ?: return null).coerceAtMost(totalLength - 1L)
                }
            }
            if (start < 0L || start > end || start >= totalLength) return null
            return start to end
        }
    }
}
