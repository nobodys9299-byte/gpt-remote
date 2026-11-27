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
    private var explicitTargetAddress: String? = null
    private var allowIncomingUntilMs: Long = 0L
    private var pendingReRegister = false
    private var connectedCategory: DeviceCategory = DeviceCategory.ANDROID_TV
    private var receiverRegistered = false
    private val userDisconnectedAddresses = mutableSetOf<String>()

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

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null && connectedHost?.address == device.address) {
                        connectedHost = null
                        explicitTargetAddress = null
                        _connectionStatus.value = ConnectionStatus.Disconnected
                        RemoteHidService.stop(context)
                    }
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
                if (pendingReRegister) {
                    pendingReRegister = false
                    registerHidApp()
                }
                return
            }

            if (pluggedDevice != null) {
                val now = System.currentTimeMillis()
                val isUserAllowed = explicitTargetAddress == pluggedDevice.address || now <= allowIncomingUntilMs
                val isLiveConnected = hasConnectPermission() &&
                    runCatching { hidDevice?.getConnectionState(pluggedDevice) == BluetoothProfile.STATE_CONNECTED }
                        .getOrDefault(false)

                if (pluggedDevice.address in userDisconnectedAddresses || !isUserAllowed || !isLiveConnected) {
                    // Reject cached / stale bonded HID plug reported by Android when TV is off or unrequested
                    if (hasConnectPermission()) {
                        runCatching { hidDevice?.disconnect(pluggedDevice) }
                    }
                    return
                }
                connectedHost = pluggedDevice
                pendingConnectAddress = null
                val name = safeDeviceName(pluggedDevice)
                _connectionStatus.value = ConnectionStatus.Connected(
                    TvDevice.BluetoothTv(
                        id = pluggedDevice.address,
                        name = name,
                        isBonded = true,
                        category = if (_isPcProfile.value) DeviceCategory.LAPTOP_PC else TvDevice.inferCategory(name),
                        majorClass = pluggedDevice.bluetoothClass?.majorDeviceClass ?: 0,
                        deviceClass = pluggedDevice.bluetoothClass?.deviceClass ?: 0
                    )
                )
                RemoteHidService.start(context, name)
            } else {
                val targetAddr = pendingConnectAddress
                val adapter = bluetoothAdapter
                if (targetAddr != null && adapter != null && hasConnectPermission()) {
                    val targetDev = runCatching { adapter.getRemoteDevice(targetAddr) }.getOrNull()
                    if (targetDev != null && targetDev.bondState == BluetoothDevice.BOND_BONDED) {
                        pendingConnectAddress = null
                        hidDevice?.connect(targetDev)
                    }
                }
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    val addr = device?.address
                    val now = System.currentTimeMillis()
                    val isUserAllowed = addr != null &&
                        (explicitTargetAddress == addr || now <= allowIncomingUntilMs)
                    if (addr == null || addr in userDisconnectedAddresses || !isUserAllowed) {
                        if (device != null && hasConnectPermission()) {
                            runCatching { hidDevice?.disconnect(device) }
                        }
                        return
                    }
                    connectedHost = device
                    pendingConnectAddress = null
                    val name = safeDeviceName(device)
                    val resolvedCategory = if (_isPcProfile.value) {
                        DeviceCategory.LAPTOP_PC
                    } else if (explicitTargetAddress == null) {
                        TvDevice.inferCategory(name)
                    } else {
                        connectedCategory
                    }
                    _connectionStatus.value = ConnectionStatus.Connected(
                        TvDevice.BluetoothTv(
                            id = addr,
                            name = name,
                            isBonded = true,
                            category = resolvedCategory
                        )
                    )
                    RemoteHidService.start(context, name)
                }

                BluetoothProfile.STATE_CONNECTING -> {
                    val addr = device?.address
                    val now = System.currentTimeMillis()
                    if (addr != null && (explicitTargetAddress == addr || now <= allowIncomingUntilMs)) {
                        val name = safeDeviceName(device)
                        _connectionStatus.value = ConnectionStatus.Connecting(
                            deviceName = name,
                            transport = TransportMode.BLUETOOTH_HID
                        )
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectedHost?.address == device?.address || connectedHost == null) {
                        connectedHost = null
                        if (_connectionStatus.value is ConnectionStatus.Connected ||
                            _connectionStatus.value is ConnectionStatus.Connecting
                        ) {
                            _connectionStatus.value = ConnectionStatus.Disconnected
                        }
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
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
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
            if (_isAppRegistered.value) {
                pendingReRegister = true
                runCatching { hid.unregisterApp() }
            } else {
                registerHidApp()
            }
        }
    }

    private fun registerHidApp(retryCount: Int = 0) {
        val hid = hidDevice ?: return
        if (!hasConnectPermission()) return

        val pcMode = _isPcProfile.value
        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            if (pcMode) "GPT Remote (PC)" else "GPT Remote (TV)",
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
        if (!registered && retryCount < 1) {
            scope.launch {
                delay(400)
                if (!_isAppRegistered.value) {
                    registerHidApp(retryCount + 1)
                }
            }
        }
    }

    fun refreshPairedDevices() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled || !hasConnectPermission()) return

        discoveredDevicesMap.clear()
        adapter.bondedDevices.orEmpty().forEach { addOrUpdateDevice(it) }
        emitSortedDevices()
    }

    fun allowIncomingPairing(windowMs: Long = 180_000L) {
        allowIncomingUntilMs = System.currentTimeMillis() + windowMs
        ensureInitialized()
    }

    fun startDiscovery() {
        allowIncomingUntilMs = System.currentTimeMillis() + 180_000L
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
        userDisconnectedAddresses.remove(address)
        explicitTargetAddress = address
        allowIncomingUntilMs = System.currentTimeMillis() + 180_000L
        val device = adapter.getRemoteDevice(address)
        val name = safeDeviceName(device)
        val previousHost = connectedHost
        if (previousHost != null && previousHost.address != address) {
            hidDevice?.disconnect(previousHost)
            connectedHost = null
        }

        connectedCategory = category
        val shouldBePc = category == DeviceCategory.LAPTOP_PC
        if (_isPcProfile.value != shouldBePc) {
            pendingConnectAddress = address
            _connectionStatus.value = ConnectionStatus.Connecting(name, TransportMode.BLUETOOTH_HID)
            setPcProfile(shouldBePc)
            return
        }
        val hid = hidDevice
        if (hid == null || !_isAppRegistered.value) {
            pendingConnectAddress = address
            _connectionStatus.value = ConnectionStatus.Connecting(name, TransportMode.BLUETOOTH_HID)
            ensureInitialized()
            return
        }

        _connectionStatus.value = ConnectionStatus.Connecting(name, TransportMode.BLUETOOTH_HID)

        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            pendingConnectAddress = address
            device.createBond()
            return
        }

        val initiated = hid.connect(device)
        if (!initiated) {
            val hint = if (shouldBePc) {
                "Pair your phone from Windows/Mac Bluetooth settings, or tap Discoverable."
            } else {
                "On TV, open Settings -> Remotes & Accessories -> Add accessory and tap Discoverable."
            }
            _connectionStatus.value = ConnectionStatus.Error(
                "Bluetooth connection not initiated. $hint",
                TransportMode.BLUETOOTH_HID
            )
        }
    }

    fun disconnect() {
        pendingConnectAddress = null
        explicitTargetAddress = null
        allowIncomingUntilMs = 0L
        if (hasConnectPermission()) {
            connectedHost?.let { host ->
                userDisconnectedAddresses.add(host.address)
                hidDevice?.disconnect(host)
            }
            hidDevice?.connectedDevices.orEmpty().forEach { dev ->
                userDisconnectedAddresses.add(dev.address)
                hidDevice?.disconnect(dev)
            }
        }
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
                    delay(18)
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
                    delay(18)
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
                delay(12)
                hid.sendReport(
                    host,
                    HidDescriptor.REPORT_ID_KEYBOARD,
                    HidDescriptor.KEYBOARD_RELEASE
                )
                delay(10)
            }
        }
    }

    fun launchTvAppViaSearch(appName: String) {
        if (appName.isBlank() || connectedHost == null || hidDevice == null || !hasConnectPermission()) return

        actionQueue.trySend {
            val host = connectedHost ?: return@trySend
            val hid = hidDevice ?: return@trySend
            // Open TV Search overlay via Consumer AC Search (0x0221)
            hid.sendReport(host, HidDescriptor.REPORT_ID_CONSUMER, HidDescriptor.consumerReport(0x0221))
            delay(22)
            hid.sendReport(host, HidDescriptor.REPORT_ID_CONSUMER, HidDescriptor.CONSUMER_RELEASE)
            delay(280)

            for (ch in appName) {
                val (keyCode, modifier) = HidDescriptor.charToHidKey(ch) ?: continue
                hid.sendReport(host, HidDescriptor.REPORT_ID_KEYBOARD, HidDescriptor.keyboardReport(keyCode, modifier))
                delay(12)
                hid.sendReport(host, HidDescriptor.REPORT_ID_KEYBOARD, HidDescriptor.KEYBOARD_RELEASE)
                delay(10)
            }
            delay(140)
            hid.sendReport(host, HidDescriptor.REPORT_ID_KEYBOARD, HidDescriptor.keyboardReport(0x28)) // Enter
            delay(22)
            hid.sendReport(host, HidDescriptor.REPORT_ID_KEYBOARD, HidDescriptor.KEYBOARD_RELEASE)
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

    fun closePcApp() {
        if (connectedHost == null || hidDevice == null || !hasConnectPermission()) return
        actionQueue.trySend {
            val host = connectedHost ?: return@trySend
            val hid = hidDevice ?: return@trySend
            hid.sendReport(
                host,
                HidDescriptor.REPORT_ID_KEYBOARD,
                HidDescriptor.keyboardReport(0x3D, HidDescriptor.MOD_LEFT_ALT) // Alt + F4
            )
            delay(40)
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
        val majorCls = runCatching { device.bluetoothClass?.majorDeviceClass ?: 0 }.getOrDefault(0)
        val devCls = runCatching { device.bluetoothClass?.deviceClass ?: 0 }.getOrDefault(0)
        discoveredDevicesMap[address] = TvDevice.BluetoothTv(
            id = address,
            name = name,
            isBonded = bonded,
            majorClass = majorCls,
            deviceClass = devCls
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
