#!/usr/bin/env python3
"""Install over release 1 on an isolated rooted emulator and verify fake user-data retention."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import sqlite3
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.mattzilla30.mbooru"
BASELINE_SHA256 = "c376506dc526b580ea180b5dd21bd40421fa8f68a2f866e28bf96df28270d808"
# Public, artificial fixture strings: never reads a user's real account or keystore.
TOKEN, AUTH, SAUCE = "upgrade-fixture-api-token", "upgrade-fixture-http-auth", "upgrade-fixture-sauce-key"
parser = argparse.ArgumentParser()
parser.add_argument("previous", type=Path)
parser.add_argument("candidate", type=Path)
parser.add_argument("--build-tools", type=Path, required=True)
parser.add_argument("--out", type=Path, required=True)
args = parser.parse_args()
def adb(*values, check=True):
    if values[0] == "install" or values[:3] == ("shell", "am", "start"):
        print("Executing fixture ADB:", " ".join(map(str, values)), flush=True)
    return subprocess.run(["adb", *map(str, values)], check=check, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=120 if values[0] == "install" else 60).stdout

def varint(value):
    result = bytearray()
    while value > 127:
        result.append((value & 127) | 128); value >>= 7
    result.append(value)
    return bytes(result)

def wire_fields(data):
    def number(position):
        value, shift = 0, 0
        while True:
            byte = data[position]; position += 1
            value |= (byte & 127) << shift
            if byte < 128:return value, position
            shift += 7
    position = 0
    while position < len(data):
        start = position
        tag, position = number(position)
        kind = tag & 7
        if kind == 2:
            length, position = number(position)
            payload = data[position:position+length]; position += length
        elif kind == 0:
            value, position = number(position); payload = varint(value)
        elif kind in (1, 5):
            length = 8 if kind == 1 else 4
            payload = data[position:position+length]; position += length
        else:raise AssertionError("Unsupported fixture protobuf wire field")
        yield tag >> 3, payload, data[start:position]

def seed_durable_preferences(path):
    replacements = {"booru_uid_activated": b"\x20" + varint(777),
                    "settings_grid_mode": b"\x2a\x05fixed"}
    retained = []
    for field, payload, raw in wire_fields(path.read_bytes()):
        key = next((value.decode() for number, value, _ in wire_fields(payload) if number == 1), None) if field == 1 else None
        if key not in replacements:retained.append(raw)
    for key, value in replacements.items():
        name = key.encode()
        entry = b"\x0a" + varint(len(name)) + name + b"\x12" + varint(len(value)) + value
        retained.append(b"\x0a" + varint(len(entry)) + entry)
    path.write_bytes(b"".join(retained))

def snapshot(destination):
    destination.mkdir(parents=True, exist_ok=True)
    adb("shell", "am", "force-stop", PACKAGE)
    adb("pull", f"/data/user/0/{PACKAGE}/databases/.", destination / "databases")
    adb("pull", f"/data/user/0/{PACKAGE}/shared_prefs/.", destination / "shared_prefs", check=False)

def launch(wait=True, require_alive=True):
    api = int(adb("shell", "getprop", "ro.build.version.sdk").strip())
    if api >= 33:
        adb("shell", "pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS")
    wait_args = ["-W"] if wait else []
    result = adb("shell", "am", "start", *wait_args, "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", "-n", PACKAGE + "/.ui.activity.MainActivity")
    print("Activity launch result:", result, flush=True)
    assert "Error" not in result, "Fixture activity did not launch: " + result

    # DataStore and Room initialization run asynchronously. This wait is fixture preparation only.
    deadline = time.monotonic() + 25
    while time.monotonic() < deadline:
        exists = adb("shell", "ls", f"/data/user/0/{PACKAGE}/databases/mbooru.db", check=False)
        if "no such" not in exists.lower():
            time.sleep(5)
            if require_alive:
                processes = adb("shell", "ps", *(["-A"] if api >= 26 else []))
                assert any(row.split() and row.split()[-1] == PACKAGE for row in processes.splitlines()), "Application process did not survive startup"
            return
        time.sleep(.25)
    raise AssertionError("Application database did not initialize")

assert adb("shell", "getprop", "ro.kernel.qemu").strip() == "1", "This fixture is restricted to an emulator"
baseline_sha = hashlib.sha256(args.previous.read_bytes()).hexdigest()
assert baseline_sha in {BASELINE_SHA256, "024c0e44f4e886fc5420a0ef650d1fd34ce939b4f54599aadecc80cee7f11e97"}, "Unexpected signed baseline"
adb("root"); adb("wait-for-device")
assert re.search(r"uid=0(?:\(|\s|$)", adb("shell", "id")), "A rooted google_apis emulator is required"
# Upgrade/data retention does not require live sites; all account strings are artificial.
adb("shell", "svc", "wifi", "disable", check=False)
if "android.hardware.telephony" in adb("shell", "pm", "list", "features"):
    adb("shell", "svc", "data", "disable", check=False)
def certificate(apk):
    output = subprocess.check_output([str(args.build_tools / "apksigner"), "verify", "--print-certs", str(apk)], text=True)
    return re.search(r"Signer #1 certificate SHA-256 digest: (\w+)", output).group(1)
assert certificate(args.previous) == certificate(args.candidate), "Signing certificate changed"
adb("install", args.previous)
# The immutable version-6 baseline crashes while drawing its icon on API 21.
# Initialize its real schema without waiting for that first frame; candidate launches remain strict.
baseline_draw_issue = baseline_sha != BASELINE_SHA256 and int(adb("shell", "getprop", "ro.build.version.sdk").strip()) == 21
launch(wait=not baseline_draw_issue, require_alive=not baseline_draw_issue)
with tempfile.TemporaryDirectory(prefix="mbooru-upgrade-") as directory:
    root = Path(directory)
    snapshot(root / "before")
    database = root / "before/databases/mbooru.db"
    with sqlite3.connect(database) as db:
        columns = [row[1] for row in db.execute("PRAGMA table_info(boorus)")]
        fixture = {"uid": 777, "name": "Customized upgrade fixture", "scheme": "https", "host": "upgrade-fixture.invalid",
                   "type": 1, "hash_salt": "custom-upgrade-salt", "blacklists": '["spoiler"]',
                   "user": json.dumps({"id": 42, "name": "upgrade-fixture", "token": TOKEN}), "auth": AUTH, "path": None}
        names = [column for column in columns if column in fixture]
        db.execute("INSERT INTO boorus (" + ",".join('`'+name+'`' for name in names) + ") VALUES (" + ",".join("?" for _ in names) + ")", [fixture[name] for name in names])
        for sql in ["INSERT INTO history(uid,booru_uid,query) VALUES(777,777,'retained search')",
                    "INSERT INTO tags_filter(uid,booru_uid,name,type) VALUES(777,777,'retained filter',0)",
                    "INSERT INTO muzei(uid,booru_uid,query) VALUES(777,777,'retained wallpaper')"]:
            if "INTO muzei" not in sql or db.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='muzei'").fetchone():
                db.execute(sql)
        has_saved = bool(db.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='saved_posts'").fetchone())
        if has_saved:
            db.execute("""INSERT INTO saved_posts(saved_at,uid,booru_uid,`index`,query,id,width,height,size,score,rating,is_favored,time,tags,preview,sample,medium,origin,pixiv_id,source,uploader) VALUES(1234,0,777,0,'retained query',123,640,480,12,9,'g',0,NULL,'[]','https://upgrade-fixture.invalid/preview.png','https://upgrade-fixture.invalid/sample.png','https://upgrade-fixture.invalid/sample.png','https://upgrade-fixture.invalid/original.png',NULL,'retained attribution','{"name":"fixture"}')""")
        db.commit(); db.execute("PRAGMA wal_checkpoint(TRUNCATE)")
    preferences = root / "before/shared_prefs" / (PACKAGE + "_preferences.xml")
    preferences.parent.mkdir(parents=True, exist_ok=True)
    document = ET.parse(preferences) if preferences.exists() else ET.ElementTree(ET.Element("map"))
    element = document.getroot()
    for key, tag, value in [("booru_uid_activated", "long", "777"), ("settings_grid_mode", "string", "fixed"), ("sauce_nao_api_key", "string", SAUCE)]:
        for existing in list(element):
            if existing.get("name") == key:element.remove(existing)
        node = ET.SubElement(element, tag, {"name": key})
        if tag == "string":node.text = value
        else:node.set("value", value)
    document.write(preferences, encoding="utf-8", xml_declaration=True)
    durable = root / "before-datastore.preferences_pb"
    adb("pull", f"/data/user/0/{PACKAGE}/files/datastore/mbooru.preferences_pb", durable, check=False)
    if durable.exists():seed_durable_preferences(durable)
    owner_match = re.search(r"(?:userId|appId)=(\d+)", adb("shell", "dumpsys", "package", PACKAGE))
    assert owner_match is not None, "Installed fixture package UID was not reported"
    owner = owner_match.group(1)
    assert owner.isdigit()
    for suffix in ["-wal", "-shm"]:adb("shell", "rm", "-f", f"/data/user/0/{PACKAGE}/databases/mbooru.db{suffix}")
    for local, remote in [(database, f"/data/user/0/{PACKAGE}/databases/mbooru.db"), (preferences, f"/data/user/0/{PACKAGE}/shared_prefs/{preferences.name}")]:
        adb("push", local, remote); adb("shell", "chown", f"{owner}:{owner}", remote); adb("shell", "chmod", "600", remote); adb("shell", "restorecon", remote)
    if durable.exists():
        remote = f"/data/user/0/{PACKAGE}/files/datastore/mbooru.preferences_pb"
        adb("push", durable, remote); adb("shell", "chown", f"{owner}:{owner}", remote); adb("shell", "chmod", "600", remote); adb("shell", "restorecon", remote)
    adb("install", "-r", args.candidate)  # Deliberately no uninstall or data clear.
    launch()
    # Existing DB presence is not a readiness signal: async Room/DataStore initialization
    # can take longer than the first Activity draw on a cold current-API emulator.
    deadline = time.monotonic() + 60
    migrated = False
    while time.monotonic() < deadline:
        probe = root / "migration-probe"
        adb("pull", f"/data/user/0/{PACKAGE}/databases/.", probe, check=False)
        try:
            with sqlite3.connect(probe / "mbooru.db") as db:
                row = db.execute("SELECT user,auth FROM boorus WHERE uid=777").fetchone()
                migrated = row is not None and all(value and value.startswith("mbooru-credential:v1:") for value in row)
        except sqlite3.Error:
            migrated = False  # A copy taken during a WAL commit is retried, never trusted.
        if migrated:
            break
        time.sleep(1)
    if not migrated:
        print("Migration readiness failed on the isolated artificial fixture. Crash-buffer diagnostics:", flush=True)
        print(adb("logcat", "-d", "-b", "crash", "-t", "100", check=False), flush=True)
        args.out.parent.mkdir(parents=True, exist_ok=True)
        with args.out.with_suffix(".png").open("wb") as screenshot:
            subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=screenshot, check=False, timeout=30)
        print(adb("shell", "ls", "-ln", f"/data/user/0/{PACKAGE}/shared_prefs", check=False), flush=True)
        with sqlite3.connect(root / "migration-probe/mbooru.db") as db:
            print("Observed schema version:", db.execute("PRAGMA user_version").fetchone()[0], flush=True)
    assert migrated, "Candidate did not finish credential migration before the deadline"
    snapshot(root / "after")
    with sqlite3.connect(root / "after/databases/mbooru.db") as db:
        site = db.execute("SELECT name,host,hash_salt,user,auth FROM boorus WHERE uid=777").fetchone()
        assert site[:3] == ("Customized upgrade fixture", "upgrade-fixture.invalid", "custom-upgrade-salt")
        assert site[3].startswith("mbooru-credential:v1:") and site[4].startswith("mbooru-credential:v1:")
        for table, column, expected in [("history", "query", "retained search"), ("tags_filter", "name", "retained filter")]:
            assert db.execute(f"SELECT {column} FROM {table} WHERE uid=777 AND booru_uid=777").fetchone()[0] == expected
        assert not db.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='muzei'").fetchone()
        assert db.execute("PRAGMA user_version").fetchone()[0] == 12
        assert db.execute("SELECT count(*) FROM saved_posts").fetchone()[0] == int(has_saved)
        if has_saved:
            assert db.execute("SELECT is_saved,saved_at,source FROM saved_posts WHERE booru_uid=777 AND id=123").fetchone() == (1,1234,"retained attribution")
        for table in ("local_likes", "named_collections", "collection_memberships"):
            assert db.execute(f"SELECT count(*) FROM {table}").fetchone()[0] == 0
        assert not db.execute("PRAGMA foreign_key_check").fetchall()
    data_store = root / "mbooru.preferences_pb"
    adb("pull", f"/data/user/0/{PACKAGE}/files/datastore/mbooru.preferences_pb", data_store)
    payload = data_store.read_bytes()
    assert SAUCE.encode() not in payload and b"sauce_nao_api_key" not in payload
    assert b"settings_grid_mode" in payload and b"fixed" in payload
    # Restart once more to exercise durable settings rather than the in-memory migration snapshot.
    launch()
    report = {"checkedAt": datetime.now(timezone.utc).isoformat(), "baselineSha256": baseline_sha,
              "candidateSha256": hashlib.sha256(args.candidate.read_bytes()).hexdigest(), "signerUnchanged": True,
              "installedWithReplace": True, "baselineFirstFrameIssue": baseline_draw_issue, "notificationPermissionPolicy": "Pregranted before first launch on API 33+; not applicable on older APIs", "networkDisabledForFixture": True, "customSiteRetained": True, "historyFiltersRetained": True, "retiredMuzeiTableRemoved": True, "localFavoritesCreated": True,
              "roomCredentialsMigratedToReferences": True, "settingsMigratedAndRestarted": True,
              "sauceKeyAbsentFromDataStorePlaintext": True, "api": adb("shell", "getprop", "ro.build.version.sdk").strip()}
    args.out.parent.mkdir(parents=True, exist_ok=True); args.out.write_text(json.dumps(report, indent=2) + "\n")
    print("Signed install-over-release-1 fixture passed; wrote public evidence without database/key payloads")
