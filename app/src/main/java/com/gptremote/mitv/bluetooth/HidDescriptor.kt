package com.gptremote.mitv.bluetooth

import com.gptremote.mitv.model.MouseButton
import com.gptremote.mitv.model.RemoteButton

/**
 * Composite USB/Bluetooth HID Report Descriptor supporting:
 * - Report ID 1: Standard Keyboard (8-byte report)
 * - Report ID 2: Consumer Control (2-byte 16-bit usage report)
 * - Report ID 3: Relative Mouse / Touchpad (4-byte report)
 */
object HidDescriptor {
    const val REPORT_ID_KEYBOARD: Int = 1
    const val REPORT_ID_CONSUMER: Int = 2
    const val REPORT_ID_MOUSE: Int = 3

    const val MOD_LEFT_SHIFT: Byte = 0x02

    val DESCRIPTOR: ByteArray = byteArrayOf(
        // ---- Keyboard (Report ID 1) ----
        0x05.toByte(), 0x01.toByte(),       // Usage Page (Generic Desktop)
        0x09.toByte(), 0x06.toByte(),       // Usage (Keyboard)
        0xA1.toByte(), 0x01.toByte(),       // Collection (Application)
        0x85.toByte(), REPORT_ID_KEYBOARD.toByte(), // Report ID (1)
        0x05.toByte(), 0x07.toByte(),       //   Usage Page (Keyboard/Keypad)
        0x19.toByte(), 0xE0.toByte(),       //   Usage Minimum (Left Control)
        0x29.toByte(), 0xE7.toByte(),       //   Usage Maximum (Right GUI)
        0x15.toByte(), 0x00.toByte(),       //   Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(),       //   Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(),       //   Report Size (1)
        0x95.toByte(), 0x08.toByte(),       //   Report Count (8)
        0x81.toByte(), 0x02.toByte(),       //   Input (Data, Var, Abs)
        0x95.toByte(), 0x01.toByte(),       //   Report Count (1)
        0x75.toByte(), 0x08.toByte(),       //   Report Size (8)
        0x81.toByte(), 0x01.toByte(),       //   Input (Const, Array, Abs)
        0x95.toByte(), 0x05.toByte(),       //   Report Count (5)
        0x75.toByte(), 0x01.toByte(),       //   Report Size (1)
        0x05.toByte(), 0x08.toByte(),       //   Usage Page (LEDs)
        0x19.toByte(), 0x01.toByte(),       //   Usage Minimum (Num Lock)
        0x29.toByte(), 0x05.toByte(),       //   Usage Maximum (Kana)
        0x91.toByte(), 0x02.toByte(),       //   Output (Data, Var, Abs)
        0x95.toByte(), 0x01.toByte(),       //   Report Count (1)
        0x75.toByte(), 0x03.toByte(),       //   Report Size (3)
        0x91.toByte(), 0x01.toByte(),       //   Output (Const, Array, Abs)
        0x95.toByte(), 0x06.toByte(),       //   Report Count (6)
        0x75.toByte(), 0x08.toByte(),       //   Report Size (8)
        0x15.toByte(), 0x00.toByte(),       //   Logical Minimum (0)
        0x26.toByte(), 0xFF.toByte(), 0x00.toByte(), // Logical Maximum (255)
        0x05.toByte(), 0x07.toByte(),       //   Usage Page (Keyboard/Keypad)
        0x19.toByte(), 0x00.toByte(),       //   Usage Minimum (0)
        0x2A.toByte(), 0xFF.toByte(), 0x00.toByte(), // Usage Maximum (255)
        0x81.toByte(), 0x00.toByte(),       //   Input (Data, Array, Abs)
        0xC0.toByte(),                      // End Collection

        // ---- Consumer Control (Report ID 2) ----
        0x05.toByte(), 0x0C.toByte(),       // Usage Page (Consumer)
        0x09.toByte(), 0x01.toByte(),       // Usage (Consumer Control)
        0xA1.toByte(), 0x01.toByte(),       // Collection (Application)
        0x85.toByte(), REPORT_ID_CONSUMER.toByte(), // Report ID (2)
        0x15.toByte(), 0x00.toByte(),       //   Logical Minimum (0)
        0x26.toByte(), 0xFF.toByte(), 0x03.toByte(), // Logical Maximum (0x03FF)
        0x19.toByte(), 0x00.toByte(),       //   Usage Minimum (0)
        0x2A.toByte(), 0xFF.toByte(), 0x03.toByte(), // Usage Maximum (0x03FF)
        0x75.toByte(), 0x10.toByte(),       //   Report Size (16)
        0x95.toByte(), 0x01.toByte(),       //   Report Count (1)
        0x81.toByte(), 0x00.toByte(),       //   Input (Data, Array, Abs)
        0xC0.toByte(),                      // End Collection

        // ---- Relative Mouse / Touchpad (Report ID 3) ----
        0x05.toByte(), 0x01.toByte(),       // Usage Page (Generic Desktop)
        0x09.toByte(), 0x02.toByte(),       // Usage (Mouse)
        0xA1.toByte(), 0x01.toByte(),       // Collection (Application)
        0x85.toByte(), REPORT_ID_MOUSE.toByte(), // Report ID (3)
        0x09.toByte(), 0x01.toByte(),       //   Usage (Pointer)
        0xA1.toByte(), 0x00.toByte(),       //   Collection (Physical)
        0x05.toByte(), 0x09.toByte(),       //     Usage Page (Button)
        0x19.toByte(), 0x01.toByte(),       //     Usage Minimum (Button 1)
        0x29.toByte(), 0x03.toByte(),       //     Usage Maximum (Button 3)
        0x15.toByte(), 0x00.toByte(),       //     Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(),       //     Logical Maximum (1)
        0x95.toByte(), 0x03.toByte(),       //     Report Count (3)
        0x75.toByte(), 0x01.toByte(),       //     Report Size (1)
        0x81.toByte(), 0x02.toByte(),       //     Input (Data, Var, Abs)
        0x95.toByte(), 0x01.toByte(),       //     Report Count (1)
        0x75.toByte(), 0x05.toByte(),       //     Report Size (5)
        0x81.toByte(), 0x01.toByte(),       //     Input (Const, Array, Abs)
        0x05.toByte(), 0x01.toByte(),       //     Usage Page (Generic Desktop)
        0x09.toByte(), 0x30.toByte(),       //     Usage (X)
        0x09.toByte(), 0x31.toByte(),       //     Usage (Y)
        0x09.toByte(), 0x38.toByte(),       //     Usage (Wheel)
        0x15.toByte(), 0x81.toByte(),       //     Logical Minimum (-127)
        0x25.toByte(), 0x7F.toByte(),       //     Logical Maximum (127)
        0x75.toByte(), 0x08.toByte(),       //     Report Size (8)
        0x95.toByte(), 0x03.toByte(),       //     Report Count (3)
        0x81.toByte(), 0x06.toByte(),       //     Input (Data, Var, Rel)
        0xC0.toByte(),                      //   End Collection (Physical)
        0xC0.toByte()                       // End Collection (Application)
    )

