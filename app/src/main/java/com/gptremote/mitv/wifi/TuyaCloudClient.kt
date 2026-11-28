package com.gptremote.mitv.wifi

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Tuya Cloud OpenAPI client for automatically fetching smart bulbs, device IDs, and 16-character
 * localKeys without manual API Explorer steps.
 */
class TuyaCloudClient(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences("tuya_cloud_prefs", Context.MODE_PRIVATE)
    private val bulbPrefs: SharedPreferences = appContext.getSharedPreferences("smart_bulb_prefs", Context.MODE_PRIVATE)

    companion object {
        const val PREF_CLIENT_ID = "tuya_client_id"
        const val PREF_CLIENT_SECRET = "tuya_client_secret"
        const val PREF_ENDPOINT = "tuya_endpoint"
        const val DEFAULT_CLIENT_ID = "hw98dtuj3hkfapvtappg"
        const val DEFAULT_ENDPOINT = "https://openapi.tuyain.com" // India Data Center
    }

    private val _statusText = MutableStateFlow(
        if (getClientSecret().isNotBlank()) "Configured • Ready to sync" else "Not configured"
    )
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncedDevices = MutableStateFlow<List<TuyaDeviceInfo>>(emptyList())
    val syncedDevices: StateFlow<List<TuyaDeviceInfo>> = _syncedDevices.asStateFlow()

    data class TuyaDeviceInfo(
        val id: String,
        val name: String,
        val localKey: String,
        val ip: String? = null,
        val category: String? = null
    )

    fun getClientId(): String = prefs.getString(PREF_CLIENT_ID, DEFAULT_CLIENT_ID).orEmpty().ifBlank { DEFAULT_CLIENT_ID }
    fun getClientSecret(): String = prefs.getString(PREF_CLIENT_SECRET, "").orEmpty()
    fun getEndpoint(): String = prefs.getString(PREF_ENDPOINT, DEFAULT_ENDPOINT).orEmpty().ifBlank { DEFAULT_ENDPOINT }

    fun saveConfig(clientId: String, clientSecret: String, endpoint: String = DEFAULT_ENDPOINT) {
        prefs.edit()
            .putString(PREF_CLIENT_ID, clientId.trim())
            .putString(PREF_CLIENT_SECRET, clientSecret.trim())
            .putString(PREF_ENDPOINT, endpoint.trim())
            .apply()
        _statusText.value = if (clientSecret.trim().isNotBlank()) "Configured • Ready to sync" else "Not configured"
    }

    suspend fun syncBulbs(): Result<List<TuyaDeviceInfo>> = withContext(Dispatchers.IO) {
        val clientId = getClientId().trim()
        val clientSecret = getClientSecret().trim()
        val baseUrl = getEndpoint().trim().removeSuffix("/")

        if (clientId.isBlank() || clientSecret.isBlank()) {
            val err = "Please enter your Tuya Client ID and Client Secret in Settings."
            _statusText.value = err
            return@withContext Result.failure(IllegalStateException(err))
        }

        _isSyncing.value = true
        _statusText.value = "Authenticating with Tuya Cloud..."

        runCatching {
            // 1. Get access token
            val (token, uid) = fetchAccessToken(baseUrl, clientId, clientSecret)
            Log.d("TuyaCloudClient", "Obtained access token, uid: $uid")
            _statusText.value = "Fetching devices and local keys..."

            // 2. Fetch device list
            val devices = mutableListOf<TuyaDeviceInfo>()

            // Try user devices endpoint first
            if (uid.isNotBlank()) {
                val userDevs = fetchUserDevices(baseUrl, clientId, clientSecret, token, uid)
                devices.addAll(userDevs)
            }

            // If empty, try project devices endpoint
            if (devices.isEmpty()) {
                val projDevs = fetchProjectDevices(baseUrl, clientId, clientSecret, token)
                devices.addAll(projDevs)
            }

            // Also check known bulb if missing localKey
            if (devices.none { it.id == "03050620dc4f22af831f" }) {
                runCatching {
                    fetchSingleDeviceDetails(baseUrl, clientId, clientSecret, token, "03050620dc4f22af831f")?.let {
                        devices.add(it)
                    }
                }
            }

            // 3. For any device where localKey wasn't returned in batch, query single device details
            val completeList = devices.map { dev ->
                if (dev.localKey.length == 16) dev
                else fetchSingleDeviceDetails(baseUrl, clientId, clientSecret, token, dev.id) ?: dev
            }

            // 4. Save credentials into bulb preferences
            completeList.forEach { dev ->
                if (dev.localKey.length == 16) {
                    bulbPrefs.edit().putString("key_${dev.id}", dev.localKey).apply()
                    dev.ip?.takeIf { it.isNotBlank() }?.let { ip ->
                        bulbPrefs.edit()
                            .putString("id_$ip", dev.id)
                            .putString("key_$ip", dev.localKey)
                            .apply()
                    }
                }
            }

            _syncedDevices.value = completeList
            val count = completeList.count { it.localKey.length == 16 }
            _statusText.value = "Synced $count smart device(s) with LocalKeys!"
            completeList
        }.also {
            _isSyncing.value = false
        }
    }

    private fun fetchAccessToken(baseUrl: String, clientId: String, clientSecret: String): Pair<String, String> {
        val t = System.currentTimeMillis().toString()
        val path = "/v1.0/token?grant_type=1"
        val stringToSign = clientId + t + "GET\n" + EMPTY_BODY_HASH + "\n\n" + path
        val sign = hmacSha256(stringToSign, clientSecret)

        val url = URL("$baseUrl$path")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("client_id", clientId)
            setRequestProperty("sign", sign)
            setRequestProperty("t", t)
            setRequestProperty("sign_method", "HMAC-SHA256")
        }

        val resp = readResponse(conn)
        val json = JSONObject(resp)
        if (!json.optBoolean("success", false)) {
            val code = json.optString("code")
            val msg = json.optString("msg")
            error("Tuya token error ($code): $msg")
        }
        val res = json.getJSONObject("result")
        val token = res.getString("access_token")
        val uid = res.optString("uid", "")
        return Pair(token, uid)
    }

    private fun fetchUserDevices(
        baseUrl: String,
        clientId: String,
        clientSecret: String,
        token: String,
        uid: String
    ): List<TuyaDeviceInfo> {
        return runCatching {
            val t = System.currentTimeMillis().toString()
            val path = "/v1.0/users/$uid/devices"
            val stringToSign = clientId + token + t + "GET\n" + EMPTY_BODY_HASH + "\n\n" + path
            val sign = hmacSha256(stringToSign, clientSecret)

            val conn = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("client_id", clientId)
                setRequestProperty("access_token", token)
                setRequestProperty("sign", sign)
                setRequestProperty("t", t)
                setRequestProperty("sign_method", "HMAC-SHA256")
            }
            val resp = readResponse(conn)
            parseDeviceArray(JSONObject(resp).optJSONArray("result"))
        }.getOrDefault(emptyList())
    }

    private fun fetchProjectDevices(
        baseUrl: String,
        clientId: String,
        clientSecret: String,
        token: String
    ): List<TuyaDeviceInfo> {
        return runCatching {
            val t = System.currentTimeMillis().toString()
            val path = "/v1.0/devices?page_no=1&page_size=50"
            val stringToSign = clientId + token + t + "GET\n" + EMPTY_BODY_HASH + "\n\n" + path
            val sign = hmacSha256(stringToSign, clientSecret)

            val conn = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("client_id", clientId)
                setRequestProperty("access_token", token)
                setRequestProperty("sign", sign)
                setRequestProperty("t", t)
                setRequestProperty("sign_method", "HMAC-SHA256")
            }
            val resp = readResponse(conn)
            val json = JSONObject(resp)
            val resObj = json.optJSONObject("result")
            val list = resObj?.optJSONArray("devices") ?: json.optJSONArray("result")
            parseDeviceArray(list)
        }.getOrDefault(emptyList())
    }

    private fun fetchSingleDeviceDetails(
        baseUrl: String,
        clientId: String,
        clientSecret: String,
        token: String,
        deviceId: String
    ): TuyaDeviceInfo? {
        return runCatching {
            val t = System.currentTimeMillis().toString()
            val path = "/v1.0/devices/$deviceId"
            val stringToSign = clientId + token + t + "GET\n" + EMPTY_BODY_HASH + "\n\n" + path
            val sign = hmacSha256(stringToSign, clientSecret)

            val conn = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("client_id", clientId)
                setRequestProperty("access_token", token)
                setRequestProperty("sign", sign)
                setRequestProperty("t", t)
                setRequestProperty("sign_method", "HMAC-SHA256")
            }
            val resp = readResponse(conn)
            val devObj = JSONObject(resp).optJSONObject("result") ?: return null
            TuyaDeviceInfo(
                id = devObj.getString("id"),
                name = devObj.optString("name", "Smart Light"),
                localKey = devObj.optString("local_key", ""),
                ip = devObj.optString("ip", "").takeIf { it.isNotBlank() },
                category = devObj.optString("category", "")
            )
        }.getOrNull()
    }

    private fun parseDeviceArray(array: JSONArray?): List<TuyaDeviceInfo> {
        if (array == null) return emptyList()
        val list = mutableListOf<TuyaDeviceInfo>()
        for (i in 0 until array.length()) {
            val dev = array.optJSONObject(i) ?: continue
            val id = dev.optString("id").ifBlank { dev.optString("devId") }
            if (id.isBlank()) continue
            val name = dev.optString("name", dev.optString("custom_name", "Smart Light"))
            val key = dev.optString("local_key", "")
            val ip = dev.optString("ip").takeIf { it.isNotBlank() }
            val cat = dev.optString("category")
            list.add(TuyaDeviceInfo(id = id, name = name, localKey = key, ip = ip, category = cat))
        }
        return list
    }

    private fun readResponse(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun hmacSha256(data: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hash = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02X".format(it) }
    }

    private val EMPTY_BODY_HASH: String by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest("".toByteArray(Charsets.UTF_8))
        hash.joinToString("") { "%02x".format(it) }
    }
}
