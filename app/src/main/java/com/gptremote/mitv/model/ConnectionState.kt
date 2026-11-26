package com.gptremote.mitv.model

import android.graphics.Bitmap
import com.gptremote.mitv.haptic.HapticStrength
import com.gptremote.mitv.ui.theme.AppAccentTheme
import com.gptremote.mitv.wifi.DiscoveredSmartBulb
import com.gptremote.mitv.wifi.SharedPcFile

data class TransferProgress(
    val fileName: String,
    val bytesDone: Long,
    val totalBytes: Long,
    val isUpload: Boolean,
    val isComplete: Boolean = false
)

sealed interface ConnectionStatus {
    data object Disconnected : ConnectionStatus
    data class Connecting(val deviceName: String, val transport: TransportMode) : ConnectionStatus
    data class AwaitingWifiPin(val device: TvDevice.WifiTv, val message: String? = null) : ConnectionStatus
    data class Connected(val device: TvDevice) : ConnectionStatus
    data class Error(val message: String, val transport: TransportMode? = null) : ConnectionStatus
}

data class RemoteUiState(
    val activeTransport: TransportMode = TransportMode.BLUETOOTH_HID,
    val status: ConnectionStatus = ConnectionStatus.Disconnected,
    val selectedBrand: BrandOption? = null,
    val connectedBrandId: String? = null,
    val irActive: Boolean = false,
    val hasIrEmitter: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val bluetoothHidRegistered: Boolean = false,
    val bluetoothPcProfile: Boolean = false,
    val bluetoothDevices: List<TvDevice.BluetoothTv> = emptyList(),
    val wifiDevices: List<TvDevice.WifiTv> = emptyList(),
    val wifiPcDevices: List<TvDevice.WifiPc> = emptyList(),
    val savedDevices: List<SavedDevice> = emptyList(),
    val hapticStrength: HapticStrength = HapticStrength.MEDIUM,
    val soundEnabled: Boolean = true,
    val accentTheme: AppAccentTheme = AppAccentTheme.ORANGE,
    val isScanningWifi: Boolean = false,
    val isScanningWifiPc: Boolean = false,
    val isScanningBluetooth: Boolean = false,
    val pcScreenBitmap: Bitmap? = null,
    val isMirroringPc: Boolean = false,
    val isCastingPhoneScreen: Boolean = false,
    val sharedPcFiles: List<SharedPcFile> = emptyList(),
    val isTransferringFile: Boolean = false,
    val transferProgress: TransferProgress? = null,
    val smartBulbs: List<DiscoveredSmartBulb> = emptyList(),
    val isScanningBulbs: Boolean = false,
    val connectedBulb: DiscoveredSmartBulb? = null,
    val phoneHostQrUrl: String? = null,
    val phoneHostSharedFiles: List<SharedPcFile> = emptyList(),
    val peerPhoneName: String? = null,
    val peerPhoneFiles: List<SharedPcFile> = emptyList(),
    val phoneTransferProgress: TransferProgress? = null,
    val lastStatusBanner: String? = null
) {
    val isConnected: Boolean
        get() = status is ConnectionStatus.Connected

    val connectedDeviceName: String?
        get() = (status as? ConnectionStatus.Connected)?.device?.name

    val connectedCategory: DeviceCategory
        get() = (status as? ConnectionStatus.Connected)?.device?.category ?: DeviceCategory.ANDROID_TV

    fun isConnectedForBrand(brand: BrandOption): Boolean {
        val dev = (status as? ConnectionStatus.Connected)?.device ?: return false
        return when (brand.productType) {
            ProductType.PC_LAPTOP -> dev.category == DeviceCategory.LAPTOP_PC
            ProductType.SMART_TV,
            ProductType.SET_TOP_BOX,
            ProductType.PROJECTOR,
            ProductType.SOUNDBAR -> {
                dev.category == DeviceCategory.ANDROID_TV &&
                    (connectedBrandId == null || connectedBrandId == brand.id)
            }
            ProductType.SMART_BULB -> connectedBulb != null
            ProductType.PHONE_TO_PHONE -> peerPhoneName != null
            else -> connectedBrandId == brand.id
        }
    }
}
