package com.gptremote.mitv.model

/**
 * Represents a discovered or paired TV target on either Bluetooth or local Wi-Fi.
 */
sealed interface TvDevice {
    val id: String
    val name: String
    val transport: TransportMode

    data class BluetoothTv(
        override val id: String, // MAC address
        override val name: String,
        val isBonded: Boolean
    ) : TvDevice {
        override val transport: TransportMode = TransportMode.BLUETOOTH_HID
    }

    data class WifiTv(
        override val id: String, // host:port
        override val name: String,
        val host: String,
        val port: Int = 6466,
        val pairingPort: Int = 6467,
        val isPaired: Boolean = false
    ) : TvDevice {
        override val transport: TransportMode = TransportMode.WIFI_ANDROID_TV
    }
}
