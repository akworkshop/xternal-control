package com.xternal.control

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class StartMenuAppsAdapter(
    private var apps: List<AppInfo>,
    private val onItemClick: (AppInfo) -> Unit,
    private val onItemLongClick: ((AppInfo) -> Unit)? = null
) : RecyclerView.Adapter<StartMenuAppsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivStartMenuIcon: ImageView = view.findViewById(R.id.ivStartMenuIcon)
        val tvStartMenuAppName: TextView = view.findViewById(R.id.tvStartMenuAppName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_start_menu_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.tvStartMenuAppName.text = app.label
        holder.ivStartMenuIcon.setImageDrawable(app.icon)

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
