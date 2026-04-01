package com.killapp.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.killapp.model.AppInfo
import com.killapp.utils.AppLoader
import com.killapp.utils.PrefsManager
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _allApps = MutableLiveData<List<AppInfo>>()
    private val _filteredApps = MutableLiveData<List<AppInfo>>()
    val filteredApps: LiveData<List<AppInfo>> = _filteredApps

    private val _excludedApps = MutableLiveData<List<AppInfo>>()
    val excludedApps: LiveData<List<AppInfo>> = _excludedApps

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private var searchQuery = ""
    private var _selectedApps = mutableListOf<AppInfo>()

    fun loadApps() {
        _isLoading.value = true
        viewModelScope.launch {
            val apps = AppLoader.loadAllApps(getApplication())
            _allApps.value = apps
            applySearch()
            refreshExcluded()
            _isLoading.value = false
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery = query
        applySearch()
    }

    private fun applySearch() {
        val all = _allApps.value ?: return
        _filteredApps.value = if (searchQuery.isBlank()) all
        else all.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    fun toggleApp(app: AppInfo, selected: Boolean) {
        _allApps.value?.find { it.packageName == app.packageName }?.isSelected = selected
        if (selected) {
            if (_selectedApps.none { it.packageName == app.packageName })
                _selectedApps.add(app)
        } else {
            _selectedApps.removeAll { it.packageName == app.packageName }
        }
        // Lưu trạng thái checkbox ngay khi user thay đổi
        persistSelection()
    }

    fun onSelectAllChanged(selected: List<AppInfo>) {
        _selectedApps.clear()
        _selectedApps.addAll(selected)
        persistSelection()
    }

    private fun persistSelection() {
        val all = _allApps.value ?: return
        val selectedPkgs = all.filter { it.isSelected && !it.isExcluded }
                              .map { it.packageName }.toSet()
        PrefsManager.saveSelectedPackages(getApplication(), selectedPkgs)
    }

    fun getSelectedApps(): List<AppInfo> {
        val all = _allApps.value ?: return emptyList()
        // Dùng trực tiếp isSelected từ object (adapter sửa in-place),
        // không dùng _selectedApps vì list đó chỉ được cập nhật khi user
        // thủ công toggle — còn mặc định tất cả app đều isSelected=true
        // nhưng _selectedApps lại rỗng nên getSelectedApps() trả về rỗng.
        return all.filter {
            it.isSelected &&
            !it.isExcluded &&
            !PrefsManager.DEFAULT_EXCLUDED.contains(it.packageName)
        }
    }

    fun getExcludedAppsForCacheClean(): List<AppInfo> {
        val all = _allApps.value ?: return emptyList()
        return all.filter { it.isExcluded }
    }

    fun addToExclude(app: AppInfo) {
        PrefsManager.addExcluded(getApplication(), app.packageName)
        // Update in-memory state
        _allApps.value?.find { it.packageName == app.packageName }?.isExcluded = true
        applySearch()
        refreshExcluded()
    }

    fun removeFromExclude(app: AppInfo) {
        if (PrefsManager.DEFAULT_EXCLUDED.contains(app.packageName)) return
        PrefsManager.removeExcluded(getApplication(), app.packageName)
        _allApps.value?.find { it.packageName == app.packageName }?.isExcluded = false
        applySearch()
        refreshExcluded()
    }

    private fun refreshExcluded() {
        val all = _allApps.value ?: return
        _excludedApps.value = all.filter { it.isExcluded }
    }

    fun getTotalCount(): Int = _allApps.value?.size ?: 0
    fun getExcludedCount(): Int = _excludedApps.value?.size ?: 0
}
