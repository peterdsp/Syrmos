"""Register the airport-services redesign Swift files in project.pbxproj.

Idempotent: skips anything already present. Anchors insertions on existing
sibling entries so the app and test Sources phases each get the right files.
"""
from __future__ import annotations

import re
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PBX = ROOT / "iosApp/Syrmos.xcodeproj/project.pbxproj"

# filename, SOURCE_ROOT path or None (=> "<group>"), group-anchor sibling,
# sources-phase-anchor sibling (a filename already in the right phase)
FILES = [
    ("AirportBusService.swift", "iosApp/Core/Networking/AirportBusService.swift",
     "SyrmosDeparturesService.swift", "TimetablesView.swift"),
    ("AirportBusVehicles.swift", "iosApp/Core/Networking/AirportBusVehicles.swift",
     "SyrmosDeparturesService.swift", "TimetablesView.swift"),
    ("AirportServiceRows.swift", "iosApp/Features/Timetables/AirportServiceRows.swift",
     "TimetablesView.swift", "TimetablesView.swift"),
    ("AirportServiceTests.swift", None,
     "ScheduleProjectorTests.swift", "ScheduleProjectorTests.swift"),
]


def gen_id() -> str:
    return uuid.uuid4().hex[:24].upper()


def main() -> None:
    src = PBX.read_text()

    for filename, src_path, group_anchor, phase_anchor in FILES:
        if f"/* {filename} */" in src:
            print(f"already registered: {filename}")
            continue

        file_ref = gen_id()
        build_ref = gen_id()

        if src_path:
            ref_line = (f"\t\t{file_ref} /* {filename} */ = {{isa = PBXFileReference; "
                        f"lastKnownFileType = sourcecode.swift; path = {src_path}; "
                        f"sourceTree = SOURCE_ROOT; }};\n")
        else:
            ref_line = (f"\t\t{file_ref} /* {filename} */ = {{isa = PBXFileReference; "
                        f"includeInIndex = 1; lastKnownFileType = sourcecode.swift; "
                        f"path = {filename}; sourceTree = \"<group>\"; }};\n")

        build_line = (f"\t\t{build_ref} /* {filename} in Sources */ = "
                      f"{{isa = PBXBuildFile; fileRef = {file_ref} /* {filename} */; }};\n")

        src = src.replace("/* End PBXFileReference section */", ref_line + "/* End PBXFileReference section */", 1)
        src = src.replace("/* End PBXBuildFile section */", build_line + "/* End PBXBuildFile section */", 1)

        # Group children: insert after the anchor sibling's child line.
        child = f"\t\t\t\t{file_ref} /* {filename} */,\n"
        gm = re.search(r"[0-9A-F]{24} /\* " + re.escape(group_anchor) + r" \*/,\n", src)
        assert gm, f"group anchor {group_anchor} not found"
        src = src[:gm.end()] + child + src[gm.end():]

        # Sources phase: insert after the anchor sibling's "in Sources" build line.
        srcline = f"\t\t\t\t{build_ref} /* {filename} in Sources */,\n"
        pm = re.search(r"[0-9A-F]{24} /\* " + re.escape(phase_anchor) + r" in Sources \*/,\n", src)
        assert pm, f"phase anchor {phase_anchor} not found"
        src = src[:pm.end()] + srcline + src[pm.end():]

        print(f"+ registered {filename}")

    PBX.write_text(src)
    print("project.pbxproj updated")


if __name__ == "__main__":
    main()
