"""Register Swift source files in iosApp/Syrmos.xcodeproj/project.pbxproj.

Idempotent: skips files already present. Adds them under the iosApp/Models
group (or Views/Stations etc. inferred from path).
"""
from __future__ import annotations

import re
import sys
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PBX = ROOT / "iosApp/Syrmos.xcodeproj/project.pbxproj"

# (relative-path-under-iosApp/iosApp, target-group-name)
FILES_TO_ADD = [
    ("Models/SyrmosSchedulesService.swift", "Models"),
    ("Models/SyrmosSchedulesStore.swift", "Models"),
    ("Models/ScheduleProjector.swift", "Models"),
    ("Models/SyrmosVisualOverridesStore.swift", "Models"),
    ("Views/Timetables/TimetablesView.swift", "Views"),
    ("Views/Stations/StationMapSheet.swift", "Views"),
    ("Views/SafariSheet.swift", "Views"),
    ("Models/ThemeManager.swift", "Models"),
]


def gen_id() -> str:
    return uuid.uuid4().hex[:24].upper()


def main() -> None:
    src = PBX.read_text()
    changed = False

    for relpath, group_name in FILES_TO_ADD:
        filename = Path(relpath).name
        if f"path = {filename};" in src or f"path = \"{filename}\";" in src:
            print(f"already registered: {filename}")
            continue

        file_ref_id = gen_id()
        build_id = gen_id()

        # 1. Add PBXFileReference
        ref_line = (
            f"\t\t{file_ref_id} /* {filename} */ = "
            f"{{isa = PBXFileReference; lastKnownFileType = sourcecode.swift; "
            f"path = {filename}; sourceTree = \"<group>\"; }};\n"
        )
        src = re.sub(
            r"(/\* End PBXFileReference section \*/)",
            ref_line + r"\1",
            src,
            count=1,
        )

        # 2. Add PBXBuildFile
        build_line = (
            f"\t\t{build_id} /* {filename} in Sources */ = "
            f"{{isa = PBXBuildFile; fileRef = {file_ref_id} /* {filename} */; }};\n"
        )
        src = re.sub(
            r"(/\* End PBXBuildFile section \*/)",
            build_line + r"\1",
            src,
            count=1,
        )

        # 3. Add to PBXSourcesBuildPhase
        src = re.sub(
            r"(/* Begin PBXSourcesBuildPhase section \*/.*?files = \(\s*)",
            lambda m: m.group(1) + f"\t\t\t\t{build_id} /* {filename} in Sources */,\n",
            src,
            count=1,
            flags=re.DOTALL,
        )

        # 4. Add to the named group's children
        # Find the group block by name (e.g., 30D4D8117BDD6930DEAF1DB9 /* Stations */ = { ... children = ( ... ); })
        group_pattern = re.compile(
            r"([0-9A-F]{24} /\* " + re.escape(group_name) + r" \*/ = \{[^}]*?children = \(\s*)",
            re.DOTALL,
        )
        m = group_pattern.search(src)
        if m:
            src = src[: m.end()] + f"\t\t\t\t{file_ref_id} /* {filename} */,\n" + src[m.end():]
            print(f"+ added {filename} to group '{group_name}'")
            changed = True
        else:
            print(f"WARN: group '{group_name}' not found for {filename}", file=sys.stderr)

    if changed:
        PBX.write_text(src)
        print("project.pbxproj updated")
    else:
        print("no changes")


if __name__ == "__main__":
    main()
