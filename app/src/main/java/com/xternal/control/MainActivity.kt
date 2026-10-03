package com.xternal.control

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import com.xternal.control.billing.BillingManager
import com.xternal.control.billing.BillingManagerProvider
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.graphics.PixelFormat
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioAttributes
import com.xternal.control.media.MediaSessionRemoteManager
import com.xternal.control.media.MediaSessionState
import com.xternal.control.service.MediaNotificationListenerService
import android.view.KeyEvent
import android.view.HapticFeedbackConstants
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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.ArrayList
import android.util.DisplayMetrics
import android.content.res.Configuration
import com.google.android.material.tabs.TabLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream
import android.graphics.BitmapFactory
import android.graphics.Color

class MainActivity : AppCompatActivity() {

    private val OVERLAY_PERMISSION_REQ_CODE = 1234
    
    // UI Elements
    private lateinit var tvStatusBadge: TextView
    private lateinit var tvConnectionInfo: TextView
    private lateinit var tvPermOverlay: TextView
    private lateinit var btnGrantOverlay: Button
    private lateinit var tvPermAccessibility: TextView
    private lateinit var btnGrantAccessibility: Button
    private lateinit var cvZoomSlider: View
    private lateinit var viewSliderHandle: View
    private lateinit var tvSliderHint: View
    private lateinit var btnPipMode: View
    private lateinit var btnToggleCursor: View
    private lateinit var rvAppsHorizontal: RecyclerView
    private lateinit var cvTrackpad: CardView
    private lateinit var tabLayout: TabLayout
    private lateinit var tabSetupContainer: View
    private lateinit var tabAppsContainer: View
    private lateinit var tabTrackpadContainer: View
    private lateinit var tvTrackpadInstruction: View
    private lateinit var viewCursorMirror: View
    private lateinit var btnDonate: Button
    private lateinit var billingManager: BillingManager
    private var backPressedTime = 0L

    // Audio & Multimedia Management
    private lateinit var mediaRemoteManager: MediaSessionRemoteManager
    private lateinit var audioManager: AudioManager
    private var audioPlaybackCallback: AudioManager.AudioPlaybackCallback? = null
    private var cardMediaControls: View? = null
    private var tvPermNotification: TextView? = null
    private var btnGrantNotification: Button? = null
    private var tvMediaTitle: TextView? = null
    private var tvMediaArtist: TextView? = null
    private var tvMediaStatusDot: View? = null
    private var tvMediaStatusLabel: TextView? = null
    private var layoutMediaTimeline: View? = null
    private var tvMediaTimeElapsed: TextView? = null
    private var pbMediaProgress: ProgressBar? = null
    private var tvMediaTimeTotal: TextView? = null
    private var btnMediaPlayPause: ImageView? = null
    private var activeMediaPackage: String = ""
    private val mediaProgressHandler = Handler(Looper.getMainLooper())
    private val mediaProgressRunnable = object : Runnable {
        override fun run() {
            if (::audioManager.isInitialized && audioManager.isMusicActive) {
                updateMediaTimelineFromPlayer()
                mediaProgressHandler.postDelayed(this, 1000)
            }
        }
    }

    // Recycler Adapter
    private lateinit var appAdapter: AppListAdapter
    private var allApps: List<AppInfo> = ArrayList()
    private var recentPackages: ArrayList<String> = ArrayList()
    private var favouritePackages: ArrayList<String> = ArrayList()

    // Display Management
    private lateinit var displayManager: DisplayManager
    private var externalDisplayId: Int = -1

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
    private var hasDraggedOrScrolled = false
    private var twoFingerMode = 0
    private val zoomHandler = Handler(Looper.getMainLooper())
    private var zoomRunnable: Runnable? = null
    private var lastZoomTime = 0L
    private var isPipModeActive = false
    private var isDesktopActive = true
    private var lastLaunchedExternalPackage: String? = null
    private var isCursorWindowAttached = false
    private val cursorHideHandler = Handler(Looper.getMainLooper())
    private val cursorHideRunnable = Runnable { hideOverlayCursor() }
    private lateinit var pickWallpaperLauncher: ActivityResultLauncher<String>

    // Bluetooth Mouse & Trackpad Long-Press States
    private var lastMouseX = 0f
    private var lastMouseY = 0f
    private var isFirstMouseHover = true
    private val trackpadHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        cvTrackpad.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        hasDraggedOrScrolled = true
        InteractionBridge.sendLongClick()
        
