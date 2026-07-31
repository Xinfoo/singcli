#!/usr/bin/env python3
import os
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SRC_DIR = ROOT / "src"
BUILD_DIR = ROOT / "build" / "native"
CLASSES_DIR = BUILD_DIR / "classes"
MANIFEST_PATH = BUILD_DIR / "MANIFEST.MF"
INPUT_JAR_PATH = BUILD_DIR / "singcli.jar"
DIST_DIR = ROOT / "dist"
EXECUTABLE_NAME = "singcli.exe" if os.name == "nt" else "singcli"
EXECUTABLE_PATH = DIST_DIR / EXECUTABLE_NAME
MAIN_CLASS = "singcli.Main"
APP_VERSION = "1.2.3"


def main() -> int:
    sources = sorted(SRC_DIR.rglob("*.java"))
    if not sources:
        print("No Java sources found in src/", file=sys.stderr)
        return 1

    graalvm_home = find_graalvm_home()
    javac = graalvm_tool(graalvm_home, "javac")
    jar = graalvm_tool(graalvm_home, "jar")
    native_image = graalvm_tool(graalvm_home, "native-image")

    clean()
    CLASSES_DIR.mkdir(parents=True, exist_ok=True)
    DIST_DIR.mkdir(parents=True, exist_ok=True)

    run([
        str(javac),
        "--release", "17",
        "-encoding", "UTF-8",
        "-d", str(CLASSES_DIR),
        *map(str, sources),
    ])
    write_manifest(javac)
    run([
        str(jar),
        "cfm", str(INPUT_JAR_PATH), str(MANIFEST_PATH),
        "-C", str(CLASSES_DIR), ".",
    ])
    run([
        str(native_image),
        "--enable-http",
        "--enable-https",
        "-march=compatibility",
        "-Os",
        "-jar", str(INPUT_JAR_PATH),
        "-o", str(EXECUTABLE_PATH),
    ])

    print(f"Built native executable: {EXECUTABLE_PATH}")
    return 0


def find_graalvm_home() -> Path:
    candidates = []
    for name in ("GRAALVM_HOME", "JAVA_HOME"):
        value = os.environ.get(name)
        if value:
            candidates.append(Path(value))

    native_image = shutil.which("native-image")
    if native_image:
        candidates.append(Path(native_image).absolute().parent.parent)

    candidates.append(Path.home() / ".local" / "share" / "graalvm" / "current")
    for candidate in candidates:
        home = candidate.expanduser().resolve()
        if all(graalvm_tool(home, name).is_file() for name in ("javac", "jar", "native-image")):
            print(f"Using GraalVM: {home}", flush=True)
            return home

    raise RuntimeError(
        "GraalVM with javac, jar, and native-image was not found. "
        "Set GRAALVM_HOME or add GraalVM bin to PATH."
    )


def graalvm_tool(home: Path, name: str) -> Path:
    candidates = [home / "bin" / file_name for file_name in tool_file_names(name)]
    return next((candidate for candidate in candidates if candidate.is_file()), candidates[0])


def tool_file_names(name: str) -> list[str]:
    if os.name != "nt":
        return [name]
    if name == "native-image":
        return [name + suffix for suffix in (".exe", ".cmd", ".bat")]
    return [name + ".exe"]


def clean() -> None:
    for path in (BUILD_DIR, DIST_DIR):
        if path.exists():
            shutil.rmtree(path)


def write_manifest(javac: Path) -> None:
    build_time = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    build_jdk = command_output([str(javac), "-version"], "unknown")
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
    print("+ " + " ".join(command), flush=True)
    subprocess.run(command, cwd=ROOT, check=True)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, subprocess.CalledProcessError) as exception:
        message = str(exception)
        if message:
            print(f"Native build failed: {message}", file=sys.stderr)
        raise SystemExit(exception.returncode if isinstance(exception, subprocess.CalledProcessError) else 1)
