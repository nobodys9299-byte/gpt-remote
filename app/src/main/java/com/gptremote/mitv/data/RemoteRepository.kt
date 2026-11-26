package com.gptremote.mitv.data

import android.content.Context
import com.gptremote.mitv.bluetooth.BluetoothHidManager
import com.gptremote.mitv.haptic.HapticManager
import com.gptremote.mitv.haptic.HapticStrength
import com.gptremote.mitv.ir.IrBlasterManager
import com.gptremote.mitv.model.BrandOption
import com.gptremote.mitv.model.ConnectionStatus
import com.gptremote.mitv.model.DeviceCategory
import com.gptremote.mitv.model.LaunchableApp
import com.gptremote.mitv.model.MouseButton
import com.gptremote.mitv.model.ProductType
import com.gptremote.mitv.model.RemoteButton
import com.gptremote.mitv.model.RemoteUiState
import com.gptremote.mitv.model.SavedDevice
import com.gptremote.mitv.model.TransportMode
import com.gptremote.mitv.model.TvDevice
import com.gptremote.mitv.wifi.AndroidTvNsdDiscovery
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
 * Coordinates Bluetooth HID, Wi-Fi Android TV Remote v2, Wi-Fi PC Companion,
 * built-in IR Blaster, Saved Devices, and hardware vibration haptics.
 */