    sealed interface HidAction {
        data class Keyboard(val keyCode: Int, val modifier: Byte = 0) : HidAction
        data class Consumer(val usage: Int) : HidAction
    }

    fun keyboardReport(keyCode: Int, modifier: Byte = 0): ByteArray = byteArrayOf(
        modifier,
        0x00,
        keyCode.toByte(),
        0x00,
        0x00,
        0x00,
        0x00,
        0x00
    )

    val KEYBOARD_RELEASE: ByteArray = ByteArray(8)

    fun consumerReport(usage: Int): ByteArray = byteArrayOf(
        (usage and 0xFF).toByte(),
        ((usage ushr 8) and 0xFF).toByte()
    )

    val CONSUMER_RELEASE: ByteArray = byteArrayOf(0x00, 0x00)

    fun mouseReport(
        buttonsMask: Int = 0,
        dx: Int = 0,
        dy: Int = 0,
        wheel: Int = 0
    ): ByteArray = byteArrayOf(
        (buttonsMask and 0x07).toByte(),
        dx.coerceIn(-127, 127).toByte(),
        dy.coerceIn(-127, 127).toByte(),
        wheel.coerceIn(-127, 127).toByte()
    )

    fun mouseButtonBit(button: MouseButton): Int = when (button) {
        MouseButton.LEFT -> 0x01
        MouseButton.RIGHT -> 0x02
        MouseButton.MIDDLE -> 0x04
    }

