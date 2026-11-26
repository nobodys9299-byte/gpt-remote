package com.gptremote.mitv

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
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
import com.gptremote.mitv.model.BrandOption
import com.gptremote.mitv.model.DeviceCategory
import com.gptremote.mitv.model.ProductCatalog
import com.gptremote.mitv.model.ProductType
import com.gptremote.mitv.ui.BrandPickerScreen
import com.gptremote.mitv.ui.HomeHubScreen
import com.gptremote.mitv.ui.RemoteScreen
import com.gptremote.mitv.ui.RemoteViewModel
import com.gptremote.mitv.ui.theme.MiTvRemoteTheme

private sealed interface AppRoute {
    data object Home : AppRoute
    data class BrandList(val product: ProductType) : AppRoute
    data class Remote(val brand: BrandOption) : AppRoute
}

private enum class FilePickTarget {
    PC_UPLOAD,
    PHONE_HOST_ADD,
    PHONE_PEER_SEND
}

class MainActivity : ComponentActivity() {

    private val viewModel: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            MiTvRemoteTheme(accentTheme = uiState.accentTheme) {
                var hasBtPermissions by remember { mutableStateOf(viewModel.hasBluetoothPermissions()) }
                var route by remember { mutableStateOf<AppRoute>(AppRoute.Home) }
                var filePickTarget by remember { mutableStateOf(FilePickTarget.PC_UPLOAD) }

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

                val screenCastLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val data = result.data
                    if (result.resultCode == RESULT_OK && data != null) {
                        viewModel.startPhoneCast(result.resultCode, data)
                    }
                }

