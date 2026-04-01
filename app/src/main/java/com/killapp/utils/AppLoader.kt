package com.killapp.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.killapp.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppLoader {

    suspend fun loadAllApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val excluded = PrefsManager.getExcludedPackages(context)

        // Đọc trạng thái checkbox đã lưu; null = lần đầu → chọn tất cả
        val savedSelected: Set<String>? = PrefsManager.getSelectedPackages(context)

        val installedApps = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            emptyList()
        }

        installedApps.mapNotNull { appInfo ->
            try {
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isExcluded = excluded.contains(appInfo.packageName)

                // Nếu savedSelected == null → mặc định chọn tất cả (lần đầu cài)
                // Nếu savedSelected != null → khôi phục đúng trạng thái đã lưu
                val isSelected = if (savedSelected == null) true
                                 else savedSelected.contains(appInfo.packageName)

                AppInfo(
                    packageName = appInfo.packageName,
                    appName = pm.getApplicationLabel(appInfo).toString(),
                    icon = pm.getApplicationIcon(appInfo.packageName),
                    isSystemApp = isSystem,
                    isSelected = isSelected,
                    isExcluded = isExcluded
                )
            } catch (e: Exception) {
                null
            }
        }.sortedWith(compareBy({ it.isSystemApp }, { it.appName.lowercase() }))
    }

    suspend fun loadUserApps(context: Context): List<AppInfo> {
        return loadAllApps(context).filter { !it.isSystemApp }
    }
}
