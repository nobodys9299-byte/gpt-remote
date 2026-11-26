package com.gptremote.mitv.data

import android.content.Context
import com.gptremote.mitv.model.DeviceCategory
import com.gptremote.mitv.model.SavedDevice
import com.gptremote.mitv.model.TransportMode
import com.gptremote.mitv.model.TvDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists user-customized electronic devices (nicknames, categories, icons, transports).
 */
class SavedDeviceStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _devices = MutableStateFlow(load())
    val devices: StateFlow<List<SavedDevice>> = _devices.asStateFlow()

    fun saveOrUpdateFromConnection(device: TvDevice) {
        val current = _devices.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.id == device.id }
        val now = System.currentTimeMillis()

        val updated = if (existingIdx >= 0) {
            current[existingIdx].copy(lastConnectedMs = now)
        } else {
            val (addressOrHost, port) = when (device) {
                is TvDevice.BluetoothTv -> device.id to 0
                is TvDevice.WifiTv -> device.host to device.port
                is TvDevice.WifiPc -> device.host to device.port
            }
            SavedDevice(
                id = device.id,
                nickname = device.name,
                originalName = device.name,
                addressOrHost = addressOrHost,
                port = port,
                transport = device.transport,
                category = device.category,
                iconKey = if (device.category == DeviceCategory.LAPTOP_PC) "laptop" else "tv",
                lastConnectedMs = now
            )
        }

        if (existingIdx >= 0) {
            current[existingIdx] = updated
        } else {
            current.add(0, updated)
        }
        persist(current)
    }

    fun upsertCustomDevice(device: SavedDevice) {
        val current = _devices.value.toMutableList()
        val idx = current.indexOfFirst { it.id == device.id }
        if (idx >= 0) {
            current[idx] = device
        } else {
            current.add(0, device)
        }
        persist(current)
    }

    fun customizeDevice(
        id: String,
        nickname: String,
        category: DeviceCategory,
        iconKey: String
    ) {
        val current = _devices.value.map { item ->
            if (item.id == id) {
                item.copy(
                    nickname = nickname.trim().ifEmpty { item.originalName },
                    category = category,
                    iconKey = iconKey
                )
            } else {
                item
            }
        }
        persist(current)
    }

    fun removeDevice(id: String) {
        persist(_devices.value.filterNot { it.id == id })
    }

    private fun persist(list: List<SavedDevice>) {
        val sorted = list.sortedByDescending { it.lastConnectedMs }
        val arr = JSONArray()
        sorted.forEach { d ->
            arr.put(
                JSONObject()
                    .put("id", d.id)
                    .put("nickname", d.nickname)
                    .put("originalName", d.originalName)
                    .put("addressOrHost", d.addressOrHost)
                    .put("port", d.port)
                    .put("transport", d.transport.name)
                    .put("category", d.category.name)
                    .put("iconKey", d.iconKey)
                    .put("lastConnectedMs", d.lastConnectedMs)
            )
        }
        prefs.edit().putString(KEY_SAVED_LIST, arr.toString()).apply()
        _devices.value = sorted
    }

    private fun load(): List<SavedDevice> {
        val raw = prefs.getString(KEY_SAVED_LIST, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val transport = TransportMode.valueOf(obj.getString("transport"))
                    val category = DeviceCategory.valueOf(obj.getString("category"))
                    add(
                        SavedDevice(
                            id = obj.getString("id"),
                            nickname = obj.getString("nickname"),
                            originalName = obj.optString("originalName", obj.getString("nickname")),
                            addressOrHost = obj.getString("addressOrHost"),
                            port = obj.optInt("port", 0),
                            transport = transport,
                            category = category,
                            iconKey = obj.optString("iconKey", if (category == DeviceCategory.LAPTOP_PC) "laptop" else "tv"),
                            lastConnectedMs = obj.optLong("lastConnectedMs", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val PREFS_NAME = "saved_electronics_store"
        private const val KEY_SAVED_LIST = "devices_json"
    }
}
