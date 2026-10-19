# MBooru

<img src="art/icon.png" alt="MBooru app icon" width="160" />

Android booru client with an orange “M,” brown “B,” and warm cream launcher icon.

## Build

Use JDK 17 or a compatible newer JDK, Android SDK 35, and the included Gradle 8.11.1 wrapper:

```sh
./gradlew :android:assembleDebug :android:testDebugUnitTest :android:lintDebug
```

The build uses AGP 8.9.2 and compile SDK 35. Target SDK is 35 and minimum SDK remains 21. Android 15 inset handling and foreground downloads are implemented; device validation remains outstanding. The Kotlin/Compose checkpoint uses Kotlin 2.1.21, its matching Compose compiler plugin, KSP 2.1.21-2.0.2, and Compose BOM 2025.07.00 (Compose 1.8.3 / Material 3 1.3.2). Room 2.7.2 and Lifecycle 2.8.7 retain Android 5 support. The [original modernization roadmap](docs/modernization-checkpoints.md) is retired; use the [active roadmap](docs/future-modernization-plan.md). Compatible AndroidX, CameraX, Media3 and barcode-scanner updates are tracked there separately from target-SDK behavior changes.

The application ID and Kotlin namespace are `com.mattzilla30.mbooru`. MBooru installs separately from the original application; its app data is separate.

## UI

Screen layouts use Jetpack Compose with Material 3, retaining the existing theme and preferences. See [migration structure and verification](docs/compose-migration.md).

## Signing with GitHub Actions

The workflow is `.github/workflows/android-release.yml`. Set repository secrets `KEY_ALIAS`, `KEY_PASSWORD`, `KEYSTORE_BASE64`, and `KEYSTORE_PASSWORD`, then run the workflow manually. Signing files are created under `.gradle/` and removed after the build. Never commit your keystore or secret values.

## Optional Firebase

The upstream Firebase configuration has been removed. To enable Analytics and Crashlytics, register `com.mattzilla30.mbooru` in your own Firebase project and put its downloaded configuration at `android/google-services.json`. Without that file, the Google Services and Crashlytics build plugins are not applied. Do not edit another app's Firebase identifiers to imitate a new registration.

MBooru is completely free: no ads, purchases, site-count limits, or paid feature checks. Backups, pool downloads and local favorites are available to everyone. Accounts are optional for the included servers. Muzei, SauceNAO and WhatAnime integrations have been removed.

Three presets are added once on first launch or upgrade: **Yande.re, Konachan, and Safebooru**. They support anonymous browsing through their configured endpoints. Optional accounts remain available. Danbooru, Gelbooru, Sankaku, and the factory Sample entry are removed on upgrade. Settings, credentials and dependent records for retained servers are preserved. Use **Add server → Add booru → Featured sites** to restore an individually deleted preset.

Copyright © 2026 mattzilla30 <mattzilla30@gmail.com> for MBooru modifications. Based on Flexbooru by onlymash and contributors; original notices and GPL licensing are retained. See [attribution](NOTICE.md).

## Modernization and validation

See the [seven-area modernization status](docs/modernization-status.md) for implemented changes, reproducible checks, Baseline Profile generation and outstanding device/service validation. Android CI runs unit/lint checks and API 28/35 emulator smoke tests before the signed-release workflow can proceed.

## Future modernization

See the [full modernization roadmap](docs/future-modernization-plan.md) for platform support, asynchronous persistence, state/navigation, networking/authentication, durable downloads, media performance, adaptive accessibility, modules and release maintenance. This is a staged future plan; the current app keeps its existing visual design.

Releases use plain integer names/tags, starting with `1`. Android version codes remain separate monotonic integers (`1374` for release `1`) so existing installations can upgrade. Source provenance is recorded by the release tag; embedded VCS metadata is disabled for release APKs to avoid recording a base commit from a working-tree build.

UI design credit: [Boorusphere](https://github.com/azrim/boorusphere), copyright 2021 Chaldeaprjkt (BSD-3-Clause). See [adaptation details and bundled license](docs/boorusphere-ui.md).

## M34 integration

The integrated Rule34 connection, direct navigation, local likes, named collections and versioned library transfer are documented in [the M34 integration guide](docs/integration/m34.md). Android API 21 compatibility and the existing MBooru package/signing identity are preserved. See [release 12 settings organization](docs/releases/12.md), [release 7 integration notes](docs/releases/7.md) and [the integration validation report](docs/validation/m34-integration.md) for signed upgrades, executed tests and remaining device/service checks.
