package com.gptremote.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.math.max

data class TvReceivedFile(
    val name: String,
    val sizeBytes: Long,
    val absolutePath: String,
    val timestampMs: Long,
    val direction: String = "received",
)

data class TvTransferProgress(
    val fileName: String,
    val bytesDone: Long,
    val totalBytes: Long,
    val bytesPerSec: Long,
    val isUpload: Boolean,
    val isComplete: Boolean = false,
)

data class TvCompanionUiState(
    val isRunning: Boolean = false,
    val deviceName: String = Build.MODEL.ifBlank { "Android TV" },
    val localIps: List<String> = emptyList(),
    val pairCode: String = "",
    val qrPayload: String = "",
    val connectedPhone: String? = null,
    val statusText: String = "Starting TV Companion...",
    val transferProgress: TvTransferProgress? = null,
    val receivedFiles: List<TvReceivedFile> = emptyList(),
    val castFrame: Bitmap? = null,
    val castLastAtMs: Long = 0L,
)

object TvCompanionState {
    private val _uiState = MutableStateFlow(TvCompanionUiState())
    val uiState: StateFlow<TvCompanionUiState> = _uiState.asStateFlow()

    fun update(block: (TvCompanionUiState) -> TvCompanionUiState) {
        _uiState.update(block)
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format("%.1f MB", mb)
        return String.format("%.2f GB", mb / 1024.0)
    }
}

class TvCompanionService : Service() {

    companion object {
        const val DISCOVERY_PORT = 58766
        const val BEACON_PORT = 58769
        const val TCP_PORT = 58765
        const val HTTP_PORT = 58768
        private const val TRANSFER_BUF = 1024 * 1024
        private const val CHANNEL_ID = "gpt_remote_tv_companion"
        private const val NOTIF_ID = 4102
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var storageDir: File
    private lateinit var pairCode: String
    private val historyLock = Any()
    private val cancelledSessions = ConcurrentHashMap.newKeySet<String>()
    private val activeDownloadCancel = AtomicBoolean(false)
    private val uploadParts = ConcurrentHashMap<String, File>()

    @Volatile
    private var tcpServer: ServerSocket? = null

    @Volatile
    private var httpServer: ServerSocket? = null

    @Volatile
    private var udpSocket: DatagramSocket? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        storageDir = File(getExternalFilesDir(null) ?: filesDir, "GptRemoteTV").apply {
            if (!exists()) mkdirs()
        }
        pairCode = loadOrCreatePairCode()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("GPT Remote TV active on Wi-Fi"))
        refreshFileListAndIps()

        TvCompanionState.update {
            it.copy(
                isRunning = true,
                pairCode = pairCode,
                statusText = "Listening on Wi-Fi (HTTP $HTTP_PORT / TCP $TCP_PORT)",
            )
        }

        scope.launch { runNetworkRefreshLoop() }
        scope.launch { runUdpDiscoveryServer() }
        scope.launch { runUdpBeaconBroadcaster() }
        scope.launch { runTcpControlServer() }
        scope.launch { runHttpCompanionServer() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refreshFileListAndIps()
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { tcpServer?.close() }
        runCatching { httpServer?.close() }
        runCatching { udpSocket?.close() }
        TvCompanionState.update { it.copy(isRunning = false, statusText = "Stopped") }
        scope.cancel()
        super.onDestroy()
    }

    private fun loadOrCreatePairCode(): String {
        val prefs = getSharedPreferences("gpt_remote_tv", Context.MODE_PRIVATE)
        val existing = prefs.getString("pair_code", null)
        if (!existing.isNullOrBlank()) return existing
        val generated = String.format("%06d", SecureRandom().nextInt(1_000_000))
        prefs.edit().putString("pair_code", generated).apply()
        return generated
    }

    private suspend fun runNetworkRefreshLoop() {
        while (scope.isActive) {
            refreshFileListAndIps()
            delay(4000L)
        }
    }

