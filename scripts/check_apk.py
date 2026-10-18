#!/usr/bin/env python3
"""Validate public APK identity/permissions and 16 KiB alignment of packaged ELF64 LOAD segments."""
import argparse
from pathlib import Path
import re
import struct
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("apk", type=Path)
parser.add_argument("--build-tools", type=Path, required=True)
parser.add_argument("--expected-certificate", type=Path, required=True)
args = parser.parse_args()
def run(tool, *values):
    return subprocess.check_output([str(args.build_tools / tool), *map(str, values)], text=True)
signature = run("apksigner", "verify", "--print-certs", args.apk)
actual = re.search(r"Signer #1 certificate SHA-256 digest: (\w+)", signature).group(1)
assert actual == args.expected_certificate.read_text().strip(), "Signing identity changed"
run("zipalign", "-c", "-P", "16", "4", args.apk)
badging = run("aapt", "dump", "badging", args.apk)
source = Path("android/build.gradle.kts").read_text()
version = re.search(r'APP_VERSION_NAME\s*=\s*"(\d+)"', source).group(1)
code = re.search(r'APP_VERSION_CODE\s*=\s*(\d+)', source).group(1)
assert f"name='com.mattzilla30.mbooru' versionCode='{code}' versionName='{version}'" in badging
assert "sdkVersion:'21'" in badging
target = re.search(r'TARGET_SDK_VERSION\s*=\s*(\d+)', source).group(1)
assert f"targetSdkVersion:'{target}'" in badging
permissions = run("aapt", "dump", "permissions", args.apk)
assert not any(value in permissions for value in ["com.android.vending.BILLING", "permission.AD_ID", "ACCESS_ADSERVICES_AD_ID"])
checked = []
with zipfile.ZipFile(args.apk) as archive:
    for name in archive.namelist():
        if not name.startswith("lib/") or not name.endswith(".so"):
            continue
        data = archive.read(name)
        if data[:5] != b"\x7fELF\x02":
            continue
        order = "<" if data[5] == 1 else ">"
        offset = struct.unpack_from(order + "Q", data, 32)[0]
        size, count = struct.unpack_from(order + "HH", data, 54)
        for index in range(count):
            header = offset + size * index
            if struct.unpack_from(order + "I", data, header)[0] == 1:
                alignment = struct.unpack_from(order + "Q", data, header + 48)[0]
                assert alignment >= 16384, f"{name}: LOAD alignment {alignment} is below 16 KiB"
        checked.append(name)
print(f"Verified MBooru {version}/{code}, signer continuity, free-app permissions and {len(checked)} ELF64 libraries")
