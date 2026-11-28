package com.gptremote.mitv.wifi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gptremote.mitv.MainActivity
import com.gptremote.mitv.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Foreground keep-alive service that:
 * 1. Keeps large file transfers running uninterrupted if the screen locks or user switches apps.
 * 2. Keeps active device sockets / Wi-Fi responsive for 5 minutes when the app goes to background.
 */
class TransferKeepAliveService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var idleStopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val label = intent?.getStringExtra(EXTRA_STATUS_TEXT)
            ?: "Keeping transfers and device connections active"
        val isTransferring = activeTransferCount.get() > 0

        acquireLocks()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID,
                    buildNotification(label, isTransferring),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIF_ID, buildNotification(label, isTransferring))
            }
        }

        idleStopJob?.cancel()
        if (!isTransferring && inBackground.get()) {
            val remainingMs = (graceUntilMs - System.currentTimeMillis()).coerceAtLeast(1000L)
            idleStopJob = scope.launch {
                delay(remainingMs)
                if (activeTransferCount.get() <= 0) {
                    stopForegroundServiceSafely()
                }
            }
        } else if (!isTransferring && !inBackground.get()) {
            // App returned to foreground with no ongoing transfers
            stopForegroundServiceSafely()
        }

        return START_NOT_STICKY
    }

    private fun acquireLocks() {
        runCatching {
            if (wakeLock == null) {
                val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gptremote:KeepAliveWakeLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(BACKGROUND_GRACE_MS + 60_000L)
            }
        }
        runCatching {
            if (wifiLock == null) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wm?.createWifiLock(mode, "gptremote:KeepAliveWifiLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
            }
        }
    }

    private fun releaseLocks() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        runCatching {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        }
    }

    private fun stopForegroundServiceSafely() {
        releaseLocks()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onDestroy() {
        idleStopJob?.cancel()
        releaseLocks()
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val existing = mgr.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Background Transfers & Keep-Alive",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps file transfers & remote device connections active in background"
                setShowBadge(false)
            }
            mgr.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(statusText: String, isTransferring: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(
                if (isTransferring) "GPT Remote • File Transfer Active"
                else "GPT Remote • Connected in Background"
            )
            .setContentText(statusText)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pi)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "gpt_transfer_keepalive"
        private const val NOTIF_ID = 4207
        const val BACKGROUND_GRACE_MS = 5 * 60_000L
        private const val EXTRA_STATUS_TEXT = "extra_status_text"

        private val activeTransferCount = AtomicInteger(0)
        private val inBackground = AtomicBoolean(false)

        @Volatile
        private var graceUntilMs: Long = 0L

        fun onTransferStarted(context: Context, label: String = "Transferring files...") {
            val n = activeTransferCount.incrementAndGet()
            startOrUpdateService(context, if (n > 1) "$n transfers active" else label)
        }

        fun onTransferFinished(context: Context) {
            val remaining = activeTransferCount.updateAndGet { cur -> (cur - 1).coerceAtLeast(0) }
            if (remaining > 0) {
                startOrUpdateService(context, "$remaining transfer(s) active")
            } else if (inBackground.get() && graceUntilMs > System.currentTimeMillis()) {
                startOrUpdateService(context, "Holding device connections for 5 min")
            } else {
                stop(context)
            }
        }

        fun onAppBackgrounded(context: Context, hasActiveConnections: Boolean) {
            inBackground.set(true)
            if (!hasActiveConnections && activeTransferCount.get() <= 0) return
            graceUntilMs = System.currentTimeMillis() + BACKGROUND_GRACE_MS
            val text = if (activeTransferCount.get() > 0) {
                "File transfer running in background"
            } else {
                "Keeping device connection ready for 5 min"
            }
            startOrUpdateService(context, text)
        }

        fun onAppForegrounded(context: Context) {
            inBackground.set(false)
            graceUntilMs = 0L
            if (activeTransferCount.get() <= 0) {
                stop(context)
            } else {
                startOrUpdateService(context, "File transfer active")
            }
        }

        private fun startOrUpdateService(context: Context, text: String) {
            runCatching {
                val app = context.applicationContext
                val intent = Intent(app, TransferKeepAliveService::class.java).apply {
                    putExtra(EXTRA_STATUS_TEXT, text)
                }
                ContextCompat.startForegroundService(app, intent)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.applicationContext.stopService(Intent(context.applicationContext, TransferKeepAliveService::class.java))
            }
        }
    }
}
