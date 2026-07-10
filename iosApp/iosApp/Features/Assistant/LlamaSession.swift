import Foundation

// Ariadne's on-device model runtime on iOS: a thin Swift wrapper over the pinned
// llama.cpp C API (bridged via Syrmos-Bridging-Header.h -> llama.h, static libs
// in Frameworks/llama.xcframework). It does the UNDERSTANDING step only: one
// grammar-constrained greedy completion that emits the intent JSON. AriadneGuided
// grounds that JSON to a canonical intent; the model never supplies an id or a
// fact. Mirrors the Android JNI shim (scripts/native/syrmos_llama.cpp).
//
// The model (~1.1 GB GGUF) is downloaded on demand (AriadneModelStore), never
// bundled. Until it is present + loaded, callers fall back to the rule parser.
actor LlamaSession {
    static let shared = LlamaSession()

    private var model: OpaquePointer?
    private var ctx: OpaquePointer?
    private var backendReady = false

    /// True once a model is loaded and ready to run completions.
    var isReady: Bool { model != nil && ctx != nil }

    /// Load the GGUF at [path] (idempotent). Returns true when ready.
    @discardableResult
    func load(path: String, nCtx: Int32 = 1024) -> Bool {
        if isReady { return true }
        if !backendReady { llama_backend_init(); backendReady = true }

        var mparams = llama_model_default_params()
        mparams.n_gpu_layers = 0
        guard let m = llama_model_load_from_file(path, mparams) else { return false }

        var cparams = llama_context_default_params()
        cparams.n_ctx = UInt32(nCtx)
        cparams.n_batch = UInt32(nCtx)
        cparams.no_perf = true
        guard let c = llama_init_from_model(m, cparams) else {
            llama_model_free(m); return false
        }
        model = m; ctx = c
        return true
    }

    /// Run one grammar-constrained greedy completion. Returns "" on any failure.
    func complete(prompt: String, maxTokens: Int32, grammar: String) -> String {
        guard let ctx, let model else { return "" }
        let vocab = llama_model_get_vocab(model)

        // Tokenize.
        let promptC = Array(prompt.utf8CString)
        let nPrompt = -llama_tokenize(vocab, promptC, Int32(promptC.count - 1), nil, 0, true, true)
        var tokens = [llama_token](repeating: 0, count: Int(nPrompt))
        let got = tokens.withUnsafeMutableBufferPointer {
            llama_tokenize(vocab, promptC, Int32(promptC.count - 1), $0.baseAddress, Int32($0.count), true, true)
        }
        if got < 0 { return "" }

        // Sampler chain: grammar (constrains to valid JSON) then greedy.
        let chain = llama_sampler_chain_init(llama_sampler_chain_default_params())
        grammar.withCString { g in
            "root".withCString { root in
                if let gs = llama_sampler_init_grammar(vocab, g, root) {
                    llama_sampler_chain_add(chain, gs)
                }
            }
        }
        llama_sampler_chain_add(chain, llama_sampler_init_greedy())
        defer { llama_sampler_free(chain) }

        var out = ""
        var piece = [CChar](repeating: 0, count: 256)
        var cur = tokens
        var produced: Int32 = 0
        while produced < maxTokens {
            let ok = cur.withUnsafeMutableBufferPointer { buf -> Bool in
                let batch = llama_batch_get_one(buf.baseAddress, Int32(buf.count))
                return llama_decode(ctx, batch) == 0
            }
            if !ok { break }
            let id = llama_sampler_sample(chain, ctx, -1)   // applies + accepts (advances grammar)
            if llama_vocab_is_eog(vocab, id) { break }
            let n = llama_token_to_piece(vocab, id, &piece, Int32(piece.count), 0, false)
            if n > 0 {
                out += String(decoding: piece[0..<Int(n)].map { UInt8(bitPattern: $0) }, as: UTF8.self)
            }
            cur = [id]
            produced += 1
        }
        return out
    }

    func unload() {
        if let ctx { llama_free(ctx) }
        if let model { llama_model_free(model) }
        ctx = nil; model = nil
    }
}
