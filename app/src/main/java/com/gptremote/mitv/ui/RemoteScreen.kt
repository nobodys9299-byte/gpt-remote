package com.gptremote.mitv.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.gptremote.mitv.haptic.HapticStrength
import com.gptremote.mitv.model.ActiveSessionItem
import com.gptremote.mitv.model.BrandOption
import com.gptremote.mitv.model.ConnectionStatus
import com.gptremote.mitv.model.DeviceCategory
import com.gptremote.mitv.model.LaunchableApp
import com.gptremote.mitv.model.MouseButton
import com.gptremote.mitv.model.ProductCatalog
import com.gptremote.mitv.model.ProductType
import com.gptremote.mitv.model.RemoteButton
import com.gptremote.mitv.model.RemoteUiState
import com.gptremote.mitv.model.TransferHistoryItem
import com.gptremote.mitv.model.TransportMode
import com.gptremote.mitv.model.TvDevice
import com.gptremote.mitv.ui.components.AppLogoBadge
import com.gptremote.mitv.ui.components.ConnectProductSheet
import com.gptremote.mitv.ui.components.DPadControl
import com.gptremote.mitv.ui.components.KeyboardSheet
import com.gptremote.mitv.ui.components.MediaVolumeBar
import com.gptremote.mitv.ui.components.PcFileSharePanel
import com.gptremote.mitv.ui.components.PcScreenSharePanel
import com.gptremote.mitv.ui.components.PhoneSharePanel
import com.gptremote.mitv.ui.components.QrScannerDialog
import com.gptremote.mitv.ui.components.SettingsSheet
import com.gptremote.mitv.ui.components.TouchpadControl
import com.gptremote.mitv.ui.components.TvCompanionSheetDialog
import com.gptremote.mitv.ui.components.TvSheetMode
import com.gptremote.mitv.ui.theme.AppAccentTheme
import com.gptremote.mitv.ui.theme.ConnectedGreen
import com.gptremote.mitv.ui.theme.MiOrange
import com.gptremote.mitv.ui.theme.RemoteBg
import com.gptremote.mitv.ui.theme.RemoteBorder
import com.gptremote.mitv.ui.theme.RemoteSurface
import com.gptremote.mitv.ui.theme.RemoteSurfaceElevated
import com.gptremote.mitv.ui.theme.TextPrimary
import com.gptremote.mitv.ui.theme.TextSecondary
import com.gptremote.mitv.ui.theme.WifiBlue
import com.gptremote.mitv.wifi.DiscoveredDlnaTv
import com.gptremote.mitv.wifi.DiscoveredSmartBulb
import com.gptremote.mitv.wifi.HostedMediaItem
import com.gptremote.mitv.wifi.SharedPcFile
import kotlin.math.roundToInt

private enum class RemoteSubTab(val label: String) {
    PRIMARY("Remote"),
    TOUCHPAD("Touchpad"),
    SCREEN("Screen"),
    FILES("Files"),
    APPS("Apps")
}

