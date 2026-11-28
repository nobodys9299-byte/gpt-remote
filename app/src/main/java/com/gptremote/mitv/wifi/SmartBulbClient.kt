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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

enum class BulbProtocol {
    PHILIPS_WIZ,
    YEELIGHT,
    TUYA_LAN,
    BLE_LIGHT,
    GOOGLE_HOME
}

data class DiscoveredSmartBulb(
    val host: String,
    val name: String,
    val protocol: BulbProtocol,
    val isBle: Boolean = false,
    val deviceId: String = ""
)

/**
 * Multi-protocol Smart Bulb discovery & controller supporting:
 * - Philips WiZ (UDP 38899)
 * - Mi / Yeelight (SSDP 239.255.255.250:1982 + TCP 55443)
 * - Tuya / Wipro / Syska / Havells Smart Bulbs (LAN UDP 6666 / 6667 beacons)
 * - Bluetooth LE Smart Bulbs (BluetoothLeScanner)
 */
class SmartBulbClient(
    context: Context,
    private val googleHomeClient: GoogleHomeClient? = null
) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = appContext.getSharedPreferences("gpt_smart_bulbs", Context.MODE_PRIVATE)
    private val seqCounter = AtomicInteger(1)
    private var sceneLoopJob: Job? = null

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
                                                val gwId = Regex("\"gwId\"\\s*:\\s*\"([^\"]+)\"")
                                                    .find(rawText)?.groupValues?.getOrNull(1).orEmpty()
                                                putBulb(
                                                    host,
                                                    DiscoveredSmartBulb(
                                                        host = host,
                                                        name = if (gwId.isNotBlank()) "Wipro / Tuya ($gwId)" else "Wipro / Syska / Tuya Bulb ($host)",
                                                        protocol = BulbProtocol.TUYA_LAN,
                                                        deviceId = gwId
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

                    // 3b. Active TCP 6668 (Tuya/Wipro LAN) & 55443 (Yeelight) subnet / ARP probe (works over Phone Hotspot!)
                    launch {
                        val candidates = collectLanCandidateHosts()
                        val sem = Semaphore(48)
                        val jobs = candidates.map { ip ->
                            async {
                                sem.withPermit {
                                    if (probeTcpPort(ip, 6668, 220)) {
                                        val savedId = prefs.getString("id_$ip", "").orEmpty()
                                        putBulb(
                                            ip,
                                            DiscoveredSmartBulb(
                                                host = ip,
                                                name = "Wipro / Tuya Wi-Fi Bulb ($ip)",
                                                protocol = BulbProtocol.TUYA_LAN,
                                                deviceId = savedId
                                            )
                                        )
                                    } else if (probeTcpPort(ip, 55443, 180)) {
                                        putBulb(
                                            ip,
                                            DiscoveredSmartBulb(
                                                host = ip,
                                                name = "Yeelight / Mi Bulb ($ip)",
                                                protocol = BulbProtocol.YEELIGHT
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        jobs.awaitAll()
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

    fun selectManualBulb(spec: String, brandId: String) {
        val parts = spec.split('|')
        val clean = parts.getOrNull(0)?.trim().orEmpty()
        if (clean.isEmpty()) return
        val devId = parts.getOrNull(1)?.trim().orEmpty()
        val localKey = parts.getOrNull(2)?.trim().orEmpty()
        if (devId.isNotEmpty()) prefs.edit().putString("id_$clean", devId).apply()
        if (localKey.length == 16) prefs.edit().putString("key_$clean", localKey).apply()

        val proto = when (brandId) {
            "bulb_yeelight" -> BulbProtocol.YEELIGHT
            "bulb_wipro", "bulb_syska", "bulb_tuya" -> BulbProtocol.TUYA_LAN
            else -> if (localKey.length == 16 || devId.isNotEmpty()) BulbProtocol.TUYA_LAN else BulbProtocol.PHILIPS_WIZ
        }
        val storedId = devId.ifEmpty { prefs.getString("id_$clean", "").orEmpty() }
        _connectedBulb.value = DiscoveredSmartBulb(clean, "Smart Bulb ($clean)", proto, deviceId = storedId)
    }

    fun selectBulb(bulb: DiscoveredSmartBulb) {
        val savedId = bulb.deviceId.ifBlank { prefs.getString("id_${bulb.host}", "").orEmpty() }
        if (savedId.isNotBlank()) prefs.edit().putString("id_${bulb.host}", savedId).apply()
        _connectedBulb.value = bulb.copy(deviceId = savedId)
    }

    fun disconnect() {
        sceneLoopJob?.cancel()
        sceneLoopJob = null
        _connectedBulb.value = null
    }

    fun stopSceneLoop() {
        sceneLoopJob?.cancel()
        sceneLoopJob = null
    }

    fun startSceneLoop(
        colors: List<Triple<Int, Int, Int>>,
        stepDelayMs: Long,
        onError: (String) -> Unit = {}
    ) {
        sceneLoopJob?.cancel()
        if (colors.isEmpty()) return
        val intervalMs = stepDelayMs.coerceIn(450L, 6000L)
        sceneLoopJob = scope.launch {
            var idx = 0
            while (isActive && _connectedBulb.value != null) {
                val c = colors[idx % colors.size]
                applyStateInternal(power = null, brightness = null, rgb = c, tempKelvin = null, onError = onError)
                idx++
                delay(intervalMs)
            }
        }
    }

    fun setState(
        power: Boolean? = null,
        brightness: Int? = null,
        rgb: Triple<Int, Int, Int>? = null,
        tempKelvin: Int? = null,
        onError: (String) -> Unit = {}
    ) {
        if (power == false || rgb != null || tempKelvin != null) {
            sceneLoopJob?.cancel()
            sceneLoopJob = null
        }
        scope.launch {
            applyStateInternal(power, brightness, rgb, tempKelvin, onError)
        }
    }

    private suspend fun applyStateInternal(
        power: Boolean? = null,
        brightness: Int? = null,
        rgb: Triple<Int, Int, Int>? = null,
        tempKelvin: Int? = null,
        onError: (String) -> Unit = {}
    ) {
        val target = _connectedBulb.value ?: return
        runCatching {
            when (target.protocol) {
                BulbProtocol.GOOGLE_HOME -> {
                    val gh = googleHomeClient
                        ?: throw IllegalStateException("Google Home client not initialized")
                    gh.applyLightState(
                        deviceId = target.deviceId,
                        power = power,
                        brightness = brightness,
                        rgb = rgb,
                        tempKelvin = tempKelvin
                    ).getOrThrow()
                }
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
                        val storedId = target.deviceId.ifBlank { prefs.getString("id_${target.host}", "").orEmpty() }
                        val storedKey = prefs.getString("key_${target.host}", "").orEmpty()
                        if (storedId.isBlank() || storedKey.length != 16) {
                            val idNote = if (storedId.isNotBlank()) " (ID: $storedId)" else ""
                            onError(
                                "Tuya/Wipro bulb detected at ${target.host}$idNote. Local LAN control requires your 16-char Tuya LocalKey — tap Connect -> Wi-Fi Bulb -> Advanced to enter it, or use WiZ / Yeelight / IR LED."
                            )
                            return@runCatching
                        }
                        val dps = JSONObject().apply {
                            power?.let { put("20", it) }
                            brightness?.let { put("22", (it.coerceIn(1, 100) * 10).coerceIn(10, 1000)) }
                            tempKelvin?.let {
                                put("21", "white")
                                put("23", ((it.coerceIn(2700, 6500) - 2700) * 1000 / 3800).coerceIn(0, 1000))
                            }
                            rgb?.let { (r, g, b) ->
                                put("21", "colour")
                                put("24", rgbToTuyaHsvHex(r, g, b))
                            }
                        }
                        if (dps.length() > 0) {
                            val tSeconds = System.currentTimeMillis() / 1000L
                            val payload = JSONObject()
                                .put("devId", storedId)
                                .put("uid", storedId)
                                .put("t", tSeconds.toString())
                                .put("dps", dps)
                                .toString()
                            val packet = buildTuya33Packet(seqCounter.getAndIncrement(), payload, storedKey)
                            Socket().use { sock ->
                                sock.connect(InetSocketAddress(target.host, 6668), 2500)
                                sock.soTimeout = 2500
                                sock.getOutputStream().use { out ->
                                    out.write(packet)
                                    out.flush()
                                }
                            }
                        }
                    }

                    BulbProtocol.BLE_LIGHT -> {
                        onError("BLE light '${target.name}' found. Sign in with Google Home in Settings to control Wipro/Tuya bulbs over Google Home cloud.")
                    }
                }
            }.onFailure { err ->
                onError("Bulb command failed: ${err.message ?: "check connection"}")
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

    private fun probeTcpPort(host: String, port: Int, timeoutMs: Int): Boolean {
        return runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        }.getOrDefault(false)
    }

    private fun collectLanCandidateHosts(): List<String> {
        val hosts = linkedSetOf<String>()
        val localIps = linkedSetOf<String>()
        // 1. Read ARP / neighbor table first (finds devices connected to Phone Hotspot immediately)
        runCatching {
            File("/proc/net/arp").takeIf { it.canRead() }?.useLines { lines ->
                lines.drop(1).forEach { line ->
                    val cols = line.trim().split(Regex("\\s+"))
                    val ip = cols.firstOrNull().orEmpty()
                    val flags = cols.getOrNull(2).orEmpty()
                    if (ip.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}")) && flags != "0x0" && !ip.startsWith("127.")) {
                        hosts += ip
                    }
                }
            }
        }
        // 2. Active Wi-Fi / Hotspot /24 subnet sweep
        runCatching {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
            for (iface in ifaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.interfaceAddresses) {
                    val inet = addr.address as? Inet4Address ?: continue
                    val ip = inet.hostAddress ?: continue
                    if (ip.startsWith("127.")) continue
                    localIps += ip
                    val parts = ip.split('.')
                    if (parts.size == 4) {
                        val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
                        for (oct in 1..254) {
                            val cand = "$prefix.$oct"
                            if (cand != ip) hosts += cand
                        }
                    }
                }
            }
        }
        hosts.removeAll(localIps)
        return hosts.toList()
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

        internal fun rgbToTuyaHsvHex(r: Int, g: Int, b: Int): String {
            val hsv = FloatArray(3)
            android.graphics.Color.RGBToHSV(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255), hsv)
            val h = hsv[0].toInt().coerceIn(0, 360)
            val s = (hsv[1] * 1000f).toInt().coerceIn(0, 1000)
            val v = (hsv[2] * 1000f).toInt().coerceIn(10, 1000)
            return String.format(Locale.US, "%04x%04x%04x", h, s, v)
        }

        internal fun buildTuya33Packet(seq: Int, payloadJson: String, localKey: String): ByteArray {
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            val keySpec = SecretKeySpec(localKey.toByteArray(Charsets.UTF_8), "AES")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)
            val encrypted = cipher.doFinal(payloadJson.toByteArray(Charsets.UTF_8))

            val header33 = ByteArray(15)
            header33[0] = '3'.code.toByte()
            header33[1] = '.'.code.toByte()
            header33[2] = '3'.code.toByte()
            val body = header33 + encrypted
            val lenField = body.size + 8

            val header = ByteBuffer.allocate(16).apply {
                putInt(0x000055AA)
                putInt(seq)
                putInt(0x00000007) // CONTROL v3.3
                putInt(lenField)
            }.array()

            val frameWithoutCrc = header + body
            val crc = CRC32().apply { update(frameWithoutCrc) }.value.toInt()
            return ByteBuffer.allocate(frameWithoutCrc.size + 8).apply {
                put(frameWithoutCrc)
                putInt(crc)
                putInt(0x0000AA55)
            }.array()
        }
    }
}
