package com.killapp.model

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean,
    var isSelected: Boolean = true,
    var isExcluded: Boolean = false
)