@Composable
fun RemoteScreen(
    brand: BrandOption,
    uiState: RemoteUiState,
    hasBluetoothPermissions: Boolean,
    onNavigateBack: () -> Unit,
    onRequestBluetoothPermissions: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onScanBluetooth: () -> Unit,
    onConnectBluetooth: (TvDevice.BluetoothTv) -> Unit,
    onScanWifiTv: () -> Unit,
    onConnectWifiTv: (TvDevice.WifiTv) -> Unit,
    onScanWifiPc: () -> Unit,
    onConnectWifiPc: (TvDevice.WifiPc) -> Unit,
    onConnectManualIp: (host: String, category: DeviceCategory) -> Unit,
    onQrScanned: (String) -> Unit,
    onScanBulbs: () -> Unit,
    onSelectBulb: (DiscoveredSmartBulb) -> Unit,
    onConnectManualBulb: (String) -> Unit,
    onBulbAction: (power: Boolean?, brightness: Int?, rgb: Triple<Int, Int, Int>?, tempKelvin: Int?, irAction: String?) -> Unit,
    onEnableIr: () -> Unit,
    onSubmitWifiPin: (String) -> Unit,
    onDisconnect: () -> Unit,
    onStartPcMirror: () -> Unit,
    onStopPcMirror: () -> Unit,
    onTapMirroredScreen: (Float, Float) -> Unit,
    onMoveMirroredScreen: (Float, Float) -> Unit,
    onOpenFullscreenMirror: () -> Unit = {},
    onStartPhoneCast: () -> Unit,
    onStopPhoneCast: () -> Unit,
    onRefreshPcFiles: () -> Unit,
    onPickFileToSend: () -> Unit,
    onShareAppsToPc: () -> Unit,
    onDownloadPcFile: (SharedPcFile) -> Unit,
    onPausePcTransfer: () -> Unit = {},
    onResumePcTransfer: () -> Unit = {},
    onCancelPcTransfer: () -> Unit = {},
    onStartPhoneShareHost: () -> Unit,
    onStartLocalHotspot: () -> Unit,
    onStopLocalHotspot: () -> Unit,
    onOpenHotspotSettings: () -> Unit,
    onPickHostFileToShare: () -> Unit,
    onShareHostApps: () -> Unit,
    onDisconnectPhonePeer: () -> Unit = {},
    onRefreshPeerPhoneFiles: () -> Unit,
    onPickFileForPeerPhone: () -> Unit,
    onShareAppsToPeerPhone: () -> Unit,
    onDownloadFromPeerPhone: (SharedPcFile) -> Unit,
    onPausePhoneTransfer: () -> Unit = {},
    onResumePhoneTransfer: () -> Unit = {},
    onCancelPhoneTransfer: () -> Unit = {},
    onOpenHistoryFile: (TransferHistoryItem) -> Unit,
    onScanTvCompanion: () -> Unit,
    onConnectTvManual: (host: String, token: String) -> Unit,
    onSelectTvCompanion: (host: String, name: String, token: String) -> Unit,
    onDisconnectTv: () -> Unit,
    onRefreshTvFiles: () -> Unit,
    onPickFilesForTv: () -> Unit,
    onShareAppsToTv: () -> Unit,
    onDownloadTvFile: (SharedPcFile) -> Unit,
    onPauseTvTransfer: () -> Unit = {},
    onResumeTvTransfer: () -> Unit = {},
    onCancelTvTransfer: () -> Unit = {},
    onStartPhoneCastToTv: () -> Unit,
    onOpenSystemCastSettings: () -> Unit,
    onStartTvBrowserHost: () -> Unit,
    onAddBrowserDlnaFiles: () -> Unit,
    onOpenInTvBrowser: () -> Unit,
    onScanDlna: () -> Unit,
    onSelectDlnaRenderer: (DiscoveredDlnaTv) -> Unit,
    onCastMediaDlna: (HostedMediaItem) -> Unit,
    onDlnaPlaybackAction: (String) -> Unit,
    onButtonPress: (RemoteButton, Boolean) -> Unit,
    onApplianceAction: (String) -> Unit,
    onLaunchApp: (LaunchableApp) -> Unit,
    onCloseApp: (LaunchableApp) -> Unit = {},
    onSendText: (String) -> Unit,
    onMouseMove: (dx: Int, dy: Int, wheel: Int) -> Unit,
    onMouseClick: (MouseButton) -> Unit,
    onSelectHapticStrength: (HapticStrength) -> Unit,
    onToggleSound: (Boolean) -> Unit,
    onSelectAccentTheme: (AppAccentTheme) -> Unit,
    onClearBanner: () -> Unit,
    onOpenActiveSession: (ActiveSessionItem) -> Unit = {},
    onDisconnectSession: (ActiveSessionItem) -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showConnectSheet by remember { mutableStateOf(false) }
    var showKeyboardSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showPhoneQrScanner by remember { mutableStateOf(false) }
    var tvSheetMode by remember { mutableStateOf<TvSheetMode?>(null) }
    var bulbPower by remember { mutableStateOf(true) }

    val statusError = (uiState.status as? ConnectionStatus.Error)?.message
    val bannerMessage = uiState.lastStatusBanner ?: statusError
    LaunchedEffect(bannerMessage) {
        if (!bannerMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(bannerMessage)
            onClearBanner()
        }
    }

    LaunchedEffect(uiState.status) {
        if (uiState.status is ConnectionStatus.AwaitingWifiPin) {
            showConnectSheet = true
        }
    }

    tvSheetMode?.let { mode ->
        TvCompanionSheetDialog(
            uiState = uiState,
            sheetMode = mode,
            preferNonAndroidTab = brand.id == "tv_samsung" || brand.id == "tv_lg",
            onScanTvCompanion = onScanTvCompanion,
            onScanTvQr = { showPhoneQrScanner = true },
            onConnectTvManual = onConnectTvManual,
            onSelectTvCompanion = onSelectTvCompanion,
            onDisconnectTv = onDisconnectTv,
            onRefreshTvFiles = onRefreshTvFiles,
            onSendFilesToTv = onPickFilesForTv,
            onSendAppsToTv = onShareAppsToTv,
            onDownloadTvFile = onDownloadTvFile,
            onPauseTvTransfer = onPauseTvTransfer,
            onResumeTvTransfer = onResumeTvTransfer,
            onCancelTvTransfer = onCancelTvTransfer,
            onStartPhoneCastToTv = onStartPhoneCastToTv,
            onStopPhoneCast = onStopPhoneCast,
            onOpenSystemCastSettings = onOpenSystemCastSettings,
            onStartTvBrowserHost = onStartTvBrowserHost,
            onAddBrowserDlnaFiles = onAddBrowserDlnaFiles,
            onOpenInTvBrowser = onOpenInTvBrowser,
            onScanDlna = onScanDlna,
            onSelectDlnaRenderer = onSelectDlnaRenderer,
            onCastMediaDlna = onCastMediaDlna,
            onDlnaPlaybackAction = onDlnaPlaybackAction,
            onOpenHistoryFile = onOpenHistoryFile,
            onDismiss = { tvSheetMode = null }
        )
    }

    if (showPhoneQrScanner) {
        QrScannerDialog(
            title = "Scan Device QR Code",
            subtitle = "Point camera at Phone Share, PC, or Smart TV QR code",
            onScanned = { payload ->
                showPhoneQrScanner = false
                onQrScanned(payload)
            },
            onDismiss = { showPhoneQrScanner = false }
        )
    }

    Scaffold(
        containerColor = RemoteBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RemoteTopBar(
                brand = brand,
                uiState = uiState,
                onBack = onNavigateBack,
                onOpenConnect = {
                    if (brand.productType == ProductType.PHONE_TO_PHONE) {
                        showPhoneQrScanner = true
                    } else {
                        showConnectSheet = true
                    }
                },
                onOpenSettings = { showSettingsSheet = true },
                onOpenActiveSession = onOpenActiveSession,
                onDisconnectSession = onDisconnectSession,
                onPowerClick = {
                    when (brand.productType) {
                        ProductType.AC,
                        ProductType.FRIDGE,
                        ProductType.WASHING_MACHINE,
                        ProductType.CEILING_FAN -> onApplianceAction("power")
                        ProductType.SMART_BULB -> {
                            bulbPower = !bulbPower
                            onBulbAction(
                                bulbPower,
                                null,
                                null,
                                null,
                                if (bulbPower) "power_on" else "power_off"
                            )
                        }
                        else -> onButtonPress(RemoteButton.POWER, true)
                    }
                }
            )

            // Dedicated Product Remote Body — ZERO false buttons
            when (brand.productType) {
                ProductType.SMART_TV -> TvRemoteBody(
                    brand = brand,
                    uiState = uiState,
                    onButtonPress = onButtonPress,
                    onMouseMove = onMouseMove,
                    onMouseClick = onMouseClick,
                    onLaunchApp = onLaunchApp,
                    onCloseApp = onCloseApp,
                    onOpenKeyboard = { showKeyboardSheet = true },
                    onOpenTvFileShare = { tvSheetMode = TvSheetMode.FILE_SHARE },
                    onOpenTvScreenCast = { tvSheetMode = TvSheetMode.SCREEN_CAST }
                )

                ProductType.PC_LAPTOP -> PcRemoteBody(
                    uiState = uiState,
                    onButtonPress = onButtonPress,
                    onMouseMove = onMouseMove,
                    onMouseClick = onMouseClick,
                    onLaunchApp = onLaunchApp,
                    onCloseApp = onCloseApp,
                    onOpenKeyboard = { showKeyboardSheet = true },
                    onStartPcMirror = onStartPcMirror,
                    onStopPcMirror = onStopPcMirror,
                    onTapMirroredScreen = onTapMirroredScreen,
                    onMoveMirroredScreen = onMoveMirroredScreen,
                    onOpenFullscreenMirror = onOpenFullscreenMirror,
                    onStartPhoneCast = onStartPhoneCast,
                    onStopPhoneCast = onStopPhoneCast,
                    onRefreshPcFiles = onRefreshPcFiles,
                    onPickFileToSend = onPickFileToSend,
                    onShareAppsToPc = onShareAppsToPc,
                    onDownloadPcFile = onDownloadPcFile,
                    onPausePcTransfer = onPausePcTransfer,
                    onResumePcTransfer = onResumePcTransfer,
                    onCancelPcTransfer = onCancelPcTransfer,
                    onOpenHistoryFile = onOpenHistoryFile,
                    onOpenConnect = { showConnectSheet = true }
                )

                ProductType.AC -> AcRemoteBody(onAction = onApplianceAction)

                ProductType.FRIDGE -> FridgeRemoteBody(onAction = onApplianceAction)

                ProductType.WASHING_MACHINE -> WashingMachineRemoteBody(onAction = onApplianceAction)

                ProductType.SET_TOP_BOX -> SetTopBoxRemoteBody(
                    brand = brand,
                    onButtonPress = onButtonPress,
                    onLaunchApp = onLaunchApp,
                    onCloseApp = onCloseApp
                )

                ProductType.SOUNDBAR -> SoundbarRemoteBody(onButtonPress = onButtonPress)

                ProductType.PROJECTOR -> ProjectorRemoteBody(
                    brand = brand,
                    uiState = uiState,
                    onButtonPress = onButtonPress,
                    onMouseMove = onMouseMove,
                    onMouseClick = onMouseClick,
                    onLaunchApp = onLaunchApp,
                    onCloseApp = onCloseApp
                )

                ProductType.CEILING_FAN -> FanRemoteBody(onAction = onApplianceAction)

                ProductType.SMART_BULB -> SmartBulbRemoteBody(
                    isPowerOn = bulbPower,
                    onTogglePower = {
                        bulbPower = !bulbPower
                        onBulbAction(
                            bulbPower,
                            null,
                            null,
                            null,
                            if (bulbPower) "power_on" else "power_off"
                        )
                    },
                    onBulbAction = onBulbAction
                )

                ProductType.PHONE_TO_PHONE -> PhoneSharePanel(
                    uiState = uiState,
                    onStartHost = onStartPhoneShareHost,
                    onStartLocalHotspot = onStartLocalHotspot,
                    onStopLocalHotspot = onStopLocalHotspot,
                    onOpenHotspotSettings = onOpenHotspotSettings,
                    onAddHostFiles = onPickHostFileToShare,
                    onShareHostApps = onShareHostApps,
                    onScanPeerQr = { showPhoneQrScanner = true },
                    onDisconnectPeer = onDisconnectPhonePeer,
                    onRefreshPeerFiles = onRefreshPeerPhoneFiles,
                    onSendFilesToPeer = onPickFileForPeerPhone,
                    onSendAppsToPeer = onShareAppsToPeerPhone,
                    onDownloadFromPeer = onDownloadFromPeerPhone,
                    onOpenHistoryFile = onOpenHistoryFile,
                    onPauseTransfer = onPausePhoneTransfer,
                    onResumeTransfer = onResumePhoneTransfer,
                    onCancelTransfer = onCancelPhoneTransfer
                )
            }
        }
    }

    if (showConnectSheet) {
        ConnectProductSheet(
            brand = brand,
            uiState = uiState,
            hasBluetoothPermissions = hasBluetoothPermissions,
            onRequestBluetoothPermissions = onRequestBluetoothPermissions,
            onMakeDiscoverable = onMakeDiscoverable,
            onScanBluetooth = onScanBluetooth,
            onConnectBluetooth = onConnectBluetooth,
            onScanWifiTv = onScanWifiTv,
            onConnectWifiTv = onConnectWifiTv,
            onScanWifiPc = onScanWifiPc,
            onConnectWifiPc = onConnectWifiPc,
            onConnectManualIp = onConnectManualIp,
            onQrScanned = onQrScanned,
            onScanBulbs = onScanBulbs,
            onSelectBulb = onSelectBulb,
            onConnectManualBulb = onConnectManualBulb,
            onEnableIr = onEnableIr,
            onSubmitWifiPin = onSubmitWifiPin,
            onDisconnect = onDisconnect,
            onDismiss = { showConnectSheet = false }
        )
    }

    if (showKeyboardSheet) {
        KeyboardSheet(
            onSendText = onSendText,
            onButtonPress = { btn -> onButtonPress(btn, false) },
            onDismiss = { showKeyboardSheet = false }
        )
    }

    if (showSettingsSheet) {
        SettingsSheet(
            hapticStrength = uiState.hapticStrength,
            soundEnabled = uiState.soundEnabled,
            accentTheme = uiState.accentTheme,
            onSelectHapticStrength = onSelectHapticStrength,
            onToggleSound = onToggleSound,
            onSelectAccentTheme = onSelectAccentTheme,
            onScanQrCode = { showPhoneQrScanner = true },
            phoneQrPayload = uiState.phoneHostQrUrl,
            onRequestHostQr = onStartPhoneShareHost,
            onDismiss = { showSettingsSheet = false }
        )
    }
}

