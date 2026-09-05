# Privacy Policy for Xternal Control

**Effective Date:** September 5, 2026  
**Last Updated:** September 5, 2026  

**Xternal Control** ("we", "our", or "the app") is committed to protecting your privacy. This Privacy Policy explains our practices regarding data collection, usage, and permissions.

---

## 1. Zero Personal Data Collection
Xternal Control is designed to operate completely offline.
* **We do not collect, store, transmit, or sell any personal data.**
* **We do not collect analytics, telemetry, device identifiers, or tracking data.**
* **No user registration or account is required.**

---

## 2. Permissions and How They Are Used

Xternal Control requests specific Android permissions strictly to provide local remote control and secondary display features:

### A. Accessibility Service API (`BIND_ACCESSIBILITY_SERVICE`)
* **Purpose:** Xternal Control uses the Android `AccessibilityService` API solely to dispatch remote cursor clicks, long-press gestures, scroll gestures, and perform standard navigation actions (Back, Home, Recents) on connected external displays (such as XR smart glasses and external monitors).
* **Privacy Assurance:** The Accessibility Service **does not** read or monitor your keystrokes, personal messages, passwords, screen text, or personal information. It operates entirely on-device and transmits zero data.

### B. Display / System Overlay (`SYSTEM_ALERT_WINDOW`)
* **Purpose:** Used to render the cursor pointer overlay and user interface elements on connected external displays and XR glasses.

### C. Media Storage / Photos (`READ_MEDIA_IMAGES` / `WRITE_EXTERNAL_STORAGE`)
* **Purpose:** Used strictly when you choose to:
  1. Pick a custom wallpaper image from your gallery for your glasses desktop.
  2. Save an external screen screenshot to your device's `Pictures/XternalControl` folder.
* **Privacy Assurance:** We only access the specific image file selected or saved by you.

---

## 3. Third-Party Services & Links
Xternal Control does not embed third-party tracking SDKs, advertising networks, or analytics services.

---

## 4. Children's Privacy
Xternal Control does not collect any personal information from anyone, including children under the age of 13.

---

## 5. Changes to This Policy
If we update this Privacy Policy, the revised version will be posted here with an updated effective date.

---

## 6. Contact Us
If you have questions or suggestions regarding this Privacy Policy, please reach out via GitHub:
* **Repository:** [https://github.com/akworkshop/xternal-control](https://github.com/akworkshop/xternal-control)
* **Developer:** AK Workshop
