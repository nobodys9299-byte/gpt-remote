package com.gptremote.mitv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.gptremote.mitv.data.RemoteRepository
import com.gptremote.mitv.haptic.HapticStrength
import com.gptremote.mitv.model.DeviceCategory
import com.gptremote.mitv.model.MouseButton
import com.gptremote.mitv.model.RemoteButton
import com.gptremote.mitv.model.RemoteUiState
import com.gptremote.mitv.model.SavedDevice
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

    fun setHapticStrength(strength: HapticStrength) = repository.setHapticStrength(strength)

    fun setBluetoothPcProfile(enabled: Boolean) = repository.setBluetoothPcProfile(enabled)

    fun selectTransport(mode: TransportMode) = repository.selectTransport(mode)

    fun scanAllDevices() = repository.scanAllDevices()

    fun startBluetoothScan() = repository.startBluetoothScan()

    fun refreshPairedBluetooth() = repository.refreshPairedBluetooth()

    fun connectBluetooth(device: TvDevice.BluetoothTv) = repository.connectBluetooth(device)

    fun startWifiScan() = repository.startWifiScan()

    fun startWifiPcScan() = repository.startWifiPcScan()

    fun connectWifi(device: TvDevice.WifiTv, forcePair: Boolean = false) =
        repository.connectWifi(device, forcePair)

    fun connectWifiPc(device: TvDevice.WifiPc) = repository.connectWifiPc(device)

    fun connectSavedDevice(saved: SavedDevice) = repository.connectSavedDevice(saved)

    fun addAndConnectManualWifi(host: String, nickname: String, category: DeviceCategory) =
        repository.addAndConnectManualWifi(host, nickname, category)

    fun customizeSavedDevice(id: String, nickname: String, category: DeviceCategory, iconKey: String) =
        repository.customizeSavedDevice(id, nickname, category, iconKey)

    fun removeSavedDevice(id: String) = repository.removeSavedDevice(id)

    fun submitWifiPin(pin: String) = repository.submitWifiPin(pin)

    fun disconnect() = repository.disconnect()

    fun pressButton(button: RemoteButton, strongHaptic: Boolean = false) =
        repository.sendButton(button, strongHaptic)

    fun sendText(text: String) = repository.sendText(text)

    fun moveMouse(dx: Int, dy: Int, wheel: Int = 0) = repository.sendMouseMove(dx, dy, wheel)

    fun clickMouse(button: MouseButton) = repository.sendMouseClick(button)

    fun clearBanner() = repository.clearBanner()

    override fun onCleared() {
        super.onCleared()
        repository.release()
    }
}