@Composable
private fun RemoteTopBar(
    brand: BrandOption,
    uiState: RemoteUiState,
    onBack: () -> Unit,
    onOpenConnect: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenActiveSession: (ActiveSessionItem) -> Unit,
    onDisconnectSession: (ActiveSessionItem) -> Unit,
    onPowerClick: () -> Unit
) {
    val accent = MiOrange
    val sessions = uiState.activeSessions
    var showSessionMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = RemoteSurfaceElevated,
                contentColor = TextPrimary
            )
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }

        val matchingConnected = uiState.isConnectedForBrand(brand)
        val (pillTitle, dotColor, icon) = when {
            brand.productType == ProductType.PHONE_TO_PHONE -> {
                uiState.peerPhoneName?.let {
                    Triple("Peer: $it", ConnectedGreen, Icons.Rounded.QrCodeScanner)
                } ?: Triple("Scan Phone QR", WifiBlue, Icons.Rounded.QrCodeScanner)
            }
            brand.productType == ProductType.SMART_BULB -> {
                uiState.connectedBulb?.let {
                    Triple(it.name, ConnectedGreen, Icons.Rounded.Lightbulb)
                } ?: if (uiState.irActive && uiState.hasIrEmitter) {
                    Triple("${brand.name} • IR Ready", ConnectedGreen, Icons.Rounded.SettingsRemote)
                } else {
                    Triple("Connect ${brand.name}", TextSecondary, Icons.Rounded.Lightbulb)
                }
            }
            matchingConnected && uiState.status is ConnectionStatus.Connected -> {
                val st = uiState.status
                val ic = if (st.device.transport == TransportMode.BLUETOOTH_HID) {
                    Icons.Rounded.Bluetooth
                } else {
                    Icons.Rounded.Wifi
                }
                Triple(st.device.name, ConnectedGreen, ic)
            }
            uiState.status is ConnectionStatus.Connecting -> {
                val ic = if (uiState.status.transport == TransportMode.BLUETOOTH_HID) {
                    Icons.Rounded.Bluetooth
                } else {
                    Icons.Rounded.Wifi
                }
                Triple("Connecting...", accent, ic)
            }
            uiState.status is ConnectionStatus.AwaitingWifiPin -> {
                Triple("Enter PIN", WifiBlue, Icons.Rounded.Wifi)
            }
            uiState.irActive && uiState.hasIrEmitter -> {
                Triple("${brand.name} • IR Ready", ConnectedGreen, Icons.Rounded.SettingsRemote)
            }
            else -> {
                val ic = if (brand.productType == ProductType.PC_LAPTOP) Icons.Rounded.Wifi else Icons.Rounded.Bluetooth
                Triple("Connect ${brand.name}", TextSecondary, ic)
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(RemoteSurfaceElevated)
                    .border(1.dp, RemoteBorder, RoundedCornerShape(22.dp))
                    .clickable(onClick = onOpenConnect)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(icon, contentDescription = null, tint = dotColor, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = pillTitle,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (sessions.size > 1) {
                Box {
                    Row(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(ConnectedGreen.copy(alpha = 0.16f))
                            .border(1.dp, ConnectedGreen.copy(alpha = 0.65f), RoundedCornerShape(22.dp))
                            .clickable { showSessionMenu = true }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "${sessions.size}",
                            color = ConnectedGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Switch connected device",
                            tint = ConnectedGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showSessionMenu,
                        onDismissRequest = { showSessionMenu = false },
                        containerColor = RemoteSurfaceElevated
                    ) {
                        sessions.forEach { session ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(ConnectedGreen)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = session.title,
                                                color = TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = session.subtitle,
                                                color = TextSecondary,
                                                fontSize = 11.sp,
                                                maxLines = 1
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                showSessionMenu = false
                                                onDisconnectSession(session)
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Disconnect ${session.title}",
                                                tint = Color(0xFFF87171),
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    showSessionMenu = false
                                    onOpenActiveSession(session)
                                }
                            )
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalIconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = RemoteSurfaceElevated,
                    contentColor = TextPrimary
                )
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings")
            }
            if (brand.productType != ProductType.PHONE_TO_PHONE) {
                FilledTonalIconButton(
                    onClick = onPowerClick,
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0xFF341A1D),
                        contentColor = Color(0xFFF87171)
                    )
                ) {
                    Icon(Icons.Rounded.PowerSettingsNew, contentDescription = "Power")
                }
            }
        }
    }
}

