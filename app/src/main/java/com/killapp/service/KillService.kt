package com.killapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.killapp.R
import com.killapp.model.AppInfo
import com.killapp.ui.main.MainActivity
import com.killapp.utils.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class KillService : Service() {

    companion object {
        private const val TAG = "KillService"
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "killapp_channel"

        const val ACTION_START_KILL = "com.killapp.START_KILL"
        const val EXTRA_APP_LIST = "app_list_packages"
        const val EXTRA_CLEAR_CACHE_ONLY = "cache_only_packages"

        fun startKill(
            context: Context,
            toKill: List<AppInfo>,      // kill + clear cache
            cacheOnly: List<AppInfo>    // only clear cache (excluded)
        ) {
            val intent = Intent(context, KillService::class.java).apply {
                action = ACTION_START_KILL
                putStringArrayListExtra(EXTRA_APP_LIST, ArrayList(toKill.map { it.packageName }))
                putStringArrayListExtra(EXTRA_CLEAR_CACHE_ONLY, ArrayList(cacheOnly.map { it.packageName }))
            }
            context.startForegroundService(intent)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Đang khởi động…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_KILL) {
            val toKillPkgs = intent.getStringArrayListExtra(EXTRA_APP_LIST) ?: arrayListOf()
            val cacheOnlyPkgs = intent.getStringArrayListExtra(EXTRA_CLEAR_CACHE_ONLY) ?: arrayListOf()

            scope.launch {
                enqueueToAccessibility(toKillPkgs, cacheOnlyPkgs)
            }
        }
        return START_NOT_STICKY
    }

    private fun enqueueToAccessibility(
        toKillPkgs: List<String>,
        cacheOnlyPkgs: List<String>
    ) {
        val a11y = KillAccessibilityService.instance
        if (a11y == null) {
            Log.e(TAG, "Accessibility service not running!")
            stopSelf()
            return
        }

        updateNotification("Xếp hàng ${toKillPkgs.size + cacheOnlyPkgs.size} ứng dụng…")

        val doForceStop = PrefsManager.getDoForceStop(this)
        val doClearCache = PrefsManager.getDoClearCache(this)
        val doClearRecents = PrefsManager.getDoClearRecents(this)
        val totalApps = toKillPkgs.size + cacheOnlyPkgs.size

        for (pkg in toKillPkgs) {
            a11y.enqueueKill(pkg, doForceStop = doForceStop, doClearCache = doClearCache)
        }
        for (pkg in cacheOnlyPkgs) {
            a11y.enqueueKill(pkg, doForceStop = false, doClearCache = doClearCache)
        }
        if (doClearRecents) a11y.scheduleClearRecent()

        // Auto-khởi động popup tiến trình (không cần user bật tay)
        startForegroundService(Intent(this, FloatingOverlayService::class.java))

        a11y.startProcessing(totalApps)

        PrefsManager.setLastCleanTime(this, System.currentTimeMillis())
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_delete)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
