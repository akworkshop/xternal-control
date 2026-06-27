# Xternal Control 📱🕶️

**Xternal Control** is a premium, ad-free, obsidian-themed Android utility application designed to control external displays, projectors, smart AR glasses (like XREAL, Viture), or **wireless screen casts (TVs, Chromecast, Miracast)** using your phone or tablet as a smart trackpad, launcher, and keyboard.

---

## 📺 Why Xternal Control? (The Problem vs. The Solution)

### The Problem: Standard Screen Mirroring
Whenever you cast your Android phone to a big screen, monitor, or AR glasses, it usually mirrors your phone screen's vertical aspect ratio. This results in huge black bars on the left and right, wasting screen real estate. 

When plugging into AR glasses (like Viture XR glasses), standard portrait mirroring crops the display. Furthermore, existing VR/AR launcher apps (like SpaceWalker) might offer a fullscreen desktop, but they restrict you to their built-in browser or custom apps—you can't select and run *any* third-party app you want.

### The Solution: True Fullscreen Extension
**Xternal Control** treats the external TV, monitor, or XR glasses as an independent **extension monitor** using its native landscape resolution (e.g., 1080p). You get a full, cinematic widescreen experience! Open **any** application installed on your phone on the big screen and control it smoothly using your phone as a high-precision trackpad.

| Standard Mirroring (Black Bars) | Xternal Control (True Widescreen) |
|:---:|:---:|
| ![Mirrored Display](screenshots/mirrored_display.jpg) | ![Xternal Control Fullscreen](screenshots/connected_display.jpg) |
| Standard portrait mirroring mirrors the aspect ratio | Xternal Control unlocks the native fullscreen resolution |

---

## 🚀 Key Features

* **Dual-State Interaction**:
  - **Physical Mode**: Connect real AR glasses or external monitors and control system-wide applications.
  - **Simulator Mode**: Split your tablet screen side-by-side to test and play with a virtual smart glasses interface locally.
* **Responsive Multi-Tab Controller**:
  - **SETUP**: Easily verify system permissions and manage display connections.
  - **APPS**: Search, sort, and launch installed apps on the glasses display.
  - **REMOTE**: A spacious trackpad grid, dedicated Left/Right click panels, and hardware-style navigation buttons (Back, Home).
* **Multi-Level App Sorting**:
  - **Favourites First**: Long-press any app card to star it. Starred apps bubble to the top of the launcher with a gold star badge.
  - **Chronological Recents**: Recently launched apps automatically list next, sorted by recents first.
  - **Alpha Rest**: All other applications follow alphabetically.
* **Low-Latency Gesture Engine**:
  - Precision trackpad supports cursor movement, vertical/horizontal scrolling, horizontal photo swiping (swapping), and double-tap right clicks.
  - Custom pinch-to-zoom gesture parser triggers native zoom-in/out actions in mapping or web browser applications.
* **Persistent Settings**: Recents, favourites, and settings are preserved across app restarts.
* **Accessibility Integration**: Focuses and injects inputs (clicks, scrolls, navigation commands) directly into third-party apps (e.g., YouTube, Chrome) on physical external screens.

---

## 📸 App Interface

Take control of your external display with our clean, high-contrast three-tab interface:

| 1. SETUP | 2. APPS Launcher | 3. REMOTE Trackpad |
|:---:|:---:|:---:|
| ![Setup Tab](screenshots/setup_tab.jpg) | ![Apps Tab](screenshots/apps_tab.jpg) | ![Remote Tab](screenshots/remote_tab.jpg) |
| Grant overlay & accessibility permissions | Fast search, favorites first, and recent app sorting | Spacious trackpad, left/right clicks, & navigation buttons |

---

## 🛠️ Requirements & Setup

### Permissions Required
Due to its remote control nature, **Xternal Control** requires two key Android permissions:
1. **System Overlay (Draw over other apps)**: Required to render the custom glowing pointer cursor over other applications on the secondary screen.
2. **Accessibility Service**: Required to inject navigation actions (Back, Home) and gestures (clicks, scrolls, pinches) on the secondary display context.

> [!NOTE]
> The app is completely safe. The Accessibility Service is strictly used locally to inject remote input actions on the external display and collects no data.

---

## 📲 Installation

> [!IMPORTANT]
> **Ad-Free and Play Protect Installation Warning**
> * **100% Ad-Free**: This app has absolutely no advertisements, trackers, or hidden fees.
> * **"Install anyway" Warning**: Because this app requires system accessibility control to inject cursor gestures and clicks on your behalf, Google Play Protect might flag it as coming from an "Unknown Developer." During installation, click the details arrow and select **"Install anyway"** to proceed.

1. Download the latest signed package `app-release.apk` from the **Releases** tab.
2. Sideload the APK onto your Android phone or tablet.
3. Open the app, navigate to the **SETUP** tab, and toggle the permissions:
   - Grant **System Overlay**.
   - Enable **Xternal Control** in your system **Accessibility** settings.

---

## 🕹️ How to Use

### Mode A: Side-by-Side Simulation (Demo Mode)
1. Tap **SIMULATE GLASSES** in the **SETUP** tab.
2. The screen will split: the left side is your controller, the right side is your virtual AR glasses screen.
3. Move your finger on the trackpad to watch the cursor overlay move on the virtual screen.
4. Launch apps (Browser, Notes, Satellite Map) and use clicks, scrolling, and pinch-to-zoom to interact.

### Mode B: Secondary Display & Screen Casting (Active Mode)
1. Connect physical AR glasses/monitor (via USB-C DP Alt Mode, HDMI) **OR** start a **wireless Screen Cast / Wireless Display connection** to your TV or Chromecast.
2. The app badge will update to **CONNECTED** and launch the desktop dashboard overlay on the target display.
3. Navigate to **REMOTE** to control any system application on the casted screen.

---

## ☕ Support the Creator

If you find this project useful, please consider supporting the creator! Your contributions help support my family with our special needs child.

* **Buy Me a Coffee**: [buymeacoffee.com/akworkshop](https://buymeacoffee.com/akworkshop)

---

## 📄 License
This project is open source and available under the [MIT License](LICENSE).
