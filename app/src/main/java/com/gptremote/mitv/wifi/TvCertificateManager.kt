package com.gptremote.mitv.wifi

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/**
 * Manages a persistent RSA-2048 client certificate in AndroidKeyStore and computes
 * Polo/Android TV Remote v2 pairing hashes.
 */
class TvCertificateManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun ensureClientCredentials(): Pair<PrivateKey, X509Certificate> {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? PrivateKey
        val existingCert = keyStore.getCertificate(KEY_ALIAS) as? X509Certificate
        if (existingKey != null && existingCert != null) {
            return existingKey to existingCert
        }

        val notBefore = Date(System.currentTimeMillis() - 86_400_000L)
        val notAfter = Date(System.currentTimeMillis() + 3650L * 86_400_000L)

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setKeySize(2048)
            .setDigests(
                KeyProperties.DIGEST_NONE,
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA384,
                KeyProperties.DIGEST_SHA512
            )
            .setSignaturePaddings(
                KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,
                KeyProperties.SIGNATURE_PADDING_RSA_PSS
            )
            .setCertificateSubject(X500Principal("CN=MiTvRemote"))
            .setCertificateSerialNumber(BigInteger.valueOf(System.currentTimeMillis()))
            .setCertificateNotBefore(notBefore)
            .setCertificateNotAfter(notAfter)
            .build()

        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
        kpg.initialize(spec)
        kpg.generateKeyPair()

        val key = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
        val cert = keyStore.getCertificate(KEY_ALIAS) as X509Certificate
        return key to cert
    }

    fun createSocketFactory(onServerCertificate: ((X509Certificate) -> Unit)? = null): SSLSocketFactory {
        val (privateKey, clientCert) = ensureClientCredentials()

        val keyManager = object : X509ExtendedKeyManager() {
            override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String> =
                arrayOf(KEY_ALIAS)

            override fun chooseClientAlias(
                keyType: Array<out String>?,
                issuers: Array<out Principal>?,
                socket: Socket?
            ): String = KEY_ALIAS

            override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null

            override fun chooseServerAlias(
                keyType: String?,
                issuers: Array<out Principal>?,
                socket: Socket?
            ): String? = null

            override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
                if (alias == KEY_ALIAS) arrayOf(clientCert) else null

            override fun getPrivateKey(alias: String?): PrivateKey? =
                if (alias == KEY_ALIAS) privateKey else null
        }

        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val leaf = chain?.firstOrNull()
                if (leaf != null) {
                    onServerCertificate?.invoke(leaf)
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(arrayOf(keyManager), arrayOf(trustManager), SecureRandom())
        return sslContext.socketFactory
    }

    fun savePairedHost(host: String, serverCert: X509Certificate?) {
        val fingerprint = serverCert?.let { sha256Hex(it.encoded) } ?: "paired"
        prefs.edit().putString("host_$host", fingerprint).apply()
    }

    fun isHostPaired(host: String): Boolean = prefs.contains("host_$host")

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "mitv_remote_client_cert_v1"
        private const val PREFS_NAME = "wifi_tv_paired_hosts"

        /**
         * Strips the leading sign 0x00 byte that BigInteger.toByteArray() emits when MSB >= 0x80.
         */
        fun BigInteger.toUnsignedBigEndianBytes(): ByteArray {
            val raw = toByteArray()
            return if (raw.size > 1 && raw[0] == 0.toByte()) {
                raw.copyOfRange(1, raw.size)
            } else {
                raw
            }
        }

        /**
         * Computes the Android TV Remote v2 / Polo pairing secret:
         * SHA-256(clientModulus || clientExponent || serverModulus || serverExponent || nonceBytes)
         * and validates that digest[0] matches the first byte of the 6-char hex PIN.
         */
        fun computeAndVerifyPairingSecret(
            clientCert: X509Certificate,
            serverCert: X509Certificate,
            pinHex: String
        ): Result<ByteArray> {
            val cleanPin = pinHex.trim().uppercase()
            if (cleanPin.length != 6 || !cleanPin.all { it in '0'..'9' || it in 'A'..'F' }) {
                return Result.failure(IllegalArgumentException("Enter the 6-character hex PIN shown on TV."))
            }

            val clientRsa = clientCert.publicKey as? RSAPublicKey
                ?: return Result.failure(IllegalStateException("Client certificate is not RSA"))
            val serverRsa = serverCert.publicKey as? RSAPublicKey
                ?: return Result.failure(IllegalStateException("TV certificate is not RSA"))

            val alphaExpected = hexToBytes(cleanPin.substring(0, 2))[0]
            val nonceBytes = hexToBytes(cleanPin.substring(2, 6))

            val digest = computePairingDigest(
                clientMod = clientRsa.modulus.toUnsignedBigEndianBytes(),
                clientExp = clientRsa.publicExponent.toUnsignedBigEndianBytes(),
                serverMod = serverRsa.modulus.toUnsignedBigEndianBytes(),
                serverExp = serverRsa.publicExponent.toUnsignedBigEndianBytes(),
                nonce = nonceBytes
            )

            if (digest[0] != alphaExpected) {
                return Result.failure(IllegalArgumentException("PIN mismatch. Check the 6-character code on your TV."))
            }
            return Result.success(digest)
        }

        fun computePairingDigest(
            clientMod: ByteArray,
            clientExp: ByteArray,
            serverMod: ByteArray,
            serverExp: ByteArray,
            nonce: ByteArray
        ): ByteArray {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(clientMod)
            md.update(clientExp)
            md.update(serverMod)
            md.update(serverExp)
            md.update(nonce)
            return md.digest()
        }

        fun hexToBytes(hex: String): ByteArray {
            require(hex.length % 2 == 0) { "Hex string must have even length" }
            return ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }

        private fun sha256Hex(bytes: ByteArray): String {
            return MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }
        }
    }
}
