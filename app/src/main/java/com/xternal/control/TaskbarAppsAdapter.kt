package com.xternal.control

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class TaskbarAppsAdapter(
    private var apps: List<AppInfo>,
    private val onItemClick: (AppInfo) -> Unit,
    private val onItemLongClick: ((AppInfo) -> Unit)? = null
) : RecyclerView.Adapter<TaskbarAppsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivTaskbarIcon: ImageView = view.findViewById(R.id.ivTaskbarIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_taskbar_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.ivTaskbarIcon.setImageDrawable(app.icon)
        if (app.isLocked) {
            val colorMatrix = android.graphics.ColorMatrix().apply { setSaturation(0f) }
            holder.ivTaskbarIcon.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
            holder.itemView.alpha = 0.38f
        } else {
            holder.ivTaskbarIcon.clearColorFilter()
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
