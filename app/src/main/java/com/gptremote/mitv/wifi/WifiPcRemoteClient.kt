package com.gptremote.mitv.wifi

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.gptremote.mitv.model.ConnectionStatus
import com.gptremote.mitv.model.MouseButton
import com.gptremote.mitv.model.RemoteButton
import com.gptremote.mitv.model.TransferHistoryItem
import com.gptremote.mitv.model.TransferProgress
import kotlin.math.max
import com.gptremote.mitv.model.TransportMode
import com.gptremote.mitv.model.TvDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

data class SharedPcFile(
    val name: String,
    val sizeBytes: Long,
    val modifiedMs: Long
)

/**
 * Client for controlling a Windows / macOS / Linux PC over Wi-Fi, plus live PC screen mirroring
 * and two-way file sharing via GPT Remote PC companion.
 */
class WifiPcRemoteClient {

    private val networkExecutor = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + networkExecutor.asCoroutineDispatcher())

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _discoveredPcs = MutableStateFlow<List<TvDevice.WifiPc>>(emptyList())
    val discoveredPcs: StateFlow<List<TvDevice.WifiPc>> = _discoveredPcs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _pcScreenBitmap = MutableStateFlow<Bitmap?>(null)
    val pcScreenBitmap: StateFlow<Bitmap?> = _pcScreenBitmap.asStateFlow()

    private val _isMirroringPc = MutableStateFlow(false)
    val isMirroringPc: StateFlow<Boolean> = _isMirroringPc.asStateFlow()

    private val _sharedFiles = MutableStateFlow<List<SharedPcFile>>(emptyList())
    val sharedFiles: StateFlow<List<SharedPcFile>> = _sharedFiles.asStateFlow()

    private val _isTransferringFile = MutableStateFlow(false)
    val isTransferringFile: StateFlow<Boolean> = _isTransferringFile.asStateFlow()

    private val _transferProgress = MutableStateFlow<TransferProgress?>(null)
    val transferProgress: StateFlow<TransferProgress?> = _transferProgress.asStateFlow()

    private val _pcTransferHistory = MutableStateFlow<List<TransferHistoryItem>>(emptyList())
    val pcTransferHistory: StateFlow<List<TransferHistoryItem>> = _pcTransferHistory.asStateFlow()

    private val _discoveredTvCompanions = MutableStateFlow<List<TvDevice.WifiPc>>(emptyList())
    val discoveredTvCompanions: StateFlow<List<TvDevice.WifiPc>> = _discoveredTvCompanions.asStateFlow()

    private val _connectedTvCompanion = MutableStateFlow<TvDevice.WifiPc?>(null)
    val connectedTvCompanion: StateFlow<TvDevice.WifiPc?> = _connectedTvCompanion.asStateFlow()

    private val _tvSharedFiles = MutableStateFlow<List<SharedPcFile>>(emptyList())
    val tvSharedFiles: StateFlow<List<SharedPcFile>> = _tvSharedFiles.asStateFlow()

    private val _tvTransferProgress = MutableStateFlow<TransferProgress?>(null)
    val tvTransferProgress: StateFlow<TransferProgress?> = _tvTransferProgress.asStateFlow()

    private val _tvTransferHistory = MutableStateFlow<List<TransferHistoryItem>>(emptyList())
    val tvTransferHistory: StateFlow<List<TransferHistoryItem>> = _tvTransferHistory.asStateFlow()

    private fun appendPcHistory(item: TransferHistoryItem) {
        _pcTransferHistory.value = listOf(item) + _pcTransferHistory.value.take(99)
    }

    private fun appendTvHistory(item: TransferHistoryItem) {
        _tvTransferHistory.value = listOf(item) + _tvTransferHistory.value.take(99)
    }

    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var activeDevice: TvDevice.WifiPc? = null

    private val sendQueue = Channel<String>(Channel.UNLIMITED)
    private var senderJob: Job? = null
    private var mouseTickerJob: Job? = null
    private var scanJob: Job? = null
    private var connectJob: Job? = null
    private var readerJob: Job? = null
    private var mirrorJob: Job? = null
    private var mirrorSocket: Socket? = null

    private val pendingDx = AtomicInteger(0)
    private val pendingDy = AtomicInteger(0)
    private val pendingWheel = AtomicInteger(0)

    init {
        startWorkers()
    }

    val connectedHostIp: String?
        get() = activeDevice?.host

    private fun startWorkers() {
        senderJob = scope.launch {
            for (line in sendQueue) {
                val out = writer ?: continue
                runCatching {
                    out.write(line)
                    out.write("\n")
                    out.flush()
                }.onFailure {
                    handleConnectionLost(it.message)
                }
            }
        }

        mouseTickerJob = scope.launch {
            while (isActive) {
                val dx = pendingDx.getAndSet(0)
                val dy = pendingDy.getAndSet(0)
                val wheel = pendingWheel.getAndSet(0)
                if ((dx != 0 || dy != 0) && writer != null) {
                    val json = JSONObject()
                        .put("t", "move")
                        .put("dx", dx)
                        .put("dy", dy)
                        .toString()
                    sendQueue.trySend(json)
                }
                if (wheel != 0 && writer != null) {
                    val json = JSONObject()
                        .put("t", "scroll")
                        .put("dy", wheel)
                        .toString()
                    sendQueue.trySend(json)
                }
                delay(12)
            }
        }
    }

    fun startDiscovery() {
        scanJob?.cancel()
        scanJob = CoroutineScope(Dispatchers.IO).launch {
            _isScanning.value = true
            val found = ConcurrentHashMap<String, TvDevice.WifiPc>()
            val foundTvs = ConcurrentHashMap<String, TvDevice.WifiPc>()
            fun publishFound() {
                _discoveredPcs.value = found.values.sortedBy { it.name.lowercase() }
                _discoveredTvCompanions.value = foundTvs.values.sortedBy { it.name.lowercase() }
            }

            try {
                coroutineScope {
                    val interfaceInfo = collectLocalIpv4Info()

                    val udpJob = launch {
                        var udpSocket: DatagramSocket? = null
                        try {
                            udpSocket = DatagramSocket().apply {
                                broadcast = true
                                soTimeout = 500
                            }
                            val probe = DISCOVER_MAGIC.toByteArray(Charsets.UTF_8)
                            val targets = buildSet {
                                add(InetAddress.getByName("255.255.255.255"))
                                interfaceInfo.forEach { info -> info.broadcast?.let { add(it) } }
                            }
                            for (attempt in 0..1) {
                                for (target in targets) {
                                    runCatching {
                                        udpSocket.send(DatagramPacket(probe, probe.size, target, DISCOVERY_PORT))
                                    }
                                }
                                if (attempt == 0) delay(250)
                            }

                            val deadline = System.currentTimeMillis() + 2400L
                            val buf = ByteArray(1536)
                            while (isActive && System.currentTimeMillis() < deadline) {
                                try {
                                    val resp = DatagramPacket(buf, buf.size)
                                    udpSocket.receive(resp)
                                    val text = String(resp.data, 0, resp.length, Charsets.UTF_8)
                                    val json = JSONObject(text)
                                    val host = json.optString("host").takeIf { it.isNotBlank() }
                                        ?: resp.address.hostAddress ?: continue
                                    if (json.optString("kind") == "tv" || json.optString("service") == "gpt_remote_tv") {
                                        val port = json.optInt("tcp_port", DEFAULT_PORT)
                                        val name = json.optString("name", "Android TV ($host)")
                                        val token = json.optString("token")
                                        foundTvs["$host:$port"] = TvDevice.WifiPc("$host:$port", name, host, port, token)
                                        publishFound()
                                    } else if (json.optString("type") == "announce") {
                                        val port = json.optInt("port", DEFAULT_PORT)
                                        val name = json.optString("name", "Laptop ($host)")
                                        val os = json.optString("os", "Desktop")
                                        val id = "$host:$port"
                                        found[id] = TvDevice.WifiPc(id, name, host, port, os)
                                        publishFound()
                                    }
                                } catch (_: SocketTimeoutException) {
                                }
                            }
                        } catch (_: Exception) {
                        } finally {
                            runCatching { udpSocket?.close() }
                        }
                    }

                    val beaconJob = launch {
                        var beaconSock: DatagramSocket? = null
                        try {
                            beaconSock = DatagramSocket(TV_BEACON_PORT).apply {
                                reuseAddress = true
                                broadcast = true
                                soTimeout = 500
                            }
                            val deadline = System.currentTimeMillis() + 2400L
                            val buf = ByteArray(1536)
                            while (isActive && System.currentTimeMillis() < deadline) {
                                try {
                                    val resp = DatagramPacket(buf, buf.size)
                                    beaconSock.receive(resp)
                                    val text = String(resp.data, 0, resp.length, Charsets.UTF_8)
                                    val json = JSONObject(text)
                                    if (json.optString("kind") == "tv" || json.optString("service") == "gpt_remote_tv") {
                                        val host = json.optString("host").takeIf { it.isNotBlank() }
                                            ?: resp.address.hostAddress ?: continue
                                        val port = json.optInt("tcp_port", DEFAULT_PORT)
                                        val name = json.optString("name", "Android TV ($host)")
                                        val token = json.optString("token")
                                        foundTvs["$host:$port"] = TvDevice.WifiPc("$host:$port", name, host, port, token)
                                        publishFound()
                                    }
                                } catch (_: SocketTimeoutException) {
                                }
                            }
                        } catch (_: Exception) {
                        } finally {
                            runCatching { beaconSock?.close() }
                        }
                    }

                    val tcpSweepJob = launch {
                        val prefixes = interfaceInfo.mapNotNull { it.slash24Prefix }.distinct()
                        val semaphore = Semaphore(48)
                        val jobs = prefixes.flatMap { prefix ->
                            (1..254).map { hostOctet ->
                                val candidateIp = "$prefix.$hostOctet"
                                async {
                                    if (interfaceInfo.any { it.localIp == candidateIp }) return@async
                                    semaphore.withPermit {
                                        probeTcpPc(candidateIp, DEFAULT_PORT)?.let { pc ->
                                            found[pc.id] = pc
                                            publishFound()
                                        }
                                    }
                                }
                            }
                        }
                        jobs.awaitAll()
                    }

                    udpJob.join()
                    beaconJob.join()
                    tcpSweepJob.join()
                }
            } finally {
                _isScanning.value = false
            }
        }
    }

    private data class LocalNetInfo(
        val localIp: String,
        val broadcast: InetAddress?,
        val slash24Prefix: String?
    )

    private fun collectLocalIpv4Info(): List<LocalNetInfo> {
        val list = mutableListOf<LocalNetInfo>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.interfaceAddresses) {
                    val inet = addr.address as? Inet4Address ?: continue
                    val host = inet.hostAddress ?: continue
                    if (host.startsWith("127.")) continue
                    val parts = host.split('.')
                    val prefix = if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}" else null
                    val fallbackBroadcast = prefix?.let {
                        runCatching { InetAddress.getByName("$it.255") }.getOrNull()
                    }
                    list += LocalNetInfo(host, addr.broadcast ?: fallbackBroadcast, prefix)
                }
            }
        }
        return list
    }

    private fun probeTcpPc(host: String, port: Int): TvDevice.WifiPc? {
        var s: Socket? = null
        return try {
            s = Socket()
            s.connect(InetSocketAddress(host, port), 600)
            s.soTimeout = 450
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val line = runCatching { reader.readLine() }.getOrNull() ?: return null
            val json = runCatching { JSONObject(line) }.getOrNull() ?: return null
            if (json.optString("type") != "announce" || json.optString("kind") == "tv") return null
            val name = json.optString("name").takeIf { it.isNotBlank() } ?: "PC ($host)"
            val os = json.optString("os").takeIf { it.isNotBlank() } ?: "Desktop"
            TvDevice.WifiPc("$host:$port", name, host, port, os)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { s?.close() }
        }
    }

    fun stopDiscovery() {
        scanJob?.cancel()
        _isScanning.value = false
    }

    fun connect(device: TvDevice.WifiPc) {
        disconnect()
        activeDevice = device
        _connectionStatus.value = ConnectionStatus.Connecting(device.name, TransportMode.WIFI_PC)

        connectJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val tcp = Socket()
                tcp.tcpNoDelay = true
                tcp.keepAlive = true
                tcp.connect(InetSocketAddress(device.host, device.port), 5000)
                if (!isActive || activeDevice?.id != device.id) {
                    runCatching { tcp.close() }
                    return@launch
                }
                socket = tcp
                writer = BufferedWriter(OutputStreamWriter(tcp.getOutputStream(), Charsets.UTF_8))
                _connectionStatus.value = ConnectionStatus.Connected(device)
                refreshSharedFiles()

                val reader = BufferedReader(InputStreamReader(tcp.getInputStream(), Charsets.UTF_8))
                readerJob = launch {
                    runCatching {
                        while (isActive && !tcp.isClosed) {
                            val line = reader.readLine() ?: break
                            if (line.isBlank()) continue
                        }
                    }
                    if (socket === tcp) {
                        handleConnectionLost("PC companion closed connection")
                    }
                }
            } catch (e: Exception) {
                if (isActive && activeDevice?.id == device.id) {
                    activeDevice = null
                    _connectionStatus.value = ConnectionStatus.Error(
                        message = "Cannot reach PC at ${device.host}:${device.port}. Open GPT Remote PC & allow Windows Firewall (${e.message ?: "timeout"}).",
                        transport = TransportMode.WIFI_PC
                    )
                }
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        readerJob?.cancel()
        readerJob = null
        stopPcScreenMirror()
        runCatching { writer?.close() }
        runCatching { socket?.close() }
        writer = null
        socket = null
        activeDevice = null
        _sharedFiles.value = emptyList()
        _connectionStatus.value = ConnectionStatus.Disconnected
    }

    fun startPcScreenMirror(onError: (String) -> Unit = {}) {
        val host = activeDevice?.host
        if (host == null) {
            onError("Connect to your PC via Wi-Fi first.")
            return
        }
        stopPcScreenMirror()
        _isMirroringPc.value = true
        mirrorJob = CoroutineScope(Dispatchers.IO).launch {
            var s: Socket? = null
            try {
                s = Socket()
                s.connect(InetSocketAddress(host, SCREEN_PORT), 4000)
                s.soTimeout = 5000
                mirrorSocket = s
                val input = DataInputStream(s.getInputStream())
                while (isActive && !s.isClosed) {
                    val frameLen = input.readInt()
                    if (frameLen <= 0 || frameLen > 6 * 1024 * 1024) break
                    val bytes = ByteArray(frameLen)
                    input.readFully(bytes)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        _pcScreenBitmap.value = bmp
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    onError("Screen mirror stopped: ${e.message ?: "check PC app"}")
                }
            } finally {
                runCatching { s?.close() }
                mirrorSocket = null
                _isMirroringPc.value = false
            }
        }
    }

    fun stopPcScreenMirror() {
        mirrorJob?.cancel()
        mirrorJob = null
        runCatching { mirrorSocket?.close() }
        mirrorSocket = null
        _isMirroringPc.value = false
        _pcScreenBitmap.value = null
    }

    fun refreshSharedFiles(onError: (String) -> Unit = {}) {
        val host = activeDevice?.host ?: return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val conn = (URL("http://$host:$FILE_HTTP_PORT/files").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3500
                    readTimeout = 3500
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val json = JSONObject(body)
                val arr = json.getJSONArray("files")
                buildList {
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        add(
                            SharedPcFile(
                                name = item.getString("name"),
                                sizeBytes = item.optLong("size", 0L),
                                modifiedMs = item.optLong("modifiedMs", 0L)
                            )
                        )
                    }
                }
            }.onSuccess { list ->
                _sharedFiles.value = list
            }.onFailure { err ->
                onError("Could not load PC files: ${err.message ?: "check PC app"}")
            }
        }
    }

    fun uploadFile(context: Context, uri: Uri, onComplete: (String) -> Unit) =
        uploadFiles(context, listOf(uri), onComplete)

    fun uploadFiles(context: Context, uris: List<Uri>, onComplete: (String) -> Unit) {
        val host = activeDevice?.host
        if (host == null) {
            onComplete("Connect to your PC via Wi-Fi first.")
            return
        }
        if (uris.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            _isTransferringFile.value = true
            var count = 0
            var lastSaved = ""
            try {
                val resolver = context.contentResolver
                for (uri in uris) {
                    val filename = resolveFileName(context, uri)
                    val size = resolveFileSize(context, uri)
                    val startedMs = System.currentTimeMillis()
                    var lastEmit = startedMs
                    _transferProgress.value = TransferProgress(
                        fileName = filename,
                        bytesDone = 0L,
                        totalBytes = size.coerceAtLeast(0L),
                        isUpload = true,
                        bytesPerSec = 0L
                    )
                    val encodedName = URLEncoder.encode(filename, "UTF-8").replace("+", "%20")

                    val conn = (URL("http://$host:$FILE_HTTP_PORT/upload").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 5000
                        readTimeout = 60_000
                        doOutput = true
                        setRequestProperty("X-Filename", encodedName)
                        if (size > 0) {
                            setFixedLengthStreamingMode(size)
                        } else {
                            setChunkedStreamingMode(65536)
                        }
                    }

                    var sent = 0L
                    val buf = ByteArray(65536)
                    resolver.openInputStream(uri)?.use { input ->
                        conn.outputStream.use { out ->
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) break
                                out.write(buf, 0, n)
                                sent += n
                                val now = System.currentTimeMillis()
                                if (now - lastEmit >= 160L || (size > 0 && sent >= size)) {
                                    lastEmit = now
                                    val spd = (sent * 1000L) / max(1L, now - startedMs)
                                    _transferProgress.value = TransferProgress(
                                        fileName = filename,
                                        bytesDone = sent,
                                        totalBytes = maxOf(size, sent),
                                        isUpload = true,
                                        bytesPerSec = spd
                                    )
                                }
                            }
                            out.flush()
                        }
                    } ?: throw IllegalStateException("Cannot read selected file $filename")

                    val code = conn.responseCode
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    if (code !in 200..299) {
                        throw IllegalStateException("Upload failed (HTTP $code)")
                    }
                    val savedAs = runCatching { JSONObject(resp).optString("savedAs", filename) }.getOrDefault(filename)
                    lastSaved = savedAs
                    val finalSpd = (sent * 1000L) / max(1L, System.currentTimeMillis() - startedMs)
                    _transferProgress.value = TransferProgress(
                        fileName = savedAs,
                        bytesDone = maxOf(size, sent),
                        totalBytes = maxOf(size, sent),
                        isUpload = true,
                        isComplete = true,
                        bytesPerSec = finalSpd
                    )
                    appendPcHistory(
                        TransferHistoryItem(
                            id = "pc_up_${System.nanoTime()}",
                            fileName = savedAs,
                            sizeBytes = maxOf(size, sent),
                            isSent = true,
                            bytesPerSec = finalSpd,
                            timestampMs = System.currentTimeMillis(),
                            localUriString = uri.toString()
                        )
                    )
                    count++
                }
                refreshSharedFiles()
                onComplete(
                    if (count == 1) "Sent '$lastSaved' to PC (Downloads/GptRemote)."
                    else "Sent $count file(s) to PC (Downloads/GptRemote)."
                )
                delay(1200)
            } catch (e: Exception) {
                onComplete("File send failed: ${e.message ?: "error"}")
            } finally {
                _transferProgress.value = null
                _isTransferringFile.value = false
            }
        }
    }

    fun downloadFile(context: Context, fileName: String, onComplete: (String) -> Unit) {
        val host = activeDevice?.host
        if (host == null) {
            onComplete("Connect to your PC via Wi-Fi first.")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            _isTransferringFile.value = true
            try {
                val encoded = URLEncoder.encode(fileName, "UTF-8")
                val conn = (URL("http://$host:$FILE_HTTP_PORT/download?name=$encoded").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 60_000
                }
                if (conn.responseCode !in 200..299) {
                    throw IllegalStateException("HTTP ${conn.responseCode}")
                }
                val total = conn.contentLengthLong.coerceAtLeast(0L)
                val startedMs = System.currentTimeMillis()
                var lastEmit = startedMs
                _transferProgress.value = TransferProgress(fileName, 0L, total, isUpload = false, bytesPerSec = 0L)

                val buf = ByteArray(65536)
                var done = 0L
                var savedUriStr: String? = null
                conn.inputStream.use { input ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/GptRemote")
                        }
                        val resolver = context.contentResolver
                        val outUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                            ?: throw IllegalStateException("Could not create Download entry")
                        savedUriStr = outUri.toString()
                        resolver.openOutputStream(outUri)?.use { out ->
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) break
                                out.write(buf, 0, n)
                                done += n
                                val now = System.currentTimeMillis()
                                if (now - lastEmit >= 160L || (total > 0 && done >= total)) {
                                    lastEmit = now
                                    val spd = (done * 1000L) / max(1L, now - startedMs)
                                    _transferProgress.value = TransferProgress(fileName, done, maxOf(total, done), isUpload = false, bytesPerSec = spd)
                                }
                            }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val dir = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "GptRemote"
                        ).apply { mkdirs() }
                        val outFile = File(dir, fileName)
                        savedUriStr = runCatching {
                            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile).toString()
                        }.getOrNull()
                        FileOutputStream(outFile).use { out ->
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) break
                                out.write(buf, 0, n)
                                done += n
                                val now = System.currentTimeMillis()
                                if (now - lastEmit >= 160L || (total > 0 && done >= total)) {
                                    lastEmit = now
                                    val spd = (done * 1000L) / max(1L, now - startedMs)
                                    _transferProgress.value = TransferProgress(fileName, done, maxOf(total, done), isUpload = false, bytesPerSec = spd)
                                }
                            }
                        }
                    }
                }
                conn.disconnect()
                val finalSize = maxOf(total, done)
                val finalSpd = (finalSize * 1000L) / max(1L, System.currentTimeMillis() - startedMs)
                _transferProgress.value = TransferProgress(
                    fileName = fileName,
                    bytesDone = finalSize,
                    totalBytes = finalSize,
                    isUpload = false,
                    isComplete = true,
                    bytesPerSec = finalSpd
                )
                appendPcHistory(
                    TransferHistoryItem(
                        id = "pc_dl_${System.nanoTime()}",
                        fileName = fileName,
                        sizeBytes = finalSize,
                        isSent = false,
                        bytesPerSec = finalSpd,
                        timestampMs = System.currentTimeMillis(),
                        localUriString = savedUriStr
                    )
                )
                onComplete("Saved '$fileName' to Downloads/GptRemote.")
                delay(1200)
            } catch (e: Exception) {
                onComplete("Download failed: ${e.message ?: "error"}")
            } finally {
                _transferProgress.value = null
                _isTransferringFile.value = false
            }
        }
    }

    fun getConnectedTvCompanionHostAndToken(): Pair<String, String>? {
        val tv = _connectedTvCompanion.value ?: return null
        return tv.host to tv.osName
    }

    fun connectTvCompanion(host: String, name: String, token: String, onStatus: (String) -> Unit) {
        val cleanHost = host.trim()
        if (cleanHost.isEmpty()) return
        val dev = TvDevice.WifiPc(
            id = "$cleanHost:$DEFAULT_PORT",
            name = name.ifBlank { "Android TV ($cleanHost)" },
            host = cleanHost,
            port = DEFAULT_PORT,
            osName = token.trim()
        )
        _connectedTvCompanion.value = dev
        onStatus("Connected to TV Companion ${dev.name}")
        refreshTvFiles(onStatus)
    }

    fun disconnectTvCompanion() {
        _connectedTvCompanion.value = null
        _tvSharedFiles.value = emptyList()
        _tvTransferProgress.value = null
    }

    fun refreshTvFiles(onError: (String) -> Unit = {}) {
        val tv = _connectedTvCompanion.value ?: return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val conn = (URL("http://${tv.host}:$FILE_HTTP_PORT/files").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3500
                    readTimeout = 3500
                    if (tv.osName.isNotBlank()) setRequestProperty("X-Auth-Token", tv.osName)
                }
                if (conn.responseCode == 401) throw IllegalStateException("Wrong TV Pair Code — scan TV QR or check code")
                if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val arr = JSONObject(body).getJSONArray("files")
                buildList {
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        add(
                            SharedPcFile(
                                name = item.getString("name"),
                                sizeBytes = item.optLong("size", 0L),
                                modifiedMs = item.optLong("mtime", 0L)
                            )
                        )
                    }
                }
            }.onSuccess { list ->
                _tvSharedFiles.value = list
            }.onFailure { err ->
                onError("TV Companion error: ${err.message ?: "open GPT Remote TV on TV"}")
            }
        }
    }

    fun uploadFilesToTv(context: Context, uris: List<Uri>, onComplete: (String) -> Unit) {
        val tv = _connectedTvCompanion.value
        if (tv == null) {
            onComplete("Connect to GPT Remote TV first (Scan TV or Scan QR).")
            return
        }
        if (uris.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            var count = 0
            var lastName = ""
            try {
                val resolver = context.contentResolver
                for (uri in uris) {
                    val filename = resolveFileName(context, uri)
                    lastName = filename
                    val size = resolveFileSize(context, uri)
                    val startedMs = System.currentTimeMillis()
                    var lastEmit = startedMs
                    _tvTransferProgress.value = TransferProgress(filename, 0L, size.coerceAtLeast(0L), isUpload = true)

                    val encodedName = URLEncoder.encode(filename, "UTF-8").replace("+", "%20")
                    val conn = (URL("http://${tv.host}:$FILE_HTTP_PORT/upload?name=$encodedName&size=$size").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 5000
                        readTimeout = 90_000
                        doOutput = true
                        setRequestProperty("X-Filename", encodedName)
                        if (tv.osName.isNotBlank()) setRequestProperty("X-Auth-Token", tv.osName)
                        if (size > 0) setFixedLengthStreamingMode(size) else setChunkedStreamingMode(65536)
                    }

                    var sent = 0L
                    val buf = ByteArray(65536)
                    resolver.openInputStream(uri)?.use { input ->
                        conn.outputStream.use { out ->
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) break
                                out.write(buf, 0, n)
                                sent += n
                                val now = System.currentTimeMillis()
                                if (now - lastEmit >= 160L || (size > 0 && sent >= size)) {
                                    lastEmit = now
                                    val spd = (sent * 1000L) / max(1L, now - startedMs)
                                    _tvTransferProgress.value = TransferProgress(
                                        fileName = filename,
                                        bytesDone = sent,
                                        totalBytes = maxOf(size, sent),
                                        isUpload = true,
                                        bytesPerSec = spd
                                    )
                                }
                            }
                            out.flush()
                        }
                    } ?: throw IllegalStateException("Cannot read $filename")

                    val code = conn.responseCode
                    conn.disconnect()
                    if (code !in 200..299) throw IllegalStateException("TV HTTP $code")

                    val finalSpd = (sent * 1000L) / max(1L, System.currentTimeMillis() - startedMs)
                    _tvTransferProgress.value = TransferProgress(
                        fileName = filename,
                        bytesDone = maxOf(size, sent),
                        totalBytes = maxOf(size, sent),
                        isUpload = true,
                        isComplete = true,
                        bytesPerSec = finalSpd
                    )
                    appendTvHistory(
                        TransferHistoryItem(
                            id = "tv_up_${System.nanoTime()}",
                            fileName = filename,
                            sizeBytes = maxOf(size, sent),
                            isSent = true,
                            bytesPerSec = finalSpd,
                            timestampMs = System.currentTimeMillis(),
                            localUriString = uri.toString()
                        )
                    )
                    count++
                }
                refreshTvFiles()
                onComplete(
                    if (count == 1) "Sent '$lastName' to ${tv.name}"
                    else "Sent $count file(s) to ${tv.name}"
                )
                delay(1200)
            } catch (e: Exception) {
                onComplete("TV transfer failed: ${e.message ?: "error"}")
            } finally {
                _tvTransferProgress.value = null
            }
        }
    }

    fun downloadFileFromTv(context: Context, fileName: String, onComplete: (String) -> Unit) {
        val tv = _connectedTvCompanion.value
        if (tv == null) {
            onComplete("Connect to GPT Remote TV first.")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val encoded = URLEncoder.encode(fileName, "UTF-8")
                val conn = (URL("http://${tv.host}:$FILE_HTTP_PORT/download?name=$encoded").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 60_000
                    if (tv.osName.isNotBlank()) setRequestProperty("X-Auth-Token", tv.osName)
                }
                if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong.coerceAtLeast(0L)
                val startedMs = System.currentTimeMillis()
                var lastEmit = startedMs
                _tvTransferProgress.value = TransferProgress(fileName, 0L, total, isUpload = false)

                val buf = ByteArray(65536)
                var done = 0L
                var savedUriStr: String? = null
                conn.inputStream.use { input ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/GptRemote")
                        }
                        val resolver = context.contentResolver
                        val outUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                            ?: throw IllegalStateException("Could not create Download entry")
                        savedUriStr = outUri.toString()
                        resolver.openOutputStream(outUri)?.use { out ->
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) break
                                out.write(buf, 0, n)
                                done += n
                                val now = System.currentTimeMillis()
                                if (now - lastEmit >= 160L || (total > 0 && done >= total)) {
                                    lastEmit = now
                                    val spd = (done * 1000L) / max(1L, now - startedMs)
                                    _tvTransferProgress.value = TransferProgress(fileName, done, maxOf(total, done), isUpload = false, bytesPerSec = spd)
                                }
                            }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val dir = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "GptRemote"
                        ).apply { mkdirs() }
                        val outFile = File(dir, fileName)
                        savedUriStr = runCatching {
                            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile).toString()
                        }.getOrNull()
                        FileOutputStream(outFile).use { out ->
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) break
                                out.write(buf, 0, n)
                                done += n
                                val now = System.currentTimeMillis()
                                if (now - lastEmit >= 160L || (total > 0 && done >= total)) {
                                    lastEmit = now
                                    val spd = (done * 1000L) / max(1L, now - startedMs)
                                    _tvTransferProgress.value = TransferProgress(fileName, done, maxOf(total, done), isUpload = false, bytesPerSec = spd)
                                }
                            }
                        }
                    }
                }
                conn.disconnect()
                val finalSize = maxOf(total, done)
                val finalSpd = (finalSize * 1000L) / max(1L, System.currentTimeMillis() - startedMs)
                _tvTransferProgress.value = TransferProgress(
                    fileName = fileName,
                    bytesDone = finalSize,
                    totalBytes = finalSize,
                    isUpload = false,
                    isComplete = true,
                    bytesPerSec = finalSpd
                )
                appendTvHistory(
                    TransferHistoryItem(
                        id = "tv_dl_${System.nanoTime()}",
                        fileName = fileName,
                        sizeBytes = finalSize,
                        isSent = false,
                        bytesPerSec = finalSpd,
                        timestampMs = System.currentTimeMillis(),
                        localUriString = savedUriStr
                    )
                )
                onComplete("Downloaded '$fileName' from TV to Downloads/GptRemote.")
                delay(1200)
            } catch (e: Exception) {
                onComplete("TV download failed: ${e.message ?: "error"}")
            } finally {
                _tvTransferProgress.value = null
            }
        }
    }

    private fun resolveFileName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    cursor.getString(idx)?.takeIf { it.isNotBlank() }?.let { return it }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "shared_file.bin"
    }

    private fun resolveFileSize(context: Context, uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && !cursor.isNull(idx)) return cursor.getLong(idx)
            }
        }
        return -1L
    }

    fun sendMouseMove(dx: Int, dy: Int, wheel: Int = 0) {
        if (writer == null) return
        if (dx != 0) pendingDx.addAndGet(dx)
        if (dy != 0) pendingDy.addAndGet(dy)
        if (wheel != 0) pendingWheel.addAndGet(wheel)
    }

    fun sendTapAbs(nx: Float, ny: Float) {
        if (writer == null) return
        sendQueue.trySend(
            JSONObject()
                .put("t", "tap_abs")
                .put("nx", nx.toDouble())
                .put("ny", ny.toDouble())
                .toString()
        )
    }

    fun sendMoveAbs(nx: Float, ny: Float) {
        if (writer == null) return
        sendQueue.trySend(
            JSONObject()
                .put("t", "move_abs")
                .put("nx", nx.toDouble())
                .put("ny", ny.toDouble())
                .toString()
        )
    }

    fun sendMouseClick(button: MouseButton) {
        if (writer == null) return
        val btnName = when (button) {
            MouseButton.LEFT -> "left"
            MouseButton.RIGHT -> "right"
            MouseButton.MIDDLE -> "middle"
        }
        sendQueue.trySend(JSONObject().put("t", "click").put("btn", btnName).toString())
    }

    fun sendButton(button: RemoteButton) {
        if (writer == null) return
        val code = buttonToPcKeyName(button) ?: return
        sendQueue.trySend(JSONObject().put("t", "key").put("code", code).toString())
    }

    fun sendText(text: String) {
        if (writer == null || text.isEmpty()) return
        sendQueue.trySend(JSONObject().put("t", "text").put("s", text).toString())
    }

    fun launchApp(appKey: String) {
        if (writer == null || appKey.isBlank()) return
        sendQueue.trySend(JSONObject().put("t", "launch").put("app", appKey).toString())
    }

    private fun handleConnectionLost(reason: String?) {
        if (activeDevice != null) {
            stopPcScreenMirror()
            runCatching { writer?.close() }
            runCatching { socket?.close() }
            writer = null
            socket = null
            activeDevice = null
            _connectionStatus.value = ConnectionStatus.Error(
                message = "PC connection closed: ${reason ?: "disconnected"}",
                transport = TransportMode.WIFI_PC
            )
        }
    }

    fun release() {
        stopDiscovery()
        disconnect()
        mouseTickerJob?.cancel()
        senderJob?.cancel()
        sendQueue.close()
        networkExecutor.shutdownNow()
    }

    companion object {
        const val DEFAULT_PORT = 58765
        const val DISCOVERY_PORT = 58766
        const val TV_BEACON_PORT = 58769
        const val SCREEN_PORT = 58767
        const val FILE_HTTP_PORT = 58768
        const val DISCOVER_MAGIC = "GPT_REMOTE_DISCOVER_V1"

        fun buttonToPcKeyName(button: RemoteButton): String? = when (button) {
            RemoteButton.DPAD_UP -> "up"
            RemoteButton.DPAD_DOWN -> "down"
            RemoteButton.DPAD_LEFT -> "left"
            RemoteButton.DPAD_RIGHT -> "right"
            RemoteButton.DPAD_CENTER, RemoteButton.ENTER -> "enter"
            RemoteButton.BACK, RemoteButton.ESCAPE -> "esc"
            RemoteButton.HOME, RemoteButton.PC_WIN_KEY -> "win"
            RemoteButton.MENU -> "menu"
            RemoteButton.VOLUME_UP -> "vol_up"
            RemoteButton.VOLUME_DOWN -> "vol_down"
            RemoteButton.MUTE -> "mute"
            RemoteButton.MEDIA_PLAY_PAUSE -> "play_pause"
            RemoteButton.MEDIA_NEXT -> "next"
            RemoteButton.MEDIA_PREVIOUS -> "prev"
            RemoteButton.MEDIA_STOP -> "stop"
            RemoteButton.BACKSPACE -> "backspace"
            RemoteButton.TAB -> "tab"
            RemoteButton.PC_ALT_TAB -> "alt_tab"
            RemoteButton.PC_COPY -> "copy"
            RemoteButton.PC_PASTE -> "paste"
            RemoteButton.PC_SHOW_DESKTOP -> "desktop"
            RemoteButton.SETTINGS -> "settings"
            RemoteButton.SEARCH -> "search"
            RemoteButton.POWER -> "lock"
            RemoteButton.CHANNEL_UP -> "page_up"
            RemoteButton.CHANNEL_DOWN -> "page_down"
            RemoteButton.DIGIT_0 -> "0"
            RemoteButton.DIGIT_1 -> "1"
            RemoteButton.DIGIT_2 -> "2"
            RemoteButton.DIGIT_3 -> "3"
            RemoteButton.DIGIT_4 -> "4"
            RemoteButton.DIGIT_5 -> "5"
            RemoteButton.DIGIT_6 -> "6"
            RemoteButton.DIGIT_7 -> "7"
            RemoteButton.DIGIT_8 -> "8"
            RemoteButton.DIGIT_9 -> "9"
            RemoteButton.TV_INPUT,
            RemoteButton.GUIDE,
            RemoteButton.INFO,
            RemoteButton.BASS_UP,
            RemoteButton.BASS_DOWN,
            RemoteButton.EQ_MODE,
            RemoteButton.KEYSTONE -> null
        }
    }
}
