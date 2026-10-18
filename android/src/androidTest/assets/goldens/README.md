# Reviewed Settings screenshot baselines

Captured and visually inspected from GitHub run 39095789577 (2026-10-18), API 28 google_apis x86_64 / pixel_2. SettingsVisualTest normalizes Compose density to 1, captures a 360×640 phone frame (720×640 tablet), and uses font scale 2 plus RTL for the large-text case. Light/dark use the existing app theme.

These are the first reviewed regression baselines for the current implementation, not a pixel comparison against a previously captured Flexbooru build. The visible rows wrap correctly; content below the viewport scrolls. The test separately scrolls to the final cache action after pixel comparison. Future replacements require visual review, including labels, controls, wrapping and reachability.
