# MBooru modernization roadmap

Prepared 2026-10-17 for mattzilla30 <mattzilla30@gmail.com>.

This is the next implementation plan, not a claim that these migrations have already shipped. The current appearance, colors, typography, navigation choices, app ID, signing identity, six site presets and free feature access remain product requirements. Modernization should preserve those choices and users' accounts, history, filters and saved configuration.

## Starting point

MBooru already uses Compose with Material 3, Navigation Compose, destination-scoped paging models, lifecycle-aware collection, WorkManager downloads, streaming SAF writes, a bounded image cache and APK signing workflows. Ads, billing and payment gates are removed. About/contact ownership and the legacy sample cleanup are handled separately from this future roadmap.

The current stack is AGP 8.9.2 / Gradle 8.11.1 / Kotlin 2.1.21, Compose BOM 2025.07.00, Room 2.7.2, compile/target SDK 35 and minimum SDK 21. These are the current compatibility pins, not a declaration that they are the latest stable releases.

Specific remaining debt found in this repository:

- `MyDatabase` permits main-thread queries and destructive fallback; DAOs still expose synchronous queries and LiveData.
- `Settings` is a global SharedPreferences facade; `App.app`, `BooruManager`, `MyDatabase.instance` and activity-bound controllers carry global/lifecycle coupling.
- `BrowseController`, `MainActivity` and the shared app-bar bridge still adapt legacy menu/activity interactions to Compose. Settings, About and the site editor retain fragment hosts.
- Retrofit/OkHttp and Ktor coexist; image loading and API traffic have separate client construction. WebView cookies and WAF handling need explicit ownership and cancellation rules.
- `User` is serialized directly into Room, including authentication tokens. Backup rules include the preferences and database.
- Downloads are cancellable and streamed, but persistent resumable transfer state and a complete download-management UI are not established.
- Unit/lint/build checks run locally. The device matrix, real Baseline Profile generation, performance measurements and GitHub Actions execution still require successful execution elsewhere.
- The build customizes APK names through AGP's internal `BaseVariantOutputImpl`; this needs replacement before a major AGP upgrade.

## Delivery order and completion criteria

Each phase should produce a reviewable change with a working app. Complete prerequisite phases before changing their dependants; avoid mixing a database migration, networking rewrite and toolchain major upgrade in one change.

| Order | Outcome | Depends on | Completion evidence |
| --- | --- | --- | --- |
| 0 | Trustworthy release baseline | Current build | Device journeys and performance baselines recorded; known failures tracked. |
| 1 | Defined Android support policy and current stable build stack | 0 | Compatibility matrix, stable SDK/toolchain build, minimum-device installation test. |
| 2 | Safe asynchronous persistence | 0; selected support policy from 1 | Migration/restore tests; no production main-thread Room access or destructive fallback. |
| 3 | Lifecycle-safe state and settings | 2 | StateFlow screen contracts, settings migration, process-death tests. |
| 4 | Complete declarative screen ownership | 3 | Typed navigation/actions; remaining app-owned fragment/menu bridges retired. |
| 5 | Testable networking and authentication boundaries | 2–3 | Offline fixtures for every engine; credential isolation and error contracts. |
| 6 | Durable downloads and background work | 2, 5 | Queue recovery, storage failure and transfer cancellation/retry tests. |
| 7 | Reliable media and measured performance | 0, 4–5 | Hardware viewer/playback checks and repeatable benchmark results. |
| 8 | Adaptive, accessible UI with the existing design | 4, 7 | Phone/tablet/foldable, input, font-scale and accessibility checks. |
| 9 | Maintainable module and dependency boundaries | 3–5 | Small justified modules, isolated tests and measured build impact. |
| 10 | Secure, reproducible release process | 0–1; grows with each phase | Gated CI execution, dependency verification, provenance and signed upgrade smoke test. |
| 11 | Sustainable maintenance | All | Versioned support policy, recurring updates and release checklist. |