    fun buttonToHidAction(button: RemoteButton): HidAction = when (button) {
        RemoteButton.DPAD_UP -> HidAction.Keyboard(0x52)
        RemoteButton.DPAD_DOWN -> HidAction.Keyboard(0x51)
        RemoteButton.DPAD_LEFT -> HidAction.Keyboard(0x50)
        RemoteButton.DPAD_RIGHT -> HidAction.Keyboard(0x4F)
        RemoteButton.DPAD_CENTER -> HidAction.Consumer(0x0041) // Menu Pick -> KEY_SELECT -> DPAD_CENTER
        RemoteButton.BACK -> HidAction.Consumer(0x0224)        // AC Back -> KEY_BACK
        RemoteButton.HOME -> HidAction.Consumer(0x0223)        // AC Home -> KEY_HOMEPAGE
        RemoteButton.MENU -> HidAction.Consumer(0x0040)        // Menu -> KEY_MENU
        RemoteButton.POWER -> HidAction.Consumer(0x0030)       // Power -> KEY_POWER
        RemoteButton.VOLUME_UP -> HidAction.Consumer(0x00E9)   // Volume Increment
        RemoteButton.VOLUME_DOWN -> HidAction.Consumer(0x00EA) // Volume Decrement
        RemoteButton.MUTE -> HidAction.Consumer(0x00E2)        // Mute
        RemoteButton.CHANNEL_UP -> HidAction.Consumer(0x009C)  // Channel Increment
        RemoteButton.CHANNEL_DOWN -> HidAction.Consumer(0x009D) // Channel Decrement
        RemoteButton.MEDIA_PLAY_PAUSE -> HidAction.Consumer(0x00CD) // Play/Pause
        RemoteButton.MEDIA_NEXT -> HidAction.Consumer(0x00B5)       // Scan Next Track
        RemoteButton.MEDIA_PREVIOUS -> HidAction.Consumer(0x00B6)   // Scan Previous Track
        RemoteButton.MEDIA_STOP -> HidAction.Consumer(0x00B7)       // Stop
        RemoteButton.SEARCH -> HidAction.Consumer(0x0221)           // AC Search
        RemoteButton.SETTINGS -> HidAction.Consumer(0x0183)         // AL Consumer Config -> KEY_CONFIG -> SETTINGS
        RemoteButton.TV_INPUT -> HidAction.Consumer(0x0089)         // Media Select TV -> KEY_TV
        RemoteButton.DIGIT_1 -> HidAction.Keyboard(0x1E)
        RemoteButton.DIGIT_2 -> HidAction.Keyboard(0x1F)
        RemoteButton.DIGIT_3 -> HidAction.Keyboard(0x20)
        RemoteButton.DIGIT_4 -> HidAction.Keyboard(0x21)
        RemoteButton.DIGIT_5 -> HidAction.Keyboard(0x22)
        RemoteButton.DIGIT_6 -> HidAction.Keyboard(0x23)
        RemoteButton.DIGIT_7 -> HidAction.Keyboard(0x24)
        RemoteButton.DIGIT_8 -> HidAction.Keyboard(0x25)
        RemoteButton.DIGIT_9 -> HidAction.Keyboard(0x26)
        RemoteButton.DIGIT_0 -> HidAction.Keyboard(0x27)
        RemoteButton.BACKSPACE -> HidAction.Keyboard(0x2A)
        RemoteButton.ENTER -> HidAction.Keyboard(0x28)
        RemoteButton.ESCAPE -> HidAction.Keyboard(0x29)
    }

    /**
     * Maps a printable ASCII character (and newline/backspace/tab) to (keyCode, modifier).
     * Returns null for unsupported non-ASCII characters.
     */
    fun charToHidKey(ch: Char): Pair<Int, Byte>? = when (ch) {
        in 'a'..'z' -> (0x04 + (ch - 'a')) to 0x00
        in 'A'..'Z' -> (0x04 + (ch - 'A')) to MOD_LEFT_SHIFT
        in '1'..'9' -> (0x1E + (ch - '1')) to 0x00
        '0' -> 0x27 to 0x00
        '\n' -> 0x28 to 0x00
        '\b' -> 0x2A to 0x00
        '\t' -> 0x2B to 0x00
        ' ' -> 0x2C to 0x00
        '!' -> 0x1E to MOD_LEFT_SHIFT
        '@' -> 0x1F to MOD_LEFT_SHIFT
        '#' -> 0x20 to MOD_LEFT_SHIFT
        '$' -> 0x21 to MOD_LEFT_SHIFT
        '%' -> 0x22 to MOD_LEFT_SHIFT
        '^' -> 0x23 to MOD_LEFT_SHIFT
        '&' -> 0x24 to MOD_LEFT_SHIFT
        '*' -> 0x25 to MOD_LEFT_SHIFT
        '(' -> 0x26 to MOD_LEFT_SHIFT
        ')' -> 0x27 to MOD_LEFT_SHIFT
        '-' -> 0x2D to 0x00
        '_' -> 0x2D to MOD_LEFT_SHIFT
        '=' -> 0x2E to 0x00
        '+' -> 0x2E to MOD_LEFT_SHIFT
        '[' -> 0x2F to 0x00
        '{' -> 0x2F to MOD_LEFT_SHIFT
        ']' -> 0x30 to 0x00
        '}' -> 0x30 to MOD_LEFT_SHIFT
        '\\' -> 0x31 to 0x00
        '|' -> 0x31 to MOD_LEFT_SHIFT
        ';' -> 0x33 to 0x00
        ':' -> 0x33 to MOD_LEFT_SHIFT
        '\'' -> 0x34 to 0x00
        '"' -> 0x34 to MOD_LEFT_SHIFT
        '`' -> 0x35 to 0x00
        '~' -> 0x35 to MOD_LEFT_SHIFT
        ',' -> 0x36 to 0x00
        '<' -> 0x36 to MOD_LEFT_SHIFT
        '.' -> 0x37 to 0x00
        '>' -> 0x37 to MOD_LEFT_SHIFT
        '/' -> 0x38 to 0x00
        '?' -> 0x38 to MOD_LEFT_SHIFT
        else -> null
    }
}
