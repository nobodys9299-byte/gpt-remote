package com.gptremote.mitv.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.lifecycle.AndroidViewModel
import com.gptremote.mitv.data.RemoteRepository
import com.gptremote.mitv.haptic.HapticStrength
import com.gptremote.mitv.model.ActiveSessionItem
import com.gptremote.mitv.model.BrandOption
import com.gptremote.mitv.model.DeviceCategory
import com.gptremote.mitv.model.LaunchableApp
import com.gptremote.mitv.model.MouseButton
import com.gptremote.mitv.model.RemoteButton
import com.gptremote.mitv.model.RemoteUiState
import com.gptremote.mitv.model.TvDevice
import com.gptremote.mitv.ui.theme.AppAccentTheme
import com.gptremote.mitv.wifi.DiscoveredDlnaTv
import com.gptremote.mitv.wifi.DiscoveredSmartBulb
import com.gptremote.mitv.wifi.HostedMediaItem
import com.gptremote.mitv.wifi.SmartTvProtocol
import kotlinx.coroutines.flow.StateFlow

class RemoteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RemoteRepository(application)

    val uiState: StateFlow<RemoteUiState> = repository.uiState

    fun registerActivityResultCaller(activity: ComponentActivity) {
        repository.googleHomeClient.registerActivityResultCaller(activity)
    }

    fun signInGoogleHome(onComplete: (String) -> Unit = {}) {
        repository.signInGoogleHome(onComplete)
    }

    fun refreshGoogleHomeLights() {
        repository.refreshGoogleHomeLights()
    }

    fun disconnectGoogleHome() {
        repository.disconnectGoogleHome()
    }

    fun hasBluetoothPermissions(): Boolean = repository.bluetoothHid.hasRequiredPermissions()

    fun onPermissionsGranted() = repository.initializeBluetooth()

    fun selectProductBrand(brand: BrandOption) = repository.selectProductBrand(brand)

    fun setIrMode(enabled: Boolean) = repository.setIrMode(enabled)

    fun setHapticStrength(strength: HapticStrength) = repository.setHapticStrength(strength)

    fun setSoundEnabled(enabled: Boolean) = repository.setSoundEnabled(enabled)

    fun setAccentTheme(theme: AppAccentTheme) = repository.setAccentTheme(theme)

    fun setBluetoothPcProfile(enabled: Boolean) = repository.setBluetoothPcProfile(enabled)

    fun allowBluetoothIncomingPairing() = repository.allowBluetoothIncomingPairing()

    fun startBluetoothScan() = repository.startBluetoothScan()

    fun refreshPairedBluetooth() = repository.refreshPairedBluetooth()

    fun connectBluetooth(device: TvDevice.BluetoothTv) = repository.connectBluetooth(device)

    fun startWifiScan() = repository.startWifiScan()

    fun startWifiPcScan() = repository.startWifiPcScan()

    fun startBulbScan() = repository.startBulbScan()

    fun selectBulb(bulb: DiscoveredSmartBulb) = repository.selectBulb(bulb)

    fun connectManualBulb(host: String) = repository.connectManualBulb(host)

    fun setBulbState(
        power: Boolean? = null,
        brightness: Int? = null,
        rgb: Triple<Int, Int, Int>? = null,
        tempKelvin: Int? = null,
        irFallbackAction: String? = null
    ) = repository.setBulbState(power, brightness, rgb, tempKelvin, irFallbackAction)

    fun connectWifi(device: TvDevice.WifiTv, forcePair: Boolean = false) =
        repository.connectWifi(device, forcePair)

    fun connectSmartTvWifi(host: String, name: String, protocol: SmartTvProtocol) =
        repository.connectSmartTvWifi(host, name, protocol)

    fun connectWifiPc(device: TvDevice.WifiPc) = repository.connectWifiPc(device)

    fun reconnectPcIfNeeded() = repository.reconnectPcIfNeeded()

    fun addAndConnectManualWifi(host: String, nickname: String, category: DeviceCategory) =
        repository.addAndConnectManualWifi(host, nickname, category)

    fun submitWifiPin(pin: String) = repository.submitWifiPin(pin)

    fun disconnect() = repository.disconnect()

    fun openActiveSession(session: ActiveSessionItem): BrandOption = repository.openActiveSession(session)

    fun disconnectSession(session: ActiveSessionItem) = repository.disconnectSession(session)

    fun startPcScreenMirror() = repository.startPcScreenMirror()

    fun stopPcScreenMirror() = repository.stopPcScreenMirror()

    fun sendPcScreenTap(nx: Float, ny: Float) = repository.sendPcScreenTap(nx, ny)

    fun sendPcScreenMove(nx: Float, ny: Float) = repository.sendPcScreenMove(nx, ny)

    fun connectFromQr(rawQr: String): String = repository.connectFromQr(rawQr)

    fun startPhoneShareHost() = repository.startPhoneShareHost()

    fun startLocalHotspot() = repository.startLocalHotspot()

    fun stopLocalHotspot() = repository.stopLocalHotspot()

    fun addLocalFileToPhoneShare(uri: Uri) = repository.addLocalFileToPhoneShare(uri)

    fun addLocalFilesToPhoneShare(uris: List<Uri>) = repository.addLocalFilesToPhoneShare(uris)

    fun refreshPeerPhoneFiles() = repository.refreshPeerPhoneFiles()

    fun disconnectPhonePeer() = repository.disconnectPhonePeer()

    fun sendFileToPeerPhone(uri: Uri) = repository.sendFileToPeerPhone(uri)

    fun sendFilesToPeerPhone(uris: List<Uri>) = repository.sendFilesToPeerPhone(uris)

    fun downloadFileFromPeerPhone(fileName: String) = repository.downloadFileFromPeerPhone(fileName)

    fun pausePhoneTransfer() = repository.pausePhoneTransfer()

    fun resumePhoneTransfer() = repository.resumePhoneTransfer()

    fun cancelPhoneTransfer() = repository.cancelPhoneTransfer()

    fun getConnectedPcHost(): String? = repository.getConnectedPcHost()

    fun startPhoneCast(resultCode: Int, data: Intent) = repository.startPhoneCast(resultCode, data)

    fun stopPhoneCast() = repository.stopPhoneCast()

    fun refreshPcFiles() = repository.refreshPcFiles()

    fun uploadFileToPc(uri: Uri) = repository.uploadFileToPc(uri)

    fun uploadFilesToPc(uris: List<Uri>) = repository.uploadFilesToPc(uris)

    fun downloadFileFromPc(fileName: String) = repository.downloadFileFromPc(fileName)

    fun pausePcTransfer() = repository.pausePcTransfer()

    fun resumePcTransfer() = repository.resumePcTransfer()

    fun cancelPcTransfer() = repository.cancelPcTransfer()

    fun startTvCompanionScan() = repository.startTvCompanionScan()

    fun connectTvCompanion(host: String, name: String, token: String) =
        repository.connectTvCompanion(host, name, token)

    fun disconnectTvCompanion() = repository.disconnectTvCompanion()

    fun refreshTvCompanionFiles() = repository.refreshTvCompanionFiles()

    fun uploadFilesToTv(uris: List<Uri>) = repository.uploadFilesToTv(uris)

    fun downloadFileFromTv(fileName: String) = repository.downloadFileFromTv(fileName)

    fun pauseTvTransfer() = repository.pauseTvTransfer()

    fun resumeTvTransfer() = repository.resumeTvTransfer()

    fun cancelTvTransfer() = repository.cancelTvTransfer()

    fun getConnectedTvCompanionHost(): String? = repository.getConnectedTvCompanionHost()

    fun startPhoneCastToTv(resultCode: Int, data: Intent) = repository.startPhoneCastToTv(resultCode, data)

    fun startTvBrowserShareHost() = repository.startTvBrowserShareHost()

    fun openSharedPortalInSmartTvBrowser() = repository.openSharedPortalInSmartTvBrowser()

    fun startDlnaScan() = repository.startDlnaScan()

    fun selectDlnaRenderer(renderer: DiscoveredDlnaTv) = repository.selectDlnaRenderer(renderer)

    fun castHostedItemToDlnaTv(item: HostedMediaItem) = repository.castHostedItemToDlnaTv(item)

    fun dlnaPlaybackCommand(action: String) = repository.dlnaPlaybackCommand(action)

    fun queryInstalledAppsForShare(): List<RemoteRepository.ShareableAppInfo> =
        repository.queryInstalledAppsForShare()

    fun stageAppsAsUris(apps: List<RemoteRepository.ShareableAppInfo>): List<Uri> =
        repository.stageAppsAsUris(apps)

    fun openTransferredFile(activityContext: Context, uriString: String?, fileName: String) =
        repository.openTransferredFile(activityContext, uriString, fileName)

    fun pressButton(button: RemoteButton, strongHaptic: Boolean = false) =
        repository.sendButton(button, strongHaptic)

    fun sendApplianceAction(actionKey: String) = repository.sendApplianceAction(actionKey)

    fun launchApp(app: LaunchableApp) = repository.launchApp(app)

    fun closeApp(app: LaunchableApp) = repository.closeApp(app)

    fun sendText(text: String) = repository.sendText(text)

    fun moveMouse(dx: Int, dy: Int, wheel: Int = 0) = repository.sendMouseMove(dx, dy, wheel)

    fun clickMouse(button: MouseButton) = repository.sendMouseClick(button)

    fun clearBanner() = repository.clearBanner()

    override fun onCleared() {
        super.onCleared()
        repository.release()
    }
}
