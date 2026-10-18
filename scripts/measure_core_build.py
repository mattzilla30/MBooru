#!/usr/bin/env python3
"""Record the isolated core test task and actual configuration-cache reuse, without A/B claims."""
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import subprocess
import time

output = Path("build/reports/core-build")
output.mkdir(parents=True, exist_ok=True)
command = ["./gradlew", "--max-workers=2", "--configuration-cache", "--rerun-tasks", ":core:model:test"]
observations = []
for label in ["first", "reused"]:
    start = time.perf_counter()
    result = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    elapsed = time.perf_counter() - start
    (output / f"core-build-{label}.log").write_text(result.stdout)
    observations.append({"run": label, "wallSeconds": round(elapsed, 3), "exitCode": result.returncode,
                         "configurationCacheReused": "Reusing configuration cache" in result.stdout})
    if result.returncode:
        print(result.stdout[-5000:])
        raise SystemExit(result.returncode)
assert observations[1]["configurationCacheReused"], "Second run did not reuse configuration cache"
report = {"checkedAt": datetime.now(timezone.utc).isoformat(), "gradle": "9.6.0", "jdk": "21", "workers": 2,
          "command": command, "observations": observations,
          "interpretation": "Warm local JVM-module observations; no controlled pre-extraction comparison or Android performance claim.",
          "coreSources": {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(Path("core/model/src/main").rglob("*.kt"))}}
path = Path("docs/validation/core-build.json")
path.parent.mkdir(parents=True, exist_ok=True)
path.write_text(json.dumps(report, indent=2) + "\n")
print(json.dumps(observations))
