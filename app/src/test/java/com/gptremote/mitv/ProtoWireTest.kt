package com.gptremote.mitv.wifi

import com.gptremote.mitv.ir.IrBlasterManager
import com.gptremote.mitv.model.ActiveSessionKind
import com.gptremote.mitv.model.ConnectionStatus
import com.gptremote.mitv.model.DeviceCategory
import com.gptremote.mitv.model.ProductCatalog
import com.gptremote.mitv.model.ProductType
import com.gptremote.mitv.model.RemoteUiState
import com.gptremote.mitv.model.TransferHistoryItem
import com.gptremote.mitv.model.TransferProgress
import com.gptremote.mitv.model.TvDevice
import com.gptremote.mitv.ui.theme.AppAccentTheme
import com.gptremote.mitv.wifi.TvCertificateManager.Companion.toUnsignedBigEndianBytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.math.BigInteger

class ProtoWireTest {

    @Test
    fun frameRoundTripPreservesPayload() {
        val payload = ProtoWire.Writer().apply {
            writeVarintField(1, 2)
            writeVarintField(2, 200)
            writeMessageField(10) {
                writeStringField(1, "com.gptremote.mitv")
                writeStringField(2, "Mi TV Remote")
            }
        }.toByteArray()

        val out = ByteArrayOutputStream()
        ProtoWire.writeFrame(out, payload)

        val decodedFrame = ProtoWire.readFrame(out.toByteArray().inputStream())
        assertArrayEquals(payload, decodedFrame)

        val fields = ProtoWire.parseFields(decodedFrame)
        assertEquals(2L, fields.first { it.number == 1 }.varint)
        assertEquals(200L, fields.first { it.number == 2 }.varint)
        val nested = ProtoWire.parseFields(fields.first { it.number == 10 }.bytes)
        assertEquals("com.gptremote.mitv", nested.first { it.number == 1 }.bytes.toString(Charsets.UTF_8))
        assertEquals("Mi TV Remote", nested.first { it.number == 2 }.bytes.toString(Charsets.UTF_8))
    }

    @Test
    fun keyInjectAndImeBatchEditEncodeExpectedFieldNumbers() {
        val keyMsg = WifiTvRemoteClient.buildKeyInject(keyCode = 19, direction = 3)
        val topFields = ProtoWire.parseFields(keyMsg)
        assertEquals(10, topFields.single().number)
        val nested = ProtoWire.parseFields(topFields.single().bytes)
        assertEquals(19L, nested.first { it.number == 1 }.varint)
        assertEquals(3L, nested.first { it.number == 2 }.varint)

        val imeMsg = WifiTvRemoteClient.buildImeBatchEdit(1, 2, "netflix")
        val imeTop = ProtoWire.parseFields(imeMsg)
        assertEquals(21, imeTop.single().number)

        val appLaunch = WifiTvRemoteClient.buildAppLinkLaunch("https://www.youtube.com")
        val appFields = ProtoWire.parseFields(appLaunch)
        assertEquals(90, appFields.single().number)
        val inner = ProtoWire.parseFields(appFields.single().bytes)
        assertEquals("https://www.youtube.com", inner.first { it.number == 1 }.bytes.toString(Charsets.UTF_8))
    }

    @Test
    fun irEncodersAndProductCatalogProduceValidStructures() {
        assertEquals(67, IrBlasterManager.buildNecAddressCmd(0x86, 0x0E).size)
        assertEquals(67, IrBlasterManager.buildSamsung32(0xE0E040BFL).size)
        assertEquals(26, IrBlasterManager.buildSonySirc12(0xA90).size)

        for (product in ProductType.entries) {
            assertTrue("Missing brands for $product", ProductCatalog.brandsFor(product).isNotEmpty())
        }
        assertTrue(ProductType.entries.contains(ProductType.SMART_BULB))
        assertTrue(ProductType.entries.contains(ProductType.PHONE_TO_PHONE))
        assertEquals(3, ProductCatalog.brandsFor(ProductType.PC_LAPTOP).size)
        assertTrue(ProductCatalog.tvApps.size >= 10)
        assertTrue(ProductCatalog.tvApps.all { it.shortBadge.isNotBlank() })
        assertTrue(ProductCatalog.brandsFor(ProductType.SMART_TV).all { it.supportsWifiTv })
        assertEquals(5, AppAccentTheme.entries.size)

        val miTv = TvDevice.BluetoothTv("EC:FA:5C:8F:67:40", "MiTV-AXS02", isBonded = true)
        val buds = TvDevice.BluetoothTv("98:47:44:C9:EB:F3", "realme Buds Wireless 3", isBonded = true)
        val car = TvDevice.BluetoothTv("A4:04:50:C3:D2:A4", "My Car", isBonded = true)
        assertTrue(miTv.matchesProduct(ProductType.SMART_TV))
        assertFalse(buds.matchesProduct(ProductType.SMART_TV))
        assertFalse(car.matchesProduct(ProductType.SMART_TV))
    }

