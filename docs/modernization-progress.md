# Active modernization progress

Started 2026-10-18. The original roadmap is retired; the [active roadmap](future-modernization-plan.md) governs new work. Completed functionality remains in place.

## Release baseline

- Published [release 1](https://github.com/mattzilla30/MBooru/releases/tag/1), app version `1`, Android version code `1374`.
- Source: `d46d3fc1050117951e310c8372e93d2e250cae8c`; APK SHA-256: `c376506dc526b580ea180b5dd21bd40421fa8f68a2f866e28bf96df28270d808`.
- APK signature/signer continuity, alignment, package/version/SDK metadata, updated contacts and free-app permissions were verified. Local debug/release/instrumentation builds, 58 unit tests and lint with zero errors passed.
- Target SDK remains 35 and minimum SDK 21. Numeric release names are separate from monotonic Android version codes.

## Phase 0 — In progress

The first executed [GitHub CI baseline](https://github.com/mattzilla30/MBooru/actions/runs/39081310069) failed during `android-actions/setup-android@v3`, before Gradle or emulator tests ran. The action defaults to `tools platform-tools`; Google's SDK catalog no longer provides the legacy `tools` package.

The first reviewable change supplies `packages: platform-tools` in all CI, release and Baseline Profile setup steps. The device/profile jobs explicitly install compile SDK 35 and build-tools 35.0.0, so an API 28 emulator does not leave the compile SDK implicit. No application behavior, target/minimum SDK, signing secrets or release-1 artifact is changed.

Validation: workflow YAML and all four action invocations were checked locally. A new GitHub CI run will establish whether SDK setup, clean build/unit/lint/benchmark APK compilation, and the API 28/35 emulator smoke matrix pass. Record actual results here rather than treating YAML validation or instrumentation compilation as device execution.

## Remaining baseline evidence

- Successful clean CI verification and API 28/35 device execution.
- Signed install-over-previous-release checks and representative account/storage/device journeys.
- Fixed-device startup/frame/memory results, real Baseline Profile generation, large-image gestures and video validation.
- An API 36 behavior migration is the next platform milestone; current Play submission requirements are documented in the roadmap.

Phase 0 is not complete until the required device/performance evidence exists. Publishing the release and repairing CI setup do not satisfy physical-device measurement requirements.

## Implementation pass — 2026-10-18

Work continues in [draft PR 1](https://github.com/mattzilla30/MBooru/pull/1). These are implementation checkpoints, not completion of the roadmap's hardware exit criteria.

- API 36 compatibility checkpoint: compile/target 36, AGP 8.13.2, Gradle 8.13 with the published wrapper checksum, JDK 21 in local/CI builds and Java 17 bytecode. Minimum API 21 remains the adopted compatibility assumption pending a user decision; no silent minimum increase. AGP's internal APK-renaming API is removed; Gradle uses its standard output names.
- Room reads/writes use Flow/suspend; production main-thread queries, destructive fallback and the database singleton are removed. Natural-key site saves preserve UID/dependent rows. Version 10 adds persistent transfer records through an additive auto-migration. A tag-filter presence query now checks filters rather than sites.
- Site/list ViewModels use flows instead of accumulating LiveData sources. Site-backed screen initialization is asynchronous. Redundant custom ViewModel scope cancellation is removed. Managers are injected instances.
- Settings, About and the site editor have direct Compose ownership. Settings migration now uses DataStore with one-time legacy preference migration, serialized asynchronous writes, explicit initialization and save-error retry. A compatibility adapter remains for callers still using Android preference listeners; those callers and the global Settings facade are not yet fully retired.
- Reverse-image search uses Retrofit/OkHttp; Ktor is removed. URL logging is removed to prevent query-token exposure. Repository cancellation propagates, and error messages avoid raw exceptions. Engine capabilities, network failure and resume rules have independent JVM tests in `core:model`.
- Account payloads move from Room into AES-GCM encrypted, device-only credential storage. API 21–22 wrap AES keys with Android Keystore RSA; API 23+ use an Android Keystore AES key. Existing Room account/auth columns are migrated transactionally to references at open. A restored reference without its device vault requires sign-in. SauceNAO's key also uses the vault through the settings repository. Device encryption/missing-payload execution is pending CI.
- Site exports are versioned, bounded and credential-free. Legacy imports are validated and strip portable credentials; transactional configuration restore preserves an existing local login and dependent records. This does not yet establish a full history/settings export and recovery drill.
- Downloads have persistent queue/progress/validator state and a Compose management screen with cancel/retry/open. Continuation requires matching validators, a complete Content-Range tail and a seekable destination; other cases restart. Real SAF-provider, reboot/quota and long-transfer checks remain required.
- Wide windows use a navigation rail and the existing phone design remains. Empty site lists offer site management. Accessibility, screenshot and physical-device layout evidence remain incomplete.
- Actions are pinned to resolved commit SHAs; monthly Gradle/action dependency updates and a numbered-release/maintenance checklist are versioned.

Executed locally: 68 Android unit tests and 5 pure model tests pass after the DataStore migration. Instrumentation compilation passes. Lint/build validation is being repeated as the pass continues; no new signed release has been published.

Executed GitHub evidence: [run 39081785007](https://github.com/mattzilla30/MBooru/actions/runs/39081785007) and [run 39083108674](https://github.com/mattzilla30/MBooru/actions/runs/39083108674) passed clean verification and actually executed the API 28/35 smoke tests. Both matrices exposed the same pinch assertion. Parent interception changes alone did not resolve it; PhotoView drawable crossfade is now disabled and the test also asserts zoom-reset behavior. The expanded API 21/28/36 matrix and vault test are awaiting execution on the current branch. Compilation does not count as a device pass.

Still required: retire global settings/menu/controller bridges; finish shared transport and engine fixtures; migration, durable-worker and provider integration tests; full backup/restore recovery coverage; dependency verification/locking, release provenance and upgrade checks; a real generated Baseline Profile; and the roadmap's physical-device performance/accessibility journeys. None is marked complete merely because a replacement dependency or workflow exists.
