package com.gptremote.mitv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.gptremote.mitv.ui.components.SettingsSheet
import com.gptremote.mitv.ui.components.TouchpadControl
import com.gptremote.mitv.ui.theme.ConnectedGreen
import com.gptremote.mitv.ui.theme.MiOrange
import com.gptremote.mitv.ui.theme.RemoteBg
import com.gptremote.mitv.ui.theme.RemoteBorder
import com.gptremote.mitv.ui.theme.RemoteSurface
import com.gptremote.mitv.ui.theme.RemoteSurfaceElevated
import com.gptremote.mitv.ui.theme.TextPrimary
import com.gptremote.mitv.ui.theme.TextSecondary
import com.gptremote.mitv.ui.theme.WifiBlue

private enum class RemoteSubTab(val label: String) {
    PRIMARY("Remote"),
    TOUCHPAD("Touchpad"),
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
    onEnableIr: () -> Unit,
    onSubmitWifiPin: (String) -> Unit,
    onDisconnect: () -> Unit,
    onButtonPress: (RemoteButton, Boolean) -> Unit,
    onApplianceAction: (String) -> Unit,
    onLaunchApp: (LaunchableApp) -> Unit,
    onSendText: (String) -> Unit,
    onMouseMove: (dx: Int, dy: Int, wheel: Int) -> Unit,
    onMouseClick: (MouseButton) -> Unit,
    onSelectHapticStrength: (HapticStrength) -> Unit,
    onToggleBluetoothPcProfile: (Boolean) -> Unit,
    onClearBanner: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showConnectSheet by remember { mutableStateOf(false) }
    var showKeyboardSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

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
                    onOpenKeyboard = { showKeyboardSheet = true },
                    onOpenConnect = { showConnectSheet = true }
                )

                ProductType.PC_LAPTOP -> PcRemoteBody(
                    uiState = uiState,
                    onButtonPress = onButtonPress,
                    onMouseMove = onMouseMove,
                    onMouseClick = onMouseClick,
                    onLaunchApp = onLaunchApp,
                    onOpenKeyboard = { showKeyboardSheet = true }
                )

                ProductType.AC -> AcRemoteBody(
                    brand = brand,
                    hasIr = uiState.hasIrEmitter,
                    onAction = onApplianceAction
                )

                ProductType.FRIDGE -> FridgeRemoteBody(
                    brand = brand,
                    hasIr = uiState.hasIrEmitter,
                    onAction = onApplianceAction
                )

                ProductType.WASHING_MACHINE -> WashingMachineRemoteBody(
                    brand = brand,
                    hasIr = uiState.hasIrEmitter,
                    onAction = onApplianceAction
                )

                ProductType.SET_TOP_BOX -> SetTopBoxRemoteBody(
                    brand = brand,
                    uiState = uiState,
                    onButtonPress = onButtonPress,
                    onLaunchApp = onLaunchApp,
                    onOpenConnect = { showConnectSheet = true }
                )

                ProductType.SOUNDBAR -> SoundbarRemoteBody(
                    onButtonPress = onButtonPress
                )

                ProductType.PROJECTOR -> ProjectorRemoteBody(
                    brand = brand,
                    uiState = uiState,
                    onButtonPress = onButtonPress,
                    onLaunchApp = onLaunchApp,
                    onOpenConnect = { showConnectSheet = true }
                )

                ProductType.CEILING_FAN -> FanRemoteBody(
                    brand = brand,
                    hasIr = uiState.hasIrEmitter,
                    onAction = onApplianceAction
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
            bluetoothPcProfile = uiState.bluetoothPcProfile,
            onSelectHapticStrength = onSelectHapticStrength,
            onToggleBluetoothPcProfile = onToggleBluetoothPcProfile,
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

        val (pillTitle, dotColor, icon) = when (val st = uiState.status) {
            is ConnectionStatus.Connected -> {
                val ic = if (st.device.transport == TransportMode.BLUETOOTH_HID) {
                    Icons.Rounded.Bluetooth
                } else {
                    Icons.Rounded.Wifi
                }
                Triple(st.device.name, ConnectedGreen, ic)
            }
            is ConnectionStatus.Connecting -> Triple("Connecting...", MiOrange, Icons.Rounded.Bluetooth)
            is ConnectionStatus.AwaitingWifiPin -> Triple("Enter PIN", WifiBlue, Icons.Rounded.Wifi)
            else -> {
                if (uiState.irActive && uiState.hasIrEmitter) {
                    Triple("${brand.name} • IR Ready", ConnectedGreen, Icons.Rounded.SettingsRemote)
                } else if (!brand.productType.supportsBluetoothHid && !brand.supportsWifiTv && !uiState.hasIrEmitter) {
                    Triple("${brand.name} • No IR", Color(0xFFFBBF24), Icons.Rounded.SettingsRemote)
                } else {
                    Triple("Connect ${brand.name}", TextSecondary, Icons.Rounded.Bluetooth)
                }
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
                Icon(Icons.Rounded.Tune, contentDescription = "Settings")
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
    onOpenKeyboard: () -> Unit,
    onOpenConnect: () -> Unit
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
            MediaVolumeBar(onButtonPress = { btn -> onButtonPress(btn, btn == RemoteButton.MUTE) })
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
                onButtonFallback = { btn -> onButtonPress(btn, false) }
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
            val isWifiTv = (uiState.status as? ConnectionStatus.Connected)?.device?.transport == TransportMode.WIFI_ANDROID_TV
            if (!isWifiTv) {
                InfoBannerCard(
                    title = "Wi-Fi TV Required for Direct App Launch",
                    body = "Direct app opening uses Android TV Wi-Fi control. Tap below to connect via Wi-Fi TV.",
                    buttonText = "Connect Wi-Fi TV",
                    onAction = onOpenConnect
                )
            }
            AppLauncherGrid(
                apps = ProductCatalog.tvApps,
                onLaunch = onLaunchApp
            )
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
    onOpenKeyboard: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(RemoteSubTab.TOUCHPAD) }
    val pcTabs = listOf(RemoteSubTab.TOUCHPAD to "Touchpad", RemoteSubTab.PRIMARY to "Keys", RemoteSubTab.APPS to "Apps")

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        pcTabs.forEachIndexed { idx, (tab, title) ->
            val icon = when (tab) {
                RemoteSubTab.TOUCHPAD -> Icons.Rounded.TouchApp
                RemoteSubTab.PRIMARY -> Icons.Rounded.Keyboard
                RemoteSubTab.APPS -> Icons.Rounded.Apps
            }
            SegmentedButton(
                selected = selectedTab == tab,
                onClick = { selectedTab = tab },
                shape = SegmentedButtonDefaults.itemShape(index = idx, count = pcTabs.size),
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(title, fontSize = 12.sp) }
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
                onButtonFallback = { btn -> onButtonPress(btn, false) }
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
            // Volume + Media ONLY (NO Channel +/- for PC!)
            PcMediaVolumeRow(onButtonPress = { btn -> onButtonPress(btn, btn == RemoteButton.MUTE) })
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

        RemoteSubTab.APPS -> {
            AppLauncherGrid(
                apps = ProductCatalog.pcApps,
                onLaunch = onLaunchApp
            )
        }
    }
}

@Composable
private fun AcRemoteBody(
    brand: BrandOption,
    hasIr: Boolean,
    onAction: (String) -> Unit
) {
    var temp by remember { mutableIntStateOf(24) }
    var modeIdx by remember { mutableIntStateOf(0) }
    var fanIdx by remember { mutableIntStateOf(0) }
    val modes = listOf("Cool", "Dry", "Fan", "Auto", "Heat")
    val fanSpeeds = listOf("Auto", "Low", "Medium", "High")

    if (!hasIr) {
        NoIrWarningCard(brand.name)
    }

    // Digital AC Readout
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(RemoteSurfaceElevated)
            .border(1.5.dp, RemoteBorder, RoundedCornerShape(24.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Rounded.AcUnit, contentDescription = null, tint = WifiBlue, modifier = Modifier.size(30.dp))
        Text(
            text = "${temp}°C",
            color = TextPrimary,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "${modes[modeIdx]}  •  Fan: ${fanSpeeds[fanIdx]}",
            color = TextSecondary,
            fontSize = 14.sp
        )
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
            colors = ButtonDefaults.buttonColors(containerColor = MiOrange),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Increase Temp")
            Spacer(modifier = Modifier.width(6.dp))
            Text("Temp +", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    ApplianceButtonGrid(
        items = listOf(
            "Mode (${modes[modeIdx]})" to {
                modeIdx = (modeIdx + 1) % modes.size
                onAction("mode")
            },
            "Fan (${fanSpeeds[fanIdx]})" to {
                fanIdx = (fanIdx + 1) % fanSpeeds.size
                onAction("fan")
            },
            "Swing" to { onAction("swing") },
            "Turbo" to { onAction("turbo") },
            "Sleep" to { onAction("sleep") },
            "Timer" to { onAction("timer") }
        )
    )
}

@Composable
private fun FridgeRemoteBody(
    brand: BrandOption,
    hasIr: Boolean,
    onAction: (String) -> Unit
) {
    var fridgeTemp by remember { mutableIntStateOf(4) }
    var freezerTemp by remember { mutableIntStateOf(-18) }

    if (!hasIr) {
        NoIrWarningCard(brand.name)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(RemoteSurfaceElevated)
                .border(1.dp, RemoteBorder, RoundedCornerShape(22.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.Kitchen, contentDescription = null, tint = ConnectedGreen)
            Text("Fridge", color = TextSecondary, fontSize = 12.sp)
            Text("${fridgeTemp}°C", color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
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
                .border(1.dp, RemoteBorder, RoundedCornerShape(22.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.AcUnit, contentDescription = null, tint = WifiBlue)
            Text("Freezer", color = TextSecondary, fontSize = 12.sp)
            Text("${freezerTemp}°C", color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
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

    ApplianceButtonGrid(
        items = listOf(
            "Super Cool" to { onAction("super_cool") },
            "Power Freeze" to { onAction("turbo") },
            "Eco Mode" to { onAction("eco") },
            "Ice Maker" to { onAction("mode") }
        )
    )
}

@Composable
private fun WashingMachineRemoteBody(
    brand: BrandOption,
    hasIr: Boolean,
    onAction: (String) -> Unit
) {
    var cycleIdx by remember { mutableIntStateOf(0) }
    var spinIdx by remember { mutableIntStateOf(1) }
    val cycles = listOf("Cotton", "Quick 15'", "Delicates", "Heavy Duty", "Rinse + Spin")
    val spins = listOf("600 RPM", "1000 RPM", "1200 RPM", "1400 RPM")

    if (!hasIr) {
        NoIrWarningCard(brand.name)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(RemoteSurfaceElevated)
            .border(1.5.dp, RemoteBorder, RoundedCornerShape(24.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Rounded.LocalLaundryService, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(30.dp))
        Text(cycles[cycleIdx], color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Spin: ${spins[spinIdx]}", color = TextSecondary, fontSize = 14.sp)
    }

    Button(
        onClick = { onAction("start_pause") },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MiOrange),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Start / Pause Wash", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }

    ApplianceButtonGrid(
        items = listOf(
            "Cycle (${cycles[cycleIdx]})" to {
                cycleIdx = (cycleIdx + 1) % cycles.size
                onAction("cycle")
            },
            "Spin (${spins[spinIdx]})" to {
                spinIdx = (spinIdx + 1) % spins.size
                onAction("spin")
            },
            "Water Temp" to { onAction("temp_up") },
            "Delay End" to { onAction("timer") }
        )
    )
}

@Composable
private fun SetTopBoxRemoteBody(
    brand: BrandOption,
    uiState: RemoteUiState,
    onButtonPress: (RemoteButton, Boolean) -> Unit,
    onLaunchApp: (LaunchableApp) -> Unit,
    onOpenConnect: () -> Unit
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
        val isWifiTv = (uiState.status as? ConnectionStatus.Connected)?.device?.transport == TransportMode.WIFI_ANDROID_TV
        if (!isWifiTv) {
            InfoBannerCard(
                title = "Connect via Wi-Fi to Open Apps",
                body = "Direct streaming app launch requires Wi-Fi connection.",
                buttonText = "Connect Wi-Fi",
                onAction = onOpenConnect
            )
        }
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
        MediaVolumeBar(onButtonPress = { btn -> onButtonPress(btn, btn == RemoteButton.MUTE) })
        NumberPadGrid(onButtonPress = { btn -> onButtonPress(btn, false) })
    }
}

@Composable
private fun SoundbarRemoteBody(
    onButtonPress: (RemoteButton, Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FilledTonalButton(
            onClick = { onButtonPress(RemoteButton.VOLUME_DOWN, false) },
            modifier = Modifier
                .weight(1f)
                .height(64.dp),
            shape = RoundedCornerShape(18.dp)
        ) { Text("Vol -", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        FilledTonalIconButton(
            onClick = { onButtonPress(RemoteButton.MUTE, true) },
            modifier = Modifier.size(64.dp),
            shape = CircleShape
        ) { Icon(Icons.AutoMirrored.Rounded.VolumeOff, contentDescription = "Mute") }
        Button(
            onClick = { onButtonPress(RemoteButton.VOLUME_UP, false) },
            modifier = Modifier
                .weight(1f)
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MiOrange),
            shape = RoundedCornerShape(18.dp)
        ) { Text("Vol +", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(RemoteSurface)
            .border(1.dp, RemoteBorder, RoundedCornerShape(18.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        FilledTonalIconButton(onClick = { onButtonPress(RemoteButton.MEDIA_PREVIOUS, false) }) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Prev")
        }
        FilledTonalIconButton(onClick = { onButtonPress(RemoteButton.MEDIA_PLAY_PAUSE, true) }) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play/Pause", tint = MiOrange)
        }
        FilledTonalIconButton(onClick = { onButtonPress(RemoteButton.MEDIA_STOP, false) }) {
            Icon(Icons.Rounded.Stop, contentDescription = "Stop")
        }
        FilledTonalIconButton(onClick = { onButtonPress(RemoteButton.MEDIA_NEXT, false) }) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Next")
        }
    }

    ApplianceButtonGrid(
        items = listOf(
            "Bass +" to { onButtonPress(RemoteButton.BASS_UP, false) },
            "Bass -" to { onButtonPress(RemoteButton.BASS_DOWN, false) },
            "Source / Input" to { onButtonPress(RemoteButton.TV_INPUT, false) },
            "EQ Mode" to { onButtonPress(RemoteButton.EQ_MODE, false) }
        )
    )
}

@Composable
private fun ProjectorRemoteBody(
    brand: BrandOption,
    uiState: RemoteUiState,
    onButtonPress: (RemoteButton, Boolean) -> Unit,
    onLaunchApp: (LaunchableApp) -> Unit,
    onOpenConnect: () -> Unit
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
        val isWifiTv = (uiState.status as? ConnectionStatus.Connected)?.device?.transport == TransportMode.WIFI_ANDROID_TV
        if (!isWifiTv) {
            InfoBannerCard(
                title = "Connect Smart Projector via Wi-Fi",
                body = "Direct app launch works over Android TV Wi-Fi connection.",
                buttonText = "Connect Wi-Fi",
                onAction = onOpenConnect
            )
        }
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
            NavCircleButton(Icons.Rounded.Menu, "Menu") {
                onButtonPress(RemoteButton.MENU, false)
            }
            NavCircleButton(Icons.AutoMirrored.Rounded.Input, "Source") {
                onButtonPress(RemoteButton.TV_INPUT, false)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = { onButtonPress(RemoteButton.VOLUME_DOWN, false) },
                modifier = Modifier.weight(1f)
            ) { Text("Vol -") }
            FilledTonalButton(
                onClick = { onButtonPress(RemoteButton.MUTE, true) },
                modifier = Modifier.weight(0.8f)
            ) { Text("Mute") }
            FilledTonalButton(
                onClick = { onButtonPress(RemoteButton.VOLUME_UP, false) },
                modifier = Modifier.weight(1f)
            ) { Text("Vol +") }
        }
        FilledTonalButton(
            onClick = { onButtonPress(RemoteButton.KEYSTONE, false) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Keystone / Focus Settings") }
    }
}

@Composable
private fun FanRemoteBody(
    brand: BrandOption,
    hasIr: Boolean,
    onAction: (String) -> Unit
) {
    var speed by remember { mutableIntStateOf(3) }

    if (!hasIr) {
        NoIrWarningCard(brand.name)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(RemoteSurfaceElevated)
            .border(1.5.dp, RemoteBorder, RoundedCornerShape(24.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Fan Speed", color = TextSecondary, fontSize = 13.sp)
        Text("Level $speed", color = TextPrimary, fontSize = 38.sp, fontWeight = FontWeight.Bold)
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

    ApplianceButtonGrid(
        items = listOf(
            "Boost" to { onAction("boost") },
            "Sleep / Breeze" to { onAction("sleep") },
            "Timer (1h–8h)" to { onAction("timer") },
            "LED Light" to { onAction("light") }
        )
    )
}

@Composable
private fun PcMediaVolumeRow(onButtonPress: (RemoteButton) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = { onButtonPress(RemoteButton.VOLUME_DOWN) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Vol -") }
            FilledTonalIconButton(
                onClick = { onButtonPress(RemoteButton.MUTE) },
                modifier = Modifier.size(48.dp)
            ) { Icon(Icons.AutoMirrored.Rounded.VolumeOff, contentDescription = "Mute") }
            FilledTonalButton(
                onClick = { onButtonPress(RemoteButton.VOLUME_UP) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Vol +") }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(RemoteSurface)
                .border(1.dp, RemoteBorder, RoundedCornerShape(16.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FilledTonalIconButton(onClick = { onButtonPress(RemoteButton.MEDIA_PREVIOUS) }) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous")
            }
            FilledTonalIconButton(onClick = { onButtonPress(RemoteButton.MEDIA_PLAY_PAUSE) }) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play/Pause", tint = MiOrange)
            }
            FilledTonalIconButton(onClick = { onButtonPress(RemoteButton.MEDIA_STOP) }) {
                Icon(Icons.Rounded.Stop, contentDescription = "Stop")
            }
            FilledTonalIconButton(onClick = { onButtonPress(RemoteButton.MEDIA_NEXT) }) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next")
            }
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
                            .height(62.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(RemoteSurfaceElevated)
                            .border(1.dp, RemoteBorder, RoundedCornerShape(16.dp))
                            .clickable { onLaunch(app) }
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = app.name,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = "Launch ${app.name}",
                            tint = MiOrange,
                            modifier = Modifier.size(18.dp)
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
private fun ApplianceButtonGrid(items: List<Pair<String, () -> Unit>>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { (label, onClick) ->
                    FilledTonalButton(
                        onClick = onClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun NoIrWarningCard(brandName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF2B2214))
            .border(1.dp, Color(0xFFFBBF24).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "No IR Blaster Detected on Phone",
            color = Color(0xFFFBBF24),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Controlling $brandName appliances directly requires a phone with a built-in Infrared (IR) emitter.",
            color = TextSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun InfoBannerCard(
    title: String,
    body: String,
    buttonText: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RemoteSurfaceElevated)
            .border(1.dp, WifiBlue.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = WifiBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = TextSecondary, fontSize = 12.sp)
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(containerColor = WifiBlue),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(buttonText, color = Color(0xFF062033), fontWeight = FontWeight.SemiBold)
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (highlight) MiOrange.copy(alpha = 0.2f) else RemoteSurfaceElevated,
                contentColor = if (highlight) MiOrange else TextPrimary
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
