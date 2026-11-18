# Game Space Reborn — Build 0.1 scaffold

A Vivo GameCube-inspired, non-root gaming assistant scaffold for Android 10+ (targeting Android 15/API 35, tested target device intended: Realme RMX5003 on Android 16). It integrates the official Shizuku API and includes a Shizuku UserService that exposes only fixed, read-only diagnostics.

## Current features
- Compose UI with dark, gaming-inspired panels
- Free Fire MAX package detection and launch (`com.dts.freefiremax`)
- Shizuku binder status and permission request
- Shizuku UserService binder connection
- Fixed read-only diagnostics: shell UID/PID, `/proc/meminfo`, build fingerprint, Android release property, Free Fire MAX GameManager modes/configs, thermal service, display modes and battery state
- Profile preference switches persisted locally (placeholder settings only)
- GitHub Actions debug APK build

## Not implemented / not verified
- FPS unlock/force, CPU/GPU frequency or overclocking
- Thermal-limit bypass or thermal policy changes
- Touch injection, touch sensitivity, touch firmware changes
- System notification suppression
- Refresh-rate switching
- Floating overlay implementation (permission is declared only; no overlay UI is shipped)
- Device-specific tuning on Realme RMX5003
- GameManager/thermal/display diagnostic results have not yet been collected from the target device; command availability and OEM behavior must be validated there

The preference switches currently save values only. They do not execute system changes or claim performance improvements. Diagnostics are read-only. The service exposes a fixed diagnostic ID allowlist and accepts no arbitrary command text. New checks use fixed read-only `cmd game list-modes`, `cmd game list-configs`, and `dumpsys` invocations. The app does not call any GameManager mode-setting command. Diagnostic output is limited to 6,000 characters per command.


## Build with GitHub Actions (phone-friendly)
1. Create a GitHub repository and upload these project files.
2. Commit the project to the `main` branch.
3. Open **Actions** → **Game Space Reborn APK Build**.
4. Run **Run workflow** (or let a push trigger it).
5. When successful, open the workflow run and download the `GameSpaceReborn-debug` artifact.
6. Extract the ZIP artifact and install `app-debug.apk`.


## Local build
Requires JDK 17 and Gradle 8.9. From the project directory:


```bash
gradle assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.


## Shizuku setup on Android 11+
1. Install Shizuku from its official source.
2. Turn on Developer options and Wireless debugging.
3. Pair Shizuku with the Wireless debugging pairing code.
4. Start the Shizuku service.
5. Open this app and tap **REQUEST SHIZUKU PERMISSION**.
6. Approve the app in Shizuku, then tap **CONNECT READ-ONLY DIAGNOSTICS**.

If the UserService connects, the Shell UID diagnostic should report UID `2000` in ADB-backed Shizuku mode. Root-backed Shizuku reports UID `0` and is not the intended setup for this project.


## Shizuku API dependency
The project uses Shizuku API and provider version `13.1.5` (official dependency coordinates). The provider is declared in `AndroidManifest.xml` per the official integration guide.


## Build status
This scaffold has not been compiled in this environment yet. The first GitHub Actions run is the compile validation. If it fails, share the Actions log and I can patch it. No feature has been tested on-device yet.


## License and assets
Original project code scaffolded for this user. No Vivo GameCube icons, proprietary code, or branding assets are included. The visual inspiration is a dark gaming control-panel aesthetic only.
