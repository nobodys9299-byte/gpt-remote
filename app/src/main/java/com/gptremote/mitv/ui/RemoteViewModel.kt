package com.gptremote.mitv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.gptremote.mitv.data.RemoteRepository
import com.gptremote.mitv.model.MouseButton
import com.gptremote.mitv.model.RemoteButton
import com.gptremote.mitv.model.RemoteUiState
import com.gptremote.mitv.model.TransportMode
import com.gptremote.mitv.model.TvDevice
import kotlinx.coroutines.flow.StateFlow

class RemoteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RemoteRepository(application)

    val uiState: StateFlow<RemoteUiState> = repository.uiState

    fun hasBluetoothPermissions(): Boolean = repository.bluetoothHid.hasRequiredPermissions()

    fun onPermissionsGranted() {
        repository.initializeBluetooth()
    }

    fun selectTransport(mode: TransportMode) = repository.selectTransport(mode)

    fun startBluetoothScan() = repository.startBluetoothScan()

    fun stopBluetoothScan() = repository.stopBluetoothScan()

    fun refreshPairedBluetooth() = repository.refreshPairedBluetooth()

    fun connectBluetooth(device: TvDevice.BluetoothTv) = repository.connectBluetooth(device)

    fun startWifiScan() = repository.startWifiScan()

    fun stopWifiScan() = repository.stopWifiScan()

    fun connectWifi(device: TvDevice.WifiTv, forcePair: Boolean = false) =
        repository.connectWifi(device, forcePair)

    fun connectWifiManual(host: String) = repository.connectWifiManual(host)

    fun submitWifiPin(pin: String) = repository.submitWifiPin(pin)

    fun disconnect() = repository.disconnect()

    fun pressButton(button: RemoteButton) = repository.sendButton(button)

    fun sendText(text: String) = repository.sendText(text)

    fun moveMouse(dx: Int, dy: Int, wheel: Int = 0) = repository.sendMouseMove(dx, dy, wheel)

    fun clickMouse(button: MouseButton) = repository.sendMouseClick(button)

    fun clearBanner() = repository.clearBanner()

    override fun onCleared() {
        super.onCleared()
        repository.release()
    }
}
