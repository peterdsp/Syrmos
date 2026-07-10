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

## How they were built

```sh
# 1. llama.cpp shared libs (per ABI)
cmake -S llama.cpp -B build-arm64 \
  -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
  -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON \
  -DGGML_OPENMP=OFF -DLLAMA_CURL=OFF \
  -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_SERVER=OFF
cmake --build build-arm64 --target llama -j

# 2. JNI shim, linked against the llama.cpp libs
$NDK/.../aarch64-linux-android26-clang++ -std=c++17 -O2 -shared -fPIC \
  scripts/native/syrmos_llama.cpp -o libsyrmos_llama.so \
  -I llama.cpp/include -I llama.cpp/ggml/include \
  -L build-arm64/bin -lllama -lggml -lggml-base -lggml-cpu -llog

# 3. strip all, copy into arm64-v8a/
$NDK/.../llvm-strip *.so
```

The model itself (Qwen2.5-1.5B GGUF) is NOT here: it is downloaded on demand at
runtime (`AriadneModelStore`) and verified against `AriadneModelManifest.SHA256`.
