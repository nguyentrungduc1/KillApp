package com.killapp.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.killapp.R
import com.killapp.ui.main.MainActivity

/**
 * Widget 1×1: KILL & CLEAN ALL
 * Nhấn vào widget → mở MainActivity và tự động kích hoạt Kill.
 */
class KillWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_kill)

        // Click widget → mở MainActivity với trigger_kill=true
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("trigger_kill", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(R.id.tvWidgetIcon, pendingIntent)
        views.setOnClickPendingIntent(R.id.tvWidgetLabel, pendingIntent)

        appWidgetManager.updateAppWidget(widgetId, views)
    }
}
