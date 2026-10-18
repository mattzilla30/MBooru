#!/usr/bin/env python3
"""Require declared license metadata or a reviewed, bundled-license override for every component."""
import argparse
import hashlib
import json
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

parser = argparse.ArgumentParser()
parser.add_argument("sbom", type=Path)
parser.add_argument("--out", type=Path, required=True)
args = parser.parse_args()
components = json.loads(args.sbom.read_text())["components"]
overrides = json.loads(Path("docs/dependency-license-overrides.json").read_text())["components"]
rows, missing = [], []
for component in components:
    project = parse_qs(urlsplit(component.get("purl", "")).query).get("project_path", [None])[0]
    explicit = next((entry for entry in overrides if
        (project is not None and entry.get("project") == project) or
        (entry.get("group") == component.get("group") and entry.get("name") == component["name"] and entry.get("version") == component.get("version"))), None)
    licenses = component.get("licenses", [])
    if not licenses and explicit is None:
        missing.append(component.get("purl", component["name"]))
    if explicit is not None:
        path = Path(explicit["file"])
        assert path.is_file(), "Reviewed license text is missing"
        assert hashlib.sha256(path.read_bytes()).hexdigest() == explicit["licenseSha256"], "Reviewed license text changed"
    rows.append({"component": component.get("purl", component["name"]), "declaredLicenses": licenses,
                 "reviewedOverride": explicit})
args.out.parent.mkdir(parents=True, exist_ok=True)
args.out.write_text(json.dumps({"format": "MBooru-license-inventory", "version": 1, "components": rows}, indent=2) + "\n")
assert not missing, "Missing license metadata: " + ", ".join(missing)
print(f"License inventory: {len(rows)} components, all declared or covered by reviewed overrides")
