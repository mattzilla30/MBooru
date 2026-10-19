# MBooru 7 integration validation

## Baseline and implementation

The integration starts from MBooru `afed22ed54878bb522712cf5707cdc5f0094b007` (6 / 1379, schema 11), which includes accepted modernization beyond `master` at `d46d3fc1050117951e310c8372e93d2e250cae8c`. The inspected M34 reference is `9b993cb017211df9e9c50e99ba1e7ee18bcae88c`. [Baseline evidence](m34-baseline.json) records 107 passing app tests and a passing debug build/lint before integration.

MBooru 7 / 1380 retains `com.mattzilla30.mbooru`, minimum API 21 and target API 36. Schema 12 uses the existing migration chain and generated 11→12 migration without destructive fallback. No M34 Java source, API 37 requirement or ARM64-only gate was imported.

## Local automated validation

Executed with JDK 21 and the checked-in Gradle wrapper:

```sh
./gradlew spotlessCheck detekt :core:model:test :android:assembleDebug \
  :android:testDebugUnitTest :android:lintDebug :android:assembleDebugAndroidTest \
  :android:verifyOfflineProfileManifest cyclonedxBom
```

- Complete command: **passed** (3 minutes 41 seconds), including Spotless, detekt, lint, debug/instrumentation APK compilation, offline-profile manifest and SBOM generation. License inventory passed for all 267 components.
- App: **126 tests, 0 failures, 0 errors, 0 skipped**.
- Core model: **5 tests, 0 failures, 0 errors, 0 skipped**.
- Focused checks cover Rule34 parsing, query/rating rejection, headers, HTTP failures, cancellation and transactional paging cursor rollback; schema 1–11 migration to 12; local likes/collections and library import rollback; credential-free exports; download bounds/deduplication; navigation, cancel/refresh and cached grid restoration after activity recreation.
- Migration fixtures seed existing saved snapshots wherever their schema supports them and verify saved timestamp, attribution, explicit-save status, source IDs and other legacy data survive. Newly introduced library tables start empty.
- Instrumentation compilation is recorded separately from actual native execution below.

## Companion M34 exporter

[M34 PR 1](https://github.com/mattzilla30/M34/pull/1) provides the supported sandbox-safe transfer path. Its [latest signed API 37 workflow](https://github.com/mattzilla30/M34/actions/runs/39201339120) passed. All 123 existing host checks passed. The downloaded M34 2 / code 12 APK matches the public release-1 signing certificate and passes 16 KiB ZIP alignment. [Public artifact verification](m34-exporter.json) records its SHA-256. This does not establish an executed device export/import.

## Signed MBooru APK and native runs

Release signing, APK verification and API 21/28/36 native upgrade/instrumentation validation are pending at this checkpoint. Their final outcomes will be appended before delivery. No release tag has been published.

## Remaining production checks

- Live Rule34 account tests need a real user ID/API key. Fixtures do not establish service availability, account entitlement or the behavior of every live site.
- Physical ARM64/API 37 testing, large-image/video gesture and memory profiling are unavailable in this cloud environment. Existing native viewer/Media3 infrastructure is retained; no new hardware performance measurements are claimed.
- Exercise Android document-provider export/import between installed M34 and MBooru, including revoked permissions, process interruption and user-selected storage providers.
- Exercise device-credential app lock with a configured PIN/password/pattern on hardware, including background/foreground and credential cancellation.
- Review screenshots/accessibility on varied screen sizes, and perform an offline user-library backup before installing any pre-release candidate on a primary device.

These gaps prevent a claim that the candidate is production-ready. No live user database or production account was used in migration fixtures.
