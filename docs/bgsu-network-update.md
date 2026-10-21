# BGSU theme and live DNS settings

The Android 17 user confirmed that enabling DNS over HTTPS restored images across sites. Their enabled setting is retained by this update. No accounts, site configuration, history or other application data need to be cleared.

The settings screen defaulted DNS over HTTPS to off while SettingsRepository defaulted to on. The defaults now agree. A shared SettingsDns evaluates the current setting on each DNS lookup, so Retrofit and Coil do not retain a stale resolver after the user changes it. An explicitly selected encrypted resolver does not silently fall back to system DNS. Media GET requests also recover from an unavailable first server address; account/API clients retain their existing no-replay policy. Browsing errors now display the safe network category instead of calling every failure a Cloudflare challenge.

Compose and native controls share a complete BGSU palette: orange #FD5000 accents, brown #4F2C1D text/branding and cream #F2D6A2 light backgrounds. Dark and black modes use warm corresponding surfaces. Text/action colors use contrast-adjusted tones where necessary. Explicit Material 3 container, dialog, menu, inverse and surface roles prevent default purple/white fallbacks. Android wallpaper-based dynamic colors are disabled. Selected site rows and navigation capsules have a distinct warm background.

Validation includes deterministic DNS switching/no-fallback tests, media route-recovery and old-cache HTTP regressions, light/dark/black/API31 theme and contrast tests, native live PNG/JPEG decoding, and reviewed Settings screenshots at phone/tablet/200% RTL sizes. Initial screenshot comparisons against the prior theme failed as expected; the actual BGSU PNGs were visually reviewed before replacing the baselines. Run IDs and final candidate hashes are recorded with the APK validation evidence.

The native live test is opt-in (`liveMediaChecks=true`) because routine CI should not depend on third-party site availability. The Android 17 user report established the physical device's DNS workaround; emulator checks provide separate software validation.
