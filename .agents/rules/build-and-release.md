# Build, Signing, and Release Protocol

## 1. Prerequisites & Environment
- **Operating System**: Windows
- **JDK Location**: `D:\Program Files\Android\Android Studio\jbr` (JDK 21)
- **Keystore**: `keystore/release-key.jks`
- **Gradle Command**: Always specify `--no-daemon` and set `$env:JAVA_HOME` explicitly.
- **Terminal Execution Requirement**:
  - Gradle builds bind internal sockets (`127.0.0.1:<port>`) which are blocked by the default Windows sandbox.
  - File copying to `X:\` requires filesystem elevation.
  - **Always use `BypassSandbox: true`** when executing Gradle compilation or artifact distribution commands.

---

## 2. Standard Build & Assembly Commands

```powershell
$env:JAVA_HOME = "D:\Program Files\Android\Android Studio\jbr"
./gradlew --no-daemon assembleGithubRelease assemblePlaystoreRelease bundlePlaystoreRelease
```

---

## 3. Artifact Naming & Destination Protocol
Upon successful compilation, every release version `vX.Y.Z` produces 3 standard artifacts:
1. `app-github-release-vX.Y.Z.apk`
2. `app-playstore-release-vX.Y.Z.apk`
3. `app-playstore-release-vX.Y.Z.aab`

These files **MUST** be copied to two locations:
1. **Repository Root**: `D:\Projects\Programming\xternal control\`
2. **Software Storage Archive**: `X:\Softwares\`

### Standard Copy Snippet:
```powershell
$version = "1.5.3"
$outDir = "app\build\outputs"
$destRoots = @("D:\Projects\Programming\xternal control", "X:\Softwares")

foreach ($dest in $destRoots) {
    Copy-Item "$outDir\apk\github\release\app-github-release.apk" "$dest\app-github-release-v$version.apk" -Force
    Copy-Item "$outDir\apk\playstore\release\app-playstore-release.apk" "$dest\app-playstore-release-v$version.apk" -Force
    Copy-Item "$outDir\bundle\playstoreRelease\app-playstore-release.aab" "$dest\app-playstore-release-v$version.aab" -Force
}
```

---

## 4. Version Increment Checklist
When preparing a new release:
1. Update `versionCode` (+1) and `versionName` (`vX.Y.Z`) in `app/build.gradle.kts`.
2. Check if a trial reset is intended:
   - For major/minor upgrades: update `CURRENT_TRIAL_EPOCH` and `KEY_TRIAL_EPOCH_RESET_TIME`.
   - Token files will automatically shift to the new version (e.g. `xt_license_v2.bin`).
3. Compile all flavors.
4. Verify file sizes (~5MB for APKs, ~5.1MB for AAB).
5. Copy to root and `X:\Softwares\`.
6. Commit and push changes:
   ```powershell
   git add .
   git commit -m "Release vX.Y.Z (versionCode <code_num>)"
   git push origin master
   ```
