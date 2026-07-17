#!/usr/bin/env python3
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
VERSION_PATTERN = re.compile(r"[0-9A-Za-z][0-9A-Za-z.+_]*")


def main() -> int:
    version = input("Enter the new version: ").strip()
    if not VERSION_PATTERN.fullmatch(version):
        print(
            "Invalid version. Use only letters, numbers, dots, plus signs, and underscores; "
            "hyphens are not supported by PKGBUILD.",
            file=sys.stderr,
        )
        return 1

    updates = {
        ROOT / "PKGBUILD": [
            (r"(?m)^(pkgver=)[^\r\n]+$", rf"\g<1>{version}"),
        ],
        ROOT / "scripts/build/windows-installer.iss": [
            (
                r"(?m)^(; Override the package version with /DMyAppVersion=)[^.\s]+(?:\.[^.\s]+)*\.$",
                rf"\g<1>{version}.",
            ),
            (r'(?m)^(\s*#define MyAppVersion ")[^"]+("\s*)$', rf"\g<1>{version}\g<2>"),
        ],
        ROOT / "scripts/build/build.py": [
            (r'(?m)^(APP_VERSION = ")[^"]+("\s*)$', rf"\g<1>{version}\g<2>"),
        ],
    }

    updated_contents: dict[Path, str] = {}
    try:
        for path, replacements in updates.items():
            content = path.read_text(encoding="utf-8")
            for pattern, replacement in replacements:
                content, count = re.subn(pattern, replacement, content)
                if count != 1:
                    raise ValueError(f"Expected one version declaration matching {pattern!r} in {path}")
            updated_contents[path] = content
    except (OSError, ValueError) as exception:
        print(f"Version update failed: {exception}", file=sys.stderr)
        return 1

    try:
        for path, content in updated_contents.items():
            path.write_text(content, encoding="utf-8")
    except OSError as exception:
        print(f"Version update failed: {exception}", file=sys.stderr)
        return 1

    print(f"Updated singcli version to {version} in:")
    for path in updates:
        print(f"  {path.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