                val pickFileLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        when (filePickTarget) {
                            FilePickTarget.PC_UPLOAD -> viewModel.uploadFileToPc(uri)
                            FilePickTarget.PHONE_HOST_ADD -> viewModel.addLocalFileToPhoneShare(uri)
                            FilePickTarget.PHONE_PEER_SEND -> viewModel.sendFileToPeerPhone(uri)
                        }
                    }
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

                when (val current = route) {
                    AppRoute.Home -> {
                        HomeHubScreen(
                            uiState = uiState,
                            onSelectProduct = { product ->
                                if (product == ProductType.PHONE_TO_PHONE) {
                                    val phoneBrand = ProductCatalog.brandsFor(product).first()
                                    viewModel.selectProductBrand(phoneBrand)
                                    route = AppRoute.Remote(phoneBrand)
                                } else {
                                    route = AppRoute.BrandList(product)
                                }
                            },
                            onOpenActiveRemote = {
                                val activeBrand = uiState.selectedBrand ?: run {
                                    val fallbackProduct = if (uiState.connectedCategory == DeviceCategory.LAPTOP_PC) {
                                        ProductType.PC_LAPTOP
                                    } else {
                                        ProductType.SMART_TV
                                    }
                                    ProductCatalog.brandsFor(fallbackProduct).first()
                                }
                                route = AppRoute.Remote(activeBrand)
                            },
                            onDisconnect = viewModel::disconnect,
                            onSelectHapticStrength = viewModel::setHapticStrength,
                            onToggleSound = viewModel::setSoundEnabled,
                            onSelectAccentTheme = viewModel::setAccentTheme,
                            onClearBanner = viewModel::clearBanner
                        )
                    }

                    is AppRoute.BrandList -> {
                        BackHandler { route = AppRoute.Home }
                        BrandPickerScreen(
                            product = current.product,
                            uiState = uiState,
                            onBack = { route = AppRoute.Home },
                            onSelectBrand = { brand ->
                                viewModel.selectProductBrand(brand)
                                route = AppRoute.Remote(brand)
                            },
                            onSelectHapticStrength = viewModel::setHapticStrength,
                            onToggleSound = viewModel::setSoundEnabled,
                            onSelectAccentTheme = viewModel::setAccentTheme
                        )
                    }

                    is AppRoute.Remote -> {
                        val backTarget = if (current.brand.productType == ProductType.PHONE_TO_PHONE) {
                            AppRoute.Home
                        } else {
                            AppRoute.BrandList(current.brand.productType)
                        }
                        BackHandler { route = backTarget }
                        RemoteScreen(
                            brand = current.brand,
                            uiState = uiState,
                            hasBluetoothPermissions = hasBtPermissions,
                            onNavigateBack = { route = backTarget },
                            onRequestBluetoothPermissions = requestBtPermissions,
                            onMakeDiscoverable = {
                                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
                                }
                                discoverableLauncher.launch(intent)
                            },
                            onScanBluetooth = viewModel::startBluetoothScan,
                            onConnectBluetooth = viewModel::connectBluetooth,
                            onScanWifiTv = viewModel::startWifiScan,
                            onConnectWifiTv = viewModel::connectWifi,
                            onScanWifiPc = viewModel::startWifiPcScan,
                            onConnectWifiPc = viewModel::connectWifiPc,
                            onConnectManualIp = { host, category ->
                                viewModel.addAndConnectManualWifi(host, current.brand.name, category)
                            },
                            onQrScanned = { rawQr ->
                                viewModel.connectFromQr(rawQr)
                            },
                            onScanBulbs = viewModel::startBulbScan,
                            onSelectBulb = viewModel::selectBulb,
                            onConnectManualBulb = viewModel::connectManualBulb,
                            onBulbAction = { power, bright, rgb, kelvin, irAction ->
                                viewModel.setBulbState(power, bright, rgb, kelvin, irAction)
                            },
                            onEnableIr = { viewModel.setIrMode(true) },
                            onSubmitWifiPin = viewModel::submitWifiPin,
                            onDisconnect = viewModel::disconnect,
                            onStartPcMirror = viewModel::startPcScreenMirror,
                            onStopPcMirror = viewModel::stopPcScreenMirror,
                            onTapMirroredScreen = viewModel::sendPcScreenTap,
                            onMoveMirroredScreen = viewModel::sendPcScreenMove,
                            onStartPhoneCast = {
                                val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                screenCastLauncher.launch(mgr.createScreenCaptureIntent())
                            },
                            onStopPhoneCast = viewModel::stopPhoneCast,
                            onRefreshPcFiles = viewModel::refreshPcFiles,
                            onPickFileToSend = {
                                filePickTarget = FilePickTarget.PC_UPLOAD
                                pickFileLauncher.launch(arrayOf("*/*"))
                            },
                            onDownloadPcFile = { file -> viewModel.downloadFileFromPc(file.name) },
                            onStartPhoneShareHost = viewModel::startPhoneShareHost,
                            onPickHostFileToShare = {
                                filePickTarget = FilePickTarget.PHONE_HOST_ADD
                                pickFileLauncher.launch(arrayOf("*/*"))
                            },
                            onRefreshPeerPhoneFiles = viewModel::refreshPeerPhoneFiles,
                            onPickFileForPeerPhone = {
                                filePickTarget = FilePickTarget.PHONE_PEER_SEND
                                pickFileLauncher.launch(arrayOf("*/*"))
                            },
                            onDownloadFromPeerPhone = { file -> viewModel.downloadFileFromPeerPhone(file.name) },
                            onButtonPress = viewModel::pressButton,
                            onApplianceAction = viewModel::sendApplianceAction,
                            onLaunchApp = viewModel::launchApp,
                            onSendText = viewModel::sendText,
                            onMouseMove = viewModel::moveMouse,
                            onMouseClick = viewModel::clickMouse,
                            onSelectHapticStrength = viewModel::setHapticStrength,
                            onToggleSound = viewModel::setSoundEnabled,
                            onSelectAccentTheme = viewModel::setAccentTheme,
                            onClearBanner = viewModel::clearBanner
                        )
                    }
                }
            }
        }
    }
}
