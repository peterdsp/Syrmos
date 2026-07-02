#!/usr/bin/env python3
"""Wire the Next Departures home-screen widget into the SyrmosWidget target.

Registers, in the SyrmosWidgetExtension target only:
  - SyrmosWidget/NextDeparturesWidget.swift (the new widget source)
  - the schedule source files it needs, shared from the app target
  - the bundled seed-schedules-v2 resource folder

The widget files/IDs are read from the committed project; this only adds build
references, it never removes anything. Idempotent: re-running makes no further
changes (each added reference carries a "(widget)" comment marker).

Usage:
  python3 scripts/add-next-departures-widget.py            # edit the project
  python3 scripts/add-next-departures-widget.py /tmp/x.pbxproj   # dry-run a copy

After running, open the project in Xcode and build the SyrmosWidgetExtension
scheme, then:
  - add any file the compiler still reports as missing via File Inspector ->
    Target Membership -> SyrmosWidgetExtension (the exact closure is
    compiler-driven, so a couple of stragglers are expected);
  - for the "nearest station (GPS)" mode, add the Location capability to the
    SyrmosWidgetExtension target (Signing & Capabilities). The "choose station"
    mode works without it.
"""
from __future__ import annotations

import re
import sys
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PBX = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "iosApp/Syrmos.xcodeproj/project.pbxproj"

# IDs read from the current project (stable across normal edits).
WIDGET_GROUP = "DA7A010CF00D010CF00D010C"      # PBXGroup "SyrmosWidget"
WIDGET_SOURCES = "DA7A0104F00D0104F00D0104"    # widget PBXSourcesBuildPhase
WIDGET_RESOURCES = "DA7A0106F00D0106F00D0106"  # widget PBXResourcesBuildPhase
SEED_FILEREF = "AABB01CD02EF030405060709"      # fileRef for Resources/seed-schedules-v2

# New widget source (path relative to SOURCE_ROOT, like the other widget files).
NEW_SOURCE_PATH = "SyrmosWidget/NextDeparturesWidget.swift"

# App sources to share into the widget target (by filename; the existing fileRef
# is looked up at runtime). The compiler may flag a few more — add those in Xcode.
SHARED_SOURCES = [
    "ScheduleProjector.swift",
    "TransitData.swift",
    "StationCoordinates.swift",
    "SyrmosSchedulesService.swift",
    "SyrmosSchedulesStore.swift",
    "SyrmosStationOffsetsStore.swift",
    "AirportData.swift",
    "Localization.swift",
    "SyrmosColors.swift",
    "DataFreshness.swift",
    "LivePositionsService.swift",
    "SyrmosRouteShapesStore.swift",
]


def gen_id() -> str:
    return uuid.uuid4().hex[:24].upper()


def find_fileref(src: str, filename: str) -> str | None:
    m = re.search(
        r"([0-9A-F]{24}) /\* [^*]*" + re.escape(filename) + r" \*/ = \{isa = PBXFileReference",
        src,
    )
    return m.group(1) if m else None


def insert_after_files(src: str, phase_id: str, entry: str) -> str:
    """Insert `entry` right after the `files = (` of the given build phase.
    Idempotency is handled by each caller's top-level guard, so this always
    inserts when reached."""
    pat = re.compile(r"(" + phase_id + r" /\* \w+ \*/ = \{.*?files = \(\n)", re.DOTALL)
    m = pat.search(src)
    if not m:
        print(f"WARN: build phase {phase_id} not found", file=sys.stderr)
        return src
    return src[: m.end()] + entry + src[m.end():]


def append_build_file(src: str, build_id: str, comment: str, file_ref: str, ref_comment: str) -> str:
    line = (
        f"\t\t{build_id} /* {comment} */ = "
        f"{{isa = PBXBuildFile; fileRef = {file_ref} /* {ref_comment} */; }};\n"
    )
    return re.sub(r"(/\* End PBXBuildFile section \*/)", line + r"\1", src, count=1)


def main() -> None:
    src = PBX.read_text()
    orig = src

    # 1. The new widget source file.
    if NEW_SOURCE_PATH not in src:
        ref_id = gen_id()
        build_id = gen_id()
        ref_line = (
            f"\t\t{ref_id} /* {NEW_SOURCE_PATH} */ = "
            f"{{isa = PBXFileReference; lastKnownFileType = sourcecode.swift; "
            f"path = {NEW_SOURCE_PATH}; sourceTree = SOURCE_ROOT; }};\n"
        )
        src = re.sub(r"(/\* End PBXFileReference section \*/)", ref_line + r"\1", src, count=1)
        comment = f"{NEW_SOURCE_PATH} in Sources (widget)"
        src = append_build_file(src, build_id, comment, ref_id, NEW_SOURCE_PATH)
        # Group children.
        gpat = re.compile(r"(" + WIDGET_GROUP + r" /\* SyrmosWidget \*/ = \{.*?children = \(\n)", re.DOTALL)
        gm = gpat.search(src)
        if gm:
            src = src[: gm.end()] + f"\t\t\t\t{ref_id} /* {NEW_SOURCE_PATH} */,\n" + src[gm.end():]
        src = insert_after_files(src, WIDGET_SOURCES, f"\t\t\t\t{build_id} /* {comment} */,\n")
        print("+ NextDeparturesWidget.swift -> SyrmosWidget target")
    else:
        print("already wired: NextDeparturesWidget.swift")

    # 2. Shared app sources into the widget target (reuse existing fileRefs).
    for fn in SHARED_SOURCES:
        comment = f"{fn} in Sources (widget)"
        if comment in src:
            print(f"already shared: {fn}")
            continue
        ref = find_fileref(src, fn)
        if not ref:
            print(f"WARN: fileRef for {fn} not found; add it in Xcode", file=sys.stderr)
            continue
        build_id = gen_id()
        src = append_build_file(src, build_id, comment, ref, fn)
        src = insert_after_files(src, WIDGET_SOURCES, f"\t\t\t\t{build_id} /* {comment} */,\n")
        print(f"+ {fn} -> SyrmosWidget target")

    # 3. Seed resource folder into the widget Resources phase.
    seed_comment = "seed-schedules-v2 in Resources (widget)"
    if seed_comment in src:
        print("already wired: seed-schedules-v2 (widget)")
    else:
        build_id = gen_id()
        src = append_build_file(src, build_id, seed_comment, SEED_FILEREF, "Resources/seed-schedules-v2")
        src = insert_after_files(src, WIDGET_RESOURCES, f"\t\t\t\t{build_id} /* {seed_comment} */,\n")
        print("+ seed-schedules-v2 -> SyrmosWidget Resources")

    if src != orig:
        PBX.write_text(src)
        print("\nproject.pbxproj updated.")
        print("Next: open Xcode, build the SyrmosWidgetExtension scheme, add any file the")
        print("compiler still flags (Target Membership), and add the Location capability to")
        print("the widget target for the nearest-GPS mode.")
    else:
        print("\nno changes")


if __name__ == "__main__":
    main()
