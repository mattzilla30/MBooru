Current roadmap implementation: see [completion and evidence](modernization-completion.md). The earlier seven-stage record below is retained for history.

# Modernization pass

The existing design, Android 5 minimum (API 21), Android 15 target (API 35), package ID and signing-secret names are retained.

| Area | Implemented in this pass | Verification still requiring another environment |
| --- | --- | --- |
| Navigation | Browsing is entirely Compose, with navigation-entry ViewModels and retained paging caches. All app-owned activity bars use Compose. Settings, About and the site editor retain content-only AndroidFragment interop; material dialogs, media and CameraX retain their specialized native integration. | Hardware back/gesture/shared-image transitions. |
| Loading/errors | Empty states, connectivity feedback, safe error copy, retries for paging, image searches and viewer images. | Representative remote services and authentication. |
| Background work | Streaming downloads larger than the image cache, safe directory grant replacement, cancellable IO, dataSync timeouts, unique downloads/Muzei publication, cache-snapshot cleanup, progress with unknown lengths, API-appropriate backup rules. New pool requests reference the site ID rather than persisting credentials in WorkManager input. | Camera/notification denial, SAF providers, foreground timeout/reboot and long-running transfers. |
| Dependencies | Kotlin-compatible networking/serialization/Coil updates; Firebase BOM 33.16 and current build plugins; AndroidX Test/Robolectric updates. Compatibility pins retain API 21. | Live Firebase and site/service configuration. |
| Accessibility | Localized post descriptions/action labels, 48 dp interactive avatars/links, scalable app bars, distinct empty/error feedback, legacy-palette body-text contrast checks. | TalkBack, switch access, device font/display scaling and dynamic-color combinations. |
| Performance | Destination-scoped paging models, bounded 256 MiB image cache, Baseline Profile consumer/generator, and a startup/tab Macrobenchmark module. | Generating the real profile and measuring frame timing, memory, large-image gestures and video on hardware. |
| CI | PR/push unit/lint/build checks, Android 9/15 emulator smoke matrix, signed-release gating and a profile-generation workflow. | Actual GitHub Actions execution after these changes are pushed. |

## Build and tests

```sh
./gradlew --no-daemon --max-workers=2 :android:assembleDebug :android:testDebugUnitTest :android:lintDebug :android:assembleDebugAndroidTest :android:assembleRelease :baselineprofile:assembleNonMinifiedRelease
```

A local release is unsigned without the existing signing configuration. Do not replace the existing release keystore. GitHub Actions keeps using `KEY_ALIAS`, `KEY_PASSWORD`, `KEYSTORE_BASE64` and `KEYSTORE_PASSWORD` after required checks pass.

## Real Baseline Profile and hardware measurements

No profile is fabricated or checked in without executing the generator. On an Android 13+ device (or a rooted Android 9–12 device), or through **Generate Android Baseline Profile** in Actions:

```sh
./gradlew :android:generateReleaseBaselineProfile
```

Review the generated files under `android/src/release/generated/baselineProfiles/`, then commit those generated profile files. The profile plugin and ProfileInstaller consume the real output on subsequent releases. The workflow uploads generated profiles rather than writing to the repository automatically.

Run timing measurements on a physical device after generating/installing a profile:

```sh
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mattzilla30.mbooru.benchmark.BrowseBenchmark -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark
```

Benchmark output is under `baselineprofile/build/outputs/`. The included journey measures cold startup and tab navigation; it does not establish large-image, paging, memory or video performance. For those checks, use a representative configured site, load a long grid, record frame timings during scroll, inspect memory while paging full-resolution images, and verify pinch/dismiss/video behavior and activity recreation. Compare the same dataset/device/build mode before and after changes.

The cloud host has no KVM device, and its software emulator previously failed during startup. Device execution and timing measurements are not claimed as completed here.

## Publisher configuration

MBooru is fully free. Ads, billing, payment screens, remote order checks and all paid feature gates have been removed, including the three-site switcher limit. A Play product or signing-certificate property is no longer required. Firebase remains optional and requires MBooru's own `android/google-services.json`.

Danbooru, Yande.re, Konachan, Gelbooru, Safebooru and Sankaku presets are installed once for new and existing users, using conflict-ignore inserts to preserve existing IDs, credentials and filters. Deleted presets stay deleted and can be restored individually using **Manage boorus → Add booru → Featured sites**. Site accounts, API keys, availability and service-side limits are separate from MBooru and are not fabricated or bypassed.

## Modernization validation before the free-app update

- Debug and unsigned optimized release APK builds passed.
- All 53 unit tests passed, with zero failures, errors or skips.
- Lint passed with zero errors (558 warnings and 7 informational findings remain).
- Android device-test APK and Baseline Profile/benchmark test APK compilation passed; the benchmark APK signature was verified.
- Both app APKs retain minimum SDK 21 and target SDK 35. ZIP alignment and all ten packaged 64-bit native libraries pass static 16 KB alignment checks.
- Room schema exports and identity hashes are unchanged. Workflow YAML structure and release-check dependencies were validated locally; signing-bundle workflow copies are synchronized.

Device tests, real Baseline Profile generation, physical-device timings and live publisher/service tests were not run. GitHub Actions execution also awaits pushing these changes. These are outstanding execution requirements, not passing checks.

## Free app and preset update

Debug and optimized release builds passed; the device-test APK compiles. All 56 unit tests passed, with zero failures, errors or skips. Lint passes with zero errors (543 warnings and 7 informational findings remain). Regression coverage checks preset installation, preserving existing credentials/custom sites, duplicate-safe restoration, respecting deletions, and switching beyond three sites without a purchase action.

The release APK was signed with the existing MBooru key and verified against the previous release certificate. Signature, ZIP alignment, package ID, minimum/target SDK 21/35, and absence of billing/advertising-ID permissions and ads-serving/billing SDK classes were verified in the signed APK. Real-device and live-service validation remain outstanding.

## About, attribution and sample retirement update

The original factory `Sample` site (`https://moe.fiepi.com`, Moebooru) is removed when opening the main/site-management screen, including upgrades where the six presets were already installed. Normal site-selection repair selects a remaining site. Renamed configurations at the former sample host and unrelated sites named Sample are preserved; deleting the retired site also removes its dependent cached/history/filter/Muzei rows through the existing foreign keys.

About no longer includes Translation, Rate this app or Website. Email is `mattzilla30@gmail.com`; Discord displays and copies `musiccat01` without inventing a profile/invite URL. Primary MBooru modification attribution is © 2026 mattzilla30, with the original Flexbooru/third-party notices and GPL licensing retained. See [NOTICE](../NOTICE.md).

Debug/release builds, unit tests, lint and device-test APK compilation passed. All 58 tests pass with no failures, errors or skips; lint reports zero errors (548 warnings and 7 informational findings remain). The updated APK is signed with the existing release key; signer continuity, signature, alignment, app metadata, free-app permissions/SDK removal and the updated contact resources were verified. Device/live-site execution remains outstanding.

The [future modernization roadmap](future-modernization-plan.md) describes the next phases; it is not an assertion that those future migrations are implemented. The current app retains target SDK 35. The roadmap prioritizes a tested API 36 migration for current Play submission requirements.
