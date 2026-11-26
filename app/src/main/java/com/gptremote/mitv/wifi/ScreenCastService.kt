package com.gptremote.mitv.wifi

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.gptremote.mitv.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Streams the phone's screen to the connected GPT Remote PC desktop app over Wi-Fi.
 */
class ScreenCastService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var lastFrameSentMs: Long = 0L
    private var targetUrl: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopProjection()
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val dataIntent: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_DATA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA_INTENT)
        }
        val pcHost = intent?.getStringExtra(EXTRA_PC_HOST) ?: return START_NOT_STICKY
        targetUrl = "http://$pcHost:58768/phone-frame"

        ensureChannel()
        val notification = buildNotification(pcHost)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (resultCode != Activity.RESULT_OK || dataIntent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startProjection(resultCode, dataIntent)
        return START_NOT_STICKY
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        stopProjection()
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager ?: return
        val projection = manager.getMediaProjection(resultCode, data) ?: return
        mediaProjection = projection

        val thread = HandlerThread("GptScreenCast").apply { start() }
        handlerThread = thread
        val handler = Handler(thread.looper)

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopProjection()
                stopSelf()
            }
        }, handler)

        val metrics = resources.displayMetrics
        val scale = (720f / metrics.widthPixels.coerceAtLeast(1)).coerceAtMost(1f)
        val width = (metrics.widthPixels * scale).toInt().coerceAtLeast(360)
        val height = (metrics.heightPixels * scale).toInt().coerceAtLeast(640)
        val density = metrics.densityDpi

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        reader.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val now = System.currentTimeMillis()
                if (now - lastFrameSentMs < 110L) return@setOnImageAvailableListener
                lastFrameSentMs = now

                val plane = img.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val rawBmp = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                rawBmp.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(rawBmp, 0, 0, width, height)
                rawBmp.recycle()

                val baos = ByteArrayOutputStream()
                cropped.compress(Bitmap.CompressFormat.JPEG, 55, baos)
                cropped.recycle()
                postFrame(baos.toByteArray())
            } catch (_: Exception) {
                // ignore frame drop
            } finally {
                img.close()
            }
        }, handler)

        virtualDisplay = projection.createVirtualDisplay(
            "GptRemoteCast",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler
        )
        _isCasting.value = true
    }

    private fun postFrame(jpeg: ByteArray) {
        val urlStr = targetUrl ?: return
        runCatching {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 800
                readTimeout = 800
                doOutput = true
                setFixedLengthStreamingMode(jpeg.size)
            }
            conn.outputStream.use { it.write(jpeg) }
            conn.responseCode
            conn.disconnect()
        }
    }

    private fun stopProjection() {
        _isCasting.value = false
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
        runCatching { handlerThread?.quitSafely() }
        handlerThread = null
    }

    override fun onDestroy() {
        stopProjection()
        super.onDestroy()
    }

    private fun ensureChannel() {
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Screen Cast to PC", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(pcHost: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("Casting Phone Screen")
            .setContentText("Mirroring screen to PC ($pcHost)")
            .setOngoing(true)
            .setContentIntent(openIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "gpt_screen_cast_channel"
        private const val NOTIFICATION_ID = 2002
        private const val ACTION_STOP = "com.gptremote.mitv.STOP_CAST"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA_INTENT = "data_intent"
        private const val EXTRA_PC_HOST = "pc_host"

        private val _isCasting = MutableStateFlow(false)
        val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

        fun start(context: Context, resultCode: Int, data: Intent, pcHost: String) {
            val intent = Intent(context, ScreenCastService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA_INTENT, data)
                putExtra(EXTRA_PC_HOST, pcHost)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, ScreenCastService::class.java))
            }
        }
    }
}
