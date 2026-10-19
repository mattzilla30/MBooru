#!/usr/bin/env bash
set -euo pipefail
mkdir -p validation/results
trap 'adb logcat -d -b crash > validation/results/crash.txt 2>&1 || true' EXIT
adb shell svc wifi disable || true
case "$(adb shell pm list features)" in *android.hardware.telephony*) adb shell svc data disable || true ;; esac
python3 validation/signed_upgrade.py validation/previous.apk validation/candidate.apk --build-tools "$ANDROID_HOME/build-tools/36.0.0" --out "validation/results/upgrade-1-to-7-api-$1.json"
adb uninstall com.mattzilla30.mbooru
python3 validation/signed_upgrade.py validation/previous-6.apk validation/candidate.apk --build-tools "$ANDROID_HOME/build-tools/36.0.0" --out "validation/results/upgrade-6-to-7-api-$1.json"
adb uninstall com.mattzilla30.mbooru
adb install validation/media-debug.apk
adb install validation/media-test.apk
if [ "$1" -ge 33 ]; then adb shell pm grant com.mattzilla30.mbooru android.permission.POST_NOTIFICATIONS; fi
mkdir -p validation/results/screenshots
adb shell am instrument -w -r -e additionalTestOutputDir /data/data/com.mattzilla30.mbooru/files/m34-results -e class com.mattzilla30.mbooru.compose.ComposeMigrationSmokeTest,com.mattzilla30.mbooru.compose.MainNavigationSmokeTest,com.mattzilla30.mbooru.compose.CredentialVaultTest,com.mattzilla30.mbooru.compose.CookieSessionTest,com.mattzilla30.mbooru.compose.EngineXmlTest com.mattzilla30.mbooru.test/androidx.test.runner.AndroidJUnitRunner | tee validation/results/instrumentation.txt
python3 - <<'PY'
from pathlib import Path
import re
output = Path('validation/results/instrumentation.txt').read_text()
match = re.search(r'OK \((\d+) tests?\)', output)
assert match and int(match[1]) >= 10 and 'FAILURES!!!' not in output, output[-3000:]
print('Executed native tests:', match[1])
PY
adb pull /data/data/com.mattzilla30.mbooru/files/m34-results/. validation/results/screenshots/ || true
