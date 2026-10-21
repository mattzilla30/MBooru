#!/usr/bin/env python3
"""Numbered releases keep monotonic Android codes without depending on checkout depth."""
import os
from pathlib import Path
import re

source = Path("android/build.gradle.kts").read_text()
name = re.search(r'APP_VERSION_NAME\s*=\s*"([^"]+)"', source).group(1)
code = int(re.search(r'APP_VERSION_CODE\s*=\s*(\d+)', source).group(1))
assert re.fullmatch(r"[1-9]\d*", name), "Release names must be positive integers"
assert code == 1373 + int(name), "Use code 1373 + release number; release 1 was 1374"
if os.environ.get("GITHUB_REF_TYPE") == "tag":
    assert os.environ["GITHUB_REF_NAME"] == name, "Tag must match app version"
print(f"MBooru release {name}, Android version code {code}")
