# Vendored native libraries (Ariadne on-device LLM)

These are pinned prebuilt shared libraries for the Android "clever" tier: a
llama.cpp runtime plus a thin JNI shim. They are committed on purpose (the
"vendor pinned prebuilt" decision) so the app build needs no NDK/CMake.

- `libllama.so`, `libggml*.so` — built from **llama.cpp tag `b4585`**
  (github.com/ggml-org/llama.cpp), stripped.
- `libsyrmos_llama.so` — the JNI shim in `scripts/native/syrmos_llama.cpp`,
  symbol namespace `Java_com_syrmos_llm_LlamaBridge_*`.

Currently `arm64-v8a` only (all shipping Android devices). To add `x86_64` for
the emulator, rebuild for that ABI and drop the `.so` set under `x86_64/`.

## C++ runtime: link the shim statically (required)

`libsyrmos_llama.so` MUST be linked with `-static-libstdc++`. The NDK's clang++
defaults to the *shared* runtime (`libc++_shared.so`), which this app does not
package — so a shim built without the flag fails at `dlopen` with
`library "libc++_shared.so" not found`, `LlamaBridge.available` goes false, and
Ariadne silently drops to the rule parser with no crash and no visible error.
That is exactly how this shipped broken once; do not regress it.

llama.cpp's own libs already static-link libc++ (their NEEDED lists no
`libc++_shared.so`), so the shim matches them. The shim only calls llama.cpp's
`extern "C"` API, so a private static libc++ is safe.

Verify before committing:

```sh
$NDK/.../llvm-readelf -d arm64-v8a/libsyrmos_llama.so | grep NEEDED
# must NOT list libc++_shared.so
```

## 16 KB page size (required)

Every `.so` here MUST be linked with 16 KB max page size. Google Play rejects a
release whose native libs are 4 KB-aligned ("Your app does not support 16 KB
memory page sizes"), and on a 16 KB-page device 4 KB-aligned libs fail to load
(`LlamaBridge` then reports `available=false` and Ariadne silently drops to the
rule parser). llama.cpp's own CMake does not set this, so pass it explicitly —
relying on the NDK default is not enough.

Verify before committing: each LOAD segment must report align `0x4000`.

```sh
$NDK/.../llvm-readelf -l arm64-v8a/libllama.so | grep LOAD   # want 0x4000, not 0x1000
```

## How they were built

```sh
ALIGN16='-Wl,-z,max-page-size=16384'

# 1. llama.cpp shared libs (per ABI)
cmake -S llama.cpp -B build-arm64 \
  -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
  -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON \
  -DGGML_OPENMP=OFF -DLLAMA_CURL=OFF \
  -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_SERVER=OFF \
  -DCMAKE_SHARED_LINKER_FLAGS="$ALIGN16"
cmake --build build-arm64 --target llama -j

# 2. JNI shim, linked against the llama.cpp libs
$NDK/.../aarch64-linux-android26-clang++ -std=c++17 -O2 -shared -fPIC \
  scripts/native/syrmos_llama.cpp -o libsyrmos_llama.so \
  -I llama.cpp/include -I llama.cpp/ggml/include \
  -L build-arm64/bin -lllama -lggml -lggml-base -lggml-cpu -llog \
  -static-libstdc++ $ALIGN16

# 3. strip all, copy into arm64-v8a/
$NDK/.../llvm-strip *.so
```

The model itself (Qwen2.5-1.5B GGUF) is NOT here: it is downloaded on demand at
runtime (`AriadneModelStore`) and verified against `AriadneModelManifest.SHA256`.
