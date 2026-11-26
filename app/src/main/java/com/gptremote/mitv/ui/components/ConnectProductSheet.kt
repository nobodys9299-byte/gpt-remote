package com.gptremote.mitv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gptremote.mitv.model.BrandOption
import com.gptremote.mitv.model.ConnectionStatus
import com.gptremote.mitv.model.DeviceCategory
import com.gptremote.mitv.model.ProductType
import com.gptremote.mitv.model.RemoteUiState
import com.gptremote.mitv.model.TvDevice
import com.gptremote.mitv.ui.theme.ConnectedGreen
import com.gptremote.mitv.ui.theme.MiOrange
import com.gptremote.mitv.ui.theme.RemoteBorder
import com.gptremote.mitv.ui.theme.RemoteSurface
import com.gptremote.mitv.ui.theme.RemoteSurfaceElevated
import com.gptremote.mitv.ui.theme.TextPrimary
import com.gptremote.mitv.ui.theme.TextSecondary
import com.gptremote.mitv.ui.theme.WifiBlue
import com.gptremote.mitv.wifi.DiscoveredSmartBulb

private enum class ConnectTab {
    BLUETOOTH,
    WIFI_TV,
    WIFI_PC,
    WIFI_SMART_BULB,
    WIFI_APPLIANCE,
    IR_BLASTER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectProductSheet(
    brand: BrandOption,
    uiState: RemoteUiState,
    hasBluetoothPermissions: Boolean,
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
    onEnableIr: () -> Unit,
    onSubmitWifiPin: (String) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    val availableTabs = remember(brand) {
        buildList {
            if (brand.productType.supportsBluetoothHid || brand.productType.supportsBluetoothGeneric) {
                add(ConnectTab.BLUETOOTH)
            }
            if (brand.supportsWifiTv) add(ConnectTab.WIFI_TV)
            if (brand.productType.supportsWifiPc) add(ConnectTab.WIFI_PC)
            if (brand.productType == ProductType.SMART_BULB) add(ConnectTab.WIFI_SMART_BULB)
            if (brand.productType.supportsWifiSmart &&
                !brand.supportsWifiTv &&
                brand.productType != ProductType.SMART_BULB
            ) {
                add(ConnectTab.WIFI_APPLIANCE)
            }
            if (brand.productType.supportsIr) add(ConnectTab.IR_BLASTER)
        }
    }
    var selectedIdx by remember(brand) {
        mutableIntStateOf(
            if (brand.productType == ProductType.SMART_BULB) {
                availableTabs.indexOf(ConnectTab.WIFI_SMART_BULB).coerceAtLeast(0)
            } else {
                0
            }
        )
    }
    val activeTab = availableTabs.getOrElse(selectedIdx) { availableTabs.first() }

    var manualIp by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }

