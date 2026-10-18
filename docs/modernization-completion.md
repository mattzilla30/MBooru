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

## Final execution evidence

- Current toolchain CI: [39094674513](https://github.com/mattzilla30/MBooru/actions/runs/39094674513), source `3d6bd8a5` (running when this checkpoint was written).
- Current profile generation: [39094675880](https://github.com/mattzilla30/MBooru/actions/runs/39094675880) (running when this checkpoint was written).
- The physical acceptance items above are intentionally outstanding. Emulator success does not prove hardware startup/frame/memory performance, TalkBack usability or third-party SAF behavior.