@Composable
private fun TvRemoteBody(
    brand: BrandOption,
    uiState: RemoteUiState,
    onButtonPress: (RemoteButton, Boolean) -> Unit,
    onMouseMove: (Int, Int, Int) -> Unit,
    onMouseClick: (MouseButton) -> Unit,
    onLaunchApp: (LaunchableApp) -> Unit,
    onCloseApp: (LaunchableApp) -> Unit = {},
    onOpenKeyboard: () -> Unit,
    onOpenTvFileShare: () -> Unit,
    onOpenTvScreenCast: () -> Unit
) {
    val tabs = remember(brand) {
        buildList {
            add(RemoteSubTab.PRIMARY)
            add(RemoteSubTab.TOUCHPAD)
            if (brand.supportsAppLaunch) add(RemoteSubTab.APPS)
        }
    }
    var selectedTab by remember(brand) { mutableStateOf(RemoteSubTab.PRIMARY) }

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, tab ->
            val icon = when (tab) {
                RemoteSubTab.PRIMARY -> Icons.Rounded.Gamepad
                RemoteSubTab.TOUCHPAD -> Icons.Rounded.TouchApp
                RemoteSubTab.SCREEN -> Icons.Rounded.Cast
                RemoteSubTab.FILES -> Icons.Rounded.Folder
                RemoteSubTab.APPS -> Icons.Rounded.Apps
            }
            SegmentedButton(
                selected = selectedTab == tab,
                onClick = { selectedTab = tab },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(tab.label, fontSize = 12.sp) }
            )
        }
    }

    when (selectedTab) {
        RemoteSubTab.PRIMARY -> {
            DPadControl(
                onButtonPress = { btn -> onButtonPress(btn, btn == RemoteButton.DPAD_CENTER) }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NavCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back") {
                    onButtonPress(RemoteButton.BACK, false)
                }
                NavCircleButton(Icons.Rounded.Home, "Home", highlight = true) {
                    onButtonPress(RemoteButton.HOME, true)
                }
                NavCircleButton(Icons.Rounded.Menu, "Menu") {
                    onButtonPress(RemoteButton.MENU, false)
                }
                NavCircleButton(Icons.Rounded.Keyboard, "Keyboard") {
                    onOpenKeyboard()
                }
            }
            MediaVolumeBar(
                onButtonPress = { btn -> onButtonPress(btn, btn == RemoteButton.MUTE) },
                showChannelRocker = true,
                showMediaRow = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickShortcutButton(Icons.Rounded.Search, "Search", Modifier.weight(1f)) {
                    onButtonPress(RemoteButton.SEARCH, false)
                }
                QuickShortcutButton(Icons.AutoMirrored.Rounded.Input, "Input", Modifier.weight(1f)) {
                    onButtonPress(RemoteButton.TV_INPUT, false)
                }
                QuickShortcutButton(Icons.Rounded.Settings, "Settings", Modifier.weight(1f)) {
                    onButtonPress(RemoteButton.SETTINGS, false)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickShortcutButton(
                    icon = Icons.Rounded.Folder,
                    label = if (uiState.connectedTvCompanion != null) "TV Share (Live)" else "File Share TV",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenTvFileShare
                )
                QuickShortcutButton(
                    icon = Icons.Rounded.Cast,
                    label = if (uiState.isCastingToTv) "Casting TV" else "Screen Cast TV",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenTvScreenCast
                )
            }
        }

        RemoteSubTab.TOUCHPAD -> {
            TouchpadControl(
                transportMode = (uiState.status as? ConnectionStatus.Connected)?.device?.transport
                    ?: uiState.activeTransport,
                onMouseMove = onMouseMove,
                onMouseClick = onMouseClick,
                onButtonFallback = { btn -> onButtonPress(btn, false) },
                showClickButtons = false
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NavCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back") {
                    onButtonPress(RemoteButton.BACK, false)
                }
                NavCircleButton(Icons.Rounded.Home, "Home", highlight = true) {
                    onButtonPress(RemoteButton.HOME, true)
                }
                NavCircleButton(Icons.Rounded.Keyboard, "Keyboard") {
                    onOpenKeyboard()
                }
            }
            if (brand.supportsAppLaunch) {
                QuickAppStrip(apps = ProductCatalog.tvApps.take(4), onLaunch = onLaunchApp)
            }
        }

        RemoteSubTab.APPS -> {
            AppLauncherGrid(
                apps = ProductCatalog.tvApps,
                storageKey = "tv_apps_order",
                onLaunch = onLaunchApp,
                onClose = onCloseApp
            )
        }

        RemoteSubTab.SCREEN, RemoteSubTab.FILES -> Unit
    }
}

