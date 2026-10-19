# MBooru 7 integration validation

## Source and compatibility

The APK application source is `8c802bbbc849890e1423494e5268937a9073e3a6`. A later instrumentation-only commit, `561c24f6f3f6e9c44e7be74964a1c164f07577c6`, adds Android 5 screenshot capture without changing the application binary. [Artifact provenance](m34-release-provenance.json) and [native evidence](m34-native-final.json) record the hashes and exact test sources.

The effective MBooru baseline is `afed22ed54878bb522712cf5707cdc5f0094b007` (6 / 1379, schema 11), including accepted modernization beyond `master` at `d46d3fc1050117951e310c8372e93d2e250cae8c`. [Baseline evidence](m34-baseline.json) records 107 passing app tests and a passing debug build/lint before integration. M34 was inspected at `9b993cb017211df9e9c50e99ba1e7ee18bcae88c`.

MBooru 7 / code 1380 retains `com.mattzilla30.mbooru`, minimum API 21, target API 36 and the existing release signer. No M34 Java source, API 37 requirement or ARM64-only gate was imported. Schema 12 uses the existing migration chain and generated 11→12 migration without destructive fallback.

## Local automated checks

Executed using JDK 21 and the checked-in wrapper:

```sh
./gradlew spotlessCheck detekt :core:model:test :android:assembleDebug \
  :android:testDebugUnitTest :android:lintDebug :android:assembleDebugAndroidTest \
  :android:verifyOfflineProfileManifest
./gradlew :android:assembleRelease
```

| Check | Result |
| --- | --- |
| App unit/Robolectric tests | **127 passed, 0 failures, 0 errors, 0 skipped** |
| Core model tests | **5 passed, 0 failures, 0 errors, 0 skipped** |
| Spotless and detekt | Passed; project rules and baselines were not relaxed |
| Debug build, instrumentation compilation and lint | Passed |
| Offline-profile manifest | Passed |
| Optimized release build | Passed, 3 minutes 53 seconds |
| Dependency license inventory | All 267 components declared or covered by reviewed overrides |

[Per-class unit results](m34-unit-results.json) preserve the test counts. Coverage includes strict Rule34 parsing, tag/rating validation, headers, HTTP errors, cancellation, transactional cursor rollback, local library/import rollback, credential-free exports, download limits/deduplication, navigation and cached grid restoration. A recreated cached query is tested with auto-refresh enabled; a terminal Moebooru page is tested to avoid another request while explicit Refresh still requests data.

Migration fixtures exercise schemas 1–11 → 12. Existing saved snapshots are seeded wherever supported and verified for saved timestamp, attribution and explicit-save status; site IDs, credentials, history and filters survive. Newly introduced library tables begin empty.

Lint initially encountered another variant's generated DAO while KSP regenerated its directory. CI now runs debug checks, release packaging and benchmark-variant generation in sequential invocations, retaining every check. The separated local commands passed. The explicitly opt-in `MediaNetworkTest` is omitted from default deterministic device runs; `MBOORU_LIVE_MEDIA_CHECKS=true` enables it. Its live-service behavior is not counted as passing.

## Signed APK and executed emulator tests

APK SHA-256: `624e511dce9def85cccb325e18a039bd92ffb71d7cb1a397ede4a0304a9add2c`.

`scripts/check_apk.py` verified signer continuity, package/version/SDK levels, absence of ad and billing permissions, compiled baseline-profile assets, 16 KiB ZIP alignment and all 10 packaged ELF64 libraries. No signing files or signing passwords were committed.

| Emulator | Signed 1 → 7 | Signed 6 → 7 | Native tests |
| --- | --- | --- | --- |
| API 21, x86_64 | Passed | Passed | **13 passed, 0 failures, 0 skipped** |
| API 28, x86_64 | Passed | Passed | **13 passed, 0 failures, 0 skipped** |
| API 36, x86_64 | Passed | Passed | **13 passed, 0 failures, 0 skipped** |

[API 28/36 run](https://github.com/mattzilla30/MBooru/actions/runs/39209724790) and [API 21 follow-up](https://github.com/mattzilla30/MBooru/actions/runs/39210406187) used the exact delivered signed APK. The collection script checked all six upgrade-report hashes against that APK, both known baseline hashes and every native test count. Upgrade fixtures verify custom site/history/filter retention, encrypted credential references, saved snapshots, schema 12, empty new library tables, foreign keys and durable settings after restart. All fixtures are artificial; network access was disabled for these runs.

The immutable release-6 baseline crashes on API 21 while drawing an inset favicon fallback. Its fixture initializes its real database without waiting for that failed first frame and records `baselineFirstFrameIssue=true`; candidate launches must survive startup. This does not claim the old version-6 UI works. The same legacy resource problem was fixed in version 7: 24 dp insets could produce nonpositive child bounds inside a 21 dp favicon. A native 21×21-pixel rendering assertion now covers it.

Native checks cover tiled image decoding, zoom/paging interaction, safe-content removal, credentials, cookies, engine XML, primary/secondary navigation, collections, cached scrolling and activity recreation. Screenshot writing uses the target app's own directory; API 21 uses `UiAutomation` because Compose's screenshot path requires `PixelCopy` from API 26. All UI assertions remain enabled. [Home](m34-api36-home.png) and [Settings](m34-api36-settings.png) screenshots show the BGSU theme; the home screenshot intentionally shows the offline fixture state.

Final code CI: [run 39210506193](https://github.com/mattzilla30/MBooru/actions/runs/39210506193) **passed all four jobs** at commit `561c24f6f3f6e9c44e7be74964a1c164f07577c6`. Verification includes release and benchmark-variant compilation, unit tests, lint, formatting, static analysis, offline-profile checks, SBOM and license inventory. Native CI executed **13 tests on API 21, 17 on API 28 and 13 on API 36**, with zero failures/errors/skips; API 28 includes four additional light/dark, large-text/RTL and tablet visual captures. [CI evidence](m34-ci-final.json) records those counts. Final documentation commits do not change the validated application or test code. No release tag has been published.

## Companion M34 exporter

[M34 PR 1](https://github.com/mattzilla30/M34/pull/1) supplies the supported sandbox-safe transfer path. Its [signed API 37 workflow](https://github.com/mattzilla30/M34/actions/runs/39201339120) passed, as did all 123 existing host checks. M34 2 / code 12 matches the public release-1 certificate and passes 16 KiB ZIP alignment. [Exporter verification](m34-exporter.json) records its hash. A physical-device export/import was not executed.

## Remaining production verification

- Live Rule34 access needs a real user ID/API key; fixtures do not establish current service availability or account entitlement.
- Physical ARM64/API 37 testing and hardware profiling of large-image/video gestures, memory and low-memory behavior remain unavailable.
- Exercise M34 → document provider → MBooru transfer on installed devices, including permissions and interrupted imports/exports.
- Exercise device-credential app lock with a configured screen lock, cancellation and background/foreground transitions on hardware.
- Exercise downloads through process death, network loss, full storage and revoked SAF permission. Deterministic transfer/storage tests do not replace these device scenarios.
- Review accessibility and additional device sizes; no physical-device performance measurements are claimed.

These remaining checks prevent a production-ready claim. The signed APK is a reviewable release candidate.
