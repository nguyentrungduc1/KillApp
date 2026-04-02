package com.killapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import com.killapp.R
import com.killapp.ui.main.MainActivity

/**
 * Popup tiến trình — hiển thị tự động khi kill đang chạy.
 * Không còn nút nổi; chỉ là overlay thông báo tiến độ.
 */
class FloatingOverlayService : Service() {

    companion object {
        private const val NOTIF_ID  = 1002
        private const val CHANNEL_ID = "killapp_overlay_channel"
    }

    private lateinit var windowManager: WindowManager
    private var popupView: View? = null
    private lateinit var params: WindowManager.LayoutParams

    // Views bên trong popup
    private var tvStepName: TextView? = null
    private var tvStepSub: TextView? = null
    private var tvCounter: TextView? = null
    private var tvAppName: TextView? = null
    private var progressBar: ProgressBar? = null

    private var totalTasks = 0
    private var currentIndex = 0
    private val handler = Handler(Looper.getMainLooper())

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getStringExtra(KillAccessibilityService.EXTRA_STATE) ?: return
            when (state) {
                KillAccessibilityService.STATE_BUSY -> {
                    totalTasks = intent.getIntExtra(KillAccessibilityService.EXTRA_TOTAL_TASKS, 0)
                    currentIndex = 0
                    showPopup()
                    updateProgress(0, totalTasks)
                    setStepName("Đang khởi động…", "")
                }

                KillAccessibilityService.STATE_STEP -> {
                    val type     = intent.getStringExtra(KillAccessibilityService.EXTRA_STEP_TYPE) ?: ""
                    val appName  = intent.getStringExtra(KillAccessibilityService.EXTRA_APP_NAME) ?: ""
                    val index    = intent.getIntExtra(KillAccessibilityService.EXTRA_STEP_INDEX, currentIndex)
                    val total    = intent.getIntExtra(KillAccessibilityService.EXTRA_STEP_TOTAL, totalTasks)

                    currentIndex = index
                    totalTasks   = total

                    val stepLabel = when (type) {
                        KillAccessibilityService.STEP_TYPE_FORCE_STOP  -> "Buộc dừng"
                        KillAccessibilityService.STEP_TYPE_CLEAR_CACHE -> "Xoá cache"
                        KillAccessibilityService.STEP_TYPE_RECENTS     -> "Đóng Recents"
                        else -> "Đang xử lý"
                    }
                    val subLabel = "#$index"

                    setStepName(stepLabel, subLabel)
                    updateProgress(index, total)
                    setCounter(index, total, appName)
                }

                KillAccessibilityService.STATE_DONE -> {
                    setStepName("✅ Hoàn tất!", "")
                    setCounter(totalTasks, totalTasks, "")
                    updateProgress(totalTasks, totalTasks)
                    // Ẩn popup sau 2.5 giây rồi tự dừng service
                    handler.postDelayed({
                        hidePopup()
                        stopSelf()
                    }, 2500)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        inflatePopup()
        registerReceiver(
            stateReceiver,
            IntentFilter(KillAccessibilityService.ACTION_STATE),
            RECEIVER_NOT_EXPORTED
        )
    }

    private fun inflatePopup() {
        popupView = LayoutInflater.from(this).inflate(R.layout.layout_floating_overlay, null)

        tvStepName  = popupView?.findViewById(R.id.floatStepName)
        tvStepSub   = popupView?.findViewById(R.id.floatStepSub)
        tvCounter   = popupView?.findViewById(R.id.floatCounter)
        tvAppName   = popupView?.findViewById(R.id.floatAppName)
        progressBar = popupView?.findViewById(R.id.floatProgressBar)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            // Góc dưới bên phải, cách mép 16dp
            gravity = Gravity.BOTTOM or Gravity.END
            x = 16
            y = 80
        }

        // Ẩn ban đầu — chỉ hiện khi STATE_BUSY
        popupView?.visibility = View.GONE
        windowManager.addView(popupView, params)
    }

    private fun showPopup() {
        popupView?.post { popupView?.visibility = View.VISIBLE }
    }

    private fun hidePopup() {
        popupView?.post { popupView?.visibility = View.GONE }
    }

    private fun setStepName(name: String, sub: String) {
        popupView?.post {
            tvStepName?.text = name
            tvStepSub?.text  = sub
        }
    }

    private fun updateProgress(done: Int, total: Int) {
        popupView?.post {
            val pct = if (total > 0) (done * 100 / total) else 0
            progressBar?.progress = pct
        }
    }

    private fun setCounter(done: Int, total: Int, appName: String) {
        popupView?.post {
            tvCounter?.text  = "$done / $total"
            tvAppName?.text  = appName
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "KillApp Progress", NotificationManager.IMPORTANCE_MIN
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("KillApp")
            .setContentText("Đang dọn dẹp…")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentIntent(pi)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        unregisterReceiver(stateReceiver)
        popupView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        super.onDestroy()
    }
}
