package com.xternal.control

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.ArrayList

class ExternalActivity : AppCompatActivity() {

    // UI elements
    private lateinit var rootContainer: FrameLayout
    private lateinit var launcherContainer: View
    private lateinit var rvAppsGrid: RecyclerView
    private lateinit var ivCursor: ImageView
    private lateinit var viewCursorRipple: View
    private lateinit var virtualAppContainer: View
    private lateinit var layoutBrowserApp: View
    private lateinit var layoutNotesApp: View
    private lateinit var etNotesArea: EditText
    private lateinit var cvContextMenu: CardView

    // Data
    private var allApps: List<AppInfo> = ArrayList()
    private lateinit var gridAdapter: AppListAdapter
    private var recentPackages: ArrayList<String> = ArrayList()
    private var favouritePackages: ArrayList<String> = ArrayList()
    private var sharedPrefsListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null
    
    // Bounds & coordinates
    private var screenWidth = 1920f
    private var screenHeight = 1080f
    private var cursorX = 960f
    private var cursorY = 540f
    
    // Additional views
    private lateinit var layoutMapApp: View
    private lateinit var tvMapCoords: TextView
    private lateinit var cvExtNavBar: View
    private var mapZoomLevel = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_external)

        loadListsFromPreferences()
        initViews()
        loadInstalledApps()
        setupInteractionBridge()
        setupSharedPreferencesListener()
    }

    override fun onDestroy() {
        val prefs = getSharedPreferences("XternalControlPrefs", Context.MODE_PRIVATE)
        sharedPrefsListener?.let { prefs.unregisterOnSharedPreferenceChangeListener(it) }
        super.onDestroy()
    }

    private fun initViews() {
        rootContainer = findViewById(R.id.rootContainer)
        launcherContainer = findViewById(R.id.launcherContainer)
        rvAppsGrid = findViewById(R.id.rvAppsGrid)
        ivCursor = findViewById(R.id.ivCursor)
        viewCursorRipple = findViewById(R.id.viewCursorRipple)
        virtualAppContainer = findViewById(R.id.virtualAppContainer)
        layoutBrowserApp = findViewById(R.id.layoutBrowserApp)
        layoutNotesApp = findViewById(R.id.layoutNotesApp)
        etNotesArea = findViewById(R.id.etNotesArea)
        cvContextMenu = findViewById(R.id.cvContextMenu)
        layoutMapApp = findViewById(R.id.layoutMapApp)
        tvMapCoords = findViewById(R.id.tvMapCoords)
        cvExtNavBar = findViewById(R.id.cvExtNavBar)

        // Capture display dimensions once loaded
        rootContainer.post {
            screenWidth = rootContainer.width.toFloat()
            screenHeight = rootContainer.height.toFloat()
            cursorX = screenWidth / 2f
            cursorY = screenHeight / 2f
            
            val displayId = window?.decorView?.display?.displayId ?: -1
            if (displayId != -1 && displayId != android.view.Display.DEFAULT_DISPLAY) {
                ivCursor.visibility = View.GONE
            } else {
                ivCursor.visibility = View.VISIBLE
                ivCursor.x = cursorX - ivCursor.width / 2f
                ivCursor.y = cursorY - ivCursor.height / 2f
            }
        }

        // Click listeners for simulated app Close buttons
        findViewById<View>(R.id.btnBrowserClose).setOnClickListener {
            closeVirtualApps()
        }
        findViewById<View>(R.id.btnNotesClose).setOnClickListener {
            closeVirtualApps()
        }
        findViewById<View>(R.id.btnMapClose).setOnClickListener {
            closeVirtualApps()
        }

        // Map Zoom Button Clicks
        findViewById<View>(R.id.btnMapZoomIn).setOnClickListener {
            mapZoomLevel = (mapZoomLevel + 0.2f).coerceAtMost(3.0f)
            updateMapZoomText()
        }
        findViewById<View>(R.id.btnMapZoomOut).setOnClickListener {
            mapZoomLevel = (mapZoomLevel - 0.2f).coerceAtLeast(0.5f)
            updateMapZoomText()
        }

        // Navigation Pill Clicks
        findViewById<View>(R.id.btnExtNavBack).setOnClickListener {
            closeVirtualApps()
        }
        findViewById<View>(R.id.btnExtNavHome).setOnClickListener {
            closeVirtualApps()
        }

        // Context Menu Item Click listeners
        findViewById<View>(R.id.tvContextBack).setOnClickListener {
            cvContextMenu.visibility = View.GONE
            closeVirtualApps()
        }
        findViewById<View>(R.id.tvContextHome).setOnClickListener {
            cvContextMenu.visibility = View.GONE
            closeVirtualApps()
        }
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        
        val apps = ArrayList<AppInfo>()
        for (info in resolveInfos) {
            if (info.activityInfo.packageName == packageName) continue
            val appInfo = AppInfo(
                label = info.loadLabel(pm).toString(),
                packageName = info.activityInfo.packageName,
                icon = info.loadIcon(pm)
            )
            apps.add(appInfo)
        }
        allApps = apps.distinctBy { it.packageName }
        applyPlayStoreAppRestrictions()

        // External launcher uses a 5-column grid layout
        gridAdapter = AppListAdapter(
            apps = allApps,
            isGridLayout = true,
            onItemClick = { app ->
                launchApp(app.packageName)
            },
            onItemLongClick = { app ->
                toggleAppFavourite(app)
            }
        )
        rvAppsGrid.layoutManager = GridLayoutManager(this, 5)
        rvAppsGrid.adapter = gridAdapter
        sortAndRefreshAppLists()
    }

    private fun loadListsFromPreferences() {
        val prefs = getSharedPreferences("XternalControlPrefs", Context.MODE_PRIVATE)
        
        val recentsStr = prefs.getString("recent_packages", "") ?: ""
        recentPackages.clear()
        if (recentsStr.isNotEmpty()) {
            recentPackages.addAll(recentsStr.split(","))
        }
        
        val favsStr = prefs.getString("favourite_packages", "") ?: ""
        favouritePackages.clear()
        if (favsStr.isNotEmpty()) {
            favouritePackages.addAll(favsStr.split(","))
        }
    }

    private fun sortAndRefreshAppLists() {
        // Set isFavourite status on allApps based on favouritePackages
        for (app in allApps) {
            app.isFavourite = favouritePackages.contains(app.packageName)
        }

        // Re-sort the app list:
        // 1. Unlocked trial apps first (Play Store flavor only)
        // 2. Favourites next (sorted alphabetically by label)
        // 3. Recents next (sorted by position in recentPackages list)
        // 4. The rest alphabetically by label
        val sortedApps = allApps.sortedWith(compareBy<AppInfo> { it.isLocked }
            .thenByDescending { it.isFavourite }
            .thenBy { app ->
                val index = recentPackages.indexOf(app.packageName)
                if (index != -1) index else Int.MAX_VALUE
            }
            .thenBy { it.label.lowercase() }
        )

        gridAdapter.updateData(sortedApps)
    }

    private fun saveListsToPreferences() {
        val prefs = getSharedPreferences("XternalControlPrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        val recentsStr = recentPackages.joinToString(",")
        editor.putString("recent_packages", recentsStr)
        
        val favsStr = favouritePackages.joinToString(",")
        editor.putString("favourite_packages", favsStr)
        
        editor.apply()
    }

    private fun toggleAppFavourite(app: AppInfo) {
        if (favouritePackages.contains(app.packageName)) {
            favouritePackages.remove(app.packageName)
            Toast.makeText(this, "${app.label} removed from Favourites", Toast.LENGTH_SHORT).show()
        } else {
            if (BuildConfig.FLAVOR == "playstore" && favouritePackages.size >= 3) {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Pro Feature")
                    .setMessage("Adding more than 3 favorite apps is a Pro feature.\n\nIn-app purchases are coming soon to unlock unlimited favorites!")
                    .setPositiveButton("OK", null)
                    .show()
                return
            }
            favouritePackages.add(app.packageName)
            Toast.makeText(this, "${app.label} added to Favourites", Toast.LENGTH_SHORT).show()
        }
        saveListsToPreferences()
        sortAndRefreshAppLists()
    }

    private fun setupSharedPreferencesListener() {
        val prefs = getSharedPreferences("XternalControlPrefs", Context.MODE_PRIVATE)
        sharedPrefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "recent_packages" || key == "favourite_packages") {
                loadListsFromPreferences()
                sortAndRefreshAppLists()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(sharedPrefsListener)
    }

    private fun setupInteractionBridge() {
        // 1. Move Cursor Listener
        InteractionBridge.cursorMoveListener = { dx, dy ->
            val scaleFactor = 1.5f
            cursorX = (cursorX + dx * scaleFactor).coerceIn(0f, screenWidth)
            cursorY = (cursorY + dy * scaleFactor).coerceIn(0f, screenHeight)
            ivCursor.x = cursorX - ivCursor.width / 2f
            ivCursor.y = cursorY - ivCursor.height / 2f
        }

        // 2. Click/Tap Listener
        InteractionBridge.clickListener = {
            // Ripple visual effect at cursor touch coordinate
            viewCursorRipple.x = cursorX - dpToPx(20)
            viewCursorRipple.y = cursorY - dpToPx(20)
            viewCursorRipple.alpha = 0.8f
            viewCursorRipple.animate()
                .alpha(0f)
                .scaleX(1.5f)
                .scaleY(1.5f)
                .setDuration(200)
                .withEndAction {
                    viewCursorRipple.scaleX = 1f
                    viewCursorRipple.scaleY = 1f
                }
                .start()

            // Handle Context Menu dismissal
            if (cvContextMenu.visibility == View.VISIBLE) {
                if (!isPointInsideView(cursorX, cursorY, cvContextMenu)) {
                    cvContextMenu.visibility = View.GONE
                }
            }

            // Synthesize Touch Event at cursor coordinate
            injectTouchEvent(cursorX, cursorY)
        }

        // 3. Right Click Listener
        InteractionBridge.rightClickListener = {
            cvContextMenu.x = cursorX.coerceAtMost(screenWidth - cvContextMenu.width)
            cvContextMenu.y = cursorY.coerceAtMost(screenHeight - cvContextMenu.height)
            cvContextMenu.visibility = View.VISIBLE
        }

        // 4. Two-finger Scroll Listener
        InteractionBridge.scrollListener = { scrollDy ->
            if (layoutBrowserApp.visibility == View.VISIBLE) {
                val scroller = findViewById<View>(R.id.browserScrollView)
                scroller.scrollBy(0, -scrollDy.toInt())
            } else if (launcherContainer.visibility == View.VISIBLE) {
                rvAppsGrid.scrollBy(0, -scrollDy.toInt())
            }
        }

        // 5. Keyboard Search Input Sync Listener
        InteractionBridge.textInputListener = { text ->
            // Mirror text directly to active notepad if open
            if (layoutNotesApp.visibility == View.VISIBLE) {
                etNotesArea.setText(text)
                etNotesArea.setSelection(text.length)
            }
        }

        // 6. Remote App Launch trigger
        InteractionBridge.appLaunchListener = { packageName ->
            launchApp(packageName)
        }

        // 7. Zoom Listener for Map zoom controls
        InteractionBridge.zoomListener = { isZoomIn ->
            if (layoutMapApp.visibility == View.VISIBLE) {
                if (isZoomIn) {
                    mapZoomLevel = (mapZoomLevel + 0.2f).coerceAtMost(3.0f)
                } else {
                    mapZoomLevel = (mapZoomLevel - 0.2f).coerceAtLeast(0.5f)
                }
                updateMapZoomText()
            }
        }
    }

    private fun filterApps(query: String) {
        val filtered = allApps.filter { it.label.contains(query, ignoreCase = true) }
        gridAdapter.updateData(filtered)
    }

    private fun launchApp(packageName: String) {
        val app = allApps.find { it.packageName == packageName }
        if (app != null && app.isLocked) {
            showProUpgradeDialog()
            return
        }
        // Track recents: move to start
        recentPackages.remove(packageName)
        recentPackages.add(0, packageName)
        saveListsToPreferences()
        sortAndRefreshAppLists()

        val pm = packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            try {
                val options = ActivityOptions.makeBasic()
                val displayId = window?.decorView?.display?.displayId ?: -1
                options.launchDisplayId = displayId
                startActivity(launchIntent, options.toBundle())
                Toast.makeText(this, "Launching real app: $packageName", Toast.LENGTH_SHORT).show()
            } catch (e: SecurityException) {
                Toast.makeText(this, "Security restriction: Cannot launch this app on secondary display", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to launch app: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        } else {
            // Fallback to simulated app dialog for prototype testing
            virtualAppContainer.visibility = View.VISIBLE
            cvExtNavBar.visibility = View.VISIBLE
            closeVirtualApps(keepNavBar = true)
            
            val nameLower = packageName.lowercase()
            if (nameLower.contains("map") || nameLower.hashCode() % 3 == 0) {
                layoutMapApp.visibility = View.VISIBLE
                mapZoomLevel = 1.0f
                updateMapZoomText()
            } else if (nameLower.contains("chrome") || nameLower.contains("browser") || nameLower.contains("web") || nameLower.hashCode() % 2 == 0) {
                layoutBrowserApp.visibility = View.VISIBLE
                findViewById<Button>(R.id.btnBrowserClickMe).setOnClickListener {
                    Toast.makeText(this, "Simulated Web Link Clicked!", Toast.LENGTH_SHORT).show()
                }
            } else {
                layoutNotesApp.visibility = View.VISIBLE
                etNotesArea.requestFocus()
            }
        }
        cvContextMenu.visibility = View.GONE
    }

    private fun closeVirtualApps(keepNavBar: Boolean = false) {
        layoutBrowserApp.visibility = View.GONE
        layoutNotesApp.visibility = View.GONE
        layoutMapApp.visibility = View.GONE
        if (!keepNavBar) {
            virtualAppContainer.visibility = View.GONE
            cvExtNavBar.visibility = View.GONE
        }
        cvContextMenu.visibility = View.GONE
    }

    private fun updateMapZoomText() {
        tvMapCoords.text = "SATELLITE POSITION: SECTOR 4-B\nZoom Level: ${String.format("%.1fx", mapZoomLevel)}"
    }

    private fun injectTouchEvent(x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        val eventTime = SystemClock.uptimeMillis()
        
        val properties = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        })
        
        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = 1f
        })

        val downEvent = MotionEvent.obtain(
            downTime, eventTime,
            MotionEvent.ACTION_DOWN, 1, properties, coords,
            0, 0, 1.0f, 1.0f, 0, 0, 0, 0
        )
        
        val upEvent = MotionEvent.obtain(
            downTime, eventTime + 30,
            MotionEvent.ACTION_UP, 1, properties, coords,
            0, 0, 1.0f, 1.0f, 0, 0, 0, 0
        )

        rootContainer.dispatchTouchEvent(downEvent)
        rootContainer.dispatchTouchEvent(upEvent)
        
        downEvent.recycle()
        upEvent.recycle()
    }

    private fun isPointInsideView(x: Float, y: Float, view: View): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val rx = location[0]
        val ry = location[1]
        return x >= rx && x <= rx + view.width && y >= ry && y <= ry + view.height
    }

    private fun applyPlayStoreAppRestrictions() {
        if (BuildConfig.FLAVOR != "playstore") return

        val preferredPackages = listOf(
            "com.google.android.youtube",
            "com.android.chrome",
            "com.google.android.apps.maps",
            "org.mozilla.firefox",
            "com.google.android.googlequicksearchbox",
            "com.android.settings"
        )

        val allowedPackages = allApps.filter { app ->
            preferredPackages.contains(app.packageName)
        }.map { it.packageName }.toMutableSet()

        if (allowedPackages.size < 4) {
            val otherApps = allApps.filter { !allowedPackages.contains(it.packageName) }
            for (app in otherApps) {
                if (allowedPackages.size >= 4) break
                allowedPackages.add(app.packageName)
            }
        }

        for (app in allApps) {
            app.isLocked = !allowedPackages.contains(app.packageName)
        }
    }

    private fun showProUpgradeDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Pro Feature")
            .setMessage("Launching this app is a Pro feature.\n\nIn-app purchases are coming soon to unlock unlimited apps!")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
