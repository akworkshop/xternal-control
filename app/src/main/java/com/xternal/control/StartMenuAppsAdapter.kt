package com.xternal.control

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

sealed class StartMenuItem(val spanSize: Int) {
    data class SectionHeader(
        val title: String,
        val actionText: String?,
        val onActionClick: (() -> Unit)?
    ) : StartMenuItem(6)

    data class PinnedApp(val app: AppInfo) : StartMenuItem(1)

    data class RecentApp(val app: AppInfo, val subtitle: String = "Recently opened") : StartMenuItem(2)

    data class LetterHeader(val letter: String) : StartMenuItem(6)

    data class AllAppList(val app: AppInfo) : StartMenuItem(6)
}

class StartMenuAppsAdapter(
    private val onItemClick: (AppInfo) -> Unit,
    private val onItemLongClick: ((AppInfo) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SECTION_HEADER = 0
        private const val TYPE_PINNED_APP = 1
        private const val TYPE_RECENT_APP = 2
        private const val TYPE_LETTER_HEADER = 3
        private const val TYPE_ALL_APP_LIST = 4
    }

    private val items = ArrayList<StartMenuItem>()

    private var pinnedApps: List<AppInfo> = emptyList()
    private var recentApps: List<AppInfo> = emptyList()
    private var allApps: List<AppInfo> = emptyList()
    private var searchQuery: String = ""

    private var showAllPinned: Boolean = false
    private var showAllRecent: Boolean = false
    private var isAllGridView: Boolean = false

    fun getSpanSize(position: Int): Int {
        return items.getOrNull(position)?.spanSize ?: 6
    }

    fun resetExpansionState() {
        showAllPinned = false
        showAllRecent = false
    }

    fun updateSections(
        pinned: List<AppInfo>,
        recent: List<AppInfo>,
        all: List<AppInfo>,
        query: String
    ) {
        this.pinnedApps = pinned
        this.recentApps = recent
        this.allApps = all
        this.searchQuery = query.trim()
        rebuildItems()
    }

    private fun rebuildItems() {
        items.clear()

        if (searchQuery.isNotEmpty()) {
            items.add(
                StartMenuItem.SectionHeader(
                    title = "Search Results (${allApps.size})",
                    actionText = null,
                    onActionClick = null
                )
            )
            for (app in allApps) {
                items.add(StartMenuItem.AllAppList(app))
            }
            notifyDataSetChanged()
            return
        }

        // 1. Pinned Section
        val effectivePinned = if (pinnedApps.isNotEmpty()) pinnedApps else allApps.take(12)
        val pinnedActionText = if (showAllPinned) "Show less ⌃" else "Show all ⌄"
        items.add(
            StartMenuItem.SectionHeader(
                title = "Pinned",
                actionText = pinnedActionText,
                onActionClick = {
                    showAllPinned = !showAllPinned
                    rebuildItems()
                }
            )
        )
        val displayedPinned = if (showAllPinned) effectivePinned else effectivePinned.take(12)
        for (app in displayedPinned) {
            items.add(StartMenuItem.PinnedApp(app))
        }

        // 2. Recent Section
        val effectiveRecent = if (recentApps.isNotEmpty()) recentApps else allApps.take(6)
        if (effectiveRecent.isNotEmpty()) {
            val recentActionText = if (showAllRecent) "Show less ⌃" else "Show all ❯"
            items.add(
                StartMenuItem.SectionHeader(
                    title = "Recent",
                    actionText = recentActionText,
                    onActionClick = {
                        showAllRecent = !showAllRecent
                        rebuildItems()
                    }
                )
            )
            val displayedRecent = if (showAllRecent) effectiveRecent else effectiveRecent.take(6)
            for (app in displayedRecent) {
                items.add(StartMenuItem.RecentApp(app, "Recently opened"))
            }
        }

        // 3. All Section
        val viewModeText = if (isAllGridView) "View: Grid ⌄" else "View: List ⌄"
        items.add(
            StartMenuItem.SectionHeader(
                title = "All",
                actionText = viewModeText,
                onActionClick = {
                    isAllGridView = !isAllGridView
                    rebuildItems()
                }
            )
        )

        if (isAllGridView) {
            for (app in allApps) {
                items.add(StartMenuItem.PinnedApp(app))
            }
        } else {
            val grouped = allApps.groupBy { app ->
                val firstChar = app.label.trim().firstOrNull()?.uppercaseChar()
                if (firstChar != null && firstChar in 'A'..'Z') {
                    firstChar.toString()
                } else {
                    "#"
                }
            }
            val sortedKeys = grouped.keys.sortedWith(Comparator { a, b ->
                when {
                    a == "#" && b == "#" -> 0
                    a == "#" -> -1
                    b == "#" -> 1
                    else -> a.compareTo(b)
                }
            })
            for (letter in sortedKeys) {
                items.add(StartMenuItem.LetterHeader(letter))
                val appsInGroup = grouped[letter] ?: emptyList()
                for (app in appsInGroup) {
                    items.add(StartMenuItem.AllAppList(app))
                }
            }
        }

        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is StartMenuItem.SectionHeader -> TYPE_SECTION_HEADER
            is StartMenuItem.PinnedApp -> TYPE_PINNED_APP
            is StartMenuItem.RecentApp -> TYPE_RECENT_APP
            is StartMenuItem.LetterHeader -> TYPE_LETTER_HEADER
            is StartMenuItem.AllAppList -> TYPE_ALL_APP_LIST
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SECTION_HEADER -> HeaderViewHolder(
                inflater.inflate(R.layout.item_start_menu_header, parent, false)
            )
            TYPE_PINNED_APP -> PinnedViewHolder(
                inflater.inflate(R.layout.item_start_menu_pinned, parent, false)
            )
            TYPE_RECENT_APP -> RecentViewHolder(
                inflater.inflate(R.layout.item_start_menu_recent, parent, false)
            )
            TYPE_LETTER_HEADER -> LetterViewHolder(
                inflater.inflate(R.layout.item_start_menu_letter, parent, false)
            )
            else -> ListAppViewHolder(
                inflater.inflate(R.layout.item_start_menu_app, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is StartMenuItem.SectionHeader -> (holder as HeaderViewHolder).bind(item)
            is StartMenuItem.PinnedApp -> (holder as PinnedViewHolder).bind(item.app)
            is StartMenuItem.RecentApp -> (holder as RecentViewHolder).bind(item.app, item.subtitle)
            is StartMenuItem.LetterHeader -> (holder as LetterViewHolder).bind(item.letter)
            is StartMenuItem.AllAppList -> (holder as ListAppViewHolder).bind(item.app)
        }
    }

    override fun getItemCount(): Int = items.size

    private fun applyLockFilter(iconView: ImageView, titleView: TextView, rootView: View, isLocked: Boolean) {
        if (isLocked) {
            val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
            iconView.colorFilter = ColorMatrixColorFilter(colorMatrix)
            titleView.setTextColor(0xFF8A92A6.toInt())
            rootView.alpha = 0.38f
        } else {
            iconView.clearColorFilter()
            titleView.setTextColor(0xFFFFFFFF.toInt())
            rootView.alpha = 1.0f
        }
    }

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvSectionTitle: TextView = view.findViewById(R.id.tvSectionTitle)
        private val tvSectionAction: TextView = view.findViewById(R.id.tvSectionAction)

        fun bind(item: StartMenuItem.SectionHeader) {
            tvSectionTitle.text = item.title
            if (item.actionText != null && item.onActionClick != null) {
                tvSectionAction.visibility = View.VISIBLE
                tvSectionAction.text = item.actionText
                tvSectionAction.setOnClickListener { item.onActionClick.invoke() }
            } else {
                tvSectionAction.visibility = View.GONE
                tvSectionAction.setOnClickListener(null)
            }
        }
    }

    inner class PinnedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivIcon: ImageView = view.findViewById(R.id.ivStartMenuPinnedIcon)
        private val tvName: TextView = view.findViewById(R.id.tvStartMenuPinnedAppName)

        fun bind(app: AppInfo) {
            tvName.text = app.label
            ivIcon.setImageDrawable(app.icon)
            applyLockFilter(ivIcon, tvName, itemView, app.isLocked)
            itemView.setOnClickListener { onItemClick(app) }
            itemView.setOnLongClickListener {
                onItemLongClick?.invoke(app)
                true
            }
        }
    }

    inner class RecentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivIcon: ImageView = view.findViewById(R.id.ivStartMenuRecentIcon)
        private val tvName: TextView = view.findViewById(R.id.tvStartMenuRecentAppName)
        private val tvSub: TextView = view.findViewById(R.id.tvStartMenuRecentSub)

        fun bind(app: AppInfo, subtitle: String) {
            tvName.text = app.label
            tvSub.text = subtitle
            ivIcon.setImageDrawable(app.icon)
            applyLockFilter(ivIcon, tvName, itemView, app.isLocked)
            itemView.setOnClickListener { onItemClick(app) }
            itemView.setOnLongClickListener {
                onItemLongClick?.invoke(app)
                true
            }
        }
    }

    inner class LetterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvLetter: TextView = view.findViewById(R.id.tvStartMenuLetter)

        fun bind(letter: String) {
            tvLetter.text = letter
        }
    }

    inner class ListAppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivIcon: ImageView = view.findViewById(R.id.ivStartMenuIcon)
        private val tvName: TextView = view.findViewById(R.id.tvStartMenuAppName)

        fun bind(app: AppInfo) {
            tvName.text = app.label
            ivIcon.setImageDrawable(app.icon)
            applyLockFilter(ivIcon, tvName, itemView, app.isLocked)
            itemView.setOnClickListener { onItemClick(app) }
            itemView.setOnLongClickListener {
                onItemLongClick?.invoke(app)
                true
            }
        }
    }
}
