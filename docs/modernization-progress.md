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

### Ownership, menus and migration checkpoint

The next local checkpoint passes 80 Android unit tests (including schema 1–9 upgrades), 5 pure model tests, instrumentation compilation and lint with zero errors. Runtime settings access is now an injected `SettingsRepository`; `Settings` contains constants only. `App.app`, the global player-cache/database provider and standalone API/media client construction are retired. Shared transport owns connection/dispatcher policy; media startup waits for cache preparation off the UI thread, guards duplicate resume delivery and cancels pending playback when offscreen. An offline H.264/AAC lifecycle test is compiled for device execution.

All 29 XML menu definitions and their PopupMenu adapters are replaced with typed action/menu contracts. Compose owns toolbar, row and overflow presentation; editor actions observe loading state, and keyboard shortcuts use the public activity shortcut callback. Native text-selection action mode remains at its platform boundary.

The isolated [API 21/28/36 checkpoint](https://github.com/mattzilla30/MBooru/actions/runs/39085171523) passed verification and executed five device tests on each API. Its pinch diagnostic reported native scale 1.0 on all three devices. The old fixed-pixel gesture did not account for Android's physical minimum scaling span (27 mm by default); the revised gesture scales with viewport width and asserts both native scaling and paging/dismiss lock/reset. API 21 additionally exposed a provider's default 16-byte GCM IV; legacy software encryption now explicitly supplies a random 96-bit nonce, while modern Keystore generates its nonce. The device vault test now also checks tampering, key loss and successful new sign-in storage after key loss. These fixes still require a fresh executed device matrix; the prior failures are retained as evidence.

### Network, media and durable-storage implementation

API responses are capped at 8 MiB, including unknown-length/chunked bodies. Cookies now have an injected owner with persisted domain/path metadata; sign-out clears that site's parent-domain/path sessions while preserving unrelated sites. WAF web content receives a header allowlist and sanitized URL, cannot replay account writes, and has a cancellation/deadline cleanup path. The API-21 credential IV fix and media checks are undergoing a new three-device run.

The pinch fixture had two separate defects: the old pixel span was too small for dense devices, and its positional arguments described parallel finger movements rather than expansion. Named pointer arguments now describe an actual pinch. The video fixture exposed a real wrong-thread call after cache preparation; player creation explicitly uses Main.immediate. Prior failures remain recorded rather than reclassified as passes.

Typed Navigation routes and drawer actions now replace string contracts. Native date pickers, sharing, storage picking and shared-element transitions remain explicit platform boundaries. Destination-owned mutation ViewModels handle wallpaper/filter/favorite writes; the coordinator no longer emulates fragment context/activity access. A scrollable navigation rail preserves controls in short large-screen windows.

New downloads retain the complete provider URI and original grant, use fresh `.part` staging documents, encrypt queued media addresses in the device vault, validate resume state, and rename only after successful transfer/flush. Existing completed files are never deliberately reused or removed. Unsupported publication leaves a failed partial file for retry/discard. Folder selection no longer revokes grants used by older jobs/files. File-channel seeking/truncation handles opaque document IDs and storage capabilities rather than constructing filesystem-like document IDs. Legacy queued work retains a compatibility path. New provider/HTTP fixtures are being executed; cloud-provider, quota and reboot checks remain unverified until exercised.

Default builds now exclude Firebase Analytics/Crashlytics and their initialization. ML Kit retains small Firebase component/encoding libraries; these are not the optional analytics SDK. Enabling it requires both a matching configuration and `-PenableFirebase=true`; scanner Task/coroutine support is an explicit dependency instead of an accidental Firebase transitive dependency. Privacy documentation now describes actual default connections, backups, credentials, queued addresses and partial files.

The next candidate is version name `2`, Android version code `1375`; release `1` and the update manifest are unchanged until a new release is validated and explicitly published. Formatting is an intentional separate maintenance change using ktfmt; Detekt's existing debt is recorded in a versioned baseline rather than presented as newly fixed.

### Executed platform/media checkpoint

[Run 39088994836](https://github.com/mattzilla30/MBooru/actions/runs/39088994836), source `f7081cbc`, passed clean verification and every API 21/28/36 device job. The tests actually executed image pinch/reset, tiled decode, local video lifecycle, grid restoration, navigation, credential tamper/key-loss/new-login recovery and cookie path/domain isolation. This establishes those fixture journeys at that checkpoint; it does not establish physical-device frame/memory performance or live account compatibility.

A newer local checkpoint passes 86 Android unit tests, including opaque-provider publication, validated range continuation and clean restart, and 5 pure model tests. A generated release runtime SBOM has 237 components, 235 with license metadata; the public OSV query on 2026-10-18 returned zero matching advisories. The two components without complete license metadata still require source/license review; the query is time-specific evidence, not a guarantee against undisclosed vulnerabilities. Further queue, WAF and remote-media URL fixtures are being added and rerun before the next checkpoint.

### Current compatible toolchain and quality checkpoint

The API-21 support floor is retained. The verified local stack is AGP 9.4.1, Gradle 9.6.0, JDK 21, built-in Kotlin 2.4.21, KSP 2.3.12, Compose BOM 2025.11.01 (Compose 1.9.5 / Material 3 1.4.0), Coil 3.4.0, Media3 1.8.1 and compile/target API 36. Newer AndroidX lines that require API 23 were not silently adopted. The wrapper was regenerated using Gradle's wrapper task and its distribution checksum is pinned. All variant dependency graphs resolve with strict checksums and versioned locks.

Local execution passes 93 Android unit tests, 5 pure model tests, Detekt against the reviewed existing baseline, instrumentation compilation and Android lint with zero errors. A full R8 release build and benchmark target compilation passed on the new toolchain; final frozen-source release/profile/device checks are tracked separately. The runtime SBOM now contains 268 components. The recorded 2026-10-18 OSV query found no matching published advisories, and every component has declared license metadata or an exact reviewed override with bundled license text. This is a dated audit, not a guarantee against undisclosed vulnerabilities.

The previous CI checkpoint `6aeedb63` passed verification. Its API 21/36 native tests passed but the runner incorrectly returned exit 2 because emulator-runner executes separate script lines in separate shells; one shell script now owns execution. API 28 exposed a navigation readiness race and the intentionally missing first screenshot goldens. The profile run stopped at dependency resolution before a device journey. These failures are preserved: navigation now waits for initialized content, screenshot files use UTP's exported additional-output directory, and the profile dependency graph now resolves locally. Fresh execution remains required.

### New-stack device, screenshot and signed-upgrade results

Clean [verification run 39095321871](https://github.com/mattzilla30/MBooru/actions/runs/39095321871) passed the complete build/unit/lint/release gate. Its older device checkout exposed an imported BOM checksum missing from a cold classpath; the reviewed parent/imported metadata is now included without disabling strict verification.

[Run 39096204368](https://github.com/mattzilla30/MBooru/actions/runs/39096204368) passes all nine native tests on both API 21 and API 36. [Run 39095789577](https://github.com/mattzilla30/MBooru/actions/runs/39095789577) passes those nine tests on API 28 and captures the first four screenshot baselines; its expected missing-golden failures were reviewed, not hidden. Every actual PNG was inspected. [Run 39096390923](https://github.com/mattzilla30/MBooru/actions/runs/39096390923) then passes all four pixel comparisons and final-setting reachability assertions, including 200% RTL text. These are initial current-implementation baselines, not an invented historical Flexbooru pixel comparison.

The signed candidate (`46d06b8a758f5d721074e16b42b74a9538ea806c6fc33baa87fc495250701706`) installs over release 1 and retains the artificial site, history, filters, wallpaper query and migrated settings/credential references on API 21/28 ([39095233055](https://github.com/mattzilla30/MBooru/actions/runs/39095233055)) and API 36 ([39096493723](https://github.com/mattzilla30/MBooru/actions/runs/39096493723)). API 36 initially left a first-launch notification dialog open; the isolated fixture now pregrants notification permission before baseline/candidate launch. This upgrade result is for that pre-profile binary; the final profiled artifact gets its own checksum and rerun.

The offline profile target manifest is verified and its actual APK contains the local video. The profile collector's original fixed post-1 selector failed after restored scrolling: exported hierarchy/logs prove the offline grid was populated and serving local images. The revised collector selects a fully visible still image, requires the viewer to open, and scrolls explicitly to video post 2 before requiring PlayerView. Generation remains pending until a successful run produces real rules.
