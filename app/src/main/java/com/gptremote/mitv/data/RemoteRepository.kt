package com.gptremote.mitv.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.gptremote.mitv.bluetooth.BluetoothHidManager
import com.gptremote.mitv.haptic.HapticManager
import com.gptremote.mitv.haptic.HapticStrength
import com.gptremote.mitv.ir.IrBlasterManager
import com.gptremote.mitv.model.ActiveSessionItem
import com.gptremote.mitv.model.ActiveSessionKind
import com.gptremote.mitv.model.BrandOption
import com.gptremote.mitv.model.ConnectionStatus
import com.gptremote.mitv.model.DeviceCategory
import com.gptremote.mitv.model.LaunchableApp
import com.gptremote.mitv.model.MouseButton
import com.gptremote.mitv.model.ProductCatalog
import com.gptremote.mitv.model.ProductType
import com.gptremote.mitv.model.RemoteButton
import com.gptremote.mitv.model.RemoteUiState
import com.gptremote.mitv.model.SavedDevice
import com.gptremote.mitv.model.TransferHistoryItem
import com.gptremote.mitv.model.TransferProgress
import com.gptremote.mitv.model.TransferQueueItem
import com.gptremote.mitv.model.TransportMode
import com.gptremote.mitv.model.TvDevice
import java.io.File
import java.util.Locale
import com.gptremote.mitv.ui.theme.AppAccentTheme
import com.gptremote.mitv.wifi.AndroidTvNsdDiscovery
import com.gptremote.mitv.wifi.DiscoveredDlnaTv
import com.gptremote.mitv.wifi.DiscoveredSmartBulb
import com.gptremote.mitv.wifi.DlnaMediaCaster
import com.gptremote.mitv.wifi.HostedMediaItem
import com.gptremote.mitv.wifi.PhoneShareServer
import com.gptremote.mitv.wifi.ScreenCastService
import com.gptremote.mitv.wifi.SharedPcFile
import com.gptremote.mitv.wifi.SmartBulbClient
import com.gptremote.mitv.wifi.SmartTvProtocol
import com.gptremote.mitv.wifi.SmartTvWebSocketClient
import com.gptremote.mitv.wifi.TvCertificateManager
import com.gptremote.mitv.wifi.WifiPcRemoteClient
import com.gptremote.mitv.wifi.WifiTvRemoteClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * Coordinates Bluetooth HID, Wi-Fi Android TV Remote v2, Samsung Tizen & LG webOS Wi-Fi TVs,
 * Wi-Fi PC Companion (control, live screen mirror/cast, two-way file sharing with progress & QR),
 * Phone-to-Phone QR File Share, Smart Bulbs, built-in IR Blaster, haptics, sound, and theme.
 */
