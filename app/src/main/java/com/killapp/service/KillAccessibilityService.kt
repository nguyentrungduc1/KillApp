package com.killapp.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class KillAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "KillA11y"

        const val ACTION_STATE      = "com.killapp.STATE"
        const val EXTRA_STATE       = "state"
        const val STATE_READY       = "ready"
        const val STATE_BUSY        = "busy"
        const val STATE_DONE        = "done"
        const val STATE_STEP        = "step"

        // Cũ — giữ tương thích
        const val EXTRA_STEP_MSG    = "step_msg"

        // Mới — dành cho popup tiến trình
        const val EXTRA_STEP_TYPE   = "step_type"   // "force_stop" | "clear_cache" | "recents"
        const val EXTRA_APP_NAME    = "app_name"    // tên hiển thị app hiện tại
        const val EXTRA_STEP_INDEX  = "step_index"  // số thứ tự app (1-based)
        const val EXTRA_STEP_TOTAL  = "step_total"  // tổng số app phải xử lý
        const val EXTRA_TOTAL_TASKS = "total_tasks" // tổng tasks gửi cùng STATE_BUSY

        const val STEP_TYPE_FORCE_STOP  = "force_stop"
        const val STEP_TYPE_CLEAR_CACHE = "clear_cache"
        const val STEP_TYPE_RECENTS     = "recents"

        var instance: KillAccessibilityService? = null
    }

    data class KillTask(val pkg: String, val doForceStop: Boolean, val doClearCache: Boolean)

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val killQueue = ArrayDeque<KillTask>()
    private var isProcessing = false
    private var clearRecentPending = false
    private var totalApps = 0  // tổng số app, truyền vào từ KillService

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = info
        broadcastState(STATE_READY)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    fun enqueueKill(pkg: String, doForceStop: Boolean, doClearCache: Boolean = true) {
        killQueue.addLast(KillTask(pkg, doForceStop, doClearCache))
    }

    fun scheduleClearRecent() {
        clearRecentPending = true
    }

    fun startProcessing(total: Int) {
        if (!isProcessing) {
            totalApps = total
            isProcessing = true
            broadcastBusy(total)
            scope.launch { processAll() }
        }
    }

    // ─── Main sequential loop ────────────────────────────────────────────────

    private suspend fun processAll() {
        var appIndex = 0
        val snapshotTotal = totalApps

        while (killQueue.isNotEmpty()) {
            val task = killQueue.removeFirst()

            if (task.pkg == packageName || task.pkg == "android" ||
                task.pkg == "com.android.systemui") {
                Log.w(TAG, "Skipping protected: ${task.pkg}")
                continue
            }

            appIndex++
            val appName = resolveAppName(task.pkg)

            // Bước Force Stop
            if (task.doForceStop) {
                broadcastStepDetail(
                    type = STEP_TYPE_FORCE_STOP,
                    appName = appName,
                    index = appIndex,
                    total = snapshotTotal,
                    msg = "Buộc dừng: $appName"
                )
            }

            // Bước Clear Cache (dùng chung appIndex vì vẫn là cùng 1 app)
            if (task.doClearCache) {
                broadcastStepDetail(
                    type = STEP_TYPE_CLEAR_CACHE,
                    appName = appName,
                    index = appIndex,
                    total = snapshotTotal,
                    msg = "Xoá cache: $appName"
                )
            }

            processOneApp(task)
            delay(400)
        }

        if (clearRecentPending) {
            clearRecentPending = false
            delay(600)
            broadcastStepDetail(
                type = STEP_TYPE_RECENTS,
                appName = "",
                index = snapshotTotal,
                total = snapshotTotal,
                msg = "Đóng tất cả tác vụ…"
            )
            clearRecentTasks()
        }

        isProcessing = false
        broadcastState(STATE_DONE)
        delay(500)
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private suspend fun processOneApp(task: KillTask) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${task.pkg}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open app info: ${task.pkg}", e)
            return
        }

        if (!waitForSettingsScreen()) {
            Log.w(TAG, "App info screen timeout: ${task.pkg}")
            return
        }

        if (task.doForceStop) {
            doForceStop()
            delay(600)
        }

        if (task.doClearCache) {
            doClearCache()
        }
    }

    // ─── Force Stop ──────────────────────────────────────────────────────────

    private suspend fun doForceStop() {
        val forceStopNode = pollForNode(
            texts = listOf("Force stop", "Force Stop", "Buộc dừng", "Buộc tạm dừng"),
            timeoutMs = 3000,
            requireEnabled = true
        ) ?: return

        forceStopNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        delay(700)

        val root = rootInActiveWindow ?: return
        val okNode = findNodeByTexts(root, listOf("OK", "Ok", "YES", "Yes", "Đồng ý", "Có"))
        okNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (okNode != null) delay(800)
    }

    // ─── Clear Cache ─────────────────────────────────────────────────────────
    // FIX 1: Samsung One UI 6+ — text node không clickable, cần leo lên ancestor
    // FIX 2: Scroll xuống cuối màn hình App Info trước khi tìm "Lưu trữ"
    //        (Samsung One UI 6 ẩn mục Storage ở cuối danh sách, không tự cuộn)

    private suspend fun doClearCache() {
        // FIX 2: Scroll xuống cuối màn hình App Info để "Lưu trữ" lộ ra
        scrollToBottomInSettings()
        delay(400)

        // Tìm mục "Lưu trữ" và leo lên clickable ancestor nếu cần
        val storageNode = pollForClickableNode(
            texts = listOf(
                "Storage", "Bộ nhớ", "Bộ nhớ & bộ nhớ đệm",
                "Storage & cache", "Storage and cache",
                "Lưu trữ", "Bộ nhớ trong", "Internal storage"
            ),
            timeoutMs = 4000
        ) ?: run {
            Log.w(TAG, "Storage node not found")
            return
        }

        storageNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        // Samsung One UI 6+ cần thêm thời gian để mở Storage screen
        delay(1200)

        // Đợi màn hình Storage (Lưu trữ) load bằng cách kiểm tra text cache xuất hiện
        if (!waitForStorageScreen()) {
            Log.w(TAG, "Storage screen timeout — going back")
            performGlobalAction(GLOBAL_ACTION_BACK)
            delay(600)
            return
        }

        // Tìm nút "Xóa bộ nhớ đệm"
        var clearCacheNode = pollForNode(
            texts = listOf(
                "Clear cache", "Clear Cache",
                "Xóa bộ nhớ đệm", "Xoá bộ nhớ đệm",
                "Delete cache", "Xóa cache"
            ),
            timeoutMs = 3000,
            requireEnabled = true
        )

        // Nếu không thấy, scroll xuống thêm lần nữa rồi tìm lại
        if (clearCacheNode == null) {
            val root = rootInActiveWindow
            findScrollable(root ?: return)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            delay(500)
            clearCacheNode = pollForNode(
                texts = listOf(
                    "Clear cache", "Clear Cache",
                    "Xóa bộ nhớ đệm", "Xoá bộ nhớ đệm",
                    "Delete cache", "Xóa cache"
                ),
                timeoutMs = 2000,
                requireEnabled = true
            )
        }

        if (clearCacheNode != null) {
            clearCacheNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(500)
            Log.d(TAG, "Cache cleared")
        } else {
            Log.w(TAG, "Clear cache button not found or disabled (cache may be 0)")
        }

        performGlobalAction(GLOBAL_ACTION_BACK)
        delay(600)
    }

    /**
     * FIX 2: Scroll xuống cuối màn hình Settings hiện tại (App Info).
     * Samsung One UI 6 không tự cuộn — mục "Lưu trữ" nằm cuối RecyclerView.
     * Thực hiện tối đa 5 lần scroll forward để đảm bảo đến cuối.
     */
    private suspend fun scrollToBottomInSettings() {
        repeat(5) {
            val root = rootInActiveWindow ?: return
            val scrollable = findScrollable(root)
            if (scrollable != null) {
                val scrolled = scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                if (!scrolled) return   // đã đến cuối, thoát sớm
                delay(250)
            } else {
                return
            }
        }
    }

    // ─── Clear Recent Tasks ──────────────────────────────────────────────────
    // FIX 3: Dùng nút "Đóng tất cả" thay cho vuốt.
    //        Samsung One UI tiếng Việt hiển thị nút "Đóng tất cả" ở cuối màn hình Recents.

    private suspend fun clearRecentTasks() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
        delay(1500) // Samsung One UI cần thêm thời gian để render màn hình Recents

        val clearAllTexts = listOf(
            "Đóng tất cả",    // Samsung One UI - tiếng Việt (ảnh màn hình)
            "Close all",      // Samsung One UI - tiếng Anh
            "Clear all",      // AOSP / Pixel
            "CLEAR ALL",
            "Clear All",
            "Xóa tất cả",
            "Xoá tất cả",
            "Dismiss all",
            "End all",
            "Close All"
        )

        // Poll để tìm nút — không dùng vuốt/scroll
        val clearAllNode = pollForNode(
            texts = clearAllTexts,
            timeoutMs = 4000
        )

        if (clearAllNode != null) {
            clearAllNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(800)
            Log.d(TAG, "Recent tasks cleared via button")
        } else {
            // Không có app nào trong Recents hoặc UI khác — về Home
            Log.w(TAG, "Clear all button not found — recents may already be empty")
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun waitForSettingsScreen(timeoutMs: Long = 4000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val pkg = rootInActiveWindow?.packageName?.toString() ?: ""
            if (pkg.contains("settings", ignoreCase = true) ||
                pkg == "com.google.android.packageinstaller") {
                return true
            }
            delay(200)
        }
        return false
    }

    /**
     * FIX 1: Đợi màn hình Storage load — kiểm tra xuất hiện keyword liên quan đến cache.
     */
    private suspend fun waitForStorageScreen(timeoutMs: Long = 5000): Boolean {
        val storageKeywords = listOf(
            "Clear cache", "Xóa bộ nhớ đệm", "Xoá bộ nhớ đệm",
            "Delete cache", "Xóa cache",
            "Bộ nhớ đệm", "Cache",
            "Clear data", "Xóa dữ liệu"
        )
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val root = rootInActiveWindow
            if (root != null) {
                val pkg = root.packageName?.toString() ?: ""
                if (pkg.contains("settings", ignoreCase = true)) {
                    val found = storageKeywords.any { kw ->
                        root.findAccessibilityNodeInfosByText(kw).isNotEmpty()
                    }
                    if (found) return true
                }
            }
            delay(300)
        }
        return false
    }

    private suspend fun pollForNode(
        texts: List<String>,
        timeoutMs: Long = 3000,
        requireEnabled: Boolean = false
    ): AccessibilityNodeInfo? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val root = rootInActiveWindow
            if (root != null) {
                val node = findNodeByTexts(root, texts)
                if (node != null && (!requireEnabled || node.isEnabled)) return node
            }
            delay(300)
        }
        return null
    }

    /**
     * FIX 1: Tìm node theo text rồi leo lên ancestor clickable.
     * Giải quyết vấn đề Samsung One UI 6+ RecyclerView item không clickable trực tiếp.
     */
    private suspend fun pollForClickableNode(
        texts: List<String>,
        timeoutMs: Long = 4000
    ): AccessibilityNodeInfo? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val root = rootInActiveWindow
            if (root != null) {
                val node = findNodeByTexts(root, texts)
                if (node != null) {
                    if (node.isClickable) return node
                    val clickable = findClickableAncestor(node, maxDepth = 5)
                    if (clickable != null) return clickable
                }
            }
            delay(300)
        }
        return null
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo, maxDepth: Int = 5): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node.parent
        var depth = 0
        while (current != null && depth < maxDepth) {
            if (current.isClickable) return current
            current = current.parent
            depth++
        }
        return null
    }

    private fun findNodeByTexts(root: AccessibilityNodeInfo, texts: List<String>): AccessibilityNodeInfo? {
        for (text in texts) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (nodes.isNotEmpty()) return nodes.first()
        }
        return null
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findScrollable(child)
            if (result != null) return result
        }
        return null
    }

    private fun broadcastState(state: String) {
        sendBroadcast(Intent(ACTION_STATE).apply {
            putExtra(EXTRA_STATE, state)
            `package` = packageName
        })
    }

    private fun broadcastBusy(total: Int) {
        sendBroadcast(Intent(ACTION_STATE).apply {
            putExtra(EXTRA_STATE, STATE_BUSY)
            putExtra(EXTRA_TOTAL_TASKS, total)
            `package` = packageName
        })
    }

    private fun broadcastStepDetail(
        type: String, appName: String, index: Int, total: Int, msg: String
    ) {
        sendBroadcast(Intent(ACTION_STATE).apply {
            putExtra(EXTRA_STATE, STATE_STEP)
            putExtra(EXTRA_STEP_MSG, msg)       // tương thích cũ
            putExtra(EXTRA_STEP_TYPE, type)
            putExtra(EXTRA_APP_NAME, appName)
            putExtra(EXTRA_STEP_INDEX, index)
            putExtra(EXTRA_STEP_TOTAL, total)
            `package` = packageName
        })
    }

    private fun resolveAppName(pkg: String): String {
        return try {
            val pm = packageManager
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (e: Exception) {
            pkg.substringAfterLast('.')
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
        instance = null
    }
}
