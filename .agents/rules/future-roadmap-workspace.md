# Future Roadmap: Virtual Workspaces & Multi-Window Desktop

> **Agent Instruction**: When the user asks to "work on the workspace feature" or "implement the multi-window desktop feature", use this document as the authoritative specification. Do not ask the user to explain the feature from scratch.

---

## 1. Feature Vision
Transform Xternal Control from a single-app secondary presentation launcher into a full-fledged **Multi-Workspace Desktop Environment** designed specifically for external monitors, TVs, and AR glasses.

---

## 2. Core Components & Architecture

### 2.1 Workspace Model (`WorkspaceManager`)
Organize applications into discrete, purpose-driven virtual workspaces:
1. **Work / Productivity**: Browser, Docs/Sheets, Slack, Email, Terminal.
2. **Media / Entertainment**: Netflix, YouTube, Apple TV+, Spotify, Plex.
3. **Gaming**: Emulators, Cloud Gaming (GeForce NOW, Xbox Cloud), Native Android Games.
4. **Utilities / Personal**: File Manager, Settings, Notes, Calculator.

```kotlin
data class Workspace(
    val id: String,
    val name: String,
    val iconRes: Int,
    val wallpaperColor: Long, // Supports OLED true black (#000000)
    val pinnedApps: List<String>, // Package names
    val openTasks: MutableList<WorkspaceTask> = mutableListOf()
)
```

### 2.2 Mobile Controller Switcher
- The phone screen serves as the **Workspace Command Deck**:
  - **Quick Workspace Dock**: Bottom bar showing 1-tap workspace tabs (`Work`, `Media`, `Game`, `Util`).
  - **3-Finger Swipe Gesture**: Swiping 3 fingers left/right on the phone trackpad instantly shifts workspaces on the external display.
  - **Task Switcher / Mission Control**: Tapping a dedicated switcher button displays open tasks in the active workspace as live preview cards.

### 2.3 Window Management Modes (External Screen)
1. **Full-Screen Single App Mode** (Current behavior):
   - Perfect for streaming media and gaming.
2. **Dual-App Split-Screen (Tiling)**:
   - 50/50 Split (Left / Right or Top / Bottom).
   - 70/30 Productivity Split (Main window + side communication panel).
   - Utilizes `ActivityOptions.setLaunchBounds(Rect)`.
3. **Floating Window Mode (Free-form)**:
   - Resizable window frames rendered on the external presentation surface.
   - Title bar with Minimize, Maximize, and Close buttons.

### 2.4 Cable Disconnect & State Persistence
- When HDMI/USB-C or AR glasses are disconnected:
  - Cache the active workspace layout and open package IDs into `SharedPreferences` / Room database (`workspace_state.json`).
- When reconnected:
  - Automatically offer a 1-tap "Restore previous workspace" prompt on the phone screen.

---

## 3. Implementation Phases

### Phase 1: Virtual Workspaces & Mobile Dock Switcher
- Create `Workspace` data classes and `WorkspaceRepository`.
- Add the Workspace switcher bar to the mobile trackpad UI.
- Filter desktop app icons and pinned shortcuts per workspace.
- Implement 3-finger horizontal swipe gesture on the trackpad.

### Phase 2: Multi-Window Tiling & Split Screen
- Implement `SplitLayoutController` on the `ExternalPresentation`.
- Support launching two concurrent activities side-by-side using `ActivityOptions.setLaunchBounds()`.
- Add draggable divider between split windows.

### Phase 3: Free-form Floating Window Management
- Implement custom window chrome (title bar, resize handles) for supported non-protected apps.
- Add window focus management and z-index ordering.

### Phase 4: State Restoration & AR Glasses Profiles
- Persist state across disconnect/reconnect cycles.
- Create XR Glasses Profile: automatically switch to True Black OLED theme with 120Hz refresh rate lock when XREAL/Rokid/RayNeo/Viture glasses are detected via USB vendor ID.