@Composable
private fun QuickAppStrip(
    apps: List<LaunchableApp>,
    onLaunch: (LaunchableApp) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        apps.forEach { app ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(RemoteSurfaceElevated)
                    .border(1.dp, RemoteBorder, RoundedCornerShape(14.dp))
                    .clickable { onLaunch(app) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AppLogoBadge(app = app, modifier = Modifier.size(34.dp))
                Text(
                    text = app.name,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PcRemoteBody(
    uiState: RemoteUiState,
    onButtonPress: (RemoteButton, Boolean) -> Unit,
    onMouseMove: (Int, Int, Int) -> Unit,
    onMouseClick: (MouseButton) -> Unit,
    onLaunchApp: (LaunchableApp) -> Unit,
    onCloseApp: (LaunchableApp) -> Unit = {},
    onOpenKeyboard: () -> Unit,
    onStartPcMirror: () -> Unit,
    onStopPcMirror: () -> Unit,
    onTapMirroredScreen: (Float, Float) -> Unit,
    onMoveMirroredScreen: (Float, Float) -> Unit,
    onOpenFullscreenMirror: () -> Unit,
    onStartPhoneCast: () -> Unit,
    onStopPhoneCast: () -> Unit,
    onRefreshPcFiles: () -> Unit,
    onPickFileToSend: () -> Unit,
    onShareAppsToPc: () -> Unit,
    onDownloadPcFile: (SharedPcFile) -> Unit,
    onPausePcTransfer: () -> Unit,
    onResumePcTransfer: () -> Unit,
    onCancelPcTransfer: () -> Unit,
    onOpenHistoryFile: (TransferHistoryItem) -> Unit,
    onOpenConnect: () -> Unit
) {
    // PC tab order: Touchpad | Apps | Screen | Files
    var selectedTab by remember { mutableStateOf(RemoteSubTab.TOUCHPAD) }
    val pcTabs = listOf(
        RemoteSubTab.TOUCHPAD,
        RemoteSubTab.APPS,
        RemoteSubTab.SCREEN,
        RemoteSubTab.FILES
    )

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        pcTabs.forEachIndexed { index, tab ->
            SegmentedButton(
                selected = selectedTab == tab,
                onClick = { selectedTab = tab },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = pcTabs.size),
                label = { Text(tab.label, fontSize = 12.sp, maxLines = 1) }
            )
        }
    }

    when (selectedTab) {
        RemoteSubTab.TOUCHPAD -> {
            val activeTransport = (uiState.status as? ConnectionStatus.Connected)?.device?.transport
                ?: TransportMode.WIFI_PC
            TouchpadControl(
                transportMode = activeTransport,
                onMouseMove = onMouseMove,
                onMouseClick = onMouseClick,
                onButtonFallback = { btn -> onButtonPress(btn, false) },
                showClickButtons = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { onButtonPress(RemoteButton.ESCAPE, false) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Esc", fontSize = 12.sp, maxLines = 1) }
                FilledTonalButton(
                    onClick = { onButtonPress(RemoteButton.PC_WIN_KEY, true) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Start", fontSize = 12.sp, maxLines = 1) }
                FilledTonalButton(
                    onClick = { onButtonPress(RemoteButton.PC_ALT_TAB, false) },
                    modifier = Modifier.weight(1.1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Alt+Tab", fontSize = 12.sp, maxLines = 1) }
                FilledTonalButton(
                    onClick = onOpenKeyboard,
                    modifier = Modifier.weight(0.9f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Keyboard, contentDescription = "Keyboard", modifier = Modifier.size(18.dp))
                }
            }
            QuickAppStrip(apps = ProductCatalog.pcApps.take(4), onLaunch = onLaunchApp)
        }

        RemoteSubTab.SCREEN -> {
            PcScreenSharePanel(
                uiState = uiState,
                onStartPcMirror = onStartPcMirror,
                onStopPcMirror = onStopPcMirror,
                onTapMirroredScreen = onTapMirroredScreen,
                onMoveMirroredScreen = onMoveMirroredScreen,
                onOpenFullscreenMirror = onOpenFullscreenMirror,
                onStartPhoneCast = onStartPhoneCast,
                onStopPhoneCast = onStopPhoneCast,
                onOpenConnect = onOpenConnect
            )
        }

        RemoteSubTab.FILES -> {
            PcFileSharePanel(
                uiState = uiState,
                onRefreshFiles = onRefreshPcFiles,
                onPickFilesToSend = onPickFileToSend,
                onShareAppsToPc = onShareAppsToPc,
                onDownloadFile = onDownloadPcFile,
                onOpenHistoryFile = onOpenHistoryFile,
                onPauseTransfer = onPausePcTransfer,
                onResumeTransfer = onResumePcTransfer,
                onCancelTransfer = onCancelPcTransfer,
                onOpenConnect = onOpenConnect
            )
        }

        RemoteSubTab.APPS -> {
            AppLauncherGrid(
                apps = ProductCatalog.pcApps,
                storageKey = "pc_apps_order",
                onLaunch = onLaunchApp,
                onClose = onCloseApp
            )

            // Shortcut keys & Volume/Media merged into Apps cleanly in 2 balanced rows
            listOf(
                listOf(RemoteButton.PC_COPY to "Copy", RemoteButton.PC_PASTE to "Paste"),
                listOf(RemoteButton.PC_SHOW_DESKTOP to "Desktop", RemoteButton.TAB to "Tab")
            ).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { (btn, label) ->
                        FilledTonalButton(
                            onClick = { onButtonPress(btn, false) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(label, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
            }

            MediaVolumeBar(
                onButtonPress = { btn -> onButtonPress(btn, btn == RemoteButton.MUTE) },
                showChannelRocker = false,
                showMediaRow = true
            )

            Button(
                onClick = onOpenKeyboard,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MiOrange),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Keyboard, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Full PC Keyboard", fontWeight = FontWeight.SemiBold)
            }
        }

        RemoteSubTab.PRIMARY -> Unit
    }
}

@Composable
private fun AcRemoteBody(onAction: (String) -> Unit) {
    var temp by remember { mutableIntStateOf(24) }
    var modeIdx by remember { mutableIntStateOf(0) }
    var fanIdx by remember { mutableIntStateOf(0) }
    var swingOn by remember { mutableStateOf(true) }
    var turboOn by remember { mutableStateOf(false) }
    var sleepOn by remember { mutableStateOf(false) }
    var timerHours by remember { mutableIntStateOf(0) }

    val modes = listOf("Cool", "Dry", "Fan", "Auto", "Heat")
    val fanSpeeds = listOf("Auto", "Low", "Medium", "High")
    val accent = MiOrange

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A2436), RemoteSurfaceElevated)
                )
            )
            .border(1.5.dp, WifiBlue.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Rounded.AcUnit, contentDescription = null, tint = WifiBlue, modifier = Modifier.size(30.dp))
        Text(
            text = "${temp}°C",
            color = TextPrimary,
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "${modes[modeIdx]}  •  Fan: ${fanSpeeds[fanIdx]}",
            color = TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusBadgePill("Swing: ${if (swingOn) "ON" else "OFF"}", swingOn, WifiBlue)
            StatusBadgePill("Turbo", turboOn, accent)
            StatusBadgePill("Sleep", sleepOn, Color(0xFFA78BFA))
            StatusBadgePill(if (timerHours == 0) "Timer Off" else "${timerHours}h", timerHours > 0, ConnectedGreen)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FilledTonalButton(
            onClick = {
                if (temp > 16) temp--
                onAction("temp_down")
            },
            modifier = Modifier
                .weight(1f)
                .height(64.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Rounded.Remove, contentDescription = "Decrease Temp")
            Spacer(modifier = Modifier.width(6.dp))
            Text("Temp -", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Button(
            onClick = {
                if (temp < 30) temp++
                onAction("temp_up")
            },
            modifier = Modifier
                .weight(1f)
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Increase Temp")
            Spacer(modifier = Modifier.width(6.dp))
            Text("Temp +", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    ApplianceToggleGrid(
        items = listOf(
            ToggleActionItem("Mode (${modes[modeIdx]})", false) {
                modeIdx = (modeIdx + 1) % modes.size
                onAction("mode")
            },
            ToggleActionItem("Fan (${fanSpeeds[fanIdx]})", false) {
                fanIdx = (fanIdx + 1) % fanSpeeds.size
                onAction("fan")
            },
            ToggleActionItem("Swing ${if (swingOn) "ON" else "OFF"}", swingOn) {
                swingOn = !swingOn
                onAction("swing")
            },
            ToggleActionItem("Turbo ${if (turboOn) "ON" else "OFF"}", turboOn) {
                turboOn = !turboOn
                onAction("turbo")
            },
            ToggleActionItem("Sleep ${if (sleepOn) "ON" else "OFF"}", sleepOn) {
                sleepOn = !sleepOn
                onAction("sleep")
            },
            ToggleActionItem(if (timerHours == 0) "Timer Off" else "Timer ${timerHours}h", timerHours > 0) {
                timerHours = when (timerHours) {
                    0 -> 1
                    1 -> 2
                    2 -> 4
                    4 -> 8
                    else -> 0
                }
                onAction("timer")
            }
        )
    )
}

@Composable
private fun FridgeRemoteBody(onAction: (String) -> Unit) {
    var fridgeTemp by remember { mutableIntStateOf(4) }
    var freezerTemp by remember { mutableIntStateOf(-18) }
    var superCool by remember { mutableStateOf(false) }
    var powerFreeze by remember { mutableStateOf(false) }
    var ecoMode by remember { mutableStateOf(false) }
    var iceMaker by remember { mutableStateOf(true) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(RemoteSurfaceElevated)
                .border(1.5.dp, ConnectedGreen.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.Kitchen, contentDescription = null, tint = ConnectedGreen)
            Text("Fridge Zone", color = TextSecondary, fontSize = 12.sp)
            Text("${fridgeTemp}°C", color = TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(onClick = {
                    if (fridgeTemp > 1) fridgeTemp--
                    onAction("fridge_down")
                }) { Icon(Icons.Rounded.Remove, contentDescription = "Cooler") }
                FilledTonalIconButton(onClick = {
                    if (fridgeTemp < 8) fridgeTemp++
                    onAction("fridge_up")
                }) { Icon(Icons.Rounded.Add, contentDescription = "Warmer") }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(RemoteSurfaceElevated)
                .border(1.5.dp, WifiBlue.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.AcUnit, contentDescription = null, tint = WifiBlue)
            Text("Freezer", color = TextSecondary, fontSize = 12.sp)
            Text("${freezerTemp}°C", color = TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(onClick = {
                    if (freezerTemp > -24) freezerTemp--
                    onAction("freezer_down")
                }) { Icon(Icons.Rounded.Remove, contentDescription = "Colder") }
                FilledTonalIconButton(onClick = {
                    if (freezerTemp < -14) freezerTemp++
                    onAction("freezer_up")
                }) { Icon(Icons.Rounded.Add, contentDescription = "Warmer") }
            }
        }
    }

    ApplianceToggleGrid(
        items = listOf(
            ToggleActionItem("Super Cool", superCool) {
                superCool = !superCool
                onAction("super_cool")
            },
            ToggleActionItem("Power Freeze", powerFreeze) {
                powerFreeze = !powerFreeze
                onAction("turbo")
            },
            ToggleActionItem("Eco Mode", ecoMode) {
                ecoMode = !ecoMode
                onAction("eco")
            },
            ToggleActionItem("Ice Maker ${if (iceMaker) "ON" else "OFF"}", iceMaker) {
                iceMaker = !iceMaker
                onAction("ice_maker")
            }
        )
    )
}

@Composable
private fun WashingMachineRemoteBody(onAction: (String) -> Unit) {
    var cycleIdx by remember { mutableIntStateOf(0) }
    var spinIdx by remember { mutableIntStateOf(1) }
    var waterTempIdx by remember { mutableIntStateOf(1) }
    var delayHours by remember { mutableIntStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }

    val cycles = listOf("Cotton", "Quick 15'", "Delicates", "Heavy Duty", "Rinse + Spin")
    val spins = listOf("600 RPM", "1000 RPM", "1200 RPM", "1400 RPM")
    val waterTemps = listOf("Cold", "30°C", "40°C", "60°C")
    val washerPurple = Color(0xFFA78BFA)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF231D36), RemoteSurfaceElevated)
                )
            )
            .border(1.5.dp, washerPurple.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Rounded.LocalLaundryService, contentDescription = null, tint = washerPurple, modifier = Modifier.size(32.dp))
        Text(cycles[cycleIdx], color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Spin: ${spins[spinIdx]}  •  Temp: ${waterTemps[waterTempIdx]}", color = TextSecondary, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadgePill(if (isRunning) "Washing Active" else "Ready", isRunning, ConnectedGreen)
            StatusBadgePill(if (delayHours == 0) "No Delay" else "Delay ${delayHours}h", delayHours > 0, washerPurple)
        }
    }

    Button(
        onClick = {
            isRunning = !isRunning
            onAction("start_pause")
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) Color(0xFF334155) else MiOrange),
        shape = RoundedCornerShape(18.dp)
    ) {
        Icon(
            imageVector = if (isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (isRunning) "Pause Wash" else "Start Wash", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }

    ApplianceToggleGrid(
        items = listOf(
            ToggleActionItem("Cycle (${cycles[cycleIdx]})", false) {
                cycleIdx = (cycleIdx + 1) % cycles.size
                onAction("cycle")
            },
            ToggleActionItem("Spin (${spins[spinIdx]})", false) {
                spinIdx = (spinIdx + 1) % spins.size
                onAction("spin")
            },
            ToggleActionItem("Water (${waterTemps[waterTempIdx]})", false) {
                waterTempIdx = (waterTempIdx + 1) % waterTemps.size
                onAction("temp_cycle")
            },
            ToggleActionItem(if (delayHours == 0) "Delay End Off" else "Delay ${delayHours}h", delayHours > 0) {
                delayHours = (delayHours + 2) % 8
                onAction("timer")
            }
        )
    )
}

@Composable
private fun SetTopBoxRemoteBody(
    brand: BrandOption,
    onButtonPress: (RemoteButton, Boolean) -> Unit,
    onLaunchApp: (LaunchableApp) -> Unit,
    onCloseApp: (LaunchableApp) -> Unit = {}
) {
    var selectedTab by remember(brand) { mutableStateOf(RemoteSubTab.PRIMARY) }
    if (brand.supportsAppLaunch) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = selectedTab == RemoteSubTab.PRIMARY,
                onClick = { selectedTab = RemoteSubTab.PRIMARY },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                icon = { Icon(Icons.Rounded.Gamepad, contentDescription = null) },
                label = { Text("Remote") }
            )
            SegmentedButton(
                selected = selectedTab == RemoteSubTab.APPS,
                onClick = { selectedTab = RemoteSubTab.APPS },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                icon = { Icon(Icons.Rounded.Apps, contentDescription = null) },
                label = { Text("Apps") }
            )
        }
    }

    if (selectedTab == RemoteSubTab.APPS && brand.supportsAppLaunch) {
        AppLauncherGrid(apps = ProductCatalog.tvApps, storageKey = "tv_apps_order", onLaunch = onLaunchApp, onClose = onCloseApp)
    } else {
        DPadControl(onButtonPress = { btn -> onButtonPress(btn, btn == RemoteButton.DPAD_CENTER) })
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NavCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back") {
                onButtonPress(RemoteButton.BACK, false)
            }
            NavCircleButton(Icons.Rounded.Home, "Home", highlight = true) {
                onButtonPress(RemoteButton.HOME, true)
            }
            NavCircleButton(Icons.Rounded.Menu, "Guide") {
                onButtonPress(RemoteButton.GUIDE, false)
            }
            NavCircleButton(Icons.Rounded.Info, "Info") {
                onButtonPress(RemoteButton.INFO, false)
            }
        }
        MediaVolumeBar(
            onButtonPress = { btn -> onButtonPress(btn, btn == RemoteButton.MUTE) },
            showChannelRocker = true,
            showMediaRow = false
        )
        NumberPadGrid(onButtonPress = { btn -> onButtonPress(btn, false) })
    }
}

@Composable
private fun SoundbarRemoteBody(onButtonPress: (RemoteButton, Boolean) -> Unit) {
    val inputs = listOf("HDMI ARC", "Bluetooth", "Optical", "AUX")
    val eqModes = listOf("Music", "Movie", "Night", "Voice")
    var inputIdx by remember { mutableIntStateOf(0) }
    var eqIdx by remember { mutableIntStateOf(0) }
    var bassLevel by remember { mutableIntStateOf(0) }
    val audioPink = Color(0xFFF472B6)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF2B1A2B), RemoteSurfaceElevated)
                )
            )
            .border(1.5.dp, audioPink.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Rounded.Speaker, contentDescription = null, tint = audioPink, modifier = Modifier.size(30.dp))
        Text(
            text = inputs[inputIdx],
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadgePill("EQ: ${eqModes[eqIdx]}", true, audioPink)
            StatusBadgePill("Bass ${if (bassLevel >= 0) "+$bassLevel" else "$bassLevel"}", true, WifiBlue)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        inputs.forEachIndexed { idx, label ->
            FilterChip(
                selected = inputIdx == idx,
                onClick = {
                    inputIdx = idx
                    onButtonPress(RemoteButton.TV_INPUT, false)
                },
                label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                modifier = Modifier.weight(1f)
            )
        }
    }

    MediaVolumeBar(
        onButtonPress = { btn -> onButtonPress(btn, btn == RemoteButton.MUTE) },
        showChannelRocker = false,
        showMediaRow = true
    )

    ApplianceToggleGrid(
        items = listOf(
            ToggleActionItem("Bass -", false) {
                if (bassLevel > -6) bassLevel--
                onButtonPress(RemoteButton.BASS_DOWN, false)
            },
            ToggleActionItem("Bass +", false) {
                if (bassLevel < 6) bassLevel++
                onButtonPress(RemoteButton.BASS_UP, false)
            },
            ToggleActionItem("EQ: ${eqModes[eqIdx]}", true) {
                eqIdx = (eqIdx + 1) % eqModes.size
                onButtonPress(RemoteButton.EQ_MODE, false)
            },
            ToggleActionItem("Source Next", false) {
                inputIdx = (inputIdx + 1) % inputs.size
                onButtonPress(RemoteButton.TV_INPUT, false)
            }
        )
    )
}

