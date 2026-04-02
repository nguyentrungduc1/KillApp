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
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import com.killapp.R
import com.killapp.ui.main.MainActivity

class FloatingOverlayService : Service() {

    companion object {
        private const val NOTIF_ID = 1002
        private const val CHANNEL_ID = "killapp_overlay_channel"
    }

    private lateinit var windowManager: WindowManager
    private var floatView: View? = null
    private lateinit var params: WindowManager.LayoutParams

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getStringExtra(KillAccessibilityService.EXTRA_STATE) ?: return
            when (state) {
                KillAccessibilityService.STATE_BUSY -> showProgress(true)
                KillAccessibilityService.STATE_DONE -> {
                    showProgress(false)
                    updateProgress(100, "Done!")
                }
                KillAccessibilityService.STATE_STEP -> {
                    val msg = intent.getStringExtra(KillAccessibilityService.EXTRA_STEP_MSG) ?: ""
                    updateProgressText(msg)
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
        inflateOverlay()
        registerReceiver(stateReceiver, IntentFilter(KillAccessibilityService.ACTION_STATE),
            RECEIVER_NOT_EXPORTED)
    }

    private fun inflateOverlay() {
        val inflater = LayoutInflater.from(this)
        floatView = inflater.inflate(R.layout.layout_floating_overlay, null)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 8
            y = 200
        }

        windowManager.addView(floatView, params)

        // Click to kill
        floatView?.findViewById<View>(R.id.floatKillBtn)?.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("trigger_kill", true)
            }
            startActivity(intent)
        }

        // Drag to move
        var initialX = 0; var initialY = 0
        var initialTouchX = 0f; var initialTouchY = 0f
        floatView?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatView, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun showProgress(show: Boolean) {
        floatView?.post {
            floatView?.findViewById<View>(R.id.floatProgress)?.visibility =
                if (show) View.VISIBLE else View.GONE
        }
    }

    private fun updateProgress(progress: Int, text: String) {
        floatView?.post {
            floatView?.findViewById<ProgressBar>(R.id.floatProgressBar)?.progress = progress
            floatView?.findViewById<TextView>(R.id.floatProgressText)?.text = text
        }
    }

    private fun updateProgressText(text: String) {
        floatView?.post {
            floatView?.findViewById<TextView>(R.id.floatProgressText)?.text =
                text.take(20) + if (text.length > 20) "…" else ""
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "KillApp Overlay", NotificationManager.IMPORTANCE_MIN
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("KillApp Overlay")
            .setContentText("Nút nổi đang hiển thị")
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setContentIntent(pi)
            .build()
    }

    override fun onDestroy() {
        unregisterReceiver(stateReceiver)
        floatView?.let { windowManager.removeView(it) }
        super.onDestroy()
    }
}
