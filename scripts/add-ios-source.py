#!/usr/bin/env python3
"""Register a Swift source file in iosApp/Syrmos.xcodeproj/project.pbxproj.

The project keeps explicit file references rather than a synchronized folder, so
a new file that is only written to disk never reaches a target and the build
silently drops it. This adds the file reference, puts it in a group and appends
it to the named targets' Sources phases.

Idempotent: a file already referenced is left alone, and a file already in a
target's Sources phase is not added twice.

usage:
  scripts/add-ios-source.py <path-under-iosApp/> <group-name> <target> [<target>...]

example:
  scripts/add-ios-source.py iosApp/Core/Schedule/StationComplexBoard.swift Schedule Syrmos iosAppTests
"""
from __future__ import annotations

import re
import sys
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PBX = ROOT / "iosApp/Syrmos.xcodeproj/project.pbxproj"


def gen_id(src: str) -> str:
    while True:
        candidate = uuid.uuid4().hex[:24].upper()
        if candidate not in src:
            return candidate


def target_sources_phase(src: str, target_name: str) -> str:
    """The PBXSourcesBuildPhase id of a native target, by target name."""
    # Each PBXNativeTarget block lists buildPhases then `name = <target>;`.
    for match in re.finditer(
        # `[^\n]*?` matters: with re.S a bare `.*?` would let the comment span
        # lines and pair one target's id with another target's body.
        r"\t\t([0-9A-F]{24}) /\* [^\n]*? \*/ = \{\n\t\t\tisa = PBXNativeTarget;(.*?)\n\t\t\};",
        src,
        re.S,
    ):
        body = match.group(2)
        # Target names can be quoted when they contain spaces
        # ("Syrmos - Athens Rail Times").
        name = re.search(r"\n\t\t\tname = \"?([^\";]+)\"?;", body)
        if not name or name.group(1).strip() != target_name:
            continue
        phases = re.search(r"buildPhases = \(\n(.*?)\n\t\t\t\);", body, re.S)
        if not phases:
            continue
        for pid in re.findall(r"\t\t\t\t([0-9A-F]{24}) /\*", phases.group(1)):
            block = re.search(
                r"\t\t" + pid + r" /\* [^\n]*? \*/ = \{\n\t\t\tisa = (\w+);", src
            )
            if block and block.group(1) == "PBXSourcesBuildPhase":
                return pid
    raise SystemExit(f"no Sources build phase found for target {target_name}")


def group_id(src: str, group_name: str) -> str:
    match = re.search(
        r"\t\t([0-9A-F]{24}) /\* " + re.escape(group_name) + r" \*/ = \{\n\t\t\tisa = PBXGroup;",
        src,
    )
    if not match:
        raise SystemExit(f"no PBXGroup named {group_name}")
    return match.group(1)


def main() -> None:
    if len(sys.argv) < 4:
        raise SystemExit(__doc__)
    rel_path, group_name, targets = sys.argv[1], sys.argv[2], sys.argv[3:]
    name = Path(rel_path).name
    if not (ROOT / "iosApp" / rel_path).exists():
        raise SystemExit(f"missing file: iosApp/{rel_path}")

    src = PBX.read_text()
    file_ref = None
    existing = re.search(
        r"\t\t([0-9A-F]{24}) /\* " + re.escape(name) + r" \*/ = \{isa = PBXFileReference;[^\n]*?path = "
        + re.escape(rel_path) + r";",
        src,
    )
    if existing:
        file_ref = existing.group(1)
        print(f"file reference exists: {name} ({file_ref})")
    else:
        file_ref = gen_id(src)
        anchor = "/* End PBXFileReference section */"
        entry = (
            f"\t\t{file_ref} /* {name} */ = {{isa = PBXFileReference; "
            f"lastKnownFileType = sourcecode.swift; path = {rel_path}; sourceTree = SOURCE_ROOT; }};\n"
        )
        src = src.replace(anchor, entry + anchor, 1)
        gid = group_id(src, group_name)
        gmatch = re.search(
            r"(\t\t" + gid + r" /\* [^\n]*? \*/ = \{\n\t\t\tisa = PBXGroup;\n\t\t\tchildren = \(\n)",
            src,
        )
        src = src[: gmatch.end(1)] + f"\t\t\t\t{file_ref} /* {name} */,\n" + src[gmatch.end(1):]
        print(f"added file reference {name} -> group {group_name}")

    for target in targets:
        phase = target_sources_phase(src, target)
        pblock = re.search(
            r"(\t\t" + phase + r" /\* [^\n]*? \*/ = \{\n\t\t\tisa = PBXSourcesBuildPhase;.*?files = \(\n)(.*?)(\n\t\t\t\);)",
            src,
            re.S,
        )
        if file_ref in pblock.group(2) and name in pblock.group(2):
            print(f"already in {target}")
            continue
        build_id = gen_id(src)
        build_entry = (
            f"\t\t{build_id} /* {name} in Sources */ = {{isa = PBXBuildFile; "
            f"fileRef = {file_ref} /* {name} */; }};\n"
        )
        src = src.replace(
            "/* End PBXBuildFile section */", build_entry + "/* End PBXBuildFile section */", 1
        )
        pblock = re.search(
            r"(\t\t" + phase + r" /\* [^\n]*? \*/ = \{\n\t\t\tisa = PBXSourcesBuildPhase;.*?files = \(\n)(.*?)(\n\t\t\t\);)",
            src,
            re.S,
        )
        src = (
            src[: pblock.end(1)]
            + f"\t\t\t\t{build_id} /* {name} in Sources */,\n"
            + src[pblock.end(1):]
        )
        print(f"added {name} to target {target}")

    PBX.write_text(src)


if __name__ == "__main__":
    main()
