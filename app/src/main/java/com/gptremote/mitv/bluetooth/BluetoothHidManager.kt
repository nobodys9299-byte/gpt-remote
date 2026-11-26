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
import com.gptremote.mitv.model.MouseButton
import com.gptremote.mitv.model.RemoteButton
import com.gptremote.mitv.model.TransportMode
import com.gptremote.mitv.model.TvDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Manages Android's BluetoothHidDevice profile so this phone emulates a Bluetooth HID
 * combo remote (Keyboard + Consumer Control + Mouse/Touchpad) for Mi Android TV.
 */
@SuppressLint("MissingPermission")
class BluetoothHidManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHost: BluetoothDevice? = null
    private var receiverRegistered = false

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
            } else if (pluggedDevice != null) {
                // Host already connected
                connectedHost = pluggedDevice
                val name = safeDeviceName(pluggedDevice)
                _connectionStatus.value = ConnectionStatus.Connected(
                    TvDevice.BluetoothTv(
                        id = pluggedDevice.address,
                        name = name,
                        isBonded = true
                    )
                )
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedHost = device
                    val address = device?.address ?: "unknown"
                    val name = safeDeviceName(device)
                    _connectionStatus.value = ConnectionStatus.Connected(
                        TvDevice.BluetoothTv(id = address, name = name, isBonded = true)
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
            // Acknowledge LED/output reports cleanly
            hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
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

    private fun registerHidApp() {
        val hid = hidDevice ?: return
        if (!hasConnectPermission()) return

        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "Mi TV Remote",
            "Bluetooth HID Remote Control for Android TV",
            "GPT Remote",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HidDescriptor.DESCRIPTOR
        )

        val outQos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800,
            9,
            0,
            11250,
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

    fun connect(address: String) {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled || !hasConnectPermission()) {
            _connectionStatus.value = ConnectionStatus.Error(
                "Bluetooth permission or adapter unavailable.",
                TransportMode.BLUETOOTH_HID
            )
            return
        }

        stopDiscovery()
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
            device.createBond()
        }

        val initiated = hid.connect(device)
        if (!initiated) {
            _connectionStatus.value = ConnectionStatus.Error(
                "Connection not initiated. On Mi TV, open Settings -> Remotes & Accessories -> Add accessory and tap 'Make Discoverable'.",
                TransportMode.BLUETOOTH_HID
            )
        }
    }

    fun disconnect() {
        val host = connectedHost ?: return
        if (!hasConnectPermission()) return
        hidDevice?.disconnect(host)
        connectedHost = null
        _connectionStatus.value = ConnectionStatus.Disconnected
        RemoteHidService.stop(context)
    }

    fun sendButton(button: RemoteButton) {
        val host = connectedHost ?: return
        val hid = hidDevice ?: return
        if (!hasConnectPermission()) return

        scope.launch {
            when (val action = HidDescriptor.buttonToHidAction(button)) {
                is HidDescriptor.HidAction.Keyboard -> {
                    hid.sendReport(
                        host,
                        HidDescriptor.REPORT_ID_KEYBOARD,
                        HidDescriptor.keyboardReport(action.keyCode, action.modifier)
                    )
                    delay(45)
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
                    delay(45)
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
        val host = connectedHost ?: return
        val hid = hidDevice ?: return
        if (!hasConnectPermission()) return

        scope.launch {
            for (ch in text) {
                val (keyCode, modifier) = HidDescriptor.charToHidKey(ch) ?: continue
                hid.sendReport(
                    host,
                    HidDescriptor.REPORT_ID_KEYBOARD,
                    HidDescriptor.keyboardReport(keyCode, modifier)
                )
                delay(25)
                hid.sendReport(
                    host,
                    HidDescriptor.REPORT_ID_KEYBOARD,
                    HidDescriptor.KEYBOARD_RELEASE
                )
                delay(20)
            }
        }
    }

    fun sendMouseMove(dx: Int, dy: Int, wheel: Int = 0) {
        val host = connectedHost ?: return
        val hid = hidDevice ?: return
        if (!hasConnectPermission()) return
        if (dx == 0 && dy == 0 && wheel == 0) return

        hid.sendReport(
            host,
            HidDescriptor.REPORT_ID_MOUSE,
            HidDescriptor.mouseReport(buttonsMask = 0, dx = dx, dy = dy, wheel = wheel)
        )
    }

    fun sendMouseClick(button: MouseButton) {
        val host = connectedHost ?: return
        val hid = hidDevice ?: return
        if (!hasConnectPermission()) return

        val bit = HidDescriptor.mouseButtonBit(button)
        scope.launch {
            hid.sendReport(
                host,
                HidDescriptor.REPORT_ID_MOUSE,
                HidDescriptor.mouseReport(buttonsMask = bit)
            )
            delay(40)
            hid.sendReport(
                host,
                HidDescriptor.REPORT_ID_MOUSE,
                HidDescriptor.mouseReport(buttonsMask = 0)
            )
        }
    }

    fun release() {
        stopDiscovery()
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(discoveryReceiver) }
            receiverRegistered = false
        }
        if (hasConnectPermission()) {
            hidDevice?.unregisterApp()
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        }
        hidDevice = null
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
