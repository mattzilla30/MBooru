# Android and dependency support policy

Owner: mattzilla30 <mattzilla30@gmail.com>. Reviewed 2026-10-18.

The maintained MBooru development line retains minimum Android 5/API 21. No minimum increase was approved during this pass. Newer dependencies that require API 23/26 cannot be forced into the build with a manifest override. Public release 1 remains its original target-35 baseline; modernization candidates target API 36. The API-36 checkpoint passed an executed API 21/28/36 matrix in [run 39088994836](https://github.com/mattzilla30/MBooru/actions/runs/39088994836).

The verified initial toolchain checkpoint is AGP 8.13.2, Gradle 8.13, JDK 21 and Java 17 bytecode. Wrapper downloads have their official SHA-256 checksum pinned. The build's version catalog is the source of concrete dependency versions. The compatibility policy matters more than selecting the largest version number.

Published AAR manifests checked during the pass:

| Library | Compatible choice | Newer incompatible line |
| --- | --- | --- |
| Compose | BOM 2025.07.00 (last selected API-21 line) | Newer Compose lines require API 23 |
| Room | 2.7.2 | 2.8 requires API 23 |
| WorkManager | 2.10.1 | Newer lines require API 23 |
| Media3 | 1.8.1 | 1.9.4/1.11.1 require API 23 |
| Coil | 3.4.0 | 3.5/3.6 require API 23; 3.6.3 also requires compile SDK 37 |

The Media3/Coil choices above are the intended compatible update group; consult the final validation record and version catalog for whether that group has passed, rather than treating this table as execution evidence.

AGP 9.4.1 is available and its official compatibility table requires Gradle 9.6.0 and supports API 37. Moving to AGP 9 includes built-in Kotlin and new public DSL changes; it is a separate build checkpoint, not an excuse to raise the runtime minimum. Android 17 SDK availability alone does not prove a release/Play deadline: verify the official platform release status and behavior changes before adopting target 37. The currently documented immediate Play milestone remains target 36.

Monthly dependency/advisory review uses the release CycloneDX SBOM and OSV. Lock and verification updates must be reviewed together with repository origins; generating checksums is trust-on-first-use, not independent proof of publisher identity. Keep experimental/preview platform tests separate from the stable release gate. Recheck minimum SDK manifests and native alignment after every media/dependency update.

WorkManager remains the compatible download scheduler. Its durable queue, storage checkpoint and explicit retry UI fit deferrable/recoverable transfers across API 21+. User-initiated data-transfer jobs on newer Android versions require a separate platform scheduler and storage/foreground equivalence checks. They are not needed to promise immediate or unlimited execution: Android may pause or stop background work, and the app must recover rather than imply otherwise.
