package com.killapp.ui.exclude

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
import com.killapp.ui.main.MainViewModel

class ExcludeFragment : Fragment() {

    private lateinit var viewModel: MainViewModel
    private lateinit var rvExcluded: RecyclerView
    private lateinit var tvCount: TextView
    private lateinit var etSearch: EditText

    private lateinit var excludeAdapter: ExcludeAdapter
    private lateinit var searchAdapter: ExcludeAdapter
    private var allApps: List<AppInfo> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_exclude, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        tvCount = view.findViewById(R.id.tvExcludeCount)
        etSearch = view.findViewById(R.id.etExcludeSearch)
        rvExcluded = view.findViewById(R.id.rvExcluded)

        // Adapter hiện danh sách loại trừ — click ✕ = xóa khỏi loại trừ
        excludeAdapter = ExcludeAdapter(
            items = mutableListOf(),
            buttonLabel = "✕",
            buttonColor = 0xFFFF4444.toInt()
        ) { app -> viewModel.removeFromExclude(app) }

        // Adapter tìm kiếm để thêm — click + = thêm vào loại trừ
        searchAdapter = ExcludeAdapter(
            items = mutableListOf(),
            buttonLabel = "+",
            buttonColor = 0xFF00F5FF.toInt()
        ) { app ->
            viewModel.addToExclude(app)
            etSearch.text.clear()
        }

        rvExcluded.layoutManager = LinearLayoutManager(requireContext())
        rvExcluded.adapter = excludeAdapter

        viewModel.excludedApps.observe(viewLifecycleOwner) { excluded ->
            excludeAdapter.updateItems(excluded)
            tvCount.text = excluded.size.toString()
        }

        viewModel.filteredApps.observe(viewLifecycleOwner) { apps ->
            allApps = apps
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isBlank()) {
                    rvExcluded.adapter = excludeAdapter
                } else {
                    val results = allApps.filter {
                        !it.isExcluded &&
                        (it.appName.contains(query, ignoreCase = true) ||
                         it.packageName.contains(query, ignoreCase = true))
                    }
                    searchAdapter.updateItems(results)
                    rvExcluded.adapter = searchAdapter
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
}