## 0. Establish the release baseline first

1. Run CI on the repository and resolve actual execution failures, including emulator setup. Compiling instrumentation APKs is not an executed device test.
2. Record representative journeys: first install, upgrade from the current signed APK, site switching, search/filter restoration, long grid scrolling, post details, zoom/dismiss, video, sign-in/sign-out, backups, downloads and Muzei.
3. Use local HTTP fixtures for deterministic automated tests. Keep separate opt-in live smoke checks with user-owned test accounts; never require live booru availability for every PR.
4. Exercise notification/camera denial, rotation, process death, expired credentials, loss of connectivity and revoked SAF folder access.
5. Record startup, frame timing, peak memory, network requests and cache behavior on a fixed midrange phone and an older supported phone. Store device/OS/build/dataset information with each result.
6. Keep the current signed APK and a backup/restore fixture as upgrade references. Test restoring credentials without printing them.

**Exit:** reproducible baseline results and an explicit list of remaining device failures. Do not hide unexecuted checks behind a passing build.

## 1. Set platform policy and update the build stack

**Immediate platform milestone:** Google Play's mobile-app policy, checked on 2026-10-17, requires new apps and updates to target Android 16 / API 36 or higher starting 2026-08-31. The current target-35 APK is therefore not ready for an ordinary new/update Play submission without a separately applicable extension. Sideloading is a separate distribution path. Deliver and test the API 36 behavior checkpoint before planning a Play release; this About/sample update does not silently change its target SDK.

- Recommended forward support policy: evaluate minimum API 26 for the modern development line. Keep API 21 support until that policy is explicitly adopted; a separate legacy maintenance line is only worthwhile if there is a demonstrated need and capacity to maintain it.
- Verify the latest stable Android SDK, Play target requirements and supported AGP/Gradle/JDK/Kotlin/KSP combinations against official documentation at implementation time. SDK catalog availability alone does not establish a Play deadline or a runtime's general availability.
- Stage target behavior changes from API 35 through API 36, then the latest verified stable API. Validate each target's behavior before the next increase. Use preview SDKs in a separate non-blocking compatibility job.
- Replace `BaseVariantOutputImpl` and other internal build APIs with public Android Components APIs or rename copies of completed artifacts outside AGP. Do this before adopting the next major AGP.
- Align local and CI JDKs. Upgrade AGP/Gradle, Kotlin/Compose compiler/KSP, then AndroidX/Compose BOM in compatible groups. Review major AGP Kotlin integration changes instead of retaining incompatible duplicate plugin configuration.
- Remove unused version-catalog entries, obsolete plugins and unnecessary repositories after proving which dependencies need them. Pin wrapper checksums; add Gradle dependency verification and locks with reviewed updates.
- Maintain a compatibility table covering SDK, AGP, Gradle, JDK, Kotlin, KSP, Compose, Room, WorkManager, Media3 and image loading. Update concrete versions only after resolving and building the selected stable artifacts.

**Exit:** debug/release/test/benchmark builds pass on the selected stack; the minimum supported device installs the release. No silent minimum-SDK increase.

## 2. Make persistence asynchronous and migration-safe

- Convert Room reads to `Flow` and writes/transactions to `suspend`; move callers off the UI thread. Use Room `withTransaction` for related operations.
- Replace `allowMainThreadQueries()` only after all production callers are migrated. Enable development StrictMode checks to catch regressions.
- Replace destructive migration fallback with explicit, tested migrations across supported schema versions. Separate rebuildable post caches from user-owned accounts, history and configuration.
- Review `OnConflictStrategy.REPLACE`: resolve the existing natural key to its UID before updating. A blind `@Upsert` of a new object with UID zero does not by itself preserve identity for a separate unique host/scheme key.
- Keep preset installation, retirement and selected-site repair deterministic. Preserve user changes and respect deleted presets; migrate retired endpoints deliberately.
- Define backup/export format versions, import validation, size limits, rollback and atomic application of imported settings. Test corrupt, old, partial and duplicate imports.
- Retain foreign-key integrity for history, filters, paging keys and Muzei. Verify migrations with realistic data, including customized sites and queued work.

