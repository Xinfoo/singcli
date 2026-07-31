#!/usr/bin/env python3
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SRC_DIR = ROOT / "src"
BUILD_DIR = ROOT / "build"
CLASSES_DIR = BUILD_DIR / "classes"
MANIFEST_PATH = BUILD_DIR / "MANIFEST.MF"
DIST_DIR = ROOT / "dist"
JAR_PATH = DIST_DIR / "singcli.jar"
MAIN_CLASS = "singcli.Main"
APP_VERSION = "1.2.4"


def main() -> int:
    sources = sorted(SRC_DIR.rglob("*.java"))
    if not sources:
        print("No Java sources found in src/", file=sys.stderr)
        return 1

    clean()
    CLASSES_DIR.mkdir(parents=True, exist_ok=True)
    DIST_DIR.mkdir(parents=True, exist_ok=True)

    run([
        "javac",
        "--release", "17",
        "-encoding", "UTF-8",
        "-d", str(CLASSES_DIR),
        *map(str, sources),
    ])
    write_manifest()
    run(["jar", "cfm", str(JAR_PATH), str(MANIFEST_PATH), "-C", str(CLASSES_DIR), "."])

    print(f"Built: {JAR_PATH}")
    print(f"Run with: java -jar {JAR_PATH}")
    return 0


def clean() -> None:
    shutil.rmtree(BUILD_DIR, ignore_errors=True)
    shutil.rmtree(DIST_DIR, ignore_errors=True)


def write_manifest() -> None:
    build_time = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    build_jdk = command_output(["javac", "-version"], "unknown")
    MANIFEST_PATH.write_text(
        "Manifest-Version: 1.0\n"
        f"Main-Class: {MAIN_CLASS}\n"
        "Implementation-Title: singcli\n"
        f"Implementation-Version: {APP_VERSION}\n"
        f"Build-Time: {build_time}\n"
        f"Build-Jdk: {build_jdk}\n\n",
        encoding="utf-8",
    )


def command_output(command: list[str], default: str) -> str:
    try:
        result = subprocess.run(
            command,
            cwd=ROOT,
            check=False,
            capture_output=True,
            text=True,
        )
    except OSError:
        return default

    output = (result.stdout or result.stderr).strip()
    return output if result.returncode == 0 and output else default


def run(command: list[str]) -> None:
    print("+ " + " ".join(command))
    subprocess.run(command, cwd=ROOT, check=True)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as exc:
        raise SystemExit(exc.returncode)
