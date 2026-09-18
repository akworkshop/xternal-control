package com.xternal.control

import android.app.ActivityOptions
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import com.xternal.control.billing.BillingManager
import com.xternal.control.billing.BillingManagerProvider
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

class ExternalActivity : AppCompatActivity() {

    // Root Containers
    private lateinit var rootContainer: FrameLayout
    private lateinit var launcherContainer: View
    private lateinit var ivExtBackground: ImageView

    // Desktop Elements (Windows Style)
    private lateinit var rvDesktopIcons: RecyclerView
    private lateinit var desktopAdapter: DesktopIconsAdapter
    private lateinit var cardDesktopSupport: View
    private lateinit var btnBuyMeCoffee: View
    private lateinit var billingManager: BillingManager

    // Start Menu Elements
    private lateinit var layoutStartMenu: View
    private lateinit var etStartMenuSearch: EditText
    private lateinit var rvStartMenuApps: RecyclerView
    private lateinit var startMenuAdapter: StartMenuAppsAdapter
    private lateinit var btnStartGuide: View
    private lateinit var btnCloseStartMenu: View

    // Taskbar Elements
    private lateinit var layoutTaskbar: View
    private lateinit var btnStartMenu: View
    private lateinit var rvTaskbarApps: RecyclerView
    private lateinit var taskbarAdapter: TaskbarAppsAdapter

    // System Tray Elements
    private lateinit var tvBatteryPercent: TextView
    private lateinit var tvTrayTime: TextView
    private lateinit var tvTrayDate: TextView

    // Interactive Demo / Simulated Apps
    private lateinit var virtualAppContainer: View
    private lateinit var layoutBrowserApp: View
    private lateinit var layoutNotesApp: View
    private lateinit var etNotesArea: EditText
    private lateinit var layoutMapApp: View
    private lateinit var tvMapCoords: TextView
    private lateinit var cvExtNavBar: View
    private var mapZoomLevel = 1.0f

    // Cursor & Context Menu
    private lateinit var ivCursor: ImageView
    private lateinit var viewCursorRipple: View
    private lateinit var cvContextMenu: CardView

    // Data Lists
    private var allApps: List<AppInfo> = ArrayList()
    private var recentPackages: ArrayList<String> = ArrayList()
    private var favouritePackages: ArrayList<String> = ArrayList()
    private var sharedPrefsListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var appSearchQuery: String = ""

    // Bounds & coordinates
    private var screenWidth = 1920f
    private var screenHeight = 1080f
    private var cursorX = 960f
    private var cursorY = 540f
    private var isPipMode = false

