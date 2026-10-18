# Retired modernization roadmap

Status: aborted/superseded at the user’s request. This document records the original roadmap and completed implementation history; it no longer directs new work. Completed code is retained. The active plan is [the future modernization roadmap](future-modernization-plan.md). Outstanding device/performance verification transfers to its phase 0.

| Checkpoint | Scope |
| --- | --- |
| 1a — Build foundation | Gradle 8.11.1, AGP 8.9.2, compile SDK 35, checksummed wrapper, current Gradle APIs. Target SDK stays 34 and minimum SDK stays 21. |
| 1b — Kotlin and Compose | Kotlin 2.1.21 with its matching Compose compiler plugin, KSP 2.1.21-2.0.2, Compose BOM 2025.07.00 (UI 1.8.3 / Material 3 1.3.2), Room 2.7.2 and Lifecycle 2.8.7. Minimum SDK remains 21. |
| 1c.1 — Compatible Android libraries | Update Activity, Fragment, Navigation, Paging, CameraX, Media3, Core, AppCompat, Material Views, DocumentFile, WorkManager and ML Kit while retaining minimum SDK 21. Inspect packaged native libraries for 16 KB alignment. |
| 1c.2 — Platform behavior | Target SDK 35; shared secondary-screen safe insets, Compose cutout/keyboard insets, dataSync downloads and WorkManager 2.10.1 timeout support. Device checks remain outstanding. Review remaining networking, billing, ads and Firebase updates separately. |
| 2a — Main navigation | Compose NavHost for browsing tabs; AndroidFragment interop, saved destination state, and Compose app bars for Settings/About. |
| 2b — Remaining navigation | Migrate remaining activity app bars and browsing fragment boundaries; preserve restoration and external entry points. |
| 3 — Loading and errors | Standardize retries, empty states, offline feedback and image-loading states. |
| 4 — Background work | Review downloads, Muzei, WorkManager, storage and notifications against current platform requirements. |
| 5 — Accessibility | Check labels, focus, large text, touch targets and contrast. |
| 6 — Performance | Measure paging, memory, large images, gestures and video on hardware; add a Baseline Profile. |
| 7 — CI | Run tests, lint and device smoke checks before producing signed releases. |

For checkpoint 1a, run:

```sh
./gradlew --no-daemon --max-workers=2 :android:assembleDebug :android:testDebugUnitTest :android:lintDebug :android:assembleDebugAndroidTest :android:assembleRelease
```

SDK 35's nullable certificate APIs require safe handling of absent signing information. Regression checks preserve the existing certificate fingerprint format and cover missing modern signing data and empty legacy certificate lists.

A local release build is unsigned when signing files are absent. The GitHub Actions workflow signs with the four configured repository secrets. Building a device-test APK does not establish that device tests ran; the cloud software emulator's startup failure remains documented in the UI migration notes.

The existing language-filter API is retained for AGP 8.9 lint compatibility: its newer `androidResources.localeFilters` API filters APK contents but does not populate the locale list used by lint. Comparing APK locale lists confirmed identical packaged languages; keeping the original API avoids treating unshipped locales as required translations.

Checkpoint 1b replaces the standalone Compose compiler extension version with `org.jetbrains.kotlin.plugin.compose`, sharing the Kotlin version. Room schema exports, including identity hashes, remain unchanged. Existing tests cover settings, theme colors, activity recreation, filtering, paging requests and certificate handling. Device gesture/playback validation remains separate.

Checkpoint 1c.1 aligns Paging runtime and Compose through one catalog version. Its dependency group uses Activity 1.10.1, Fragment 1.8.9, Navigation 2.8.9, Paging 3.3.6, CameraX 1.4.2, Media3 1.5.1, Core 1.15.0, AppCompat 1.7.0, Material Views 1.12.0, DocumentFile 1.1.0, WorkManager 2.9.1 and ML Kit barcode scanning 17.3.0. These are compatibility-focused upgrades, not a claim that every dependency is the latest release.

Checkpoint 1c.2 review and device-validation requirements:

