# Reviewed Settings screenshot baselines

BGSU palette captured and visually reviewed from GitHub run 39101079284 (2026-10-18), API 28 google_apis x86_64 / pixel_2. All four initial comparisons failed as expected because the user requested a new palette. Reviewed phone light, phone dark, 200% RTL and tablet captures show readable labels and controls without clipping.

SettingsVisualTest normalizes Compose density to 1, captures a 360×640 phone frame (720×640 tablet), and uses font scale 2 plus RTL for the large-text case. Light/dark share the BGSU app theme. The test separately scrolls to the final cache action after pixel comparison. Future replacements require visual review, including labels, controls, wrapping and reachability.

Earlier Flexbooru-style baselines came from run 39095789577. These captures document the approved BGSU design, not a pixel comparison with legacy Flexbooru.
