# Compose UI

MBooru's screen layouts now use Jetpack Compose and Material 3. The existing Android theme supplies the Compose palette, including dynamic color, dark mode and the black night theme. Existing preference keys, resources, fonts and grid sizing remain the source of the visual defaults; the orange/brown/cream branding applies to the app icon.

## Structure

- `ui/compose/settings`: resource-backed preference definitions, preference storage, Settings ViewModel and screen.
- `ui/compose/browse`: search, filters, paged lists and post grids. Post keys include the site, query and ID; lazy layout state restores scroll position.
- `ui/compose/navigation`: drawer and bottom navigation. Navigation Compose owns the main tab back stack. Navigation entries own browsing ViewModels; browsing no longer uses fragments.
- `ui/compose/detail`: paged viewer and its controls. Zoom disables parent paging and downward dismissal. Only the active page resumes video playback.
- Other Compose screens cover site/account editing, history, comments, blacklist, Muzei, image search, scanner, About and post information.

ViewModels expose StateFlow for screen state, lifecycle-aware collection drives rendering, and callbacks carry actions. Existing repositories, Room and networking remain in use. One-shot actions use channels rather than persistent state. Credentials remain out of saved instance state.

## Deliberate Android interop

PhotoView, SubsamplingScaleImageView and Media3 retain their image/gesture/video responsibilities. Native thumbnail views retain activity shared-element transitions. CameraX preview, selectable rich comment text and license HTML use AndroidView. Platform/material dialogs remain supported boundaries. Settings and About use Compose app bars and AndroidFragment hosts; all remaining app-owned activity bars also use the shared Compose app bar. View Binding and the retired screen XML/adapter infrastructure are removed; only the two Media3 control layouts remain.

## Checks

```sh
./gradlew :android:assembleDebug :android:testDebugUnitTest :android:lintDebug :android:assembleDebugAndroidTest
# With an Android device/emulator attached:
./gradlew :android:connectedDebugAndroidTest
```

Unit/Compose checks cover preference compatibility and persistence, dependent controls, recreation, cache cancellation/error handling, legacy theme colors, query/filter state, paging request snapshots, keys and image ratios. Device smoke tests exercise navigation recreation, grid restoration, pinch zoom and full-resolution tiled images.

Before release, also check representative real services and accounts, large remote images, GIF/video playback, paging load failures, orientation changes, and return transitions on a physical device. Measure scrolling and gesture performance on hardware; a software emulator does not establish production frame rates.

## Navigation checkpoint

The main drawer and bottom tabs now use a Compose NavHost, with stable routes for posts, popular posts, pools, tags and artists. Tab selection saves/restores destination state and returns to posts on back; selecting the active tab still scrolls its content to the top. Changing site capabilities resets the graph to the supported tab set, matching the previous XML-graph behavior. Main XML graphs, the navigation-host XML and the unused fragment-navigation lookup are removed.

The earlier AndroidX browsing-fragment bridge has been replaced by Compose screen controllers. Search editor state and existing lazy-layout state use saved state; tag/pool/artist applied request parameters are also saved so fragment recreation does not reset the displayed query/order/category. Navigation-entry ViewModels retain paging caches across tab switches; hardware/network performance remains to be measured.

Settings and About use a shared Compose app bar with the existing title, theme background, 56 dp height and up behavior. Their AndroidFragment hosts reconnect restored fragment content through the supported AndroidX integration. Browsing fragments are removed, and shared Compose bars preserve the remaining activity menu callbacks. Settings/About/site-editor content retains AndroidFragment interop. External activity entry points, including booru links and Muzei settings, remain in the manifest.

Preferences are cached by the current Application rather than by the process-wide Settings object, so recreated Application instances resolve their own dependency binding. Navigation regression tests exercise tab query restoration across tab switches/activity recreation, back to posts, switching to a posts-only site, Settings recreation and its Compose up action.
