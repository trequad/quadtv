#!/usr/bin/env python3
"""Build a QuadTV beta APK, copy it to the live admin portal, and publish release metadata.

This intentionally bypasses the browser form for Tre's normal beta workflow. It uses
local Gradle for the APK build and SSH/docker on Loki for file delivery + DB publish.
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID_DIR = ROOT / "android-app"
GRADLE_FILE = ANDROID_DIR / "app" / "build.gradle.kts"
DEFAULT_ORIGIN_API = "http://127.0.0.1:8088"
DEFAULT_PUBLIC_BASE_URL = "https://example.invalid"
DEFAULT_DEPLOY_HOST = "user@example.invalid"
DEFAULT_DEPLOY_PATH = "/opt/quadtv-admin"


def run(cmd: list[str], *, cwd: Path | None = None, input_text: str | None = None) -> str:
    print("$", " ".join(cmd))
    result = subprocess.run(cmd, cwd=cwd, input=input_text, text=True, capture_output=True)
    if result.stdout:
        print(result.stdout, end="")
    if result.stderr:
        print(result.stderr, end="", file=sys.stderr)
    if result.returncode != 0:
        raise SystemExit(result.returncode)
    return result.stdout


def fetch_current_release(origin_api: str) -> dict:
    with urllib.request.urlopen(f"{origin_api.rstrip('/')}/api/v1/releases/current", timeout=15) as response:
        return json.loads(response.read().decode("utf-8"))


def infer_next_version(current: dict) -> tuple[str, int]:
    release = current.get("release") or {}
    current_code = int(release.get("version_code") or 0)
    current_name = str(release.get("version_name") or "1.5.0")
    match = re.match(r"^(\d+)\.5\.(\d+)$", current_name)
    if match:
        version_name = f"{match.group(1)}.5.{int(match.group(2)) + 1}"
    else:
        version_name = "1.5.1"
    return version_name, current_code + 1


def update_gradle_version(version_name: str, version_code: int) -> None:
    major, track, patch = version_name.split(".", 2)
    if track != "5":
        raise SystemExit(f"Beta version_name must use track 5, e.g. 1.5.2; got {version_name}")
    text = GRADLE_FILE.read_text()
    text = re.sub(r"versionCode = \d+", f"versionCode = {version_code}", text, count=1)
    text = re.sub(r'versionName = "[^"]+"', f'versionName = "{major}"', text, count=1)
    text = re.sub(r'versionNameSuffix = "\.5\.[^"]+"', f'versionNameSuffix = ".5.{patch}"', text, count=1)
    GRADLE_FILE.write_text(text)


def find_beta_apk() -> Path:
    candidates = sorted((ANDROID_DIR / "app" / "build" / "outputs" / "apk" / "beta").glob("*.apk"))
    if not candidates:
        raise SystemExit("No beta APK found under app/build/outputs/apk/beta")
    apk = candidates[0]
    if apk.read_bytes()[:4] != b"PK\x03\x04":
        raise SystemExit(f"Built file is not an APK/ZIP: {apk}")
    return apk


def verify_apk_version(apk: Path, expected_version_name: str, expected_version_code: int) -> None:
    aapt_candidates = ["aapt"] + [
        str(path)
        for base in (Path("/opt/android-sdk/build-tools"), Path("/root/Android/Sdk/build-tools"))
        for path in base.glob("*/aapt")
    ]
    last_error = None
    for aapt in aapt_candidates:
        try:
            output = subprocess.check_output([aapt, "dump", "badging", str(apk)], text=True, stderr=subprocess.STDOUT, timeout=30)
            package_line = next(line for line in output.splitlines() if line.startswith("package:"))
            code_match = re.search(r"versionCode='(\d+)'", package_line)
            name_match = re.search(r"versionName='([^']+)'", package_line)
            if code_match is None or name_match is None:
                raise RuntimeError(f"Could not parse APK package line: {package_line}")
            version_code = int(code_match.group(1))
            version_name = name_match.group(1)
            if version_code != expected_version_code or version_name != expected_version_name:
                raise SystemExit(
                    f"Built APK version mismatch: expected {expected_version_name} code {expected_version_code}, "
                    f"got {version_name} code {version_code}. Refusing to publish bad metadata."
                )
            print(f"verified APK manifest: {version_name} code {version_code}")
            return
        except SystemExit:
            raise
        except Exception as error:
            last_error = error
    raise SystemExit(f"Could not verify APK version with aapt: {last_error}")


def publish_in_container(deploy_host: str, version_name: str, version_code: int, apk_url: str, changelog: str) -> None:
    remote_code = f"""
