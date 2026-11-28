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
    val bytesPerSec: Long = 0L,
    val isPaused: Boolean = false,
    val transferId: String = ""
)

data class TransferQueueItem(
    val id: String,
    val fileName: String,
    val sizeBytes: Long,
    val status: String = "Queued",
    val localUriString: String? = null
)

data class TransferHistoryItem(
    val id: String,
    val fileName: String,
    val sizeBytes: Long,
    val isSent: Boolean,
    val bytesPerSec: Long,
    val timestampMs: Long,
    val localUriString: String? = null,
    val statusLabel: String = if (isSent) "Sent" else "Received"
)

enum class ActiveSessionKind {
    BLUETOOTH_REMOTE,
    WIFI_TV_REMOTE,
    WIFI_PC_REMOTE,
    TV_COMPANION,
    PHONE_PEER,
    SMART_BULB
}

data class ActiveSessionItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: ActiveSessionKind,
    val targetProduct: ProductType,
    val targetBrandId: String
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
    val pcTransferQueue: List<TransferQueueItem> = emptyList(),
    val pcTransferHistory: List<TransferHistoryItem> = emptyList(),
    val smartBulbs: List<DiscoveredSmartBulb> = emptyList(),
    val isScanningBulbs: Boolean = false,
    val connectedBulb: DiscoveredSmartBulb? = null,
    val googleHomeSignedIn: Boolean = false,
    val googleHomeStatus: String = "Not connected",
    val googleHomeAccountEmail: String? = null,
    val googleHomeDevicesCount: Int = 0,
    val tuyaStatus: String = "Not configured",
    val tuyaIsSyncing: Boolean = false,
    val tuyaClientId: String = "hw98dtuj3hkfapvtappg",
    val tuyaClientSecret: String = "",
    val tuyaEndpoint: String = "https://openapi.tuyain.com",
    val tuyaSyncedCount: Int = 0,
    val phoneHostQrUrl: String? = null,
    val phoneHostSharedFiles: List<SharedPcFile> = emptyList(),
    val peerPhoneName: String? = null,
    val peerPhoneFiles: List<SharedPcFile> = emptyList(),
    val phoneTransferProgress: TransferProgress? = null,
    val phoneTransferQueue: List<TransferQueueItem> = emptyList(),
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
    val tvTransferQueue: List<TransferQueueItem> = emptyList(),
    val tvTransferHistory: List<TransferHistoryItem> = emptyList(),
    val isCastingToTv: Boolean = false,
    val lastStatusBanner: String? = null,
    val connectedBtDevice: TvDevice? = null,
    val connectedWifiTvDevice: TvDevice? = null,
    val connectedWifiPcDevice: TvDevice.WifiPc? = null
) {
    val isConnected: Boolean
        get() = status is ConnectionStatus.Connected ||
            connectedBtDevice != null ||
            connectedWifiTvDevice != null ||
            connectedWifiPcDevice != null ||
            connectedTvCompanion != null ||
            connectedBulb != null ||
            peerPhoneName != null

    val connectedDeviceName: String?
        get() = (status as? ConnectionStatus.Connected)?.device?.name
            ?: connectedWifiPcDevice?.name
            ?: connectedBtDevice?.name
            ?: connectedWifiTvDevice?.name
            ?: connectedTvCompanion?.name
            ?: connectedBulb?.name
            ?: peerPhoneName

    val connectedCategory: DeviceCategory
        get() = (status as? ConnectionStatus.Connected)?.device?.category ?: DeviceCategory.ANDROID_TV

    val connectedBrandOption: BrandOption?
        get() {
            val dev = (status as? ConnectionStatus.Connected)?.device
                ?: connectedBtDevice
                ?: connectedWifiTvDevice
                ?: connectedWifiPcDevice
            if (dev != null) {
                if (dev.category == DeviceCategory.LAPTOP_PC) {
                    return ProductCatalog.brandsFor(ProductType.PC_LAPTOP)
                        .firstOrNull { it.id == connectedBrandId }
                        ?: ProductCatalog.brandsFor(ProductType.PC_LAPTOP).first()
                }
                val matched = connectedBrandId?.let { id ->
                    ProductType.entries.firstNotNullOfOrNull { pt ->
                        if (pt == ProductType.PHONE_TO_PHONE || pt == ProductType.SMART_BULB || pt == ProductType.PC_LAPTOP) null
                        else ProductCatalog.brandsFor(pt).firstOrNull { it.id == id }
                    }
                }
                return matched ?: ProductCatalog.brandsFor(ProductType.SMART_TV).first()
            }
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
            return null
        }

    val activeSessions: List<ActiveSessionItem>
        get() = buildList {
            connectedWifiPcDevice?.let { pc ->
                val pcBrand = ProductCatalog.brandsFor(ProductType.PC_LAPTOP)
                    .firstOrNull { it.id == connectedBrandId }
                    ?: ProductCatalog.brandsFor(ProductType.PC_LAPTOP).first()
                add(
                    ActiveSessionItem(
                        id = "pc_${pc.id}",
                        title = pc.name,
                        subtitle = "PC Wi-Fi",
                        kind = ActiveSessionKind.WIFI_PC_REMOTE,
                        targetProduct = ProductType.PC_LAPTOP,
                        targetBrandId = pcBrand.id
                    )
                )
            }
            connectedBtDevice?.let { btDev ->
                val resolvedBrand = if (btDev.category == DeviceCategory.LAPTOP_PC) {
                    ProductCatalog.brandsFor(ProductType.PC_LAPTOP).first()
                } else {
                    connectedBrandId?.let { id ->
                        ProductType.entries.firstNotNullOfOrNull { pt ->
                            if (pt == ProductType.PHONE_TO_PHONE || pt == ProductType.SMART_BULB || pt == ProductType.PC_LAPTOP) null
                            else ProductCatalog.brandsFor(pt).firstOrNull { it.id == id }
                        }
                    } ?: ProductCatalog.brandsFor(ProductType.SMART_TV).first()
                }
                val prod = resolvedBrand.productType
                add(
                    ActiveSessionItem(
                        id = "bt_${btDev.id}",
                        title = btDev.name,
                        subtitle = if (btDev.category == DeviceCategory.LAPTOP_PC) "PC Bluetooth" else "${prod.title} BT",
                        kind = ActiveSessionKind.BLUETOOTH_REMOTE,
                        targetProduct = prod,
                        targetBrandId = resolvedBrand.id
                    )
                )
            }
            connectedWifiTvDevice?.let { tvDev ->
                val tvBrand = connectedBrandId?.let { id ->
                    ProductType.entries.firstNotNullOfOrNull { pt ->
                        if (pt == ProductType.PHONE_TO_PHONE || pt == ProductType.SMART_BULB || pt == ProductType.PC_LAPTOP) null
                        else ProductCatalog.brandsFor(pt).firstOrNull { it.id == id }
                    }
                } ?: ProductCatalog.brandsFor(ProductType.SMART_TV).first()
                add(
                    ActiveSessionItem(
                        id = "wifitv_${tvDev.id}",
                        title = tvDev.name,
                        subtitle = "${tvBrand.productType.title} Wi-Fi",
                        kind = ActiveSessionKind.WIFI_TV_REMOTE,
                        targetProduct = tvBrand.productType,
                        targetBrandId = tvBrand.id
                    )
                )
            }
            connectedTvCompanion?.let { tv ->
                val sameTvAsPrimary = connectedWifiTvDevice != null &&
                    connectedWifiTvDevice.name.equals(tv.name, ignoreCase = true)
                if (!sameTvAsPrimary) {
                    val tvBrand = ProductCatalog.brandsFor(ProductType.SMART_TV)
                        .firstOrNull { it.id == connectedBrandId }
                        ?: ProductCatalog.brandsFor(ProductType.SMART_TV).first()
                    add(
                        ActiveSessionItem(
                            id = "tvcomp_${tv.host}",
                            title = tv.name,
                            subtitle = "TV Companion",
                            kind = ActiveSessionKind.TV_COMPANION,
                            targetProduct = ProductType.SMART_TV,
                            targetBrandId = tvBrand.id
                        )
                    )
                }
            }
            peerPhoneName?.let { pName ->
                add(
                    ActiveSessionItem(
                        id = "phone_$pName",
                        title = pName,
                        subtitle = "Phone Share",
                        kind = ActiveSessionKind.PHONE_PEER,
                        targetProduct = ProductType.PHONE_TO_PHONE,
                        targetBrandId = ProductCatalog.brandsFor(ProductType.PHONE_TO_PHONE).first().id
                    )
                )
            }
            connectedBulb?.let { bulb ->
                add(
                    ActiveSessionItem(
                        id = "bulb_${bulb.host}",
                        title = bulb.name,
                        subtitle = "Smart Bulb",
                        kind = ActiveSessionKind.SMART_BULB,
                        targetProduct = ProductType.SMART_BULB,
                        targetBrandId = ProductCatalog.brandsFor(ProductType.SMART_BULB).first().id
                    )
                )
            }
        }

    fun isConnectedForBrand(brand: BrandOption): Boolean {
        if (brand.productType == ProductType.SMART_BULB) return connectedBulb != null
        if (brand.productType == ProductType.PHONE_TO_PHONE) return peerPhoneName != null
        if (brand.productType == ProductType.PC_LAPTOP) {
            return connectedWifiPcDevice != null || connectedBtDevice?.category == DeviceCategory.LAPTOP_PC
        }
        if (brand.productType == ProductType.SMART_TV && connectedTvCompanion != null) {
            return connectedBrandId == null || connectedBrandId == brand.id
        }
        if (connectedWifiTvDevice != null || connectedBtDevice != null) {
            val activeDev = connectedWifiTvDevice ?: connectedBtDevice
            if (activeDev?.category == DeviceCategory.ANDROID_TV) {
                return connectedBrandId == null || connectedBrandId == brand.id
            }
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
