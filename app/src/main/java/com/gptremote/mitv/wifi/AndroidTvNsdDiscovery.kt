package com.gptremote.mitv.wifi

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.gptremote.mitv.model.TvDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Discovers Android TV / Mi TV devices advertising `_androidtvremote2._tcp.` on local Wi-Fi.
 */
class AndroidTvNsdDiscovery(
    context: Context,
    private val certificateManager: TvCertificateManager
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val _devices = MutableStateFlow<List<TvDevice.WifiTv>>(emptyList())
    val devices: StateFlow<List<TvDevice.WifiTv>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val discovered = LinkedHashMap<String, TvDevice.WifiTv>()
    private var activeListener: NsdManager.DiscoveryListener? = null

    fun startDiscovery() {
        val manager = nsdManager ?: return
        stopDiscovery()
        discovered.clear()
        _devices.value = emptyList()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                _isScanning.value = true
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType?.contains("_androidtvremote2") == true) {
                    resolveService(manager, serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val name = serviceInfo.serviceName ?: return
                discovered.entries.removeIf { it.value.name == name }
                _devices.value = discovered.values.toList()
            }

            override fun onDiscoveryStopped(serviceType: String) {
                _isScanning.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                _isScanning.value = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                _isScanning.value = false
            }
        }

        activeListener = listener
        runCatching {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            _isScanning.value = false
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveService(manager: NsdManager, serviceInfo: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            manager.registerServiceInfoCallback(
                serviceInfo,
                { it.run() },
                object : NsdManager.ServiceInfoCallback {
                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit
                    override fun onServiceUpdated(updated: NsdServiceInfo) {
                        val host = updated.hostAddresses.firstOrNull()?.hostAddress ?: return
                        val port = if (updated.port > 0) updated.port else 6466
                        addDiscoveredTv(updated.serviceName ?: "Android TV", host, port)
                        runCatching { manager.unregisterServiceInfoCallback(this) }
                    }
                    override fun onServiceLost() = Unit
                    override fun onServiceInfoCallbackUnregistered() = Unit
                }
            )
        } else {
            manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    val host = resolved.host?.hostAddress ?: return
                    val port = if (resolved.port > 0) resolved.port else 6466
                    addDiscoveredTv(resolved.serviceName ?: "Android TV", host, port)
                }
            })
        }
    }

    private fun addDiscoveredTv(name: String, host: String, port: Int) {
        val id = "$host:$port"
        discovered[id] = TvDevice.WifiTv(
            id = id,
            name = name,
            host = host,
            port = port,
            pairingPort = port + 1,
            isPaired = certificateManager.isHostPaired(host)
        )
        _devices.value = discovered.values.toList()
    }

    fun stopDiscovery() {
        val listener = activeListener ?: return
        activeListener = null
        runCatching { nsdManager?.stopServiceDiscovery(listener) }
        _isScanning.value = false
    }

    companion object {
        private const val SERVICE_TYPE = "_androidtvremote2._tcp."
    }
}
