# Release and maintenance checks

Owner: mattzilla30 <mattzilla30@gmail.com>. Preserve the GPL source and original Flexbooru attribution.

For every numbered release:

- Select an unused positive integer release name. Increment Android versionCode above every published build; do not derive it from shallow git history.
- Record source commit, JDK, Gradle/AGP and dependency lock changes. Review dependency verification changes against the configured repositories.
- Link successful CI unit/lint/build checks and minimum, middle and target API device journeys. Include settings recreation, site switching, zoom/dismiss, downloads and permission denial.
- Test installing the signed APK over the previous signed release and confirm accounts, custom sites, filters and history survive. Never uninstall as a substitute for upgrade testing.
- Inspect APK package/version/SDK/permissions, signing certificate continuity, SHA-256 checksum and 16 KB native alignment. Upload source correspondence and checksums with the APK; never signing material.
- Record backup/export and restore results, denied or revoked SAF access, interrupted transfer recovery and unsupported account/site behavior.
- Link the generated Baseline Profile and hardware performance report. Investigate startup regressions above 10% or jank increases above 5 percentage points on the same device/dataset. Emulator timings do not establish phone performance.
- If a check is unavailable, record it as unverified with its reason. Do not mark its roadmap exit complete.

Monthly: review automated dependency/action updates, security advisories and upstream engine/auth changes. Run affected fixtures and device checks before merging. Keep ads, billing and feature gates absent.

Each Android stable release: confirm its GA status, SDK/toolchain compatibility, target deadlines, foreground-work quotas, permissions, edge-to-edge and navigation changes. Update the support policy before changing minimum SDK.

Quarterly: repeat physical-device performance, accessibility, credential-free backup/restore and license/source drills; review obsolete compatibility code and endpoint presets. Preserve customized site records when retiring factory endpoints.

Recovery: retain the previous signed APK, its matching source tag and a verified user-data export. A database upgrade failure must surface without destructive fallback; do not advise clearing app data as an automatic recovery step. Android blocks APK downgrades with a lower versionCode, so a recovery build must carry a higher code and a tested compatible data migration.
