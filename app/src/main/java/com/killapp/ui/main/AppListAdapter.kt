package com.killapp.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.killapp.R
import com.killapp.model.AppInfo

class AppListAdapter(
    private var items: List<AppInfo>,
    private val onToggle: (AppInfo, Boolean) -> Unit
) : RecyclerView.Adapter<AppListAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivAppIcon)
        val name: TextView = view.findViewById(R.id.tvAppName)
        val pkg: TextView = view.findViewById(R.id.tvPackageName)
        val type: TextView = view.findViewById(R.id.tvAppType)
        val checkbox: CheckBox = view.findViewById(R.id.cbApp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = items[position]

        holder.icon.setImageDrawable(app.icon)
        holder.name.text = app.appName
        holder.pkg.text = app.packageName

        if (app.isExcluded) {
            holder.type.text = "LOẠI TRỪ"
            holder.type.setTextColor(0xFFFF00FF.toInt())
            holder.type.setBackgroundResource(0)
        } else if (app.isSystemApp) {
            holder.type.text = "HỆ THỐNG"
            holder.type.setTextColor(0xFFFFCC00.toInt())
        } else {
            holder.type.text = "USER"
            holder.type.setTextColor(0xFF00F5FF.toInt())
        }

        holder.checkbox.isChecked = app.isSelected && !app.isExcluded
        holder.checkbox.alpha = if (app.isExcluded) 0.3f else 1.0f

        holder.itemView.alpha = if (app.isExcluded) 0.6f else 1.0f

        holder.itemView.setOnClickListener {
            if (!app.isExcluded) {
                app.isSelected = !app.isSelected
                holder.checkbox.isChecked = app.isSelected
                onToggle(app, app.isSelected)
            }
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<AppInfo>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun selectAll(selected: Boolean) {
        items.forEach { if (!it.isExcluded) it.isSelected = selected }
        notifyDataSetChanged()
    }

    fun getSelectedApps(): List<AppInfo> = items.filter { it.isSelected && !it.isExcluded }
}
