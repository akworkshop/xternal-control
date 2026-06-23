# Xternal Control 📱🕶️

**Xternal Control** is a premium, obsidian-themed Android utility application designed to control external displays, projectors, smart AR glasses, or **wireless screen casts (Chromecast, Miracast, wireless displays)** using a phone or tablet as a smart trackpad, launcher, and keyboard. 

It provides an intuitive interface tailored for multi-display Android systems, allowing you to launch applications, navigate system UI, scroll feeds, swipe galleries, and zoom maps on the secondary screen smoothly and with minimal latency.

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

## 🛠️ Requirements & Setup

### Permissions Required
Due to its remote control nature, **Xternal Control** requires two key Android permissions:
1. **System Overlay (Draw over other apps)**: Required to render the custom glowing pointer cursor over other applications on the secondary screen.
2. **Accessibility Service**: Required to inject navigation actions (Back, Home) and gestures (clicks, scrolls, pinches) on the secondary display context.

> [!NOTE]
> The app is completely safe. The Accessibility Service is strictly used locally to inject remote input actions on the external display and collects no data.

---

## 📲 Installation

1. Download the latest release signed package `app-release.apk` from the **Releases** tab.
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

* **Buy Me a Coffee**: [buymeacoffee.com/piccohan107](https://buymeacoffee.com/piccohan107)

---

## 📄 License
This project is open source and available under the [MIT License](LICENSE).
