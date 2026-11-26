package com.gptremote.mitv.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.gptremote.mitv.model.ConnectionStatus
import com.gptremote.mitv.model.DeviceCategory
import com.gptremote.mitv.model.MouseButton
import com.gptremote.mitv.model.RemoteButton
import com.gptremote.mitv.model.TransportMode
import com.gptremote.mitv.model.TvDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages Android's BluetoothHidDevice profile so this phone emulates a Bluetooth HID
 * remote (Keyboard + Consumer Control + Mouse/Touchpad) for Mi Android TV.
 *
 * All HID reports run off the main thread:
 * - Button and text events are serialized sequentially to prevent press/release races.
 * - Mouse deltas are accumulated atomically and flushed at ~80Hz (12ms) to eliminate Binder/L2CAP lag.
 */
@SuppressLint("MissingPermission")
class BluetoothHidManager(private val context: Context) {

    private val hidExecutor = Executors.newSingleThreadExecutor()
    private val hidDispatcher = hidExecutor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + hidDispatcher)
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHost: BluetoothDevice? = null
    private var pendingConnectAddress: String? = null
    private var connectedCategory: DeviceCategory = DeviceCategory.ANDROID_TV
    private var receiverRegistered = false

    private val _isPcProfile = MutableStateFlow(false)
    val isPcProfile: StateFlow<Boolean> = _isPcProfile.asStateFlow()

    // Serialized queue for discrete key/consumer/click/text actions
    private val actionQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var queueConsumerJob: Job? = null

    // Coalesced atomic mouse delta accumulators flushed by mouseTickerJob at ~80Hz
    private val pendingDx = AtomicInteger(0)
    private val pendingDy = AtomicInteger(0)
    private val pendingWheel = AtomicInteger(0)
    private var mouseTickerJob: Job? = null

    private val _isBluetoothEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled == true)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _isAppRegistered = MutableStateFlow(false)
    val isAppRegistered: StateFlow<Boolean> = _isAppRegistered.asStateFlow()

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _devices = MutableStateFlow<List<TvDevice.BluetoothTv>>(emptyList())
    val devices: StateFlow<List<TvDevice.BluetoothTv>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val discoveredDevicesMap = LinkedHashMap<String, TvDevice.BluetoothTv>()

    init {
        startWorkers()
    }

    private fun startWorkers() {
        queueConsumerJob = scope.launch {
            for (action in actionQueue) {
                runCatching { action() }
            }
        }

        mouseTickerJob = scope.launch {
            while (isActive) {
                val dxTotal = pendingDx.getAndSet(0)
                val dyTotal = pendingDy.getAndSet(0)
                val wheelTotal = pendingWheel.getAndSet(0)

                if (dxTotal != 0 || dyTotal != 0 || wheelTotal != 0) {
                    val host = connectedHost
                    val hid = hidDevice
                    if (host != null && hid != null && hasConnectPermission()) {
                        var remX = dxTotal
                        var remY = dyTotal
                        var remW = wheelTotal
                        while (remX != 0 || remY != 0 || remW != 0) {
                            val stepX = remX.coerceIn(-127, 127)
                            val stepY = remY.coerceIn(-127, 127)
                            val stepW = remW.coerceIn(-127, 127)
                            hid.sendReport(
                                host,
                                HidDescriptor.REPORT_ID_MOUSE,
                                HidDescriptor.mouseReport(0, stepX, stepY, stepW)
                            )
                            remX -= stepX
                            remY -= stepY
                            remW -= stepW
                        }
                    }
                }
                delay(12)
            }
        }
    }

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    val enabled = state == BluetoothAdapter.STATE_ON
                    _isBluetoothEnabled.value = enabled
                    if (enabled) {
                        ensureInitialized()
                    } else {
                        connectedHost = null
                        _isAppRegistered.value = false
                        _connectionStatus.value = ConnectionStatus.Disconnected
                    }
                }

                BluetoothDevice.ACTION_FOUND -> {
                    if (!hasConnectPermission()) return
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { addOrUpdateDevice(it) }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    refreshPairedDevices()
                    if (!hasConnectPermission()) return
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    if (device != null &&
                        bondState == BluetoothDevice.BOND_BONDED &&
                        pendingConnectAddress == device.address
                    ) {
                        pendingConnectAddress = null
                        hidDevice?.connect(device)
                    }
                }
            }
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            _isAppRegistered.value = registered
            if (!registered) {
                connectedHost = null
                _connectionStatus.value = ConnectionStatus.Disconnected
                return
            }

            val activeHost = pluggedDevice ?: runCatching {
                if (hasConnectPermission()) hidDevice?.connectedDevices?.firstOrNull() else null
            }.getOrNull()

            if (activeHost != null) {
                connectedHost = activeHost
                val name = safeDeviceName(activeHost)
                _connectionStatus.value = ConnectionStatus.Connected(
                    TvDevice.BluetoothTv(
                        id = activeHost.address,
                        name = name,
                        isBonded = true,
                        category = if (_isPcProfile.value) DeviceCategory.LAPTOP_PC else TvDevice.inferCategory(name)
                    )
                )
                RemoteHidService.start(context, name)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedHost = device
                    pendingConnectAddress = null
                    val address = device?.address ?: "unknown"
                    val name = safeDeviceName(device)
                    val resolvedCategory = if (_isPcProfile.value) {
                        DeviceCategory.LAPTOP_PC
                    } else {
                        connectedCategory
                    }
                    _connectionStatus.value = ConnectionStatus.Connected(
                        TvDevice.BluetoothTv(
                            id = address,
                            name = name,
                            isBonded = true,
                            category = resolvedCategory
                        )
                    )
                    RemoteHidService.start(context, name)
                }

                BluetoothProfile.STATE_CONNECTING -> {
                    val name = safeDeviceName(device)
                    _connectionStatus.value = ConnectionStatus.Connecting(
                        deviceName = name,
                        transport = TransportMode.BLUETOOTH_HID
                    )
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectedHost?.address == device?.address || connectedHost == null) {
                        connectedHost = null
                        _connectionStatus.value = ConnectionStatus.Disconnected
                        RemoteHidService.stop(context)
                    }
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            val hid = hidDevice ?: return
            val payload = when (id.toInt()) {
                HidDescriptor.REPORT_ID_KEYBOARD -> HidDescriptor.KEYBOARD_RELEASE
                HidDescriptor.REPORT_ID_CONSUMER -> HidDescriptor.CONSUMER_RELEASE
                HidDescriptor.REPORT_ID_MOUSE -> HidDescriptor.mouseReport()
                else -> {
                    hid.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
                    return
                }
            }
            hid.replyReport(device, type, id, payload)
        }

        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }

        override fun onSetProtocol(device: BluetoothDevice?, protocol: Byte) {
            // Keep Report Protocol active; no-op acknowledgement
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HID_DEVICE && proxy is BluetoothHidDevice) {
                hidDevice = proxy
                registerHidApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                _isAppRegistered.value = false
                connectedHost = null
                _connectionStatus.value = ConnectionStatus.Disconnected
            }
        }
    }

    fun hasRequiredPermissions(): Boolean {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            listOf(
                android.Manifest.permission.BLUETOOTH,
                android.Manifest.permission.BLUETOOTH_ADMIN,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        return required.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureInitialized() {
        val adapter = bluetoothAdapter
        _isBluetoothEnabled.value = adapter?.isEnabled == true
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(
                context,
                discoveryReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            receiverRegistered = true
        }

        if (adapter == null || !adapter.isEnabled || !hasConnectPermission()) {
            return
        }

        refreshPairedDevices()

        if (hidDevice == null) {
            adapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
        } else if (!_isAppRegistered.value) {
            registerHidApp()
        }
    }

    fun setPcProfile(enabled: Boolean) {
        if (_isPcProfile.value == enabled) return
        _isPcProfile.value = enabled
        connectedCategory = if (enabled) DeviceCategory.LAPTOP_PC else DeviceCategory.ANDROID_TV
        val hid = hidDevice
        if (hid != null && hasConnectPermission() && connectedHost == null) {
            runCatching { hid.unregisterApp() }
            _isAppRegistered.value = false
            registerHidApp()
        }
    }

    private fun registerHidApp() {
        val hid = hidDevice ?: return
        if (!hasConnectPermission()) return

        val pcMode = _isPcProfile.value
        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            if (pcMode) "GPT Remote (PC)" else "Mi TV Remote",
            if (pcMode) "Bluetooth HID Keyboard & Mouse" else "Bluetooth HID Remote Control for Android TV",
            "GPT Remote",
            if (pcMode) BluetoothHidDevice.SUBCLASS1_COMBO else BluetoothHidDevice.SUBCLASS2_REMOTE_CONTROL,
            HidDescriptor.DESCRIPTOR
        )

        val outQos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800,
            9,
            0,
            10000,
            BluetoothHidDeviceAppQosSettings.MAX
        )

        val registered = hid.registerApp(sdpSettings, null, outQos, callbackExecutor, hidCallback)
        if (!registered) {
            _connectionStatus.value = ConnectionStatus.Error(
                "Could not register Bluetooth HID profile on this phone.",
                TransportMode.BLUETOOTH_HID
            )
        }
    }

    fun refreshPairedDevices() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled || !hasConnectPermission()) return

        discoveredDevicesMap.clear()
        adapter.bondedDevices.orEmpty().forEach { addOrUpdateDevice(it) }
        emitSortedDevices()
    }

    fun startDiscovery() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled || !hasRequiredPermissions()) return

        refreshPairedDevices()
        if (adapter.isDiscovering) {
            adapter.cancelDiscovery()
        }
        _isScanning.value = adapter.startDiscovery()
    }

    fun stopDiscovery() {
        val adapter = bluetoothAdapter ?: return
        if (!hasRequiredPermissions()) return
        if (adapter.isDiscovering) {
            adapter.cancelDiscovery()
        }
        _isScanning.value = false
    }

    fun connect(address: String, category: DeviceCategory = DeviceCategory.ANDROID_TV) {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled || !hasConnectPermission()) {
            _connectionStatus.value = ConnectionStatus.Error(
                "Bluetooth permission or adapter unavailable.",
                TransportMode.BLUETOOTH_HID
            )
            return
        }

        stopDiscovery()
        connectedCategory = category
        val shouldBePc = category == DeviceCategory.LAPTOP_PC
        if (_isPcProfile.value != shouldBePc) {
            setPcProfile(shouldBePc)
        }
        val device = adapter.getRemoteDevice(address)
        val hid = hidDevice
        if (hid == null || !_isAppRegistered.value) {
            ensureInitialized()
            _connectionStatus.value = ConnectionStatus.Error(
                "Bluetooth HID service is still initializing. Try again in a moment.",
                TransportMode.BLUETOOTH_HID
            )
            return
        }

        val name = safeDeviceName(device)
        _connectionStatus.value = ConnectionStatus.Connecting(name, TransportMode.BLUETOOTH_HID)

        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            pendingConnectAddress = address
            device.createBond()
            return
        }

        val initiated = hid.connect(device)
        if (!initiated) {
            _connectionStatus.value = ConnectionStatus.Error(
                "Connection not initiated. On Mi TV, open Settings -> Remotes & Accessories -> Add accessory and tap 'Discoverable'.",
                TransportMode.BLUETOOTH_HID
            )
        }
    }

    fun disconnect() {
        val host = connectedHost ?: return
        if (!hasConnectPermission()) return
        pendingConnectAddress = null
        hidDevice?.disconnect(host)
        connectedHost = null
        _connectionStatus.value = ConnectionStatus.Disconnected
        RemoteHidService.stop(context)
    }

    fun sendButton(button: RemoteButton) {
        if (connectedHost == null || hidDevice == null || !hasConnectPermission()) return

        actionQueue.trySend {
            val host = connectedHost ?: return@trySend
            val hid = hidDevice ?: return@trySend
            val isPc = _isPcProfile.value || connectedCategory == DeviceCategory.LAPTOP_PC
            when (val action = HidDescriptor.buttonToHidAction(button, isPcMode = isPc)) {
                is HidDescriptor.HidAction.Keyboard -> {
                    hid.sendReport(
                        host,
                        HidDescriptor.REPORT_ID_KEYBOARD,
                        HidDescriptor.keyboardReport(action.keyCode, action.modifier)
                    )
                    delay(35)
                    hid.sendReport(
                        host,
                        HidDescriptor.REPORT_ID_KEYBOARD,
                        HidDescriptor.KEYBOARD_RELEASE
                    )
                }

                is HidDescriptor.HidAction.Consumer -> {
                    hid.sendReport(
                        host,
                        HidDescriptor.REPORT_ID_CONSUMER,
                        HidDescriptor.consumerReport(action.usage)
                    )
                    delay(35)
                    hid.sendReport(
                        host,
                        HidDescriptor.REPORT_ID_CONSUMER,
                        HidDescriptor.CONSUMER_RELEASE
                    )
                }
            }
        }
    }

    fun sendText(text: String) {
        if (text.isEmpty() || connectedHost == null || hidDevice == null || !hasConnectPermission()) return

        actionQueue.trySend {
            val host = connectedHost ?: return@trySend
            val hid = hidDevice ?: return@trySend
            for (ch in text) {
                val (keyCode, modifier) = HidDescriptor.charToHidKey(ch) ?: continue
                hid.sendReport(
                    host,
                    HidDescriptor.REPORT_ID_KEYBOARD,
                    HidDescriptor.keyboardReport(keyCode, modifier)
                )
                delay(20)
                hid.sendReport(
                    host,
                    HidDescriptor.REPORT_ID_KEYBOARD,
                    HidDescriptor.KEYBOARD_RELEASE
                )
                delay(16)
            }
        }
    }

    fun launchPcApp(runCommand: String) {
        if (runCommand.isBlank() || connectedHost == null || hidDevice == null || !hasConnectPermission()) return

        actionQueue.trySend {
            val host = connectedHost ?: return@trySend
            val hid = hidDevice ?: return@trySend
            // Win + R opens Run dialog on Windows
            hid.sendReport(
                host,
                HidDescriptor.REPORT_ID_KEYBOARD,
                HidDescriptor.keyboardReport(0x15, HidDescriptor.MOD_LEFT_GUI) // 'r' + Win
            )
            delay(40)
            hid.sendReport(host, HidDescriptor.REPORT_ID_KEYBOARD, HidDescriptor.KEYBOARD_RELEASE)
            delay(220)
            for (ch in runCommand) {
                val (keyCode, modifier) = HidDescriptor.charToHidKey(ch) ?: continue
                hid.sendReport(host, HidDescriptor.REPORT_ID_KEYBOARD, HidDescriptor.keyboardReport(keyCode, modifier))
                delay(18)
                hid.sendReport(host, HidDescriptor.REPORT_ID_KEYBOARD, HidDescriptor.KEYBOARD_RELEASE)
                delay(14)
            }
            hid.sendReport(host, HidDescriptor.REPORT_ID_KEYBOARD, HidDescriptor.keyboardReport(0x28)) // Enter
            delay(35)
            hid.sendReport(host, HidDescriptor.REPORT_ID_KEYBOARD, HidDescriptor.KEYBOARD_RELEASE)
        }
    }

    fun sendMouseMove(dx: Int, dy: Int, wheel: Int = 0) {
        if (connectedHost == null || hidDevice == null) return
        if (dx != 0) pendingDx.addAndGet(dx)
        if (dy != 0) pendingDy.addAndGet(dy)
        if (wheel != 0) pendingWheel.addAndGet(wheel)
    }

    fun sendMouseClick(button: MouseButton) {
        if (connectedHost == null || hidDevice == null || !hasConnectPermission()) return

        val bit = HidDescriptor.mouseButtonBit(button)
        actionQueue.trySend {
            val host = connectedHost ?: return@trySend
            val hid = hidDevice ?: return@trySend
            hid.sendReport(
                host,
                HidDescriptor.REPORT_ID_MOUSE,
                HidDescriptor.mouseReport(buttonsMask = bit)
            )
            delay(35)
            hid.sendReport(
                host,
                HidDescriptor.REPORT_ID_MOUSE,
                HidDescriptor.mouseReport(buttonsMask = 0)
            )
        }
    }

    fun release() {
        stopDiscovery()
        mouseTickerJob?.cancel()
        queueConsumerJob?.cancel()
        actionQueue.close()
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(discoveryReceiver) }
            receiverRegistered = false
        }
        if (hasConnectPermission()) {
            hidDevice?.unregisterApp()
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        }
        hidDevice = null
        hidExecutor.shutdownNow()
        callbackExecutor.shutdownNow()
        RemoteHidService.stop(context)
    }

    private fun addOrUpdateDevice(device: BluetoothDevice) {
        val address = device.address ?: return
        val name = safeDeviceName(device)
        val bonded = device.bondState == BluetoothDevice.BOND_BONDED
        discoveredDevicesMap[address] = TvDevice.BluetoothTv(
            id = address,
            name = name,
            isBonded = bonded
        )
        emitSortedDevices()
    }

    private fun emitSortedDevices() {
        _devices.value = discoveredDevicesMap.values
            .sortedWith(
                compareByDescending<TvDevice.BluetoothTv> { it.isBonded }
                    .thenByDescending { it.name.contains("TV", ignoreCase = true) || it.name.contains("Mi", ignoreCase = true) }
                    .thenBy { it.name.lowercase() }
            )
    }

    private fun safeDeviceName(device: BluetoothDevice?): String {
        if (device == null) return "Unknown TV"
        return runCatching {
            if (hasConnectPermission()) device.name ?: device.address else device.address
        }.getOrDefault("Bluetooth TV")
    }
}
