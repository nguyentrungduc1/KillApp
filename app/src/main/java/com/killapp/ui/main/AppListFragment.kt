package com.killapp.ui.main

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.killapp.R
import com.killapp.model.AppInfo

class AppListFragment : Fragment() {

    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: AppListAdapter
    private var filterType = "all" // all, user, system

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_app_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        val rvApps = view.findViewById<RecyclerView>(R.id.rvApps)
        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        val btnSelectAll = view.findViewById<android.widget.Button>(R.id.btnSelectAll)
        val chipAll = view.findViewById<TextView>(R.id.chipAll)
        val chipUser = view.findViewById<TextView>(R.id.chipUser)
        val chipSystem = view.findViewById<TextView>(R.id.chipSystem)

        adapter = AppListAdapter(emptyList()) { app, selected ->
            viewModel.toggleApp(app, selected)
        }
        rvApps.layoutManager = LinearLayoutManager(requireContext())
        rvApps.adapter = adapter

        // Observe apps
        viewModel.filteredApps.observe(viewLifecycleOwner) { apps ->
            adapter.updateItems(applyFilter(apps))
        }

        // Search
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setSearchQuery(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Select all toggle
        var allSelected = true
        btnSelectAll.setOnClickListener {
            allSelected = !allSelected
            adapter.selectAll(allSelected)
            btnSelectAll.text = if (allSelected) "Bỏ chọn" else "Tất cả"
            viewModel.onSelectAllChanged(adapter.getSelectedApps())
        }

        // Filter chips
        chipAll.setOnClickListener { setFilter("all", chipAll, chipUser, chipSystem) }
        chipUser.setOnClickListener { setFilter("user", chipAll, chipUser, chipSystem) }
        chipSystem.setOnClickListener { setFilter("system", chipAll, chipUser, chipSystem) }
    }

    private fun setFilter(type: String, chipAll: TextView, chipUser: TextView, chipSystem: TextView) {
        filterType = type
        // Update chip visuals
        val activeColor = 0xFF0D1117.toInt()
        val inactiveColor = 0xFFFFFFFF.toInt()
        chipAll.setTextColor(if (type == "all") activeColor else inactiveColor)
        chipUser.setTextColor(if (type == "user") activeColor else inactiveColor)
        chipSystem.setTextColor(if (type == "system") activeColor else inactiveColor)
        chipAll.setBackgroundResource(if (type == "all") R.drawable.bg_kill_button else R.drawable.bg_card)
        chipUser.setBackgroundResource(if (type == "user") R.drawable.bg_kill_button else R.drawable.bg_card)
        chipSystem.setBackgroundResource(if (type == "system") R.drawable.bg_kill_button else R.drawable.bg_card)

        viewModel.filteredApps.value?.let { apps ->
            adapter.updateItems(applyFilter(apps))
        }
    }

    private fun applyFilter(apps: List<AppInfo>): List<AppInfo> {
        return when (filterType) {
            "user" -> apps.filter { !it.isSystemApp }
            "system" -> apps.filter { it.isSystemApp }
            else -> apps
        }
    }

    fun getSelectedApps(): List<AppInfo> = adapter.getSelectedApps()
}
