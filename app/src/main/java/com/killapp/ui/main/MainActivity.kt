package com.killapp.ui.main

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.killapp.R
import com.killapp.service.KillAccessibilityService
import com.killapp.service.KillService
import com.killapp.utils.PrefsManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var pagerAdapter: MainPagerAdapter

    private lateinit var cardAccessibility: LinearLayout
    private lateinit var cardOverlay: LinearLayout
    private lateinit var layoutPermissions: LinearLayout
    private lateinit var tvTotalApps: TextView
    private lateinit var tvExcluded: TextView
    private lateinit var tvLastCleaned: TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var btnKillAll: Button
    private lateinit var layoutProgress: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgressLabel: TextView

    private var progressStep = 0
    private var totalSteps   = 0

    // FIX 1: Cờ báo widget đang chờ danh sách app load xong
    private var pendingWidgetKill = false

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getStringExtra(KillAccessibilityService.EXTRA_STATE) ?: return
            when (state) {
                KillAccessibilityService.STATE_BUSY -> setProcessing(true)
                KillAccessibilityService.STATE_STEP -> {
                    val msg = intent.getStringExtra(KillAccessibilityService.EXTRA_STEP_MSG) ?: ""
                    tvProgressLabel.text = msg
                    progressStep++
                    if (totalSteps > 0)
                        progressBar.progress = ((progressStep.toFloat() / totalSteps) * 100).toInt()
                }
                KillAccessibilityService.STATE_DONE -> {
                    progressBar.progress = 100
                    tvProgressLabel.text = "✅ Hoàn tất!"
                    btnKillAll.postDelayed({ setProcessing(false) }, 1500)
                    updateStats()
                    updateLastClean()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        bindViews()
        setupPager()
        setupClickListeners()
        observeViewModel()

        viewModel.loadApps()
        registerReceiver(
            stateReceiver,
            IntentFilter(KillAccessibilityService.ACTION_STATE),
            RECEIVER_NOT_EXPORTED
        )

        // FIX 1: Nếu được mở từ widget, đặt cờ — sẽ kill ngay khi isLoading chuyển false
        if (intent.getBooleanExtra("trigger_kill", false)) {
            pendingWidgetKill = true
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        updateStats()
        updateLastClean()
    }

    private fun bindViews() {
        cardAccessibility  = findViewById(R.id.cardAccessibility)
        cardOverlay        = findViewById(R.id.cardOverlay)
        layoutPermissions  = findViewById(R.id.layoutPermissions)
        tvTotalApps        = findViewById(R.id.tvTotalApps)
        tvExcluded         = findViewById(R.id.tvExcluded)
        tvLastCleaned      = findViewById(R.id.tvLastCleaned)
        tabLayout          = findViewById(R.id.tabLayout)
        viewPager          = findViewById(R.id.viewPager)
        btnKillAll         = findViewById(R.id.btnKillAll)
        layoutProgress     = findViewById(R.id.layoutProgress)
        progressBar        = findViewById(R.id.progressBar)
        tvProgressLabel    = findViewById(R.id.tvProgressLabel)
    }

    private fun setupPager() {
        pagerAdapter = MainPagerAdapter(this)
        viewPager.adapter = pagerAdapter
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "⚡ Dọn dẹp"
                1 -> "🚫 Loại trừ"
                else -> ""
            }
        }.attach()
    }

    private fun setupClickListeners() {
        cardAccessibility.setOnClickListener { openAccessibilitySettings() }
        cardOverlay.setOnClickListener { openOverlaySettings() }
        btnKillAll.setOnClickListener { startKillAll() }
    }

    private fun observeViewModel() {
        viewModel.filteredApps.observe(this) { updateStats() }
        viewModel.excludedApps.observe(this) { updateStats() }

        // FIX 1: Lắng nghe isLoading — khi false (load xong) và có cờ widget thì kill
        viewModel.isLoading.observe(this) { loading ->
            if (!loading && pendingWidgetKill) {
                pendingWidgetKill = false
                startKillAll()
            }
        }
    }

    fun startKillAll() {
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "Vui lòng bật Accessibility Service trước!", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }

        val toKill    = viewModel.getSelectedApps()
        val cacheOnly = viewModel.getExcludedAppsForCacheClean()

        if (toKill.isEmpty() && cacheOnly.isEmpty()) {
            Toast.makeText(this, "Không có ứng dụng nào để xử lý", Toast.LENGTH_SHORT).show()
            return
        }

        val doClearRecents = PrefsManager.getDoClearRecents(this)
        totalSteps   = toKill.size + cacheOnly.size + (if (doClearRecents) 1 else 0)
        progressStep = 0
        progressBar.progress = 0
        tvProgressLabel.text = "Đang bắt đầu…"
        setProcessing(true)

        KillService.startKill(this, toKill, cacheOnly)
    }

    private fun setProcessing(processing: Boolean) {
        layoutProgress.visibility = if (processing) View.VISIBLE else View.GONE
        btnKillAll.isEnabled = !processing
        btnKillAll.alpha     = if (processing) 0.5f else 1.0f
        btnKillAll.text      = if (processing) "⏳ ĐANG XỬ LÝ…" else "⚡  KILL & CLEAN ALL"
    }

    private fun checkPermissions() {
        val a11yOk    = isAccessibilityEnabled()
        val overlayOk = Settings.canDrawOverlays(this)
        cardAccessibility.visibility = if (a11yOk)    View.GONE else View.VISIBLE
        cardOverlay.visibility       = if (overlayOk) View.GONE else View.VISIBLE
        layoutPermissions.visibility = if (a11yOk && overlayOk) View.GONE else View.VISIBLE
    }

    fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.id.contains(packageName) }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun openOverlaySettings() {
        startActivity(Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }

    @SuppressLint("SetTextI18n")
    private fun updateStats() {
        tvTotalApps.text = viewModel.getTotalCount().toString()
        tvExcluded.text  = viewModel.getExcludedCount().toString()
    }

    private fun updateLastClean() {
        val last = PrefsManager.getLastCleanTime(this)
        tvLastCleaned.text = if (last == 0L) "--"
        else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(last))
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // FIX 1: Widget nhấn lại — nếu đang load thì chờ, không thì kill ngay
        if (intent?.getBooleanExtra("trigger_kill", false) == true) {
            if (viewModel.isLoading.value == true) {
                pendingWidgetKill = true
            } else {
                startKillAll()
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(stateReceiver)
        super.onDestroy()
    }
}