**Exit:** no main-thread Room access; schema/backup upgrade tests preserve user data. Every migration has a tested recovery path without silently deleting accounts.

## 3. Use one lifecycle-safe state model

- Migrate remaining LiveData presentation models to immutable `StateFlow<ScreenUiState>` and lifecycle-aware collection. Model loading, empty, content, retry, offline and authentication-expired states explicitly.
- Route user actions through typed callbacks/ViewModel methods. Keep networking, database work and preference writes outside composables.
- Use `viewModelScope` and structured concurrency; cancellation must propagate through repositories and stream copies. Remove obsolete custom scope wrappers once their callers are migrated.
- Move settings to a typed DataStore repository with one-time SharedPreferences migration. Retain preference keys/behavior and distinguish process-restored state from durable preferences.
- Replace `App.app` access and database/service singletons with injected interfaces and properly scoped instances. Retain Koin unless there is a demonstrated reason to change DI frameworks.
- Keep saved queries, filters, selected site, list positions and editor drafts in appropriate saved-state owners. Do not put tokens or image bytes into SavedStateHandle, bundles or navigation arguments.
- Define lifecycle-safe delivery for one-off messages and navigation requests, including rotation and background/foreground transitions.

**Exit:** screen contracts can be tested without activities; process recreation restores meaningful state without duplicate requests or lost edits.

## 4. Finish Compose ownership without destabilizing media

- Move Settings, About and site-editor content out of AndroidFragment hosts, one screen at a time.
- Replace the XML-menu/`PopupMenu` metadata bridge with typed Compose app-bar and overflow actions. Preserve existing labels, ordering, visibility and shortcuts.
- Move action routing out of `MainActivity`/`BrowseController` into destination ViewModels and small platform interfaces. Remove emulated fragment/activity access once no longer needed.
- Adopt stable type-safe route contracts supported by the selected Navigation version. Pass IDs and small arguments; load data through repositories. Evaluate newer navigation APIs only after a stable migration path is verified.
- Centralize back-stack restoration, predictive-back behavior, deep links and unsupported-capability handling. Handle an empty site list with an actionable empty state.
- Keep proven native image, media and CameraX components where they deliver necessary functionality. Replace them only when feature and performance parity is measured.

**Exit:** app-owned screens and menus have declarative ownership; navigation/back/deep-link/recreation tests pass. The existing design remains recognizable.

## 5. Consolidate networking, errors and credential handling

- Inventory every API client and choose a shared transport/configuration layer. Prefer one documented OkHttp policy; retain separate serializers/adapters where engines require XML or distinct JSON schemas. Remove Ktor only after its actual uses have an equivalent implementation.
- Define a per-engine interface and a capability model: posts, popular, tags, pools, artists, comments, favorites, authentication and archive downloads. Drive UI availability from capabilities rather than scattered integer checks.
- Keep wire DTOs separate from domain/presentation models; validate malformed fields, dates, ratings, sizes and URLs at the boundary.
- Standardize timeouts, cancellation, retry/backoff, 429/Retry-After, unavailable sites, expired sessions and safe user-facing errors. Do not automatically retry non-idempotent favorites/account writes.
- Preserve host-specific headers, referers, Sankaku behavior, cookies and custom/private-server support with MockWebServer fixtures. Do not assume all engines accept the same endpoint or authentication format.
- Give WebView/WAF sessions explicit lifecycle, host boundaries and timeouts; prefer supported browser/account flows where possible. Never bypass TLS verification.
- Introduce a credential repository. Move tokens out of ordinary Room payloads using a platform-keystore-backed design where appropriate; account for key loss, device transfer and clear re-authentication. Avoid adopting deprecated encrypted-preference APIs by default.
- Exclude credentials from ordinary diagnostic logs, crash breadcrumbs, navigation state and WorkManager input. Offer credential-free backups by default; design any credential-inclusive export as an explicit encrypted format with tested restore behavior.
- Make optional Firebase genuinely optional in runtime initialization and published privacy documentation. Keep advertising and billing absent.