from datetime import datetime, timezone
from app.database import get_session_factory
from app.models import AppReleaseModel
s = get_session_factory()()
model = AppReleaseModel(
    version_name={version_name!r},
    version_code={version_code},
    changelog={changelog!r},
    apk_url={apk_url!r},
    minimum_supported_version_code=0,
    forced=False,
    published=True,
    release_date=datetime.now(timezone.utc),
)
s.add(model)
s.commit()
s.refresh(model)
print({{"id": model.id, "version_name": model.version_name, "version_code": model.version_code, "apk_url": model.apk_url}})
s.close()
"""
    run(["ssh", deploy_host, "docker exec -i quadtv-admin python -"], input_text=remote_code)


def verify_url(url: str) -> None:
    req = urllib.request.Request(url, headers={"User-Agent": "QuadTV-beta-publisher/1.0", "Range": "bytes=0-3"})
    with urllib.request.urlopen(req, timeout=30) as response:
        magic = response.read(4)
        ctype = response.headers.get("Content-Type", "")
        print(f"verify {url}: HTTP {response.status} {ctype} {magic.hex()}")
        if magic != b"PK\x03\x04":
            raise SystemExit("Published URL did not return APK magic bytes")


def main() -> None:
    parser = argparse.ArgumentParser(description="Build and publish a QuadTV beta APK release")
    parser.add_argument("--version-name", help="Beta version name, e.g. 1.5.2. Defaults to latest beta patch + 1.")
    parser.add_argument("--version-code", type=int, help="Android versionCode. Defaults to live latest + 1.")
    parser.add_argument("--changelog", default="QuadTV beta update.")
    parser.add_argument("--origin-api", default=DEFAULT_ORIGIN_API)
    parser.add_argument("--public-base-url", default=DEFAULT_PUBLIC_BASE_URL)
    parser.add_argument("--deploy-host", default=DEFAULT_DEPLOY_HOST)
    parser.add_argument("--deploy-path", default=DEFAULT_DEPLOY_PATH)
    args = parser.parse_args()

    current = fetch_current_release(args.origin_api)
    inferred_name, inferred_code = infer_next_version(current)
    version_name = args.version_name or inferred_name
    version_code = args.version_code or inferred_code
    filename = f"app-beta-{version_name}.apk"
    apk_url = f"{args.public_base_url.rstrip('/')}/downloads/{filename}"

    update_gradle_version(version_name, version_code)
    run(["./gradlew", "--no-daemon", ":app:assembleBeta"], cwd=ANDROID_DIR)
    apk = find_beta_apk()
    verify_apk_version(apk, version_name, version_code)

    with tempfile.TemporaryDirectory() as td:
        staged = Path(td) / filename
        shutil.copy2(apk, staged)
        run(["ssh", args.deploy_host, f"mkdir -p {args.deploy_path}/web/downloads"])
        run(["scp", str(staged), f"{args.deploy_host}:{args.deploy_path}/web/downloads/{filename}"])
        run(["ssh", args.deploy_host, f"docker cp {args.deploy_path}/web/downloads/{filename} quadtv-admin:/app/web/downloads/{filename}"])

    publish_in_container(args.deploy_host, version_name, version_code, apk_url, args.changelog)
    verify_url(f"{apk_url}?v={version_code}")
    print(json.dumps({"version_name": version_name, "version_code": version_code, "apk_url": apk_url}, indent=2))


if __name__ == "__main__":
    main()
