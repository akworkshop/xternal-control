package com.xternal.control

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DesktopIconsAdapter(
    private var apps: List<AppInfo>,
    private val onItemClick: (AppInfo) -> Unit,
    private val onItemLongClick: ((AppInfo) -> Unit)? = null
) : RecyclerView.Adapter<DesktopIconsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivDesktopIcon: ImageView = view.findViewById(R.id.ivDesktopIcon)
        val tvDesktopAppName: TextView = view.findViewById(R.id.tvDesktopAppName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_desktop_icon, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.tvDesktopAppName.text = app.label
        holder.ivDesktopIcon.setImageDrawable(app.icon)
        if (app.isLocked) {
            val colorMatrix = android.graphics.ColorMatrix().apply { setSaturation(0f) }
            holder.ivDesktopIcon.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
            holder.tvDesktopAppName.setTextColor(0xFF8A92A6.toInt())
            holder.itemView.alpha = 0.38f
        } else {
            holder.ivDesktopIcon.clearColorFilter()
            holder.tvDesktopAppName.setTextColor(0xFFFFFFFF.toInt())
            holder.itemView.alpha = 1.0f
        }

        holder.itemView.setOnClickListener { onItemClick(app) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick?.invoke(app)
            true
        }
    }

    override fun getItemCount(): Int = apps.size

    fun updateData(newApps: List<AppInfo>) {
        this.apps = newApps
        notifyDataSetChanged()
    }
}
