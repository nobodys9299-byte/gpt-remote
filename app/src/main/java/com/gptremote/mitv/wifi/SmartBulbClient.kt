package com.gptremote.mitv.wifi

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException

enum class BulbProtocol {
    PHILIPS_WIZ,
    YEELIGHT,
    TUYA_LAN,
    BLE_LIGHT
}

data class DiscoveredSmartBulb(
    val host: String,
    val name: String,
    val protocol: BulbProtocol,
    val isBle: Boolean = false
)

/**
 * Multi-protocol Smart Bulb discovery & controller supporting:
 * - Philips WiZ (UDP 38899)
 * - Mi / Yeelight (SSDP 239.255.255.250:1982 + TCP 55443)
 * - Tuya / Wipro / Syska / Havells Smart Bulbs (LAN UDP 6666 / 6667 beacons)
 * - Bluetooth LE Smart Bulbs (BluetoothLeScanner)
 */
class SmartBulbClient(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _bulbs = MutableStateFlow<List<DiscoveredSmartBulb>>(emptyList())
    val bulbs: StateFlow<List<DiscoveredSmartBulb>> = _bulbs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectedBulb = MutableStateFlow<DiscoveredSmartBulb?>(null)
    val connectedBulb: StateFlow<DiscoveredSmartBulb?> = _connectedBulb.asStateFlow()

    @SuppressLint("MissingPermission")
    fun discoverBulbs() {
        scope.launch {
            _isScanning.value = true
            val found = LinkedHashMap<String, DiscoveredSmartBulb>()
            fun publish() {
                synchronized(found) {
                    _bulbs.value = found.values.toList()
                }
            }
            fun putBulb(key: String, bulb: DiscoveredSmartBulb) {
                synchronized(found) {
                    found[key] = bulb
                }
                publish()
            }

            try {
                coroutineScope {
                    // 1. Philips WiZ UDP broadcast probe on 38899 (+ subnet broadcast fallback)
                    launch {
                        var udp: DatagramSocket? = null
                        try {
                            udp = DatagramSocket().apply {
                                broadcast = true
                                soTimeout = 500
                            }
                            val wizProbe = """{"method":"getPilot","params":{}}""".toByteArray(Charsets.UTF_8)
                            val bcasts = collectBroadcastAddresses()
                            for (addr in bcasts) {
                                runCatching { udp.send(DatagramPacket(wizProbe, wizProbe.size, addr, 38899)) }
                            }
                            val deadline = System.currentTimeMillis() + 2600L
                            val buf = ByteArray(1024)
                            while (System.currentTimeMillis() < deadline) {
                                try {
                                    val p = DatagramPacket(buf, buf.size)
                                    udp.receive(p)
                                    val host = p.address.hostAddress ?: continue
                                    val txt = String(p.data, 0, p.length, Charsets.UTF_8)
                                    if (txt.contains("result") || txt.contains("mac")) {
                                        putBulb(host, DiscoveredSmartBulb(host, "WiZ Bulb ($host)", BulbProtocol.PHILIPS_WIZ))
                                    }
                                } catch (_: SocketTimeoutException) {
                                }
                            }
                        } catch (_: Exception) {
                        } finally {
                            runCatching { udp?.close() }
                        }
                    }

                    // 2. Mi / Yeelight SSDP multicast probe on 239.255.255.250:1982
                    launch {
                        var ssdp: DatagramSocket? = null
                        try {
                            ssdp = DatagramSocket().apply { soTimeout = 500 }
                            val msg = (
                                "M-SEARCH * HTTP/1.1\r\n" +
                                    "HOST: 239.255.255.250:1982\r\n" +
                                    "MAN: \"ssdp:discover\"\r\n" +
                                    "ST: wifi_bulb\r\n\r\n"
                                ).toByteArray(Charsets.UTF_8)
                            runCatching {
                                ssdp.send(DatagramPacket(msg, msg.size, InetAddress.getByName("239.255.255.250"), 1982))
                            }
                            val deadline = System.currentTimeMillis() + 2600L
                            val buf = ByteArray(2048)
                            while (System.currentTimeMillis() < deadline) {
                                try {
                                    val p = DatagramPacket(buf, buf.size)
                                    ssdp.receive(p)
                                    val host = p.address.hostAddress ?: continue
                                    val txt = String(p.data, 0, p.length, Charsets.UTF_8)
                                    if (txt.contains("yeelight", ignoreCase = true) || txt.contains("wifi_bulb", ignoreCase = true)) {
                                        putBulb(host, DiscoveredSmartBulb(host, "Yeelight / Mi Bulb ($host)", BulbProtocol.YEELIGHT))
                                    }
                                } catch (_: SocketTimeoutException) {
                                }
                            }
                        } catch (_: Exception) {
                        } finally {
                            runCatching { ssdp?.close() }
                        }
                    }

                    // 3. Tuya / Wipro / Syska / Havells LAN UDP beacon listener (ports 6666 & 6667)
                    for (tuyaPort in listOf(6666, 6667)) {
                        launch {
                            var sock: DatagramSocket? = null
                            try {
                                sock = DatagramSocket(tuyaPort).apply {
                                    reuseAddress = true
                                    broadcast = true
                                    soTimeout = 500
                                }
                                val deadline = System.currentTimeMillis() + 2600L
                                val buf = ByteArray(2048)
                                while (System.currentTimeMillis() < deadline) {
                                    try {
                                        val p = DatagramPacket(buf, buf.size)
                                        sock.receive(p)
                                        val host = p.address.hostAddress ?: continue
                                        if (p.length >= 16) {
                                            val rawText = String(p.data, 0, p.length, Charsets.UTF_8)
                                            val hasTuyaMagic = (p.data[0].toInt() == 0x00 && p.data[1].toInt() == 0x00 &&
                                                (p.data[2].toInt() == 0x55 || p.data[2].toInt() == 0x66))
                                            val looksTuyaJson = rawText.contains("gwId") || rawText.contains("productKey")
                                            if (hasTuyaMagic || looksTuyaJson) {
                                                putBulb(
                                                    host,
                                                    DiscoveredSmartBulb(
                                                        host = host,
                                                        name = "Wipro / Syska / Tuya Wi-Fi Bulb ($host)",
                                                        protocol = BulbProtocol.TUYA_LAN
                                                    )
                                                )
                                            }
                                        }
                                    } catch (_: SocketTimeoutException) {
                                    }
                                }
                            } catch (_: Exception) {
                            } finally {
                                runCatching { sock?.close() }
                            }
                        }
                    }

                    // 4. Bluetooth LE Smart Bulb scanner (Wipro, Syska, WiZ, Yeelight, Tapo, LED lamps)
                    launch {
                        if (!hasBleScanPermission()) return@launch
                        val mgr = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                        val adapter = mgr?.adapter
                        val scanner = if (adapter?.isEnabled == true) adapter.bluetoothLeScanner else null
                        if (scanner == null) return@launch

                        val callback = object : ScanCallback() {
                            override fun onScanResult(callbackType: Int, result: ScanResult) {
                                val dev = result.device ?: return
                                val addr = dev.address ?: return
                                val advName = runCatching { result.scanRecord?.deviceName ?: dev.name }.getOrNull().orEmpty().trim()
                                val hasBulbName = BLE_BULB_KEYWORDS.any { advName.contains(it, ignoreCase = true) }
                                val serviceUuids = result.scanRecord?.serviceUuids.orEmpty().map { it.uuid.toString().lowercase() }
                                val hasLightUuid = serviceUuids.any {
                                    it.startsWith("0000a201") || it.startsWith("0000fd50") || it.startsWith("0000fe95")
                                }
                                if (hasBulbName || hasLightUuid) {
                                    val label = if (advName.isNotBlank()) "$advName (BLE)" else "Smart BLE Bulb ($addr)"
                                    putBulb(
                                        addr,
                                        DiscoveredSmartBulb(
                                            host = addr,
                                            name = label,
                                            protocol = BulbProtocol.BLE_LIGHT,
                                            isBle = true
                                        )
                                    )
                                }
                            }
                        }

                        runCatching {
                            val settings = ScanSettings.Builder()
                                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                                .build()
                            scanner.startScan(null, settings, callback)
                            delay(2600L)
                        }
                        runCatching { scanner.stopScan(callback) }
                    }
                }
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun selectManualBulb(host: String, brandId: String) {
        val clean = host.trim()
        if (clean.isEmpty()) return
        val proto = when (brandId) {
            "bulb_yeelight" -> BulbProtocol.YEELIGHT
            "bulb_wipro", "bulb_syska", "bulb_tuya" -> BulbProtocol.TUYA_LAN
            else -> BulbProtocol.PHILIPS_WIZ
        }
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

                    BulbProtocol.TUYA_LAN -> {
                        onError("Tuya/Wipro LAN bulb detected at ${target.host}. Encrypted v3.3+ local control requires your Smart Life/Wipro localKey; use WiZ, Yeelight (LAN Control ON), or IR LED strip.")
                    }

                    BulbProtocol.BLE_LIGHT -> {
                        onError("BLE light '${target.name}' found. Proprietary BLE-Mesh encryption requires pairing in its vendor app, or use a WiZ / Yeelight / IR light.")
                    }
                }
            }.onFailure { err ->
                onError("Bulb command failed: ${err.message ?: "check Wi-Fi IP"}")
            }
        }
    }

    private fun hasBleScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    appContext,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun collectBroadcastAddresses(): List<InetAddress> {
        val list = linkedSetOf<InetAddress>()
        runCatching { list += InetAddress.getByName("255.255.255.255") }
        runCatching {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
            for (iface in ifaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.interfaceAddresses) {
                    val inet = addr.address as? Inet4Address ?: continue
                    val host = inet.hostAddress ?: continue
                    if (host.startsWith("127.")) continue
                    addr.broadcast?.let { list += it }
                    val parts = host.split('.')
                    if (parts.size == 4) {
                        runCatching {
                            list += InetAddress.getByName("${parts[0]}.${parts[1]}.${parts[2]}.255")
                        }
                    }
                }
            }
        }
        return list.toList()
    }

    companion object {
        private val BLE_BULB_KEYWORDS = listOf(
            "wipro", "syska", "wiz", "yeelight", "tapo", "philips",
            "halonix", "bajaj", "havells", "tuya", "bulb", "lamp", "light", "mesh"
        )
    }
}
