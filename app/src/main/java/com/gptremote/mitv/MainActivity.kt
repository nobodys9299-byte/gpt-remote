package com.gptremote.mitv

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.gptremote.mitv.model.ConnectionStatus
import com.gptremote.mitv.ui.HomeHubScreen
import com.gptremote.mitv.ui.RemoteScreen
import com.gptremote.mitv.ui.RemoteViewModel
import com.gptremote.mitv.ui.theme.MiTvRemoteTheme

private enum class AppPage {
    HOME_HUB,
    REMOTE
}

class MainActivity : ComponentActivity() {

    private val viewModel: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MiTvRemoteTheme {
                val uiState by viewModel.uiState.collectAsState()
                var hasBtPermissions by remember { mutableStateOf(viewModel.hasBluetoothPermissions()) }
                var currentPage by remember { mutableStateOf(AppPage.HOME_HUB) }
                var wasConnected by remember { mutableStateOf(false) }

                // Automatically open RemoteScreen when a new connection succeeds
                LaunchedEffect(uiState.status) {
                    val isNowConnected = uiState.status is ConnectionStatus.Connected
                    if (isNowConnected && !wasConnected) {
                        currentPage = AppPage.REMOTE
                    }
                    wasConnected = isNowConnected
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    hasBtPermissions = viewModel.hasBluetoothPermissions()
                    if (hasBtPermissions) {
                        viewModel.onPermissionsGranted()
                    }
                }

                val discoverableLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) {
                    viewModel.refreshPairedBluetooth()
                }

                val requestBtPermissions = {
                    val perms = buildList {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            add(Manifest.permission.BLUETOOTH_CONNECT)
                            add(Manifest.permission.BLUETOOTH_SCAN)
                            add(Manifest.permission.BLUETOOTH_ADVERTISE)
                        } else {
                            add(Manifest.permission.BLUETOOTH)
                            add(Manifest.permission.BLUETOOTH_ADMIN)
                            add(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }.toTypedArray()
                    permissionLauncher.launch(perms)
                }

                LaunchedEffect(Unit) {
                    if (hasBtPermissions) {
                        viewModel.onPermissionsGranted()
                    } else {
                        requestBtPermissions()
                    }
                }

                if (currentPage == AppPage.REMOTE) {
                    BackHandler {
                        currentPage = AppPage.HOME_HUB
                    }
                    RemoteScreen(
                        uiState = uiState,
                        onNavigateBackToHub = { currentPage = AppPage.HOME_HUB },
                        onButtonPress = viewModel::pressButton,
                        onSendText = viewModel::sendText,
                        onMouseMove = viewModel::moveMouse,
                        onMouseClick = viewModel::clickMouse,
                        onSelectHapticStrength = viewModel::setHapticStrength,
                        onToggleBluetoothPcProfile = viewModel::setBluetoothPcProfile,
                        onClearBanner = viewModel::clearBanner
                    )
                } else {
                    HomeHubScreen(
                        uiState = uiState,
                        hasBluetoothPermissions = hasBtPermissions,
                        onRequestBluetoothPermissions = requestBtPermissions,
                        onMakeDiscoverable = {
                            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
                            }
                            discoverableLauncher.launch(intent)
                        },
                        onScanAll = viewModel::scanAllDevices,
                        onScanBluetooth = viewModel::startBluetoothScan,
                        onScanWifiTv = viewModel::startWifiScan,
                        onScanWifiPc = viewModel::startWifiPcScan,
                        onConnectBluetooth = viewModel::connectBluetooth,
                        onConnectWifiTv = viewModel::connectWifi,
                        onConnectWifiPc = viewModel::connectWifiPc,
                        onConnectSaved = viewModel::connectSavedDevice,
                        onAddManualWifi = viewModel::addAndConnectManualWifi,
                        onCustomizeSaved = viewModel::customizeSavedDevice,
                        onRemoveSaved = viewModel::removeSavedDevice,
                        onSubmitWifiPin = viewModel::submitWifiPin,
                        onDisconnect = viewModel::disconnect,
                        onOpenRemote = { currentPage = AppPage.REMOTE },
                        onSelectHapticStrength = viewModel::setHapticStrength,
                        onToggleBluetoothPcProfile = viewModel::setBluetoothPcProfile,
                        onClearBanner = viewModel::clearBanner
                    )
                }
            }
        }
    }
}