class RemoteRepository(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settingsPrefs = appContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)

    val bluetoothHid = BluetoothHidManager(appContext)
    val haptics = HapticManager(appContext)
    val irBlaster = IrBlasterManager(appContext)
    private val savedStore = SavedDeviceStore(appContext)

    private val certManager = TvCertificateManager(appContext)
    private val wifiDiscovery = AndroidTvNsdDiscovery(appContext, certManager)
    private val wifiTvClient = WifiTvRemoteClient(certManager)
    private val smartTvWsClient = SmartTvWebSocketClient(appContext)
    private val wifiPcClient = WifiPcRemoteClient()
    private val smartBulbClient = SmartBulbClient(appContext)
    private val phoneShareServer = PhoneShareServer(appContext)
    private val dlnaMediaCaster = DlnaMediaCaster()

    private val activeTransport = MutableStateFlow(TransportMode.BLUETOOTH_HID)
    private val selectedBrand = MutableStateFlow<BrandOption?>(null)
    private val connectedBrandId = MutableStateFlow<String?>(null)
    private val irActive = MutableStateFlow(false)
    private val hapticStrengthFlow = MutableStateFlow(haptics.strength)
    private val soundEnabledFlow = MutableStateFlow(haptics.soundEnabled)
    private val accentThemeFlow = MutableStateFlow(loadAccentTheme())
    private val statusBanner = MutableStateFlow<String?>(null)

    private data class CoreFlows(
        val transport: TransportMode,
        val btStatus: ConnectionStatus,
        val wifiTvStatus: ConnectionStatus,
        val smartTvWsStatus: ConnectionStatus,
        val wifiPcStatus: ConnectionStatus,
        val btEnabled: Boolean,
        val btRegistered: Boolean,
        val btPcProfile: Boolean
    )

    private data class DiscoveryFlows(
        val btDevices: List<TvDevice.BluetoothTv>,
        val wifiTvDevices: List<TvDevice.WifiTv>,
        val wifiPcDevices: List<TvDevice.WifiPc>,
        val savedDevices: List<SavedDevice>,
        val bulbs: List<DiscoveredSmartBulb>,
        val isScanningBulbs: Boolean,
        val connectedBulb: DiscoveredSmartBulb?
    )

    private data class FlagsFlows(
        val btScanning: Boolean,
        val wifiTvScanning: Boolean,
        val wifiPcScanning: Boolean,
        val hapticStrength: HapticStrength,
        val soundEnabled: Boolean,
        val accentTheme: AppAccentTheme,
        val banner: String?,
        val brand: BrandOption?,
        val connBrandId: String?,
        val irEnabled: Boolean
    )

    private data class MediaShareFlows(
        val pcScreen: Bitmap?,
        val isMirroringPc: Boolean,
        val isCastingPhone: Boolean,
        val files: List<SharedPcFile>,
        val isTransferring: Boolean,
        val transferProgress: TransferProgress?,
        val pcHistory: List<TransferHistoryItem>,
        val pcQueue: List<TransferQueueItem>
    )

    private data class PhoneShareFlows(
        val hostQrUrl: String?,
        val hostedFiles: List<SharedPcFile>,
        val peerName: String?,
        val peerFiles: List<SharedPcFile>,
        val phoneProgress: TransferProgress?,
        val phoneHistory: List<TransferHistoryItem>,
        val hotspotSsid: String?,
        val hotspotPassword: String?,
        val hotspotError: String?,
        val browserShareUrl: String?,
        val hostedMediaItems: List<HostedMediaItem>,
        val phoneQueue: List<TransferQueueItem>
    )

    private data class TvCompanionFlows(
        val discoveredTvs: List<TvDevice.WifiPc>,
        val connectedTv: TvDevice.WifiPc?,
        val tvFiles: List<SharedPcFile>,
        val tvProgress: TransferProgress?,
        val tvHistory: List<TransferHistoryItem>,
        val castTargetKind: String?,
        val tvQueue: List<TransferQueueItem>
    )

    private data class DlnaFlows(
        val renderers: List<DiscoveredDlnaTv>,
        val isScanning: Boolean,
        val activeRenderer: DiscoveredDlnaTv?
    )

    private val coreFlows = combine(
        activeTransport,
        bluetoothHid.connectionStatus,
        wifiTvClient.connectionStatus,
        smartTvWsClient.connectionStatus,
        wifiPcClient.connectionStatus,
        bluetoothHid.isBluetoothEnabled,
        bluetoothHid.isAppRegistered,
        bluetoothHid.isPcProfile
    ) { values ->
        CoreFlows(
            transport = values[0] as TransportMode,
            btStatus = values[1] as ConnectionStatus,
            wifiTvStatus = values[2] as ConnectionStatus,
            smartTvWsStatus = values[3] as ConnectionStatus,
            wifiPcStatus = values[4] as ConnectionStatus,
            btEnabled = values[5] as Boolean,
            btRegistered = values[6] as Boolean,
            btPcProfile = values[7] as Boolean
        )
    }

    private val discoveryFlows = combine(
        bluetoothHid.devices,
        wifiDiscovery.devices,
        wifiPcClient.discoveredPcs,
        savedStore.devices,
        smartBulbClient.bulbs,
        smartBulbClient.isScanning,
        smartBulbClient.connectedBulb
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        DiscoveryFlows(
            btDevices = values[0] as List<TvDevice.BluetoothTv>,
            wifiTvDevices = values[1] as List<TvDevice.WifiTv>,
            wifiPcDevices = values[2] as List<TvDevice.WifiPc>,
            savedDevices = values[3] as List<SavedDevice>,
            bulbs = values[4] as List<DiscoveredSmartBulb>,
            isScanningBulbs = values[5] as Boolean,
            connectedBulb = values[6] as DiscoveredSmartBulb?
        )
    }

    private val flagsFlows = combine(
        bluetoothHid.isScanning,
        wifiDiscovery.isScanning,
        wifiPcClient.isScanning,
        hapticStrengthFlow,
        soundEnabledFlow,
        accentThemeFlow,
        statusBanner,
        selectedBrand,
        connectedBrandId,
        irActive
    ) { values ->
        FlagsFlows(
            btScanning = values[0] as Boolean,
            wifiTvScanning = values[1] as Boolean,
            wifiPcScanning = values[2] as Boolean,
            hapticStrength = values[3] as HapticStrength,
            soundEnabled = values[4] as Boolean,
            accentTheme = values[5] as AppAccentTheme,
            banner = values[6] as String?,
            brand = values[7] as BrandOption?,
            connBrandId = values[8] as String?,
            irEnabled = values[9] as Boolean
        )
    }

    private val mediaShareFlows = combine(
        wifiPcClient.pcScreenBitmap,
        wifiPcClient.isMirroringPc,
        ScreenCastService.isCasting,
        wifiPcClient.sharedFiles,
        wifiPcClient.isTransferringFile,
        wifiPcClient.transferProgress,
        wifiPcClient.pcTransferHistory,
        wifiPcClient.pcTransferQueue
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        MediaShareFlows(
            pcScreen = values[0] as Bitmap?,
            isMirroringPc = values[1] as Boolean,
            isCastingPhone = values[2] as Boolean,
            files = values[3] as List<SharedPcFile>,
            isTransferring = values[4] as Boolean,
            transferProgress = values[5] as TransferProgress?,
            pcHistory = values[6] as List<TransferHistoryItem>,
            pcQueue = values[7] as List<TransferQueueItem>
        )
    }

    private val phoneShareFlows = combine(
        phoneShareServer.hostQrUrl,
        phoneShareServer.hostedFiles,
        phoneShareServer.peerName,
        phoneShareServer.peerFiles,
        phoneShareServer.transferProgress,
        phoneShareServer.transferHistory,
        phoneShareServer.hotspotSsid,
        phoneShareServer.hotspotPassword,
        phoneShareServer.hotspotError,
        phoneShareServer.browserShareUrl,
        phoneShareServer.hostedMediaItems,
        phoneShareServer.transferQueue
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        PhoneShareFlows(
            hostQrUrl = values[0] as String?,
            hostedFiles = values[1] as List<SharedPcFile>,
            peerName = values[2] as String?,
            peerFiles = values[3] as List<SharedPcFile>,
            phoneProgress = values[4] as TransferProgress?,
            phoneHistory = values[5] as List<TransferHistoryItem>,
            hotspotSsid = values[6] as String?,
            hotspotPassword = values[7] as String?,
            hotspotError = values[8] as String?,
            browserShareUrl = values[9] as String?,
            hostedMediaItems = values[10] as List<HostedMediaItem>,
            phoneQueue = values[11] as List<TransferQueueItem>
        )
    }

    private val dlnaFlows = combine(
        dlnaMediaCaster.renderers,
        dlnaMediaCaster.isScanning,
        dlnaMediaCaster.activeRenderer
    ) { list, scanning, active ->
        DlnaFlows(list, scanning, active)
    }

    private val tvCompanionFlows = combine(
        wifiPcClient.discoveredTvCompanions,
        wifiPcClient.connectedTvCompanion,
        wifiPcClient.tvSharedFiles,
        wifiPcClient.tvTransferProgress,
        wifiPcClient.tvTransferHistory,
        ScreenCastService.castTargetKind,
        wifiPcClient.tvTransferQueue
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        TvCompanionFlows(
            discoveredTvs = values[0] as List<TvDevice.WifiPc>,
            connectedTv = values[1] as TvDevice.WifiPc?,
            tvFiles = values[2] as List<SharedPcFile>,
            tvProgress = values[3] as TransferProgress?,
            tvHistory = values[4] as List<TransferHistoryItem>,
            castTargetKind = values[5] as String?,
            tvQueue = values[6] as List<TransferQueueItem>
        )
    }

    val uiState: StateFlow<RemoteUiState> = combine(
        coreFlows,
        discoveryFlows,
        flagsFlows,
        mediaShareFlows,
        phoneShareFlows,
        tvCompanionFlows,
        dlnaFlows
    ) { values ->
        val core = values[0] as CoreFlows
        val disc = values[1] as DiscoveryFlows
        val flags = values[2] as FlagsFlows
        val media = values[3] as MediaShareFlows
        val phone = values[4] as PhoneShareFlows
        val tv = values[5] as TvCompanionFlows
        val dlna = values[6] as DlnaFlows
        val effectiveStatus = when (core.transport) {
            TransportMode.BLUETOOTH_HID -> when {
                core.btStatus !is ConnectionStatus.Disconnected -> core.btStatus
                core.wifiPcStatus is ConnectionStatus.Connected -> core.wifiPcStatus
                core.wifiTvStatus is ConnectionStatus.Connected -> core.wifiTvStatus
                core.smartTvWsStatus is ConnectionStatus.Connected -> core.smartTvWsStatus
                else -> core.btStatus
            }
            TransportMode.WIFI_PC -> when {
                core.wifiPcStatus !is ConnectionStatus.Disconnected -> core.wifiPcStatus
                core.btStatus is ConnectionStatus.Connected -> core.btStatus
                core.wifiTvStatus is ConnectionStatus.Connected -> core.wifiTvStatus
                core.smartTvWsStatus is ConnectionStatus.Connected -> core.smartTvWsStatus
                else -> core.wifiPcStatus
            }
            TransportMode.WIFI_ANDROID_TV -> when {
                core.smartTvWsStatus !is ConnectionStatus.Disconnected -> core.smartTvWsStatus
                core.wifiTvStatus !is ConnectionStatus.Disconnected -> core.wifiTvStatus
                core.btStatus is ConnectionStatus.Connected -> core.btStatus
                core.wifiPcStatus is ConnectionStatus.Connected -> core.wifiPcStatus
                else -> core.wifiTvStatus
            }
        }

        val enrichedStatus = if (effectiveStatus is ConnectionStatus.Connected) {
            val saved = disc.savedDevices.firstOrNull { it.id == effectiveStatus.device.id }
            if (saved != null) {
                ConnectionStatus.Connected(saved.toTvDevice())
            } else {
                effectiveStatus
            }
        } else {
            effectiveStatus
        }

        val btDev = (core.btStatus as? ConnectionStatus.Connected)?.device
        val wifiTvDev = (core.smartTvWsStatus as? ConnectionStatus.Connected)?.device
            ?: (core.wifiTvStatus as? ConnectionStatus.Connected)?.device
        val wifiPcDev = (core.wifiPcStatus as? ConnectionStatus.Connected)?.device as? TvDevice.WifiPc

        RemoteUiState(
            activeTransport = core.transport,
            status = enrichedStatus,
            selectedBrand = flags.brand,
            connectedBrandId = flags.connBrandId,
            irActive = flags.irEnabled,
            hasIrEmitter = irBlaster.hasIrEmitter,
            bluetoothEnabled = core.btEnabled,
            bluetoothHidRegistered = core.btRegistered,
            bluetoothPcProfile = core.btPcProfile,
            bluetoothDevices = disc.btDevices,
            wifiDevices = disc.wifiTvDevices,
            wifiPcDevices = disc.wifiPcDevices,
            savedDevices = disc.savedDevices,
            hapticStrength = flags.hapticStrength,
            soundEnabled = flags.soundEnabled,
            accentTheme = flags.accentTheme,
            isScanningBluetooth = flags.btScanning,
            isScanningWifi = flags.wifiTvScanning,
            isScanningWifiPc = flags.wifiPcScanning,
            pcScreenBitmap = media.pcScreen,
            isMirroringPc = media.isMirroringPc,
            isCastingPhoneScreen = media.isCastingPhone && tv.castTargetKind != "tv",
            sharedPcFiles = media.files,
            isTransferringFile = media.isTransferring,
            transferProgress = media.transferProgress,
            pcTransferQueue = media.pcQueue,
            pcTransferHistory = media.pcHistory,
            smartBulbs = disc.bulbs,
            isScanningBulbs = disc.isScanningBulbs,
            connectedBulb = disc.connectedBulb,
            phoneHostQrUrl = phone.hostQrUrl,
            phoneHostSharedFiles = phone.hostedFiles,
            peerPhoneName = phone.peerName,
            peerPhoneFiles = phone.peerFiles,
            phoneTransferProgress = phone.phoneProgress,
            phoneTransferQueue = phone.phoneQueue,
            phoneTransferHistory = phone.phoneHistory,
            phoneHotspotSsid = phone.hotspotSsid,
            phoneHotspotPassword = phone.hotspotPassword,
            phoneHotspotError = phone.hotspotError,
            tvBrowserShareUrl = phone.browserShareUrl,
            phoneHostedMediaItems = phone.hostedMediaItems,
            dlnaRenderers = dlna.renderers,
            isScanningDlna = dlna.isScanning,
            activeDlnaRenderer = dlna.activeRenderer,
            discoveredTvCompanions = tv.discoveredTvs,
            connectedTvCompanion = tv.connectedTv,
            tvCompanionFiles = tv.tvFiles,
            tvTransferProgress = tv.tvProgress,
            tvTransferQueue = tv.tvQueue,
            tvTransferHistory = tv.tvHistory,
            isCastingToTv = media.isCastingPhone && tv.castTargetKind == "tv",
            lastStatusBanner = flags.banner,
            connectedBtDevice = btDev,
            connectedWifiTvDevice = wifiTvDev,
            connectedWifiPcDevice = wifiPcDev
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = RemoteUiState(
            hasIrEmitter = irBlaster.hasIrEmitter,
            hapticStrength = haptics.strength,
            soundEnabled = haptics.soundEnabled,
            accentTheme = accentThemeFlow.value
        )
    )

    init {
        uiState.onEach { state ->
            val connected = (state.status as? ConnectionStatus.Connected)?.device
            if (connected != null) {
                savedStore.saveOrUpdateFromConnection(connected)
                irActive.value = false
            } else if ((state.status is ConnectionStatus.Disconnected || state.status is ConnectionStatus.Error) &&
                state.connectedTvCompanion == null
            ) {
                connectedBrandId.value = null
            }
        }.launchIn(scope)
    }

    fun initializeBluetooth() = bluetoothHid.ensureInitialized()

    fun selectProductBrand(brand: BrandOption) {
        selectedBrand.value = brand
        statusBanner.value = null
        val isPc = brand.productType == ProductType.PC_LAPTOP
        if (isPc) {
            if (wifiPcClient.connectionStatus.value is ConnectionStatus.Connected) {
                activeTransport.value = TransportMode.WIFI_PC
                irActive.value = false
                wifiPcClient.refreshSharedFiles()
            } else if (bluetoothHid.connectionStatus.value !is ConnectionStatus.Connected &&
                bluetoothHid.isPcProfile.value != true
            ) {
                bluetoothHid.setPcProfile(true)
            }
        } else if (brand.productType == ProductType.PHONE_TO_PHONE) {
            phoneShareServer.startHostIfNeeded()
        } else if (brand.productType.supportsBluetoothHid) {
            when {
                wifiTvClient.connectionStatus.value is ConnectionStatus.Connected ||
                    smartTvWsClient.connectionStatus.value is ConnectionStatus.Connected -> {
                    activeTransport.value = TransportMode.WIFI_ANDROID_TV
                    irActive.value = false
                }
                bluetoothHid.connectionStatus.value is ConnectionStatus.Connected -> {
                    activeTransport.value = TransportMode.BLUETOOTH_HID
                    irActive.value = false
                }
                bluetoothHid.isPcProfile.value -> {
                    bluetoothHid.setPcProfile(false)
                }
            }
        } else if (irBlaster.hasIrEmitter) {
            irActive.value = true
        }
    }

    fun startPhoneShareHost() {
        phoneShareServer.startHostIfNeeded()
    }

    fun startLocalHotspot() {
        haptics.click()
        phoneShareServer.startLocalHotspot()
    }

    fun stopLocalHotspot() {
        haptics.click()
        phoneShareServer.stopLocalHotspot()
    }

    fun addLocalFileToPhoneShare(uri: Uri) = addLocalFilesToPhoneShare(listOf(uri))

    fun addLocalFilesToPhoneShare(uris: List<Uri>) {
        if (uris.isEmpty()) return
        haptics.click()
        phoneShareServer.addFilesToShare(uris)
        statusBanner.value = "${uris.size} file(s) ready for QR download."
    }

    fun refreshPeerPhoneFiles() {
        phoneShareServer.refreshPeerFiles { err -> statusBanner.value = err }
    }

    fun disconnectPhonePeer() {
        haptics.click()
        phoneShareServer.disconnectPeer()
        statusBanner.value = "Disconnected peer phone."
    }

    fun sendFileToPeerPhone(uri: Uri) = sendFilesToPeerPhone(listOf(uri))

    fun sendFilesToPeerPhone(uris: List<Uri>) {
        if (uris.isEmpty()) return
        haptics.click()
        phoneShareServer.sendFilesToPeer(uris) { msg -> statusBanner.value = msg }
    }

    fun downloadFileFromPeerPhone(fileName: String) {
        haptics.click()
        phoneShareServer.downloadFileFromPeer(fileName) { msg -> statusBanner.value = msg }
    }

    fun connectFromQr(rawQr: String): String {
        val text = rawQr.trim()
        val parsed = runCatching { Uri.parse(text) }.getOrNull()
        val scheme = parsed?.scheme?.lowercase()
        val hostType = parsed?.host?.lowercase()
        if (parsed != null && scheme == "gptremote") {
            val ip = parsed.getQueryParameter("host")?.trim().orEmpty()
            val port = parsed.getQueryParameter("port")?.toIntOrNull()
            val token = parsed.getQueryParameter("token")?.trim().orEmpty()
            val name = parsed.getQueryParameter("name")?.trim().orEmpty().ifBlank { ip }
            if (ip.isNotBlank()) {
                return when (hostType) {
                    "phone" -> {
                        val brand = ProductCatalog.brandsFor(ProductType.PHONE_TO_PHONE).first()
                        selectedBrand.value = brand
                        phoneShareServer.connectPeer(ip, port ?: PhoneShareServer.DEFAULT_PORT, name) { err ->
                            statusBanner.value = err
                        }
                        statusBanner.value = "Connected to phone $name ($ip)"
                        "phone"
                    }
                    "tv" -> {
                        val brand = ProductCatalog.brandsFor(ProductType.SMART_TV)
                            .firstOrNull { it.id == selectedBrand.value?.id }
                            ?: ProductCatalog.brandsFor(ProductType.SMART_TV).first()
                        selectedBrand.value = brand
                        connectedBrandId.value = brand.id
                        wifiPcClient.connectTvCompanion(ip, name, token) { msg ->
                            statusBanner.value = msg
                        }
                        "tv"
                    }
                    "bulb" -> {
                        val brand = ProductCatalog.brandsFor(ProductType.SMART_BULB).first()
                        selectedBrand.value = brand
                        connectManualBulb(ip)
                        "bulb"
                    }
                    else -> {
                        val brand = ProductCatalog.brandsFor(ProductType.PC_LAPTOP).first()
                        selectedBrand.value = brand
                        connectedBrandId.value = brand.id
                        connectWifiPc(
                            TvDevice.WifiPc(
                                id = "$ip:${port ?: WifiPcRemoteClient.DEFAULT_PORT}",
                                name = name,
                                host = ip,
                                port = port ?: WifiPcRemoteClient.DEFAULT_PORT
                            )
                        )
                        "pc"
                    }
                }
            }
        }
        // Fallback: plain IP scanned
        if (text.matches(Regex("""\d{1,3}(\.\d{1,3}){3}(:\d+)?"""))) {
            val ip = text.substringBefore(':')
            val port = text.substringAfter(':', "${WifiPcRemoteClient.DEFAULT_PORT}").toIntOrNull()
                ?: WifiPcRemoteClient.DEFAULT_PORT
            val brand = ProductCatalog.brandsFor(ProductType.PC_LAPTOP).first()
            selectedBrand.value = brand
            connectedBrandId.value = brand.id
            connectWifiPc(TvDevice.WifiPc("$ip:$port", "PC ($ip)", ip, port))
            return "pc"
        }
        statusBanner.value = "Unrecognized QR code: $text"
        return "unknown"
    }

    fun setIrMode(enabled: Boolean) {
        if (enabled && !irBlaster.hasIrEmitter) {
            statusBanner.value = "This phone does not have a built-in IR Blaster."
            irActive.value = false
            return
        }
        irActive.value = enabled
        if (enabled) {
            statusBanner.value = "IR Blaster mode active — point phone at ${selectedBrand.value?.name ?: "device"}."
        }
    }

    fun setHapticStrength(strength: HapticStrength) {
        haptics.strength = strength
        hapticStrengthFlow.value = strength
        if (strength != HapticStrength.OFF) {
            haptics.click()
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        haptics.soundEnabled = enabled
        soundEnabledFlow.value = enabled
        if (enabled) haptics.click()
    }

    fun setAccentTheme(theme: AppAccentTheme) {
        accentThemeFlow.value = theme
        settingsPrefs.edit().putString(KEY_ACCENT_THEME, theme.name).apply()
        haptics.click()
    }

    fun setBluetoothPcProfile(enabled: Boolean) = bluetoothHid.setPcProfile(enabled)

    fun allowBluetoothIncomingPairing() = bluetoothHid.allowIncomingPairing()

    fun startBluetoothScan() {
        activeTransport.value = TransportMode.BLUETOOTH_HID
        bluetoothHid.ensureInitialized()
        bluetoothHid.startDiscovery()
    }

    fun refreshPairedBluetooth() {
        bluetoothHid.ensureInitialized()
        bluetoothHid.refreshPairedDevices()
    }

    fun connectBluetooth(device: TvDevice.BluetoothTv) {
        irActive.value = false
        connectedBrandId.value = selectedBrand.value?.id
        activeTransport.value = TransportMode.BLUETOOTH_HID
        val category = if (selectedBrand.value?.productType == ProductType.PC_LAPTOP) {
            DeviceCategory.LAPTOP_PC
        } else {
            device.category
        }
        bluetoothHid.connect(device.id, category)
    }

    fun startWifiScan() {
        activeTransport.value = TransportMode.WIFI_ANDROID_TV
        wifiDiscovery.startDiscovery()
    }

    fun startWifiPcScan() {
        activeTransport.value = TransportMode.WIFI_PC
        wifiPcClient.startDiscovery()
    }

    fun startBulbScan() {
        smartBulbClient.discoverBulbs()
    }

    fun selectBulb(bulb: DiscoveredSmartBulb) {
        haptics.click()
        smartBulbClient.selectBulb(bulb)
        statusBanner.value = "Connected to ${bulb.name}"
    }

    fun connectManualBulb(host: String) {
        val clean = host.trim()
        if (clean.isEmpty()) return
        haptics.click()
        smartBulbClient.selectManualBulb(clean, selectedBrand.value?.id ?: "bulb_wiz")
        statusBanner.value = "Bulb ready at $clean"
    }

    fun setBulbState(
        power: Boolean? = null,
        brightness: Int? = null,
        rgb: Triple<Int, Int, Int>? = null,
        tempKelvin: Int? = null,
        irFallbackAction: String? = null
    ) {
        haptics.click()
        if (smartBulbClient.connectedBulb.value != null) {
            smartBulbClient.setState(
                power = power,
                brightness = brightness,
                rgb = rgb,
                tempKelvin = tempKelvin,
                onError = { err -> statusBanner.value = err }
            )
            return
        }
        val brand = selectedBrand.value
        if (brand != null && irBlaster.hasIrEmitter && irFallbackAction != null) {
            irBlaster.sendApplianceCommand(brand, irFallbackAction)
        } else {
            statusBanner.value = "Open Connect to pair WiZ / Yeelight bulb on local Wi-Fi."
        }
    }

    fun connectWifi(device: TvDevice.WifiTv, forcePair: Boolean = false) {
        irActive.value = false
        connectedBrandId.value = selectedBrand.value?.id
        activeTransport.value = TransportMode.WIFI_ANDROID_TV
        wifiDiscovery.stopDiscovery()
        when (device.port) {
            8001, 8002 -> {
                wifiTvClient.disconnect()
                smartTvWsClient.connect(device.host, device.name, SmartTvProtocol.SAMSUNG_TIZEN)
            }
            3000, 3001 -> {
                wifiTvClient.disconnect()
                smartTvWsClient.connect(device.host, device.name, SmartTvProtocol.LG_WEBOS)
            }
            else -> {
                smartTvWsClient.disconnect()
                wifiTvClient.connect(device, forcePair)
            }
        }
    }

    fun connectSmartTvWifi(host: String, name: String, protocol: SmartTvProtocol) {
        val cleanHost = host.trim()
        if (cleanHost.isEmpty()) return
        wifiTvClient.disconnect()
        irActive.value = false
        connectedBrandId.value = selectedBrand.value?.id
        activeTransport.value = TransportMode.WIFI_ANDROID_TV
        val label = name.trim().ifEmpty {
            if (protocol == SmartTvProtocol.SAMSUNG_TIZEN) "Samsung TV ($cleanHost)" else "LG TV ($cleanHost)"
        }
        smartTvWsClient.connect(cleanHost, label, protocol)
    }

    fun connectWifiPc(device: TvDevice.WifiPc) {
        irActive.value = false
        connectedBrandId.value = selectedBrand.value?.id ?: "pc_windows"
        activeTransport.value = TransportMode.WIFI_PC
        wifiPcClient.stopDiscovery()
        wifiPcClient.connect(device)
    }

    fun reconnectPcIfNeeded() = wifiPcClient.reconnectIfNeeded()

    fun addAndConnectManualWifi(host: String, nickname: String, category: DeviceCategory) {
        val cleanHost = host.trim()
        if (cleanHost.isEmpty()) return
        val brandId = selectedBrand.value?.id
        when {
            category == DeviceCategory.LAPTOP_PC -> {
                val name = nickname.trim().ifEmpty { "PC ($cleanHost)" }
                connectWifiPc(
                    TvDevice.WifiPc(
                        id = "$cleanHost:${WifiPcRemoteClient.DEFAULT_PORT}",
                        name = name,
                        host = cleanHost
                    )
                )
            }
            brandId == "tv_samsung" -> {
                connectSmartTvWifi(cleanHost, nickname.ifBlank { "Samsung Smart TV" }, SmartTvProtocol.SAMSUNG_TIZEN)
            }
            brandId == "tv_lg" -> {
                connectSmartTvWifi(cleanHost, nickname.ifBlank { "LG webOS TV" }, SmartTvProtocol.LG_WEBOS)
            }
            else -> {
                val name = nickname.trim().ifEmpty { "Smart TV ($cleanHost)" }
                connectWifi(
                    TvDevice.WifiTv(
                        id = "$cleanHost:6466",
                        name = name,
                        host = cleanHost,
                        isPaired = certManager.isHostPaired(cleanHost)
                    )
                )
            }
        }
    }

    fun submitWifiPin(pin: String) = wifiTvClient.submitPairingPin(pin)

    fun disconnect() {
        ScreenCastService.stop(appContext)
        bluetoothHid.disconnect()
        wifiTvClient.disconnect()
        smartTvWsClient.disconnect()
        wifiPcClient.disconnect()
        wifiPcClient.disconnectTvCompanion()
        phoneShareServer.disconnectPeer()
        smartBulbClient.disconnect()
        connectedBrandId.value = null
        irActive.value = false
        statusBanner.value = "Disconnected from all devices."
    }

    fun openActiveSession(session: ActiveSessionItem): BrandOption {
        haptics.click()
        val brand = ProductCatalog.brandsFor(session.targetProduct)
            .firstOrNull { it.id == session.targetBrandId }
            ?: ProductCatalog.brandsFor(session.targetProduct).first()
        selectedBrand.value = brand
        irActive.value = false
        when (session.kind) {
            ActiveSessionKind.WIFI_PC_REMOTE -> {
                activeTransport.value = TransportMode.WIFI_PC
                wifiPcClient.refreshSharedFiles()
            }
            ActiveSessionKind.BLUETOOTH_REMOTE -> {
                activeTransport.value = TransportMode.BLUETOOTH_HID
            }
            ActiveSessionKind.WIFI_TV_REMOTE -> {
                activeTransport.value = TransportMode.WIFI_ANDROID_TV
            }
            ActiveSessionKind.TV_COMPANION -> {
                wifiPcClient.refreshTvFiles()
            }
            ActiveSessionKind.PHONE_PEER -> {
                phoneShareServer.startHostIfNeeded()
                phoneShareServer.refreshPeerFiles()
            }
            ActiveSessionKind.SMART_BULB -> Unit
        }
        return brand
    }

    fun disconnectSession(session: ActiveSessionItem) {
        haptics.click()
        when (session.kind) {
            ActiveSessionKind.WIFI_PC_REMOTE -> {
                wifiPcClient.disconnect()
            }
            ActiveSessionKind.BLUETOOTH_REMOTE -> {
                bluetoothHid.disconnect()
            }
            ActiveSessionKind.WIFI_TV_REMOTE -> {
                wifiTvClient.disconnect()
                smartTvWsClient.disconnect()
                irActive.value = false
            }
            ActiveSessionKind.TV_COMPANION -> {
                ScreenCastService.stop(appContext)
                wifiPcClient.disconnectTvCompanion()
            }
            ActiveSessionKind.PHONE_PEER -> {
                phoneShareServer.disconnectPeer()
            }
            ActiveSessionKind.SMART_BULB -> {
                smartBulbClient.disconnect()
            }
        }
        statusBanner.value = "Disconnected ${session.title}."
    }

    fun startTvCompanionScan() {
        wifiPcClient.startDiscovery()
    }

    fun connectTvCompanion(host: String, name: String, token: String) {
        haptics.click()
        connectedBrandId.value = selectedBrand.value?.id ?: "tv_xiaomi"
        wifiPcClient.connectTvCompanion(host, name, token) { msg -> statusBanner.value = msg }
    }

    fun disconnectTvCompanion() {
        ScreenCastService.stop(appContext)
        wifiPcClient.disconnectTvCompanion()
    }

    fun refreshTvCompanionFiles() {
        wifiPcClient.refreshTvFiles { err -> statusBanner.value = err }
    }

    fun uploadFilesToTv(uris: List<Uri>) {
        if (uris.isEmpty()) return
        haptics.click()
        wifiPcClient.uploadFilesToTv(appContext, uris) { msg -> statusBanner.value = msg }
    }

    fun downloadFileFromTv(fileName: String) {
        haptics.click()
        wifiPcClient.downloadFileFromTv(appContext, fileName) { msg -> statusBanner.value = msg }
    }

    fun pauseTvTransfer() {
        haptics.click()
        wifiPcClient.pauseTvTransfer()
    }

    fun resumeTvTransfer() {
        haptics.click()
        wifiPcClient.resumeTvTransfer(appContext) { msg -> statusBanner.value = msg }
    }

    fun cancelTvTransfer() {
        haptics.click()
        wifiPcClient.cancelTvTransfer { msg -> statusBanner.value = msg }
    }

    fun getConnectedTvCompanionHost(): String? = wifiPcClient.getConnectedTvCompanionHostAndToken()?.first

    fun startPhoneCastToTv(resultCode: Int, data: Intent) {
        val pair = wifiPcClient.getConnectedTvCompanionHostAndToken()
        if (pair == null) {
            statusBanner.value = "Connect to GPT Remote TV Companion first to cast phone screen."
            return
        }
        ScreenCastService.start(appContext, resultCode, data, pair.first, pair.second, "TV")
    }

    fun startTvBrowserShareHost() {
        phoneShareServer.startHostIfNeeded()
    }

    fun openSharedPortalInSmartTvBrowser() {
        phoneShareServer.startHostIfNeeded()
        val url = phoneShareServer.browserShareUrl.value
        if (url.isNullOrBlank()) {
            statusBanner.value = "Turn on Wi-Fi or Hotspot first to generate the TV Browser link."
            return
        }
        if (smartTvWsClient.connectionStatus.value is ConnectionStatus.Connected) {
            haptics.click()
            smartTvWsClient.openBrowserUrl(url)
            statusBanner.value = "Opening $url on TV Browser..."
        } else {
            statusBanner.value = "Open $url in your TV Browser, or connect Samsung/LG Wi-Fi remote first."
        }
    }

    fun startDlnaScan() {
        phoneShareServer.startHostIfNeeded()
        dlnaMediaCaster.discoverRenderers()
    }

    fun selectDlnaRenderer(renderer: DiscoveredDlnaTv) {
        haptics.click()
        dlnaMediaCaster.selectRenderer(renderer)
        statusBanner.value = "Selected DLNA TV: ${renderer.name}"
    }

    fun castHostedItemToDlnaTv(item: HostedMediaItem) {
        phoneShareServer.startHostIfNeeded()
        val target = dlnaMediaCaster.activeRenderer.value ?: dlnaMediaCaster.renderers.value.firstOrNull()
        if (target == null) {
            statusBanner.value = "Scan Wi-Fi TVs below and select your Smart TV first."
            return
        }
        haptics.heavyClick()
        val url = phoneShareServer.hostedMediaItems.value.firstOrNull { it.id == item.id }?.viewUrl ?: item.viewUrl
        dlnaMediaCaster.castMedia(target, url, item.name, item.mimeType) { msg ->
            statusBanner.value = msg
        }
    }

    fun dlnaPlaybackCommand(action: String) {
        haptics.click()
        dlnaMediaCaster.sendPlaybackCommand(action) { err -> statusBanner.value = err }
    }

    fun startPcScreenMirror() {
        wifiPcClient.startPcScreenMirror { err -> statusBanner.value = err }
    }

    fun stopPcScreenMirror() = wifiPcClient.stopPcScreenMirror()

    fun sendPcScreenTap(nx: Float, ny: Float) {
        haptics.click()
        wifiPcClient.sendTapAbs(nx, ny)
    }

    fun sendPcScreenMove(nx: Float, ny: Float) {
        wifiPcClient.sendMoveAbs(nx, ny)
    }

    fun getConnectedPcHost(): String? = wifiPcClient.connectedHostIp

    fun startPhoneCast(resultCode: Int, data: Intent) {
        val host = wifiPcClient.connectedHostIp
        if (host == null) {
            statusBanner.value = "Connect to your PC via Wi-Fi first to cast phone screen."
            return
        }
        ScreenCastService.start(appContext, resultCode, data, host)
    }

    fun stopPhoneCast() {
        ScreenCastService.stop(appContext)
    }

    fun refreshPcFiles() {
        wifiPcClient.refreshSharedFiles { err -> statusBanner.value = err }
    }

    fun uploadFileToPc(uri: Uri) = uploadFilesToPc(listOf(uri))

    fun uploadFilesToPc(uris: List<Uri>) {
        if (uris.isEmpty()) return
        haptics.click()
        wifiPcClient.uploadFiles(appContext, uris) { msg -> statusBanner.value = msg }
    }

    fun downloadFileFromPc(fileName: String) {
        haptics.click()
        wifiPcClient.downloadFile(appContext, fileName) { msg -> statusBanner.value = msg }
    }

    fun pausePcTransfer() {
        haptics.click()
        wifiPcClient.pausePcTransfer()
    }

    fun resumePcTransfer() {
        haptics.click()
        wifiPcClient.resumePcTransfer(appContext) { msg -> statusBanner.value = msg }
    }

    fun cancelPcTransfer() {
        haptics.click()
        wifiPcClient.cancelPcTransfer { msg -> statusBanner.value = msg }
    }

    fun pausePhoneTransfer() {
        haptics.click()
        phoneShareServer.pauseActiveTransfer()
    }

    fun resumePhoneTransfer() {
        haptics.click()
        phoneShareServer.resumeActiveTransfer { msg -> statusBanner.value = msg }
    }

    fun cancelPhoneTransfer() {
        haptics.click()
        phoneShareServer.cancelActiveTransfer { msg -> statusBanner.value = msg }
    }

    fun queryInstalledAppsForShare(): List<ShareableAppInfo> {
        val pm = appContext.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }
        val seenPkgs = HashSet<String>()
        val result = ArrayList<ShareableAppInfo>()
        for (ri in activities) {
            val ai = ri.activityInfo?.applicationInfo ?: continue
            val pkg = ai.packageName ?: continue
            if (!seenPkgs.add(pkg)) continue
            val src = ai.sourceDir ?: continue
            val apkFile = File(src)
            if (!apkFile.exists() || !apkFile.canRead()) continue
            val label = runCatching { ri.loadLabel(pm).toString() }.getOrDefault(pkg).trim().ifBlank { pkg }
            result += ShareableAppInfo(
                packageName = pkg,
                label = label,
                apkPath = src,
                sizeBytes = apkFile.length()
            )
        }
        return result.sortedBy { it.label.lowercase(Locale.US) }
    }

    fun stageAppsAsUris(apps: List<ShareableAppInfo>): List<Uri> {
        val shareDir = File(appContext.cacheDir, "apk_share").apply { mkdirs() }
        return apps.mapNotNull { app ->
            runCatching {
                val cleanName = app.label.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_').ifBlank { app.packageName }
                val outFile = File(shareDir, "$cleanName.apk")
                File(app.apkPath).inputStream().use { inp ->
                    outFile.outputStream().use { out -> inp.copyTo(out) }
                }
                FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", outFile)
            }.getOrNull()
        }
    }

    fun openTransferredFile(activityContext: Context, uriString: String?, fileName: String) {
        var targetUri = uriString?.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (targetUri == null) {
            targetUri = findDownloadedFileUri(fileName)
        }
        if (targetUri == null) {
            statusBanner.value = "File saved in Downloads/GptRemote/$fileName (open with your Files app)."
            return
        }
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        val mime = when {
            ext == "apk" -> "application/vnd.android.package-archive"
            ext.isNotBlank() -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            else -> null
        } ?: appContext.contentResolver.getType(targetUri) ?: "*/*"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(targetUri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            activityContext.startActivity(intent)
        }.onFailure {
            val fallback = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(targetUri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                activityContext.startActivity(Intent.createChooser(fallback, "Open $fileName"))
            }.onFailure {
                statusBanner.value = "No app found on phone to open $fileName."
            }
        }
    }

    private fun findDownloadedFileUri(fileName: String): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                appContext.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Downloads._ID),
                    "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                    arrayOf(fileName),
                    "${MediaStore.Downloads.DATE_MODIFIED} DESC"
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val id = c.getLong(0)
                        return Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                    }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val f = File(
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GptRemote"),
                fileName
            )
            if (f.exists()) {
                return runCatching {
                    FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", f)
                }.getOrNull()
            }
        }
        return null
    }

    data class ShareableAppInfo(
        val packageName: String,
        val label: String,
        val apkPath: String,
        val sizeBytes: Long
    )

    fun sendButton(button: RemoteButton, strongHaptic: Boolean = false) {
        if (strongHaptic) haptics.heavyClick() else haptics.click()

        when (val current = uiState.value.status) {
            is ConnectionStatus.Connected -> {
                when (current.device.transport) {
                    TransportMode.BLUETOOTH_HID -> bluetoothHid.sendButton(button)
                    TransportMode.WIFI_ANDROID_TV -> {
                        if (smartTvWsClient.connectionStatus.value is ConnectionStatus.Connected) {
                            smartTvWsClient.sendButton(button)
                        } else {
                            wifiTvClient.sendButton(button)
                        }
                    }
                    TransportMode.WIFI_PC -> wifiPcClient.sendButton(button)
                }
                statusBanner.value = null
            }
            else -> {
                val brand = selectedBrand.value
                if (irActive.value && brand != null && irBlaster.hasIrEmitter) {
                    val sent = irBlaster.sendButton(brand, button)
                    if (!sent) statusBanner.value = "No IR mapping for this button on ${brand.name}."
                } else {
                    statusBanner.value = "Tap Connect at the top to pair with ${brand?.name ?: "your device"}."
                }
            }
        }
    }

    fun sendApplianceAction(actionKey: String) {
        haptics.click()
        val brand = selectedBrand.value
        if (brand != null && irBlaster.hasIrEmitter) {
            irBlaster.sendApplianceCommand(brand, actionKey)
        } else if (uiState.value.status is ConnectionStatus.Connected &&
            uiState.value.connectedBrandId == brand?.id
        ) {
            val mapped = when (actionKey) {
                "power" -> RemoteButton.POWER
                "speed_up", "temp_up" -> RemoteButton.VOLUME_UP
                "speed_down", "temp_down" -> RemoteButton.VOLUME_DOWN
                "mode", "cycle" -> RemoteButton.MENU
                "start_pause" -> RemoteButton.MEDIA_PLAY_PAUSE
                else -> null
            }
            mapped?.let { sendButton(it) }
        } else {
            statusBanner.value = "Use a phone with IR Blaster or connect via Wi-Fi / BT."
        }
    }

    fun launchApp(app: LaunchableApp) {
        haptics.heavyClick()
        when (val current = uiState.value.status) {
            is ConnectionStatus.Connected -> {
                when (current.device.transport) {
                    TransportMode.WIFI_ANDROID_TV -> {
                        if (smartTvWsClient.connectionStatus.value is ConnectionStatus.Connected) {
                            smartTvWsClient.launchApp(app.id)
                        } else {
                            app.tvAppLink?.let { wifiTvClient.launchApp(it) }
                        }
                    }
                    TransportMode.WIFI_PC -> app.pcAppKey?.let { wifiPcClient.launchApp(it) }
                    TransportMode.BLUETOOTH_HID -> {
                        if (selectedBrand.value?.productType == ProductType.PC_LAPTOP ||
                            current.device.category == DeviceCategory.LAPTOP_PC
                        ) {
                            app.pcFallbackCommand?.let { bluetoothHid.launchPcApp(it) }
                        } else {
                            bluetoothHid.launchTvAppViaSearch(app.name)
                        }
                    }
                }
            }
            else -> statusBanner.value = "Connect via Wi-Fi or Bluetooth first to launch ${app.name}."
        }
    }

    fun closeApp(app: LaunchableApp) {
        haptics.click()
        when (val current = uiState.value.status) {
            is ConnectionStatus.Connected -> {
                when (current.device.transport) {
                    TransportMode.WIFI_ANDROID_TV -> {
                        if (smartTvWsClient.connectionStatus.value is ConnectionStatus.Connected) {
                            smartTvWsClient.closeApp(app.id)
                        } else {
                            wifiTvClient.sendButton(RemoteButton.HOME)
                        }
                    }
                    TransportMode.WIFI_PC -> {
                        wifiPcClient.closeApp(app.pcAppKey ?: app.id)
                    }
                    TransportMode.BLUETOOTH_HID -> {
                        if (selectedBrand.value?.productType == ProductType.PC_LAPTOP ||
                            current.device.category == DeviceCategory.LAPTOP_PC
                        ) {
                            bluetoothHid.closePcApp()
                        } else {
                            bluetoothHid.sendButton(RemoteButton.HOME)
                        }
                    }
                }
                statusBanner.value = "Closing ${app.name}..."
            }
            else -> statusBanner.value = "Connect via Wi-Fi or Bluetooth first to close ${app.name}."
        }
    }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        haptics.tick()
        when (val current = uiState.value.status) {
            is ConnectionStatus.Connected -> {
                when (current.device.transport) {
                    TransportMode.BLUETOOTH_HID -> bluetoothHid.sendText(text)
                    TransportMode.WIFI_ANDROID_TV -> {
                        if (smartTvWsClient.connectionStatus.value is ConnectionStatus.Connected) {
                            smartTvWsClient.sendText(text)
                        } else {
                            wifiTvClient.sendText(text)
                        }
                    }
                    TransportMode.WIFI_PC -> wifiPcClient.sendText(text)
                }
            }
            else -> statusBanner.value = "Connect to your device first to send text."
        }
    }

    fun sendMouseMove(dx: Int, dy: Int, wheel: Int = 0) {
        if (wheel != 0) haptics.tick()
        val current = uiState.value.status
        if (current is ConnectionStatus.Connected) {
            when (current.device.transport) {
                TransportMode.BLUETOOTH_HID -> bluetoothHid.sendMouseMove(dx, dy, wheel)
                TransportMode.WIFI_PC -> wifiPcClient.sendMouseMove(dx, dy, wheel)
                TransportMode.WIFI_ANDROID_TV -> Unit
            }
        }
    }

    fun sendMouseClick(button: MouseButton) {
        if (button == MouseButton.RIGHT) haptics.heavyClick() else haptics.click()
        val current = uiState.value.status
        if (current is ConnectionStatus.Connected) {
            when (current.device.transport) {
                TransportMode.BLUETOOTH_HID -> bluetoothHid.sendMouseClick(button)
                TransportMode.WIFI_PC -> wifiPcClient.sendMouseClick(button)
                TransportMode.WIFI_ANDROID_TV -> {
                    val mapped = when (button) {
                        MouseButton.LEFT -> RemoteButton.DPAD_CENTER
                        MouseButton.RIGHT -> RemoteButton.BACK
                        MouseButton.MIDDLE -> RemoteButton.HOME
                    }
                    if (smartTvWsClient.connectionStatus.value is ConnectionStatus.Connected) {
                        smartTvWsClient.sendButton(mapped)
                    } else {
                        wifiTvClient.sendButton(mapped)
                    }
                }
            }
        } else {
            statusBanner.value = "Connect to your TV or PC first to use touchpad."
        }
    }

    fun clearBanner() {
        statusBanner.value = null
    }

    fun release() {
        ScreenCastService.stop(appContext)
        bluetoothHid.release()
        wifiDiscovery.stopDiscovery()
        wifiTvClient.disconnect()
        smartTvWsClient.disconnect()
        wifiPcClient.release()
        phoneShareServer.release()
    }

    private fun loadAccentTheme(): AppAccentTheme {
        val raw = settingsPrefs.getString(KEY_ACCENT_THEME, AppAccentTheme.ORANGE.name)
        return AppAccentTheme.entries.firstOrNull { it.name == raw } ?: AppAccentTheme.ORANGE
    }

    private companion object {
        const val SETTINGS_PREFS = "gpt_remote_settings"
        const val KEY_ACCENT_THEME = "accent_theme"
    }
}
