# Vendored native framework (Ariadne on-device LLM, iOS)

`llama.xcframework` is a pinned prebuilt static-library xcframework of llama.cpp
(**tag `b4585`**), CPU-only, with `ios-arm64` (device) and `ios-arm64-simulator`
slices. `llama-headers/` holds the public C headers for the bridging header.
Built the same way as the Android libs (see
`composeApp/src/androidMain/jniLibs/README.md`), but with
`-DCMAKE_SYSTEM_NAME=iOS` per slice and combined via `xcodebuild
-create-xcframework`.

The Swift runtime is `iosApp/iosApp/Features/Assistant/LlamaSession.swift` (a
mirror of the Android JNI shim) and `AriadneModelStore.swift` (on-demand,
checksum-verified download of the ~1.1 GB model; never bundled).

## Remaining Xcode-project wiring (must be done in the .pbxproj, NOT via xcodegen)

The checked-in `Syrmos.xcodeproj` is authoritative (it carries the Watch/Widget
targets that `project.yml` does not), so **do not run `xcodegen`** — it would wipe
them. Wire these by hand / with the existing `scripts/add-ios-*.py` helpers:

1. Add `LlamaSession.swift`, `AriadneModelStore.swift` (and any new grounding
   file) to the `iosApp` target's Compile Sources.
2. Link `Frameworks/llama.xcframework` into the `iosApp` target (Link Binary With
   Libraries; do not embed — it is a static lib).
3. Build settings on the `iosApp` target:
   - `SWIFT_OBJC_BRIDGING_HEADER = iosApp/Syrmos-Bridging-Header.h`
   - `HEADER_SEARCH_PATHS = $(SRCROOT)/Frameworks/llama-headers`
   - `OTHER_LDFLAGS = -lc++` (llama.cpp is C++; the app must link libc++)
4. Flip `AriadneGuided.classify(...)` to run `LlamaSession.shared` with
   `AriadneModelManifest`'s prompt + `AriadneGrammar.GBNF`, grounding the JSON the
   same way the Kotlin `IntentGrounder.ground` does. Add an in-app "Download
   Ariadne's brain" control bound to `AriadneModelStore`.
5. Build + run on a device/simulator to verify inference. The rule parser is the
   floor until then, so nothing regresses in the meantime.
