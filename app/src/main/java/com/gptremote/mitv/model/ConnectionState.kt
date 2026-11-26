package com.gptremote.mitv.model

import com.gptremote.mitv.haptic.HapticStrength

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
    val bluetoothEnabled: Boolean = false,
    val bluetoothHidRegistered: Boolean = false,
    val bluetoothPcProfile: Boolean = false,
    val bluetoothDevices: List<TvDevice.BluetoothTv> = emptyList(),
    val wifiDevices: List<TvDevice.WifiTv> = emptyList(),
    val wifiPcDevices: List<TvDevice.WifiPc> = emptyList(),
    val savedDevices: List<SavedDevice> = emptyList(),
    val hapticStrength: HapticStrength = HapticStrength.MEDIUM,
    val isScanningWifi: Boolean = false,
    val isScanningWifiPc: Boolean = false,
    val isScanningBluetooth: Boolean = false,
    val lastStatusBanner: String? = null
) {
    val isConnected: Boolean
        get() = status is ConnectionStatus.Connected

    val connectedDeviceName: String?
        get() = (status as? ConnectionStatus.Connected)?.device?.name

    val connectedCategory: DeviceCategory
        get() = (status as? ConnectionStatus.Connected)?.device?.category ?: DeviceCategory.ANDROID_TV
}
