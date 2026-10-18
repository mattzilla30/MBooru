#!/usr/bin/env python3
"""Emit public artifact provenance; never reads signing keys or secret environment variables."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("apk", type=Path)
parser.add_argument("--source", required=True, help="Full source commit used for this APK")
parser.add_argument("--out", type=Path, required=True)
args = parser.parse_args()
assert len(args.source) == 40 and all(c in "0123456789abcdef" for c in args.source)
data = {
    "format": "MBooru-release-provenance", "version": 1,
    "source": "https://github.com/mattzilla30/MBooru/commit/" + args.source,
    "artifact": args.apk.name, "size": args.apk.stat().st_size,
    "sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(),
    "gradleWrapper": Path("gradle/wrapper/gradle-wrapper.properties").read_text(),
    "dependencies": {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(Path(".").rglob("gradle.lockfile")) if ".gradle" not in p.parts},
    "verificationSha256": hashlib.sha256(Path("gradle/verification-metadata.xml").read_bytes()).hexdigest(),
}
args.out.parent.mkdir(parents=True, exist_ok=True)
args.out.write_text(json.dumps(data, indent=2) + "\n")
print(f"Wrote provenance for {args.apk.name}")