@Composable
private fun ProjectorRemoteBody(
    brand: BrandOption,
    uiState: RemoteUiState,
    onButtonPress: (RemoteButton, Boolean) -> Unit,
    onMouseMove: (Int, Int, Int) -> Unit,
    onMouseClick: (MouseButton) -> Unit,
    onLaunchApp: (LaunchableApp) -> Unit,
    onCloseApp: (LaunchableApp) -> Unit = {}
) {
    val tabs = remember(brand) {
        buildList {
            add(RemoteSubTab.PRIMARY)
            add(RemoteSubTab.TOUCHPAD)
            if (brand.supportsAppLaunch) add(RemoteSubTab.APPS)
        }
    }
    var selectedTab by remember(brand) { mutableStateOf(RemoteSubTab.PRIMARY) }

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        tabs.forEachIndexed { idx, tab ->
            val icon = when (tab) {
                RemoteSubTab.PRIMARY -> Icons.Rounded.Gamepad
                RemoteSubTab.TOUCHPAD -> Icons.Rounded.TouchApp
                RemoteSubTab.SCREEN, RemoteSubTab.FILES, RemoteSubTab.APPS -> Icons.Rounded.Apps
            }
            SegmentedButton(
                selected = selectedTab == tab,
                onClick = { selectedTab = tab },
                shape = SegmentedButtonDefaults.itemShape(idx, tabs.size),
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(tab.label, fontSize = 12.sp) }
            )
        }
    }

    when (selectedTab) {
        RemoteSubTab.PRIMARY -> {
            DPadControl(onButtonPress = { btn -> onButtonPress(btn, btn == RemoteButton.DPAD_CENTER) })
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NavCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back") {
                    onButtonPress(RemoteButton.BACK, false)
                }
                NavCircleButton(Icons.Rounded.Home, "Home", highlight = true) {
                    onButtonPress(RemoteButton.HOME, true)
                }
                NavCircleButton(Icons.Rounded.Menu, "Menu") {
                    onButtonPress(RemoteButton.MENU, false)
                }
                NavCircleButton(Icons.AutoMirrored.Rounded.Input, "Source") {
                    onButtonPress(RemoteButton.TV_INPUT, false)
                }
            }
            MediaVolumeBar(
                onButtonPress = { btn -> onButtonPress(btn, btn == RemoteButton.MUTE) },
                showChannelRocker = false,
                showMediaRow = false
            )
            FilledTonalButton(
                onClick = { onButtonPress(RemoteButton.KEYSTONE, false) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Keystone / Focus Settings") }
        }

        RemoteSubTab.TOUCHPAD -> {
            TouchpadControl(
                transportMode = (uiState.status as? ConnectionStatus.Connected)?.device?.transport
                    ?: uiState.activeTransport,
                onMouseMove = onMouseMove,
                onMouseClick = onMouseClick,
                onButtonFallback = { btn -> onButtonPress(btn, false) },
                showClickButtons = false
            )
            if (brand.supportsAppLaunch) {
                QuickAppStrip(apps = ProductCatalog.tvApps.take(4), onLaunch = onLaunchApp)
            }
        }

        RemoteSubTab.APPS -> {
            AppLauncherGrid(apps = ProductCatalog.tvApps, storageKey = "tv_apps_order", onLaunch = onLaunchApp, onClose = onCloseApp)
        }

        RemoteSubTab.SCREEN, RemoteSubTab.FILES -> Unit
    }
}