**Exit:** fixture coverage for every supported engine and auth/error path; sign-out removes the relevant session; backups/restores and logs follow the documented credential policy.

## 6. Build a durable download experience

- Add a persistent queue model containing IDs, destination permission references, state, bytes, retry reason and timestamps. Resolve current credentials at execution time rather than storing passwords in job input.
- Expose queue status, cancel, retry and completed-file actions in a coherent UI. Distinguish a queued task from an actively transferring file.
- Implement resume only when the server supports Range plus suitable validators such as ETag/Last-Modified and the destination supports safe writes/seeking. Otherwise explain and perform a clean restart.
- Validate Android foreground-service and job quota changes on each target. Evaluate eligible user-initiated data-transfer jobs on supported Android versions for explicit long transfers; use WorkManager for appropriate deferrable/compatible work. Do not promise unlimited foreground execution.
- Handle revoked SAF grants, provider disconnects, no space, duplicate names, partial files, process death, reboot and login changes. Clean up partial output consistently.
- Keep cancellation prompt and progress throttled. Unknown content length is an indeterminate state, not zero progress forever or a division error.
- Add WorkManager tests for uniqueness, retries, constraints and recovery; exercise real SAF providers and notification actions on devices.

**Exit:** a long transfer can be observed, cancelled and recovered correctly; failure never appears as a completed file or corrupts a previous download.

## 7. Modernize media and prove performance

- Upgrade image loading and Media3 only after the support-policy/toolchain checkpoint. For Coil 3, explicitly configure the network module, headers/cookies, cache ownership, GIF/animated formats and cancellation; retain tiling for huge images until parity is proven.
- Test large JPEG/PNG, WebP/GIF, transparent images, portrait/landscape content and supported video formats. Cover rapid swipes, zoom-to-dismiss interactions, rotation, shared-image transitions and release of offscreen players.
- Use stable list/grid keys and paging caches already present. Measure before adding prefetch; bound decoded image sizes, active players and memory/cache usage.
- Generate a real Baseline Profile from representative startup and navigation journeys. Review the generated output and consume it in release builds; the generator module alone is not a profile.
- Expand hardware benchmarks to cold/warm startup, sustained grid scrolling, detail opening, large-image zoom and video transitions. Record frame timing, jank and peak memory.
- Proposed initial regression budgets: investigate a >10% startup slowdown or >5 percentage-point jank increase on the fixed benchmark device; tune thresholds from baseline variance. Do not turn noisy emulator numbers into physical-device performance claims.
- Continue checking 16 KB ZIP/ELF alignment for every packaged 64-bit native library after dependency updates.

**Exit:** reproducible hardware results, no sustained memory growth or stale audio during navigation, and a real release profile with measured benefit.

## 8. Adapt layouts and accessibility while keeping the design

- Define window-size-aware layouts: current phone layout, a wider grid and optional navigation rail on larger windows, and a tested two-pane detail experience where useful. Reuse the existing palette and typography.
- Exercise resize, split screen, fold/unfold, cutouts, edge-to-edge, IME insets, portrait/landscape and predictive-back transitions on supported OS versions.
- Support hardware keyboards, focus traversal, mouse/trackpad and switch access. Keep touch targets at least 48 dp and avoid icon-only actions without meaningful descriptions.
- Test TalkBack reading order, merged post semantics, meaningful image descriptions, announcements for failures/progress, 200% font scaling, RTL and contrast across light/dark/dynamic-color variants.
- Honor reduced-motion preferences where supported; avoid layout shifts when images load or errors appear.
- Localize newly added UI strings and review existing fallback text. Keep proper names, email addresses and usernames untranslatable; do not invent translations for branding.