    val awaitingPin = uiState.status as? ConnectionStatus.AwaitingWifiPin
    if (awaitingPin != null) {
        AlertDialog(
            onDismissRequest = onDisconnect,
            title = { Text("Enter TV Pairing PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter the 6-character code shown on ${awaitingPin.device.name}:",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it.uppercase().take(6) },
                        label = { Text("6-char hex PIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    awaitingPin.message?.let { err ->
                        Text(text = err, color = Color(0xFFF87171), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSubmitWifiPin(pinInput)
                        pinInput = ""
                    },
                    enabled = pinInput.length == 6
                ) {
                    Text("Pair")
                }
            },
            dismissButton = {
                TextButton(onClick = onDisconnect) { Text("Cancel") }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = RemoteSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Connect ${brand.name}",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = brand.note,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                if (uiState.isConnected || uiState.irActive || uiState.connectedBulb != null) {
                    TextButton(onClick = onDisconnect) {
                        Text("Disconnect", color = Color(0xFFF87171))
                    }
                }
            }

            if (availableTabs.size > 1) {
                TabRow(
                    selectedTabIndex = selectedIdx.coerceIn(0, availableTabs.lastIndex),
                    containerColor = RemoteSurfaceElevated
                ) {
                    availableTabs.forEachIndexed { idx, tab ->
                        val (label, icon) = when (tab) {
                            ConnectTab.BLUETOOTH -> "Bluetooth" to Icons.Rounded.Bluetooth
                            ConnectTab.WIFI_TV -> "Wi-Fi TV" to Icons.Rounded.Wifi
                            ConnectTab.WIFI_PC -> "Wi-Fi PC" to Icons.Rounded.Wifi
                            ConnectTab.WIFI_SMART_BULB -> "Wi-Fi Bulb" to Icons.Rounded.Lightbulb
                            ConnectTab.WIFI_APPLIANCE -> "Wi-Fi" to Icons.Rounded.Wifi
                            ConnectTab.IR_BLASTER -> "IR Blaster" to Icons.Rounded.SettingsRemote
                        }
                        Tab(
                            selected = selectedIdx == idx,
                            onClick = {
                                selectedIdx = idx
                                if (tab == ConnectTab.WIFI_TV) onScanWifiTv()
                                if (tab == ConnectTab.WIFI_PC) onScanWifiPc()
                                if (tab == ConnectTab.WIFI_SMART_BULB) onScanBulbs()
                            },
                            text = { Text(label, fontSize = 11.sp, maxLines = 1) },
                            icon = { Icon(icon, contentDescription = null) }
                        )
                    }
                }
            }

            when (activeTab) {
                ConnectTab.BLUETOOTH -> {
                    if (!hasBluetoothPermissions) {
                        Button(
                            onClick = onRequestBluetoothPermissions,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MiOrange)
                        ) {
                            Text("Grant Bluetooth Permissions")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onScanBluetooth,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (uiState.isScanningBluetooth) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (uiState.isScanningBluetooth) "Scanning..." else "Scan")
                            }
                            OutlinedButton(
                                onClick = onMakeDiscoverable,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Discoverable")
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (uiState.bluetoothDevices.isEmpty()) {
                                item {
                                    Text(
                                        text = "Tap Scan or pair your phone from ${brand.name} Bluetooth settings.",
                                        color = TextSecondary,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            } else {
                                items(uiState.bluetoothDevices, key = { it.id }) { dev ->
                                    val connected = (uiState.status as? ConnectionStatus.Connected)?.device?.id == dev.id
                                    ConnectRow(
                                        title = dev.name,
                                        subtitle = "${dev.id} • ${if (dev.isBonded) "Paired" else "Available"}",
                                        isConnected = connected,
                                        accent = MiOrange,
                                        isPc = brand.productType == ProductType.PC_LAPTOP,
                                        onClick = {
                                            onConnectBluetooth(dev)
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                ConnectTab.WIFI_TV -> {
                    val isSamsung = brand.id == "tv_samsung"
                    val isLg = brand.id == "tv_lg"
                    Text(
                        text = when {
                            isSamsung -> "Samsung Tizen TV: enter your TV IP below and allow the pop-up on your TV screen."
                            isLg -> "LG webOS TV: enter your TV IP below (enable LG Connect Apps in TV Network settings)."
                            else -> "Scan local Wi-Fi Android TVs or enter TV IP directly."
                        },
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = manualIp,
                            onValueChange = { manualIp = it },
                            label = {
                                Text(
                                    when {
                                        isSamsung -> "Samsung TV IP (e.g. 192.168.1.40)"
                                        isLg -> "LG webOS TV IP (e.g. 192.168.1.40)"
                                        else -> "TV IP (e.g. 192.168.1.40)"
                                    }
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                onConnectManualIp(manualIp, DeviceCategory.ANDROID_TV)
                                onDismiss()
                            },
                            enabled = manualIp.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = WifiBlue)
                        ) {
                            Text("Go")
                        }
                    }
                    FilledTonalButton(
                        onClick = onScanWifiTv,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (uiState.isScanningWifi) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (uiState.isScanningWifi) "Discovering TVs..." else "Scan Wi-Fi TVs")
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(168.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.wifiDevices, key = { it.id }) { dev ->
                            val connected = (uiState.status as? ConnectionStatus.Connected)?.device?.id == dev.id
                            ConnectRow(
                                title = dev.name,
                                subtitle = "${dev.host}:${dev.port}",
                                isConnected = connected,
                                accent = WifiBlue,
                                isPc = false,
                                onClick = {
                                    onConnectWifiTv(dev)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }

                ConnectTab.WIFI_PC -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = manualIp,
                            onValueChange = { manualIp = it },
                            label = { Text("PC Wi-Fi IP (e.g. 10.31.1.70)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                onConnectManualIp(manualIp, DeviceCategory.LAPTOP_PC)
                                onDismiss()
                            },
                            enabled = manualIp.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = WifiBlue)
                        ) {
                            Text("Go")
                        }
                    }
                    FilledTonalButton(
                        onClick = onScanWifiPc,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (uiState.isScanningWifiPc) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (uiState.isScanningWifiPc) "Scanning Network..." else "Scan Wi-Fi PCs")
                    }
                    Text(
                        text = "Install GptRemotePC-Setup.exe on Windows to control mouse, mirror screens, and share files.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.wifiPcDevices, key = { it.id }) { dev ->
                            val connected = (uiState.status as? ConnectionStatus.Connected)?.device?.id == dev.id
                            ConnectRow(
                                title = dev.name,
                                subtitle = "${dev.host}:${dev.port} • ${dev.osName}",
                                isConnected = connected,
                                accent = WifiBlue,
                                isPc = true,
                                onClick = {
                                    onConnectWifiPc(dev)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }

                ConnectTab.WIFI_SMART_BULB -> {
                    Text(
                        text = "Discover WiZ / Yeelight bulbs on local Wi-Fi or enter your bulb IP.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = manualIp,
                            onValueChange = { manualIp = it },
                            label = { Text("Smart Bulb IP (e.g. 192.168.1.55)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                onConnectManualBulb(manualIp)
                                onDismiss()
                            },
                            enabled = manualIp.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = MiOrange)
                        ) {
                            Text("Go")
                        }
                    }
                    FilledTonalButton(
                        onClick = onScanBulbs,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (uiState.isScanningBulbs) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (uiState.isScanningBulbs) "Scanning Bulbs..." else "Scan Smart Bulbs (WiZ)")
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.smartBulbs, key = { it.host }) { bulb ->
                            val isSelected = uiState.connectedBulb?.host == bulb.host
                            ConnectRow(
                                title = bulb.name,
                                subtitle = "${bulb.host} • ${bulb.protocol.name}",
                                isConnected = isSelected,
                                accent = MiOrange,
                                isPc = false,
                                onClick = {
                                    onSelectBulb(bulb)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }

                ConnectTab.WIFI_APPLIANCE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(RemoteSurfaceElevated)
                            .border(1.dp, RemoteBorder, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${brand.name} Wi-Fi / Smart Appliance",
                            color = WifiBlue,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Pair your ${brand.productType.title} with the Bluetooth tab or enter its local network IP below.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = manualIp,
                                onValueChange = { manualIp = it },
                                label = { Text("Device Wi-Fi IP") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    onConnectManualIp(manualIp, DeviceCategory.ANDROID_TV)
                                    onDismiss()
                                },
                                enabled = manualIp.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = WifiBlue)
                            ) {
                                Text("Go")
                            }
                        }
                    }
                }

                ConnectTab.IR_BLASTER -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(RemoteSurfaceElevated)
                            .border(1.dp, RemoteBorder, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (uiState.hasIrEmitter) {
                                "Built-in IR Blaster Detected"
                            } else {
                                "No IR Blaster on This Phone"
                            },
                            color = if (uiState.hasIrEmitter) ConnectedGreen else Color(0xFFFBBF24),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (uiState.hasIrEmitter) {
                                "Point the top of your phone directly at your ${brand.name} ${brand.productType.title} and tap Use IR Mode."
                            } else {
                                "Infrared requires a hardware IR emitter (Xiaomi/Redmi/POCO phones). Use Bluetooth or Wi-Fi where supported by your ${brand.productType.title}."
                            },
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        if (uiState.hasIrEmitter) {
                            Button(
                                onClick = {
                                    onEnableIr()
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MiOrange)
                            ) {
                                Text("Use IR Blaster Mode")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectRow(
    title: String,
    subtitle: String,
    isConnected: Boolean,
    accent: Color,
    isPc: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RemoteSurfaceElevated)
            .border(
                width = 1.dp,
                color = if (isConnected) ConnectedGreen else RemoteBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isPc) Icons.Rounded.Laptop else Icons.Rounded.Tv,
                contentDescription = null,
                tint = if (isConnected) ConnectedGreen else accent
            )
            Column {
                Text(text = title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, color = TextSecondary, fontSize = 12.sp)
            }
        }
        if (isConnected) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = "Connected", tint = ConnectedGreen)
        }
    }
}