    @Test
    fun unsignedBigEndianStripsLeadingZeroSignByte() {
        val highBit = BigInteger(1, byteArrayOf(0x80.toByte(), 0x01))
        assertEquals(3, highBit.toByteArray().size) // BigInteger adds leading 0x00
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0x01), highBit.toUnsignedBigEndianBytes())

        val lowBit = BigInteger(1, byteArrayOf(0x7F, 0x01))
        assertArrayEquals(byteArrayOf(0x7F, 0x01), lowBit.toUnsignedBigEndianBytes())
    }

    @Test
    fun pairingDigestProduces32ByteSha256() {
        val digest = TvCertificateManager.computePairingDigest(
            clientMod = byteArrayOf(1, 2, 3),
            clientExp = byteArrayOf(1, 0, 1),
            serverMod = byteArrayOf(4, 5, 6),
            serverExp = byteArrayOf(1, 0, 1),
            nonce = TvCertificateManager.hexToBytes("A1B2")
        )
        assertEquals(32, digest.size)
        assertTrue(digest.any { it != 0.toByte() })
    }

    @Test
    fun remoteUiStateResolvesConnectedBrandAndTransferFields() {
        val phoneBrand = ProductCatalog.brandsFor(ProductType.PHONE_TO_PHONE).first()
        val miTvBrand = ProductCatalog.brandsFor(ProductType.SMART_TV).first { it.id == "tv_xiaomi" }
        val tvDevice = TvDevice.BluetoothTv(
            id = "EC:FA:5C:8F:67:40",
            name = "MiTV-AXS02",
            isBonded = true,
            category = DeviceCategory.ANDROID_TV
        )
        // Even if user browsed Phone Share last, connectedBrandOption must resolve the connected Smart TV
        val stateWithTv = RemoteUiState(
            status = ConnectionStatus.Connected(tvDevice),
            selectedBrand = phoneBrand,
            connectedBrandId = miTvBrand.id
        )
        assertEquals("tv_xiaomi", stateWithTv.connectedBrandOption?.id)
        assertTrue(stateWithTv.isConnected)
        assertEquals("MiTV-AXS02", stateWithTv.connectedDeviceName)

        val pcDevice = TvDevice.WifiPc("10.0.0.5:58765", "Desktop", "10.0.0.5")
        val stateWithPc = RemoteUiState(
            status = ConnectionStatus.Connected(pcDevice),
            selectedBrand = phoneBrand,
            connectedBrandId = null
        )
        assertEquals(ProductType.PC_LAPTOP, stateWithPc.connectedBrandOption?.productType)

        val prog = TransferProgress("movie.mp4", 1024L, 2048L, isUpload = true, bytesPerSec = 512_000L)
        assertEquals(512_000L, prog.bytesPerSec)
        val hist = TransferHistoryItem("1", "movie.mp4", 2048L, isSent = true, bytesPerSec = 512_000L, timestampMs = 1L)
        assertEquals("movie.mp4", hist.fileName)
        assertTrue(BulbProtocol.entries.contains(BulbProtocol.TUYA_LAN))
        assertTrue(BulbProtocol.entries.contains(BulbProtocol.BLE_LIGHT))
    }

    @Test
    fun httpRangeParserAndDlnaSoapWorkCleanly() {
        assertEquals(0L to 499L, PhoneShareServer.parseByteRange("bytes=0-499", 1000L))
        assertEquals(200L to 999L, PhoneShareServer.parseByteRange("bytes=200-", 1000L))
        assertEquals(900L to 999L, PhoneShareServer.parseByteRange("bytes=-100", 1000L))
        assertEquals(500L to 999L, PhoneShareServer.parseByteRange("bytes=500-9999", 1000L))
        assertNull(PhoneShareServer.parseByteRange("bytes=1200-1300", 1000L))
        assertNull(PhoneShareServer.parseByteRange(null, 1000L))

        val sampleXml = """
            <?xml version="1.0"?>
            <root xmlns="urn:schemas-upnp-org:device-1-0">
              <device>
                <friendlyName>[TV] Samsung Crystal UHD</friendlyName>
                <manufacturer>Samsung Electronics</manufacturer>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                    <controlURL>/upnp/control/RenderingControl1</controlURL>
                  </service>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                    <controlURL>/upnp/control/AVTransport1</controlURL>
                  </service>
                </serviceList>
              </device>
            </root>
        """.trimIndent()

        val parsed = DlnaMediaCaster.parseDeviceDescription("http://192.168.1.45:9197/dmr", sampleXml)
        assertNotNull(parsed)
        assertEquals("[TV] Samsung Crystal UHD", parsed?.name)
        assertEquals("Samsung Electronics", parsed?.manufacturer)
        assertEquals("http://192.168.1.45:9197/upnp/control/AVTransport1", parsed?.controlUrl)

        val envelope = DlnaMediaCaster.buildSetAvTransportEnvelope(
            serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
            mediaUrl = "http://192.168.1.10:58770/view?id=1",
            title = "Holiday & Fun <4K>",
            mimeType = "video/mp4"
        )
        assertTrue(envelope.contains("u:SetAVTransportURI"))
        assertTrue(envelope.contains("Holiday &amp;amp; Fun &amp;lt;4K&amp;gt;"))
        assertTrue(envelope.contains("object.item.videoItem"))
    }

    @Test
    fun smartBulbFilteringAndTuya33PacketFraming() {
        val tv = TvDevice.BluetoothTv("11:22:33:44:55:66", "MiTV-AXS02", isBonded = true)
        val car = TvDevice.BluetoothTv("22:33:44:55:66:77", "My Car", isBonded = true)
        val buds = TvDevice.BluetoothTv("33:44:55:66:77:88", "realme Buds Wireless 3", isBonded = true)
        val bulb = TvDevice.BluetoothTv("44:55:66:77:88:99", "Wipro Smart Bulb", isBonded = true)
        assertFalse(tv.matchesProduct(ProductType.SMART_BULB))
        assertFalse(car.matchesProduct(ProductType.SMART_BULB))
        assertFalse(buds.matchesProduct(ProductType.SMART_BULB))
        assertTrue(bulb.matchesProduct(ProductType.SMART_BULB))

        val pkt = SmartBulbClient.buildTuya33Packet(
            seq = 7,
            payloadJson = """{"devId":"bf1234","dps":{"20":true}}""",
            localKey = "0123456789abcdef"
        )
        assertTrue(pkt.size > 32)
        // Prefix 00 00 55 AA
        assertEquals(0x00.toByte(), pkt[0])
        assertEquals(0x00.toByte(), pkt[1])
        assertEquals(0x55.toByte(), pkt[2])
        assertEquals(0xAA.toByte(), pkt[3])
        // Tuya 3.3 tag at offset 16..18
        assertEquals('3'.code.toByte(), pkt[16])
        assertEquals('.'.code.toByte(), pkt[17])
        assertEquals('3'.code.toByte(), pkt[18])
        // Suffix 00 00 AA 55
        assertEquals(0x00.toByte(), pkt[pkt.size - 4])
        assertEquals(0x00.toByte(), pkt[pkt.size - 3])
        assertEquals(0xAA.toByte(), pkt[pkt.size - 2])
        assertEquals(0x55.toByte(), pkt[pkt.size - 1])
    }

    @Test
    fun multiSessionRoutingListsIndependentPcBtPhoneAndBulbSessions() {
        val pcDevice = TvDevice.WifiPc("192.168.1.5:58765", "DESKTOP-JLDTF6M", "192.168.1.5")
        val btTablet = TvDevice.BluetoothTv("AA:BB:CC:DD:EE:FF", "TB328XU", isBonded = true)
        val bulb = DiscoveredSmartBulb("192.168.1.60", "Wipro Bulb", BulbProtocol.TUYA_LAN, deviceId = "dev123")
        val state = RemoteUiState(
            status = ConnectionStatus.Connected(pcDevice),
            connectedWifiPcDevice = pcDevice,
            connectedBtDevice = btTablet,
            peerPhoneName = "Phone B",
            connectedBulb = bulb
        )
        assertEquals(ProductType.PC_LAPTOP, state.connectedBrandOption?.productType)
        assertEquals("DESKTOP-JLDTF6M", state.connectedDeviceName)
        assertEquals(4, state.activeSessions.size)
        assertEquals(ActiveSessionKind.WIFI_PC_REMOTE, state.activeSessions[0].kind)
        assertEquals(ActiveSessionKind.BLUETOOTH_REMOTE, state.activeSessions[1].kind)
        assertEquals(ActiveSessionKind.PHONE_PEER, state.activeSessions[2].kind)
        assertEquals(ActiveSessionKind.SMART_BULB, state.activeSessions[3].kind)
    }
}