**Exit:** the supported device/window/input matrix is usable without hidden controls, clipped text or inaccessible core actions.

## 9. Introduce module boundaries where they pay off

- First establish repository/state contracts; then extract `core:model`, `core:data` and a shared UI/design-system boundary if they reduce coupling.
- Separate engine adapters and media/download implementations when their dependencies or test needs justify it. Group feature modules by ownership and change frequency rather than creating a module for every screen.
- Keep platform code out of pure model/domain logic. Use fakes and dependency injection to run feature tests without starting the entire application.
- Measure clean/incremental build time and configuration-cache compatibility before and after each extraction. Keep public APIs small; remove accidental cyclic dependencies.

**Exit:** feature/data tests run independently and the module structure reduces rebuild scope or clarifies ownership without slowing normal development unnecessarily.

## 10. Make CI and releases dependable

- Run formatting, Kotlin static analysis, unit tests, lint, migration tests and debug/release compilation in required checks. Start from the existing lint report: fix correctness/security/accessibility findings first, prevent new findings, and retire the backlog incrementally.
- Test on the adopted minimum API, a representative middle API and the latest stable API. Add tablet/large-screen and release-build smoke coverage; keep previews in an early-warning lane.
- Add deterministic screenshot checks for key themes, font scaling and window sizes. Review legitimate visual changes instead of accepting blanket baselines.
- Pin GitHub Actions to reviewed commit SHAs, minimize permissions, isolate signing from untrusted PR execution, and schedule dependency/security updates with compatibility checks.
- Add dependency verification, license inventory/SBOM and vulnerability triage. Preserve upstream attribution and maintain the GPL source/release correspondence.
- Replace commit-count-only versioning with a documented monotonic version-code strategy that works for release tags, shallow checkouts and parallel release branches.
- Produce signed APKs and, if publishing to Play, AABs; record source revision, toolchain versions and checksums. Verify signer continuity, package/SDK metadata, permissions and native alignment before publishing.
- Validate an install-over-previous-release upgrade, data retention and a known recovery procedure. Keep secure offline keystore backups; never expose signing material in logs or artifacts intended to be public.

**Exit:** an executed CI run gates a reproducible, traceable release. Successful workflow YAML parsing does not satisfy this gate.

## 11. Maintain standards after the migration

- Monthly: dependency/security triage and small compatible updates; rerun impacted fixtures and device journeys.
- Each stable Android release: review behavior changes, preview findings, target deadlines, permissions and background limits; update the support matrix.
- Quarterly: hardware performance/accessibility checks, backup/restore drills, source/license review and removal of obsolete compatibility paths.
- Document engine availability/auth requirements and retire or repair broken presets without overwriting custom user configurations.
- Maintain a short release checklist with links to the actual test runs, profiles, measurements and signed artifacts.

## First implementation milestone

Start with phase 0's executed device baseline and phase 1's API 36 compatibility checkpoint for Play eligibility. Record the minimum-SDK support decision, then deliver phase 2's asynchronous Room/repository slice for site management. This addresses current platform eligibility and demonstrated UI-thread/data-loss risks. Next migrate Settings/state ownership and remove the remaining menu/fragment bridges; schedule networking, downloads and media upgrades after those interfaces are stable.

Do not implement this whole roadmap as an unverified dependency sweep. A phase is complete when its exit evidence exists, not when the replacement library is merely added to Gradle.

## Reference documents

- [Current modernization status](modernization-status.md)
- [Compose migration structure](compose-migration.md)
- [Android platform releases](https://developer.android.com/about/versions)
- [Target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878)
- [AGP compatibility](https://developer.android.com/build/releases/gradle-plugin)
- [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations)
- [Room migrations](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- [Background data transfer](https://developer.android.com/develop/background-work/background-tasks/data-transfer-options)
- [Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview)
- [Large-screen app quality](https://developer.android.com/docs/quality-guidelines/large-screen-app-quality)
