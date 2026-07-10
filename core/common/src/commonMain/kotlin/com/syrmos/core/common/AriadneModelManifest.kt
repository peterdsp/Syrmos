package com.syrmos.core.common

/**
 * The single source of truth for the on-device model that backs Ariadne's
 * "clever" tier on every platform. One GGUF file runs the same on iOS
 * (llama.cpp via SPM), Android (llama.cpp via JNI), and Web (llama.cpp via
 * wllama/WASM), so the model identity, size, and checksum live here once.
 *
 * The file is NOT committed to the repository (it is ~350 MB). CI downloads it
 * from [url], verifies it against [sha256], and packages it per platform:
 *  - iOS   : copied into the app bundle as a resource.
 *  - Android: staged into the install-time asset pack (Play Asset Delivery).
 *  - Web   : served as a static asset and cached in the browser after first load.
 *
 * The model only does the UNDERSTANDING step (message -> intent + quoted slots);
 * it never produces a transit fact. If it is missing or fails to load, Ariadne
 * falls back to the deterministic rule parser, so this manifest describes an
 * enhancement, never a hard dependency.
 */
object AriadneModelManifest {

    /** Filename as it ships on every platform. Keep in sync with CI + loaders. */
    const val FILE_NAME: String = "qwen2.5-0.5b-instruct-q4_k_m.gguf"

    /** Human-facing model name for the Settings engine readout. */
    const val DISPLAY_NAME: String = "Qwen2.5 0.5B (Q4)"

    /**
     * Pinned download source: the official Qwen GGUF repository (public, no
     * auth). CI is the only consumer; the app never fetches at runtime on mobile
     * (the file is bundled), and on Web the static asset is served from our own
     * origin, not this URL.
     */
    const val URL: String =
        "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"

    /**
     * SHA-256 of the exact file at [URL] (the HF LFS object id). CI must fail the
     * build if the download does not match, so a moved/retagged upstream artifact
     * can never slip an unverified binary into a release.
     */
    const val SHA256: String =
        "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"

    /**
     * Exact on-disk size in bytes, for progress UI and sanity checks. Larger than
     * a naive 0.5B estimate because Qwen2.5's 151k-token embedding matrix (kept
     * at higher precision) dominates the file at this size.
     */
    const val APPROX_BYTES: Long = 491_400_032L
}
