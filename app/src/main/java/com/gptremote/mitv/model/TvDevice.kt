package com.gptremote.mitv.model

import android.bluetooth.BluetoothClass

/**
 * Represents a discovered, paired, or saved electronic device across Bluetooth and Wi-Fi.
 */
sealed interface TvDevice {
    val id: String
    val name: String
    val transport: TransportMode
    val category: DeviceCategory

    data class BluetoothTv(
        override val id: String, // MAC address
        override val name: String,
        val isBonded: Boolean,
        override val category: DeviceCategory = inferCategory(name),
        val majorClass: Int = 0,
        val deviceClass: Int = 0
    ) : TvDevice {
        override val transport: TransportMode = TransportMode.BLUETOOTH_HID

        fun matchesProduct(product: ProductType): Boolean {
            val lower = name.lowercase()
            return when (product) {
                ProductType.SMART_TV, ProductType.SET_TOP_BOX, ProductType.PROJECTOR -> {
                    val isAudioAccessory = lower.contains("buds") || lower.contains("headphone") ||
                        lower.contains("ear") || lower.contains("pod") || lower.contains("car") ||
                        lower.contains("watch") || lower.contains("band") || lower.contains("mouse") ||
                        lower.contains("toad") || lower.contains("bs3344") || lower.contains("dock")
                    val isTvKeyword = lower.contains("tv") || lower.contains("mi") || lower.contains("xiaomi") ||
                        lower.contains("bravia") || lower.contains("samsung") || lower.contains("lg") ||
                        lower.contains("sony") || lower.contains("tcl") || lower.contains("hisense") ||
                        lower.contains("oneplus") || lower.contains("projector") || lower.contains("xgimi") ||
                        lower.contains("wanbo") || lower.contains("nebula") || lower.contains("fire") ||
                        lower.contains("shield") || lower.contains("apple") || lower.contains("box") ||
                        lower.contains("xstream") || lower.contains("jio")
                    val isTvBtClass = deviceClass == BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER ||
                        deviceClass == BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX ||
                        deviceClass == BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR
                    (isTvKeyword || isTvBtClass) && !isAudioAccessory
                }
                ProductType.PC_LAPTOP -> {
                    majorClass == BluetoothClass.Device.Major.COMPUTER ||
                        lower.contains("pc") || lower.contains("laptop") || lower.contains("desktop") ||
                        lower.contains("mac") || lower.contains("win") || lower.contains("linux")
                }
                else -> true
            }
        }
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
        override val category: DeviceCategory = DeviceCategory.ANDROID_TV
    }

    data class WifiPc(
        override val id: String, // host:port
        override val name: String,
        val host: String,
        val port: Int = 58765,
        val osName: String = "Desktop"
    ) : TvDevice {
        override val transport: TransportMode = TransportMode.WIFI_PC
        override val category: DeviceCategory = DeviceCategory.LAPTOP_PC
    }

    companion object {
        fun inferCategory(name: String): DeviceCategory {
            val lower = name.lowercase()
            return if (lower.contains("tv") || lower.contains("mi ") || lower.contains("mitv") ||
                lower.contains("xiaomi") || lower.contains("bravia") || lower.contains("shield") ||
                lower.contains("fire")
            ) {
                DeviceCategory.ANDROID_TV
            } else if (lower.contains("pc") || lower.contains("laptop") || lower.contains("desktop") ||
                lower.contains("mac") || lower.contains("win") || lower.contains("thinkpad") ||
                lower.contains("dell") || lower.contains("hp") || lower.contains("lenovo") || lower.contains("asus")
            ) {
                DeviceCategory.LAPTOP_PC
            } else {
                DeviceCategory.ANDROID_TV
            }
        }
    }
}

/**
 * User-customizable saved device card displayed on the Home Hub.
 */
data class SavedDevice(
    val id: String,
    val nickname: String,
    val originalName: String,
    val addressOrHost: String,
    val port: Int,
    val transport: TransportMode,
    val category: DeviceCategory,
    val iconKey: String = if (category == DeviceCategory.LAPTOP_PC) "laptop" else "tv",
    val lastConnectedMs: Long = 0L
) {
    fun toTvDevice(): TvDevice = when (transport) {
        TransportMode.BLUETOOTH_HID -> TvDevice.BluetoothTv(
            id = addressOrHost,
            name = nickname,
            isBonded = true,
            category = category
        )
        TransportMode.WIFI_ANDROID_TV -> TvDevice.WifiTv(
            id = id,
            name = nickname,
            host = addressOrHost,
            port = port,
            pairingPort = port + 1,
            isPaired = true
        )
        TransportMode.WIFI_PC -> TvDevice.WifiPc(
            id = id,
            name = nickname,
            host = addressOrHost,
            port = port
        )
    }
}
