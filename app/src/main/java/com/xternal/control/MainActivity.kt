package com.xternal.control

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.graphics.PixelFormat
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Display
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.ArrayList
import android.util.DisplayMetrics
import android.content.res.Configuration
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {

    private val OVERLAY_PERMISSION_REQ_CODE = 1234
    
    // UI Elements
    private lateinit var tvStatusBadge: TextView
    private lateinit var tvConnectionInfo: TextView
    private lateinit var btnSimulate: Button
    private lateinit var tvPermOverlay: TextView
    private lateinit var btnGrantOverlay: Button
    private lateinit var tvPermAccessibility: TextView
    private lateinit var btnGrantAccessibility: Button
    private lateinit var btnLeftClick: View
    private lateinit var btnRightClick: View
    private lateinit var rvAppsHorizontal: RecyclerView
    private lateinit var cvTrackpad: CardView
    private lateinit var tabLayout: TabLayout
    private lateinit var tabSetupContainer: View
    private lateinit var tabAppsContainer: View
    private lateinit var tabTrackpadContainer: View
    private lateinit var tvTrackpadInstruction: View
    private lateinit var viewCursorMirror: View
    private lateinit var simulationContainer: FrameLayout
    private lateinit var btnDonate: Button
    private var backPressedTime = 0L

    // Recycler Adapter
    private lateinit var appAdapter: AppListAdapter
    private var allApps: List<AppInfo> = ArrayList()
    private var recentPackages: ArrayList<String> = ArrayList()
    private var favouritePackages: ArrayList<String> = ArrayList()

    // Display Management
    private lateinit var displayManager: DisplayManager
    private var externalDisplayId: Int = -1
    private var isSimulating: Boolean = false

    // Trackpad gestures state
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downTime = 0L
    private var isMultiTouch = false
    private var lastScrollDistance = 0f
    
    // Throttled scroll variables for accessibility service
    private var lastScrollGestureTime = 0L
    private var accumulatedScrollDx = 0f
    private var accumulatedScrollDy = 0f
    
    // Pinch gesture state variables
    private var initialPinchDistance = 0f
    private var isPinchGesture = false
    private var lastPinchDistance = 0f

    // Simulated Glasses UI Views (when simulation mode is ON)
    private var simCursorX = 500f
    private var simCursorY = 300f
    private var simRoot: View? = null
    private var simCursor: View? = null
    private var simGrid: RecyclerView? = null
    private var simAppContainer: View? = null
    private var simBrowserApp: View? = null
    private var simNotesApp: View? = null
    private var simNotesArea: EditText? = null
    private var simContextMenu: CardView? = null
    private var simAppAdapter: AppListAdapter? = null
    private var simMapApp: View? = null
    private var simMapCoords: TextView? = null
    private var simExtNavBar: View? = null
    private var simMapZoomLevel = 1.0f
    private var appSearchQuery: String = ""
    private var originalBrightness: Float = -1f

    // System Overlay Cursor for Real Secondary Display
    private var overlayCursorView: ImageView? = null
    private var overlayWindowManager: WindowManager? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var overlayCursorX = 960f
    private var overlayCursorY = 540f
    private var externalDisplayWidth = 1920
    private var externalDisplayHeight = 1080

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize display manager
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        
        loadListsFromPreferences()
        initViews()
        applyOrientationLayout()
        loadInstalledApps()
        checkExternalDisplays()
        setupDisplayListener()
        setupTrackpad()

        // Double back press to exit prompt
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (System.currentTimeMillis() - backPressedTime < 2000) {
                    finish()
                } else {
                    Toast.makeText(this@MainActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                    backPressedTime = System.currentTimeMillis()
                }
            }
        })

        checkAndShowDonationPrompt()
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun initViews() {
        tvStatusBadge = findViewById(R.id.tvStatusBadge)
        tvConnectionInfo = findViewById(R.id.tvConnectionInfo)
        btnSimulate = findViewById(R.id.btnSimulate)
        tvPermOverlay = findViewById(R.id.tvPermOverlay)
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)
        tvPermAccessibility = findViewById(R.id.tvPermAccessibility)
        btnGrantAccessibility = findViewById(R.id.btnGrantAccessibility)
        rvAppsHorizontal = findViewById(R.id.rvAppsHorizontal)
        cvTrackpad = findViewById(R.id.cvTrackpad)
        btnLeftClick = findViewById(R.id.btnLeftClick)
        btnRightClick = findViewById(R.id.btnRightClick)
        tvTrackpadInstruction = findViewById(R.id.tvTrackpadInstruction)
        viewCursorMirror = findViewById(R.id.viewCursorMirror)
        simulationContainer = findViewById(R.id.simulationContainer)

        btnDonate = findViewById(R.id.btnDonate)
        val cardDonation = findViewById<View>(R.id.cardDonation)
        if (BuildConfig.FLAVOR == "playstore") {
            cardDonation.visibility = View.GONE
        } else {
            btnDonate.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/akworkshop"))
                startActivity(intent)
            }
        }

        tabLayout = findViewById(R.id.controllerTabLayout)
        tabSetupContainer = findViewById(R.id.tabSetupContainer)
        tabAppsContainer = findViewById(R.id.tabAppsContainer)
        tabTrackpadContainer = findViewById(R.id.tabTrackpadContainer)

        val etAppSearch = findViewById<EditText>(R.id.etAppSearch)
        etAppSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                appSearchQuery = s?.toString() ?: ""
                sortAndRefreshAppLists()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Setup Tab Items
        tabLayout.addTab(tabLayout.newTab().setText("SETUP"))
        tabLayout.addTab(tabLayout.newTab().setText("APPS"))
        tabLayout.addTab(tabLayout.newTab().setText("REMOTE"))

        // Add Selection Listener
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> {
                        tabSetupContainer.visibility = View.VISIBLE
                        tabAppsContainer.visibility = View.GONE
                        tabTrackpadContainer.visibility = View.GONE
                    }
                    1 -> {
                        tabSetupContainer.visibility = View.GONE
                        tabAppsContainer.visibility = View.VISIBLE
                        tabTrackpadContainer.visibility = View.GONE
                    }
                    2 -> {
                        tabSetupContainer.visibility = View.GONE
                        tabAppsContainer.visibility = View.GONE
                        tabTrackpadContainer.visibility = View.VISIBLE
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        btnLeftClick.setOnClickListener {
            performLeftClick()
        }

        btnRightClick.setOnClickListener {
            performRightClick()
        }

        val btnTheaterMode = findViewById<View>(R.id.btnTheaterMode)
        val layoutTheaterModeOverlay = findViewById<View>(R.id.layoutTheaterModeOverlay)

        btnTheaterMode.setOnClickListener {
            enterTheaterMode()
        }

        var lastTheaterClickTime: Long = 0
        layoutTheaterModeOverlay.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTheaterClickTime < 300) {
                exitTheaterMode()
            }
            lastTheaterClickTime = currentTime
        }

        // Toggle Simulator Button Click
        btnSimulate.setOnClickListener {
            toggleSimulation()
        }

        // Grant Overlay Permission Click
        btnGrantOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
        }

        // Grant Accessibility Permission Click
        btnGrantAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Enable 'Xternal Control' service in the list", Toast.LENGTH_LONG).show()
        }

        // Bind controller bottom navigation bar remote buttons
        findViewById<View>(R.id.btnMainBack).setOnClickListener {
            val service = ControllerAccessibilityService.instance
            if (service != null && externalDisplayId != -1) {
                service.performBackOnDisplay(externalDisplayId, overlayCursorX, overlayCursorY)
            } else if (service != null) {
                service.performBackAction()
            } else {
                InteractionBridge.sendRightClick() // Routes BACK action to ExternalActivity
                Toast.makeText(this, "Enable Accessibility for system-wide Back control", Toast.LENGTH_SHORT).show()
            }
            if (isSimulating) {
                closeSimulatedApps()
            }
        }

        findViewById<View>(R.id.btnMainHome).setOnClickListener {
            if (externalDisplayId != -1) {
                try {
                    // Brings ExternalActivity home launcher back to foreground on the glasses
                    val options = ActivityOptions.makeBasic()
                    options.launchDisplayId = externalDisplayId
                    val intent = Intent(this, ExternalActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    }
                    startActivity(intent, options.toBundle())
                    Toast.makeText(this, "Glasses returned to Launcher Grid", Toast.LENGTH_SHORT).show()
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    Toast.makeText(this, "Security restriction: Cannot launch on secondary display", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Failed to return home: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            if (isSimulating) {
                closeSimulatedApps()
            }
        }


    }

    private fun checkPermissions() {
        if (Settings.canDrawOverlays(this)) {
            tvPermOverlay.text = "System Overlay: Granted"
            tvPermOverlay.setTextColor(ContextCompat.getColor(this, R.color.neon_emerald))
            btnGrantOverlay.visibility = View.GONE
        } else {
            tvPermOverlay.text = "System Overlay: Missing"
            tvPermOverlay.setTextColor(ContextCompat.getColor(this, R.color.neon_warning))
            btnGrantOverlay.visibility = View.VISIBLE
        }

        val isAccessibilityActive = ControllerAccessibilityService.instance != null
        if (isAccessibilityActive) {
            tvPermAccessibility.text = "Accessibility: Active"
            tvPermAccessibility.setTextColor(ContextCompat.getColor(this, R.color.neon_emerald))
            btnGrantAccessibility.visibility = View.GONE
        } else {
            tvPermAccessibility.text = "Accessibility: Inactive"
            tvPermAccessibility.setTextColor(ContextCompat.getColor(this, R.color.neon_warning))
            btnGrantAccessibility.visibility = View.VISIBLE
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
            if (info.activityInfo.packageName == packageName) continue // skip self launcher loop
            val appInfo = AppInfo(
                label = info.loadLabel(pm).toString(),
                packageName = info.activityInfo.packageName,
                icon = info.loadIcon(pm)
            )
            apps.add(appInfo)
        }
        allApps = apps.distinctBy { it.packageName }
        applyPlayStoreAppRestrictions()

        // Configure App Grid Adapter on Tablet/Phone
        appAdapter = AppListAdapter(
            allApps,
            isGridLayout = true,
            onItemClick = { app ->
                launchAppOnGlasses(app.packageName)
            },
            onItemLongClick = { app ->
                toggleAppFavourite(app)
            }
        )
        val columns = if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 3 else 5
        rvAppsHorizontal.layoutManager = GridLayoutManager(this, columns)
        rvAppsHorizontal.adapter = appAdapter
        sortAndRefreshAppLists()
    }

    private fun checkExternalDisplays() {
        val displays = displayManager.displays
        val externalDisplays = displays.filter { it.displayId != Display.DEFAULT_DISPLAY }

        if (externalDisplays.isNotEmpty()) {
            val firstExternal = externalDisplays.first()
            val newDisplayId = firstExternal.displayId
            
            if (externalDisplayId == newDisplayId) {
                // Already connected, verify trackpad mode and overlay cursor are enabled
                if (overlayCursorView == null && Settings.canDrawOverlays(this)) {
                    showOverlayCursor()
                }
                return
            }
            
            externalDisplayId = newDisplayId
            tvStatusBadge.text = "CONNECTED"
            tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            tvStatusBadge.setBackgroundResource(R.drawable.bg_rounded_search)
            tvStatusBadge.backgroundTintList = ContextCompat.getColorStateList(this, R.color.neon_emerald)
            tvConnectionInfo.text = "Glasses display connected: ID $externalDisplayId (${firstExternal.name})"
            
            // Activate trackpad
            activateTrackpadMode()
            // Switch to APPS tab automatically
            tabLayout.getTabAt(1)?.select()

            // Auto-launch ExternalActivity on target display
            try {
                val options = ActivityOptions.makeBasic()
                options.launchDisplayId = externalDisplayId
                val intent = Intent(this, ExternalActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                }
                startActivity(intent, options.toBundle())
            } catch (e: SecurityException) {
                e.printStackTrace()
                Toast.makeText(this, "Security restriction: Cannot launch External View on secondary display. Fallback to standard launch...", Toast.LENGTH_LONG).show()
                try {
                    val intent = Intent(this, ExternalActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to launch on glasses display: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            if (!isSimulating) {
                externalDisplayId = -1
                tvStatusBadge.text = "DISCONNECTED"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.neon_danger))
                tvStatusBadge.setBackgroundResource(R.drawable.bg_rounded_search)
                tvStatusBadge.backgroundTintList = null
                tvConnectionInfo.text = "No physical external display found."
                deactivateTrackpadMode()
                // Switch back to SETUP tab
                tabLayout.getTabAt(0)?.select()
            }
        }
    }

    private fun setupDisplayListener() {
        displayManager.registerDisplayListener(object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                checkExternalDisplays()
            }
            override fun onDisplayRemoved(displayId: Int) {
                checkExternalDisplays()
            }
            override fun onDisplayChanged(displayId: Int) {
                checkExternalDisplays()
            }
        }, null)
    }

    private fun toggleSimulation() {
        if (isSimulating) {
            // Turn off simulator
            isSimulating = false
            btnSimulate.text = "SIMULATE GLASSES"
            simulationContainer.visibility = View.GONE
            applyOrientationLayout()
            checkExternalDisplays()
        } else {
            // Turn on simulator side-by-side
            isSimulating = true
            btnSimulate.text = "STOP SIMULATOR"
            tvStatusBadge.text = "SIMULATING"
            tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            tvStatusBadge.setBackgroundResource(R.drawable.bg_rounded_search)
            tvStatusBadge.backgroundTintList = ContextCompat.getColorStateList(this, R.color.neon_cyan)
            tvConnectionInfo.text = "Simulated Glasses Panel Active on Right Side."
            
            // Enable trackpad
            activateTrackpadMode()
            // Switch to APPS tab automatically
            tabLayout.getTabAt(1)?.select()
            
            // Inflate external layout inside simulator container
            simulationContainer.visibility = View.VISIBLE
            applyOrientationLayout()
            simulationContainer.removeAllViews()
            val inflater = LayoutInflater.from(this)
            simRoot = inflater.inflate(R.layout.activity_external, simulationContainer, true)
            
            setupSimulatedGlassesUI()
        }
    }

    private fun activateTrackpadMode() {
        tvTrackpadInstruction.visibility = View.GONE
        showOverlayCursor()
    }

    private fun deactivateTrackpadMode() {
        tvTrackpadInstruction.visibility = View.VISIBLE
        viewCursorMirror.visibility = View.GONE
        hideOverlayCursor()
    }

    private fun showOverlayCursor() {
        if (externalDisplayId == -1) return
        if (!Settings.canDrawOverlays(this)) return

        runOnUiThread {
            try {
                if (overlayCursorView != null) {
                    hideOverlayCursor()
                }
                
                val display = displayManager.getDisplay(externalDisplayId) ?: return@runOnUiThread
                // Create display context from applicationContext to prevent binding to activity token
                val displayContext = applicationContext.createDisplayContext(display)
                overlayWindowManager = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

                // Query and store real screen resolution for cursor constraints
                val metrics = DisplayMetrics()
                display.getRealMetrics(metrics)
                externalDisplayWidth = metrics.widthPixels
                externalDisplayHeight = metrics.heightPixels

                // Set cursor coordinates to center of screen if not yet initialized
                if (overlayCursorX == 960f && overlayCursorY == 540f) {
                    overlayCursorX = externalDisplayWidth / 2f
                    overlayCursorY = externalDisplayHeight / 2f
                }

                overlayCursorView = ImageView(displayContext).apply {
                    setImageResource(R.drawable.bg_cursor)
                }

                overlayParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    val offset = dpToPx(12)
                    x = (overlayCursorX - offset).toInt()
                    y = (overlayCursorY - offset).toInt()
                }

                overlayWindowManager?.addView(overlayCursorView, overlayParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun hideOverlayCursor() {
        runOnUiThread {
            try {
                if (overlayCursorView != null && overlayWindowManager != null) {
                    overlayWindowManager?.removeView(overlayCursorView)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                overlayCursorView = null
                overlayWindowManager = null
                overlayParams = null
            }
        }
    }

    private fun updateOverlayCursor(dx: Float, dy: Float) {
        if (externalDisplayId == -1 || overlayWindowManager == null || overlayCursorView == null) return

        val scaleFactor = 1.5f
        overlayCursorX = (overlayCursorX + dx * scaleFactor).coerceIn(0f, externalDisplayWidth.toFloat())
        overlayCursorY = (overlayCursorY + dy * scaleFactor).coerceIn(0f, externalDisplayHeight.toFloat())

        overlayParams?.let { params ->
            val offset = dpToPx(12).toFloat()
            params.x = (overlayCursorX - offset).toInt()
            params.y = (overlayCursorY - offset).toInt()
            try {
                overlayWindowManager?.updateViewLayout(overlayCursorView, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun launchAppOnGlasses(packageName: String) {
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

        // Always route launch event to the glasses activity interface
        InteractionBridge.sendAppLaunch(packageName)

        // If physical external display is connected, also launch the real package on it
        if (externalDisplayId != -1) {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                try {
                    val options = ActivityOptions.makeBasic()
                    options.launchDisplayId = externalDisplayId
                    startActivity(launchIntent, options.toBundle())
                    Toast.makeText(this, "Opening real app on glasses display", Toast.LENGTH_SHORT).show()
                } catch (e: SecurityException) {
                    Toast.makeText(this, "Security restriction: Cannot launch this app on secondary display", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to launch app: ${e.message}", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
            }
        }
    }

    private fun getPointerDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
    }

    private fun setupTrackpad() {
        cvTrackpad.setOnTouchListener { _, event ->
            if (externalDisplayId == -1 && !isSimulating) {
                return@setOnTouchListener false
            }

            val x = event.x
            val y = event.y

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = x
                    startY = y
                    lastX = x
                    lastY = y
                    downTime = System.currentTimeMillis()
                    isMultiTouch = false
                    viewCursorMirror.visibility = View.VISIBLE
                    viewCursorMirror.x = x - (viewCursorMirror.width / 2)
                    viewCursorMirror.y = y - (viewCursorMirror.height / 2)
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    isMultiTouch = true
                    if (event.pointerCount == 2) {
                        lastScrollDistance = Math.abs(event.getY(0) - event.getY(1))
                        accumulatedScrollDx = 0f
                        accumulatedScrollDy = 0f
                        lastScrollGestureTime = System.currentTimeMillis()
                        
                        initialPinchDistance = getPointerDistance(event)
                        lastPinchDistance = initialPinchDistance
                        isPinchGesture = false
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isMultiTouch && event.pointerCount == 2) {
                        val currentDist = getPointerDistance(event)
                        val distDeltaFromInitial = Math.abs(currentDist - initialPinchDistance)

                        if (!isPinchGesture && distDeltaFromInitial > dpToPx(20)) {
                            isPinchGesture = true
                            lastPinchDistance = currentDist
                            accumulatedScrollDx = 0f
                            accumulatedScrollDy = 0f
                        }

                        if (isPinchGesture) {
                            val pinchDelta = currentDist - lastPinchDistance
                            if (Math.abs(pinchDelta) > dpToPx(15)) {
                                val isZoomIn = pinchDelta > 0
                                InteractionBridge.sendZoom(isZoomIn)
                                if (isSimulating) handleSimulatedZoom(isZoomIn)

                                val service = ControllerAccessibilityService.instance
                                if (externalDisplayId != -1 && service != null) {
                                    service.dispatchZoom(externalDisplayId, overlayCursorX, overlayCursorY, isZoomIn)
                                }
                                lastPinchDistance = currentDist
                            }
                        } else {
                            // Two-finger scroll drag
                            val scrollDx = (x - lastX) * 2.5f
                            val scrollDy = (y - lastY) * 2.5f
                            InteractionBridge.sendScroll(scrollDy)
                            if (isSimulating) handleSimulatedScroll(scrollDy)

                            // Accumulate scroll/swipe coordinates for accessibility injection
                            accumulatedScrollDx += scrollDx
                            accumulatedScrollDy += scrollDy
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastScrollGestureTime > 80) {
                                val service = ControllerAccessibilityService.instance
                                if (externalDisplayId != -1 && service != null) {
                                    val absDx = Math.abs(accumulatedScrollDx)
                                    val absDy = Math.abs(accumulatedScrollDy)

                                    if (absDx > absDy && absDx > dpToPx(4)) {
                                        // Swipe horizontally
                                        val endX = (overlayCursorX + accumulatedScrollDx * 1.5f).coerceIn(0f, externalDisplayWidth.toFloat())
                                        service.dispatchScroll(externalDisplayId, overlayCursorX, overlayCursorY, endX, overlayCursorY)
                                        accumulatedScrollDx = 0f
                                        accumulatedScrollDy = 0f
                                        lastScrollGestureTime = currentTime
                                    } else if (absDy > absDx && absDy > dpToPx(4)) {
                                        // Scroll vertically
                                        val endY = (overlayCursorY + accumulatedScrollDy * 1.5f).coerceIn(0f, externalDisplayHeight.toFloat())
                                        service.dispatchScroll(externalDisplayId, overlayCursorX, overlayCursorY, overlayCursorX, endY)
                                        accumulatedScrollDx = 0f
                                        accumulatedScrollDy = 0f
                                        lastScrollGestureTime = currentTime
                                    }
                                }
                            }
                        }
                    } else {
                        // Single finger movement
                        val dx = x - lastX
                        val dy = y - lastY
                        InteractionBridge.sendCursorMove(dx, dy)
                        if (isSimulating) moveSimulatedCursor(dx, dy)
                        updateOverlayCursor(dx, dy)
                        
                        viewCursorMirror.x = x - (viewCursorMirror.width / 2)
                        viewCursorMirror.y = y - (viewCursorMirror.height / 2)
                    }
                    lastX = x
                    lastY = y
                }
                MotionEvent.ACTION_UP -> {
                    viewCursorMirror.visibility = View.GONE
                    val duration = System.currentTimeMillis() - downTime
                    val totalDx = Math.abs(x - startX)
                    val totalDy = Math.abs(y - startY)
                    
                    if (!isMultiTouch && duration < 250 && totalDx < 15 && totalDy < 15) {
                        performLeftClick()
                    } else if (isMultiTouch) {
                        if (!isPinchGesture) {
                            // Dispatch any final scroll/swipe gesture remaining
                            val service = ControllerAccessibilityService.instance
                            if (externalDisplayId != -1 && service != null) {
                                val absDx = Math.abs(accumulatedScrollDx)
                                val absDy = Math.abs(accumulatedScrollDy)
                                if (absDx > absDy && absDx > dpToPx(5)) {
                                    val endX = (overlayCursorX + accumulatedScrollDx * 1.5f).coerceIn(0f, externalDisplayWidth.toFloat())
                                    service.dispatchScroll(externalDisplayId, overlayCursorX, overlayCursorY, endX, overlayCursorY)
                                } else if (absDy > absDx && absDy > dpToPx(5)) {
                                    val endY = (overlayCursorY + accumulatedScrollDy * 1.5f).coerceIn(0f, externalDisplayHeight.toFloat())
                                    service.dispatchScroll(externalDisplayId, overlayCursorX, overlayCursorY, overlayCursorX, endY)
                                }
                            }
                        }
                        accumulatedScrollDx = 0f
                        accumulatedScrollDy = 0f
                    }
                    isMultiTouch = false
                    isPinchGesture = false
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    // Detect two-finger tap (right click) when one of the fingers lifts
                    val duration = System.currentTimeMillis() - downTime
                    val absDx = Math.abs(accumulatedScrollDx)
                    val absDy = Math.abs(accumulatedScrollDy)
                    if (isMultiTouch && !isPinchGesture && duration < 300 && absDx < dpToPx(8) && absDy < dpToPx(8)) {
                        performRightClick()
                    } else if (isMultiTouch && !isPinchGesture) {
                        // Dispatch scroll gesture on finger lift
                        val service = ControllerAccessibilityService.instance
                        if (externalDisplayId != -1 && service != null) {
                            if (absDx > absDy && absDx > dpToPx(5)) {
                                val endX = (overlayCursorX + accumulatedScrollDx * 1.5f).coerceIn(0f, externalDisplayWidth.toFloat())
                                service.dispatchScroll(externalDisplayId, overlayCursorX, overlayCursorY, endX, overlayCursorY)
                                accumulatedScrollDx = 0f
                                accumulatedScrollDy = 0f
                                lastScrollGestureTime = System.currentTimeMillis()
                            } else if (absDy > absDx && absDy > dpToPx(5)) {
                                val endY = (overlayCursorY + accumulatedScrollDy * 1.5f).coerceIn(0f, externalDisplayHeight.toFloat())
                                service.dispatchScroll(externalDisplayId, overlayCursorX, overlayCursorY, overlayCursorX, endY)
                                accumulatedScrollDx = 0f
                                accumulatedScrollDy = 0f
                                lastScrollGestureTime = System.currentTimeMillis()
                            }
                        }
                    }
                }
            }
            true
        }
    }


    // --- SIMULATED GLASSES INTERACTIVE EVENT HANDLERS ---
    private fun setupSimulatedGlassesUI() {
        simRoot?.let { root ->
            simCursor = root.findViewById(R.id.ivCursor)
            simGrid = root.findViewById(R.id.rvAppsGrid)
            simAppContainer = root.findViewById(R.id.virtualAppContainer)
            simBrowserApp = root.findViewById(R.id.layoutBrowserApp)
            simNotesApp = root.findViewById(R.id.layoutNotesApp)
            simNotesArea = root.findViewById(R.id.etNotesArea)
            simContextMenu = root.findViewById(R.id.cvContextMenu)
            simMapApp = root.findViewById(R.id.layoutMapApp)
            simMapCoords = root.findViewById(R.id.tvMapCoords)
            simExtNavBar = root.findViewById(R.id.cvExtNavBar)

            // Setup app grid adapter
            simAppAdapter = AppListAdapter(
                allApps,
                isGridLayout = true,
                onItemClick = { app ->
                    launchSimulatedApp(app.packageName)
                },
                onItemLongClick = { app ->
                    toggleAppFavourite(app)
                }
            )
            simGrid?.layoutManager = GridLayoutManager(this, 3) // 3 columns for side container width
            simGrid?.adapter = simAppAdapter
            sortAndRefreshAppLists()

            // Center initial cursor
            simCursor?.post {
                simCursorX = root.width / 2f
                simCursorY = root.height / 2f
                simCursor?.x = simCursorX - simCursor!!.width / 2f
                simCursor?.y = simCursorY - simCursor!!.height / 2f
            }

            // Bind click listeners for simulated app Close buttons
            root.findViewById<View>(R.id.btnBrowserClose).setOnClickListener {
                closeSimulatedApps()
            }
            root.findViewById<View>(R.id.btnNotesClose).setOnClickListener {
                closeSimulatedApps()
            }
            root.findViewById<View>(R.id.btnMapClose).setOnClickListener {
                closeSimulatedApps()
            }

            // Bind click listeners for simulated map zoom buttons
            root.findViewById<View>(R.id.btnMapZoomIn).setOnClickListener {
                simMapZoomLevel = (simMapZoomLevel + 0.2f).coerceAtMost(3.0f)
                updateSimulatedMapZoomText()
            }
            root.findViewById<View>(R.id.btnMapZoomOut).setOnClickListener {
                simMapZoomLevel = (simMapZoomLevel - 0.2f).coerceAtLeast(0.5f)
                updateSimulatedMapZoomText()
            }

            // Bind click listeners for floating Navbar back & home buttons
            root.findViewById<View>(R.id.btnExtNavBack).setOnClickListener {
                closeSimulatedApps()
            }
            root.findViewById<View>(R.id.btnExtNavHome).setOnClickListener {
                closeSimulatedApps()
            }

            // Bind click listeners for context menu items
            root.findViewById<View>(R.id.tvContextBack).setOnClickListener {
                simContextMenu?.visibility = View.GONE
                closeSimulatedApps()
            }
            root.findViewById<View>(R.id.tvContextHome).setOnClickListener {
                simContextMenu?.visibility = View.GONE
                closeSimulatedApps()
            }

            // Setup direct launch bridge for Simulation Mode
            InteractionBridge.appLaunchListener = { packageName ->
                launchSimulatedApp(packageName)
            }
        }
    }

    private fun moveSimulatedCursor(dx: Float, dy: Float) {
        simRoot?.let { root ->
            val scaleFactor = 1.2f // Accelerate coordinates mapping
            simCursorX = (simCursorX + dx * scaleFactor).coerceIn(0f, root.width.toFloat())
            simCursorY = (simCursorY + dy * scaleFactor).coerceIn(0f, root.height.toFloat())

            simCursor?.x = simCursorX - simCursor!!.width / 2f
            simCursor?.y = simCursorY - simCursor!!.height / 2f
        }
    }

    private fun triggerSimulatedClick() {
        simRoot?.let { root ->
            // Click visual effect (Ripple)
            val ripple = root.findViewById<View>(R.id.viewCursorRipple)
            ripple.x = simCursorX - dpToPx(20)
            ripple.y = simCursorY - dpToPx(20)
            ripple.alpha = 0.8f
            ripple.animate().alpha(0f).scaleX(1.5f).scaleY(1.5f).setDuration(200).withEndAction {
                ripple.scaleX = 1f
                ripple.scaleY = 1f
            }.start()

            // Dismiss context menu if clicking outside
            if (simContextMenu?.visibility == View.VISIBLE) {
                if (!isPointInsideView(simCursorX, simCursorY, simContextMenu!!)) {
                    simContextMenu?.visibility = View.GONE
                    return
                }
            }

            // Inject native touch event to simulated container at cursor coordinates
            injectSimulatedTouchEvent(simCursorX, simCursorY)
        }
    }

    private fun injectSimulatedTouchEvent(x: Float, y: Float) {
        simRoot?.let { root ->
            val downTime = SystemClock.uptimeMillis()
            val eventTime = SystemClock.uptimeMillis()
            
            // dispatchTouchEvent expects coordinates relative to the view it's dispatched on, which is x, y
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

            root.dispatchTouchEvent(downEvent)
            root.dispatchTouchEvent(upEvent)
            
            downEvent.recycle()
            upEvent.recycle()
        }
    }

    private fun triggerSimulatedRightClick() {
        simRoot?.let { root ->
            simContextMenu?.let { menu ->
                menu.x = simCursorX.coerceAtMost((root.width - menu.width).toFloat())
                menu.y = simCursorY.coerceAtMost((root.height - menu.height).toFloat())
                menu.visibility = View.VISIBLE
            }
        }
    }

    private fun handleSimulatedScroll(dy: Float) {
        // Scroll simulated browser if visible, otherwise scroll grid launcher
        if (simBrowserApp?.visibility == View.VISIBLE) {
            val scroller = simRoot?.findViewById<View>(R.id.browserScrollView)
            scroller?.scrollBy(0, -dy.toInt())
        } else if (simGrid?.visibility == View.VISIBLE) {
            simGrid?.scrollBy(0, -dy.toInt())
        }
    }

    private fun filterSimulatedApps(query: String) {
        val filtered = allApps.filter { it.label.contains(query, ignoreCase = true) }
        simAppAdapter?.updateData(filtered)
    }

    private fun launchSimulatedApp(packageName: String) {
        val app = allApps.find { it.packageName == packageName }
        if (app != null && app.isLocked) {
            showProUpgradeDialog()
            return
        }
        simAppContainer?.visibility = View.VISIBLE
        simExtNavBar?.visibility = View.VISIBLE
        closeSimulatedApps(keepNavBar = true)
        
        // Simulating launch of specific apps
        val nameLower = packageName.lowercase()
        if (nameLower.contains("map") || nameLower.hashCode() % 3 == 0) {
            simMapApp?.visibility = View.VISIBLE
            simMapZoomLevel = 1.0f
            updateSimulatedMapZoomText()
        } else if (nameLower.contains("chrome") || nameLower.contains("browser") || nameLower.contains("web") || nameLower.hashCode() % 2 == 0) {
            simBrowserApp?.visibility = View.VISIBLE
            simRoot?.findViewById<Button>(R.id.btnBrowserClickMe)?.setOnClickListener {
                Toast.makeText(this, "Simulated Web Link Clicked!", Toast.LENGTH_SHORT).show()
            }
        } else {
            simNotesApp?.visibility = View.VISIBLE
            simNotesArea?.requestFocus()
        }
        
        simContextMenu?.visibility = View.GONE
    }

    private fun closeSimulatedApps(keepNavBar: Boolean = false) {
        simBrowserApp?.visibility = View.GONE
        simNotesApp?.visibility = View.GONE
        simMapApp?.visibility = View.GONE
        if (!keepNavBar) {
            simAppContainer?.visibility = View.GONE
            simExtNavBar?.visibility = View.GONE
        }
        simContextMenu?.visibility = View.GONE
    }

    private fun updateSimulatedMapZoomText() {
        simMapCoords?.text = "SATELLITE POSITION: SECTOR 4-B\nZoom Level: ${String.format("%.1fx", simMapZoomLevel)}"
    }

    private fun handleSimulatedZoom(isZoomIn: Boolean) {
        if (simMapApp?.visibility == View.VISIBLE) {
            if (isZoomIn) {
                simMapZoomLevel = (simMapZoomLevel + 0.2f).coerceAtMost(3.0f)
            } else {
                simMapZoomLevel = (simMapZoomLevel - 0.2f).coerceAtLeast(0.5f)
            }
            updateSimulatedMapZoomText()
        }
    }

    private fun checkAndShowDonationPrompt() {
        if (BuildConfig.FLAVOR == "playstore") return
        val prefs = getSharedPreferences("XternalControlPrefs", Context.MODE_PRIVATE)
        val dontShow = prefs.getBoolean("dont_show_donation", false)
        if (dontShow) return

        val launchCount = prefs.getInt("launch_count", 0) + 1
        prefs.edit().putInt("launch_count", launchCount).apply()

        // Show prompt on the 3rd launch, and then every 7 launches to keep it polite
        if (launchCount == 3 || (launchCount > 3 && (launchCount - 3) % 7 == 0)) {
            showDonationDialog()
        }
    }

    private fun showDonationDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Support the Creator")
            .setMessage("Do you like using Xternal Control?\n\nIf this app has been useful to you, please consider supporting the creator. Your support makes a meaningful contribution to my family with a special needs child.\n\nEverything remains fully free to use!")
            .setPositiveButton("☕ Buy Me a Coffee") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/akworkshop"))
                startActivity(intent)
            }
            .setNegativeButton("Maybe Later", null)
            .setNeutralButton("Don't Show Again") { _, _ ->
                getSharedPreferences("XternalControlPrefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("dont_show_donation", true)
                    .apply()
            }
            .show()
    }

    private fun isPointInsideView(x: Float, y: Float, view: View): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val rootLocation = IntArray(2)
        simRoot?.getLocationOnScreen(rootLocation)
        val rx = location[0] - rootLocation[0]
        val ry = location[1] - rootLocation[1]
        return x >= rx && x <= rx + view.width && y >= ry && y <= ry + view.height
    }

    private fun performLeftClick() {
        val service = ControllerAccessibilityService.instance
        if (externalDisplayId != -1 && service != null) {
            service.dispatchClick(externalDisplayId, overlayCursorX, overlayCursorY)
        } else {
            InteractionBridge.sendClick()
        }
        if (isSimulating) {
            triggerSimulatedClick()
        }
    }

    private fun performRightClick() {
        InteractionBridge.sendRightClick()
        if (isSimulating) {
            triggerSimulatedRightClick()
        }
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
        // Filter apps based on search query
        val filteredApps = if (appSearchQuery.isEmpty()) {
            allApps
        } else {
            allApps.filter { it.label.contains(appSearchQuery, ignoreCase = true) }
        }

        // Set isFavourite status on filteredApps based on favouritePackages
        for (app in filteredApps) {
            app.isFavourite = favouritePackages.contains(app.packageName)
        }

        // Re-sort the app list:
        // 1. Unlocked trial apps first (Play Store flavor only)
        // 2. Favourites next (sorted alphabetically by label)
        // 3. Recents next (sorted by position in recentPackages list)
        // 4. The rest alphabetically by label
        val sortedApps = filteredApps.sortedWith(compareBy<AppInfo> { it.isLocked }
            .thenByDescending { it.isFavourite }
            .thenBy { app ->
                val index = recentPackages.indexOf(app.packageName)
                if (index != -1) index else Int.MAX_VALUE
            }
            .thenBy { it.label.lowercase() }
        )

        // Update adapter data
        appAdapter.updateData(sortedApps)
        simAppAdapter?.updateData(sortedApps)
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

    private fun enterTheaterMode() {
        val overlay = findViewById<View>(R.id.layoutTheaterModeOverlay)
        overlay.visibility = View.VISIBLE

        val lp = window.attributes
        originalBrightness = lp.screenBrightness

        lp.screenBrightness = 0.01f
        window.attributes = lp

        Toast.makeText(this, "Theater Mode Active (Double-tap to exit)", Toast.LENGTH_SHORT).show()
    }

    private fun exitTheaterMode() {
        val overlay = findViewById<View>(R.id.layoutTheaterModeOverlay)
        overlay.visibility = View.GONE

        val lp = window.attributes
        lp.screenBrightness = originalBrightness
        window.attributes = lp

        Toast.makeText(this, "Screen Restored", Toast.LENGTH_SHORT).show()
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun applyOrientationLayout() {
        val orientation = resources.configuration.orientation
        val rootLayout = findViewById<LinearLayout>(R.id.rootLayout)
        val controllerPanel = findViewById<View>(R.id.controllerPanel)
        val simulationContainer = findViewById<View>(R.id.simulationContainer)

        val cpParams = controllerPanel.layoutParams as LinearLayout.LayoutParams
        val simParams = simulationContainer.layoutParams as LinearLayout.LayoutParams

        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            rootLayout.orientation = LinearLayout.VERTICAL
            
            cpParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            cpParams.height = 0
            cpParams.weight = 1f
            
            simParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            simParams.height = 0
            simParams.weight = 1f
        } else {
            rootLayout.orientation = LinearLayout.HORIZONTAL
            
            cpParams.width = 0
            cpParams.height = LinearLayout.LayoutParams.MATCH_PARENT
            cpParams.weight = 1f
            
            simParams.width = 0
            simParams.height = LinearLayout.LayoutParams.MATCH_PARENT
            simParams.weight = 1.3f
        }
        controllerPanel.layoutParams = cpParams
        simulationContainer.layoutParams = simParams
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientationLayout()
        
        // Update column count in apps grid
        val columns = if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) 3 else 5
        if (rvAppsHorizontal.layoutManager is GridLayoutManager) {
            (rvAppsHorizontal.layoutManager as GridLayoutManager).spanCount = columns
            appAdapter.notifyDataSetChanged()
        }
    }
}
