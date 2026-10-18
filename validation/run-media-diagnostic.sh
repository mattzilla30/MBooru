#!/usr/bin/env bash
set -euo pipefail
mkdir -p validation/results
trap 'adb logcat -d -b crash > validation/results/crash.txt 2>&1 || true' EXIT
python3 validation/signed_upgrade.py validation/previous.apk validation/candidate.apk --build-tools "$ANDROID_HOME/build-tools/36.0.0" --out "validation/results/upgrade-api-$1.json"
