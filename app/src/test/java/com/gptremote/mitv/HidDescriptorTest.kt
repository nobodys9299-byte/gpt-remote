package com.gptremote.mitv.bluetooth

import com.gptremote.mitv.model.MouseButton
import com.gptremote.mitv.model.RemoteButton
import com.gptremote.mitv.wifi.WifiPcRemoteClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HidDescriptorTest {

    @Test
    fun descriptorHasBalancedCollections() {
        val desc = HidDescriptor.DESCRIPTOR
        var collections = 0
        var endCollections = 0
        var i = 0
        while (i < desc.size) {
            val tag = desc[i].toInt() and 0xFF
            if (tag == 0xA1) collections++
            if (tag == 0xC0) endCollections++
            val sizeCode = tag and 0x03
            val dataLen = when (sizeCode) {
                0 -> 0
                1 -> 1
                2 -> 2
                else -> 4
            }
            i += 1 + dataLen
        }
        assertEquals(desc.size, i)
        assertTrue(collections >= 4)
        assertEquals(collections, endCollections)
    }

    @Test
    fun keyboardAndConsumerReportsHaveExpectedBytes() {
        val enterReport = HidDescriptor.keyboardReport(0x28)
        assertArrayEquals(byteArrayOf(0, 0, 0x28, 0, 0, 0, 0, 0), enterReport)

        val homeReport = HidDescriptor.consumerReport(0x0223)
        assertArrayEquals(byteArrayOf(0x23, 0x02), homeReport)
    }

    @Test
    fun mouseReportClampsDeltasAndEncodesButtons() {
        val report = HidDescriptor.mouseReport(
            buttonsMask = HidDescriptor.mouseButtonBit(MouseButton.LEFT) or HidDescriptor.mouseButtonBit(MouseButton.RIGHT),
            dx = 250,
            dy = -250,
            wheel = -1
        )
        assertArrayEquals(byteArrayOf(0x03, 127, (-127).toByte(), (-1).toByte()), report)
    }

    @Test
    fun allRemoteButtonsMapToValidHidAction() {
        for (button in RemoteButton.entries) {
            val tvAction = HidDescriptor.buttonToHidAction(button, isPcMode = false)
            val pcAction = HidDescriptor.buttonToHidAction(button, isPcMode = true)
            assertNotNull("Missing TV mapping for $button", tvAction)
            assertNotNull("Missing PC mapping for $button", pcAction)
        }
        assertEquals(HidDescriptor.HidAction.Consumer(0x0041), HidDescriptor.buttonToHidAction(RemoteButton.DPAD_CENTER, isPcMode = false))
        assertEquals(HidDescriptor.HidAction.Keyboard(0x28), HidDescriptor.buttonToHidAction(RemoteButton.DPAD_CENTER, isPcMode = true))
        assertEquals(HidDescriptor.HidAction.Consumer(0x0183), HidDescriptor.buttonToHidAction(RemoteButton.SETTINGS, isPcMode = false))
        assertEquals(HidDescriptor.HidAction.Consumer(0x0089), HidDescriptor.buttonToHidAction(RemoteButton.TV_INPUT, isPcMode = false))
        assertEquals("alt_tab", WifiPcRemoteClient.buttonToPcKeyName(RemoteButton.PC_ALT_TAB))
        assertEquals("desktop", WifiPcRemoteClient.buttonToPcKeyName(RemoteButton.PC_SHOW_DESKTOP))
    }

    @Test
    fun charToHidKeyMapsAsciiAndRejectsUnsupportedChars() {
        assertEquals(0x04 to 0.toByte(), HidDescriptor.charToHidKey('a'))
        assertEquals(0x04 to HidDescriptor.MOD_LEFT_SHIFT, HidDescriptor.charToHidKey('A'))
        assertEquals(0x2C to 0.toByte(), HidDescriptor.charToHidKey(' '))
        assertEquals(0x1F to HidDescriptor.MOD_LEFT_SHIFT, HidDescriptor.charToHidKey('@'))
        assertNull(HidDescriptor.charToHidKey('€'))
    }
}
