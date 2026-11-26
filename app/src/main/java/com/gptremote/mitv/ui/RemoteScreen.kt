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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
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
import com.gptremote.mitv.model.ConnectionStatus
import com.gptremote.mitv.model.DeviceCategory
import com.gptremote.mitv.model.MouseButton
import com.gptremote.mitv.model.RemoteButton
import com.gptremote.mitv.model.RemoteUiState
import com.gptremote.mitv.model.TransportMode
import com.gptremote.mitv.ui.components.DPadControl
import com.gptremote.mitv.ui.components.KeyboardSheet
import com.gptremote.mitv.ui.components.MediaVolumeBar
import com.gptremote.mitv.ui.components.SettingsSheet
import com.gptremote.mitv.ui.components.TouchpadControl
import com.gptremote.mitv.ui.theme.ConnectedGreen
import com.gptremote.mitv.ui.theme.MiOrange
import com.gptremote.mitv.ui.theme.RemoteBg
import com.gptremote.mitv.ui.theme.RemoteBorder
import com.gptremote.mitv.ui.theme.RemoteSurfaceElevated
import com.gptremote.mitv.ui.theme.TextPrimary
import com.gptremote.mitv.ui.theme.TextSecondary
import com.gptremote.mitv.ui.theme.WifiBlue

private enum class PadMode {
    DPAD,
    TOUCHPAD
}

