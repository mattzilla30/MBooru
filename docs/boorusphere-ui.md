# MBooru UI and server update

MBooru adapts [Boorusphere](https://github.com/azrim/boorusphere)'s UI from commit
`2186a58ec496840e1a2e6c223b7cf73ba195528c` to Android Jetpack Compose.
Boorusphere is copyright 2021 Chaldeaprjkt, distributed under BSD-3-Clause.
Its complete notice is bundled at `android/src/main/assets/licenses/boorusphere.txt`
and available from MBooru's About screen. Flexbooru's GPL attribution remains.

The adaptation follows the bottom search bar, server drawer and selector,
compact viewer controls, details sheet, settings grouping, server editor,
download rows and About layout. The viewer opens related searches using artist,
character, copyright and general tags, and can open the original file externally.
Its local favorite heart exposes a checked accessibility state. MBooru retains its name, icon and BGSU
orange/brown/cream theme. Existing native image/video and networking layers
remain. Engine-specific browsing is available under Explore server.

Favorites are stored locally without an account and survive timeline refreshes.
Optional accounts remain available for supported servers. The Room 10-to-11
migration removes Muzei's retired table and adds independent saved posts.
History, filters, settings and optional credentials for retained servers remain.
Removing a retired server also removes its dependent local records and credentials.

Muzei, SauceNAO and WhatAnime integrations have been removed, including their
activities, provider, work, settings and SauceNAO secret. Previously scheduled
Muzei work is cancelled on startup when WorkManager is initialized.

The preset list contains Yande.re, Konachan, Safebooru, e926 and e621.
Their configured post APIs returned posts without authentication during validation.
Upgrades add e926 and e621 once without replacing existing configurations or
restoring deleted older presets. Subsequent launches preserve deletions.
Paheal is removed from saved sites on upgrade and cannot be re-added through the
site editor. Its response-specific XML normalization has been removed.
Gelbooru's
configured API returned HTTP 401 and Sankaku's configured API returned HTTP 403;
neither configured endpoint was usable anonymously in that check. Danbooru was
removed at the owner's request. These retired hosts are removed on upgrade,
including when an earlier version already installed presets, and cannot be
re-added through the site editor. Compatible custom engines remain available.

New Boorusphere-specific labels currently fall back to English in other locales.
Existing translated settings and actions retain their locale resources.

## Validation

[Recorded results](validation/boorusphere-ui.json): 101 Android unit tests,
static analysis, formatting, lint, frozen dependency builds, and 267 dependency
licenses passed. Native checks passed on API 28 (13 checks including four reviewed
screenshot comparisons) and API 36 (9 checks). These cover navigation/recreation,
grid state, pinch/paging, tiled images, video/GIF/image decoding and credentials.
The native run's later combined-upgrade setup failed because a debug installation
was still present; the same signed APK then passed the upgrade checks on fresh emulators.

Signed release-1-to-3 upgrades passed on API 28 and 36, preserving retained-site
history, filters, settings and credential references while retiring the Muzei
table and SauceNAO key. The APK retains its signing identity, contains a compiled
Baseline Profile, and passes 16 KiB checks for all ten packaged ELF64 libraries.

The API 21 emulator stalled before app installation; that run was cancelled.
Physical-device performance and manual accessibility checks were not repeated.
