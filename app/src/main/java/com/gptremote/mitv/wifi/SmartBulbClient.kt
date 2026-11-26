package com.gptremote.mitv.wifi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

enum class BulbProtocol {
    PHILIPS_WIZ,
    YEELIGHT
}

data class DiscoveredSmartBulb(
    val host: String,
    val name: String,
    val protocol: BulbProtocol
)

/**
 * Local Wi-Fi Smart Bulb controller supporting Philips WiZ (UDP 38899) and Mi/Yeelight (TCP 55443).
 */
class SmartBulbClient {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _bulbs = MutableStateFlow<List<DiscoveredSmartBulb>>(emptyList())
    val bulbs: StateFlow<List<DiscoveredSmartBulb>> = _bulbs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectedBulb = MutableStateFlow<DiscoveredSmartBulb?>(null)
    val connectedBulb: StateFlow<DiscoveredSmartBulb?> = _connectedBulb.asStateFlow()

    fun discoverBulbs() {
        scope.launch {
            _isScanning.value = true
            val found = LinkedHashMap<String, DiscoveredSmartBulb>()
            var udp: DatagramSocket? = null
            try {
                udp = DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 700
                }
                // 1. Philips WiZ UDP discovery on port 38899
                val wizProbe = """{"method":"getPilot","params":{}}""".toByteArray(Charsets.UTF_8)
                runCatching {
                    udp.send(
                        DatagramPacket(wizProbe, wizProbe.size, InetAddress.getByName("255.255.255.255"), 38899)
                    )
                }
                val deadline = System.currentTimeMillis() + 2000L
                val buf = ByteArray(1024)
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val p = DatagramPacket(buf, buf.size)
                        udp.receive(p)
                        val host = p.address.hostAddress ?: continue
                        val txt = String(p.data, 0, p.length, Charsets.UTF_8)
                        if (txt.contains("result")) {
                            found[host] = DiscoveredSmartBulb(host, "WiZ Smart Bulb ($host)", BulbProtocol.PHILIPS_WIZ)
                            _bulbs.value = found.values.toList()
                        }
                    } catch (_: SocketTimeoutException) {
                    }
                }
            } catch (_: Exception) {
            } finally {
                runCatching { udp?.close() }
                _isScanning.value = false
            }
        }
    }

    fun selectManualBulb(host: String, brandId: String) {
        val clean = host.trim()
        if (clean.isEmpty()) return
        val proto = if (brandId == "bulb_yeelight") BulbProtocol.YEELIGHT else BulbProtocol.PHILIPS_WIZ
        _connectedBulb.value = DiscoveredSmartBulb(clean, "Smart Bulb ($clean)", proto)
    }

    fun selectBulb(bulb: DiscoveredSmartBulb) {
        _connectedBulb.value = bulb
    }

    fun disconnect() {
        _connectedBulb.value = null
    }

    fun setState(
        power: Boolean? = null,
        brightness: Int? = null,
        rgb: Triple<Int, Int, Int>? = null,
        tempKelvin: Int? = null,
        onError: (String) -> Unit = {}
    ) {
        val target = _connectedBulb.value ?: return
        scope.launch {
            runCatching {
                when (target.protocol) {
                    BulbProtocol.PHILIPS_WIZ -> {
                        val params = JSONObject().apply {
                            power?.let { put("state", it) }
                            brightness?.let { put("dimming", it.coerceIn(10, 100)) }
                            rgb?.let { (r, g, b) ->
                                put("r", r.coerceIn(0, 255))
                                put("g", g.coerceIn(0, 255))
                                put("b", b.coerceIn(0, 255))
                            }
                            tempKelvin?.let { put("temp", it.coerceIn(2200, 6500)) }
                        }
                        val cmd = JSONObject().put("method", "setPilot").put("params", params).toString()
                        val bytes = cmd.toByteArray(Charsets.UTF_8)
                        DatagramSocket().use { sock ->
                            sock.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(target.host), 38899))
                        }
                    }
                    BulbProtocol.YEELIGHT -> {
                        Socket().use { sock ->
                            sock.connect(InetSocketAddress(target.host, 55443), 2500)
                            val out = sock.getOutputStream()
                            val cmds = mutableListOf<String>()
                            power?.let {
                                val st = if (it) "on" else "off"
                                cmds += """{"id":1,"method":"set_power","params":["$st","smooth",300]}"""
                            }
                            brightness?.let {
                                cmds += """{"id":2,"method":"set_bright","params":[${it.coerceIn(1, 100)},"smooth",300]}"""
                            }
                            rgb?.let { (r, g, b) ->
                                val packed = (r shl 16) or (g shl 8) or b
                                cmds += """{"id":3,"method":"set_rgb","params":[$packed,"smooth",300]}"""
                            }
                            tempKelvin?.let {
                                cmds += """{"id":4,"method":"set_ct_abx","params":[${it.coerceIn(1700, 6500)},"smooth",300]}"""
                            }
                            for (c in cmds) {
                                out.write((c + "\r\n").toByteArray(Charsets.UTF_8))
                            }
                            out.flush()
                        }
                    }
                }
            }.onFailure { err ->
                onError("Bulb command failed: ${err.message ?: "check Wi-Fi IP"}")
            }
        }
    }
}
