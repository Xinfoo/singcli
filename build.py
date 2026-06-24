#!/usr/bin/env python3
import shutil
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent
SRC_DIR = ROOT / "src"
BUILD_DIR = ROOT / "build"
CLASSES_DIR = BUILD_DIR / "classes"
MANIFEST_PATH = BUILD_DIR / "MANIFEST.MF"
DIST_DIR = ROOT / "dist"
JAR_PATH = DIST_DIR / "singcli.jar"
MAIN_CLASS = "Main"


def main() -> int:
    sources = sorted(SRC_DIR.rglob("*.java"))
    if not sources:
        print("No Java sources found in src/", file=sys.stderr)
        return 1

    clean()
    CLASSES_DIR.mkdir(parents=True, exist_ok=True)
    DIST_DIR.mkdir(parents=True, exist_ok=True)

    run(["javac", "-encoding", "UTF-8", "-d", str(CLASSES_DIR), *map(str, sources)])
    write_manifest()
    run(["jar", "cfm", str(JAR_PATH), str(MANIFEST_PATH), "-C", str(CLASSES_DIR), "."])

    print(f"Built: {JAR_PATH}")
    print(f"Run with: java -jar {JAR_PATH}")
    return 0


def clean() -> None:
    shutil.rmtree(BUILD_DIR, ignore_errors=True)
    shutil.rmtree(DIST_DIR, ignore_errors=True)


def write_manifest() -> None:
    MANIFEST_PATH.write_text(
        f"Manifest-Version: 1.0\nMain-Class: {MAIN_CLASS}\n\n",
        encoding="utf-8",
    )


def run(command: list[str]) -> None:
    print("+ " + " ".join(command))
    subprocess.run(command, cwd=ROOT, check=True)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as exc:
        raise SystemExit(exc.returncode)
