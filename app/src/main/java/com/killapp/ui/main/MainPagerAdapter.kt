package com.killapp.ui.main

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.killapp.ui.exclude.ExcludeFragment

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    val appListFragment = AppListFragment()
    val excludeFragment = ExcludeFragment()

    override fun getItemCount() = 2

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> appListFragment
        1 -> excludeFragment
        else -> appListFragment
    }
}
