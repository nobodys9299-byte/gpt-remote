package com.gptremote.mitv.data

import android.content.Context
import com.gptremote.mitv.bluetooth.BluetoothHidManager
import com.gptremote.mitv.model.ConnectionStatus
import com.gptremote.mitv.model.MouseButton
import com.gptremote.mitv.model.RemoteButton
import com.gptremote.mitv.model.RemoteUiState
import com.gptremote.mitv.model.TransportMode
import com.gptremote.mitv.model.TvDevice
import com.gptremote.mitv.wifi.AndroidTvNsdDiscovery
import com.gptremote.mitv.wifi.TvCertificateManager
import com.gptremote.mitv.wifi.WifiTvRemoteClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Coordinates Bluetooth HID (primary) and Wi-Fi Android TV Remote v2 (fallback).
 */
class RemoteRepository(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val bluetoothHid = BluetoothHidManager(appContext)
    private val certManager = TvCertificateManager(appContext)
    private val wifiDiscovery = AndroidTvNsdDiscovery(appContext, certManager)
    private val wifiClient = WifiTvRemoteClient(certManager)

    private val activeTransport = MutableStateFlow(TransportMode.BLUETOOTH_HID)
    private val statusBanner = MutableStateFlow<String?>(null)

    private data class CoreFlows(
        val transport: TransportMode,
        val btStatus: ConnectionStatus,
        val wifiStatus: ConnectionStatus,
        val btEnabled: Boolean,
        val btRegistered: Boolean
    )

    private val coreFlows = combine(
        activeTransport,
        bluetoothHid.connectionStatus,
        wifiClient.connectionStatus,
        bluetoothHid.isBluetoothEnabled,
        bluetoothHid.isAppRegistered
    ) { transport, btStatus, wifiStatus, btEnabled, btRegistered ->
        CoreFlows(transport, btStatus, wifiStatus, btEnabled, btRegistered)
    }

    val uiState: StateFlow<RemoteUiState> = combine(
        coreFlows,
        bluetoothHid.devices,
        wifiDiscovery.devices,
        bluetoothHid.isScanning,
        wifiDiscovery.isScanning,
        statusBanner
    ) { values ->
        val core = values[0] as CoreFlows
        @Suppress("UNCHECKED_CAST")
        val btDevices = values[1] as List<TvDevice.BluetoothTv>
        @Suppress("UNCHECKED_CAST")
        val wifiDevices = values[2] as List<TvDevice.WifiTv>
        val btScanning = values[3] as Boolean
        val wifiScanning = values[4] as Boolean
        val banner = values[5] as String?

        val effectiveStatus = when {
            core.btStatus is ConnectionStatus.Connected -> core.btStatus
            core.wifiStatus is ConnectionStatus.Connected -> core.wifiStatus
            core.transport == TransportMode.BLUETOOTH_HID -> core.btStatus
            else -> core.wifiStatus
        }

        RemoteUiState(
            activeTransport = core.transport,
            status = effectiveStatus,
            bluetoothEnabled = core.btEnabled,
            bluetoothHidRegistered = core.btRegistered,
            bluetoothDevices = btDevices,
            wifiDevices = wifiDevices,
            isScanningBluetooth = btScanning,
            isScanningWifi = wifiScanning,
            lastStatusBanner = banner
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = RemoteUiState()
    )

    fun initializeBluetooth() {
        bluetoothHid.ensureInitialized()
    }

    fun selectTransport(mode: TransportMode) {
        activeTransport.value = mode
        if (mode == TransportMode.WIFI_ANDROID_TV) {
            wifiDiscovery.startDiscovery()
        } else {
            wifiDiscovery.stopDiscovery()
            bluetoothHid.ensureInitialized()
        }
    }

    fun startBluetoothScan() {
        activeTransport.value = TransportMode.BLUETOOTH_HID
        bluetoothHid.ensureInitialized()
        bluetoothHid.startDiscovery()
    }

    fun stopBluetoothScan() {
        bluetoothHid.stopDiscovery()
    }

    fun refreshPairedBluetooth() {
        bluetoothHid.ensureInitialized()
        bluetoothHid.refreshPairedDevices()
    }

    fun connectBluetooth(device: TvDevice.BluetoothTv) {
        wifiClient.disconnect()
        activeTransport.value = TransportMode.BLUETOOTH_HID
        bluetoothHid.connect(device.id)
    }

    fun startWifiScan() {
        activeTransport.value = TransportMode.WIFI_ANDROID_TV
        wifiDiscovery.startDiscovery()
    }

    fun stopWifiScan() {
        wifiDiscovery.stopDiscovery()
    }

    fun connectWifi(device: TvDevice.WifiTv, forcePair: Boolean = false) {
        bluetoothHid.disconnect()
        activeTransport.value = TransportMode.WIFI_ANDROID_TV
        wifiDiscovery.stopDiscovery()
        wifiClient.connect(device, forcePair)
    }

    fun connectWifiManual(host: String, name: String = "Mi TV ($host)") {
        val cleanHost = host.trim()
        if (cleanHost.isEmpty()) return
        val device = TvDevice.WifiTv(
            id = "$cleanHost:6466",
            name = name,
            host = cleanHost,
            port = 6466,
            pairingPort = 6467,
            isPaired = certManager.isHostPaired(cleanHost)
        )
        connectWifi(device)
    }

    fun submitWifiPin(pin: String) {
        wifiClient.submitPairingPin(pin)
    }

    fun disconnect() {
        bluetoothHid.disconnect()
        wifiClient.disconnect()
    }

    fun sendButton(button: RemoteButton) {
        when (val current = uiState.value.status) {
            is ConnectionStatus.Connected -> {
                when (current.device.transport) {
                    TransportMode.BLUETOOTH_HID -> bluetoothHid.sendButton(button)
                    TransportMode.WIFI_ANDROID_TV -> wifiClient.sendButton(button)
                }
                statusBanner.value = null
            }
            else -> {
                statusBanner.value = "Not connected to a TV. Tap Connect to pair via Bluetooth or Wi-Fi."
            }
        }
    }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        when (val current = uiState.value.status) {
            is ConnectionStatus.Connected -> {
                when (current.device.transport) {
                    TransportMode.BLUETOOTH_HID -> bluetoothHid.sendText(text)
                    TransportMode.WIFI_ANDROID_TV -> wifiClient.sendText(text)
                }
            }
            else -> {
                statusBanner.value = "Connect to your TV first to send text."
            }
        }
    }

    fun sendMouseMove(dx: Int, dy: Int, wheel: Int = 0) {
        val current = uiState.value.status
        if (current is ConnectionStatus.Connected &&
            current.device.transport == TransportMode.BLUETOOTH_HID
        ) {
            bluetoothHid.sendMouseMove(dx, dy, wheel)
        }
    }

    fun sendMouseClick(button: MouseButton) {
        val current = uiState.value.status
        if (current is ConnectionStatus.Connected) {
            when (current.device.transport) {
                TransportMode.BLUETOOTH_HID -> bluetoothHid.sendMouseClick(button)
                TransportMode.WIFI_ANDROID_TV -> {
                    val mapped = when (button) {
                        MouseButton.LEFT -> RemoteButton.DPAD_CENTER
                        MouseButton.RIGHT -> RemoteButton.BACK
                        MouseButton.MIDDLE -> RemoteButton.HOME
                    }
                    wifiClient.sendButton(mapped)
                }
            }
        } else {
            statusBanner.value = "Connect to your TV first to use touchpad."
        }
    }

    fun clearBanner() {
        statusBanner.value = null
    }

    fun release() {
        bluetoothHid.release()
        wifiDiscovery.stopDiscovery()
        wifiClient.disconnect()
    }
}
