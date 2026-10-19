# Build and test: exact commands and gotchas

The full local toolchain works on this Mac: JDK 17 + Android SDK, Xcode + iPhone
simulators, node v24, and direct `curl` to the live API. Do not reflexively label Android
or iOS "CI-only"; try the local run first.

## Environment setup (run once per shell)

```bash
# JDK 17 (there is no PATH `java` by default; /usr/libexec/java_home finds nothing)
export JAVA_HOME="$HOME/Library/Android/jdk/jdk-17.0.20.1+1/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
```

### The `local.properties` trap (Android "SDK location not found")
`local.properties` is gitignored and can carry a STALE `sdk.dir` from a prior session
pointing at a scratchpad path. AGP prefers `local.properties` `sdk.dir` over the env var,
so gradle fails even with `ANDROID_SDK_ROOT` set. Fix:

```bash
printf 'sdk.dir=%s/Library/Android/sdk\n' "$HOME" > local.properties
```

### Xcode path and license (iOS builds silently blocked)
`xcode-select -p` may point at `/Library/Developer/CommandLineTools` (xcodebuild refuses),
and Xcode may live outside `/Applications`. Find it and override per-process without sudo:

```bash
mdfind "kMDItemCFBundleIdentifier == 'com.apple.dt.Xcode'"   # locate it
export DEVELOPER_DIR=/Users/peterdsp/Downloads/Xcode.app/Contents/Developer
```

A freshly moved/updated Xcode can throw "You have not agreed to the Xcode license
agreements". That needs `sudo xcodebuild -license accept`, which only the user can run.
If the embedded Watch app fails with "watchOS must be installed", run once (no password):
`xcodebuild -downloadPlatform watchOS`.

## KMP / Android

Core modules (`:core:domain`, `:core:common`, `:core:model`) have **no JVM target**:
`jvmTest` and `test --tests` both fail. Run `commonTest` via the Android unit-test task,
which is the only one that accepts `--tests`:

```bash
./gradlew :core:domain:testDebugUnitTest --tests "com.syrmos.core.domain.journey.SomeTest"
# results XML: core/domain/build/test-results/testDebugUnitTest/*.xml
```

Compile checks:
```bash
./gradlew :composeApp:compileDebugKotlinAndroid     # Android app
./gradlew :composeApp:compileKotlinWasmJs            # web/wasm (CI gate), ~24s warm
./gradlew :androidApp:assembleDebug                  # full debug APK
```

Feature modules do have the Android unit-test task, e.g. `:feature:map:testDebugUnitTest`.
The KMP aggregate `test` / `allTests` does NOT accept `--tests`.

## iOS

Project: `iosApp/Syrmos.xcodeproj`, scheme `Syrmos - Athens Rail Times`. Pure Swift, no KMP
framework. Fast syntax-only check: `swiftc -parse <file>`.

```bash
# build to a booted simulator
xcodebuild build -scheme "Syrmos - Athens Rail Times" \
  -destination 'platform=iOS Simulator,name=iPhone 17' CODE_SIGNING_ALLOWED=NO

# run one test class (the test-host link is the slow part, minutes; log looks idle until done)
xcodebuild test -scheme "Syrmos - Athens Rail Times" \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -only-testing:iosAppTests/SomeTests
```

If building a bare target instead of the scheme (to skip the embedded Watch app): use
`CONFIGURATION_BUILD_DIR=<dir>` (not `-derivedDataPath`, which needs `-scheme`) and
`ARCHS=arm64` (the bundled `llama.xcframework` has no x86_64 slice, so the default link fails).

### Adding a new `.swift` file
`project.pbxproj` has no `PBXFileSystemSynchronizedRootGroup`; sources are referenced
explicitly, so a new file is NOT compiled until added. Two options:
1. **Zero-risk:** put the new code in an already-referenced file (new types or test cases
   in an existing file build and test clean with no pbxproj surgery).
2. **New file:** add 4 insertions (PBXBuildFile, PBXFileReference, group children, Sources
   phase). `xcodegen` is NOT installed; the pbxproj is the source of truth, `project.yml`
   is stale. Use an idempotent helper script like the existing
   `scripts/add-departure-grouping-files.py`. Hand-added files use the
   `FE0000000000000000000Cxx` UUID convention (current max noted in memory).

## Web

```bash
node --test web-tests/*.test.js     # GLOB, not the directory (directory mode fails)
node --check composeApp/src/wasmJsMain/resources/web-map.js   # fast syntax path
```
Web JS is UMD: `require()` in node, `window.Syrmos*` in the browser. Tests extract the
functions from the real files and harness them; mirror the existing `web-tests/*.test.js`.

## Server (Pi Python)

System python is 3.9 and has no `fastapi`. For local runs make a venv and
`pip install fastapi uvicorn python-multipart eval_type_backport` (the backport lets 3.9
eval the app's `str | None` annotations). CI does NOT run the `ops/syrmos-api` unittests or
`scripts/verify-bundles.py` (those run in `seed-refresh.yml`), so run server tests yourself.

## What CI gates (`.github/workflows/ci.yml`, on PRs to master)

KMP Android unit tests, web wasmJs compile (`web-tests` job runs `node --test`), Android
`assembleDebug`, and iOS build + tests. It does NOT run server Python tests or
`verify-bundles.py`. A server test failure will not block a PR, so it will not catch itself.
