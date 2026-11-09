# GEMINI.md - Xternal Control Project Foundation, Selling Points & Engineering Rules

## 1. Project Overview & Core Selling Points
**Xternal Control** is an ultra-lightweight Android productivity app (~5MB download) that transforms any Android phone or tablet into a native 16:9 widescreen desktop workspace, universal media controller, and precision multi-touch trackpad when connected to external monitors, TVs, or XR/AR smart glasses (XREAL, Rokid, RayNeo, Viture).

### Key Selling Points:
1. **Ultra-Lightweight Footprint**: Zero bloated web engines, zero heavy frameworks, raw native Android Jetpack Compose + View hierarchy (~5MB download, ~15MB RAM footprint).
2. **True 16:9 Native Resolution Desktop**: Overrides phone aspect ratio mirroring (which leaves black pillar bars on monitors) using native `ActivityOptions.setLaunchDisplayId()` into secondary presentation workspaces.
3. **XR / AR Smart Glasses Optimization**:
   - Ultra-low latency cursor pass-through.
   - High refresh-rate rendering support (up to 120Hz).
   - Dedicated True Black (OLED `#000000`) HUD mode to eliminate light bleed on micro-OLED smart glasses prisms.
4. **Hardware Widevine L1 DRM Workaround**:
   - Proprietary "Playback Detach" workflow allows protected streaming apps (Netflix, Disney+, Apple TV+, Prime Video, Max) to play at 1080p/4K without the black screen DRM lockout caused by screen-mirroring protocols.
5. **Universal Media Remote with Hardware Focus Bypass**:
   - Tapping phone media buttons dispatches playback controls directly into active background `MediaSession` tokens across all connected displays, eliminating the need to focus the external display window.
6. **Multi-Touch Trackpad with Dynamic Pointer Smoothing**:
   - Full 2-finger scroll, tap-to-click, edge gestures, and customizable pointer velocity/acceleration curves.
   - Cursor overlay stays completely non-blocking to on-screen keyboards (`FLAG_NOT_FOCUSABLE`, `FLAG_NOT_TOUCHABLE`).

---

## 2. Strict Regression Prevention Rules (Past Bugs)
Every developer and agent modifying this repository **MUST** respect the following rules to prevent regression of historical bugs:

### Rule 1: Trial Lockout Prevention (Android MediaStore Duplication)
- **Root Cause**: On Android 10+ (API 29+), `MediaStore.Files` renames conflicting filenames instead of overwriting them (e.g. `xt_license_v1 (1).bin`). In v1.5.2, `onPreExistingTrialDetected()` mistook these duplicate update tokens for anti-tamper cheat attempts, setting `trialStartTime = 1L` (instantly expired).
- **Rule**:
  1. Never run anti-tamper expiry (`trialStartTime = 1L`) if a trial reset epoch is active (`KEY_TRIAL_EPOCH_RESET_TIME > 0` and within 48 hours).
  2. For major/minor version upgrades or manual trial resets, increment `CURRENT_TRIAL_EPOCH` and use versioned token filenames (`xt_license_v2.bin`, `xt_device_state_v2.bin`).
  3. Never query legacy token filenames without validating their creation timestamps against the current epoch reset time.

### Rule 2: Apple TV & Streaming App Remote Focus Drop
- **Root Cause**: On Samsung DeX and desktop displays, touching the phone trackpad or UI steals window focus away from the external display. Apple TV+ and Netflix drop standard Android key events (`KEYCODE_MEDIA_PLAY_PAUSE`) if their window is not active.
- **Rule**:
  - Always attempt `MediaSessionManager` active session dispatch first via `transportControls.play()`, `transportControls.pause()`, etc.
  - Only fallback to injecting `KeyEvent` if no active `MediaSession` is registered.

### Rule 3: Gboard / On-Screen Keyboard Typing Interference
- **Root Cause**: The cursor overlay view on secondary displays or virtual input window can intercept soft keyboard touches if touch flags are incorrectly configured.
- **Rule**:
  - The pointer/cursor overlay `WindowManager.LayoutParams` must strictly have `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE | FLAG_LAYOUT_NO_LIMITS`.
  - Maintain the visual cursor offset (+3px vertical/horizontal) to avoid dead-zone occlusion under pointer coordinates.

### Rule 4: Xiaomi & Vivo Virtual / Rear Display Misdetection
- **Root Cause**: Xiaomi 17/18 Pro, Mi Mix Fold, and Vivo Pad 3 Pro expose internal secondary displays (rear selfie screens, folding hinge screens, or virtualization surfaces) to `DisplayManager`.
- **Rule**:
  - Filter `DisplayManager.getDisplays()` by flags:
    1. Must match `Display.FLAG_PRESENTATION` or `Display.TYPE_EXTERNAL` / `Display.TYPE_WIFI`.
    2. Explicitly exclude internal private flags and zero-size / rear display IDs.

### Rule 5: Samsung DeX Mode Auto-Detection
- **Root Cause**: On Samsung One UI, if DeX is disabled in system settings, attempting to launch desktop activities into external displays may be silently suppressed or mirrored.
- **Rule**:
  - Query Samsung DeX state via `SemDesktopModeManager` or system configuration `uiMode & UI_MODE_TYPE_DESK`. Warn user if DeX mode is interfering with direct presentation routing.

---

## 3. Future Roadmap: Multi-Window & Virtual Desktop Workspaces
When the user requests to **"work on the workspace feature"** or **"multi-window feature"**, refer directly to [`.agents/rules/future-roadmap-workspace.md`](file:///d:/Projects/Programming/xternal%20control/.agents/rules/future-roadmap-workspace.md).

### Summary of the Upcoming Workspace Architecture:
- **Phase 1: Multi-Workspace Manager (Virtual Desktops)**:
  - Workspaces: `Work`, `Media`, `Gaming`, `Utilities`.
  - Fast switcher on the mobile trackpad screen (swipe 3 fingers or tap workspace dock).
  - Independent app stacks and layout states per workspace.
- **Phase 2: Free-Form / Tiling Window Manager**:
  - Secondary presentation split-screen tiling (50/50, 70/30, 2x2 grid).
  - Floating window overlays managed via secondary activity task stacks (`ActivityOptions.setLaunchBounds`).
- **Phase 3: Persistent Workspace State**:
  - Save open application presets per workspace across cable disconnects.

---

## 4. Build, Signing & Release Protocols
- **Build Environment**: Always use JDK 21 (`D:\Program Files\Android\Android Studio\jbr`) and `./gradlew --no-daemon`.
- **Keystore**: `keystore/release-key.jks` (Key alias: `release`, configured in `app/build.gradle.kts`).
- **Artifacts Destination**:
  - Workspace root: `D:\Projects\Programming\xternal control\`
  - Local software drive: `X:\Softwares\`
  - Required files per release:
    - `app-github-release-vX.Y.Z.apk`
    - `app-playstore-release-vX.Y.Z.apk`
    - `app-playstore-release-vX.Y.Z.aab`
- **Sandbox Note**: Windows sandbox blocks Gradle socket bindings and access to `X:\`. Always execute Gradle and deployment commands with `BypassSandbox: true`.
