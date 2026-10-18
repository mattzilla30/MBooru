#!/usr/bin/env bash
set -euo pipefail
mkdir -p validation/results
adb root
adb install -r validation/media-debug.apk
adb install -r validation/media-test.apk
adb shell am instrument -w -r -e class com.mattzilla30.mbooru.compose.MediaNetworkTest -e liveMediaChecks true -e additionalTestOutputDir /data/user/0/com.mattzilla30.mbooru/files/diagnostics com.mattzilla30.mbooru.test/androidx.test.runner.AndroidJUnitRunner > validation/results/instrument.txt
adb pull /data/user/0/com.mattzilla30.mbooru/files/diagnostics validation/results/ || true
cat validation/results/instrument.txt
python3 -c "from pathlib import Path; assert 'OK (1 test)' in Path('validation/results/instrument.txt').read_text()"