@Composable
fun RemoteScreen(
    uiState: RemoteUiState,
    onNavigateBackToHub: () -> Unit,
    onButtonPress: (RemoteButton, Boolean) -> Unit,
    onSendText: (String) -> Unit,
    onMouseMove: (dx: Int, dy: Int, wheel: Int) -> Unit,
    onMouseClick: (MouseButton) -> Unit,
    onSelectHapticStrength: (HapticStrength) -> Unit,
    onToggleBluetoothPcProfile: (Boolean) -> Unit,
    onClearBanner: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val isPcDevice = uiState.connectedCategory == DeviceCategory.LAPTOP_PC ||
        uiState.activeTransport == TransportMode.WIFI_PC ||
        (uiState.activeTransport == TransportMode.BLUETOOTH_HID && uiState.bluetoothPcProfile)

    var padMode by remember(isPcDevice) {
        mutableStateOf(if (isPcDevice) PadMode.TOUCHPAD else PadMode.DPAD)
    }
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
            TopControlBar(
                uiState = uiState,
                isPcDevice = isPcDevice,
                onBackToHub = onNavigateBackToHub,
                onPowerClick = { onButtonPress(RemoteButton.POWER, true) },
                onSettingsClick = { showSettingsSheet = true },
                onKeyboardClick = { showKeyboardSheet = true }
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = padMode == PadMode.DPAD,
                    onClick = { padMode = PadMode.DPAD },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Rounded.Gamepad, contentDescription = null) },
                    label = { Text(if (isPcDevice) "Arrow Keys" else "D-Pad") }
                )
                SegmentedButton(
                    selected = padMode == PadMode.TOUCHPAD,
                    onClick = { padMode = PadMode.TOUCHPAD },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Rounded.TouchApp, contentDescription = null) },
                    label = { Text("Touchpad") }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                when (padMode) {
                    PadMode.DPAD -> DPadControl(
                        onButtonPress = { btn ->
                            onButtonPress(btn, btn == RemoteButton.DPAD_CENTER)
                        }
                    )
                    PadMode.TOUCHPAD -> TouchpadControl(
                        transportMode = (uiState.status as? ConnectionStatus.Connected)?.device?.transport
                            ?: uiState.activeTransport,
                        onMouseMove = onMouseMove,
                        onMouseClick = onMouseClick,
                        onButtonFallback = { btn -> onButtonPress(btn, false) }
                    )
                }
            }

            // Core Navigation Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavCircleButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    label = if (isPcDevice) "Esc" else "Back",
                    onClick = { onButtonPress(RemoteButton.BACK, false) }
                )
                NavCircleButton(
                    icon = if (isPcDevice) Icons.Rounded.Laptop else Icons.Rounded.Home,
                    label = if (isPcDevice) "Start" else "Home",
                    highlight = true,
                    onClick = { onButtonPress(RemoteButton.HOME, true) }
                )
                NavCircleButton(
                    icon = Icons.Rounded.Menu,
                    label = "Menu",
                    onClick = { onButtonPress(RemoteButton.MENU, false) }
                )
                NavCircleButton(
                    icon = Icons.Rounded.Keyboard,
                    label = "Type",
                    onClick = { showKeyboardSheet = true }
                )
            }

            if (isPcDevice) {
                // Dedicated Laptop / PC Shortcuts Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        RemoteButton.PC_ALT_TAB to "Alt+Tab",
                        RemoteButton.TAB to "Tab",
                        RemoteButton.PC_COPY to "Copy",
                        RemoteButton.PC_PASTE to "Paste",
                        RemoteButton.PC_SHOW_DESKTOP to "Desktop",
                        RemoteButton.ENTER to "Enter"
                    ).forEach { (btn, label) ->
                        FilledTonalButton(
                            onClick = { onButtonPress(btn, false) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(label, fontSize = 12.sp)
                        }
                    }
                }
            }

            MediaVolumeBar(
                onButtonPress = { btn -> onButtonPress(btn, btn == RemoteButton.MUTE) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickShortcutButton(
                    icon = Icons.Rounded.Search,
                    label = "Search",
                    onClick = { onButtonPress(RemoteButton.SEARCH, false) },
                    modifier = Modifier.weight(1f)
                )
                if (!isPcDevice) {
                    QuickShortcutButton(
                        icon = Icons.AutoMirrored.Rounded.Input,
                        label = "Input",
                        onClick = { onButtonPress(RemoteButton.TV_INPUT, false) },
                        modifier = Modifier.weight(1f)
                    )
                }
                QuickShortcutButton(
                    icon = Icons.Rounded.Settings,
                    label = if (isPcDevice) "PC Settings" else "TV Settings",
                    onClick = { onButtonPress(RemoteButton.SETTINGS, false) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
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
private fun TopControlBar(
    uiState: RemoteUiState,
    isPcDevice: Boolean,
    onBackToHub: () -> Unit,
    onPowerClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onKeyboardClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = onBackToHub,
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = RemoteSurfaceElevated,
                contentColor = TextPrimary
            )
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to Hub")
        }

        val (statusText, dotColor, transportIcon) = when (val st = uiState.status) {
            is ConnectionStatus.Connected -> {
                val icon = when (st.device.transport) {
                    TransportMode.BLUETOOTH_HID -> Icons.Rounded.Bluetooth
                    TransportMode.WIFI_ANDROID_TV, TransportMode.WIFI_PC -> Icons.Rounded.Wifi
                }
                Triple(st.device.name, ConnectedGreen, icon)
            }
            is ConnectionStatus.Connecting -> Triple("Connecting...", MiOrange, Icons.Rounded.Bluetooth)
            is ConnectionStatus.AwaitingWifiPin -> Triple("Enter PIN", WifiBlue, Icons.Rounded.Wifi)
            is ConnectionStatus.Error -> Triple("Tap for Hub", Color(0xFFF87171), Icons.Rounded.Bluetooth)
            ConnectionStatus.Disconnected -> Triple("Pick Device", TextSecondary, Icons.Rounded.Bluetooth)
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(RemoteSurfaceElevated)
                .border(1.dp, RemoteBorder, RoundedCornerShape(22.dp))
                .clickable(onClick = onBackToHub)
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
            Spacer(modifier = Modifier.size(6.dp))
            Icon(
                imageVector = transportIcon,
                contentDescription = null,
                tint = dotColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = statusText,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalIconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = RemoteSurfaceElevated,
                    contentColor = TextPrimary
                )
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = "Haptics & Settings")
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
                Icon(
                    Icons.Rounded.PowerSettingsNew,
                    contentDescription = if (isPcDevice) "Lock / Power" else "Power"
                )
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(modifier = Modifier.size(6.dp))
        Text(text = label, fontSize = 12.sp)
    }
}
