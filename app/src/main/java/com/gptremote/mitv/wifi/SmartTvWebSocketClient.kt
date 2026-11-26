package com.gptremote.mitv.wifi

import android.content.Context
import android.util.Base64
import com.gptremote.mitv.model.ConnectionStatus
import com.gptremote.mitv.model.RemoteButton
import com.gptremote.mitv.model.TransportMode
import com.gptremote.mitv.model.TvDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

enum class SmartTvProtocol {
    SAMSUNG_TIZEN,
    LG_WEBOS
}

/**
 * Dependency-free RFC-6455 WebSocket client for Samsung Tizen TVs (8001/8002)
 * and LG webOS TVs (3000/3001).
 */
class SmartTvWebSocketClient(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("smart_tv_ws_keys", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private var socket: Socket? = null
    private var output: OutputStream? = null
    private var readerJob: Job? = null
    private var activeProtocol: SmartTvProtocol = SmartTvProtocol.SAMSUNG_TIZEN
    private var lgMsgId = 1
    private var lgPointerOut: OutputStream? = null
    private var lgPointerSocket: Socket? = null

    fun connect(host: String, name: String, protocol: SmartTvProtocol) {
        disconnect()
        activeProtocol = protocol
        _connectionStatus.value = ConnectionStatus.Connecting(name, TransportMode.WIFI_ANDROID_TV)

        readerJob = scope.launch {
            try {
                when (protocol) {
                    SmartTvProtocol.SAMSUNG_TIZEN -> connectSamsung(host, name)
                    SmartTvProtocol.LG_WEBOS -> connectLgWebOs(host, name)
                }
            } catch (e: Exception) {
                _connectionStatus.value = ConnectionStatus.Error(
                    message = "Could not connect to $name over Wi-Fi: ${e.message ?: "check TV network & allow prompt"}",
                    transport = TransportMode.WIFI_ANDROID_TV
                )
            }
        }
    }

    private suspend fun CoroutineScope.connectSamsung(host: String, name: String) {
        val clientNameB64 = Base64.encodeToString("GPT Remote".toByteArray(), Base64.NO_WRAP)
        val path = "/api/v2/channels/samsung.remote.control?name=$clientNameB64"

        // Try TLS 8002 first (2018+ Tizen), fall back to plain 8001
        val (sock, inp, out) = runCatching {
            openWebSocket(host, 8002, path, useTls = true)
        }.getOrElse {
            openWebSocket(host, 8001, path, useTls = false)
        }
        socket = sock
        output = out

        val tvDevice = TvDevice.WifiTv(
            id = "$host:8001",
            name = name,
            host = host,
            port = 8001,
            pairingPort = 8002,
            isPaired = true
        )
        _connectionStatus.value = ConnectionStatus.Connected(tvDevice)

        while (isActive && !sock.isClosed) {
            val frame = readWsTextFrame(inp) ?: break
            val json = runCatching { JSONObject(frame) }.getOrNull() ?: continue
            val event = json.optString("event")
            if (event == "ms.channel.connect") {
                val token = json.optJSONObject("data")?.optString("token")
                if (!token.isNullOrBlank()) {
                    prefs.edit().putString("samsung_token_$host", token).apply()
                }
            }
        }
    }

    private suspend fun CoroutineScope.connectLgWebOs(host: String, name: String) {
        val (sock, inp, out) = runCatching {
            openWebSocket(host, 3000, "/", useTls = false)
        }.getOrElse {
            openWebSocket(host, 3001, "/", useTls = true)
        }
        socket = sock
        output = out

        val savedKey = prefs.getString("lg_key_$host", null)
        val payload = JSONObject().apply {
            if (!savedKey.isNullOrBlank()) put("client-key", savedKey)
            put("pairingType", "PROMPT")
            put("manifest", JSONObject().apply {
                put("manifestVersion", 1)
                put("permissions", org.json.JSONArray(listOf(
                    "LAUNCH", "CONTROL_AUDIO", "CONTROL_INPUT_TV",
                    "CONTROL_POWER", "READ_INSTALLED_APPS", "CONTROL_INPUT_MEDIA_PLAYBACK"
                )))
            })
        }
        val registerMsg = JSONObject().apply {
            put("id", "reg_${lgMsgId++}")
            put("type", "register")
            put("payload", payload)
        }
        sendWsTextFrame(out, registerMsg.toString())

        val tvDevice = TvDevice.WifiTv(
            id = "$host:3000",
            name = name,
            host = host,
            port = 3000,
            pairingPort = 3001,
            isPaired = !savedKey.isNullOrBlank()
        )

        while (isActive && !sock.isClosed) {
            val frame = readWsTextFrame(inp) ?: break
            val json = runCatching { JSONObject(frame) }.getOrNull() ?: continue
            val type = json.optString("type")
            if (type == "registered") {
                val key = json.optJSONObject("payload")?.optString("client-key")
                if (!key.isNullOrBlank()) {
                    prefs.edit().putString("lg_key_$host", key).apply()
                }
                _connectionStatus.value = ConnectionStatus.Connected(tvDevice.copy(isPaired = true))
                // Request pointer input socket for D-Pad navigation
                sendLgCommand("ssap://com.webos.service.networkinput/getPointerInputSocket")
            } else if (type == "response") {
                val socketPath = json.optJSONObject("payload")?.optString("socketPath")
                if (!socketPath.isNullOrBlank() && socketPath.startsWith("ws")) {
                    launch { openLgPointerSocket(socketPath) }
                }
            }
        }
    }

    private fun openLgPointerSocket(wsUrl: String) {
        runCatching {
            val clean = wsUrl.removePrefix("ws://").removePrefix("wss://")
            val useTls = wsUrl.startsWith("wss://")
            val hostPort = clean.substringBefore("/")
            val path = "/" + clean.substringAfter("/", "")
            val h = hostPort.substringBefore(":")
            val p = hostPort.substringAfter(":", if (useTls) "3001" else "3000").toInt()
            val (s, _, out) = openWebSocket(h, p, path, useTls)
            lgPointerSocket = s
            lgPointerOut = out
        }
    }

    fun sendButton(button: RemoteButton) {
        scope.launch {
            when (activeProtocol) {
                SmartTvProtocol.SAMSUNG_TIZEN -> sendSamsungButton(button)
                SmartTvProtocol.LG_WEBOS -> sendLgButton(button)
            }
        }
    }

    fun launchApp(appId: String) {
        scope.launch {
            when (activeProtocol) {
                SmartTvProtocol.SAMSUNG_TIZEN -> {
                    val samsungId = samsungAppId(appId) ?: return@launch
                    val out = output ?: return@launch
                    val json = JSONObject().apply {
                        put("method", "ms.channel.emit")
                        put("params", JSONObject().apply {
                            put("event", "ed.apps.launch")
                            put("to", "host")
                            put("data", JSONObject().apply {
                                put("appId", samsungId)
                                put("action_type", "NATIVE_LAUNCH")
                            })
                        })
                    }
                    runCatching { sendWsTextFrame(out, json.toString()) }
                }
                SmartTvProtocol.LG_WEBOS -> {
                    val lgId = lgAppId(appId) ?: return@launch
                    sendLgCommand("ssap://system.launcher/launch", JSONObject().put("id", lgId))
                }
            }
        }
    }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        scope.launch {
            if (activeProtocol == SmartTvProtocol.LG_WEBOS) {
                sendLgCommand(
                    "ssap://com.webos.service.ime/insertText",
                    JSONObject().put("text", text).put("replace", 0)
                )
            }
        }
    }

    private fun sendSamsungButton(button: RemoteButton) {
        val out = output ?: return
        val key = when (button) {
            RemoteButton.POWER -> "KEY_POWER"
            RemoteButton.DPAD_UP -> "KEY_UP"
            RemoteButton.DPAD_DOWN -> "KEY_DOWN"
            RemoteButton.DPAD_LEFT -> "KEY_LEFT"
            RemoteButton.DPAD_RIGHT -> "KEY_RIGHT"
            RemoteButton.DPAD_CENTER, RemoteButton.ENTER -> "KEY_ENTER"
            RemoteButton.BACK, RemoteButton.ESCAPE -> "KEY_RETURN"
            RemoteButton.HOME -> "KEY_HOME"
            RemoteButton.MENU, RemoteButton.SETTINGS -> "KEY_MENU"
            RemoteButton.VOLUME_UP -> "KEY_VOLUP"
            RemoteButton.VOLUME_DOWN -> "KEY_VOLDOWN"
            RemoteButton.MUTE -> "KEY_MUTE"
            RemoteButton.CHANNEL_UP -> "KEY_CHUP"
            RemoteButton.CHANNEL_DOWN -> "KEY_CHDOWN"
            RemoteButton.MEDIA_PLAY_PAUSE -> "KEY_PLAY_PAUSE"
            RemoteButton.MEDIA_PREVIOUS -> "KEY_REWIND"
            RemoteButton.MEDIA_NEXT -> "KEY_FF"
            RemoteButton.MEDIA_STOP -> "KEY_STOP"
            RemoteButton.TV_INPUT -> "KEY_SOURCE"
            RemoteButton.SEARCH -> "KEY_SEARCH"
            RemoteButton.GUIDE -> "KEY_GUIDE"
            RemoteButton.INFO -> "KEY_INFO"
            RemoteButton.DIGIT_0 -> "KEY_0"
            RemoteButton.DIGIT_1 -> "KEY_1"
            RemoteButton.DIGIT_2 -> "KEY_2"
            RemoteButton.DIGIT_3 -> "KEY_3"
            RemoteButton.DIGIT_4 -> "KEY_4"
            RemoteButton.DIGIT_5 -> "KEY_5"
            RemoteButton.DIGIT_6 -> "KEY_6"
            RemoteButton.DIGIT_7 -> "KEY_7"
            RemoteButton.DIGIT_8 -> "KEY_8"
            RemoteButton.DIGIT_9 -> "KEY_9"
            else -> return
        }
        val msg = JSONObject().apply {
            put("method", "ms.remote.control")
            put("params", JSONObject().apply {
                put("Cmd", "Click")
                put("DataOfCmd", key)
                put("Option", "false")
                put("TypeOfRemote", "SendRemoteKey")
            })
        }
        runCatching { sendWsTextFrame(out, msg.toString()) }
    }

    private fun sendLgButton(button: RemoteButton) {
        when (button) {
            RemoteButton.VOLUME_UP -> sendLgCommand("ssap://audio/volumeUp")
            RemoteButton.VOLUME_DOWN -> sendLgCommand("ssap://audio/volumeDown")
            RemoteButton.MUTE -> sendLgCommand("ssap://audio/setMute", JSONObject().put("mute", true))
            RemoteButton.CHANNEL_UP -> sendLgCommand("ssap://tv/channelUp")
            RemoteButton.CHANNEL_DOWN -> sendLgCommand("ssap://tv/channelDown")
            RemoteButton.POWER -> sendLgCommand("ssap://system/turnOff")
            RemoteButton.MEDIA_PLAY_PAUSE -> sendLgCommand("ssap://media.controls/play")
            RemoteButton.MEDIA_STOP -> sendLgCommand("ssap://media.controls/stop")
            RemoteButton.MEDIA_NEXT -> sendLgCommand("ssap://media.controls/fastForward")
            RemoteButton.MEDIA_PREVIOUS -> sendLgCommand("ssap://media.controls/rewind")
            RemoteButton.HOME -> sendLgPointerKey("HOME")
            RemoteButton.BACK -> sendLgPointerKey("BACK")
            RemoteButton.MENU -> sendLgPointerKey("MENU")
            RemoteButton.DPAD_UP -> sendLgPointerKey("UP")
            RemoteButton.DPAD_DOWN -> sendLgPointerKey("DOWN")
            RemoteButton.DPAD_LEFT -> sendLgPointerKey("LEFT")
            RemoteButton.DPAD_RIGHT -> sendLgPointerKey("RIGHT")
            RemoteButton.DPAD_CENTER, RemoteButton.ENTER -> sendLgPointerKey("ENTER")
            else -> Unit
        }
    }

    private fun sendLgCommand(uri: String, payload: JSONObject? = null) {
        val out = output ?: return
        val json = JSONObject().apply {
            put("id", "cmd_${lgMsgId++}")
            put("type", "request")
            put("uri", uri)
            if (payload != null) put("payload", payload)
        }
        runCatching { sendWsTextFrame(out, json.toString()) }
    }

    private fun sendLgPointerKey(name: String) {
        val pOut = lgPointerOut ?: return
        val frame = "type:button\nname:$name\n\n"
        runCatching { sendWsTextFrame(pOut, frame) }
    }

    fun disconnect() {
        readerJob?.cancel()
        readerJob = null
        runCatching { lgPointerSocket?.close() }
        lgPointerSocket = null
        lgPointerOut = null
        runCatching { socket?.close() }
        socket = null
        output = null
        _connectionStatus.value = ConnectionStatus.Disconnected
    }

    private fun samsungAppId(appId: String): String? = when (appId) {
        "youtube" -> "111299001912"
        "netflix" -> "11101200001"
        "prime" -> "3201512006785"
        "hotstar" -> "3201901017640"
        "spotify" -> "3201606009684"
        "sonyliv" -> "3201611011005"
        "zee5" -> "3201806016390"
        else -> null
    }

    private fun lgAppId(appId: String): String? = when (appId) {
        "youtube" -> "youtube.leanback.v4"
        "netflix" -> "netflix"
        "prime" -> "amazon"
        "hotstar" -> "in.startv.hotstar"
        "spotify" -> "spotify-beehive"
        "playstore" -> "com.webos.app.discovery"
        else -> null
    }

    private fun openWebSocket(host: String, port: Int, path: String, useTls: Boolean): Triple<Socket, InputStream, OutputStream> {
        val rawSocket: Socket = if (useTls) {
            val trustAll = arrayOf(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            })
            val ctx = SSLContext.getInstance("TLS").apply { init(null, trustAll, SecureRandom()) }
            (ctx.socketFactory.createSocket() as SSLSocket).apply {
                connect(InetSocketAddress(host, port), 4500)
                startHandshake()
            }
        } else {
            Socket().apply { connect(InetSocketAddress(host, port), 4500) }
        }

        val out = rawSocket.getOutputStream()
        val inp = rawSocket.getInputStream()
        val wsKey = Base64.encodeToString(ByteArray(16).apply { SecureRandom().nextBytes(this) }, Base64.NO_WRAP)
        val req = buildString {
            append("GET $path HTTP/1.1\r\n")
            append("Host: $host:$port\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Key: $wsKey\r\n")
            append("Sec-WebSocket-Version: 13\r\n\r\n")
        }
        out.write(req.toByteArray(Charsets.UTF_8))
        out.flush()

        val headerBuf = StringBuilder()
        var b: Int
        while (inp.read().also { b = it } != -1) {
            headerBuf.append(b.toChar())
            if (headerBuf.endsWith("\r\n\r\n")) break
        }
        if (!headerBuf.toString().contains("101")) {
            rawSocket.close()
            throw IllegalStateException("WebSocket handshake rejected")
        }
        return Triple(rawSocket, inp, out)
    }

    @Synchronized
    private fun sendWsTextFrame(out: OutputStream, text: String) {
        val payload = text.toByteArray(Charsets.UTF_8)
        val mask = ByteArray(4).apply { SecureRandom().nextBytes(this) }
        out.write(0x81) // FIN + text frame
        val len = payload.size
        when {
            len <= 125 -> out.write(0x80 or len)
            len <= 65535 -> {
                out.write(0x80 or 126)
                out.write((len ushr 8) and 0xFF)
                out.write(len and 0xFF)
            }
            else -> throw IllegalArgumentException("Payload too large")
        }
        out.write(mask)
        val masked = ByteArray(len) { i -> (payload[i].toInt() xor mask[i % 4].toInt()).toByte() }
        out.write(masked)
        out.flush()
    }

    private fun readWsTextFrame(inp: InputStream): String? {
        return try {
            val b1 = inp.read()
            if (b1 == -1) return null
            val b2 = inp.read()
            if (b2 == -1) return null
            val masked = (b2 and 0x80) != 0
            var len = b2 and 0x7F
            if (len == 126) {
                len = (inp.read() shl 8) or inp.read()
            } else if (len == 127) {
                var l = 0L
                repeat(8) { l = (l shl 8) or (inp.read().toLong() and 0xFF) }
                len = l.toInt()
            }
            val mask = if (masked) ByteArray(4).also { readFully(inp, it) } else null
            val data = ByteArray(len).also { readFully(inp, it) }
            if (mask != null) {
                for (i in data.indices) data[i] = (data[i].toInt() xor mask[i % 4].toInt()).toByte()
            }
            if ((b1 and 0x0F) == 0x08) return null
            String(data, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun readFully(inp: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = inp.read(buf, off, buf.size - off)
            if (n == -1) throw EOFException()
            off += n
        }
    }
}