class RemoteRepository(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val bluetoothHid = BluetoothHidManager(appContext)
    val haptics = HapticManager(appContext)
    val irBlaster = IrBlasterManager(appContext)
    private val savedStore = SavedDeviceStore(appContext)

    private val certManager = TvCertificateManager(appContext)
    private val wifiDiscovery = AndroidTvNsdDiscovery(appContext, certManager)
    private val wifiTvClient = WifiTvRemoteClient(certManager)
    private val wifiPcClient = WifiPcRemoteClient()

    private val activeTransport = MutableStateFlow(TransportMode.BLUETOOTH_HID)
    private val selectedBrand = MutableStateFlow<BrandOption?>(null)
    private val irActive = MutableStateFlow(false)
    private val hapticStrengthFlow = MutableStateFlow(haptics.strength)
    private val statusBanner = MutableStateFlow<String?>(null)

    private data class CoreFlows(
        val transport: TransportMode,
        val btStatus: ConnectionStatus,
        val wifiTvStatus: ConnectionStatus,
        val wifiPcStatus: ConnectionStatus,
        val btEnabled: Boolean,
        val btRegistered: Boolean,
        val btPcProfile: Boolean
    )

    private data class DiscoveryFlows(
        val btDevices: List<TvDevice.BluetoothTv>,
        val wifiTvDevices: List<TvDevice.WifiTv>,
        val wifiPcDevices: List<TvDevice.WifiPc>,
        val savedDevices: List<SavedDevice>
    )

    private data class FlagsFlows(
        val btScanning: Boolean,
        val wifiTvScanning: Boolean,
        val wifiPcScanning: Boolean,
        val hapticStrength: HapticStrength,
        val banner: String?,
        val brand: BrandOption?,
        val irEnabled: Boolean
    )

    private val coreFlows = combine(
        activeTransport,
        bluetoothHid.connectionStatus,
        wifiTvClient.connectionStatus,
        wifiPcClient.connectionStatus,
        bluetoothHid.isBluetoothEnabled,
        bluetoothHid.isAppRegistered,
        bluetoothHid.isPcProfile
    ) { values ->
        CoreFlows(
            transport = values[0] as TransportMode,
            btStatus = values[1] as ConnectionStatus,
            wifiTvStatus = values[2] as ConnectionStatus,
            wifiPcStatus = values[3] as ConnectionStatus,
            btEnabled = values[4] as Boolean,
            btRegistered = values[5] as Boolean,
            btPcProfile = values[6] as Boolean
        )
    }

    private val discoveryFlows = combine(
        bluetoothHid.devices,
        wifiDiscovery.devices,
        wifiPcClient.discoveredPcs,
        savedStore.devices
    ) { bt, wifiTv, wifiPc, saved ->
        DiscoveryFlows(bt, wifiTv, wifiPc, saved)
    }

    private val flagsFlows = combine(
        bluetoothHid.isScanning,
        wifiDiscovery.isScanning,
        wifiPcClient.isScanning,
        hapticStrengthFlow,
        statusBanner,
        selectedBrand,
        irActive
    ) { values ->
        FlagsFlows(
            btScanning = values[0] as Boolean,
            wifiTvScanning = values[1] as Boolean,
            wifiPcScanning = values[2] as Boolean,
            hapticStrength = values[3] as HapticStrength,
            banner = values[4] as String?,
            brand = values[5] as BrandOption?,
            irEnabled = values[6] as Boolean
        )
    }

    val uiState: StateFlow<RemoteUiState> = combine(
        coreFlows,
        discoveryFlows,
        flagsFlows
    ) { core, disc, flags ->
        val effectiveStatus = when {
            core.btStatus is ConnectionStatus.Connected -> core.btStatus
            core.wifiTvStatus is ConnectionStatus.Connected -> core.wifiTvStatus
            core.wifiPcStatus is ConnectionStatus.Connected -> core.wifiPcStatus
            core.transport == TransportMode.BLUETOOTH_HID -> core.btStatus
            core.transport == TransportMode.WIFI_ANDROID_TV -> core.wifiTvStatus
            else -> core.wifiPcStatus
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

        RemoteUiState(
            activeTransport = core.transport,
            status = enrichedStatus,
            selectedBrand = flags.brand,
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
            isScanningBluetooth = flags.btScanning,
            isScanningWifi = flags.wifiTvScanning,
            isScanningWifiPc = flags.wifiPcScanning,
            lastStatusBanner = flags.banner
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = RemoteUiState(
            hasIrEmitter = irBlaster.hasIrEmitter,
            hapticStrength = haptics.strength
        )
    )

    init {
        uiState.onEach { state ->
            val connected = (state.status as? ConnectionStatus.Connected)?.device
            if (connected != null) {
                savedStore.saveOrUpdateFromConnection(connected)
                irActive.value = false
            }
        }.launchIn(scope)
    }

    fun initializeBluetooth() {
        bluetoothHid.ensureInitialized()
    }

    fun selectProductBrand(brand: BrandOption) {
        selectedBrand.value = brand
        val isPc = brand.productType == ProductType.PC_LAPTOP
        bluetoothHid.setPcProfile(isPc)
        if (!isPc && !brand.supportsBluetoothHidOnly() && irBlaster.hasIrEmitter) {
            irActive.value = true
        }
    }

    private fun BrandOption.supportsBluetoothHidOnly(): Boolean =
        productType == ProductType.SMART_TV ||
            productType == ProductType.PC_LAPTOP ||
            productType == ProductType.SET_TOP_BOX ||
            productType == ProductType.SOUNDBAR ||
            productType == ProductType.PROJECTOR

    fun setIrMode(enabled: Boolean) {
        if (enabled && !irBlaster.hasIrEmitter) {
            statusBanner.value = "This phone does not have a built-in IR Blaster."
            irActive.value = false
            return
        }
        irActive.value = enabled
        if (enabled) {
            statusBanner.value = "IR Blaster mode active — point your phone at the ${selectedBrand.value?.name ?: "device"}."
        }
    }

    fun setHapticStrength(strength: HapticStrength) {
        haptics.strength = strength
        hapticStrengthFlow.value = strength
        if (strength != HapticStrength.OFF) {
            haptics.click()
        }
    }

    fun setBluetoothPcProfile(enabled: Boolean) {
        bluetoothHid.setPcProfile(enabled)
    }

    fun selectTransport(mode: TransportMode) {
        activeTransport.value = mode
        when (mode) {
            TransportMode.BLUETOOTH_HID -> {
                wifiDiscovery.stopDiscovery()
                wifiPcClient.stopDiscovery()
                bluetoothHid.ensureInitialized()
            }
            TransportMode.WIFI_ANDROID_TV -> {
                wifiPcClient.stopDiscovery()
                wifiDiscovery.startDiscovery()
            }
            TransportMode.WIFI_PC -> {
                wifiDiscovery.stopDiscovery()
                wifiPcClient.startDiscovery()
            }
        }
    }

    fun scanAllDevices() {
        bluetoothHid.ensureInitialized()
        bluetoothHid.startDiscovery()
        wifiDiscovery.startDiscovery()
        wifiPcClient.startDiscovery()
    }

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
        wifiTvClient.disconnect()
        wifiPcClient.disconnect()
        irActive.value = false
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

    fun connectWifi(device: TvDevice.WifiTv, forcePair: Boolean = false) {
        bluetoothHid.disconnect()
        wifiPcClient.disconnect()
        irActive.value = false
        activeTransport.value = TransportMode.WIFI_ANDROID_TV
        wifiDiscovery.stopDiscovery()
        wifiTvClient.connect(device, forcePair)
    }

    fun connectWifiPc(device: TvDevice.WifiPc) {
        bluetoothHid.disconnect()
        wifiTvClient.disconnect()
        irActive.value = false
        activeTransport.value = TransportMode.WIFI_PC
        wifiPcClient.stopDiscovery()
        wifiPcClient.connect(device)
    }

    fun connectSavedDevice(saved: SavedDevice) {
        when (saved.transport) {
            TransportMode.BLUETOOTH_HID -> {
                connectBluetooth(
                    TvDevice.BluetoothTv(
                        id = saved.addressOrHost,
                        name = saved.nickname,
                        isBonded = true,
                        category = saved.category
                    )
                )
            }
            TransportMode.WIFI_ANDROID_TV -> {
                connectWifi(
                    TvDevice.WifiTv(
                        id = saved.id,
                        name = saved.nickname,
                        host = saved.addressOrHost,
                        port = if (saved.port > 0) saved.port else 6466,
                        pairingPort = (if (saved.port > 0) saved.port else 6466) + 1,
                        isPaired = certManager.isHostPaired(saved.addressOrHost)
                    )
                )
            }
            TransportMode.WIFI_PC -> {
                connectWifiPc(
                    TvDevice.WifiPc(
                        id = saved.id,
                        name = saved.nickname,
                        host = saved.addressOrHost,
                        port = if (saved.port > 0) saved.port else WifiPcRemoteClient.DEFAULT_PORT
                    )
                )
            }
        }
    }

    fun addAndConnectManualWifi(
        host: String,
        nickname: String,
        category: DeviceCategory
    ) {
        val cleanHost = host.trim()
        if (cleanHost.isEmpty()) return
        val name = nickname.trim().ifEmpty {
            if (category == DeviceCategory.LAPTOP_PC) "Laptop ($cleanHost)" else "Smart TV ($cleanHost)"
        }
        val port = if (category == DeviceCategory.LAPTOP_PC) WifiPcRemoteClient.DEFAULT_PORT else 6466
        val transport = if (category == DeviceCategory.LAPTOP_PC) TransportMode.WIFI_PC else TransportMode.WIFI_ANDROID_TV

        val saved = SavedDevice(
            id = "$cleanHost:$port",
            nickname = name,
            originalName = name,
            addressOrHost = cleanHost,
            port = port,
            transport = transport,
            category = category,
            iconKey = if (category == DeviceCategory.LAPTOP_PC) "laptop" else "tv",
            lastConnectedMs = System.currentTimeMillis()
        )
        savedStore.upsertCustomDevice(saved)
        connectSavedDevice(saved)
    }

    fun submitWifiPin(pin: String) {
        wifiTvClient.submitPairingPin(pin)
    }

    fun disconnect() {
        bluetoothHid.disconnect()
        wifiTvClient.disconnect()
        wifiPcClient.disconnect()
        irActive.value = false
    }

    fun sendButton(button: RemoteButton, strongHaptic: Boolean = false) {
        if (strongHaptic) haptics.heavyClick() else haptics.click()

        when (val current = uiState.value.status) {
            is ConnectionStatus.Connected -> {
                when (current.device.transport) {
                    TransportMode.BLUETOOTH_HID -> bluetoothHid.sendButton(button)
                    TransportMode.WIFI_ANDROID_TV -> wifiTvClient.sendButton(button)
                    TransportMode.WIFI_PC -> wifiPcClient.sendButton(button)
                }
                statusBanner.value = null
            }
            else -> {
                val brand = selectedBrand.value
                if (irActive.value && brand != null && irBlaster.hasIrEmitter) {
                    val sent = irBlaster.sendButton(brand, button)
                    if (!sent) {
                        statusBanner.value = "No IR mapping for this button on ${brand.name}."
                    }
                } else {
                    statusBanner.value = "Tap Connect at the top to pair with your ${brand?.name ?: "device"}."
                }
            }
        }
    }

    fun sendApplianceAction(actionKey: String) {
        haptics.click()
        val brand = selectedBrand.value
        if (brand != null && irBlaster.hasIrEmitter) {
            irBlaster.sendApplianceCommand(brand, actionKey)
        } else {
            statusBanner.value = "Phone has no built-in IR Blaster to transmit to ${brand?.name ?: "this appliance"}."
        }
    }

    fun launchApp(app: LaunchableApp) {
        haptics.heavyClick()
        when (val current = uiState.value.status) {
            is ConnectionStatus.Connected -> {
                when (current.device.transport) {
                    TransportMode.WIFI_ANDROID_TV -> {
                        val link = app.tvAppLink
                        if (link != null) {
                            wifiTvClient.launchApp(link)
                        }
                    }
                    TransportMode.WIFI_PC -> {
                        val key = app.pcAppKey
                        if (key != null) {
                            wifiPcClient.launchApp(key)
                        }
                    }
                    TransportMode.BLUETOOTH_HID -> {
                        if (selectedBrand.value?.productType == ProductType.PC_LAPTOP ||
                            current.device.category == DeviceCategory.LAPTOP_PC
                        ) {
                            val cmd = app.pcFallbackCommand
                            if (cmd != null) {
                                bluetoothHid.launchPcApp(cmd)
                            }
                        } else {
                            statusBanner.value = "Direct TV app launch requires Wi-Fi Android TV connection (tap Connect -> Wi-Fi)."
                        }
                    }
                }
            }
            else -> {
                statusBanner.value = "Connect via Wi-Fi or Bluetooth first to launch ${app.name}."
            }
        }
    }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        haptics.tick()
        when (val current = uiState.value.status) {
            is ConnectionStatus.Connected -> {
                when (current.device.transport) {
                    TransportMode.BLUETOOTH_HID -> bluetoothHid.sendText(text)
                    TransportMode.WIFI_ANDROID_TV -> wifiTvClient.sendText(text)
                    TransportMode.WIFI_PC -> wifiPcClient.sendText(text)
                }
            }
            else -> {
                statusBanner.value = "Connect to your device first to send text."
            }
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
                    wifiTvClient.sendButton(mapped)
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
        bluetoothHid.release()
        wifiDiscovery.stopDiscovery()
        wifiTvClient.disconnect()
        wifiPcClient.release()
    }
}