@Composable
private fun FanRemoteBody(onAction: (String) -> Unit) {
    var speed by remember { mutableIntStateOf(3) }
    var boost by remember { mutableStateOf(false) }
    var breeze by remember { mutableStateOf(false) }
    var ledOn by remember { mutableStateOf(true) }
    var timerHrs by remember { mutableIntStateOf(0) }
    val teal = Color(0xFF2DD4BF)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF142D2E), RemoteSurfaceElevated)
                )
            )
            .border(1.5.dp, teal.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Rounded.Air, contentDescription = null, tint = teal, modifier = Modifier.size(30.dp))
        Text("Fan Speed", color = TextSecondary, fontSize = 13.sp)
        Text("Level $speed", color = TextPrimary, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadgePill("Boost", boost, MiOrange)
            StatusBadgePill("Breeze", breeze, teal)
            StatusBadgePill(if (ledOn) "LED On" else "LED Off", ledOn, Color(0xFFFDE047))
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        (1..5).forEach { lvl ->
            val selected = speed == lvl
            FilledTonalButton(
                onClick = {
                    val prev = speed
                    speed = lvl
                    onAction(if (lvl >= prev) "speed_up" else "speed_down")
                },
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "$lvl",
                    color = if (selected) MiOrange else TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    ApplianceToggleGrid(
        items = listOf(
            ToggleActionItem("Boost", boost) {
                boost = !boost
                onAction("boost")
            },
            ToggleActionItem("Sleep / Breeze", breeze) {
                breeze = !breeze
                onAction("sleep")
            },
            ToggleActionItem(if (timerHrs == 0) "Timer Off" else "Timer ${timerHrs}h", timerHrs > 0) {
                timerHrs = (timerHrs + 2) % 10
                onAction("timer")
            },
            ToggleActionItem("LED Light", ledOn) {
                ledOn = !ledOn
                onAction("light")
            }
        )
    )
}

@Composable
private fun SmartBulbRemoteBody(
    isPowerOn: Boolean,
    onTogglePower: () -> Unit,
    onBulbAction: (power: Boolean?, brightness: Int?, rgb: Triple<Int, Int, Int>?, tempKelvin: Int?, irAction: String?) -> Unit
) {
    var modeTab by remember { mutableIntStateOf(0) } // 0 = RGB Colors, 1 = Warm to Cool White
    var brightness by remember { mutableFloatStateOf(100f) }
    var tempKelvin by remember { mutableFloatStateOf(3500f) }
    var activeColor by remember { mutableStateOf(Color(0xFFFFA88C)) }
    var activeScene by remember { mutableStateOf("") }

    val presetSwatches = listOf(
        Triple(255, 179, 71) to "bulb_amber",
        Triple(255, 255, 255) to "bulb_white",
        Triple(255, 140, 0) to "bulb_amber",
        Triple(255, 46, 67) to "bulb_red",
        Triple(255, 204, 0) to "bulb_warm",
        Triple(16, 216, 105) to "bulb_green",
        Triple(0, 217, 255) to "bulb_cool",
        Triple(37, 99, 235) to "bulb_blue",
        Triple(168, 85, 247) to "bulb_purple",
        Triple(244, 0, 89) to "bulb_red",
        Triple(255, 168, 140) to "bulb_warm",
        Triple(167, 243, 208) to "bulb_cool"
    )

    val whiteDisplayColor = remember(tempKelvin) {
        when {
            tempKelvin < 3200f -> Color(0xFFFFCF86)
            tempKelvin < 4800f -> Color(0xFFFFF4DE)
            else -> Color(0xFFBAE6FD)
        }
    }
    val orbColor = if (!isPowerOn) TextSecondary else if (modeTab == 0) activeColor else whiteDisplayColor
    val amberAccent = Color(0xFFF59E0B)

    // Interactive Glowing Bulb Orb
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(182.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            if (isPowerOn) orbColor.copy(alpha = 0.30f) else Color.Transparent,
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(144.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                if (isPowerOn) orbColor.copy(alpha = 0.22f) else RemoteSurface,
                                Color(0xFF1A1D24)
                            )
                        )
                    )
                    .border(
                        width = 3.dp,
                        color = if (isPowerOn) orbColor else RemoteBorder,
                        shape = CircleShape
                    )
                    .clickable(onClick = onTogglePower),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lightbulb,
                    contentDescription = "Toggle Bulb Power",
                    tint = if (isPowerOn) orbColor else TextSecondary.copy(alpha = 0.55f),
                    modifier = Modifier
                        .padding(bottom = 14.dp)
                        .size(52.dp)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                        .clip(CircleShape)
                        .background(Color(0x9912151C))
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isPowerOn) "${brightness.roundToInt()}%  •  ON" else "OFF",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Dual Mode Toggle: RGB Colors | Warm to Cool White
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RemoteSurfaceElevated)
            .border(1.dp, RemoteBorder, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val rgbSelected = modeTab == 0
        Row(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (rgbSelected) Color(0xFF2563EB) else Color.Transparent)
                .clickable { modeTab = 0 },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Palette,
                contentDescription = null,
                tint = if (rgbSelected) Color.White else TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "RGB Colors",
                color = if (rgbSelected) Color.White else TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }

        val whiteSelected = modeTab == 1
        Row(
            modifier = Modifier
                .weight(1.1f)
                .height(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (whiteSelected) Color(0xFF2563EB) else Color.Transparent)
                .clickable { modeTab = 1 },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.WbSunny,
                contentDescription = null,
                tint = if (whiteSelected) Color.White else TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Warm to Cool White",
                color = if (whiteSelected) Color.White else TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }

    // Brightness Slider Card
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(RemoteSurfaceElevated)
            .border(1.dp, RemoteBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = amberAccent, modifier = Modifier.size(18.dp))
                Text("Brightness", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("${brightness.roundToInt()}%", color = amberAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = brightness,
            onValueChange = { brightness = it },
            onValueChangeFinished = {
                val lvl = brightness.roundToInt().coerceIn(10, 100)
                onBulbAction(true, lvl, null, null, if (lvl >= 50) "bright_up" else "bright_down")
            },
            valueRange = 10f..100f,
            colors = SliderDefaults.colors(
                thumbColor = amberAccent,
                activeTrackColor = amberAccent,
                inactiveTrackColor = Color(0xFF2D3342)
            )
        )
    }

    if (modeTab == 0) {
        // Color Presets (2 x 6 rounded squircles)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Color Presets", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("16M Colors", color = TextSecondary, fontSize = 11.sp)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            presetSwatches.chunked(6).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { (rgb, irKey) ->
                        val swatch = Color(rgb.first, rgb.second, rgb.third)
                        val selected = activeColor == swatch && activeScene.isEmpty()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(swatch)
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) Color.White else RemoteBorder.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    activeColor = swatch
                                    activeScene = ""
                                    onBulbAction(true, null, rgb, null, irKey)
                                }
                        )
                    }
                }
            }
        }
    } else {
        // Warm to Cool White Kelvin Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(RemoteSurfaceElevated)
                .border(1.dp, RemoteBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Color Temperature", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("${tempKelvin.roundToInt()}K", color = whiteDisplayColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFFFF9E44), Color(0xFFFFF1D6), Color(0xFF93C5FD))
                        )
                    )
            )
            Slider(
                value = tempKelvin,
                onValueChange = { tempKelvin = it },
                onValueChangeFinished = {
                    val k = tempKelvin.roundToInt().coerceIn(2200, 6500)
                    onBulbAction(true, null, null, k, if (k >= 4500) "bulb_cool" else "bulb_warm")
                },
                valueRange = 2200f..6500f,
                colors = SliderDefaults.colors(
                    thumbColor = whiteDisplayColor,
                    activeTrackColor = Color(0xFF2563EB),
                    inactiveTrackColor = Color(0xFF2D3342)
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(2700 to "Warm 2700K", 4000 to "Natural 4000K", 6500 to "Cool 6500K").forEach { (kelvin, label) ->
                    val chosen = kotlin.math.abs(tempKelvin - kelvin) < 350f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (chosen) Color(0xFF2563EB).copy(alpha = 0.25f) else RemoteSurface)
                            .border(1.dp, if (chosen) Color(0xFF2563EB) else RemoteBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                tempKelvin = kelvin.toFloat()
                                activeScene = ""
                                onBulbAction(true, null, null, kelvin, if (kelvin >= 4500) "bulb_cool" else "bulb_warm")
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (chosen) TextPrimary else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    // Smart Ambience Scenes (5 rounded cards matching screenshot)
    Text("Smart Ambience Scenes", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        data class BulbScene(
            val label: String,
            val icon: ImageVector,
            val tint: Color,
            val targetBright: Int,
            val rgb: Triple<Int, Int, Int>?,
            val kelvin: Int?,
            val irKey: String
        )
        val scenes = listOf(
            BulbScene("Relax", Icons.Rounded.LocalCafe, Color(0xFFF59E0B), 65, null, 2700, "bulb_warm"),
            BulbScene("Read", Icons.Rounded.WbSunny, Color(0xFFE2E8F0), 95, null, 4500, "bulb_white"),
            BulbScene("Night", Icons.Rounded.NightsStay, Color(0xFFFB923C), 15, null, 2200, "bright_down"),
            BulbScene("Party", Icons.Rounded.AutoAwesome, Color(0xFFA855F7), 100, Triple(192, 68, 255), null, "bulb_purple"),
            BulbScene("Romance", Icons.Rounded.Favorite, Color(0xFFF43F5E), 45, Triple(255, 72, 118), null, "bulb_red")
        )
        scenes.forEach { sc ->
            val selected = activeScene == sc.label
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) sc.tint.copy(alpha = 0.16f) else RemoteSurfaceElevated)
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) sc.tint else RemoteBorder,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable {
                        activeScene = sc.label
                        brightness = sc.targetBright.toFloat()
                        sc.rgb?.let { (r, g, b) ->
                            modeTab = 0
                            activeColor = Color(r, g, b)
                        } ?: sc.kelvin?.let { k ->
                            modeTab = 1
                            tempKelvin = k.toFloat()
                        }
                        onBulbAction(true, sc.targetBright, sc.rgb, sc.kelvin, sc.irKey)
                    }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(sc.tint.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(sc.icon, contentDescription = sc.label, tint = sc.tint, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = sc.label,
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun AppLauncherGrid(
    apps: List<LaunchableApp>,
    storageKey: String,
    onLaunch: (LaunchableApp) -> Unit,
    onClose: (LaunchableApp) -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var orderedApps by remember(apps, storageKey) {
        mutableStateOf(loadAppOrder(context, storageKey, apps))
    }
    val itemBounds = remember(storageKey) { HashMap<String, Rect>() }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var pointerRoot by remember { mutableStateOf(Offset.Zero) }
    var touchAnchor by remember { mutableStateOf(Offset.Zero) }
    var lastSwapMs by remember { mutableLongStateOf(0L) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = if (draggedId != null) "Drag to reorder app" else "Tap to open • Tap × to close • Hold to reorder",
            color = if (draggedId != null) MiOrange else TextSecondary,
            fontSize = 11.sp
        )

        orderedApps.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { app ->
                    val isDragged = draggedId == app.id
                    val rect = itemBounds[app.id]
                    val delta = if (isDragged && rect != null) {
                        pointerRoot - rect.topLeft - touchAnchor
                    } else {
                        Offset.Zero
                    }
                    val activeAccent = MiOrange

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(66.dp)
                            .zIndex(if (isDragged) 10f else 0f)
                            .graphicsLayer {
                                translationX = delta.x
                                translationY = delta.y
                                scaleX = if (isDragged) 1.05f else 1f
                                scaleY = if (isDragged) 1.05f else 1f
                            }
                            .onGloballyPositioned { coords ->
                                itemBounds[app.id] = coords.boundsInRoot()
                            }
                            .clip(RoundedCornerShape(18.dp))
                            .background(RemoteSurfaceElevated)
                            .border(
                                width = if (isDragged) 2.dp else 1.dp,
                                color = if (isDragged) activeAccent else RemoteBorder,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .pointerInput(app.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { startOffset ->
                                        val r = itemBounds[app.id] ?: return@detectDragGesturesAfterLongPress
                                        draggedId = app.id
                                        touchAnchor = startOffset
                                        pointerRoot = r.topLeft + startOffset
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        pointerRoot += dragAmount
                                        val now = System.currentTimeMillis()
                                        if (now - lastSwapMs >= 140L) {
                                            val hit = itemBounds.entries.firstOrNull { (otherId, otherRect) ->
                                                otherId != app.id && otherRect.contains(pointerRoot)
                                            }?.key
                                            if (hit != null) {
                                                val fromIdx = orderedApps.indexOfFirst { it.id == app.id }
                                                val toIdx = orderedApps.indexOfFirst { it.id == hit }
                                                if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
                                                    val mutable = orderedApps.toMutableList()
                                                    mutable.add(toIdx, mutable.removeAt(fromIdx))
                                                    orderedApps = mutable
                                                    lastSwapMs = now
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        draggedId = null
                                        saveAppOrder(context, storageKey, orderedApps)
                                    },
                                    onDragCancel = {
                                        draggedId = null
                                        saveAppOrder(context, storageKey, orderedApps)
                                    }
                                )
                            }
                            .clickable { onLaunch(app) }
                            .padding(start = 10.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppLogoBadge(app = app)
                        Text(
                            text = app.name,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2B181C))
                                .border(1.dp, Color(0xFFF87171).copy(alpha = 0.45f), CircleShape)
                                .clickable { onClose(app) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close ${app.name}",
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private data class ToggleActionItem(
    val label: String,
    val active: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun ApplianceToggleGrid(items: List<ToggleActionItem>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { item ->
                    val accent = MiOrange
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (item.active) accent.copy(alpha = 0.2f) else RemoteSurfaceElevated)
                            .border(
                                width = 1.dp,
                                color = if (item.active) accent else RemoteBorder,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable(onClick = item.onClick)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.label,
                            color = if (item.active) accent else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatusBadgePill(text: String, active: Boolean, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) color.copy(alpha = 0.2f) else RemoteSurface)
            .border(1.dp, if (active) color.copy(alpha = 0.65f) else RemoteBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = if (active) color else TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun NumberPadGrid(onButtonPress: (RemoteButton) -> Unit) {
    val rows = listOf(
        listOf(RemoteButton.DIGIT_1 to "1", RemoteButton.DIGIT_2 to "2", RemoteButton.DIGIT_3 to "3"),
        listOf(RemoteButton.DIGIT_4 to "4", RemoteButton.DIGIT_5 to "5", RemoteButton.DIGIT_6 to "6"),
        listOf(RemoteButton.DIGIT_7 to "7", RemoteButton.DIGIT_8 to "8", RemoteButton.DIGIT_9 to "9"),
        listOf(RemoteButton.BACK to "CLR", RemoteButton.DIGIT_0 to "0", RemoteButton.ENTER to "OK")
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (btn, label) ->
                    FilledTonalButton(
                        onClick = { onButtonPress(btn) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(label, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun NavCircleButton(
    icon: ImageVector,
    label: String,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    val accent = MiOrange
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (highlight) accent.copy(alpha = 0.22f) else RemoteSurfaceElevated,
                contentColor = if (highlight) accent else TextPrimary
            )
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
        Text(text = label, color = TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun QuickShortcutButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, fontSize = 12.sp)
    }
}

private const val APP_ORDER_PREFS = "gpt_app_order"

private fun loadAppOrder(
    context: Context,
    key: String,
    defaults: List<LaunchableApp>
): List<LaunchableApp> {
    val csv = context.applicationContext.getSharedPreferences(APP_ORDER_PREFS, Context.MODE_PRIVATE)
        .getString(key, null)
    if (csv.isNullOrBlank()) return defaults
    val list = csv.split(',')
        .mapNotNull { id -> defaults.firstOrNull { it.id == id } }
        .distinctBy { it.id }
        .toMutableList()
    defaults.forEach { app -> if (list.none { it.id == app.id }) list.add(app) }
    return list
}

private fun saveAppOrder(context: Context, key: String, list: List<LaunchableApp>) {
    context.applicationContext.getSharedPreferences(APP_ORDER_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(key, list.joinToString(",") { it.id })
        .apply()
}
