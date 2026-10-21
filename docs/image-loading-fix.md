# Image loading after the Coil 3 upgrade

The previous application image loader reused `cacheDir/image_cache` from Coil 2. Coil 2 writes a millisecond timestamp on the first metadata line; Coil 3 expects an integer HTTP status code there. Coil 3.4.0 throws `NumberFormatException` when reading that timestamp. This prevents previously cached thumbnails and full-size images from loading after an upgrade.

The loader now uses `cacheDir/image_cache_v3`. Existing account, settings, database and download data are unaffected. Android may reclaim the old image cache as ordinary disposable cache data.

`MediaImageLoadingTest` starts a local HTTP server and seeds the old cache with Coil 2 response metadata and image bytes. The test reproduced the exception before the directory change. After the fix it verifies a successful HTTP image fetch, then a disk-cache hit without a second network request. DNS over HTTPS is disabled only in this loopback test so it does not depend on an external resolver.

This reproduces a concrete upgrade regression. Site-specific availability, login restrictions and the reporting device's network still require confirmation if images continue failing with the corrected APK.
