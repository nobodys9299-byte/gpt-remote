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
import com.gptremote.mitv.model.TransferQueueItem
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
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

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

    private val _transferQueue = MutableStateFlow<List<TransferQueueItem>>(emptyList())
    val transferQueue: StateFlow<List<TransferQueueItem>> = _transferQueue.asStateFlow()

    private val _hotspotSsid = MutableStateFlow<String?>(null)
    val hotspotSsid: StateFlow<String?> = _hotspotSsid.asStateFlow()

    private val _hotspotPassword = MutableStateFlow<String?>(null)
    val hotspotPassword: StateFlow<String?> = _hotspotPassword.asStateFlow()

    private val _hotspotError = MutableStateFlow<String?>(null)
    val hotspotError: StateFlow<String?> = _hotspotError.asStateFlow()

    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null

    private var peerHost: String? = null
    private var peerPort: Int = DEFAULT_PORT

    private data class PausedUploadState(
        val uris: List<Uri>,
        val currentIndex: Int,
        val sessionId: String,
        val fileName: String,
        val totalBytes: Long,
        val bytesDone: Long
    )

    private data class PausedDownloadState(
        val fileName: String,
        val totalBytes: Long,
        val bytesDone: Long,
        val partFile: File
    )

    private val cancelRequested = AtomicBoolean(false)
    private val pauseRequested = AtomicBoolean(false)
    private val cancelledSessions = ConcurrentHashMap.newKeySet<String>()
    private val pausedSessions = ConcurrentHashMap.newKeySet<String>()
    private val sessionControlCommands = ConcurrentHashMap<String, String>()
    private val serverDownloadCancel = AtomicBoolean(false)
    private val serverUploadParts = ConcurrentHashMap<String, File>()
    private var activeTransferJob: Job? = null
    @Volatile
    private var activeConn: HttpURLConnection? = null
    private var pausedUploadState: PausedUploadState? = null
    private var pausedDownloadState: PausedDownloadState? = null
    private var activeSessionId: String? = null
    @Volatile
    private var activeIncomingSessionId: String? = null

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
        scope.launch {
            runCatching {
                val filesConn = (URL("http://$host:$port/files").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3500
                    readTimeout = 3500
                }
                if (filesConn.responseCode !in 200..299) {
                    filesConn.disconnect()
                    throw IllegalStateException("HTTP ${filesConn.responseCode}")
                }
                val json = JSONObject(filesConn.inputStream.bufferedReader().use { it.readText() })
                filesConn.disconnect()

                // Notify peer phone so BOTH phones show connected
                startHostIfNeeded()
                runCatching {
                    val helloConn = (URL("http://$host:$port/hello").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 2500
                        readTimeout = 2500
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                    }
                    val helloBody = JSONObject().put("name", Build.MODEL).put("port", DEFAULT_PORT).toString().toByteArray()
                    helloConn.outputStream.use { it.write(helloBody) }
                    helloConn.responseCode
                    helloConn.disconnect()
                }

                peerHost = host
                peerPort = port
                _peerName.value = name.ifBlank { host }
                val arr = json.getJSONArray("files")
                _peerFiles.value = buildList {
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
            }.onFailure { err ->
                peerHost = null
                _peerName.value = null
                _peerFiles.value = emptyList()
                onStatus("Could not reach other phone at $host:$port (${err.message ?: "check Wi-Fi/Hotspot"})")
            }
        }
    }

    fun disconnectPeer() {
        val oldHost = peerHost
        val oldPort = peerPort
        peerHost = null
        peerPort = DEFAULT_PORT
        _peerName.value = null
        _peerFiles.value = emptyList()
        if (oldHost != null) {
            scope.launch {
                runCatching {
                    val conn = (URL("http://$oldHost:$oldPort/bye").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 1500
                        readTimeout = 1500
                        doOutput = true
                    }
                    conn.outputStream.use {}
                    conn.responseCode
                    conn.disconnect()
                }
            }
        }
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

    private fun sendPeerTransferCommand(endpoint: String, sessionId: String?) {
        val host = peerHost ?: return
        val sid = sessionId?.trim().orEmpty()
        if (sid.isEmpty()) return
        scope.launch {
            runCatching {
                val conn = (URL("http://$host:$peerPort/$endpoint?session=${URLEncoder.encode(sid, "UTF-8")}").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 2000
                    readTimeout = 2000
                }
                conn.responseCode
                conn.disconnect()
            }
        }
    }

    private fun updateQueueStatusForFile(fileName: String?, newStatus: String) {
        if (fileName.isNullOrBlank() || _transferQueue.value.isEmpty()) return
        _transferQueue.value = _transferQueue.value.map { item ->
            if (item.fileName == fileName && (item.status == "Sending" || item.status == "Paused")) {
                item.copy(status = newStatus)
            } else {
                item
            }
        }
    }

    fun pauseActiveTransfer() {
        val cur = _transferProgress.value ?: return
        if (cur.isComplete) return
        val incomingSid = activeIncomingSessionId
        if (activeTransferJob?.isActive != true && !incomingSid.isNullOrBlank()) {
            pausedSessions.add(incomingSid)
            sessionControlCommands[incomingSid] = "pause"
            _transferProgress.value = cur.copy(isPaused = true, bytesPerSec = 0L)
            return
        }
        pauseRequested.set(true)
        _transferProgress.value = cur.copy(isPaused = true, bytesPerSec = 0L)
        updateQueueStatusForFile(cur.fileName, "Paused")
        val sid = activeSessionId ?: cur.transferId
        sendPeerTransferCommand("transfer-pause", sid)
    }

    fun resumeActiveTransfer(onComplete: (String) -> Unit) {
        val incomingSid = activeIncomingSessionId
        if (activeTransferJob?.isActive != true && pausedUploadState == null && pausedDownloadState == null && !incomingSid.isNullOrBlank()) {
            pausedSessions.remove(incomingSid)
            sessionControlCommands[incomingSid] = "resume"
            _transferProgress.value = _transferProgress.value?.copy(isPaused = false)
            return
        }
        val sid = activeSessionId ?: _transferProgress.value?.transferId
        if (activeTransferJob?.isActive == true) {
            cancelRequested.set(false)
            pauseRequested.set(false)
            val cur = _transferProgress.value
            _transferProgress.value = cur?.copy(isPaused = false)
            updateQueueStatusForFile(cur?.fileName, "Sending")
            sendPeerTransferCommand("transfer-resume", sid)
            return
        }
        val up = pausedUploadState
        if (up != null) {
            pausedUploadState = null
            pauseRequested.set(false)
            cancelRequested.set(false)
            sendPeerTransferCommand("transfer-resume", up.sessionId)
            sendFilesInternal(up.uris, up.currentIndex, up.sessionId, onComplete, checkResume = true)
            return
        }
        val dl = pausedDownloadState
        if (dl != null) {
            pausedDownloadState = null
            pauseRequested.set(false)
            cancelRequested.set(false)
            downloadFileInternal(dl.fileName, dl.partFile, dl.totalBytes, onComplete)
        }
    }

    fun cancelActiveTransfer(onComplete: (String) -> Unit) {
        val cur = _transferProgress.value
        val jobWasActive = activeTransferJob?.isActive == true
        val incomingSid = activeIncomingSessionId
        if (!jobWasActive && !incomingSid.isNullOrBlank()) {
            cancelledSessions.add(incomingSid)
            pausedSessions.remove(incomingSid)
            sessionControlCommands[incomingSid] = "cancel"
            _transferProgress.value = null
            if (cur != null) onComplete("Cancelled '${cur.fileName}'.")
            return
        }
        val sid = activeSessionId ?: cur?.transferId.orEmpty()
        cancelRequested.set(true)
        pauseRequested.set(false)
        if (sid.isNotEmpty()) {
            cancelledSessions.add(sid)
        }
        runCatching { activeConn?.disconnect() }
        val host = peerHost
        if (host != null && sid.isNotEmpty()) {
            scope.launch {
                runCatching {
                    val conn = (URL("http://$host:$peerPort/transfer-cancel?session=${URLEncoder.encode(sid, "UTF-8")}").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 2000
                        readTimeout = 2000
                    }
                    conn.responseCode
                    conn.disconnect()
                }
            }
        }
        if (jobWasActive) return

        pausedUploadState = null
        pausedDownloadState?.let { runCatching { it.partFile.delete() } }
        pausedDownloadState = null
        activeConn = null
        cancelRequested.set(false)
        _transferQueue.value = emptyList()
        if (cur != null) {
            appendHistory(
                TransferHistoryItem(
                    id = "ph_cancel_${System.nanoTime()}",
                    fileName = cur.fileName,
                    sizeBytes = cur.bytesDone,
                    isSent = cur.isUpload,
                    bytesPerSec = 0L,
                    timestampMs = System.currentTimeMillis(),
                    statusLabel = "Cancelled"
                )
            )
            onComplete("Cancelled '${cur.fileName}'.")
        }
        _transferProgress.value = null
    }

    fun sendFileToPeer(uri: Uri, onComplete: (String) -> Unit) =
        sendFilesToPeer(listOf(uri), onComplete)

    fun sendFilesToPeer(uris: List<Uri>, onComplete: (String) -> Unit) {
        if (uris.isEmpty()) return
        pauseRequested.set(false)
        cancelRequested.set(false)
        pausedUploadState = null
        sendFilesInternal(uris, 0, "ph_${System.currentTimeMillis()}", onComplete, checkResume = false)
    }

    private fun sendFilesInternal(
        uris: List<Uri>,
        startIndex: Int,
        initialSessionId: String,
        onComplete: (String) -> Unit,
        checkResume: Boolean = false
    ) {
        val host = peerHost
        if (host == null) {
            onComplete("Scan the receiver phone's QR code first.")
            return
        }
        activeTransferJob?.cancel()
        val job = scope.launch {
            var successCount = 0
            var lastName = ""
            val queueItems = (startIndex until uris.size).mapIndexed { qIdx, origIdx ->
                val u = uris[origIdx]
                TransferQueueItem(
                    id = "q_${initialSessionId}_$qIdx",
                    fileName = resolveFileName(u),
                    sizeBytes = resolveFileSize(u).coerceAtLeast(0L),
                    status = if (qIdx == 0) "Sending" else "Queued"
                )
            }.toMutableList()
            _transferQueue.value = queueItems.toList()
            try {
                for (idx in startIndex until uris.size) {
                    val relIdx = idx - startIndex
                    val uri = uris[idx]
                    val name = queueItems.getOrNull(relIdx)?.fileName ?: resolveFileName(uri)
                    val total = resolveFileSize(uri).coerceAtLeast(1L)
                    val sessionId = if (idx == startIndex) initialSessionId else "ph_${System.currentTimeMillis()}_$idx"
                    activeSessionId = sessionId
                    if (relIdx in queueItems.indices) {
                        queueItems[relIdx] = queueItems[relIdx].copy(
                            status = if (pauseRequested.get()) "Paused" else "Sending"
                        )
                        _transferQueue.value = queueItems.toList()
                    }

                    var sent = 0L
                    var activeMs = 0L
                    var lastSliceMs = System.currentTimeMillis()
                    var uploadedNow = 0L
                    var lastEmit = lastSliceMs
                    var fatalError = false

                    val controlPoller = scope.launch {
                        while (isActive && !cancelRequested.get()) {
                            delay(420L)
                            if (cancelRequested.get()) break
                            val ctrl = runCatching {
                                val u = "http://$host:$peerPort/transfer-control?session=${URLEncoder.encode(sessionId, "UTF-8")}"
                                val c = (URL(u).openConnection() as HttpURLConnection).apply {
                                    connectTimeout = 1500
                                    readTimeout = 1500
                                }
                                val txt = c.inputStream.bufferedReader().use { it.readText() }
                                c.disconnect()
                                JSONObject(txt).optString("action", "none")
                            }.getOrNull() ?: continue
                            when (ctrl) {
                                "pause" -> if (!pauseRequested.get()) {
                                    pauseRequested.set(true)
                                    _transferProgress.value = _transferProgress.value?.copy(isPaused = true, bytesPerSec = 0L)
                                    if (relIdx in queueItems.indices) {
                                        queueItems[relIdx] = queueItems[relIdx].copy(status = "Paused")
                                        _transferQueue.value = queueItems.toList()
                                    }
                                }
                                "resume" -> if (pauseRequested.get()) {
                                    pauseRequested.set(false)
                                    _transferProgress.value = _transferProgress.value?.copy(isPaused = false)
                                    if (relIdx in queueItems.indices) {
                                        queueItems[relIdx] = queueItems[relIdx].copy(status = "Sending")
                                        _transferQueue.value = queueItems.toList()
                                    }
                                }
                                "cancel" -> {
                                    cancelRequested.set(true)
                                    runCatching { activeConn?.disconnect() }
                                    break
                                }
                            }
                        }
                    }

                    try {
                        val remoteOffset = if (checkResume && idx == startIndex) {
                            runCatching {
                                val stConn = (URL("http://$host:$peerPort/upload-status?session=${URLEncoder.encode(sessionId, "UTF-8")}").openConnection() as HttpURLConnection).apply {
                                    connectTimeout = 2500
                                    readTimeout = 2500
                                }
                                val txt = stConn.inputStream.bufferedReader().use { it.readText() }
                                stConn.disconnect()
                                JSONObject(txt).optLong("bytes", 0L).coerceIn(0L, total)
                            }.getOrDefault(0L)
                        } else {
                            0L
                        }

                        val remainingBytes = (total - remoteOffset).coerceAtLeast(0L)
                        sent = remoteOffset
                        lastSliceMs = System.currentTimeMillis()
                        lastEmit = lastSliceMs
                        _transferProgress.value = TransferProgress(
                            fileName = name,
                            bytesDone = sent,
                            totalBytes = maxOf(total, sent),
                            isUpload = true,
                            isPaused = pauseRequested.get(),
                            bytesPerSec = 0L,
                            transferId = sessionId
                        )

                        val encodedName = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
                        val uploadUrl = "http://$host:$peerPort/upload?session=${URLEncoder.encode(sessionId, "UTF-8")}&offset=$remoteOffset&total=$total"
                        val conn = (URL(uploadUrl).openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = 5000
                            readTimeout = 3_600_000
                            doOutput = true
                            setRequestProperty("X-Filename", encodedName)
                            setRequestProperty("Connection", "close")
                            if (total > 0) setFixedLengthStreamingMode(remainingBytes) else setChunkedStreamingMode(TRANSFER_BUF)
                        }
                        activeConn = conn

                        val buf = ByteArray(TRANSFER_BUF)
                        appContext.contentResolver.openInputStream(uri)?.use { inp ->
                            if (remoteOffset > 0L) skipFully(inp, remoteOffset)
                            BufferedOutputStream(conn.outputStream, TRANSFER_BUF).use { out ->
                                while (true) {
                                    if (cancelRequested.get()) throw IllegalStateException("Cancelled by user")
                                    if (pauseRequested.get()) {
                                        val nowPause = System.currentTimeMillis()
                                        activeMs += (nowPause - lastSliceMs).coerceAtLeast(0L)
                                        while (pauseRequested.get() && !cancelRequested.get()) {
                                            delay(80L)
                                        }
                                        lastSliceMs = System.currentTimeMillis()
                                        if (cancelRequested.get()) throw IllegalStateException("Cancelled by user")
                                    }
                                    val n = inp.read(buf)
                                    if (n <= 0) break
                                    out.write(buf, 0, n)
                                    sent += n
                                    uploadedNow += n
                                    val now = System.currentTimeMillis()
                                    if (now - lastEmit >= 220L || sent >= total) {
                                        activeMs += (now - lastSliceMs).coerceAtLeast(0L)
                                        lastSliceMs = now
                                        lastEmit = now
                                        val spd = (uploadedNow * 1000L) / max(1L, activeMs)
                                        _transferProgress.value = TransferProgress(
                                            fileName = name,
                                            bytesDone = sent,
                                            totalBytes = maxOf(total, sent),
                                            isUpload = true,
                                            isPaused = pauseRequested.get(),
                                            bytesPerSec = if (pauseRequested.get()) 0L else spd,
                                            transferId = sessionId
                                        )
                                    }
                                }
                                out.flush()
                            }
                        } ?: throw IllegalStateException("Cannot read selected file $name")

                        if (cancelRequested.get()) throw IllegalStateException("Cancelled by user")

                        val code = conn.responseCode
                        val resp = runCatching { conn.inputStream.bufferedReader().use { it.readText() } }.getOrDefault("{}")
                        conn.disconnect()
                        activeConn = null
                        if (code !in 200..299) throw IllegalStateException("HTTP $code")
                        val respJson = runCatching { JSONObject(resp) }.getOrNull()
                        if (respJson?.optBoolean("cancelled") == true) {
                            cancelRequested.set(true)
                            throw IllegalStateException("Cancelled on receiver")
                        }

                        lastName = name
                        val finalActiveMs = activeMs + (System.currentTimeMillis() - lastSliceMs).coerceAtLeast(0L)
                        val finalSpd = (uploadedNow * 1000L) / max(1L, finalActiveMs)
                        _transferProgress.value = TransferProgress(
                            fileName = name,
                            bytesDone = maxOf(total, sent),
                            totalBytes = maxOf(total, sent),
                            isUpload = true,
                            isComplete = true,
                            bytesPerSec = finalSpd,
                            transferId = sessionId
                        )
                        if (relIdx in queueItems.indices) {
                            queueItems[relIdx] = queueItems[relIdx].copy(status = "Sent")
                            _transferQueue.value = queueItems.toList()
                        }
                        appendHistory(
                            TransferHistoryItem(
                                id = "ph_sent_${System.nanoTime()}",
                                fileName = name,
                                sizeBytes = maxOf(total, sent),
                                isSent = true,
                                bytesPerSec = finalSpd,
                                timestampMs = System.currentTimeMillis(),
                                localUriString = uri.toString(),
                                statusLabel = "Sent"
                            )
                        )
                        successCount++
                    } catch (e: Exception) {
                        runCatching { activeConn?.disconnect() }
                        activeConn = null
                        if (cancelRequested.compareAndSet(true, false)) {
                            pauseRequested.set(false)
                            if (relIdx in queueItems.indices) {
                                queueItems[relIdx] = queueItems[relIdx].copy(status = "Cancelled")
                                _transferQueue.value = queueItems.toList()
                            }
                            appendHistory(
                                TransferHistoryItem(
                                    id = "ph_cancel_${System.nanoTime()}",
                                    fileName = name,
                                    sizeBytes = sent,
                                    isSent = true,
                                    bytesPerSec = 0L,
                                    timestampMs = System.currentTimeMillis(),
                                    statusLabel = "Cancelled"
                                )
                            )
                            onComplete("Cancelled '$name'.")
                        } else {
                            if (relIdx in queueItems.indices) {
                                queueItems[relIdx] = queueItems[relIdx].copy(status = "Failed")
                                _transferQueue.value = queueItems.toList()
                            }
                            appendHistory(
                                TransferHistoryItem(
                                    id = "ph_fail_${System.nanoTime()}",
                                    fileName = name,
                                    sizeBytes = sent,
                                    isSent = true,
                                    bytesPerSec = 0L,
                                    timestampMs = System.currentTimeMillis(),
                                    statusLabel = "Failed"
                                )
                            )
                            _transferProgress.value = null
                            onComplete("Phone send failed: ${e.message ?: "error"}")
                            fatalError = true
                        }
                    } finally {
                        controlPoller.cancel()
                    }
                    if (fatalError) break
                }
                if (successCount > 0) {
                    delay(1200)
                    refreshPeerFiles()
                    val msg = if (successCount == 1) {
                        "Sent '$lastName' to ${_peerName.value}"
                    } else {
                        "Sent $successCount file(s) to ${_peerName.value}"
                    }
                    onComplete(msg)
                }
                _transferProgress.value = null
            } finally {
                cancelRequested.set(false)
                pauseRequested.set(false)
                _transferQueue.value = emptyList()
                pausedUploadState = null
                activeConn = null
                activeSessionId = null
                if (activeTransferJob === coroutineContext[Job]) activeTransferJob = null
            }
        }
        activeTransferJob = job
    }

    fun downloadFileFromPeer(fileName: String, onComplete: (String) -> Unit) {
        pauseRequested.set(false)
        cancelRequested.set(false)
        pausedDownloadState = null
        val partFile = File(appContext.cacheDir, "ph_dl_${System.currentTimeMillis()}.gptpart")
        downloadFileInternal(fileName, partFile, 0L, onComplete)
    }

    private fun downloadFileInternal(
        fileName: String,
        partFile: File,
        knownTotal: Long,
        onComplete: (String) -> Unit
    ) {
        val host = peerHost
        if (host == null) {
            onComplete("Scan sender phone's QR code first.")
            return
        }
        activeTransferJob?.cancel()
        val job = scope.launch {
            var doneBytes = if (partFile.exists()) partFile.length() else 0L
            try {
                val sid = partFile.nameWithoutExtension
                activeSessionId = sid
                val encoded = URLEncoder.encode(fileName, "UTF-8")
                val conn = (URL("http://$host:$peerPort/download?name=$encoded").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 3_600_000
                    if (doneBytes > 0L) setRequestProperty("Range", "bytes=$doneBytes-")
                }
                activeConn = conn
                if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
                val respLen = conn.contentLengthLong.coerceAtLeast(0L)
                val total = if (conn.responseCode == 206 && knownTotal > 0L) knownTotal else maxOf(knownTotal, doneBytes + respLen)
                var activeMs = 0L
                var lastSliceMs = System.currentTimeMillis()
                var downloadedThisConn = 0L
                var lastEmit = lastSliceMs
                _transferProgress.value = TransferProgress(fileName, doneBytes, total, isUpload = false, isPaused = pauseRequested.get(), bytesPerSec = 0L, transferId = sid)

                val buf = ByteArray(TRANSFER_BUF)
                BufferedInputStream(conn.inputStream, TRANSFER_BUF).use { inp ->
                    FileOutputStream(partFile, doneBytes > 0L && conn.responseCode == 206).use { fos ->
                        if (conn.responseCode == 200 && doneBytes > 0L) doneBytes = 0L
                        while (true) {
                            if (cancelRequested.get()) throw IllegalStateException("Cancelled by user")
                            if (pauseRequested.get()) {
                                val nowPause = System.currentTimeMillis()
                                activeMs += (nowPause - lastSliceMs).coerceAtLeast(0L)
                                while (pauseRequested.get() && !cancelRequested.get()) {
                                    delay(80L)
                                }
                                lastSliceMs = System.currentTimeMillis()
                                if (cancelRequested.get()) throw IllegalStateException("Cancelled by user")
                            }
                            val n = inp.read(buf)
                            if (n <= 0) break
                            fos.write(buf, 0, n)
                            doneBytes += n
                            downloadedThisConn += n
                            val now = System.currentTimeMillis()
                            if (now - lastEmit >= 220L || (total > 0 && doneBytes >= total)) {
                                activeMs += (now - lastSliceMs).coerceAtLeast(0L)
                                lastSliceMs = now
                                lastEmit = now
                                val spd = (downloadedThisConn * 1000L) / max(1L, activeMs)
                                _transferProgress.value = TransferProgress(
                                    fileName = fileName,
                                    bytesDone = doneBytes,
                                    totalBytes = maxOf(total, doneBytes),
                                    isUpload = false,
                                    isPaused = pauseRequested.get(),
                                    bytesPerSec = if (pauseRequested.get()) 0L else spd,
                                    transferId = sid
                                )
                            }
                        }
                    }
                }
                conn.disconnect()
                activeConn = null

                val savedUri = partFile.inputStream().use { fis ->
                    saveStreamToDownloads(fileName, fis) { }
                }
                runCatching { partFile.delete() }
                val finalSize = maxOf(total, doneBytes)
                val finalActiveMs = activeMs + (System.currentTimeMillis() - lastSliceMs).coerceAtLeast(0L)
                val finalSpd = (downloadedThisConn * 1000L) / max(1L, finalActiveMs)
                _transferProgress.value = TransferProgress(
                    fileName = fileName,
                    bytesDone = finalSize,
                    totalBytes = finalSize,
                    isUpload = false,
                    isComplete = true,
                    bytesPerSec = finalSpd,
                    transferId = sid
                )
                appendHistory(
                    TransferHistoryItem(
                        id = "ph_dl_${System.nanoTime()}",
                        fileName = fileName,
                        sizeBytes = finalSize,
                        isSent = false,
                        bytesPerSec = finalSpd,
                        timestampMs = System.currentTimeMillis(),
                        localUriString = savedUri?.toString(),
                        statusLabel = "Received"
                    )
                )
                delay(1200)
                _transferProgress.value = null
                onComplete("Saved '$fileName' to Downloads/GptRemote")
            } catch (e: Exception) {
                runCatching { activeConn?.disconnect() }
                activeConn = null
                runCatching { partFile.delete() }
                if (cancelRequested.compareAndSet(true, false)) {
                    appendHistory(
                        TransferHistoryItem(
                            id = "ph_dl_cancel_${System.nanoTime()}",
                            fileName = fileName,
                            sizeBytes = doneBytes,
                            isSent = false,
                            bytesPerSec = 0L,
                            timestampMs = System.currentTimeMillis(),
                            statusLabel = "Cancelled"
                        )
                    )
                    _transferProgress.value = null
                    onComplete("Cancelled '$fileName'.")
                } else {
                    appendHistory(
                        TransferHistoryItem(
                            id = "ph_dl_fail_${System.nanoTime()}",
                            fileName = fileName,
                            sizeBytes = doneBytes,
                            isSent = false,
                            bytesPerSec = 0L,
                            timestampMs = System.currentTimeMillis(),
                            statusLabel = "Failed"
                        )
                    )
                    _transferProgress.value = null
                    onComplete("Download failed: ${e.message ?: "error"}")
                }
            } finally {
                cancelRequested.set(false)
                pauseRequested.set(false)
                pausedDownloadState = null
                activeConn = null
                activeSessionId = null
                if (activeTransferJob === coroutineContext[Job]) activeTransferJob = null
            }
        }
        activeTransferJob = job
    }

    private fun handleHttpClient(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = 3_600_000
            runCatching {
                socket.receiveBufferSize = 2 * 1024 * 1024
                socket.sendBufferSize = 2 * 1024 * 1024
            }
            val inp = BufferedInputStream(socket.getInputStream(), TRANSFER_BUF)
            val out = BufferedOutputStream(socket.getOutputStream(), TRANSFER_BUF)
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

                method == "POST" && path == "/hello" -> {
                    val cl = headerMap["content-length"]?.toIntOrNull()?.coerceIn(0, 16384) ?: 0
                    val bodyBytes = ByteArray(cl)
                    var off = 0
                    while (off < cl) {
                        val r = inp.read(bodyBytes, off, cl - off)
                        if (r <= 0) break
                        off += r
                    }
                    val json = runCatching { JSONObject(String(bodyBytes, 0, off, Charsets.UTF_8)) }.getOrNull()
                    val callerIp = socket.inetAddress.hostAddress
                    val callerPort = json?.optInt("port", DEFAULT_PORT) ?: DEFAULT_PORT
                    val callerName = json?.optString("name")?.takeIf { it.isNotBlank() } ?: callerIp ?: "Phone"
                    if (!callerIp.isNullOrBlank()) {
                        peerHost = callerIp
                        peerPort = callerPort
                        _peerName.value = callerName
                        refreshPeerFiles()
                    }
                    val resp = """{"ok":true,"name":"${Build.MODEL}"}""".toByteArray(Charsets.UTF_8)
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${resp.size}\r\n\r\n".toByteArray())
                    out.write(resp)
                    out.flush()
                }

                method == "POST" && path == "/bye" -> {
                    val callerIp = socket.inetAddress.hostAddress
                    if (callerIp != null && callerIp == peerHost) {
                        peerHost = null
                        _peerName.value = null
                        _peerFiles.value = emptyList()
                    }
                    out.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK".toByteArray())
                    out.flush()
                }

                method == "GET" && path == "/upload-status" -> {
                    val sid = query.split('&').firstOrNull { it.startsWith("session=") }
                        ?.substringAfter("session=")?.let { URLDecoder.decode(it, "UTF-8") }?.trim().orEmpty()
                    val existing = if (sid.isNotEmpty()) serverUploadParts[sid]?.takeIf { it.exists() }?.length() ?: 0L else 0L
                    val body = """{"bytes":$existing}""".toByteArray(Charsets.UTF_8)
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\n\r\n".toByteArray())
                    out.write(body)
                    out.flush()
                }

                method == "POST" && path == "/transfer-cancel" -> {
                    val sid = query.split('&').firstOrNull { it.startsWith("session=") }
                        ?.substringAfter("session=")?.let { URLDecoder.decode(it, "UTF-8") }?.trim().orEmpty()
                    if (sid.isNotEmpty()) {
                        cancelledSessions.add(sid)
                        pausedSessions.remove(sid)
                        sessionControlCommands.remove(sid)
                        serverUploadParts.remove(sid)?.let { runCatching { it.delete() } }
                    } else {
                        serverDownloadCancel.set(true)
                    }
                    _transferProgress.value = null
                    out.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK".toByteArray())
                    out.flush()
                }

                method == "POST" && path == "/transfer-pause" -> {
                    val sid = query.split('&').firstOrNull { it.startsWith("session=") }
                        ?.substringAfter("session=")?.let { URLDecoder.decode(it, "UTF-8") }?.trim().orEmpty()
                    if (sid.isNotEmpty()) pausedSessions.add(sid)
                    val cur = _transferProgress.value
                    if (cur != null && (sid.isEmpty() || cur.transferId == sid)) {
                        _transferProgress.value = cur.copy(isPaused = true, bytesPerSec = 0L)
                    }
                    val resp = """{"ok":true}""".toByteArray(Charsets.UTF_8)
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${resp.size}\r\n\r\n".toByteArray())
                    out.write(resp)
                    out.flush()
                }

                method == "POST" && path == "/transfer-resume" -> {
                    val sid = query.split('&').firstOrNull { it.startsWith("session=") }
                        ?.substringAfter("session=")?.let { URLDecoder.decode(it, "UTF-8") }?.trim().orEmpty()
                    if (sid.isNotEmpty()) pausedSessions.remove(sid)
                    val cur = _transferProgress.value
                    if (cur != null && (sid.isEmpty() || cur.transferId == sid)) {
                        _transferProgress.value = cur.copy(isPaused = false)
                    }
                    val resp = """{"ok":true}""".toByteArray(Charsets.UTF_8)
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${resp.size}\r\n\r\n".toByteArray())
                    out.write(resp)
                    out.flush()
                }

                method == "GET" && path == "/transfer-control" -> {
                    val sid = query.split('&').firstOrNull { it.startsWith("session=") }
                        ?.substringAfter("session=")?.let { URLDecoder.decode(it, "UTF-8") }?.trim().orEmpty()
                    val action = if (sid.isNotEmpty()) (sessionControlCommands.remove(sid) ?: "none") else "none"
                    val isPaused = sid.isNotEmpty() && pausedSessions.contains(sid)
                    val resp = JSONObject().put("action", action).put("paused", isPaused).toString().toByteArray(Charsets.UTF_8)
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${resp.size}\r\n\r\n".toByteArray())
                    out.write(resp)
                    out.flush()
                }

                method == "GET" && path == "/download" -> {
                    serverDownloadCancel.set(false)
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
                    val total = item.sizeBytes
                    val parsedRange = if (total > 0L) parseByteRange(headerMap["range"], total) else null
                    val startOffset = parsedRange?.first ?: 0L
                    val endOffset = parsedRange?.second ?: (total - 1L).coerceAtLeast(0L)
                    val toSend = if (parsedRange != null) (endOffset - startOffset + 1L) else total
                    val statusLine = if (parsedRange != null) "206 Partial Content" else "200 OK"
                    val rangeHdr = if (parsedRange != null) "Content-Range: bytes $startOffset-$endOffset/$total\r\n" else ""
                    val lenHeader = if (toSend > 0) "Content-Length: $toSend\r\n" else ""
                    val head = "HTTP/1.1 $statusLine\r\nContent-Type: application/octet-stream\r\nAccept-Ranges: bytes\r\n${rangeHdr}${lenHeader}Connection: close\r\n\r\n"
                    out.write(head.toByteArray(Charsets.UTF_8))
                    var sent = startOffset
                    var sentThisConn = 0L
                    val startedMs = System.currentTimeMillis()
                    var lastEmit = startedMs
                    val buf = ByteArray(TRANSFER_BUF)
                    _transferProgress.value = TransferProgress(item.name, sent, total, isUpload = true, bytesPerSec = 0L)
                    appContext.contentResolver.openInputStream(item.uri)?.use { fis ->
                        if (startOffset > 0L) skipFully(fis, startOffset)
                        var remaining = if (toSend > 0) toSend else Long.MAX_VALUE
                        while (remaining > 0L && !serverDownloadCancel.get()) {
                            val n = fis.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            sent += n
                            sentThisConn += n
                            remaining -= n
                            val now = System.currentTimeMillis()
                            if (now - lastEmit >= 160L || sent >= total) {
                                lastEmit = now
                                val spd = (sentThisConn * 1000L) / max(1L, now - startedMs)
                                _transferProgress.value = TransferProgress(
                                    fileName = item.name,
                                    bytesDone = sent,
                                    totalBytes = maxOf(total, sent),
                                    isUpload = true,
                                    bytesPerSec = spd
                                )
                            }
                        }
                    }
                    out.flush()
                    val finalSpd = (sentThisConn * 1000L) / max(1L, System.currentTimeMillis() - startedMs)
                    val completed = !serverDownloadCancel.get() && (total <= 0L || sent >= total)
                    if (completed) {
                        appendHistory(
                            TransferHistoryItem(
                                id = "ph_host_sent_${System.nanoTime()}",
                                fileName = item.name,
                                sizeBytes = sent,
                                isSent = true,
                                bytesPerSec = finalSpd,
                                timestampMs = System.currentTimeMillis(),
                                localUriString = item.uri.toString(),
                                statusLabel = "Sent"
                            )
                        )
                    } else if (serverDownloadCancel.get()) {
                        appendHistory(
                            TransferHistoryItem(
                                id = "ph_host_cancel_${System.nanoTime()}",
                                fileName = item.name,
                                sizeBytes = sent,
                                isSent = true,
                                bytesPerSec = 0L,
                                timestampMs = System.currentTimeMillis(),
                                statusLabel = "Cancelled"
                            )
                        )
                    }
                    _transferProgress.value = null
                }

                method == "POST" && path == "/upload" -> {
                    val qMap = query.split('&').mapNotNull {
                        val eq = it.indexOf('=')
                        if (eq > 0) it.substring(0, eq) to URLDecoder.decode(it.substring(eq + 1), "UTF-8") else null
                    }.toMap()
                    val sid = qMap["session"]?.trim().orEmpty()
                    if (sid.isNotEmpty()) {
                        cancelledSessions.remove(sid)
                        pausedSessions.remove(sid)
                        sessionControlCommands.remove(sid)
                        activeIncomingSessionId = sid
                    }
                    val offset = qMap["offset"]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                    val totalParam = qMap["total"]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L

                    val rawName = headerMap["x-filename"]?.let { URLDecoder.decode(it, "UTF-8") } ?: "shared_file.bin"
                    val cleanName = rawName.substringAfterLast('/').substringAfterLast('\\').ifBlank { "shared_file.bin" }
                    val contentLength = headerMap["content-length"]?.toLongOrNull() ?: 0L
                    val totalExpected = maxOf(totalParam, offset + contentLength)

                    val startedMs = System.currentTimeMillis()
                    var lastEmit = startedMs
                    var receivedThisConn = 0L
                    _transferProgress.value = TransferProgress(cleanName, offset, totalExpected, isUpload = false, bytesPerSec = 0L, transferId = sid)

                    val partFile = if (sid.isNotEmpty()) {
                        serverUploadParts.getOrPut(sid) {
                            File(appContext.cacheDir, ".phone_${sid.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.gptpart")
                        }
                    } else {
                        File(appContext.cacheDir, ".phone_${System.nanoTime()}.gptpart")
                    }

                    val buf = ByteArray(TRANSFER_BUF)
                    try {
                        RandomAccessFile(partFile, "rw").use { raf ->
                            if (offset > 0L && partFile.exists()) raf.seek(offset) else raf.setLength(0L)
                            var remaining = if (contentLength > 0L) contentLength else Long.MAX_VALUE
                            while (remaining > 0L) {
                                if (sid.isNotEmpty() && cancelledSessions.contains(sid)) break
                                val toRead = minOf(buf.size.toLong(), remaining).toInt()
                                val r = inp.read(buf, 0, toRead)
                                if (r <= 0) break
                                raf.write(buf, 0, r)
                                receivedThisConn += r
                                remaining -= r
                                val overallDone = offset + receivedThisConn
                                val now = System.currentTimeMillis()
                                val isSessionPaused = sid.isNotEmpty() && pausedSessions.contains(sid)
                                if ((now - lastEmit >= 220L || (totalExpected > 0L && overallDone >= totalExpected)) && !isSessionPaused) {
                                    lastEmit = now
                                    val spd = (receivedThisConn * 1000L) / max(1L, now - startedMs)
                                    _transferProgress.value = TransferProgress(
                                        fileName = cleanName,
                                        bytesDone = overallDone,
                                        totalBytes = maxOf(totalExpected, overallDone),
                                        isUpload = false,
                                        isPaused = false,
                                        bytesPerSec = spd,
                                        transferId = sid
                                    )
                                }
                            }
                        }
                    } finally {
                        if (activeIncomingSessionId == sid) activeIncomingSessionId = null
                    }

                    val overallDone = offset + receivedThisConn
                    if (sid.isNotEmpty() && cancelledSessions.remove(sid)) {
                        serverUploadParts.remove(sid)
                        runCatching { partFile.delete() }
                        _transferProgress.value = null
                        appendHistory(
                            TransferHistoryItem(
                                id = "ph_host_cancel_${System.nanoTime()}",
                                fileName = cleanName,
                                sizeBytes = overallDone,
                                isSent = false,
                                bytesPerSec = 0L,
                                timestampMs = System.currentTimeMillis(),
                                statusLabel = "Cancelled"
                            )
                        )
                        val resp = """{"ok":false,"cancelled":true}""".toByteArray(Charsets.UTF_8)
                        out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${resp.size}\r\n\r\n".toByteArray())
                        out.write(resp)
                        out.flush()
                        return
                    }

                    val completed = sid.isEmpty() || totalExpected <= 0L || overallDone >= totalExpected
                    if (!completed) {
                        _transferProgress.value = TransferProgress(cleanName, overallDone, totalExpected, isUpload = false, isPaused = true, transferId = sid)
                        val resp = """{"ok":true,"paused":true,"received":$overallDone}""".toByteArray(Charsets.UTF_8)
                        out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${resp.size}\r\n\r\n".toByteArray())
                        out.write(resp)
                        out.flush()
                        return
                    }

                    if (sid.isNotEmpty()) serverUploadParts.remove(sid)
                    val savedUri = partFile.inputStream().use { fis ->
                        saveStreamToDownloads(cleanName, fis) { }
                    }
                    runCatching { partFile.delete() }
                    val finalSize = maxOf(totalExpected, overallDone)
                    val finalSpd = (receivedThisConn * 1000L) / max(1L, System.currentTimeMillis() - startedMs)
                    appendHistory(
                        TransferHistoryItem(
                            id = "ph_host_recv_${System.nanoTime()}",
                            fileName = cleanName,
                            sizeBytes = finalSize,
                            isSent = false,
                            bytesPerSec = finalSpd,
                            timestampMs = System.currentTimeMillis(),
                            localUriString = savedUri?.toString(),
                            statusLabel = "Received"
                        )
                    )
                    _transferProgress.value = TransferProgress(cleanName, finalSize, finalSize, isUpload = false, isComplete = true, bytesPerSec = finalSpd, transferId = sid)
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
        val buf = ByteArray(TRANSFER_BUF)
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
        private const val TRANSFER_BUF = 2 * 1024 * 1024

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
