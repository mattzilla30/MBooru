#!/usr/bin/env python3
"""Query the public OSV service for the exact Maven components in the release SBOM."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument("sbom", type=Path)
parser.add_argument("--out", type=Path, required=True)
args = parser.parse_args()
components = [c for c in json.loads(args.sbom.read_text()).get("components", [])
              if c.get("purl", "").startswith("pkg:maven/")]
assert components, "No Maven components in release SBOM"
findings = []
for start in range(0, len(components), 100):
    batch = components[start:start + 100]
    request = urllib.request.Request("https://api.osv.dev/v1/querybatch",
        data=json.dumps({"queries": [{"package": {"purl": c["purl"]}} for c in batch]}).encode(),
        headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=40) as response:
        results = json.load(response)["results"]
    assert len(results) == len(batch), "Incomplete advisory response"
    findings.extend({"component": c["purl"], "advisories": r["vulns"]}
                    for c, r in zip(batch, results) if r.get("vulns"))
report = {"checkedAt": datetime.now(timezone.utc).isoformat(), "source": "https://osv.dev/",
          "componentCount": len(components), "findings": findings}
args.out.parent.mkdir(parents=True, exist_ok=True)
args.out.write_text(json.dumps(report, indent=2) + "\n")
print(f"OSV: {len(components)} release components, {len(findings)} matching advisory entries")
if findings:
    for finding in findings:
        print(finding["component"], ", ".join(v["id"] for v in finding["advisories"]))
    raise SystemExit("Review advisories and update or document applicability before release")
