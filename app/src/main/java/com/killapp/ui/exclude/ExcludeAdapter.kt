package com.killapp.ui.exclude

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.killapp.R
import com.killapp.model.AppInfo
import com.killapp.utils.PrefsManager

class ExcludeAdapter(
    private var items: MutableList<AppInfo>,
    private val buttonLabel: String = "✕",
    private val buttonColor: Int = 0xFFFF4444.toInt(),
    var onAction: (AppInfo) -> Unit
) : RecyclerView.Adapter<ExcludeAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val name: TextView = view.findViewById(R.id.tvName)
        val pkg: TextView = view.findViewById(R.id.tvPkg)
        val btnAction: TextView = view.findViewById(R.id.btnRemove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_exclude, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = items[position]
        holder.icon.setImageDrawable(app.icon)
        holder.name.text = app.appName
        holder.pkg.text = app.packageName

        val isDefault = PrefsManager.DEFAULT_EXCLUDED.contains(app.packageName)
        if (isDefault) {
            holder.btnAction.visibility = View.INVISIBLE
        } else {
            holder.btnAction.visibility = View.VISIBLE
            holder.btnAction.text = buttonLabel
            holder.btnAction.setTextColor(buttonColor)
            holder.btnAction.setOnClickListener { onAction(app) }
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<AppInfo>) {
        items = newItems.toMutableList()
        notifyDataSetChanged()
    }
}
