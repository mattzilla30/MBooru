#!/usr/bin/env python3
"""Black-box real signed APK browsing on a fresh, rooted emulator; public safe-site evidence only."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shutil
import sqlite3
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.mattzilla30.mbooru"
REMOTE = f"/data/user/0/{PACKAGE}"
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("apk", type=Path, nargs="?", default=Path("validation/media-release.apk"))
parser.add_argument("--out", type=Path, default=Path("build/release-media"))
parser.add_argument("--sha256")
parser.add_argument("--sites", choices=["danbooru", "safebooru", "both"], default="both")
args = parser.parse_args()
args.out.mkdir(parents=True, exist_ok=True)
sha = hashlib.sha256(args.apk.read_bytes()).hexdigest()
assert args.sha256 is None or args.sha256 == sha, "Unexpected public APK checksum"


def adb(*values, check=True, timeout=35):
    command = ["adb", *map(str, values)]
    result = subprocess.run(command, check=check, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            text=True, timeout=timeout)
    return result.stdout


def network(enabled):
    action = "enable" if enabled else "disable"
    adb("shell", "svc", "wifi", action, check=False)
    adb("shell", "svc", "data", action, check=False)


def launch():
    adb("shell", "pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS", check=False)
    result = adb("shell", "am", "start", "-W", "-a", "android.intent.action.MAIN", "-c",
                 "android.intent.category.LAUNCHER", "-n", PACKAGE + "/.ui.activity.MainActivity", timeout=60)
    print(result, flush=True)
    assert "Error" not in result, "Real launcher activity did not start"


def capture_ui(destination, *, screenshot=True):
    destination.parent.mkdir(parents=True, exist_ok=True)
    remote_xml = "/data/local/tmp/mbooru-release-media-ui.xml"
    last = None
    for _ in range(3):
        try:
            adb("shell", "uiautomator", "dump", "--compressed", remote_xml, timeout=18)
            payload = adb("shell", "cat", remote_xml)
            xml = ET.fromstring(payload[payload.index("<?xml"):])
            destination.with_suffix(".xml").write_text(ET.tostring(xml, encoding="unicode"))
            if screenshot:
                with destination.with_suffix(".png").open("wb") as output:
                    subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=output, check=True, timeout=20)
            return xml
        except (ValueError, ET.ParseError, subprocess.SubprocessError) as failure:
            last = failure
            time.sleep(.5)
    raise RuntimeError("Could not capture current UI") from last


def image_type(data):
    if data.startswith(b"\xff\xd8\xff"): return "jpeg"
    if data.startswith(b"\x89PNG\r\n\x1a\n"): return "png"
    if data.startswith((b"GIF87a", b"GIF89a")): return "gif"
    if data.startswith(b"RIFF") and data[8:12] == b"WEBP": return "webp"
    return None


def owner_and_install(local, remote):
    adb("push", local, remote)
    adb("shell", "chown", f"{owner}:{owner}", remote)
    adb("shell", "chmod", "600", remote)
    adb("shell", "restorecon", remote)


def read_posts(root):
    local = root / "database-probe"
    shutil.rmtree(local, ignore_errors=True)
    local.mkdir()
    adb("pull", REMOTE + "/databases/.", local)
    try:
        with sqlite3.connect(local / "mbooru.db") as db:
            db.row_factory = sqlite3.Row
            return [dict(row) for row in db.execute(
                "SELECT id,rating,preview,sample,origin FROM posts WHERE booru_uid=777 AND query='' ORDER BY `index` LIMIT 60")]
    except sqlite3.Error:
        # A WAL copy made during a commit is not accepted as evidence; retry the snapshot later.
        return []


def cache_summary(root):
    local = root / "image-cache-probe"
    shutil.rmtree(local, ignore_errors=True)
    local.mkdir()
    adb("pull", REMOTE + "/cache/image_cache_v3/.", local, check=False)
    images = {}
    for path in local.glob("*.1"):
        payload = path.read_bytes()
        kind = image_type(payload[:16])
        if kind:
            images[path.stem] = {"bytes": len(payload), "format": kind,
                                 "sha256": hashlib.sha256(payload).hexdigest()}
    return {"metadataEntries": len(list(local.glob("*.0"))), "imageEntries": images,
            "imageBytes": sum(value["bytes"] for value in images.values())}


def cache_key(url):
    return hashlib.sha256(url.encode()).hexdigest()


def prepare_site(root, name, host, engine):
    network(False)
    adb("shell", "am", "force-stop", PACKAGE)
    local = root / "seed-database"
    shutil.rmtree(local, ignore_errors=True)
    local.mkdir()
    adb("pull", REMOTE + "/databases/.", local)
    database = local / "mbooru.db"
    with sqlite3.connect(database) as db:
        db.execute("PRAGMA foreign_keys=ON")
        db.execute("DELETE FROM boorus")
        columns = [row[1] for row in db.execute("PRAGMA table_info(boorus)")]
        fixture = {"uid": 777, "name": name, "scheme": "https", "host": host,
                   "type": engine, "hash_salt": "", "blacklists": "[]", "user": None,
                   "auth": None, "path": None}
        names = [column for column in columns if column in fixture]
        db.execute("INSERT INTO boorus (" + ",".join('`'+name+'`' for name in names) + ") VALUES (" +
                   ",".join("?" for _ in names) + ")", [fixture[column] for column in names])
        db.commit()
        db.execute("PRAGMA wal_checkpoint(TRUNCATE)")
    for suffix in ["-wal", "-shm"]:
        adb("shell", "rm", "-f", REMOTE + "/databases/mbooru.db" + suffix)
    owner_and_install(database, REMOTE + "/databases/mbooru.db")
    # This is a freshly cleared emulator-only fixture. Re-run the app's real legacy preference
    # migration so selected site/safe mode/DoH have durable values before real UI startup.
    adb("shell", "rm", "-f", REMOTE + "/files/datastore/mbooru.preferences_pb",
        REMOTE + "/files/datastore/mbooru.preferences_pb.tmp")
    preferences = ET.Element("map")
    ET.SubElement(preferences, "long", name="booru_uid_activated", value="777")
    ET.SubElement(preferences, "boolean", name="settings_safe_mode", value="true")
    ET.SubElement(preferences, "boolean", name="settings_dns_over_https", value="true")
    ET.SubElement(preferences, "boolean", name="featured_boorus_v1_installed", value="true")
    ET.SubElement(preferences, "string", name="settings_detail_size").text = "origin"
    ET.SubElement(preferences, "string", name="settings_page_limit").text = "20"
    path = root / "fixture-preferences.xml"
    ET.ElementTree(preferences).write(path, encoding="utf-8", xml_declaration=True)
    adb("shell", "mkdir", "-p", REMOTE + "/shared_prefs")
    adb("shell", "chown", f"{owner}:{owner}", REMOTE + "/shared_prefs")
    adb("shell", "chmod", "700", REMOTE + "/shared_prefs")
    owner_and_install(path, REMOTE + "/shared_prefs/" + PACKAGE + "_preferences.xml")
    # No cache/preloaded images can satisfy the assertions. Only live real-app fetches can.
    adb("shell", "rm", "-rf", REMOTE + "/cache/image_cache_v3")
    adb("logcat", "-c")
    network(True)
    time.sleep(2)


def run_site(root, name, host, engine, allowed_ratings):
    directory = args.out / name.lower()
    directory.mkdir(exist_ok=True)
    report = {"site": host, "apkSha256": sha, "safeMode": True,
              "queryPolicy": "rating:general" if engine == 0 else "rating:safe",
              "explicitDohEnabled": True, "originViewerSelected": True, "checks": {}}
    try:
        prepare_site(root, name, host, engine)
        launch()
        deadline = time.monotonic() + 100
        posts, cache = [], {}
        while time.monotonic() < deadline:
            posts = read_posts(root)
            cache = cache_summary(root)
            preview_keys = {cache_key(row["preview"]) for row in posts if row["preview"]}
            matching = preview_keys.intersection(cache.get("imageEntries", {}))
            print(name, "posts=", len(posts), "livePreviewEntries=", len(matching), "cacheImageBytes=", cache.get("imageBytes", 0), flush=True)
            if posts and matching:
                break
            time.sleep(3)
        report["postCount"] = len(posts)
        report["ratingsObserved"] = sorted({row["rating"] for row in posts})
        report["gridCache"] = cache
        report["checks"]["postsLoadedFromLiveApi"] = bool(posts)
        report["checks"]["onlySafeRatings"] = bool(posts) and all(row["rating"] in allowed_ratings for row in posts)
        report["checks"]["livePreviewBytesCached"] = bool(posts) and bool({cache_key(row["preview"]) for row in posts}.intersection(cache.get("imageEntries", {})))
        assert report["checks"]["onlySafeRatings"], "Expected only general/safe posts from the public API"
        xml = capture_ui(directory / "grid")
        assert report["checks"]["livePreviewBytesCached"], "No real grid thumbnail bytes reached the actual Coil cache"
        still_posts = {row["id"]: row for row in posts if re.search(r"\.(?:jpe?g|png|webp)(?:\?|$)", row["origin"], re.I)}
        selected = None
        bounds = None
        for node in xml.iter("node"):
            match = re.search(r"\bPost\s+(\d+)\b", node.get("content-desc", ""), re.I)
            if not match or int(match.group(1)) not in still_posts:
                continue
            values = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
            if values:
                x1, y1, x2, y2 = map(int, values.groups())
                if x2 > x1 and y2 > y1 and y1 >= 100:
                    selected = still_posts[int(match.group(1))]
                    bounds = ((x1 + x2)//2, (y1 + y2)//2)
                    break
        assert selected and bounds, "No visible real still-image post was clickable in the actual grid"
        report["selectedPostId"] = selected["id"]
        origin_key = cache_key(selected["origin"])
        adb("shell", "input", "tap", *bounds)
        deadline = time.monotonic() + 120
        viewer_opened = False
        while time.monotonic() < deadline:
            xml = capture_ui(directory / "detail-probe", screenshot=False)
            viewer_opened = any(node.get("resource-id", "").endswith("/post_media_image") for node in xml.iter("node"))
            cache = cache_summary(root)
            failed = any("Image could not be loaded" in node.get("text", "") for node in xml.iter("node"))
            loading = any(node.get("class", "").endswith("ProgressBar") for node in xml.iter("node"))
            if viewer_opened and origin_key in cache.get("imageEntries", {}) and not loading:
                break
            if failed:
                break
            time.sleep(2)
        capture_ui(directory / "detail")
        report["detailCache"] = cache
        report["checks"]["realViewerOpened"] = viewer_opened
        report["checks"]["liveOriginBytesCached"] = origin_key in cache.get("imageEntries", {})
        report["checks"]["viewerWithoutRetryError"] = not failed
        report["checks"]["viewerCompletedLoading"] = not loading
        assert viewer_opened, "Actual native image viewer did not open"
        assert report["checks"]["liveOriginBytesCached"], "The selected original was not downloaded through the production app"
        assert not failed and not loading, "Original viewer is still loading or displays its image error"
    except Exception as failure:
        report["error"] = f"{type(failure).__name__}: {failure}"
        try: capture_ui(directory / "failure", screenshot=report["checks"].get("onlySafeRatings", False))
        except Exception as capture_failure: report["captureError"] = str(capture_failure)
    finally:
        crash = adb("logcat", "-d", "-b", "crash", check=False)
        (directory / "crash.txt").write_text(crash)
        (directory / "logcat.txt").write_text(adb("logcat", "-d", "-t", "5000", check=False))
        report["checks"]["noFatalAppCrash"] = not ("FATAL EXCEPTION" in crash and PACKAGE in crash)
        report["success"] = not report.get("error") and all(report["checks"].values())
        (directory / "report.json").write_text(json.dumps(report, indent=2) + "\n")
        print(json.dumps({"site": host, "success": report["success"], "error": report.get("error"), "checks": report["checks"]}), flush=True)
    return report


assert adb("shell", "getprop", "ro.kernel.qemu").strip() == "1", "Fresh emulator only"
adb("root"); adb("wait-for-device")
assert re.search(r"uid=0(?:\(|\s|$)", adb("shell", "id")), "Rooted google_apis emulator required"
assert int(adb("shell", "getprop", "ro.build.version.sdk").strip()) >= 28
adb("install", "-r", args.apk, timeout=120)
assert "Success" in adb("shell", "pm", "clear", PACKAGE), "Could not initialize fresh emulator-only application data"
package_dump = adb("shell", "dumpsys", "package", PACKAGE)
assert "DEBUGGABLE" not in package_dump, "A production non-debuggable release APK is required"
owner_match = re.search(r"(?:userId|appId)=(\d+)", package_dump)
assert owner_match, "Installed package UID unavailable"
owner = owner_match.group(1)
network(False)
launch()
deadline = time.monotonic() + 40
while time.monotonic() < deadline:
    if "no such" not in adb("shell", "ls", REMOTE + "/databases/mbooru.db", check=False).lower():
        time.sleep(3)
        break
    time.sleep(.5)
else:
    raise AssertionError("Real release application database did not initialize")
reports = []
with tempfile.TemporaryDirectory(prefix="mbooru-release-media-") as temporary:
    root = Path(temporary)
    if args.sites in ("both", "danbooru"):
        reports.append(run_site(root, "Danbooru", "danbooru.donmai.us", 0, {"g"}))
    if args.sites in ("both", "safebooru"):
        reports.append(run_site(root, "Safebooru", "safebooru.org", 6, {"s", "g"}))
summary = {"checkedAt": datetime.now(timezone.utc).isoformat(), "apkSha256": sha,
           "api": adb("shell", "getprop", "ro.build.version.sdk").strip(),
           "realSignedAppWithProductionR8": True, "freshEmulatorDataOnly": True,
           "noPrivateCredentials": True, "sites": reports, "success": all(report["success"] for report in reports)}
(args.out / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
assert summary["success"], "Real release browsing/image diagnostic failed; inspect exported safe-site screenshots/XML/cache reports/logcat"
print("Real signed app live preview + original image journeys passed", flush=True)
