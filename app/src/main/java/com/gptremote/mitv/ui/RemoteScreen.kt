package com.gptremote.mitv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gptremote.mitv.haptic.HapticStrength
import com.gptremote.mitv.model.BrandOption
import com.gptremote.mitv.model.ConnectionStatus
import com.gptremote.mitv.model.DeviceCategory
import com.gptremote.mitv.model.LaunchableApp
import com.gptremote.mitv.model.MouseButton
import com.gptremote.mitv.model.ProductCatalog
import com.gptremote.mitv.model.ProductType
import com.gptremote.mitv.model.RemoteButton
import com.gptremote.mitv.model.RemoteUiState
import com.gptremote.mitv.model.TransportMode
import com.gptremote.mitv.model.TvDevice
import com.gptremote.mitv.ui.components.ConnectProductSheet
import com.gptremote.mitv.ui.components.DPadControl
import com.gptremote.mitv.ui.components.KeyboardSheet
import com.gptremote.mitv.ui.components.MediaVolumeBar
import com.gptremote.mitv.ui.components.PcFileSharePanel
import com.gptremote.mitv.ui.components.PcScreenSharePanel
import com.gptremote.mitv.ui.components.SettingsSheet
import com.gptremote.mitv.ui.components.TouchpadControl
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
import com.gptremote.mitv.wifi.DiscoveredSmartBulb
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
    onStartPhoneCast: () -> Unit,
    onStopPhoneCast: () -> Unit,
    onRefreshPcFiles: () -> Unit,
    onPickFileToSend: () -> Unit,
    onDownloadPcFile: (SharedPcFile) -> Unit,
    onButtonPress: (RemoteButton, Boolean) -> Unit,
    onApplianceAction: (String) -> Unit,
    onLaunchApp: (LaunchableApp) -> Unit,
    onSendText: (String) -> Unit,
    onMouseMove: (dx: Int, dy: Int, wheel: Int) -> Unit,
    onMouseClick: (MouseButton) -> Unit,
    onSelectHapticStrength: (HapticStrength) -> Unit,
    onToggleSound: (Boolean) -> Unit,
    onSelectAccentTheme: (AppAccentTheme) -> Unit,
    onClearBanner: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showConnectSheet by remember { mutableStateOf(false) }
    var showKeyboardSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
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

    Scaffold(
        containerColor = RemoteBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RemoteTopBar(
                brand = brand,
                uiState = uiState,
                onBack = onNavigateBack,
                onOpenConnect = { showConnectSheet = true },
                onOpenSettings = { showSettingsSheet = true },
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
                    onOpenKeyboard = { showKeyboardSheet = true }
                )

                ProductType.PC_LAPTOP -> PcRemoteBody(
                    uiState = uiState,
                    onButtonPress = onButtonPress,
                    onMouseMove = onMouseMove,
                    onMouseClick = onMouseClick,
                    onLaunchApp = onLaunchApp,
                    onOpenKeyboard = { showKeyboardSheet = true },
                    onStartPcMirror = onStartPcMirror,
                    onStopPcMirror = onStopPcMirror,
                    onTapMirroredScreen = onTapMirroredScreen,
                    onStartPhoneCast = onStartPhoneCast,
                    onStopPhoneCast = onStopPhoneCast,
                    onRefreshPcFiles = onRefreshPcFiles,
                    onPickFileToSend = onPickFileToSend,
                    onDownloadPcFile = onDownloadPcFile,
                    onOpenConnect = { showConnectSheet = true }
                )

                ProductType.AC -> AcRemoteBody(onAction = onApplianceAction)

                ProductType.FRIDGE -> FridgeRemoteBody(onAction = onApplianceAction)

                ProductType.WASHING_MACHINE -> WashingMachineRemoteBody(onAction = onApplianceAction)

                ProductType.SET_TOP_BOX -> SetTopBoxRemoteBody(
                    brand = brand,
                    onButtonPress = onButtonPress,
                    onLaunchApp = onLaunchApp
                )

                ProductType.SOUNDBAR -> SoundbarRemoteBody(onButtonPress = onButtonPress)

                ProductType.PROJECTOR -> ProjectorRemoteBody(
                    brand = brand,
                    uiState = uiState,
                    onButtonPress = onButtonPress,
                    onMouseMove = onMouseMove,
                    onMouseClick = onMouseClick,
                    onLaunchApp = onLaunchApp
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
    onPowerClick: () -> Unit
) {
    val accent = MiOrange
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

        val (pillTitle, dotColor, icon) = when {
            uiState.status is ConnectionStatus.Connected -> {
                val st = uiState.status
                val ic = if (st.device.transport == TransportMode.BLUETOOTH_HID) {
                    Icons.Rounded.Bluetooth
                } else {
                    Icons.Rounded.Wifi
                }
                Triple(st.device.name, ConnectedGreen, ic)
            }
            brand.productType == ProductType.SMART_BULB && uiState.connectedBulb != null -> {
                Triple(uiState.connectedBulb.name, ConnectedGreen, Icons.Rounded.Lightbulb)
            }
            uiState.status is ConnectionStatus.Connecting -> {
                Triple("Connecting...", accent, Icons.Rounded.Bluetooth)
            }
            uiState.status is ConnectionStatus.AwaitingWifiPin -> {
                Triple("Enter PIN", WifiBlue, Icons.Rounded.Wifi)
            }
            uiState.irActive && uiState.hasIrEmitter -> {
                Triple("${brand.name} • IR Ready", ConnectedGreen, Icons.Rounded.SettingsRemote)
            }
            else -> {
                Triple("Connect ${brand.name}", TextSecondary, Icons.Rounded.Bluetooth)
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
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

@Composable
private fun TvRemoteBody(
    brand: BrandOption,
    uiState: RemoteUiState,
    onButtonPress: (RemoteButton, Boolean) -> Unit,
    onMouseMove: (Int, Int, Int) -> Unit,
    onMouseClick: (MouseButton) -> Unit,
    onLaunchApp: (LaunchableApp) -> Unit,
    onOpenKeyboard: () -> Unit
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
                RemoteSubTab.SCREEN, RemoteSubTab.FILES, RemoteSubTab.APPS -> Icons.Rounded.Apps
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
        }

        RemoteSubTab.APPS -> {
            AppLauncherGrid(
                apps = ProductCatalog.tvApps,
                onLaunch = onLaunchApp
            )
        }

        RemoteSubTab.SCREEN, RemoteSubTab.FILES -> Unit
    }
}

@Composable
private fun PcRemoteBody(
    uiState: RemoteUiState,
    onButtonPress: (RemoteButton, Boolean) -> Unit,
    onMouseMove: (Int, Int, Int) -> Unit,
    onMouseClick: (MouseButton) -> Unit,
    onLaunchApp: (LaunchableApp) -> Unit,
    onOpenKeyboard: () -> Unit,
    onStartPcMirror: () -> Unit,
    onStopPcMirror: () -> Unit,
    onTapMirroredScreen: (Float, Float) -> Unit,
    onStartPhoneCast: () -> Unit,
    onStopPhoneCast: () -> Unit,
    onRefreshPcFiles: () -> Unit,
    onPickFileToSend: () -> Unit,
    onDownloadPcFile: (SharedPcFile) -> Unit,
    onOpenConnect: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(RemoteSubTab.TOUCHPAD) }
    val pcTabs = listOf(
        RemoteSubTab.TOUCHPAD to "Touchpad",
        RemoteSubTab.PRIMARY to "Keys",
        RemoteSubTab.SCREEN to "Screen",
        RemoteSubTab.FILES to "Files",
        RemoteSubTab.APPS to "Apps"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        pcTabs.forEach { (tab, title) ->
            FilterChip(
                selected = selectedTab == tab,
                onClick = { selectedTab = tab },
                label = { Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
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
                ) { Text("Esc", fontSize = 12.sp) }
                FilledTonalButton(
                    onClick = { onButtonPress(RemoteButton.PC_WIN_KEY, true) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Start", fontSize = 12.sp) }
                FilledTonalButton(
                    onClick = { onButtonPress(RemoteButton.PC_ALT_TAB, false) },
                    modifier = Modifier.weight(1.1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Alt+Tab", fontSize = 12.sp) }
                FilledTonalButton(
                    onClick = onOpenKeyboard,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Keyboard, contentDescription = "Keyboard", modifier = Modifier.size(18.dp))
                }
            }
        }

        RemoteSubTab.PRIMARY -> {
            DPadControl(
                onButtonPress = { btn -> onButtonPress(btn, btn == RemoteButton.DPAD_CENTER) }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    RemoteButton.PC_COPY to "Copy",
                    RemoteButton.PC_PASTE to "Paste",
                    RemoteButton.PC_SHOW_DESKTOP to "Desktop",
                    RemoteButton.TAB to "Tab"
                ).forEach { (btn, label) ->
                    FilledTonalButton(
                        onClick = { onButtonPress(btn, false) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(label, fontSize = 12.sp) }
                }
            }
            MediaVolumeBar(
                onButtonPress = { btn -> onButtonPress(btn, btn == RemoteButton.MUTE) },
                showChannelRocker = false,
                showMediaRow = true
            )
            Button(
                onClick = onOpenKeyboard,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MiOrange),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Keyboard, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Full PC Keyboard")
            }
        }

        RemoteSubTab.SCREEN -> {
            PcScreenSharePanel(
                uiState = uiState,
                onStartPcMirror = onStartPcMirror,
                onStopPcMirror = onStopPcMirror,
                onTapMirroredScreen = onTapMirroredScreen,
                onStartPhoneCast = onStartPhoneCast,
                onStopPhoneCast = onStopPhoneCast,
                onOpenConnect = onOpenConnect
            )
        }

        RemoteSubTab.FILES -> {
            PcFileSharePanel(
                uiState = uiState,
                onRefreshFiles = onRefreshPcFiles,
                onPickFileToSend = onPickFileToSend,
                onDownloadFile = onDownloadPcFile,
                onOpenConnect = onOpenConnect
            )
        }

        RemoteSubTab.APPS -> {
            AppLauncherGrid(
                apps = ProductCatalog.pcApps,
                onLaunch = onLaunchApp
            )
        }
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

    // Live Digital AC Display Card
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
    onLaunchApp: (LaunchableApp) -> Unit
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
        AppLauncherGrid(apps = ProductCatalog.tvApps, onLaunch = onLaunchApp)
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

    // Sleek Soundbar Status Header
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

    // Input Source Pills
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
    onLaunchApp: (LaunchableApp) -> Unit
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
        }

        RemoteSubTab.APPS -> {
            AppLauncherGrid(apps = ProductCatalog.tvApps, onLaunch = onLaunchApp)
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
    var brightness by remember { mutableFloatStateOf(80f) }
    var activeColor by remember { mutableStateOf(Color(0xFFFDE047)) }
    var tempLabel by remember { mutableStateOf("Warm 2700K") }

    val bulbColors = listOf(
        Triple(255, 68, 68) to ("Red" to "bulb_red"),
        Triple(255, 165, 0) to ("Amber" to "bulb_amber"),
        Triple(52, 211, 153) to ("Green" to "bulb_green"),
        Triple(56, 189, 248) to ("Cyan" to "bulb_cool"),
        Triple(99, 102, 241) to ("Blue" to "bulb_blue"),
        Triple(192, 132, 252) to ("Purple" to "bulb_purple")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        if (isPowerOn) activeColor.copy(alpha = 0.22f) else RemoteSurface,
                        RemoteSurfaceElevated
                    )
                )
            )
            .border(
                width = 1.5.dp,
                color = if (isPowerOn) activeColor.copy(alpha = 0.65f) else RemoteBorder,
                shape = RoundedCornerShape(26.dp)
            )
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(if (isPowerOn) activeColor.copy(alpha = 0.25f) else RemoteSurface)
                .border(2.dp, if (isPowerOn) activeColor else RemoteBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Lightbulb,
                contentDescription = "Bulb State",
                tint = if (isPowerOn) activeColor else TextSecondary,
                modifier = Modifier.size(38.dp)
            )
        }
        Text(
            text = if (isPowerOn) "${brightness.roundToInt()}% Brightness" else "Bulb Off",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(text = tempLabel, color = TextSecondary, fontSize = 13.sp)

        Button(
            onClick = onTogglePower,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isPowerOn) Color(0xFF334155) else MiOrange
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isPowerOn) "Turn Off" else "Turn On", fontWeight = FontWeight.SemiBold)
        }
    }

    // Brightness Slider
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(RemoteSurfaceElevated)
            .border(1.dp, RemoteBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Brightness", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("${brightness.roundToInt()}%", color = MiOrange, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = brightness,
            onValueChange = { brightness = it },
            onValueChangeFinished = {
                val lvl = brightness.roundToInt().coerceIn(10, 100)
                onBulbAction(null, lvl, null, null, if (lvl >= 50) "bright_up" else "bright_down")
            },
            valueRange = 10f..100f,
            colors = SliderDefaults.colors(
                thumbColor = MiOrange,
                activeTrackColor = MiOrange
            )
        )
    }

    // White Temperature Presets
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            Triple("Warm", 2700, Color(0xFFFDE047)) to "bulb_warm",
            Triple("Neutral", 4500, Color(0xFFF8FAFC)) to "bulb_white",
            Triple("Cool", 6500, Color(0xFFBAE6FD)) to "bulb_cool"
        ).forEach { (preset, irKey) ->
            val (label, kelvin, previewColor) = preset
            FilledTonalButton(
                onClick = {
                    tempLabel = "$label ${kelvin}K"
                    activeColor = previewColor
                    onBulbAction(true, null, null, kelvin, irKey)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(label, fontSize = 13.sp)
            }
        }
    }

    // Color Swatches
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(RemoteSurfaceElevated)
            .border(1.dp, RemoteBorder, RoundedCornerShape(20.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        bulbColors.forEach { (rgb, meta) ->
            val (label, irKey) = meta
            val swatch = Color(rgb.first, rgb.second, rgb.third)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(swatch)
                    .border(
                        width = if (activeColor == swatch) 3.dp else 1.dp,
                        color = if (activeColor == swatch) Color.White else RemoteBorder,
                        shape = CircleShape
                    )
                    .clickable {
                        activeColor = swatch
                        tempLabel = "$label Color"
                        onBulbAction(true, null, rgb, null, irKey)
                    }
            )
        }
    }
}

@Composable
private fun AppLauncherGrid(
    apps: List<LaunchableApp>,
    onLaunch: (LaunchableApp) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        apps.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { app ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(66.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(RemoteSurfaceElevated)
                            .border(1.dp, RemoteBorder, RoundedCornerShape(18.dp))
                            .clickable { onLaunch(app) }
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(app.badgeColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = app.shortBadge,
                                color = Color.White,
                                fontSize = if (app.shortBadge.length >= 3) 11.sp else 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Text(
                            text = app.name,
                            color = TextPrimary,
                            fontSize = 14.sp,
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