    private fun refreshFileListAndIps() {
        val ips = detectLocalIpv4s()
        val primaryIp = ips.firstOrNull() ?: "0.0.0.0"
        val deviceName = Build.MODEL.ifBlank { "Android TV" }
        val encodedName = java.net.URLEncoder.encode(deviceName, "UTF-8")
        val qr = "gptremote://tv?host=$primaryIp&port=$TCP_PORT&http=$HTTP_PORT&token=$pairCode&name=$encodedName"
        val diskFiles = (storageDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".gptpart") } ?: emptyList())
            .sortedByDescending { it.lastModified() }
            .map { f ->
                TvReceivedFile(
                    name = f.name,
                    sizeBytes = f.length(),
                    absolutePath = f.absolutePath,
                    timestampMs = f.lastModified(),
                )
            }
        TvCompanionState.update { prev ->
            prev.copy(
                deviceName = deviceName,
                localIps = ips,
                qrPayload = qr,
                receivedFiles = diskFiles,
            )
        }
    }

    private fun buildDiscoveryJson(): String {
        val host = TvCompanionState.uiState.value.localIps.firstOrNull()
            ?: detectLocalIpv4s().firstOrNull()
            ?: ""
        return JSONObject().apply {
            put("service", "gpt_remote_tv")
            put("kind", "tv")
            put("name", TvCompanionState.uiState.value.deviceName)
            put("host", host)
            put("tcp_port", TCP_PORT)
            put("http_port", HTTP_PORT)
            put("token", pairCode)
        }.toString()
    }

    private suspend fun runUdpDiscoveryServer() {
        try {
            val sock = DatagramSocket(DISCOVERY_PORT).apply {
                reuseAddress = true
                broadcast = true
            }
            udpSocket = sock
            val buf = ByteArray(2048)
            while (scope.isActive) {
                val packet = DatagramPacket(buf, buf.size)
                sock.receive(packet)
                val msg = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                if (msg.contains("GPT_REMOTE_DISCOVER", ignoreCase = true)) {
                    val reply = buildDiscoveryJson().toByteArray(Charsets.UTF_8)
                    val resp = DatagramPacket(reply, reply.size, packet.address, packet.port)
                    runCatching { sock.send(resp) }
                }
            }
        } catch (_: Throwable) {
        }
    }

    private suspend fun runUdpBeaconBroadcaster() {
        var beaconSock: DatagramSocket? = null
        try {
            beaconSock = DatagramSocket().apply { broadcast = true }
            while (scope.isActive) {
                val ips = detectLocalIpv4s()
                if (ips.isNotEmpty()) {
                    val data = buildDiscoveryJson().toByteArray(Charsets.UTF_8)
                    val targets = mutableSetOf("255.255.255.255")
                    ips.forEach { ip ->
                        val parts = ip.split(".")
                        if (parts.size == 4) {
                            targets += "${parts[0]}.${parts[1]}.${parts[2]}.255"
                        }
                    }
                    targets.forEach { bcast ->
                        val addr = runCatching { InetAddress.getByName(bcast) }.getOrNull() ?: return@forEach
                        runCatching { beaconSock.send(DatagramPacket(data, data.size, addr, BEACON_PORT)) }
                        runCatching { beaconSock.send(DatagramPacket(data, data.size, addr, DISCOVERY_PORT)) }
                    }
                }
                delay(2500L)
            }
        } catch (_: Throwable) {
        } finally {
            runCatching { beaconSock?.close() }
        }
    }

    private suspend fun runTcpControlServer() {
        try {
            val server = ServerSocket(TCP_PORT).apply { reuseAddress = true }
            tcpServer = server
            while (scope.isActive) {
                val client = server.accept()
                scope.launch { handleTcpClient(client) }
            }
        } catch (_: Throwable) {
        }
    }

    private fun handleTcpClient(socket: Socket) {
        try {
            socket.use { sock ->
                sock.soTimeout = 0
                sock.keepAlive = true
                sock.tcpNoDelay = true
                val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
                val writer = OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8)
                var authenticated = false
                val remoteAddr = sock.inetAddress.hostAddress ?: "Phone"
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    val json = runCatching { JSONObject(line) }.getOrNull() ?: continue
                    val type = json.optString("type")
                    if (type == "auth") {
                        val tok = json.optString("token")
                        if (tok == pairCode || pairCode.isBlank()) {
                            authenticated = true
                            val phoneName = json.optString("client_name").ifBlank { remoteAddr }
                            TvCompanionState.update { it.copy(connectedPhone = phoneName) }
                            val ack = JSONObject().apply {
                                put("type", "auth_ok")
                                put("kind", "tv")
                                put("name", TvCompanionState.uiState.value.deviceName)
                                put("http_port", HTTP_PORT)
                            }.toString() + "\n"
                            writer.write(ack)
                            writer.flush()
                        } else {
                            writer.write("{\"type\":\"auth_fail\"}\n")
                            writer.flush()
                            break
                        }
                    } else if (!authenticated) {
                        continue
                    } else if (type == "ping") {
                        writer.write("{\"type\":\"pong\"}\n")
                        writer.flush()
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }

    private suspend fun runHttpCompanionServer() {
        try {
            val server = ServerSocket(HTTP_PORT).apply { reuseAddress = true }
            httpServer = server
            while (scope.isActive) {
                val client = server.accept()
                scope.launch { handleHttpClient(client) }
            }
        } catch (_: Throwable) {
        }
    }

    private fun handleHttpClient(socket: Socket) {
        try {
            socket.use { sock ->
                sock.soTimeout = 3_600_000
                sock.tcpNoDelay = true
                runCatching {
                    sock.receiveBufferSize = 1024 * 1024
                    sock.sendBufferSize = 1024 * 1024
                }
                val bis = BufferedInputStream(sock.getInputStream(), TRANSFER_BUF)
                val out = BufferedOutputStream(sock.getOutputStream(), TRANSFER_BUF)

                val headerBytes = readUntilHeaderEnd(bis) ?: return
                val headerText = String(headerBytes, Charsets.ISO_8859_1)
                val lines = headerText.split("\r\n")
                if (lines.isEmpty()) return
                val requestLine = lines[0].split(" ")
                if (requestLine.size < 2) return
                val method = requestLine[0].uppercase()
                val rawTarget = requestLine[1]

                val qIdx = rawTarget.indexOf('?')
                val path = if (qIdx >= 0) rawTarget.substring(0, qIdx) else rawTarget
                val query = if (qIdx >= 0) parseQuery(rawTarget.substring(qIdx + 1)) else emptyMap()

                val headers = HashMap<String, String>()
                for (i in 1 until lines.size) {
                    val colon = lines[i].indexOf(':')
                    if (colon > 0) {
                        headers[lines[i].substring(0, colon).trim().lowercase()] =
                            lines[i].substring(colon + 1).trim()
                    }
                }

                val authToken = headers["x-auth-token"] ?: query["token"].orEmpty()
                val needsAuth = path != "/status"
                if (needsAuth && authToken != pairCode) {
                    writeJsonResponse(out, 401, JSONObject().put("error", "unauthorized").toString())
                    return
                }

                when {
                    method == "GET" && path == "/status" -> {
                        val st = TvCompanionState.uiState.value
                        val json = JSONObject().apply {
                            put("service", "gpt_remote_tv")
                            put("kind", "tv")
                            put("name", st.deviceName)
                            put("host", st.localIps.firstOrNull().orEmpty())
                            put("files_count", st.receivedFiles.size)
                        }.toString()
                        writeJsonResponse(out, 200, json)
                    }

                    method == "GET" && path == "/files" -> {
                        val files = (storageDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".gptpart") } ?: emptyList())
                            .sortedByDescending { it.lastModified() }
                        val arr = JSONArray()
                        files.forEach { f ->
                            arr.put(
                                JSONObject().apply {
                                    put("name", f.name)
                                    put("size", f.length())
                                    put("mtime", f.lastModified())
                                }
                            )
                        }
                        writeJsonResponse(out, 200, JSONObject().put("files", arr).toString())
                    }

                    method == "GET" && path == "/upload-status" -> {
                        val sid = query["session"].orEmpty().trim()
                        val existing = if (sid.isNotEmpty()) uploadParts[sid]?.takeIf { it.exists() }?.length() ?: 0L else 0L
                        writeJsonResponse(out, 200, JSONObject().put("bytes", existing).toString())
                    }

                    method == "POST" && path == "/transfer-cancel" -> {
                        val sid = query["session"].orEmpty().trim()
                        if (sid.isNotEmpty()) {
                            cancelledSessions.add(sid)
                            uploadParts.remove(sid)?.let { runCatching { it.delete() } }
                        } else {
                            activeDownloadCancel.set(true)
                        }
                        TvCompanionState.update { it.copy(transferProgress = null, statusText = "Transfer cancelled") }
                        writeJsonResponse(out, 200, "{\"ok\":true}")
                    }

                    method == "POST" && path == "/upload" -> {
                        val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L
                        val sid = query["session"].orEmpty().trim()
                        if (sid.isNotEmpty()) cancelledSessions.remove(sid)
                        val offset = query["offset"]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                        val totalParam = query["total"]?.toLongOrNull()?.coerceAtLeast(0L) ?: contentLength
                        val totalExpected = max(totalParam, offset + contentLength)

                        val rawName = query["name"] ?: headers["x-file-name"] ?: headers["x-filename"] ?: "file_${System.currentTimeMillis()}.bin"
                        val safeName = sanitizeFilename(rawName)
                        val partFile = if (sid.isNotEmpty()) {
                            uploadParts.getOrPut(sid) {
                                File(storageDir, ".${sanitizeFilename(sid)}.gptpart")
                            }
                        } else {
                            uniqueFile(storageDir, safeName)
                        }

                        var done = 0L
                        val startedAt = System.currentTimeMillis()
                        var lastUiAt = startedAt
                        val buf = ByteArray(TRANSFER_BUF)
                        val raf = RandomAccessFile(partFile, "rw")
                        try {
                            if (offset > 0L && partFile.exists()) {
                                raf.seek(offset)
                            } else {
                                raf.setLength(0L)
                            }
                            var remaining = contentLength
                            while (remaining > 0L) {
                                if (sid.isNotEmpty() && cancelledSessions.contains(sid)) break
                                val toRead = minOf(buf.size.toLong(), remaining).toInt()
                                val read = bis.read(buf, 0, toRead)
                                if (read <= 0) break
                                raf.write(buf, 0, read)
                                done += read
                                remaining -= read
                                val overallDone = offset + done
                                val now = System.currentTimeMillis()
                                if (now - lastUiAt >= 160L || remaining == 0L) {
                                    lastUiAt = now
                                    val elapsedMs = max(1L, now - startedAt)
                                    val bps = (done * 1000L) / elapsedMs
                                    TvCompanionState.update {
                                        it.copy(
                                            connectedPhone = sock.inetAddress.hostAddress ?: it.connectedPhone,
                                            transferProgress = TvTransferProgress(
                                                fileName = safeName,
                                                bytesDone = overallDone,
                                                totalBytes = max(totalExpected, overallDone),
                                                bytesPerSec = bps,
                                                isUpload = false,
                                                isComplete = false,
                                            ),
                                        )
                                    }
                                }
                            }
                        } finally {
                            runCatching { raf.close() }
                        }

                        val overallDone = offset + done
                        if (sid.isNotEmpty() && cancelledSessions.remove(sid)) {
                            uploadParts.remove(sid)
                            runCatching { partFile.delete() }
                            TvCompanionState.update { it.copy(transferProgress = null, statusText = "Cancelled $safeName") }
                            writeJsonResponse(out, 200, JSONObject().put("ok", false).put("cancelled", true).toString())
                            return
                        }

                        val isComplete = totalExpected <= 0L || overallDone >= totalExpected
                        val finalFile = if (sid.isNotEmpty() && isComplete) {
                            uploadParts.remove(sid)
                            val target = uniqueFile(storageDir, safeName)
                            if (!partFile.renameTo(target)) partFile else target
                        } else if (sid.isNotEmpty()) {
                            partFile
                        } else {
                            partFile
                        }

                        refreshFileListAndIps()
                        val elapsedMs = max(1L, System.currentTimeMillis() - startedAt)
                        val avgBps = (done * 1000L) / elapsedMs
                        TvCompanionState.update {
                            it.copy(
                                transferProgress = if (isComplete) TvTransferProgress(
                                    fileName = finalFile.name,
                                    bytesDone = overallDone,
                                    totalBytes = max(totalExpected, overallDone),
                                    bytesPerSec = avgBps,
                                    isUpload = false,
                                    isComplete = true,
                                ) else null,
                                statusText = if (isComplete) {
                                    "Received ${finalFile.name} (${TvCompanionState.formatBytes(overallDone)})"
                                } else {
                                    "Paused $safeName (${TvCompanionState.formatBytes(overallDone)})"
                                },
                            )
                        }
                        val resp = JSONObject().apply {
                            put("ok", true)
                            put("name", finalFile.name)
                            put("size", overallDone)
                            put("paused", !isComplete)
                        }.toString()
                        writeJsonResponse(out, 200, resp)
                    }

                    method == "GET" && path == "/download" -> {
                        activeDownloadCancel.set(false)
                        val rawName = query["name"].orEmpty()
                        val safeName = sanitizeFilename(rawName)
                        val file = File(storageDir, safeName)
                        if (!file.exists() || !file.isFile) {
                            writeJsonResponse(out, 404, "{\"error\":\"not_found\"}")
                            return
                        }
                        val total = file.length()
                        val rangeHdr = headers["range"].orEmpty()
                        var startOffset = 0L
                        var endOffset = max(0L, total - 1L)
                        var isPartial = false
                        if (rangeHdr.startsWith("bytes=")) {
                            val spec = rangeHdr.removePrefix("bytes=").substringBefore(",").trim()
                            val pStart = spec.substringBefore("-").toLongOrNull()
                            val pEnd = spec.substringAfter("-", "").toLongOrNull()
                            if (pStart != null) {
                                startOffset = pStart.coerceIn(0L, total)
                                if (pEnd != null) endOffset = pEnd.coerceIn(startOffset, max(0L, total - 1L))
                                isPartial = true
                            }
                        }
                        val toSend = if (total > 0L) (endOffset - startOffset + 1L).coerceAtLeast(0L) else 0L
                        val statusLine = if (isPartial) "206 Partial Content" else "200 OK"
                        val rangeLine = if (isPartial && total > 0L) "Content-Range: bytes $startOffset-$endOffset/$total\r\n" else ""
                        val header = (
                            "HTTP/1.1 $statusLine\r\n" +
                                "Content-Type: application/octet-stream\r\n" +
                                "Accept-Ranges: bytes\r\n" +
                                rangeLine +
                                "Content-Length: $toSend\r\n" +
                                "Connection: close\r\n\r\n"
                            ).toByteArray(Charsets.ISO_8859_1)
                        out.write(header)
                        val buf = ByteArray(TRANSFER_BUF)
                        RandomAccessFile(file, "r").use { raf ->
                            if (startOffset > 0L) raf.seek(startOffset)
                            var remaining = toSend
                            while (remaining > 0L && !activeDownloadCancel.get()) {
                                val r = raf.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                                if (r <= 0) break
                                out.write(buf, 0, r)
                                remaining -= r
                            }
                        }
                        out.flush()
                    }

                    method == "POST" && path == "/phone-frame" -> {
                        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                        if (contentLength <= 0 || contentLength > 8 * 1024 * 1024) {
                            writeJsonResponse(out, 400, "{\"error\":\"bad_frame\"}")
                            return
                        }
                        val payload = ByteArray(contentLength)
                        var offset = 0
                        while (offset < contentLength) {
                            val r = bis.read(payload, offset, contentLength - offset)
                            if (r <= 0) break
                            offset += r
                        }
                        val bmp = BitmapFactory.decodeByteArray(payload, 0, offset)
                        if (bmp != null) {
                            val prevAt = TvCompanionState.uiState.value.castLastAtMs
                            val now = System.currentTimeMillis()
                            TvCompanionState.update {
                                it.copy(
                                    connectedPhone = sock.inetAddress.hostAddress ?: it.connectedPhone,
                                    castFrame = bmp,
                                    castLastAtMs = now,
                                )
                            }
                            if (now - prevAt > 6000L) {
                                runCatching {
                                    val launch = Intent(this, TvMainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    }
                                    startActivity(launch)
                                }
                            }
                        }
                        writeJsonResponse(out, 200, "{\"ok\":true}")
                    }

                    else -> writeJsonResponse(out, 404, "{\"error\":\"not_found\"}")
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun readUntilHeaderEnd(bis: BufferedInputStream): ByteArray? {
        val baos = ByteArrayOutputStream(1024)
        var prev1 = 0
        var prev2 = 0
        var prev3 = 0
        while (baos.size() < 16384) {
            val b = bis.read()
            if (b == -1) return null
            baos.write(b)
            if (prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && b == '\n'.code) {
                return baos.toByteArray()
            }
            prev3 = prev2
            prev2 = prev1
            prev1 = b
        }
        return null
    }

    private fun writeJsonResponse(out: java.io.OutputStream, status: Int, bodyJson: String) {
        val body = bodyJson.toByteArray(Charsets.UTF_8)
        val statusText = when (status) {
            200 -> "OK"
            401 -> "Unauthorized"
            404 -> "Not Found"
            else -> "Error"
        }
        val headers = (
            "HTTP/1.1 $status $statusText\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
            ).toByteArray(Charsets.ISO_8859_1)
        out.write(headers)
        out.write(body)
        out.flush()
    }

    private fun parseQuery(queryString: String): Map<String, String> {
        val map = HashMap<String, String>()
        queryString.split("&").forEach { pair ->
            if (pair.isBlank()) return@forEach
            val eq = pair.indexOf('=')
            val k = if (eq >= 0) pair.substring(0, eq) else pair
            val v = if (eq >= 0) pair.substring(eq + 1) else ""
            map[URLDecoder.decode(k, "UTF-8")] = URLDecoder.decode(v, "UTF-8")
        }
        return map
    }

    private fun sanitizeFilename(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        val cleaned = base.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim()
        return cleaned.ifBlank { "shared_file.bin" }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "${stem}_$i$ext")
            i++
        }
        return candidate
    }

    private fun detectLocalIpv4s(): List<String> {
        val result = mutableListOf<String>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                if (!nif.isUp || nif.isLoopback) continue
                val addrs = nif.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (!result.contains(ip)) result += ip
                    }
                }
            }
        }
        return result
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "GPT Remote TV Companion",
                        NotificationManager.IMPORTANCE_LOW,
                    )
                )
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, TvMainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.tv_icon)
            .setContentTitle("GPT Remote TV")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }
}
