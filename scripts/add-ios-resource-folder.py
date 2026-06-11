"""Add `Resources/seed-schedules-v2` as a folder reference to the iOS Xcode project.

Folder references (blue folders) preserve the subdirectory at runtime so
Bundle.main.url(forResource:..., subdirectory:"seed-schedules-v2") works.

Safe to re-run: skips if already added.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PBX = ROOT / "iosApp/Syrmos.xcodeproj/project.pbxproj"

FOLDER_PATH = "Resources/seed-schedules-v2"
BUILD_FILE_ID = "AABB01CD02EF030405060708"
FILE_REF_ID = "AABB01CD02EF030405060709"

src = PBX.read_text()

if FOLDER_PATH in src:
    print(f"already referenced: {FOLDER_PATH}")
    sys.exit(0)

# 1. PBXBuildFile entry
build_file_entry = (
    f"\t\t{BUILD_FILE_ID} /* seed-schedules-v2 in Resources */ = "
    f"{{isa = PBXBuildFile; fileRef = {FILE_REF_ID} /* seed-schedules-v2 */; }};\n"
)
src = re.sub(
    r"(/\* End PBXBuildFile section \*/)",
    build_file_entry + r"\1",
    src,
    count=1,
)

# 2. PBXFileReference entry
file_ref_entry = (
    f"\t\t{FILE_REF_ID} /* seed-schedules-v2 */ = "
    f"{{isa = PBXFileReference; lastKnownFileType = folder; "
    f"path = \"{FOLDER_PATH}\"; sourceTree = \"<group>\"; }};\n"
)
src = re.sub(
    r"(/\* End PBXFileReference section \*/)",
    file_ref_entry + r"\1",
    src,
    count=1,
)

# 3. Add to PBXResourcesBuildPhase
src, n = re.subn(
    r"(/* Begin PBXResourcesBuildPhase section \*/.*?files = \(\s*)",
    lambda m: m.group(1) + f"\t\t\t\t{BUILD_FILE_ID} /* seed-schedules-v2 in Resources */,\n",
    src,
    count=1,
    flags=re.DOTALL,
)
if n != 1:
    print("ERROR: could not locate PBXResourcesBuildPhase files list", file=sys.stderr)
    sys.exit(2)

# 4. Add to the iosApp group children. Find the group containing Assets.xcassets
# and add the new ref next to it.
src, n = re.subn(
    r"(E3F4A5B6C7D80912A3B4C5D6 /\* Assets\.xcassets \*/,)",
    lambda m: m.group(1) + f"\n\t\t\t\t{FILE_REF_ID} /* seed-schedules-v2 */,",
    src,
    count=1,
)
if n != 1:
    print("ERROR: could not find Assets.xcassets sibling slot", file=sys.stderr)
    sys.exit(3)

PBX.write_text(src)
print(f"added folder reference: {FOLDER_PATH}")
