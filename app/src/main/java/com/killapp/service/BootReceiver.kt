package com.killapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.killapp.utils.PrefsManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Restart floating overlay if it was enabled
            if (PrefsManager.isFloatEnabled(context) &&
                Settings.canDrawOverlays(context)
            ) {
                val serviceIntent = Intent(context, FloatingOverlayService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
