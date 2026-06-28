package com.xternal.control

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppListAdapter(
    private var apps: List<AppInfo>,
    private val isGridLayout: Boolean,
    private val onItemClick: (AppInfo) -> Unit,
    private val onItemLongClick: ((AppInfo) -> Unit)? = null
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAppIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvAppName: TextView = view.findViewById(R.id.tvAppName)
        val ivFavIndicator: ImageView? = view.findViewById(R.id.ivFavIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = if (isGridLayout) R.layout.item_app_grid else R.layout.item_app_horizontal
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.tvAppName.text = app.label
        holder.ivAppIcon.setImageDrawable(app.icon)
        
        if (app.isLocked) {
            holder.itemView.alpha = 0.4f
        } else {
            holder.itemView.alpha = 1.0f
        }
        
        holder.itemView.setOnClickListener { onItemClick(app) }
        
        holder.itemView.setOnLongClickListener {
            onItemLongClick?.invoke(app)
            true
        }
        
        holder.ivFavIndicator?.visibility = if (app.isFavourite) View.VISIBLE else View.GONE
    }

    override fun getItemCount(): Int = apps.size

    fun updateData(newApps: List<AppInfo>) {
        this.apps = newApps
        notifyDataSetChanged()
    }
}