- Review all activity roots and native action bars for Android 15 edge-to-edge insets, including the keyboard, cutouts and three-button navigation.
- Downloads now use `dataSync` with its matching permission. WorkManager 2.10.1 handles the Android 15 timeout callback and stops affected workers. Transfer cancellation releases streams and cached snapshots; foreground-start exceptions surfaced to the worker return failure instead of an immediate retry loop. Test timeout/restart behavior on a device.
- Exercise notification permission denial, camera permission denial, background downloads and process recreation on devices.
- Check native ELF load-segment alignment and APK ZIP alignment. Static alignment checks do not establish runtime compatibility on a 16 KB device.

Newer dependency families can raise the minimum Android version. Preserve Android 5 support when choosing updates; do not override library manifest minimums to force incompatible versions into the app.

For the 1c.1 debug APK, static inspection confirms 16 KB ELF load-segment alignment in all eight packaged 64-bit native libraries (ARM64 and x86-64), and `zipalign -c -P 16 4` passes. The ML Kit ARM32 and x86 binaries retain 4 KB alignment. The APK still declares minimum SDK 21 and target SDK 34. Device verification, including operation on a 16 KB Android system, remains outstanding.

Checkpoint 1c.1 validation passed: debug and unsigned optimized release builds, all 34 unit tests (no failures or skips), lint with zero errors and 550 warnings, and device-test APK compilation. Room schema JSON is unchanged. Both APKs pass static checks for 16 KB alignment of all packaged 64-bit libraries and ZIP alignment. Device tests were not run.

## Android 15 implementation

All BaseActivity windows draw edge-to-edge. Secondary screens inset the complete AppCompat host, including retained action bars, by the union of system bars, display cutouts and the keyboard. Insets are consumed at that host so descendants do not add them twice. Main browsing and search retain Compose-owned insets, with added horizontal safe areas and keyboard padding. The image viewer keeps media full-screen while its controls avoid cutouts. Activities explicitly use `adjustResize` for keyboard inset delivery on older Android versions.

Downloads enter foreground mode before network IO. Coil downloads use its suspend API; stream copying runs interruptibly and checks cancellation between chunks. Progress listeners and cached snapshots are released on failure or cancellation. Completed-download notifications open the chooser directly with an immutable PendingIntent and read access, avoiding a notification broadcast trampoline. Missing notification permission suppresses completion/error notifications without failing file downloads. Retry notifications retain their explicit broadcast receiver and immutable PendingIntent.

WorkManager 2.10.1's published AAR declares minimum SDK 21; its foreground service implements the Android 15 `onTimeout(startId, fgsType)` callback. These capabilities were checked against the dependency artifact and source. WorkManager can also handle foreground-start denial inside its service; verify those stop/restart paths on a device. Android's shared dataSync quota still applies; this change does not promise uninterrupted downloads beyond the platform limit or automatic transfer resumption. Pool downloads restart from the beginning after interruption.

Device release checks remain: portrait/landscape and cutouts; gesture/three-button navigation; keyboard open/close on search and editors; camera denial; notification denial while downloading; cancelling both post/pool downloads; tapping completion/retry notifications; and foreground timeout/restart behavior. The cloud emulator cannot boot, so compiled device tests and static/Robolectric checks do not establish these device outcomes.

Checkpoint 1c.2 validation: debug and unsigned optimized release builds passed; all 38 unit tests passed without skips (including safe-inset updates and interrupted copying); lint reports zero errors and 550 warnings; device-test APK compilation passed. The merged debug manifest and APK metadata confirm `dataSync`, its matching permission, minimum SDK 21 and target SDK 35. Device execution remains outstanding.

Checkpoint 2a validation passed: debug and unsigned optimized release builds, all 42 unit tests (zero failures/skips), lint with zero errors and 552 reported warnings/informational findings, and device-test APK compilation. Regression tests cover tab search state across navigation/recreation, back to posts, site capability changes, drawer recreation, Settings content restoration and its Compose up button. Device execution and hardware performance checks remain outstanding.

## Combined implementation pass

The remaining navigation, loading/error, background-work, dependency, accessibility, performance-infrastructure and CI changes are implemented together. See [the current status and remaining execution requirements](modernization-status.md); earlier checkpoint results above are historical and do not imply that later hardware checks or profile generation ran.
