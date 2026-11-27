package com.gptremote.mitv.model

import android.graphics.Bitmap
import com.gptremote.mitv.haptic.HapticStrength
import com.gptremote.mitv.ui.theme.AppAccentTheme
import com.gptremote.mitv.wifi.DiscoveredDlnaTv
import com.gptremote.mitv.wifi.DiscoveredSmartBulb
import com.gptremote.mitv.wifi.HostedMediaItem
import com.gptremote.mitv.wifi.SharedPcFile

data class TransferProgress(
    val fileName: String,
    val bytesDone: Long,
    val totalBytes: Long,
    val isUpload: Boolean,
    val isComplete: Boolean = false,
    val bytesPerSec: Long = 0L
)

data class TransferHistoryItem(
    val id: String,
    val fileName: String,
    val sizeBytes: Long,
    val isSent: Boolean,
    val bytesPerSec: Long,
    val timestampMs: Long,
    val localUriString: String? = null
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
    val pcTransferHistory: List<TransferHistoryItem> = emptyList(),
    val smartBulbs: List<DiscoveredSmartBulb> = emptyList(),
    val isScanningBulbs: Boolean = false,
    val connectedBulb: DiscoveredSmartBulb? = null,
    val phoneHostQrUrl: String? = null,
    val phoneHostSharedFiles: List<SharedPcFile> = emptyList(),
    val peerPhoneName: String? = null,
    val peerPhoneFiles: List<SharedPcFile> = emptyList(),
    val phoneTransferProgress: TransferProgress? = null,
    val phoneTransferHistory: List<TransferHistoryItem> = emptyList(),
    val phoneHotspotSsid: String? = null,
    val phoneHotspotPassword: String? = null,
    val phoneHotspotError: String? = null,
    val tvBrowserShareUrl: String? = null,
    val phoneHostedMediaItems: List<HostedMediaItem> = emptyList(),
    val dlnaRenderers: List<DiscoveredDlnaTv> = emptyList(),
    val isScanningDlna: Boolean = false,
    val activeDlnaRenderer: DiscoveredDlnaTv? = null,
    val discoveredTvCompanions: List<TvDevice.WifiPc> = emptyList(),
    val connectedTvCompanion: TvDevice.WifiPc? = null,
    val tvCompanionFiles: List<SharedPcFile> = emptyList(),
    val tvTransferProgress: TransferProgress? = null,
    val tvTransferHistory: List<TransferHistoryItem> = emptyList(),
    val isCastingToTv: Boolean = false,
    val lastStatusBanner: String? = null
) {
    val isConnected: Boolean
        get() = status is ConnectionStatus.Connected ||
            connectedTvCompanion != null ||
            connectedBulb != null ||
            peerPhoneName != null

    val connectedDeviceName: String?
        get() = (status as? ConnectionStatus.Connected)?.device?.name
            ?: connectedTvCompanion?.name
            ?: connectedBulb?.name
            ?: peerPhoneName

    val connectedCategory: DeviceCategory
        get() = (status as? ConnectionStatus.Connected)?.device?.category ?: DeviceCategory.ANDROID_TV

    val connectedBrandOption: BrandOption?
        get() {
            if (connectedTvCompanion != null) {
                return ProductCatalog.brandsFor(ProductType.SMART_TV)
                    .firstOrNull { it.id == connectedBrandId }
                    ?: ProductCatalog.brandsFor(ProductType.SMART_TV).first()
            }
            if (connectedBulb != null) {
                return ProductCatalog.brandsFor(ProductType.SMART_BULB).firstOrNull()
            }
            if (peerPhoneName != null) {
                return ProductCatalog.brandsFor(ProductType.PHONE_TO_PHONE).firstOrNull()
            }
            val dev = (status as? ConnectionStatus.Connected)?.device ?: return null
            if (dev.category == DeviceCategory.LAPTOP_PC) {
                return ProductCatalog.brandsFor(ProductType.PC_LAPTOP)
                    .firstOrNull { it.id == connectedBrandId }
                    ?: ProductCatalog.brandsFor(ProductType.PC_LAPTOP).first()
            }
            val explicit = connectedBrandId?.let { id ->
                ProductType.entries.firstNotNullOfOrNull { pt ->
                    ProductCatalog.brandsFor(pt).firstOrNull { it.id == id }
                }
            }
            if (explicit != null) return explicit
            return ProductCatalog.brandsFor(ProductType.SMART_TV).firstOrNull()
        }

    fun isConnectedForBrand(brand: BrandOption): Boolean {
        if (brand.productType == ProductType.SMART_BULB) return connectedBulb != null
        if (brand.productType == ProductType.PHONE_TO_PHONE) return peerPhoneName != null
        if (brand.productType == ProductType.SMART_TV && connectedTvCompanion != null) {
            return connectedBrandId == null || connectedBrandId == brand.id
        }
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
            else -> connectedBrandId == brand.id
        }
    }
}
