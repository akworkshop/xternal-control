package com.xternal.control

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DesktopIconsAdapter(
    apps: List<AppInfo>,
    private val onItemClick: (AppInfo) -> Unit,
    private val onItemLongClick: ((AppInfo) -> Unit)? = null
) : RecyclerView.Adapter<DesktopIconsAdapter.ViewHolder>() {

    private var appList: MutableList<AppInfo> = apps.toMutableList()
    private var iconSizeMode: String = "small"

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivDesktopIcon: ImageView = view.findViewById(R.id.ivDesktopIcon)
        val tvDesktopAppName: TextView = view.findViewById(R.id.tvDesktopAppName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_desktop_icon, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = appList[position]
        val density = holder.itemView.resources.displayMetrics.density

        val (cellWidthDp, cellHeightDp, iconSizeDp, textSizeSp) = when (iconSizeMode) {
            "large" -> arrayOf(98, 106, 60, 12)
            "medium" -> arrayOf(82, 88, 48, 11)
            else -> arrayOf(66, 72, 36, 10)
        }

        holder.itemView.layoutParams = holder.itemView.layoutParams?.apply {
            width = (cellWidthDp * density).toInt()
            height = (cellHeightDp * density).toInt()
        }
        holder.ivDesktopIcon.layoutParams = holder.ivDesktopIcon.layoutParams?.apply {
            width = (iconSizeDp * density).toInt()
            height = (iconSizeDp * density).toInt()
        }
        holder.tvDesktopAppName.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (iconSizeMode == "small") 9.5f else textSizeSp.toFloat())

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
        if (onItemLongClick != null) {
            holder.itemView.setOnLongClickListener {
                onItemLongClick.invoke(app)
                true
            }
        } else {
            holder.itemView.setOnLongClickListener(null)
            holder.itemView.isLongClickable = false
        }
    }

    override fun getItemCount(): Int = appList.size

    fun setIconSizeMode(mode: String) {
        if (this.iconSizeMode != mode) {
            this.iconSizeMode = mode
            notifyDataSetChanged()
        }
    }

    fun updateData(newApps: List<AppInfo>) {
        this.appList = newApps.toMutableList()
        notifyDataSetChanged()
    }

    fun moveItem(fromPos: Int, toPos: Int): Boolean {
        if (fromPos in appList.indices && toPos in appList.indices && fromPos != toPos) {
            val item = appList.removeAt(fromPos)
            appList.add(toPos, item)
            notifyItemMoved(fromPos, toPos)
            return true
        }
        return false
    }

    fun getItems(): List<AppInfo> = appList
}
