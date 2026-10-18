#!/usr/bin/env bash
# UTP copies additionalTestOutputDir before uninstalling the test app, including screenshots.
set -euo pipefail
bash ./gradlew --no-daemon --max-workers=2 :android:connectedDebugAndroidTest
