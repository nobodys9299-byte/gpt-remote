package com.gptremote.mitv.wifi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL

data class DiscoveredDlnaTv(
    val id: String,
    val name: String,
    val manufacturer: String,
    val host: String,
    val controlUrl: String,
    val serviceType: String
)

/**
 * Discovers non-Android Smart TVs (Samsung Tizen, LG webOS, Hisense VIDAA, Sony, etc.)
 * via UPnP / SSDP MediaRenderer discovery and pushes photos, videos, or audio over DLNA AVTransport.
 */
class DlnaMediaCaster {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _renderers = MutableStateFlow<List<DiscoveredDlnaTv>>(emptyList())
    val renderers: StateFlow<List<DiscoveredDlnaTv>> = _renderers.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _activeRenderer = MutableStateFlow<DiscoveredDlnaTv?>(null)
    val activeRenderer: StateFlow<DiscoveredDlnaTv?> = _activeRenderer.asStateFlow()

    fun discoverRenderers() {
        scope.launch {
            _isScanning.value = true
            val found = LinkedHashMap<String, DiscoveredDlnaTv>()
            val seenLocations = HashSet<String>()
            var sock: DatagramSocket? = null
            try {
                sock = DatagramSocket().apply { soTimeout = 550 }
                val group = InetAddress.getByName("239.255.255.250")
                val searchTargets = listOf(
                    "urn:schemas-upnp-org:device:MediaRenderer:1",
                    "urn:schemas-upnp-org:service:AVTransport:1"
                )
                for (st in searchTargets) {
                    val probe = (
                        "M-SEARCH * HTTP/1.1\r\n" +
                            "HOST: 239.255.255.250:1900\r\n" +
                            "MAN: \"ssdp:discover\"\r\n" +
                            "MX: 2\r\n" +
                            "ST: $st\r\n\r\n"
                        ).toByteArray(Charsets.UTF_8)
                    runCatching { sock.send(DatagramPacket(probe, probe.size, group, 1900)) }
                }

                val deadline = System.currentTimeMillis() + 2800L
                val buf = ByteArray(2048)
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        sock.receive(packet)
                        val resp = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val location = resp.lines()
                            .firstOrNull { it.trim().startsWith("LOCATION:", ignoreCase = true) }
                            ?.substringAfter(':')
                            ?.trim()
                            ?: continue
                        if (!location.startsWith("http", ignoreCase = true)) continue
                        if (!seenLocations.add(location)) continue

                        val xml = runCatching { fetchText(location) }.getOrNull() ?: continue
                        val parsed = parseDeviceDescription(location, xml) ?: continue
                        found[parsed.id] = parsed
                        _renderers.value = found.values.sortedBy { it.name.lowercase() }
                    } catch (_: SocketTimeoutException) {
                    }
                }
            } catch (_: Exception) {
            } finally {
                runCatching { sock?.close() }
                _isScanning.value = false
            }
        }
    }

    fun selectRenderer(renderer: DiscoveredDlnaTv) {
        _activeRenderer.value = renderer
    }

    fun castMedia(
        renderer: DiscoveredDlnaTv,
        mediaUrl: String,
        title: String,
        mimeType: String,
        onStatus: (String) -> Unit
    ) {
        _activeRenderer.value = renderer
        scope.launch {
            runCatching {
                val setUriXml = buildSetAvTransportEnvelope(
                    serviceType = renderer.serviceType,
                    mediaUrl = mediaUrl,
                    title = title,
                    mimeType = mimeType
                )
                postSoapAction(
                    controlUrl = renderer.controlUrl,
                    soapAction = "\"${renderer.serviceType}#SetAVTransportURI\"",
                    bodyXml = setUriXml
                )
                val playXml = buildSimpleTransportEnvelope(
                    serviceType = renderer.serviceType,
                    action = "Play",
                    extraTag = "<Speed>1</Speed>"
                )
                postSoapAction(
                    controlUrl = renderer.controlUrl,
                    soapAction = "\"${renderer.serviceType}#Play\"",
                    bodyXml = playXml
                )
            }.onSuccess {
                onStatus("Playing '$title' on ${renderer.name}")
            }.onFailure { err ->
                onStatus("TV DLNA playback failed: ${err.message ?: "format not supported by TV"}")
            }
        }
    }

    fun sendPlaybackCommand(action: String, onError: (String) -> Unit = {}) {
        val renderer = _activeRenderer.value ?: return
        scope.launch {
            runCatching {
                val extra = if (action == "Play") "<Speed>1</Speed>" else ""
                val xml = buildSimpleTransportEnvelope(renderer.serviceType, action, extra)
                postSoapAction(
                    controlUrl = renderer.controlUrl,
                    soapAction = "\"${renderer.serviceType}#$action\"",
                    bodyXml = xml
                )
            }.onFailure { err ->
                onError("DLNA $action failed: ${err.message ?: "TV unreachable"}")
            }
        }
    }

    private fun fetchText(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 2500
            readTimeout = 2500
        }
        return try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun postSoapAction(controlUrl: String, soapAction: String, bodyXml: String) {
        val bytes = bodyXml.toByteArray(Charsets.UTF_8)
        val conn = (URL(controlUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 3500
            readTimeout = 4500
            doOutput = true
            setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            setRequestProperty("SOAPACTION", soapAction)
            setFixedLengthStreamingMode(bytes.size)
        }
        try {
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val errBody = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
                throw IllegalStateException("HTTP $code ${errBody.take(120)}".trim())
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        fun parseDeviceDescription(baseLocationUrl: String, xml: String): DiscoveredDlnaTv? {
            return runCatching {
                val factory = DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = false
                }
                val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
                fun firstTag(name: String): String {
                    val nodes = doc.getElementsByTagName(name)
                    if (nodes.length == 0) return ""
                    return nodes.item(0).textContent?.trim().orEmpty()
                }
                val friendlyName = firstTag("friendlyName")
                val manufacturer = firstTag("manufacturer")

                val services = doc.getElementsByTagName("service")
                var avServiceType = ""
                var avControlUrl = ""
                for (i in 0 until services.length) {
                    val el = services.item(i) as? Element ?: continue
                    val st = el.getElementsByTagName("serviceType").item(0)?.textContent?.trim().orEmpty()
                    val cu = el.getElementsByTagName("controlURL").item(0)?.textContent?.trim().orEmpty()
                    if (st.contains("AVTransport", ignoreCase = true) && cu.isNotBlank()) {
                        avServiceType = st
                        avControlUrl = cu
                        break
                    }
                }

                if (avControlUrl.isBlank()) return@runCatching null
                val baseUrl = URL(baseLocationUrl)
                val resolvedControl = URL(baseUrl, avControlUrl).toString()
                val host = baseUrl.host.orEmpty()
                val label = friendlyName.ifBlank { "Smart TV ($host)" }
                DiscoveredDlnaTv(
                    id = resolvedControl,
                    name = label,
                    manufacturer = manufacturer.ifBlank { "Smart TV" },
                    host = host,
                    controlUrl = resolvedControl,
                    serviceType = avServiceType.ifBlank { "urn:schemas-upnp-org:service:AVTransport:1" }
                )
            }.getOrNull()
        }

        fun buildSetAvTransportEnvelope(
            serviceType: String,
            mediaUrl: String,
            title: String,
            mimeType: String
        ): String {
            val upnpClass = when {
                mimeType.startsWith("image/") -> "object.item.imageItem.photo"
                mimeType.startsWith("audio/") -> "object.item.audioItem.musicTrack"
                else -> "object.item.videoItem"
            }
            val didl = (
                "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
                    "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
                    "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
                    "<item id=\"0\" parentID=\"-1\" restricted=\"1\">" +
                    "<dc:title>${xmlEscape(title)}</dc:title>" +
                    "<upnp:class>$upnpClass</upnp:class>" +
                    "<res protocolInfo=\"http-get:*:${xmlEscape(mimeType)}:*\">${xmlEscape(mediaUrl)}</res>" +
                    "</item></DIDL-Lite>"
                )
            return (
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                    "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                    "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                    "<s:Body>" +
                    "<u:SetAVTransportURI xmlns:u=\"${xmlEscape(serviceType)}\">" +
                    "<InstanceID>0</InstanceID>" +
                    "<CurrentURI>${xmlEscape(mediaUrl)}</CurrentURI>" +
                    "<CurrentURIMetaData>${xmlEscape(didl)}</CurrentURIMetaData>" +
                    "</u:SetAVTransportURI>" +
                    "</s:Body></s:Envelope>"
                )
        }

        fun buildSimpleTransportEnvelope(
            serviceType: String,
            action: String,
            extraTag: String = ""
        ): String {
            return (
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                    "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                    "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                    "<s:Body>" +
                    "<u:${xmlEscape(action)} xmlns:u=\"${xmlEscape(serviceType)}\">" +
                    "<InstanceID>0</InstanceID>" +
                    extraTag +
                    "</u:${xmlEscape(action)}>" +
                    "</s:Body></s:Envelope>"
                )
        }

        private fun xmlEscape(s: String): String = s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
