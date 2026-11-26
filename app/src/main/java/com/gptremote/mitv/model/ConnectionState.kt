package com.gptremote.mitv.model

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
    val bluetoothDevices: List<TvDevice.BluetoothTv> = emptyList(),
    val wifiDevices: List<TvDevice.WifiTv> = emptyList(),
    val isScanningWifi: Boolean = false,
    val isScanningBluetooth: Boolean = false,
    val lastStatusBanner: String? = null
) {
    val isConnected: Boolean
        get() = status is ConnectionStatus.Connected

    val connectedDeviceName: String?
        get() = (status as? ConnectionStatus.Connected)?.device?.name
}
