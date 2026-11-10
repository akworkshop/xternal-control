# Architecture & Selling Points: Xternal Control

## Overview
Xternal Control turns Android devices into productivity powerhouses when tethered (USB-C DisplayPort Alternate Mode, HDMI adapters) or wirelessly cast (Miracast, Wi-Fi Display) to external displays, monitors, TVs, and XR/AR smart glasses.

---

## 1. Core Architectural Pillars

### 1.1 Native 16:9 Secondary Presentation Architecture
- Rather than mirroring the phone's non-standard aspect ratio (19.5:9 or 20:9) which produces ugly vertical pillarboxes on 16:9 or 21:9 displays:
  - Uses `android.app.Presentation` bound to the external `Display` instance.
  - Dynamically calculates display resolution, density (DPI), and refresh rate.
  - Launches secondary desktop launcher activities targeted via `ActivityOptions.setLaunchDisplayId(displayId)`.

### 1.2 Widevine L1 Hardware DRM Workaround ("Playback Detach")
- Standard wireless casting or software desktop launchers fail when rendering Widevine L1 protected content (Netflix, Disney+, Apple TV+, Prime Video, Max), producing a black screen with audio only due to HDCP / secure display pipeline restrictions.
- **Xternal Control Solution**:
  - Employs a hardware "Playback Detach" sequence.
  - The phone tracks media sessions and routes the target video player activity directly to the primary hardware-accelerated surface on the external display without intermediary virtual display wrappers.
  - Disables overlay draw passes during active DRM playback so Android's `SurfaceView` / `MediaCodec` secure rendering path is never invalidated.

### 1.3 Precision Multi-Touch Trackpad
- Turns the phone screen into a multi-touch trackpad:
  - 1-finger drag: Cursor movement with dynamic acceleration curve (exponential smoothing).
  - 1-finger tap: Left click.
  - 2-finger tap: Right click / Context menu.
  - 2-finger vertical/horizontal drag: Smooth scrolling via `MotionEvent.ACTION_SCROLL` / `AXIS_VSCROLL`.
  - Non-intrusive cursor overlay on external screen with `FLAG_NOT_FOCUSABLE` and `FLAG_NOT_TOUCHABLE` to prevent blocking keyboard interactions or app taps.

### 1.4 Smart Glasses Optimization (XREAL, Rokid, RayNeo, Viture)
- Full support for 60Hz, 90Hz, and 120Hz micro-OLED optical engines.
- True Black (`#000000`) HUD & Dark Mode:
  - Micro-OLED smart glasses turn off black pixels completely.
  - A 100% black desktop mode makes the background transparent in the user's field of view, presenting a clean floating AR head-up display without annoying light rectangular frames.

### 1.5 Universal Media Remote
- Independent media controls overlay on the phone: Play/Pause, Skip Next/Previous, Rewind/Fast Forward 10s, Volume Slider, Mute.
- Uses `MediaSessionManager.getActiveSessions()` to send transport commands straight to the active media player regardless of display focus.

### 1.6 Lightweight Native Codebase
- Written in clean Kotlin with minimal overhead:
  - Jetpack Compose for phone UI.
  - Native Android View / Canvas rendering for high-performance external presentation.
  - No bloated web engines or unnecessary heavy dependencies (keep total size lean and well under 100MB).
