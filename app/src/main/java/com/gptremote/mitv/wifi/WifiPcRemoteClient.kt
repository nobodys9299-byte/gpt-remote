package com.gptremote.mitv.wifi

import com.gptremote.mitv.model.ConnectionStatus
import com.gptremote.mitv.model.MouseButton
import com.gptremote.mitv.model.RemoteButton
import com.gptremote.mitv.model.TransportMode
import com.gptremote.mitv.model.TvDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Client for controlling a Windows / macOS / Linux laptop or PC over Wi-Fi using the
 * lightweight companion script (pc-companion/gpt_remote_pc.py).
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

    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var activeDevice: TvDevice.WifiPc? = null

    private val sendQueue = Channel<String>(Channel.UNLIMITED)
    private var senderJob: Job? = null
    private var mouseTickerJob: Job? = null
    private var scanJob: Job? = null

    private val pendingDx = AtomicInteger(0)
    private val pendingDy = AtomicInteger(0)
    private val pendingWheel = AtomicInteger(0)

    init {
        startWorkers()
    }

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
            val found = LinkedHashMap<String, TvDevice.WifiPc>()
            var udpSocket: DatagramSocket? = null
            try {
                udpSocket = DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 600
                }
                val probe = DISCOVER_MAGIC.toByteArray(Charsets.UTF_8)
                val packet = DatagramPacket(
                    probe,
                    probe.size,
                    InetAddress.getByName("255.255.255.255"),
                    DISCOVERY_PORT
                )
                udpSocket.send(packet)

                val deadline = System.currentTimeMillis() + 2500L
                val buf = ByteArray(1024)
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val resp = DatagramPacket(buf, buf.size)
                        udpSocket.receive(resp)
                        val text = String(resp.data, 0, resp.length, Charsets.UTF_8)
                        val json = JSONObject(text)
                        if (json.optString("type") == "announce") {
                            val host = resp.address.hostAddress ?: continue
                            val port = json.optInt("port", DEFAULT_PORT)
                            val name = json.optString("name", "Laptop ($host)")
                            val os = json.optString("os", "Desktop")
                            val id = "$host:$port"
                            found[id] = TvDevice.WifiPc(
                                id = id,
                                name = name,
                                host = host,
                                port = port,
                                osName = os
                            )
                            _discoveredPcs.value = found.values.toList()
                        }
                    } catch (_: SocketTimeoutException) {
                        // continue until deadline
                    }
                }
            } catch (_: Exception) {
                // Ignore UDP errors when network offline
            } finally {
                runCatching { udpSocket?.close() }
                _isScanning.value = false
            }
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

        scope.launch {
            try {
                val tcp = Socket()
                tcp.tcpNoDelay = true
                tcp.connect(InetSocketAddress(device.host, device.port), 5000)
                socket = tcp
                writer = BufferedWriter(OutputStreamWriter(tcp.getOutputStream(), Charsets.UTF_8))
                _connectionStatus.value = ConnectionStatus.Connected(device)
            } catch (e: Exception) {
                _connectionStatus.value = ConnectionStatus.Error(
                    message = "Cannot reach PC at ${device.host}:${device.port}. Start gpt_remote_pc.py on your laptop first.",
                    transport = TransportMode.WIFI_PC
                )
            }
        }
    }

    fun disconnect() {
        runCatching { writer?.close() }
        runCatching { socket?.close() }
        writer = null
        socket = null
        activeDevice = null
        _connectionStatus.value = ConnectionStatus.Disconnected
    }

    fun sendMouseMove(dx: Int, dy: Int, wheel: Int = 0) {
        if (writer == null) return
        if (dx != 0) pendingDx.addAndGet(dx)
        if (dy != 0) pendingDy.addAndGet(dy)
        if (wheel != 0) pendingWheel.addAndGet(wheel)
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

    private fun handleConnectionLost(reason: String?) {
        if (activeDevice != null) {
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
            RemoteButton.TV_INPUT -> null
        }
    }
}