    // System Tray Tick Handler
    private val systemTrayHandler = Handler(Looper.getMainLooper())
    private val systemTrayRunnable = object : Runnable {
        override fun run() {
            updateSystemTrayStatus()
            systemTrayHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        billingManager = BillingManagerProvider.getInstance(this)
        setContentView(R.layout.activity_external)

        loadListsFromPreferences()
        initViews()
        tryEnableHighestResolution()
        loadInstalledApps()

        billingManager.initialize {
            updateDesktopSupportCard()
            applyPlayStoreAppRestrictions()
            sortAndRefreshAppLists()
        }

        setupInteractionBridge()
        setupSharedPreferencesListener()
    }

    override fun onDestroy() {
        val prefs = getSharedPreferences("XternalControlPrefs", Context.MODE_PRIVATE)
        sharedPrefsListener?.let { prefs.unregisterOnSharedPreferenceChangeListener(it) }
        systemTrayHandler.removeCallbacks(systemTrayRunnable)
        if (::billingManager.isInitialized) {
            billingManager.destroy()
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (isPipMode) {
            closeVirtualApps()
            launcherContainer.visibility = View.GONE
            ivExtBackground.visibility = View.GONE
            rootContainer.setBackgroundColor(Color.BLACK)
        } else {
            returnToDesktop()
        }
        systemTrayHandler.post(systemTrayRunnable)
        loadListsFromPreferences()
        updateDesktopSupportCard()
        applyPlayStoreAppRestrictions()
        sortAndRefreshAppLists()
        if (!isPipMode) {
            applyBackgroundTheme()
        }
    }

    override fun onPause() {
        systemTrayHandler.removeCallbacks(systemTrayRunnable)
        super.onPause()
    }

    private fun initViews() {
        rootContainer = findViewById(R.id.rootContainer)
        launcherContainer = findViewById(R.id.launcherContainer)
        ivExtBackground = findViewById(R.id.ivExtBackground)

        // Desktop
        rvDesktopIcons = findViewById(R.id.rvDesktopIcons)
        cardDesktopSupport = findViewById(R.id.cardDesktopSupport)
        btnBuyMeCoffee = findViewById(R.id.btnBuyMeCoffee)

        updateDesktopSupportCard()

        // Start Menu
        layoutStartMenu = findViewById(R.id.layoutStartMenu)
        etStartMenuSearch = findViewById(R.id.etStartMenuSearch)
        rvStartMenuApps = findViewById(R.id.rvStartMenuApps)
        btnStartGuide = findViewById(R.id.btnStartGuide)
        btnCloseStartMenu = findViewById(R.id.btnCloseStartMenu)

        btnStartGuide.setOnClickListener {
            layoutStartMenu.visibility = View.GONE
            showGuideDialog()
        }
        btnCloseStartMenu.setOnClickListener {
            layoutStartMenu.visibility = View.GONE
        }

        etStartMenuSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                appSearchQuery = s?.toString() ?: ""
                sortAndRefreshAppLists()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Taskbar
        layoutTaskbar = findViewById(R.id.layoutTaskbar)
        btnStartMenu = findViewById(R.id.btnStartMenu)
        rvTaskbarApps = findViewById(R.id.rvTaskbarApps)

        btnStartMenu.setOnClickListener {
            toggleStartMenu()
        }

        // System Tray
        tvBatteryPercent = findViewById(R.id.tvBatteryPercent)
        tvTrayTime = findViewById(R.id.tvTrayTime)
        tvTrayDate = findViewById(R.id.tvTrayDate)

        // Simulated Apps & Overlays
        virtualAppContainer = findViewById(R.id.virtualAppContainer)
        layoutBrowserApp = findViewById(R.id.layoutBrowserApp)
        layoutNotesApp = findViewById(R.id.layoutNotesApp)
        etNotesArea = findViewById(R.id.etNotesArea)
        layoutMapApp = findViewById(R.id.layoutMapApp)
        tvMapCoords = findViewById(R.id.tvMapCoords)
        cvExtNavBar = findViewById(R.id.cvExtNavBar)

        ivCursor = findViewById(R.id.ivCursor)
        viewCursorRipple = findViewById(R.id.viewCursorRipple)
        cvContextMenu = findViewById(R.id.cvContextMenu)

        // Capture display dimensions once loaded & detect real hardware resolution
        rootContainer.post {
            screenWidth = rootContainer.width.toFloat()
            screenHeight = rootContainer.height.toFloat()
            cursorX = screenWidth / 2f
            cursorY = screenHeight / 2f
            ivCursor.visibility = View.GONE

            val tvExtHeader = findViewById<TextView>(R.id.tvExtHeader)
            if (tvExtHeader != null) {
                try {
                    val display = window?.decorView?.display
                    if (display != null) {
                        val mode = display.mode
                        val pWidth = mode.physicalWidth
                        val pHeight = mode.physicalHeight
                        val resLabel = if (pWidth >= 3840 || pHeight >= 3840) {
                            "4K $pWidth×$pHeight"
                        } else if (pWidth >= 2560 || pHeight >= 2560) {
                            "2K $pWidth×$pHeight"
                        } else if (pWidth >= 1920 || pHeight >= 1920) {
                            "1080P $pWidth×$pHeight"
                        } else {
                            "$pWidth×$pHeight"
                        }
                        tvExtHeader.text = resLabel
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Simulated App Close Buttons
        findViewById<View>(R.id.btnBrowserClose).setOnClickListener { closeVirtualApps() }
        findViewById<View>(R.id.btnNotesClose).setOnClickListener { closeVirtualApps() }
        findViewById<View>(R.id.btnMapClose).setOnClickListener { closeVirtualApps() }

        findViewById<View>(R.id.btnExtNavBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        findViewById<View>(R.id.btnExtNavHome).setOnClickListener { returnToDesktop() }

        findViewById<View>(R.id.tvContextBack).setOnClickListener {
            cvContextMenu.visibility = View.GONE
            onBackPressedDispatcher.onBackPressed()
        }
        findViewById<View>(R.id.tvContextHome).setOnClickListener {
            cvContextMenu.visibility = View.GONE
            returnToDesktop()
        }
    }

    private fun toggleStartMenu() {
        layoutStartMenu.visibility = if (layoutStartMenu.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun updateSystemTrayStatus() {
        try {
            // Time and Date
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val dateFormat = SimpleDateFormat("M/d/yyyy", Locale.getDefault())
            val now = Date()
            tvTrayTime.text = timeFormat.format(now)
            tvTrayDate.text = dateFormat.format(now)

            // Battery Status
            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 100
            tvBatteryPercent.text = "🔋 $batteryPct%"
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)

        val appList = ArrayList<AppInfo>()
        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            if (pkg == packageName) continue

            val label = info.loadLabel(pm).toString()
            val icon = info.loadIcon(pm)
            val isFav = favouritePackages.contains(pkg)

            appList.add(
                AppInfo(
                    packageName = pkg,
                    label = label,
                    icon = icon,
                    isFavourite = isFav
                )
            )
        }

        allApps = appList
        applyPlayStoreAppRestrictions()

        // 1. Desktop Icons Adapter: Arranges in columns from top to bottom, wrapping to multiple columns horizontally
        desktopAdapter = DesktopIconsAdapter(
            apps = emptyList(),
            onItemClick = { app -> launchApp(app.packageName) },
            onItemLongClick = { app -> toggleAppFavourite(app) }
        )
        rvDesktopIcons.layoutManager = GridLayoutManager(this, 5, RecyclerView.HORIZONTAL, false)
        rvDesktopIcons.adapter = desktopAdapter

        // 2. Taskbar Pinned Apps Adapter (Horizontal)
        taskbarAdapter = TaskbarAppsAdapter(
            apps = emptyList(),
            onItemClick = { app -> launchApp(app.packageName) },
            onItemLongClick = { app -> toggleAppFavourite(app) }
        )
        rvTaskbarApps.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvTaskbarApps.adapter = taskbarAdapter

        // 3. Start Menu Apps Adapter (Vertical list)
        startMenuAdapter = StartMenuAppsAdapter(
            apps = allApps,
            onItemClick = { app ->
                layoutStartMenu.visibility = View.GONE
                launchApp(app.packageName)
            },
            onItemLongClick = { app -> toggleAppFavourite(app) }
        )
        rvStartMenuApps.layoutManager = LinearLayoutManager(this)
        rvStartMenuApps.adapter = startMenuAdapter

        sortAndRefreshAppLists()
    }

    private fun isProActive(): Boolean {
        return if (::billingManager.isInitialized) billingManager.isProActive() else (BuildConfig.FLAVOR != "playstore")
    }

    private fun updateDesktopSupportCard() {
        val card = findViewById<View>(R.id.cardDesktopSupport) ?: return
        val btn = findViewById<View>(R.id.btnBuyMeCoffee) ?: return

        if (BuildConfig.FLAVOR == "github") {
            card.visibility = View.VISIBLE
            btn.setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/akworkshop"))
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            if (isProActive()) {
                card.visibility = View.GONE
            } else {
                card.visibility = View.VISIBLE
                val tvSupportMsg = findViewById<TextView>(R.id.tvSupportMsg)
                val tvSupportBtnText = findViewById<TextView>(R.id.tvSupportBtnText)
                tvSupportMsg?.text = "★ Unlock all apps & unlimited favourites with Pro!"
                tvSupportBtnText?.text = "⚡ UNLOCK PRO"
                btn.setOnClickListener {
                    try {
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("EXTRA_TRIGGER_PURCHASE", true)
                        }
                        startActivity(intent)
                        Toast.makeText(this, "Complete Pro purchase on your phone screen", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun applyPlayStoreAppRestrictions() {
        if (BuildConfig.FLAVOR != "playstore") {
            for (app in allApps) {
                app.isLocked = false
            }
            return
        }

        if (isProActive()) {
            for (app in allApps) {
                app.isLocked = false
            }
            return
        }

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

    private fun sortAndRefreshAppLists() {
        applyPlayStoreAppRestrictions()
        val updatedApps = allApps.map { app ->
            app.copy(isFavourite = favouritePackages.contains(app.packageName))
        }
        allApps = updatedApps

        // Favorite Apps for Desktop and Taskbar
        val favApps = allApps.filter { it.isFavourite }
        desktopAdapter.updateData(favApps)
        taskbarAdapter.updateData(favApps)

        // Filtered apps for Start Menu
        val filteredStartApps = if (appSearchQuery.isEmpty()) {
            allApps.sortedBy { it.label.lowercase(Locale.getDefault()) }
        } else {
            allApps.filter { it.label.contains(appSearchQuery, ignoreCase = true) }
                .sortedBy { it.label.lowercase(Locale.getDefault()) }
        }
        startMenuAdapter.updateData(filteredStartApps)
    }

    private fun toggleAppFavourite(app: AppInfo) {
        val pkg = app.packageName
        if (favouritePackages.contains(pkg)) {
            favouritePackages.remove(pkg)
            Toast.makeText(this, "Unpinned from Desktop & Taskbar: ${app.label}", Toast.LENGTH_SHORT).show()
        } else {
            if (!isProActive() && favouritePackages.size >= BillingManager.FREE_MAX_FAVOURITES) {
                Toast.makeText(this, "Free version limited to ${BillingManager.FREE_MAX_FAVOURITES} pinned apps. Upgrade to Pro on your phone!", Toast.LENGTH_LONG).show()
                return
            }
            favouritePackages.add(pkg)
            Toast.makeText(this, "Pinned to Desktop & Taskbar: ${app.label}", Toast.LENGTH_SHORT).show()
        }
        saveListsToPreferences()
        sortAndRefreshAppLists()
    }

    private fun launchApp(packageName: String) {
        layoutStartMenu.visibility = View.GONE

        if (!isProActive()) {
            val targetApp = allApps.find { it.packageName == packageName }
            if (targetApp != null && targetApp.isLocked) {
                Toast.makeText(this, "App locked in Free version. Upgrade to Pro on your phone!", Toast.LENGTH_LONG).show()
                return
            }
        }

        when (packageName) {
            "mock.browser" -> {
                closeVirtualApps(keepNavBar = true)
                launcherContainer.visibility = View.GONE
                ivExtBackground.visibility = View.GONE
                rootContainer.setBackgroundColor(Color.BLACK)
                virtualAppContainer.visibility = View.VISIBLE
                layoutBrowserApp.visibility = View.VISIBLE
                cvExtNavBar.visibility = View.VISIBLE
            }
            "mock.notes" -> {
                closeVirtualApps(keepNavBar = true)
                launcherContainer.visibility = View.GONE
                ivExtBackground.visibility = View.GONE
                rootContainer.setBackgroundColor(Color.BLACK)
                virtualAppContainer.visibility = View.VISIBLE
                layoutNotesApp.visibility = View.VISIBLE
                cvExtNavBar.visibility = View.VISIBLE
            }
            "mock.map" -> {
                closeVirtualApps(keepNavBar = true)
                launcherContainer.visibility = View.GONE
                ivExtBackground.visibility = View.GONE
                rootContainer.setBackgroundColor(Color.BLACK)
                virtualAppContainer.visibility = View.VISIBLE
                layoutMapApp.visibility = View.VISIBLE
                cvExtNavBar.visibility = View.VISIBLE
            }
            else -> {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    try {
                        val options = ActivityOptions.makeBasic()
                        val currentDisplay = window?.decorView?.display
                        val displayId = currentDisplay?.displayId ?: -1
                        if (currentDisplay != null) {
                            options.launchDisplayId = displayId
                        }
                        startActivity(launchIntent, options.toBundle())
                    } catch (e: Exception) {
                        e.printStackTrace()
                        startActivity(launchIntent)
                    }
                } else {
                    Toast.makeText(this, "App not found or cannot launch directly", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun returnToDesktop() {
        closeVirtualApps()
        layoutStartMenu.visibility = View.GONE

        launcherContainer.visibility = View.VISIBLE
        applyBackgroundTheme()
    }

    private fun setupInteractionBridge() {
        InteractionBridge.cursorMoveListener = { dx, dy ->
            val scaleFactor = 1.5f
            cursorX = (cursorX + dx * scaleFactor).coerceIn(0f, screenWidth)
            cursorY = (cursorY + dy * scaleFactor).coerceIn(0f, screenHeight)
        }

        InteractionBridge.clickListener = {
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

            if (cvContextMenu.visibility == View.VISIBLE) {
                if (!isPointInsideView(cursorX, cursorY, cvContextMenu)) {
                    cvContextMenu.visibility = View.GONE
                }
            }

            injectTouchEvent(cursorX, cursorY)
        }

        InteractionBridge.longClickListener = {
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

            injectLongTouchEvent(cursorX, cursorY)
        }

        InteractionBridge.rightClickListener = {
            cvContextMenu.x = cursorX.coerceAtMost(screenWidth - cvContextMenu.width)
            cvContextMenu.y = cursorY.coerceAtMost(screenHeight - cvContextMenu.height)
            cvContextMenu.visibility = View.VISIBLE
        }

        InteractionBridge.scrollListener = { scrollDy ->
            if (layoutBrowserApp.visibility == View.VISIBLE) {
                val scroller = findViewById<View>(R.id.browserScrollView)
                scroller.scrollBy(0, -scrollDy.toInt())
            } else if (layoutStartMenu.visibility == View.VISIBLE) {
                rvStartMenuApps.scrollBy(0, -scrollDy.toInt())
            }
        }

        InteractionBridge.textInputListener = { text ->
            if (layoutNotesApp.visibility == View.VISIBLE) {
                etNotesArea.append(text)
            } else if (layoutStartMenu.visibility == View.VISIBLE) {
                etStartMenuSearch.append(text)
            }
        }

        InteractionBridge.appLaunchListener = { packageName ->
            launchApp(packageName)
        }

        InteractionBridge.zoomListener = { isZoomIn ->
            if (layoutMapApp.visibility == View.VISIBLE) {
                if (isZoomIn) {
                    mapZoomLevel = (mapZoomLevel + 0.2f).coerceAtMost(4.0f)
                } else {
                    mapZoomLevel = (mapZoomLevel - 0.2f).coerceAtLeast(0.5f)
                }
                val ivMap = findViewById<View>(R.id.ivMapImage)
                ivMap.scaleX = mapZoomLevel
                ivMap.scaleY = mapZoomLevel
                updateMapZoomText()
            }
        }

        InteractionBridge.pipModeListener = { enabled ->
            isPipMode = enabled
            if (enabled) {
                launcherContainer.visibility = View.GONE
                ivExtBackground.visibility = View.GONE
                rootContainer.setBackgroundColor(Color.BLACK)
            } else {
                launcherContainer.visibility = View.VISIBLE
                applyBackgroundTheme()
            }
        }

        InteractionBridge.screenshotRequestListener = {
            captureScreenToGallery()
        }

        InteractionBridge.homeRequestListener = {
            returnToDesktop()
        }
    }

    private fun captureScreenToGallery() {
        try {
            val width = if (rootContainer.width > 0) rootContainer.width else (screenWidth.toInt().takeIf { it > 0 } ?: 1920)
            val height = if (rootContainer.height > 0) rootContainer.height else (screenHeight.toInt().takeIf { it > 0 } ?: 1080)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Draw full view hierarchy directly onto software canvas
            rootContainer.draw(canvas)
            saveBitmapToGallery(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Screenshot error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        try {
            val filename = "Xternal_Screenshot_${System.currentTimeMillis()}.png"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/XternalControl")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    contentResolver.update(uri, contentValues, null, null)
                }
                Toast.makeText(this, "📸 Screenshot saved to Pictures/XternalControl!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save screenshot: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyBackgroundTheme() {
        val prefs = getSharedPreferences("XternalControlPrefs", Context.MODE_PRIVATE)
        val bgType = prefs.getString("glasses_bg_type", "default") ?: "default"

        when (bgType) {
            "image" -> {
                val wallpaperFile = File(filesDir, "glasses_wallpaper.png")
                if (wallpaperFile.exists()) {
                    val bmp = android.graphics.BitmapFactory.decodeFile(wallpaperFile.absolutePath)
                    ivExtBackground.setImageBitmap(bmp)
                    ivExtBackground.visibility = View.VISIBLE
                } else {
                    ivExtBackground.setImageResource(R.drawable.bg_default_wallpaper)
                    ivExtBackground.visibility = View.VISIBLE
                }
            }
            "color" -> {
                val hex = prefs.getString("glasses_bg_color", "#0A0B10") ?: "#0A0B10"
                try {
                    val parsedColor = Color.parseColor(hex)
                    ivExtBackground.setImageDrawable(null)
                    ivExtBackground.visibility = View.GONE
                    rootContainer.setBackgroundColor(parsedColor)
                } catch (e: Exception) {
                    ivExtBackground.visibility = View.GONE
                    rootContainer.setBackgroundColor(Color.BLACK)
                }
            }
            else -> {
                ivExtBackground.setImageResource(R.drawable.bg_default_wallpaper)
                ivExtBackground.visibility = View.VISIBLE
            }
        }
    }

    private fun setupSharedPreferencesListener() {
        val prefs = getSharedPreferences("XternalControlPrefs", Context.MODE_PRIVATE)
        sharedPrefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key?.startsWith("glasses_bg_") == true || key?.startsWith("wallpaper_") == true) {
                applyBackgroundTheme()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(sharedPrefsListener)
    }

    private fun showGuideDialog() {
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
        builder.setTitle("👓 Windows Desktop Guide & Tips")
        builder.setMessage(
            "🪟 Windows Desktop Controls:\n" +
            "• Taskbar: Click 'Start' to open the Start Menu search & app drawer.\n" +
            "• Pinned Apps: Pinned apps appear both on your Desktop & Taskbar.\n" +
            "• System Tray: Check live Wi-Fi, Battery %, Time & Notification flyout.\n" +
            "• Multitasking Blob: When an app is open, tap ⚡ on the right to switch apps in 1-click!\n\n" +
            "🖱️ Trackpad & Mouse:\n" +
            "• 1-Finger Tap: Left Click / Select\n" +
            "• Long Press (600ms): Pin / Unpin apps\n" +
            "• 2-Finger Drag: Scroll lists smoothly\n\n" +
            "📸 Screenshot Tool:\n" +
            "• Tap 📸 on your phone controller to capture and save the external glasses display!"
        )
        builder.setPositiveButton("GOT IT") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun tryEnableHighestResolution() {
        try {
            val display = window?.decorView?.display ?: return
            val modes = display.supportedModes
            if (modes != null && modes.isNotEmpty()) {
                val maxMode = modes.maxByOrNull { it.physicalWidth * it.physicalHeight }
                if (maxMode != null && maxMode.modeId != display.mode.modeId) {
                    val lp = window.attributes
                    lp.preferredDisplayModeId = maxMode.modeId
                    window.attributes = lp
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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

    private fun findViewAt(view: View, x: Float, y: Float): View? {
        if (view.visibility != View.VISIBLE) return null
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val rx = x - location[0]
        val ry = y - location[1]
        if (rx < 0 || rx > view.width || ry < 0 || ry > view.height) return null
        if (view is ViewGroup) {
            for (i in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(i)
                val found = findViewAt(child, x, y)
                if (found != null) return found
            }
        }
        return view
    }

    private fun injectLongTouchEvent(x: Float, y: Float) {
        var view = findViewAt(rootContainer, x, y)
        while (view != null) {
            if (view.isLongClickable || view.hasOnClickListeners()) {
                if (view.performLongClick()) {
                    break
                }
            }
            view = view.parent as? View
        }
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
    }

    private fun isPointInsideView(x: Float, y: Float, view: View): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val viewX = location[0]
        val viewY = location[1]
        return (x >= viewX && x <= (viewX + view.width)) &&
                (y >= viewY && y <= (viewY + view.height))
    }

    private fun dpToPx(dp: Int): Float {
        return dp * resources.displayMetrics.density
    }

    private fun loadListsFromPreferences() {
        val prefs = getSharedPreferences("XternalControlPrefs", Context.MODE_PRIVATE)
        try {
            val favsStr = prefs.getString("favourite_packages", "") ?: ""
            favouritePackages.clear()
            if (favsStr.isNotEmpty()) {
                favouritePackages.addAll(favsStr.split(","))
            }
        } catch (e: Exception) {
            try {
                val favsSet = prefs.getStringSet("favourite_packages", emptySet()) ?: emptySet()
                favouritePackages.clear()
                favouritePackages.addAll(favsSet)
            } catch (e2: Exception) {
                favouritePackages.clear()
            }
        }

        try {
            val recentsStr = prefs.getString("recent_packages", "") ?: ""
            recentPackages.clear()
            if (recentsStr.isNotEmpty()) {
                recentPackages.addAll(recentsStr.split(","))
            }
        } catch (e: Exception) {
            try {
                val recentsSet = prefs.getStringSet("recent_packages", emptySet()) ?: emptySet()
                recentPackages.clear()
                recentPackages.addAll(recentsSet)
            } catch (e2: Exception) {
                recentPackages.clear()
            }
        }
    }

    private fun saveListsToPreferences() {
        val prefs = getSharedPreferences("XternalControlPrefs", Context.MODE_PRIVATE)
        val recentsStr = recentPackages.joinToString(",")
        val favsStr = favouritePackages.joinToString(",")
        prefs.edit()
            .putString("recent_packages", recentsStr)
            .putString("favourite_packages", favsStr)
            .apply()
    }
}
