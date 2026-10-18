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
