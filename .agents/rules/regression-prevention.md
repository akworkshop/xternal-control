# Regression Prevention Rules & Historical Bug Fixes

To maintain rock-solid reliability across Android updates and manufacturer skins (Samsung One UI, Google Pixel, Xiaomi HyperOS, Vivo OriginOS), all developers and agents must adhere strictly to these constraints:

---

## 1. Free Trial & Anti-Tamper License Management

### Historical Failure (v1.5.2 Lockout Bug):
- In `v1.5.2`, when updating from `v1.5.1`, Android `MediaStore` created a copy of the existing license file (`xt_license_v1 (1).bin`).
- `onPreExistingTrialDetected()` interpreted this unexpected file as a sign of clock tampering / data wiping, forcing `trialStartTime = 1L` (instantly expired).
- Users who upgraded were immediately locked out even with days remaining on their trial.

### Mandatory Rules:
1. **Reset Epoch Validation**:
   - Never trigger tamper expiry (`trialStartTime = 1L`) if a trial reset epoch is active:
   ```kotlin
   val isResetWindow = (System.currentTimeMillis() - resetEpochTime) < 48 * 60 * 60 * 1000L
   if (isResetWindow) {
       // Must NOT expire trial on pre-existing trial detected
       return
   }
   ```
2. **Versioned License Files**:
   - Token files must include the epoch version: `xt_license_v2.bin`, `xt_device_state_v2.bin`.
   - When bumping `CURRENT_TRIAL_EPOCH`, old version files (`xt_license_v1.bin`) must be safely ignored or cleaned up without triggering anti-tamper penalties.
3. **Pro License Immunity**:
   - Once a device is marked Pro (`isProActivated = true`), never attempt to read, write, or validate trial files.

---

## 2. Media Remote Focus Drop & Streaming App Key Event Loss

### Historical Failure (Samsung DeX & Apple TV+):
- When playing content on Apple TV+, Netflix, or Disney+ on an external screen or DeX, tapping "Play/Pause" or "Rewind" on the mobile phone shifted Android window focus to the phone.
- The external app dropped standard hardware `KeyEvent` triggers (`KEYCODE_MEDIA_PLAY_PAUSE`) because its window was unfocused.

### Mandatory Rules:
1. **Active MediaSession Direct Dispatch**:
   - Always query `MediaSessionManager` for active notification/playback tokens.
   - Dispatch `transportControls.play()`, `pause()`, `skipToNext()`, `skipToPrevious()`, or `seekTo()` directly to the token.
2. **Hardware Fallback Only**:
   - Only use `AudioManager.dispatchMediaKeyEvent()` or `Instrumentation` if zero active media sessions exist.

---

## 3. On-Screen Keyboard (Gboard) Touch Interference

### Historical Failure (Pixel & Secondary Display Keyboard):
- When typing on the secondary display (or phone touch screen with cursor visible), the cursor overlay view intercepted touch coordinates, causing dropped keystrokes on Gboard.

### Mandatory Rules:
1. **Window Flags**:
   - Any cursor or pointer overlay window added via `WindowManager` must specify:
   ```kotlin
   flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
           WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
           WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
   ```
2. **Visual Offset**:
   - Maintain the visual cursor offset (+3px) so the hit point doesn't occlude touch targets or trigger edge clipping.

---

## 4. Multi-Display Enumeration (Xiaomi / Vivo Foldables)

### Historical Failure:
- Xiaomi 17/18 Pro and Vivo Pad 3 Pro expose virtual secondary displays, rear selfie screens, or internal hinge virtualization surfaces via `DisplayManager`.
- Xternal Control inadvertently attached its `Presentation` to internal sub-screens instead of HDMI/USB-C monitors.

### Mandatory Rules:
1. **Display Filtering**:
   - Only select displays matching:
     - `Display.TYPE_EXTERNAL` (HDMI/DisplayPort), OR
     - `Display.TYPE_WIFI` (Miracast/Wi-Fi Display), OR
     - Display with `Display.FLAG_PRESENTATION` AND display dimensions `width >= 1280 && height >= 720`.
2. **Internal Screen Exclusion**:
   - Explicitly ignore `Display.TYPE_BUILT_IN` and sub-displays tagged as private or non-presentation.

---

## 5. Samsung DeX Setting Coordination

### Historical Failure:
- On Samsung One UI, if Samsung DeX mode is manually toggled off in device settings, launching activities into the external display can result in blank black output or unhandled exceptions.

### Mandatory Rules:
1. Check `SemDesktopModeManager` availability or configuration.
2. Prompt user with clear guidance if DeX mode setting is blocking external activity launches.