        val service = ControllerAccessibilityService.instance
        if (externalDisplayId != -1 && service != null) {
            service.dispatchLongClick(externalDisplayId, overlayCursorX, overlayCursorY)
        }
    }

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
        billingManager = BillingManagerProvider.getInstance(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        val rootLayout = findViewById<View>(R.id.rootLayout)
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val defaultStatusBarHeight = if (resId > 0) resources.getDimensionPixelSize(resId) else 0
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        if (isPortrait && defaultStatusBarHeight > 0) {
            rootLayout.setPadding(0, defaultStatusBarHeight, 0, 0)
        }

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val portraitNow = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            val fallbackTop = if (portraitNow) defaultStatusBarHeight else 0
            val top = if (systemBars.top > 0) systemBars.top else fallbackTop
            v.setPadding(systemBars.left, top, systemBars.right, systemBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(rootLayout)

        // Initialize display manager
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        
        pickWallpaperLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { saveWallpaperFromUri(it) }
        }

        loadListsFromPreferences()
        initViews()
        applyOrientationLayout()
        loadInstalledApps()

        billingManager.initialize {
            updateProUi()
            applyPlayStoreAppRestrictions()
            sortAndRefreshAppLists()
            checkAndShowTrialExpiredDialog()
        }

        checkExternalDisplays()
        setupDisplayListener()
        setupTrackpad()
        setupBackgroundCustomizer()

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

        mediaRemoteManager = MediaSessionRemoteManager.init(this)
        checkAndShowOnboardingGuide()
        checkAndShowDonationPrompt()
        setupPlaystoreReviewCard()
        checkAndShowPlaystoreRatingPrompt()
        setupMediaControls()
        setupMediaDetection()
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        checkAndShowTrialExpiredDialog()
        updateProUi()
        applyPlayStoreAppRestrictions()
        sortAndRefreshAppLists()
        if (::mediaRemoteManager.isInitialized) {
            mediaRemoteManager.refreshActiveSessions()
        }
        if (::audioManager.isInitialized) {
            checkCurrentMediaPlayback()
        }
    }

    private fun initViews() {
        tvStatusBadge = findViewById(R.id.tvStatusBadge)
        tvConnectionInfo = findViewById(R.id.tvConnectionInfo)
        tvPermOverlay = findViewById(R.id.tvPermOverlay)
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)
        tvPermAccessibility = findViewById(R.id.tvPermAccessibility)
        btnGrantAccessibility = findViewById(R.id.btnGrantAccessibility)
        tvPermNotification = findViewById(R.id.tvPermNotification)
        btnGrantNotification = findViewById(R.id.btnGrantNotification)
        btnGrantNotification?.setOnClickListener {
            showNotificationAccessDisclosureDialog()
        }
        cardMediaControls = findViewById(R.id.cardMediaControls)
        rvAppsHorizontal = findViewById(R.id.rvAppsHorizontal)
        cvTrackpad = findViewById(R.id.cvTrackpad)
        cvZoomSlider = findViewById(R.id.cvZoomSlider)
        viewSliderHandle = findViewById(R.id.viewSliderHandle)
        tvSliderHint = findViewById(R.id.tvSliderHint)
        tvTrackpadInstruction = findViewById(R.id.tvTrackpadInstruction)
        viewCursorMirror = findViewById(R.id.viewCursorMirror)
        btnPipMode = findViewById(R.id.btnPipMode)
        btnToggleCursor = findViewById(R.id.btnToggleCursor)
        setupBridgeListeners()

        btnDonate = findViewById(R.id.btnDonate)
        updateProUi()

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

        var sliderStartX = 0f
        var sliderLastTriggerX = 0f

        cvZoomSlider.setOnTouchListener { _, event ->
            if (externalDisplayId == -1) {
                return@setOnTouchListener false
            }

            val x = event.x

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    sliderStartX = x
                    sliderLastTriggerX = x
                    tvSliderHint.animate().alpha(0f).setDuration(150).start()
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = x - sliderStartX
                    val maxTranslation = dpToPx(100).toFloat()
                    val constrainedTranslation = deltaX.coerceIn(-maxTranslation, maxTranslation)
                    viewSliderHandle.translationX = constrainedTranslation

                    val totalDx = x - sliderLastTriggerX
                    val threshold = dpToPx(16).toFloat()

                    if (Math.abs(totalDx) > threshold) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastZoomTime > 300) {
                            cvZoomSlider.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                            val isZoomIn = totalDx > 0
                            InteractionBridge.sendZoom(isZoomIn)
                            val service = ControllerAccessibilityService.instance
                            if (externalDisplayId != -1 && service != null) {
                                service.dispatchZoom(externalDisplayId, overlayCursorX, overlayCursorY, isZoomIn)
                            }
                            lastZoomTime = currentTime
                            sliderLastTriggerX = x
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    viewSliderHandle.animate().translationX(0f).setDuration(200).start()
                    tvSliderHint.animate().alpha(0.6f).setDuration(200).start()
                }
            }
            true
        }

        val btnTheaterMode = findViewById<View>(R.id.btnTheaterMode)
        val layoutTheaterModeOverlay = findViewById<View>(R.id.layoutTheaterModeOverlay)

        btnTheaterMode.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
            enterTheaterMode()
        }

        btnPipMode.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
            togglePipPassThrough()
        }

        btnToggleCursor.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
            // Instantly hide and completely detach the cursor overlay to let DRM play
            hideOverlayCursor()
            Toast.makeText(this, "DRM Play Active (Cursor hidden)", Toast.LENGTH_SHORT).show()
        }

        val btnScreenshot = findViewById<View>(R.id.btnScreenshot)
        btnScreenshot?.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
            val service = ControllerAccessibilityService.instance
            if (service != null && externalDisplayId != -1 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                Toast.makeText(this, "Capturing external screen...", Toast.LENGTH_SHORT).show()
                service.captureDisplayScreenshot(externalDisplayId) { bitmap ->
                    if (bitmap != null) {
                        saveScreenshotBitmapToGallery(bitmap)
                    } else {
                        InteractionBridge.sendScreenshotRequest()
                    }
                }
            } else {
                InteractionBridge.sendScreenshotRequest()
                Toast.makeText(this, "Capturing external screen...", Toast.LENGTH_SHORT).show()
            }
        }

        var lastTheaterClickTime: Long = 0
        layoutTheaterModeOverlay.setOnClickListener { v ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTheaterClickTime < 300) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                exitTheaterMode()
            }
            lastTheaterClickTime = currentTime
        }

        // Grant Overlay Permission Click
        btnGrantOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
        }

        // Grant Accessibility Permission Click with Google Play Prominent Disclosure
        btnGrantAccessibility.setOnClickListener {
            showAccessibilityDisclosureDialog()
        }

        // Bind controller bottom navigation bar remote buttons
        findViewById<View>(R.id.btnMainBack).setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
            val consumedByDesktopMenu = InteractionBridge.sendBackRequest()
            if (consumedByDesktopMenu) {
                return@setOnClickListener
            }

            val service = ControllerAccessibilityService.instance
            if (service != null && externalDisplayId != -1) {
                service.performBackOnDisplay(externalDisplayId)
            } else if (service != null) {
                service.performBackAction()
            }
        }

        findViewById<View>(R.id.btnMainHome).setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
            isDesktopActive = true
            updatePipButtonUi(false)
            InteractionBridge.sendPipMode(false)
            InteractionBridge.sendHomeRequest()
            if (externalDisplayId != -1) {
                try {
                    val options = ActivityOptions.makeBasic()
                    options.launchDisplayId = externalDisplayId
                    val intent = Intent(this, ExternalActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(intent, options.toBundle())
                    Toast.makeText(this, "Glasses returned to Desktop", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Hide physical mouse cursor on the phone screen
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            findViewById<View>(android.R.id.content)?.pointerIcon = 
                android.view.PointerIcon.getSystemIcon(this, android.view.PointerIcon.TYPE_NULL)
        }
    }

    private fun showAccessibilityDisclosureDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Accessibility Service Disclosure")
            .setMessage(
                "Xternal Control requires the AccessibilityServices API to provide remote control and navigation functionality for your connected external display (XR glasses, TV, or monitor).\n\n" +
                "How this service is used:\n" +
                "• Simulating cursor clicks, scrolling, and pinch-to-zoom gestures on external screens.\n" +
                "• Performing system navigation (Back and Home) inside third-party apps on the external display.\n\n" +
                "Data Safety & Privacy:\n" +
                "• Xternal Control DOES NOT collect, store, transmit, or share any personal or sensitive user data.\n" +
                "• No keystrokes, personal messages, or screen contents are monitored.\n" +
                "• All input operations execute locally on your device."
            )
            .setPositiveButton("Agree & Enable") { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
                Toast.makeText(this, "Find and enable 'Xternal Control' in the list", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Deny", null)
            .setCancelable(false)
            .show()
    }

    private fun showNotificationAccessDisclosureDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Media Remote Permission")
            .setMessage(
                "To control playback, sync track titles, and seek ±10s in media apps (YouTube, Spotify, SBS, etc.), Android requires 'Notification Access'.\n\n" +
                "Why Android requires this:\n" +
                "• Android's MediaSession architecture requires NotificationListenerService authorization to communicate with media players.\n" +
                "• It allows Xternal Control to detect active playback, display live progress, and send universal seek commands.\n\n" +
                "Privacy & Setup Tip:\n" +
                "• You DO NOT need to enable Conversations, Real-time chats, or Silent alerts!\n" +
                "• You can safely leave 'Conversations', 'Alerting', and 'Silent' UNTICKED or OFF in system settings. Only the main Notification Access switch is needed.\n" +
                "• Xternal Control never reads, saves, or replies to personal messages or chats."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                openNotificationAccessSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) {
            val action = event.actionMasked
            if (action == MotionEvent.ACTION_HOVER_MOVE || action == MotionEvent.ACTION_HOVER_ENTER) {
                val x = event.x
                val y = event.y
                if (!isFirstMouseHover) {
                    val dx = (x - lastMouseX) * 1.5f
                    val dy = (y - lastMouseY) * 1.5f
                    
                    cursorHideHandler.removeCallbacks(cursorHideRunnable)
                    showOverlayCursor()

                    InteractionBridge.sendCursorMove(dx, dy)
                    updateOverlayCursor(dx, dy)
                } else {
                    isFirstMouseHover = false
                }
                lastMouseX = x
                lastMouseY = y
                return true
            } else if (action == MotionEvent.ACTION_SCROLL) {
                val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                if (vScroll != 0f) {
                    val scrollDy = -vScroll * 120f
                    
                    cursorHideHandler.removeCallbacks(cursorHideRunnable)
                    showOverlayCursor()

                    InteractionBridge.sendScroll(scrollDy)
                    
                    val service = ControllerAccessibilityService.instance
                    if (externalDisplayId != -1 && service != null) {
                        val endY = (overlayCursorY - scrollDy).coerceIn(0f, externalDisplayHeight.toFloat())
                        service.dispatchScroll(externalDisplayId, overlayCursorX, overlayCursorY, overlayCursorX, endY)
                    }
                }
                return true
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) {
            val action = event.actionMasked
            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    downTime = System.currentTimeMillis()
                }
                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - downTime
                    if (duration >= 500) {
                        InteractionBridge.sendLongClick()
                        val service = ControllerAccessibilityService.instance
                        if (externalDisplayId != -1 && service != null) {
                            service.dispatchLongClick(externalDisplayId, overlayCursorX, overlayCursorY)
                        }
                    } else {
                        performLeftClick()
                    }
                }
            }
            return true
        }
        return super.dispatchTouchEvent(event)
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

        val isNotificationAccessGranted = MediaNotificationListenerService.isAccessGranted(this)
        if (isNotificationAccessGranted) {
            tvPermNotification?.text = "Media Remote: Active"
            tvPermNotification?.setTextColor(ContextCompat.getColor(this, R.color.neon_emerald))
            btnGrantNotification?.visibility = View.GONE
            if (::mediaRemoteManager.isInitialized) {
                mediaRemoteManager.setupSessionListenerIfAuthorized()
            }
        } else {
            tvPermNotification?.text = "Media Remote: Disabled"
            tvPermNotification?.setTextColor(ContextCompat.getColor(this, R.color.neon_warning))
            btnGrantNotification?.visibility = View.VISIBLE
        }
    }

    private fun openNotificationAccessSettings() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                    putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, MediaNotificationListenerService.getComponentName(this@MainActivity).flattenToString())
                }
            } else {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
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

            // Teardown stale trackpad and overlay context before binding to new display ID
            deactivateTrackpadMode()
            
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
            externalDisplayId = -1
            tvStatusBadge.text = "DISCONNECTED"
            tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.neon_danger))
            tvStatusBadge.setBackgroundResource(R.drawable.bg_rounded_search)
            tvStatusBadge.backgroundTintList = null
            tvConnectionInfo.text = "No physical external display found."
            deactivateTrackpadMode()
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

    private fun activateTrackpadMode() {
        tvTrackpadInstruction.visibility = View.GONE
        showOverlayCursor()
    }

    private fun deactivateTrackpadMode() {
        tvTrackpadInstruction.visibility = View.VISIBLE
        viewCursorMirror.visibility = View.GONE
        cursorHideHandler.removeCallbacks(cursorHideRunnable)
        hideOverlayCursor()
        runOnUiThread {
            overlayCursorView = null
            overlayWindowManager = null
            overlayParams = null
        }
    }

    private fun showOverlayCursor() {
        if (externalDisplayId == -1) return
        if (!Settings.canDrawOverlays(this)) return

        runOnUiThread {
            try {
                if (overlayCursorView == null) {
                    val display = displayManager.getDisplay(externalDisplayId) ?: return@runOnUiThread
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
                        val cursorDrawable = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_cursor)
                        if (cursorDrawable != null) {
                            setImageDrawable(cursorDrawable)
                        } else {
                            setImageResource(R.drawable.bg_cursor)
                        }
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
                        x = overlayCursorX.toInt()
                        y = overlayCursorY.toInt()
                    }
                }

                if (!isCursorWindowAttached && overlayCursorView != null && overlayParams != null) {
                    overlayWindowManager?.addView(overlayCursorView, overlayParams)
                    isCursorWindowAttached = true
                }

                // Smoothly unhide view and restore opacity
                overlayCursorView?.let { view ->
                    view.visibility = View.VISIBLE
                    view.alpha = 1.0f
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun hideOverlayCursor() {
        runOnUiThread {
            try {
                if (isCursorWindowAttached && overlayCursorView != null && overlayWindowManager != null) {
                    overlayWindowManager?.removeView(overlayCursorView)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isCursorWindowAttached = false
            }
        }
    }

    private fun updateOverlayCursor(dx: Float, dy: Float) {
        if (externalDisplayId == -1 || overlayWindowManager == null || overlayCursorView == null) return

        val scaleFactor = 1.5f
        overlayCursorX = (overlayCursorX + dx * scaleFactor).coerceIn(0f, externalDisplayWidth.toFloat())
        overlayCursorY = (overlayCursorY + dy * scaleFactor).coerceIn(0f, externalDisplayHeight.toFloat())

        overlayParams?.let { params ->
            params.x = overlayCursorX.toInt()
            params.y = overlayCursorY.toInt()
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
        isDesktopActive = false
        lastLaunchedExternalPackage = packageName
        hideOverlayCursor()
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
        } else {
            Toast.makeText(this, "Connect glasses or external display to launch apps on secondary screen", Toast.LENGTH_SHORT).show()
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
            if (externalDisplayId == -1) {
                return@setOnTouchListener false
            }

            cursorHideHandler.removeCallbacks(cursorHideRunnable)
            showOverlayCursor()

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
                    hasDraggedOrScrolled = false
                    viewCursorMirror.visibility = View.VISIBLE
                    viewCursorMirror.x = x
                    viewCursorMirror.y = y

                    // Queue long press check
                    trackpadHandler.removeCallbacks(longPressRunnable)
                    trackpadHandler.postDelayed(longPressRunnable, 600)
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    isMultiTouch = true
                    hasDraggedOrScrolled = true
                    trackpadHandler.removeCallbacks(longPressRunnable)
                    if (event.pointerCount >= 2) {
                        accumulatedScrollDx = 0f
                        accumulatedScrollDy = 0f
                        lastScrollGestureTime = System.currentTimeMillis()
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val totalMoved = Math.hypot((x - startX).toDouble(), (y - startY).toDouble())
                    if (totalMoved > 10) {
                        hasDraggedOrScrolled = true
                        trackpadHandler.removeCallbacks(longPressRunnable)
                    }
                    if (isMultiTouch && event.pointerCount >= 2) {
                        // Two-finger scroll drag
                        val scrollDx = (x - lastX) * 2.5f
                        val scrollDy = (y - lastY) * 2.5f
                        InteractionBridge.sendScroll(scrollDy)

                        accumulatedScrollDx += scrollDx
                        accumulatedScrollDy += scrollDy
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastScrollGestureTime > 80) {
                            val service = ControllerAccessibilityService.instance
                            if (externalDisplayId != -1 && service != null) {
                                val absDx = Math.abs(accumulatedScrollDx)
                                val absDy = Math.abs(accumulatedScrollDy)

                                if (absDx > absDy && absDx > dpToPx(4)) {
                                    val endX = (overlayCursorX + accumulatedScrollDx * 1.5f).coerceIn(0f, externalDisplayWidth.toFloat())
                                    service.dispatchScroll(externalDisplayId, overlayCursorX, overlayCursorY, endX, overlayCursorY)
                                    accumulatedScrollDx = 0f
                                    accumulatedScrollDy = 0f
                                    lastScrollGestureTime = currentTime
                                } else if (absDy > absDx && absDy > dpToPx(4)) {
                                    val endY = (overlayCursorY + accumulatedScrollDy * 1.5f).coerceIn(0f, externalDisplayHeight.toFloat())
                                    service.dispatchScroll(externalDisplayId, overlayCursorX, overlayCursorY, overlayCursorX, endY)
                                    accumulatedScrollDx = 0f
                                    accumulatedScrollDy = 0f
                                    lastScrollGestureTime = currentTime
                                }
                            }
                        }
                    } else {
                        // Single finger movement
                        val dx = x - lastX
                        val dy = y - lastY
                        InteractionBridge.sendCursorMove(dx, dy)
                        updateOverlayCursor(dx, dy)
                        
                        viewCursorMirror.x = x
                        viewCursorMirror.y = y
                    }
                    lastX = x
                    lastY = y
                }
                MotionEvent.ACTION_UP -> {
                    trackpadHandler.removeCallbacks(longPressRunnable)
                    viewCursorMirror.visibility = View.GONE
                    val duration = System.currentTimeMillis() - downTime
                    
                    if (!hasDraggedOrScrolled && duration < 250) {
                        performLeftClick()
                    } else if (isMultiTouch) {
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
                        accumulatedScrollDx = 0f
                        accumulatedScrollDy = 0f
                    }
                    isMultiTouch = false
                    cursorHideHandler.postDelayed(cursorHideRunnable, 5000)
                }
                MotionEvent.ACTION_CANCEL -> {
                    isMultiTouch = false
                    cursorHideHandler.postDelayed(cursorHideRunnable, 5000)
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    val duration = System.currentTimeMillis() - downTime
                    val absDx = Math.abs(accumulatedScrollDx)
                    val absDy = Math.abs(accumulatedScrollDy)
                    if (isMultiTouch && duration < 300 && absDx < dpToPx(8) && absDy < dpToPx(8)) {
                        performRightClick()
                    } else if (isMultiTouch) {
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

                    val actionIndex = event.actionIndex
                    val remainingIndex = if (actionIndex == 0) 1 else 0
                    if (event.pointerCount > remainingIndex) {
                        lastX = event.getX(remainingIndex)
                        lastY = event.getY(remainingIndex)
                    }
                }
            }
            true
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

    private fun setupPlaystoreReviewCard() {
        val cardPlaystoreReview = findViewById<View>(R.id.cardPlaystoreReview)
        if (BuildConfig.FLAVOR == "playstore") {
            cardPlaystoreReview?.visibility = View.VISIBLE
            findViewById<View>(R.id.btnRateApp)?.setOnClickListener {
                openPlayStoreRating()
            }
            findViewById<View>(R.id.btnSendFeedback)?.setOnClickListener {
                sendFeedbackEmail()
            }
        } else {
            cardPlaystoreReview?.visibility = View.GONE
        }
    }

    private fun openPlayStoreRating() {
        val targetPackage = if (packageName.endsWith(".play")) packageName else "$packageName.play"
        try {
            val uri = Uri.parse("market://details?id=$targetPackage")
            val goToMarket = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            startActivity(goToMarket)
        } catch (e: Exception) {
            val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$targetPackage")
            startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }

    private fun sendFeedbackEmail() {
        try {
            val displayInfo = if (externalDisplayId != -1) {
                "ID: $externalDisplayId, Res: ${externalDisplayWidth}x${externalDisplayHeight}"
            } else {
                "None connected"
            }
            val subject = "Feedback: Xternal Control v${BuildConfig.VERSION_NAME}"
            val body = """
                
                
                --- Device Diagnostics ---
                App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
                Flavor: ${BuildConfig.FLAVOR}
                Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
                Android Version: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})
                External Screen: $displayInfo
            """.trimIndent()

            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf("arkay107@gmail.com"))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            startActivity(Intent.createChooser(intent, "Send Feedback"))
        } catch (e: Exception) {
            Toast.makeText(this, "No email app found to send feedback", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndShowPlaystoreRatingPrompt() {
        if (BuildConfig.FLAVOR != "playstore") return
        val prefs = getSharedPreferences("XternalControlPrefs", Context.MODE_PRIVATE)
        val hasRatedOrDismissed = prefs.getBoolean("has_rated_or_dismissed", false)
        if (hasRatedOrDismissed) return

        val launchCount = prefs.getInt("playstore_launch_count", 0) + 1
        prefs.edit().putInt("playstore_launch_count", launchCount).apply()

        // Prompt on the 4th launch, and then every 8 launches if skipped
        if (launchCount == 4 || (launchCount > 4 && (launchCount - 4) % 8 == 0)) {
            showPlaystoreRatingDialog()
        }
    }

    private fun showPlaystoreRatingDialog() {
        val prefs = getSharedPreferences("XternalControlPrefs", Context.MODE_PRIVATE)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Enjoying Xternal Control?")
            .setMessage("If Xternal Control is helping you get a great desktop experience on your XR glasses or screen, please take a moment to rate us on Google Play. Your review directly helps us improve and support more devices!")
            .setPositiveButton("⭐ Rate on Google Play") { _, _ ->
                prefs.edit().putBoolean("has_rated_or_dismissed", true).apply()
                openPlayStoreRating()
            }
            .setNegativeButton("Maybe Later", null)
            .setNeutralButton("Don't Ask Again") { _, _ ->
                prefs.edit().putBoolean("has_rated_or_dismissed", true).apply()
            }
            .show()
    }

    private fun getMediaTargetDisplayInfo(): Triple<Int, Int, Int> {
        val targetDisplayId = if (externalDisplayId != -1) externalDisplayId else Display.DEFAULT_DISPLAY
        val dm = resources.displayMetrics
        val targetW = if (externalDisplayId != -1 && externalDisplayWidth > 0) externalDisplayWidth else dm.widthPixels
        val targetH = if (externalDisplayId != -1 && externalDisplayHeight > 0) externalDisplayHeight else dm.heightPixels
        return Triple(targetDisplayId, targetW, targetH)
    }

    private fun isLocalVideoForeground(): Boolean {
        val pkg = activeMediaPackage.lowercase()
        return pkg.contains("gallery") || pkg.contains("video") || pkg.contains("photos")
    }

    private fun isYouTubeForeground(): Boolean {
        val pkg = activeMediaPackage.lowercase()
        return pkg.contains("youtube")
    }

    private fun setupMediaControls() {
        tvMediaTitle = findViewById(R.id.tvMediaTitle)
        tvMediaArtist = findViewById(R.id.tvMediaArtist)
        tvMediaStatusDot = findViewById(R.id.tvMediaStatusDot)
        tvMediaStatusLabel = findViewById(R.id.tvMediaStatusLabel)
        layoutMediaTimeline = findViewById(R.id.layoutMediaTimeline)
        tvMediaTimeElapsed = findViewById(R.id.tvMediaTimeElapsed)
        pbMediaProgress = findViewById(R.id.pbMediaProgress)
        tvMediaTimeTotal = findViewById(R.id.tvMediaTimeTotal)
        btnMediaPlayPause = findViewById(R.id.btnMediaPlayPause)

        val btnMediaRewind = findViewById<View>(R.id.btnMediaRewind)
        btnMediaRewind?.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
            val currentState = mediaRemoteManager.getCurrentState()
            if (currentState.hasActiveSession) {
                mediaRemoteManager.seekRelative(-10_000)
            } else {
                val (targetDisplayId, targetW, targetH) = getMediaTargetDisplayInfo()
                val service = ControllerAccessibilityService.instance

                if (service != null && (isLocalVideoForeground() || isYouTubeForeground())) {
                    service.performMediaAction(
                        ControllerAccessibilityService.MediaAction.REWIND,
                        targetDisplayId,
                        targetW,
                        targetH,
                        activeMediaPackage
                    )
                } else {
                    sendMediaKey(KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD)
                }
            }
        }
        btnMediaRewind?.setOnLongClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
            val currentState = mediaRemoteManager.getCurrentState()
            if (currentState.hasActiveSession) {
                mediaRemoteManager.skipToPrevious()
            } else {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
            Toast.makeText(this, "⏮️ Previous Track/Video", Toast.LENGTH_SHORT).show()
            true
        }

        btnMediaPlayPause?.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
            val currentState = mediaRemoteManager.getCurrentState()
            if (currentState.hasActiveSession) {
                mediaRemoteManager.togglePlayPause()
            } else if (isLocalVideoForeground()) {
                val (targetDisplayId, targetW, targetH) = getMediaTargetDisplayInfo()
                val service = ControllerAccessibilityService.instance
                service?.performMediaAction(
                    ControllerAccessibilityService.MediaAction.PLAY_PAUSE,
                    targetDisplayId,
                    targetW,
                    targetH,
                    activeMediaPackage
                )
            } else {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
            v.postDelayed({ checkCurrentMediaPlayback() }, 300)
            v.postDelayed({ checkCurrentMediaPlayback() }, 600)
        }

        val btnMediaForward = findViewById<View>(R.id.btnMediaForward)
        btnMediaForward?.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
            val currentState = mediaRemoteManager.getCurrentState()
            if (currentState.hasActiveSession) {
                mediaRemoteManager.seekRelative(10_000)
            } else {
                val (targetDisplayId, targetW, targetH) = getMediaTargetDisplayInfo()
                val service = ControllerAccessibilityService.instance

                if (service != null && (isLocalVideoForeground() || isYouTubeForeground())) {
                    service.performMediaAction(
                        ControllerAccessibilityService.MediaAction.FAST_FORWARD,
                        targetDisplayId,
                        targetW,
                        targetH,
                        activeMediaPackage
                    )
                } else {
                    sendMediaKey(KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD)
                }
            }
        }
        btnMediaForward?.setOnLongClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
            val currentState = mediaRemoteManager.getCurrentState()
            if (currentState.hasActiveSession) {
                mediaRemoteManager.skipToNext()
            } else {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            }
            Toast.makeText(this, "⏭️ Next Track/Video", Toast.LENGTH_SHORT).show()
            true
        }

        val btnMediaVolume = findViewById<View>(R.id.btnMediaVolume)
        btnMediaVolume?.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
            try {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        pbMediaProgress?.setOnTouchListener { view, event ->
            val currentState = mediaRemoteManager.getCurrentState()
            if (currentState.hasActiveSession && currentState.durationMs > 0) {
                if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_MOVE) {
                    val width = view.width
                    if (width > 0) {
                        val touchX = event.x.coerceIn(0f, width.toFloat())
                        val ratio = touchX / width.toFloat()
                        val targetMs = (ratio * currentState.durationMs).toLong()
                        pbMediaProgress?.progress = (ratio * 100).toInt()
                        tvMediaTimeElapsed?.text = formatDurationMs(targetMs)
                        if (event.action == MotionEvent.ACTION_UP) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                            mediaRemoteManager.seekTo(targetMs)
                        }
                    }
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    private fun setupMediaDetection() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (::mediaRemoteManager.isInitialized) {
            mediaRemoteManager.onStateChangedListener = { state ->
                runOnUiThread {
                    applyMediaSessionState(state)
                }
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            audioPlaybackCallback = object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
                    runOnUiThread {
                        checkCurrentMediaPlayback()
                    }
                    window?.decorView?.postDelayed({
                        checkCurrentMediaPlayback()
                    }, 250)
                }
            }
            audioManager.registerAudioPlaybackCallback(audioPlaybackCallback!!, Handler(Looper.getMainLooper()))
        }

        checkCurrentMediaPlayback()
    }

    private fun updateMediaAppInfo(pkg: String) {
        if (pkg.isEmpty() || pkg == packageName) return
        activeMediaPackage = pkg
        if (::mediaRemoteManager.isInitialized) {
            mediaRemoteManager.foregroundPackage = pkg
        }
        try {
            val isSystemOrLauncher = pkg.contains("launcher") || pkg.contains("home") || pkg.contains("systemui") || pkg.contains("settings")
            if (isSystemOrLauncher) {
                runOnUiThread {
                    if (!isAudioActivelyPlaying()) {
                        val state = if (::mediaRemoteManager.isInitialized) mediaRemoteManager.getCurrentState() else null
                        if (state != null && state.hasActiveSession) {
                            applyMediaSessionState(state)
                        } else {
                            cardMediaControls?.visibility = View.GONE
                        }
                    }
                }
                return
            }
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(pkg, 0)
            val label = pm.getApplicationLabel(appInfo).toString()
            runOnUiThread {
                val state = if (::mediaRemoteManager.isInitialized) mediaRemoteManager.getCurrentState() else null
                if (state == null || !state.hasActiveSession) {
                    tvMediaTitle?.text = label
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                val state = if (::mediaRemoteManager.isInitialized) mediaRemoteManager.getCurrentState() else null
                if (state == null || !state.hasActiveSession) {
                    tvMediaTitle?.text = "Media Controller"
                }
            }
        }
    }

    private fun applyMediaSessionState(state: MediaSessionState) {
        if (!state.hasActiveSession) {
            if (isLocalVideoForeground()) {
                cardMediaControls?.visibility = View.VISIBLE
                val appLabel = try {
                    val pm = packageManager
                    val appInfo = pm.getApplicationInfo(activeMediaPackage, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    "Video Player"
                }
                tvMediaTitle?.text = appLabel
                tvMediaArtist?.text = if (externalDisplayId != -1) "Playing on Glasses / Display" else "Local Video"
                updateMediaTimelineFromPlayer()
            } else {
                cardMediaControls?.visibility = View.GONE
                mediaProgressHandler.removeCallbacks(mediaProgressRunnable)
            }
            return
        }

        // Active MediaSession -> Reveal widget (Option 1)
        cardMediaControls?.visibility = View.VISIBLE

        tvMediaTitle?.text = if (state.title.isNotBlank()) state.title else state.appName
        tvMediaArtist?.text = when {
            state.artist.isNotBlank() -> state.artist
            externalDisplayId != -1 -> "Playing on Glasses / Display"
            state.appName.isNotBlank() -> state.appName
            else -> "Ready for playback"
        }

        if (state.isPlaying) {
            tvMediaStatusDot?.backgroundTintList = ContextCompat.getColorStateList(this, R.color.neon_emerald)
            tvMediaStatusLabel?.text = "PLAYING"
            tvMediaStatusLabel?.setTextColor(ContextCompat.getColor(this, R.color.neon_emerald))
            btnMediaPlayPause?.setImageResource(R.drawable.ic_media_pause)
        } else {
            tvMediaStatusDot?.backgroundTintList = ContextCompat.getColorStateList(this, R.color.text_secondary)
            tvMediaStatusLabel?.text = "PAUSED"
            tvMediaStatusLabel?.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            btnMediaPlayPause?.setImageResource(R.drawable.ic_media_play)
        }

        if (state.durationMs > 0) {
            layoutMediaTimeline?.visibility = View.VISIBLE
            tvMediaTimeElapsed?.text = formatDurationMs(state.currentPositionMs)
            tvMediaTimeTotal?.text = formatDurationMs(state.durationMs)
            val percent = ((state.currentPositionMs.toDouble() / state.durationMs.toDouble()) * 100).toInt().coerceIn(0, 100)
            pbMediaProgress?.progress = percent
        } else {
            val timeline = ControllerAccessibilityService.instance?.queryActiveMediaTimeline()
            if (timeline != null) {
                layoutMediaTimeline?.visibility = View.VISIBLE
                tvMediaTimeElapsed?.text = timeline.currentTime
                tvMediaTimeTotal?.text = timeline.totalTime
                pbMediaProgress?.progress = timeline.progressPercent
            } else {
                layoutMediaTimeline?.visibility = View.GONE
            }
        }
    }

    private fun formatDurationMs(ms: Long): String {
        if (ms <= 0L) return "0:00"
        val totalSecs = ms / 1000
        val seconds = totalSecs % 60
        val minutes = (totalSecs / 60) % 60
        val hours = totalSecs / 3600
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    private fun updateMediaTimelineFromPlayer() {
        val timeline = ControllerAccessibilityService.instance?.queryActiveMediaTimeline()
        if (timeline != null) {
            layoutMediaTimeline?.visibility = View.VISIBLE
            tvMediaTimeElapsed?.text = timeline.currentTime
            tvMediaTimeTotal?.text = timeline.totalTime
            pbMediaProgress?.progress = timeline.progressPercent
        } else {
            layoutMediaTimeline?.visibility = View.GONE
        }
    }

    private fun isAudioActivelyPlaying(): Boolean {
        // Step 1: Check active playback configurations (Android O / API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val configs = audioManager.activePlaybackConfigurations
                if (configs.isNotEmpty()) {
                    var foundMediaStream = false
                    val anyStreamStarted = configs.any { cfg ->
                        val usage = cfg.audioAttributes?.usage ?: -1
                        val isMediaUsage = (usage == AudioAttributes.USAGE_MEDIA ||
                                           usage == AudioAttributes.USAGE_GAME ||
                                           usage == AudioAttributes.USAGE_UNKNOWN)
                        if (!isMediaUsage) return@any false
                        foundMediaStream = true

                        // In AOSP, AudioPlaybackConfiguration has @hide / @SystemApi isActive() or getPlayerState()
                        val activeFlag = try {
                            val method = cfg.javaClass.getMethod("isActive")
                            method.invoke(cfg) as? Boolean
                        } catch (e: Throwable) {
                            null
                        }

                        if (activeFlag != null) {
                            activeFlag
                        } else {
                            val state = try {
                                val stateMethod = cfg.javaClass.getMethod("getPlayerState")
                                stateMethod.invoke(cfg) as? Int ?: 0
                            } catch (e: Throwable) {
                                0
                            }
                            // 3 is PLAYER_STATE_STARTED in AudioPlaybackConfiguration
                            state == 3
                        }
                    }
                    if (anyStreamStarted) return true
                    // If a media stream exists but none are in started/active state, playback is paused!
                    if (foundMediaStream) return false
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        // Step 2: Check on-screen accessibility video controls (e.g. video playing in Gallery)
        val videoState = ControllerAccessibilityService.instance?.queryVideoPlayingState()
        if (videoState != null) {
            return videoState
        }

        // Step 3: Fallback to AudioManager.isMusicActive for legacy Android
        return try {
            audioManager.isMusicActive
        } catch (e: Throwable) {
            false
        }
    }

    private fun checkCurrentMediaPlayback() {
        if (::mediaRemoteManager.isInitialized) {
            mediaRemoteManager.refreshActiveSessions()
            val state = mediaRemoteManager.getCurrentState()
            if (state.hasActiveSession) {
                applyMediaSessionState(state)
                return
            }
        }
        val active = isAudioActivelyPlaying()
        updateMediaPlaybackUi(active)
    }

    private fun updateMediaPlaybackUi(isPlaying: Boolean) {
        if (isPlaying) {
            cardMediaControls?.visibility = View.VISIBLE
            tvMediaStatusDot?.backgroundTintList = ContextCompat.getColorStateList(this, R.color.neon_emerald)
            tvMediaStatusLabel?.text = "PLAYING"
            tvMediaStatusLabel?.setTextColor(ContextCompat.getColor(this, R.color.neon_emerald))
            
            // Unified styling: identical vector icon, background, and tint
            btnMediaPlayPause?.setImageResource(R.drawable.ic_media_pause)

            val subtitle = if (externalDisplayId != -1) "Playing on Glasses / Display" else "Playback Active"
            tvMediaArtist?.text = subtitle

            updateMediaTimelineFromPlayer()
            mediaProgressHandler.removeCallbacks(mediaProgressRunnable)
            mediaProgressHandler.post(mediaProgressRunnable)
        } else {
            if (isLocalVideoForeground()) {
                cardMediaControls?.visibility = View.VISIBLE
                tvMediaStatusDot?.backgroundTintList = ContextCompat.getColorStateList(this, R.color.text_secondary)
                tvMediaStatusLabel?.text = "PAUSED"
                tvMediaStatusLabel?.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                btnMediaPlayPause?.setImageResource(R.drawable.ic_media_play)
                layoutMediaTimeline?.visibility = View.GONE
            } else {
                cardMediaControls?.visibility = View.GONE
            }
            mediaProgressHandler.removeCallbacks(mediaProgressRunnable)
        }
    }

    private fun sendMediaKey(keyCode: Int) {
        try {
            val now = SystemClock.uptimeMillis()
            val downEvent = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
            val upEvent = KeyEvent(now, now + 10, KeyEvent.ACTION_UP, keyCode, 0)
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkAndShowOnboardingGuide() {
        val prefs = getSharedPreferences("XternalControlPrefs", Context.MODE_PRIVATE)
        val hasSeen = prefs.getBoolean("has_seen_onboarding_guide", false)
        if (!hasSeen) {
            showOnboardingGuideDialog()
            prefs.edit().putBoolean("has_seen_onboarding_guide", true).apply()
        }
    }

    private fun showOnboardingGuideDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_quick_guide, null)
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvHeaderSubtitle = dialogView.findViewById<TextView>(R.id.tvGuideHeaderSubtitle)
        val btnClose = dialogView.findViewById<View>(R.id.btnGuideClose)
        val btnPrev = dialogView.findViewById<android.widget.Button>(R.id.btnGuidePrev)
        val btnNext = dialogView.findViewById<android.widget.Button>(R.id.btnGuideNext)
        val tvPageDots = dialogView.findViewById<TextView>(R.id.tvGuidePageDots)

        val page1 = dialogView.findViewById<View>(R.id.layoutPage1)
        val page2 = dialogView.findViewById<View>(R.id.layoutPage2)
        val page3 = dialogView.findViewById<View>(R.id.layoutPage3)
        val page4 = dialogView.findViewById<View>(R.id.layoutPage4)
        val pages = listOf(page1, page2, page3, page4)

        val subtitles = listOf(
            "Page 1 of 4: Setup & Connection",
            "Page 2 of 4: Touchpad Gestures",
            "Page 3 of 4: Remote Buttons Explained",
            "Page 4 of 4: DRM Video Playback"
        )
        val dots = listOf(
            "● ○ ○ ○",
            "○ ● ○ ○",
            "○ ○ ● ○",
            "○ ○ ○ ●"
        )

        var currentPage = 0

        fun updatePageUi() {
            pages.forEachIndexed { index, view ->
                view?.visibility = if (index == currentPage) View.VISIBLE else View.GONE
            }
            tvHeaderSubtitle?.text = subtitles[currentPage]
            tvPageDots?.text = dots[currentPage]
            btnPrev?.visibility = if (currentPage > 0) View.VISIBLE else View.INVISIBLE
            btnNext?.text = if (currentPage == pages.size - 1) "Got It! ✓" else "Next →"
        }

        btnPrev?.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                updatePageUi()
            }
        }

        btnNext?.setOnClickListener {
            if (currentPage < pages.size - 1) {
                currentPage++
                updatePageUi()
            } else {
                dialog.dismiss()
            }
        }

        btnClose?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun performLeftClick() {
        val service = ControllerAccessibilityService.instance
        if (externalDisplayId != -1 && service != null) {
            service.dispatchClick(externalDisplayId, overlayCursorX, overlayCursorY)
        } else {
            InteractionBridge.sendClick()
        }
    }

    private fun performRightClick() {
        InteractionBridge.sendRightClick()
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
        if (::appAdapter.isInitialized) {
            try {
                appAdapter.updateData(sortedApps)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    private fun isProActive(): Boolean {
        return if (::billingManager.isInitialized) billingManager.isProActive() else (BuildConfig.FLAVOR != "playstore")
    }

    private fun toggleAppFavourite(app: AppInfo) {
        if (favouritePackages.contains(app.packageName)) {
            favouritePackages.remove(app.packageName)
            Toast.makeText(this, "${app.label} removed from Favourites", Toast.LENGTH_SHORT).show()
        } else {
            if (!isProActive() && favouritePackages.size >= BillingManager.FREE_MAX_FAVOURITES) {
                showProUpgradeDialog("Free version is limited to ${BillingManager.FREE_MAX_FAVOURITES} favourite apps.\n\nUpgrade to Pro (Lifetime) for unlimited pinned favourites!")
                return
            }
            favouritePackages.add(app.packageName)
            Toast.makeText(this, "${app.label} added to Favourites", Toast.LENGTH_SHORT).show()
        }
        saveListsToPreferences()
        sortAndRefreshAppLists()
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

    private fun showProUpgradeDialog(
        message: String = "Launching this app is a Pro feature.\n\nUpgrade to Pro (Lifetime) to unlock all installed apps and unlimited favourites!"
    ) {
        if (BuildConfig.FLAVOR == "playstore") {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Xternal Control Pro")
                .setMessage(message)
                .setPositiveButton("⚡ Upgrade to Pro") { _, _ ->
                    if (::billingManager.isInitialized) billingManager.purchasePro(this)
                }
                .setNegativeButton("Maybe Later", null)
                .setNeutralButton("Restore") { _, _ ->
                    if (::billingManager.isInitialized) billingManager.restorePurchases(this)
                }
                .show()
        } else {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndShowTrialExpiredDialog() {
        if (BuildConfig.FLAVOR == "playstore" && ::billingManager.isInitialized) {
            if (billingManager.shouldShowTrialExpiredDialog()) {
                billingManager.markTrialExpiredDialogShown()
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Trial Version Expired")
                    .setMessage("Your 2-day free trial of Xternal Control has ended. We hope you enjoyed the full desktop experience!\n\nUpgrade to Pro (Lifetime) to unlock unlimited apps and pinned favourites on your external display.")
                    .setPositiveButton("⚡ Upgrade to Pro") { _, _ ->
                        billingManager.purchasePro(this)
                    }
                    .setNegativeButton("Maybe Later", null)
                    .setNeutralButton("Restore") { _, _ ->
                        billingManager.restorePurchases(this)
                    }
                    .show()
            }
        }
    }

    private fun updateProUi() {
        val cardDonation = findViewById<View>(R.id.cardDonation) ?: return
        val tvDonationHeader = findViewById<TextView>(R.id.tvDonationHeader) ?: return
        val tvDonationMsg = findViewById<TextView>(R.id.tvDonationMsg) ?: return
        val btnDonate = findViewById<Button>(R.id.btnDonate) ?: return
        val btnRestorePurchase = findViewById<TextView>(R.id.btnRestorePurchase)

        cardDonation.visibility = View.VISIBLE
        if (BuildConfig.FLAVOR == "playstore") {
            val isTrial = ::billingManager.isInitialized && billingManager.isTrialActive()
            val isExpired = ::billingManager.isInitialized && billingManager.isTrialExpired()

            if (isTrial) {
                val hours = billingManager.getTrialHoursRemaining()
                tvDonationHeader.text = "⏱️ 2-DAY FREE TRIAL ACTIVE"
                tvDonationHeader.setTextColor(ContextCompat.getColor(this, R.color.neon_cyan))
                tvDonationMsg.text = "You have $hours hours left in your full-featured free trial! All installed apps and unlimited favourites are currently unlocked."
                btnDonate.text = "⚡ UNLOCK PRO (LIFETIME)"
                btnDonate.backgroundTintList = ContextCompat.getColorStateList(this, R.color.neon_emerald)
                btnDonate.setTextColor(ContextCompat.getColor(this, R.color.text_dark))
                btnDonate.isEnabled = true
                btnDonate.setOnClickListener {
                    if (::billingManager.isInitialized) billingManager.purchasePro(this)
                }
                btnRestorePurchase?.visibility = View.VISIBLE
                btnRestorePurchase?.setOnClickListener {
                    if (::billingManager.isInitialized) billingManager.restorePurchases(this)
                }
            } else if (isProActive()) {
                tvDonationHeader.text = "★ XTERNAL CONTROL PRO ACTIVE"
                tvDonationHeader.setTextColor(ContextCompat.getColor(this, R.color.neon_cyan))
                tvDonationMsg.text = "Lifetime Pro is active on this device. All installed apps and unlimited favourites are fully unlocked. Thank you for your support!"
                btnDonate.text = "✓ PRO LIFETIME ACTIVE"
                btnDonate.backgroundTintList = ContextCompat.getColorStateList(this, R.color.neon_cyan)
                btnDonate.setTextColor(ContextCompat.getColor(this, R.color.text_dark))
                btnDonate.isEnabled = false
                btnRestorePurchase?.visibility = View.GONE
            } else {
                tvDonationHeader.text = if (isExpired) "★ TRIAL EXPIRED - UPGRADE TO PRO" else "★ UPGRADE TO PRO (LIFETIME)"
                tvDonationHeader.setTextColor(ContextCompat.getColor(this, R.color.neon_cyan))
                tvDonationMsg.text = if (isExpired) {
                    "Your 2-day free trial has expired. Upgrade to Pro (Lifetime) to unlock unlimited apps and pinned favourites on your external display!"
                } else {
                    "Unlock all installed apps and unlimited pinned favourites on your external display. One-time payment, lifetime access!"
                }
                btnDonate.text = "⚡ UNLOCK PRO (LIFETIME)"
                btnDonate.backgroundTintList = ContextCompat.getColorStateList(this, R.color.neon_emerald)
                btnDonate.setTextColor(ContextCompat.getColor(this, R.color.text_dark))
                btnDonate.isEnabled = true
                btnDonate.setOnClickListener {
                    if (::billingManager.isInitialized) billingManager.purchasePro(this)
                }
                btnRestorePurchase?.visibility = View.VISIBLE
                btnRestorePurchase?.setOnClickListener {
                    if (::billingManager.isInitialized) billingManager.restorePurchases(this)
                }
            }
        } else {
            tvDonationHeader.text = "SUPPORT THE CREATOR"
            tvDonationMsg.text = "If you find this app useful, please consider supporting the creator. Your contributions help my family with our special needs child. Thank you so much!"
            btnDonate.text = "☕ BUY ME A COFFEE"
            btnDonate.backgroundTintList = ContextCompat.getColorStateList(this, R.color.neon_emerald)
            btnDonate.setTextColor(ContextCompat.getColor(this, R.color.text_dark))
            btnDonate.isEnabled = true
            btnDonate.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/akworkshop"))
                startActivity(intent)
            }
            btnRestorePurchase?.visibility = View.GONE
        }
    }

    private fun startContinuousZoom(isZoomIn: Boolean) {
        stopContinuousZoom()
        val runnable = object : Runnable {
            override fun run() {
                InteractionBridge.sendZoom(isZoomIn)
                val service = ControllerAccessibilityService.instance
                if (externalDisplayId != -1 && service != null) {
                    service.dispatchZoom(externalDisplayId, overlayCursorX, overlayCursorY, isZoomIn)
                }
                zoomHandler.postDelayed(this, 250)
            }
        }
        zoomRunnable = runnable
        zoomHandler.post(runnable)
    }

    private fun stopContinuousZoom() {
        zoomRunnable?.let {
            zoomHandler.removeCallbacks(it)
        }
        zoomRunnable = null
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

    private fun setupBridgeListeners() {
        InteractionBridge.appLaunchedFromExternalListener = { pkg ->
            try {
                if (pkg != packageName) {
                    isDesktopActive = false
                    lastLaunchedExternalPackage = pkg
                    recentPackages.remove(pkg)
                    recentPackages.add(0, pkg)
                    saveListsToPreferences()
                    if (::appAdapter.isInitialized) {
                        sortAndRefreshAppLists()
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        InteractionBridge.foregroundPackageChangedListener = { pkg ->
            try {
                if (pkg == packageName) {
                    isDesktopActive = true
                } else if (!pkg.isNullOrEmpty()) {
                    isDesktopActive = false
                    lastLaunchedExternalPackage = pkg
                }
                updateMediaAppInfo(pkg)
                checkCurrentMediaPlayback()
                window?.decorView?.postDelayed({ checkCurrentMediaPlayback() }, 250)
                window?.decorView?.postDelayed({ checkCurrentMediaPlayback() }, 500)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        InteractionBridge.desktopForegroundStateListener = { isForeground ->
            try {
                isDesktopActive = isForeground
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        InteractionBridge.pipStateChangedListener = { active ->
            try {
                updatePipButtonUi(active)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    private fun updatePipButtonUi(isActive: Boolean) {
        try {
            isPipModeActive = isActive
            val tvBtn = btnPipMode as? TextView ?: return
            if (isActive) {
                tvBtn.backgroundTintList = ContextCompat.getColorStateList(this, R.color.neon_cyan)
                tvBtn.setTextColor(ContextCompat.getColor(this, R.color.text_dark))
            } else {
                tvBtn.backgroundTintList = null
                tvBtn.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun restoreAppToFullScreen(targetPackage: String) {
        if (targetPackage.isEmpty() || targetPackage == packageName) {
            return
        }
        hideOverlayCursor()
        var appLaunched = false
        if (externalDisplayId != -1) {
            when {
                targetPackage == "mock.browser" || targetPackage == "mock.notes" || targetPackage == "mock.map" -> {
                    InteractionBridge.sendAppLaunch(targetPackage)
                    appLaunched = true
                    isDesktopActive = false
                    updatePipButtonUi(false)
                    Toast.makeText(this, "Restoring app to Full Screen", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    try {
                        val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
                        if (launchIntent != null) {
                            val options = ActivityOptions.makeBasic()
                            options.launchDisplayId = externalDisplayId
                            launchIntent.addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                            )
                            startActivity(launchIntent, options.toBundle())
                            appLaunched = true
                            isDesktopActive = false
                            updatePipButtonUi(false)
                            Toast.makeText(this, "Restoring app to Full Screen...", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
            }
        }
        if (!appLaunched) {
            Toast.makeText(this, "App could not be restored", Toast.LENGTH_SHORT).show()
        }
    }

    private fun togglePipPassThrough() {
        try {
            val targetPackage = if (!lastLaunchedExternalPackage.isNullOrEmpty() && lastLaunchedExternalPackage != packageName) {
                lastLaunchedExternalPackage
            } else {
                recentPackages.firstOrNull { it.isNotEmpty() && it != packageName }
            }

            if (isDesktopActive) {
                // If on desktop, clicking glasses button restores the running app to full screen
                if (!targetPackage.isNullOrEmpty()) {
                    restoreAppToFullScreen(targetPackage)
                } else {
                    Toast.makeText(this, "Open an app first to use floating mode", Toast.LENGTH_SHORT).show()
                }
                return
            }

            // Inside an app: toggle between floating (black pass-through) and full screen
            isPipModeActive = !isPipModeActive

            if (isPipModeActive) {
                // 1st Tap: Enter Floating / PiP mode:
                // Desktop launcher is hidden (pure black pass-through for AR glasses)
                updatePipButtonUi(true)
                Toast.makeText(this, "PiP Mode Active (Glasses background is black)", Toast.LENGTH_SHORT).show()
                InteractionBridge.sendPipMode(true)

                if (externalDisplayId != -1) {
                    try {
                        val options = ActivityOptions.makeBasic()
                        options.launchDisplayId = externalDisplayId
                        val intent = Intent(this, ExternalActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(intent, options.toBundle())
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
            } else {
                // 2nd Tap: User clicked Glasses button again:
                // Restore running app back to FULL SCREEN!
                updatePipButtonUi(false)
                InteractionBridge.sendPipMode(false)

                if (!targetPackage.isNullOrEmpty()) {
                    restoreAppToFullScreen(targetPackage)
                } else {
                    // Fallback: restore desktop in ExternalActivity
                    isDesktopActive = true
                    if (externalDisplayId != -1) {
                        try {
                            val options = ActivityOptions.makeBasic()
                            options.launchDisplayId = externalDisplayId
                            val intent = Intent(this, ExternalActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            startActivity(intent, options.toBundle())
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                    }
                    Toast.makeText(this, "Glasses returned to Desktop", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            Toast.makeText(this, "Action could not be completed", Toast.LENGTH_SHORT).show()
        }
    }


    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun applyOrientationLayout() {
        val controllerPanel = findViewById<View>(R.id.controllerPanel)
        val cpParams = controllerPanel.layoutParams as LinearLayout.LayoutParams
        cpParams.width = LinearLayout.LayoutParams.MATCH_PARENT
        cpParams.height = LinearLayout.LayoutParams.MATCH_PARENT
        cpParams.weight = 1f
        controllerPanel.layoutParams = cpParams
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientationLayout()
        val rootLayout = findViewById<View>(R.id.rootLayout)
        ViewCompat.requestApplyInsets(rootLayout)
        
        // Update column count in apps grid
        val columns = if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) 3 else 5
        if (rvAppsHorizontal.layoutManager is GridLayoutManager) {
            (rvAppsHorizontal.layoutManager as GridLayoutManager).spanCount = columns
            appAdapter.notifyDataSetChanged()
        }
    }

    private fun setGlassesBackgroundColor(colorHex: String, name: String) {
        val prefs = getSharedPreferences("XternalControlPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("glasses_bg_type", "color")
            .putString("glasses_bg_color", colorHex)
            .apply()
        Toast.makeText(this, "Glasses Theme: $name", Toast.LENGTH_SHORT).show()
    }

    private fun saveWallpaperFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val wallpaperFile = File(filesDir, "glasses_wallpaper.png")
            val outputStream = FileOutputStream(wallpaperFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()

            val prefs = getSharedPreferences("XternalControlPrefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("glasses_bg_type", "image")
                .putLong("glasses_bg_updated", System.currentTimeMillis())
                .apply()

            Toast.makeText(this, "Custom Gallery Wallpaper Set!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBackgroundCustomizer() {
        findViewById<View>(R.id.btnBgBlack)?.setOnClickListener {
            setGlassesBackgroundColor("#000000", "OLED Black (Transparent AR)")
        }
        findViewById<View>(R.id.btnBgObsidian)?.setOnClickListener {
            setGlassesBackgroundColor("#0A0B10", "Obsidian Dark")
        }
        findViewById<View>(R.id.btnBgCyber)?.setOnClickListener {
            setGlassesBackgroundColor("#0D111E", "Cyber Neon Aura")
        }
        findViewById<View>(R.id.btnBgPurple)?.setOnClickListener {
            setGlassesBackgroundColor("#130C1E", "Midnight Purple")
        }
        findViewById<View>(R.id.btnBgEmerald)?.setOnClickListener {
            setGlassesBackgroundColor("#071912", "Emerald Matrix")
        }

        val etCustomHex = findViewById<EditText>(R.id.etCustomHex)
        findViewById<View>(R.id.btnApplyHex)?.setOnClickListener {
            val hex = etCustomHex?.text?.toString()?.trim() ?: ""
            if (hex.isNotEmpty()) {
                val formattedHex = if (hex.startsWith("#")) hex else "#$hex"
                try {
                    Color.parseColor(formattedHex)
                    setGlassesBackgroundColor(formattedHex, "Custom Hex ($formattedHex)")
                    etCustomHex.setText("")
                } catch (e: Exception) {
                    Toast.makeText(this, "Invalid Hex Color (e.g., #2B003B)", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<View>(R.id.btnPickWallpaper)?.setOnClickListener {
            try {
                pickWallpaperLauncher.launch("image/*")
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Cannot open gallery picker", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.btnClearWallpaper)?.setOnClickListener {
            val wallpaperFile = File(filesDir, "glasses_wallpaper.png")
            if (wallpaperFile.exists()) {
                wallpaperFile.delete()
            }
            val prefs = getSharedPreferences("XternalControlPrefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("glasses_bg_type", "default")
                .apply()
            Toast.makeText(this, "Reset to Default Wallpaper", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btnQuickGuide)?.setOnClickListener {
            showOnboardingGuideDialog()
        }
    }

    private fun saveScreenshotBitmapToGallery(bitmap: android.graphics.Bitmap) {
        try {
            val filename = "Xternal_Screenshot_${System.currentTimeMillis()}.png"
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/XternalControl")
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val uri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    contentResolver.update(uri, contentValues, null, null)
                }
                Toast.makeText(this, "📸 Screenshot saved to Pictures/XternalControl!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save screenshot: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.getBooleanExtra("EXTRA_TRIGGER_PURCHASE", false) == true) {
            showProUpgradeDialog()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            audioPlaybackCallback?.let {
                try {
                    audioManager.unregisterAudioPlaybackCallback(it)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        mediaProgressHandler.removeCallbacks(mediaProgressRunnable)
        if (::billingManager.isInitialized) {
            billingManager.destroy()
        }
    }
}
