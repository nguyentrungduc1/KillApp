package com.killapp.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object PrefsManager {

    private const val PREFS_NAME = "killapp_prefs"
    private const val KEY_EXCLUDED = "excluded_packages"
    private const val KEY_LAST_CLEAN = "last_clean_time"
    private const val KEY_FLOAT_ENABLED = "float_enabled"

    // FIX 2: Keys lưu trữ tùy chọn hoạt động — nhớ qua các lần mở app
    private const val KEY_DO_FORCE_STOP = "do_force_stop"
    private const val KEY_DO_CLEAR_CACHE = "do_clear_cache"
    private const val KEY_DO_CLEAR_RECENTS = "do_clear_recents"

    private val gson = Gson()

    val DEFAULT_EXCLUDED = setOf(
        "com.killapp",
        "android",
        "com.android.systemui",
        "com.android.launcher",
        "com.android.launcher2",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.android.phone",
        "com.android.settings",
        "com.android.inputmethod.latin",
        "com.google.android.inputmethod.latin",
        "com.android.vending",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.packageinstaller",
        "com.android.permissioncontroller"
    )

    fun getExcludedPackages(context: Context): MutableSet<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_EXCLUDED, null)
        return if (json != null) {
            val type = object : TypeToken<MutableSet<String>>() {}.type
            (gson.fromJson<MutableSet<String>>(json, type) ?: mutableSetOf()).also {
                it.addAll(DEFAULT_EXCLUDED)
            }
        } else {
            DEFAULT_EXCLUDED.toMutableSet()
        }
    }

    fun saveExcludedPackages(context: Context, packages: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_EXCLUDED, gson.toJson(packages)).apply()
    }

    fun addExcluded(context: Context, packageName: String) {
        val set = getExcludedPackages(context)
        set.add(packageName)
        saveExcludedPackages(context, set)
    }

    fun removeExcluded(context: Context, packageName: String) {
        if (DEFAULT_EXCLUDED.contains(packageName)) return
        val set = getExcludedPackages(context)
        set.remove(packageName)
        saveExcludedPackages(context, set)
    }

    fun isExcluded(context: Context, packageName: String): Boolean {
        return getExcludedPackages(context).contains(packageName)
    }

    fun setLastCleanTime(context: Context, time: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_CLEAN, time).apply()
    }

    fun getLastCleanTime(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_CLEAN, 0L)
    }

    fun setFloatEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_FLOAT_ENABLED, enabled).apply()
    }

    fun isFloatEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_FLOAT_ENABLED, false)
    }

    // ─── Lưu / đọc trạng thái checkbox (app nào được chọn) ──────────────────
    private const val KEY_SELECTED_PACKAGES = "selected_packages"
    // Sentinel: null = chưa có dữ liệu → mặc định chọn tất cả
    // empty json set = user đã bỏ chọn tất cả

    fun getSelectedPackages(context: Context): Set<String>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SELECTED_PACKAGES, null) ?: return null
        val type = object : TypeToken<Set<String>>() {}.type
        return gson.fromJson(json, type) ?: emptySet()
    }

    fun saveSelectedPackages(context: Context, packages: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SELECTED_PACKAGES, gson.toJson(packages)).apply()
    }

    // ─── FIX 2: Lưu / đọc tùy chọn hoạt động ────────────────────────────────

    /** Có Force Stop app không? Mặc định: true */
    fun getDoForceStop(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DO_FORCE_STOP, true)

    fun setDoForceStop(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DO_FORCE_STOP, value).apply()
    }

    /** Có xóa bộ nhớ đệm (cache) không? Mặc định: true */
    fun getDoClearCache(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DO_CLEAR_CACHE, true)

    fun setDoClearCache(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DO_CLEAR_CACHE, value).apply()
    }

    /** Có đóng tất cả tác vụ gần đây (Recents) sau cùng không? Mặc định: true */
    fun getDoClearRecents(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DO_CLEAR_RECENTS, true)

    fun setDoClearRecents(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DO_CLEAR_RECENTS, value).apply()
    }
}
