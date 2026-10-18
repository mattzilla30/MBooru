# Modernization implementation and evidence

This records the one-pass implementation against the original 12-phase roadmap. It does not declare unperformed physical-device acceptance complete. Published release `1` is unchanged; candidate `2` has version code `1375`.

| Phase | Implemented outcome | Evidence / limit |
| --- | --- | --- |
| 0 — baseline | Offline engine/media fixtures and API 21/28/36 CI | [Executed checkpoint](https://github.com/mattzilla30/MBooru/actions/runs/39088994836); final stack rerun tracked below. Physical baseline unavailable. |
| 1 — platform | AGP 9.4.1 / Gradle 9.6 / Kotlin 2.4.21; target 36, minimum 21 retained | Debug, R8 release and benchmark builds; [support policy](support-policy.md). Nonblocking API 37 lane. |
| 2 — persistence | Suspend/Flow DAOs, explicit migrations, identity-preserving writes, versioned validated site backups | Schema 1–9 upgrade fixtures and version-10 queue schema, import/rollback/credential-preservation tests. |
| 3 — state | Injected DataStore/settings/vault, StateFlow ownership, saved state, global service removal | Migration/recreation tests; cold-start saved theme applied after asynchronous readiness. |
| 4 — Compose | Direct Settings/About/editor, typed navigation and 80 menu actions; all 29 XML menus retired | Navigation/recreation fixture and format/lint checks. Native media/date/share/storage boundaries remain deliberately owned. |
| 5 — networking | Shared OkHttp transport, capped API bodies, engine fixtures, isolated cookies, cancellation-safe WAF, encrypted credentials | Seven-engine fixtures, error/write-no-replay tests; native vault and cookie tests. Live account smoke tests require test accounts. |
| 6 — downloads | Persistent queue, encrypted request addresses, validated Range resume, unique partial files and atomic publication attempt | Provider/HTTP range, fallback, existing-file preservation and WorkManager fixtures. Real cloud providers/reboot/quota not verified. |
| 7 — media | Coil 3 and shared cache, bounded decoded images, owned player lifecycle, offline profile and benchmark journeys | Native still/GIF/tiling/zoom/video tests; generated profile evidence recorded below. Physical frame/memory benefit unmeasured. |
| 8 — adaptive/accessibility | Responsive grid/rail, scrollable short-window controls, labels/touch targets, reduced motion and translated new strings | API-28 screenshots cover light/dark, tablet and 200% RTL. TalkBack, switches, physical foldables and full contrast audit remain manual. |
| 9 — boundaries | Pure `core:model`, typed engine capabilities and injected platform owners | Five independent JVM tests; [measured local configuration-cache reuse](validation/core-build.json). No invented before/after speedup. More modules were not justified by measured needs. |
| 10 — releases | Required quality jobs, locks/checksums, pinned actions, SBOM/licenses/OSV, protected signing, provenance and signed-upgrade fixture | 93 Android + 5 core tests; lint zero errors; all 268 runtime components covered by license metadata/reviewed overrides; dated OSV results. Final signed artifact/upgrade evidence below. |
| 11 — maintenance | Monthly audit/update jobs, preview lane, integer release/check/recovery policy | [Release checklist](release-checklist.md), [support policy](support-policy.md), [hardware acceptance procedure](hardware-validation.md). Recurring future execution cannot be pre-completed. |

## Execution evidence

- Clean [verification job in 39095321871](https://github.com/mattzilla30/MBooru/actions/runs/39095321871) passes formatting/static checks, unit tests, lint, release and instrumentation builds. The old device checkout in that run subsequently exposed an imported-BOM checksum gap, which was fixed; the whole old run is not described as green.
- [39096204368](https://github.com/mattzilla30/MBooru/actions/runs/39096204368): nine native tests pass on API 21 and nine on API 36.
- [39095789577](https://github.com/mattzilla30/MBooru/actions/runs/39095789577): nine native tests pass on API 28; four intentional missing-golden failures export the first screenshots for review. [39096390923](https://github.com/mattzilla30/MBooru/actions/runs/39096390923) subsequently passes all four reviewed screenshot comparisons and final-setting reachability checks.
- [Machine-readable device evidence](validation/device-checkpoints.json) records report hashes, actual test names and matching production source trees across these checkpoints.
- The signed pre-profile candidate passes install-over-release-1 data retention on API 21/28 ([39095233055](https://github.com/mattzilla30/MBooru/actions/runs/39095233055)) and API 36 ([39096493723](https://github.com/mattzilla30/MBooru/actions/runs/39096493723)). Its checksum and the initial fixture failures are in [progress](modernization-progress.md). The final profiled APK gets a separate exact-checksum upgrade run.
- Profile collection and the final consolidated CI are running from source `4bfdf0e8`: [profile](https://github.com/mattzilla30/MBooru/actions/runs/39096819214), [CI](https://github.com/mattzilla30/MBooru/actions/runs/39096820390). Generation/packaging is not claimed complete until real rules and a passing artifact check exist.
- The physical acceptance items above are intentionally outstanding. Emulator success does not prove hardware startup/frame/memory performance, TalkBack usability or third-party SAF behavior.
