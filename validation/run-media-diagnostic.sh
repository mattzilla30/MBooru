#!/usr/bin/env bash
set -euo pipefail
mkdir -p validation/results
trap 'adb logcat -d -b crash > validation/results/crash.txt 2>&1 || true' EXIT
adb root
adb wait-for-device
adb install -r validation/media-debug.apk
adb install -r validation/media-test.apk
python3 - <<'READY'
import subprocess,time
until=time.monotonic()+60
while time.monotonic()<until:
    state=subprocess.run(['adb','shell','service','check','activity'],capture_output=True,text=True).stdout
    if 'found' in state:
        break
    time.sleep(1)
else:
    raise RuntimeError('Android activity service did not become ready after package installation')
READY
if [ "$1" = 28 ]; then
  check_class=com.mattzilla30.mbooru.visual.SettingsVisualTest
  expected='OK (4 tests)'
else
  check_class=com.mattzilla30.mbooru.compose.MediaNetworkTest
  expected='OK (1 test)'
fi
adb shell am instrument -w -r -e class "$check_class" -e liveMediaChecks true -e additionalTestOutputDir /data/user/0/com.mattzilla30.mbooru/files/diagnostics com.mattzilla30.mbooru.test/androidx.test.runner.AndroidJUnitRunner > validation/results/instrument.txt
adb pull /data/user/0/com.mattzilla30.mbooru/files/diagnostics validation/results/ || true
cat validation/results/instrument.txt
python3 - "$expected" <<'PYCHECK'
from pathlib import Path
import sys
assert sys.argv[1] in Path('validation/results/instrument.txt').read_text()
PYCHECK
