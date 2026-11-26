package com.gptremote.mitv.wifi

import android.os.Build
import android.view.KeyEvent
import com.gptremote.mitv.model.ConnectionStatus
import com.gptremote.mitv.model.RemoteButton
import com.gptremote.mitv.model.TransportMode
import com.gptremote.mitv.model.TvDevice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSocket

/**
 * Client for Android TV Remote Protocol v2 over local Wi-Fi (Pairing on port 6467, Remote on 6466).
 */
class WifiTvRemoteClient(
    private val certificateManager: TvCertificateManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private var activeSocket: SSLSocket? = null
    private var outputStream: OutputStream? = null
    private var readerJob: Job? = null
    private var activeDevice: TvDevice.WifiTv? = null

    private var pendingPinDeferred: CompletableDeferred<String>? = null
    private var pairingJob: Job? = null

    private var imeCounter: Int = 0
    private var fieldCounter: Int = 0

    fun connect(device: TvDevice.WifiTv, forcePair: Boolean = false) {
        disconnect()
        activeDevice = device

        if (!forcePair && certificateManager.isHostPaired(device.host)) {
            connectControlSession(device)
        } else {
            startPairingSession(device)
        }
    }

    fun submitPairingPin(pin: String) {
        pendingPinDeferred?.complete(pin)
    }

    private fun startPairingSession(device: TvDevice.WifiTv) {
        pairingJob = scope.launch {
            _connectionStatus.value = ConnectionStatus.Connecting(
                deviceName = device.name,
                transport = TransportMode.WIFI_ANDROID_TV
            )

            var pairingSocket: SSLSocket? = null
            try {
                var serverCert: X509Certificate? = null
                val factory = certificateManager.createSocketFactory { cert -> serverCert = cert }
                pairingSocket = factory.createSocket() as SSLSocket
                pairingSocket.connect(InetSocketAddress(device.host, device.pairingPort), 7000)
                pairingSocket.soTimeout = 30_000
                pairingSocket.startHandshake()

                val capturedServerCert = serverCert
                    ?: (pairingSocket.session.peerCertificates.firstOrNull() as? X509Certificate)
                    ?: throw IllegalStateException("TV did not present a TLS certificate")

                val (_, clientCert) = certificateManager.ensureClientCredentials()
                val input = pairingSocket.inputStream
                val output = pairingSocket.outputStream

                // 1. PairingRequest
                ProtoWire.writeFrame(output, buildPairingRequest())
                readPairingAckOrThrow(input, expectedField = 11, stage = "pairing request")

                // 2. PairingOption
                ProtoWire.writeFrame(output, buildPairingOption())
                readPairingAckOrThrow(input, expectedField = 20, stage = "pairing option")

                // 3. PairingConfiguration
                ProtoWire.writeFrame(output, buildPairingConfiguration())
                readPairingAckOrThrow(input, expectedField = 31, stage = "pairing configuration")

                // 4. Wait for user to enter the 6-char hex PIN displayed on TV
                while (isActive) {
                    val deferred = CompletableDeferred<String>()
                    pendingPinDeferred = deferred
                    _connectionStatus.value = ConnectionStatus.AwaitingWifiPin(device)

                    val pin = deferred.await()
                    val secretResult = TvCertificateManager.computeAndVerifyPairingSecret(
                        clientCert = clientCert,
                        serverCert = capturedServerCert,
                        pinHex = pin
                    )

                    if (secretResult.isFailure) {
                        _connectionStatus.value = ConnectionStatus.AwaitingWifiPin(
                            device = device,
                            message = secretResult.exceptionOrNull()?.message
                        )
                        continue
                    }

                    ProtoWire.writeFrame(output, buildPairingSecret(secretResult.getOrThrow()))
                    readPairingAckOrThrow(input, expectedField = 41, stage = "pairing secret")
                    break
                }

                certificateManager.savePairedHost(device.host, capturedServerCert)
                runCatching { pairingSocket.close() }
                connectControlSession(device.copy(isPaired = true))
            } catch (e: Exception) {
                runCatching { pairingSocket?.close() }
                _connectionStatus.value = ConnectionStatus.Error(
                    message = e.message ?: "Wi-Fi pairing failed",
                    transport = TransportMode.WIFI_ANDROID_TV
                )
            } finally {
                pendingPinDeferred = null
            }
        }
    }

    private fun connectControlSession(device: TvDevice.WifiTv) {
        readerJob = scope.launch {
            _connectionStatus.value = ConnectionStatus.Connecting(
                deviceName = device.name,
                transport = TransportMode.WIFI_ANDROID_TV
            )

            try {
                val factory = certificateManager.createSocketFactory()
                val socket = factory.createSocket() as SSLSocket
                socket.connect(InetSocketAddress(device.host, device.port), 7000)
                socket.soTimeout = 0 // Blocking reads for incoming pings & IME updates
                socket.startHandshake()

                activeSocket = socket
                val input = socket.inputStream
                val out = socket.outputStream
                outputStream = out

                // Handshake: TV sends RemoteConfigure -> we reply RemoteConfigure
                // TV sends RemoteSetActive -> we reply RemoteSetActive
                var handshakeDone = false
                while (isActive && !socket.isClosed) {
                    val frame = ProtoWire.readFrame(input)
                    val fields = ProtoWire.parseFields(frame)

                    for (field in fields) {
                        when (field.number) {
                            1 -> { // remote_configure
                                sendFrame(buildRemoteConfigure())
                            }
                            2 -> { // remote_set_active
                                sendFrame(buildRemoteSetActive())
                                if (!handshakeDone) {
                                    handshakeDone = true
                                    _connectionStatus.value = ConnectionStatus.Connected(device.copy(isPaired = true))
                                }
                            }
                            8 -> { // remote_ping_request
                                val pingFields = ProtoWire.parseFields(field.bytes)
                                val val1 = pingFields.firstOrNull { it.number == 1 }?.varint?.toInt() ?: 0
                                sendFrame(buildPingResponse(val1))
                            }
                            20 -> { // remote_ime_key_inject / counter sync
                                val nested = ProtoWire.parseFields(field.bytes)
                                nested.firstOrNull { it.number == 1 }?.let { imeCounter = it.varint.toInt() }
                                nested.firstOrNull { it.number == 2 }?.let { fieldCounter = it.varint.toInt() }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // If TLS fails because TV forgot pairing, offer re-pairing
                _connectionStatus.value = ConnectionStatus.Error(
                    message = "Wi-Fi connection closed: ${e.message ?: "check TV network or re-pair"}",
                    transport = TransportMode.WIFI_ANDROID_TV
                )
            }
        }
    }

    fun sendButton(button: RemoteButton) {
        val keyCode = buttonToAndroidKeyCode(button) ?: return
        scope.launch {
            runCatching {
                sendFrame(buildKeyInject(keyCode, direction = DIRECTION_SHORT))
            }
        }
    }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        scope.launch {
            runCatching {
                sendFrame(buildImeBatchEdit(imeCounter, fieldCounter, text))
            }
        }
    }

    fun disconnect() {
        pairingJob?.cancel()
        pairingJob = null
        pendingPinDeferred?.cancel()
        pendingPinDeferred = null
        readerJob?.cancel()
        readerJob = null
        runCatching { activeSocket?.close() }
        activeSocket = null
        outputStream = null
        activeDevice = null
        _connectionStatus.value = ConnectionStatus.Disconnected
    }

    private suspend fun sendFrame(payload: ByteArray) {
        val out = outputStream ?: return
        writeMutex.withLock {
            ProtoWire.writeFrame(out, payload)
        }
    }

    private fun readPairingAckOrThrow(input: InputStream, expectedField: Int, stage: String) {
        val frame = ProtoWire.readFrame(input)
        val fields = ProtoWire.parseFields(frame)
        val status = fields.firstOrNull { it.number == 2 }?.varint?.toInt() ?: 0
        if (status != STATUS_OK) {
            throw IllegalStateException("TV rejected $stage (status=$status)")
        }
        val hasExpected = fields.any { it.number == expectedField }
        if (!hasExpected) {
            throw IllegalStateException("Unexpected TV response during $stage")
        }
    }

    companion object {
        private const val PROTOCOL_VERSION = 2
        private const val STATUS_OK = 200
        private const val ENCODING_HEXADECIMAL = 3
        private const val ROLE_INPUT = 1
        private const val DIRECTION_SHORT = 3

        fun buildPairingRequest(
            serviceName: String = "com.gptremote.mitv",
            clientName: String = Build.MODEL ?: "Mi TV Remote"
        ): ByteArray = ProtoWire.Writer().apply {
            writeVarintField(1, PROTOCOL_VERSION)
            writeVarintField(2, STATUS_OK)
            writeMessageField(10) {
                writeStringField(1, serviceName)
                writeStringField(2, clientName)
            }
        }.toByteArray()

        fun buildPairingOption(): ByteArray = ProtoWire.Writer().apply {
            writeVarintField(1, PROTOCOL_VERSION)
            writeVarintField(2, STATUS_OK)
            writeMessageField(20) {
                writeMessageField(1) { // input_encodings
                    writeVarintField(1, ENCODING_HEXADECIMAL)
                    writeVarintField(2, 6)
                }
                writeVarintField(3, ROLE_INPUT)
            }
        }.toByteArray()

        fun buildPairingConfiguration(): ByteArray = ProtoWire.Writer().apply {
            writeVarintField(1, PROTOCOL_VERSION)
            writeVarintField(2, STATUS_OK)
            writeMessageField(30) {
                writeMessageField(1) { // encoding
                    writeVarintField(1, ENCODING_HEXADECIMAL)
                    writeVarintField(2, 6)
                }
                writeVarintField(2, ROLE_INPUT)
            }
        }.toByteArray()

        fun buildPairingSecret(secretDigest: ByteArray): ByteArray = ProtoWire.Writer().apply {
            writeVarintField(1, PROTOCOL_VERSION)
            writeVarintField(2, STATUS_OK)
            writeMessageField(40) {
                writeBytesField(1, secretDigest)
            }
        }.toByteArray()

        fun buildRemoteConfigure(): ByteArray = ProtoWire.Writer().apply {
            writeMessageField(1) {
                writeVarintField(1, 622)
                writeMessageField(2) {
                    writeStringField(1, Build.MODEL ?: "Android")
                    writeStringField(2, Build.MANUFACTURER ?: "Phone")
                    writeVarintField(3, 1)
                    writeStringField(4, "1")
                    writeStringField(5, "com.gptremote.mitv")
                    writeStringField(6, "1.0.0")
                }
            }
        }.toByteArray()

        fun buildRemoteSetActive(): ByteArray = ProtoWire.Writer().apply {
            writeMessageField(2) {
                writeVarintField(1, 622)
            }
        }.toByteArray()

        fun buildPingResponse(val1: Int): ByteArray = ProtoWire.Writer().apply {
            writeMessageField(9) {
                writeVarintField(1, val1)
            }
        }.toByteArray()

        fun buildKeyInject(keyCode: Int, direction: Int = DIRECTION_SHORT): ByteArray =
            ProtoWire.Writer().apply {
                writeMessageField(10) {
                    writeVarintField(1, keyCode)
                    writeVarintField(2, direction)
                }
            }.toByteArray()

        fun buildImeBatchEdit(imeCounter: Int, fieldCounter: Int, text: String): ByteArray =
            ProtoWire.Writer().apply {
                writeMessageField(21) {
                    writeVarintField(1, imeCounter)
                    writeVarintField(2, fieldCounter)
                    writeMessageField(3) {
                        writeVarintField(1, 1)
                        writeMessageField(2) {
                            writeVarintField(1, 0)
                            writeVarintField(2, 0)
                            writeStringField(3, text)
                        }
                    }
                }
            }.toByteArray()

        fun buttonToAndroidKeyCode(button: RemoteButton): Int? = when (button) {
            RemoteButton.DPAD_UP -> KeyEvent.KEYCODE_DPAD_UP
            RemoteButton.DPAD_DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
            RemoteButton.DPAD_LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
            RemoteButton.DPAD_RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
            RemoteButton.DPAD_CENTER -> KeyEvent.KEYCODE_DPAD_CENTER
            RemoteButton.BACK -> KeyEvent.KEYCODE_BACK
            RemoteButton.HOME -> KeyEvent.KEYCODE_HOME
            RemoteButton.MENU -> KeyEvent.KEYCODE_MENU
            RemoteButton.POWER -> KeyEvent.KEYCODE_POWER
            RemoteButton.VOLUME_UP -> KeyEvent.KEYCODE_VOLUME_UP
            RemoteButton.VOLUME_DOWN -> KeyEvent.KEYCODE_VOLUME_DOWN
            RemoteButton.MUTE -> KeyEvent.KEYCODE_VOLUME_MUTE
            RemoteButton.CHANNEL_UP -> KeyEvent.KEYCODE_CHANNEL_UP
            RemoteButton.CHANNEL_DOWN -> KeyEvent.KEYCODE_CHANNEL_DOWN
            RemoteButton.MEDIA_PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            RemoteButton.MEDIA_PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            RemoteButton.MEDIA_NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            RemoteButton.MEDIA_STOP -> KeyEvent.KEYCODE_MEDIA_STOP
            RemoteButton.SEARCH -> KeyEvent.KEYCODE_SEARCH
            RemoteButton.SETTINGS -> KeyEvent.KEYCODE_SETTINGS
            RemoteButton.TV_INPUT -> KeyEvent.KEYCODE_TV_INPUT
            RemoteButton.DIGIT_0 -> KeyEvent.KEYCODE_0
            RemoteButton.DIGIT_1 -> KeyEvent.KEYCODE_1
            RemoteButton.DIGIT_2 -> KeyEvent.KEYCODE_2
            RemoteButton.DIGIT_3 -> KeyEvent.KEYCODE_3
            RemoteButton.DIGIT_4 -> KeyEvent.KEYCODE_4
            RemoteButton.DIGIT_5 -> KeyEvent.KEYCODE_5
            RemoteButton.DIGIT_6 -> KeyEvent.KEYCODE_6
            RemoteButton.DIGIT_7 -> KeyEvent.KEYCODE_7
            RemoteButton.DIGIT_8 -> KeyEvent.KEYCODE_8
            RemoteButton.DIGIT_9 -> KeyEvent.KEYCODE_9
            RemoteButton.BACKSPACE -> KeyEvent.KEYCODE_DEL
            RemoteButton.ENTER -> KeyEvent.KEYCODE_ENTER
            RemoteButton.ESCAPE -> KeyEvent.KEYCODE_ESCAPE
            RemoteButton.TAB -> KeyEvent.KEYCODE_TAB
            RemoteButton.PC_COPY -> KeyEvent.KEYCODE_COPY
            RemoteButton.PC_PASTE -> KeyEvent.KEYCODE_PASTE
            RemoteButton.PC_WIN_KEY,
            RemoteButton.PC_ALT_TAB,
            RemoteButton.PC_SHOW_DESKTOP -> null
        }
    }
}
