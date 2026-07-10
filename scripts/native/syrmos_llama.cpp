// JNI shim: the thin bridge between Kotlin (com.syrmos.llm.LlamaBridge) and
// llama.cpp. It exposes exactly what Ariadne's classifier needs: load a GGUF,
// run one grammar-constrained greedy completion, free. It never interprets the
// output; the Kotlin side grounds the JSON via IntentGrounder.
#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include "llama.h"

#define LOG_TAG "SyrmosLlama"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
struct Session {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
};

std::string jstr(JNIEnv *env, jstring s) {
    if (!s) return {};
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string out = c ? c : "";
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}
}  // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_syrmos_llm_LlamaBridge_nativeInit(JNIEnv *, jobject) {
    llama_backend_init();
}

JNIEXPORT jlong JNICALL
Java_com_syrmos_llm_LlamaBridge_nativeLoadModel(JNIEnv *env, jobject, jstring path, jint nCtx) {
    std::string modelPath = jstr(env, path);
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;  // CPU only on device
    llama_model *model = llama_model_load_from_file(modelPath.c_str(), mp);
    if (!model) { LOGE("load failed: %s", modelPath.c_str()); return 0; }

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = (uint32_t) (nCtx > 0 ? nCtx : 1024);
    cp.n_batch = cp.n_ctx;
    cp.no_perf = true;
    llama_context *ctx = llama_init_from_model(model, cp);
    if (!ctx) { LOGE("ctx failed"); llama_model_free(model); return 0; }

    auto *s = new Session{model, ctx};
    return reinterpret_cast<jlong>(s);
}

JNIEXPORT jstring JNICALL
Java_com_syrmos_llm_LlamaBridge_nativeComplete(JNIEnv *env, jobject, jlong handle,
                                               jstring promptJ, jint maxTokens, jstring grammarJ) {
    auto *s = reinterpret_cast<Session *>(handle);
    if (!s || !s->ctx) return env->NewStringUTF("");
    const llama_vocab *vocab = llama_model_get_vocab(s->model);
    std::string prompt = jstr(env, promptJ);
    std::string grammar = jstr(env, grammarJ);

    // Tokenize the prompt.
    int n_prompt = -llama_tokenize(vocab, prompt.c_str(), (int) prompt.size(),
                                   nullptr, 0, true, true);
    std::vector<llama_token> tokens(n_prompt);
    if (llama_tokenize(vocab, prompt.c_str(), (int) prompt.size(),
                       tokens.data(), (int) tokens.size(), true, true) < 0) {
        return env->NewStringUTF("");
    }

    // Sampler chain: grammar (if any) constrains to valid JSON, then greedy.
    llama_sampler *chain = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (!grammar.empty()) {
        llama_sampler *g = llama_sampler_init_grammar(vocab, grammar.c_str(), "root");
        if (g) llama_sampler_chain_add(chain, g);
    }
    llama_sampler_chain_add(chain, llama_sampler_init_greedy());

    std::string out;
    llama_batch batch = llama_batch_get_one(tokens.data(), (int) tokens.size());
    char piece[256];
    for (int i = 0; i < maxTokens; i++) {
        if (llama_decode(s->ctx, batch) != 0) break;
        // llama_sampler_sample applies the chain and internally accepts the
        // token, advancing the grammar state.
        llama_token id = llama_sampler_sample(chain, s->ctx, -1);
        if (llama_vocab_is_eog(vocab, id)) break;
        int n = llama_token_to_piece(vocab, id, piece, sizeof(piece), 0, false);
        if (n > 0) out.append(piece, n);
        batch = llama_batch_get_one(&id, 1);
    }

    llama_sampler_free(chain);
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT void JNICALL
Java_com_syrmos_llm_LlamaBridge_nativeFree(JNIEnv *, jobject, jlong handle) {
    auto *s = reinterpret_cast<Session *>(handle);
    if (!s) return;
    if (s->ctx) llama_free(s->ctx);
    if (s->model) llama_model_free(s->model);
    delete s;
}

}  // extern "C"
