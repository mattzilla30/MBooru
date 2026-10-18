# Performance and accessibility evidence

No physical Android device is attached to this cloud workspace. `/dev/kvm` is unavailable locally; functional device execution uses GitHub's KVM emulators. Emulator correctness results and generated ART profiles are valid functional/profile evidence. They do not establish phone startup speed, thermal behavior, sustained jank, accessibility usability or peak memory.

## Repeatable corpus

The profile target alone uses `FixtureApp`: 60 synthetic 1600 × 900 images, a locally generated 320 × 180 H.264/AAC video, one fixed Moebooru configuration and an interceptor that returns all fixture responses without external requests. It exercises startup, browsing, details, zoom, video and settings. Fixture code/assets are excluded from the production release. Viewer instrumentation separately covers transparent PNG/WebP, JPEG, animated GIF, huge tiled images, paging/zoom reset and video foreground/offscreen lifecycle. Source and licenses accompany these fixtures.

## Physical-device protocol — unverified

Use one fixed midrange phone and one older supported phone. Record model, OS/API/security patch, refresh rate, resolution/density, power mode, battery/temperature, source commit, signing identity, compilation mode and corpus version. Disable unrelated background work; repeat the same cold/warm/cached conditions. Do not compare different devices or treat a Gradle-cache warmup as an app startup improvement.

1. Generate and consume the committed Baseline Profile, then install the benchmark target on the same device. Keep separate runs with `CompilationMode.None()` and `CompilationMode.Partial()` when measuring profile benefit.
2. Run `BrowseBenchmark` through `:baselineprofile:connectedNonMinifiedReleaseAndroidTest`, passing `-Pandroid.testInstrumentationRunnerArguments.class=com.mattzilla30.mbooru.benchmark.BrowseBenchmark`. Preserve the JSON and Perfetto outputs.
3. Record at least five cold and five warm startups; report distributions, not just the fastest sample. Measure sustained grid scrolling, detail opening, large-image zoom and image/video transitions using the same dataset.
4. Record frame timing/jank, peak memory (`dumpsys meminfo`/Perfetto) and network/cache behavior. Confirm no persistent memory growth or stale audio after repeated swipes/backgrounding. Retest at thermal steady state.
5. Investigate a startup regression above 10% or a jank increase above five percentage points, accounting for baseline variance. Keep raw observations and device metadata alongside conclusions.

No physical-device numbers or profile-speedup percentages have been invented for this pass.

## Accessibility and window protocol — partially automated, hardware review pending

Automated checks exercise settings at light/dark, tablet width, 200% text and RTL on a fixed API-28 renderer. Golden PNGs must be visually reviewed before updates. Existing phone palette/typography remain bridged from the original theme. Wide-screen navigation uses a scrollable rail; Compose controls provide standard touch semantics and keyboard shortcuts.

Manual checks still require TalkBack reading order/announcements, switch access, external keyboard/mouse, contrast in dynamic-color variants, fold/unfold and split-screen resizing, cutouts/IME, predictive back, camera permission denial and real storage providers. Review controls at 200% text without relying solely on screenshots. Use one actual local SAF provider and a cloud provider for revoked grants, disconnect/full storage, cancelled transfers, reboot and publication behavior. These checks remain unverified until their observations are recorded.
