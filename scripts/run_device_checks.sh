#!/usr/bin/env bash
# UTP copies additionalTestOutputDir before uninstalling the test app, including screenshots.
set -euo pipefail
# Public-site probes are explicitly opt-in. AGP's UTP currently reports their JUnit assumptions
# as failures, so omit only that opt-in class from the default deterministic suite.
if [[ "${MBOORU_LIVE_MEDIA_CHECKS:-false}" == "true" ]]; then
  bash ./gradlew --no-daemon --max-workers=2 :android:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.liveMediaChecks=true
else
  bash ./gradlew --no-daemon --max-workers=2 :android:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notClass=com.mattzilla30.mbooru.compose.MediaNetworkTest
fi
