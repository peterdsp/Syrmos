// Ariadne on-device LLM bridge for the web (Kotlin/wasmJs -> this -> wllama).
//
// Exposes window.AriadneLLM the Kotlin actual calls:
//   status()                  -> 'idle' | 'loading' | 'ready' | 'error'
//   download(assets, modelUrl)-> starts the one-time model download+load
//   classify(prompt)          -> JSON string, or '' when the model is not ready
//                                (so Kotlin falls back to the rule parser and
//                                never blocks a query).
//
// The model is ~1.1 GB and is downloaded ON DEMAND, only after the user opts in
// (Ariadne never auto-pulls that much data). wllama caches the weights in the
// browser (Cache Storage / OPFS), so once downloaded, later sessions load in
// ~2 s and run fully offline. Until then, the deterministic rule parser answers.
//
// The model itself is fetched from its pinned source URL (passed in from the
// Kotlin AriadneModelManifest); only the small wllama runtime (wasm + grammar)
// ships with the web app, keeping the first load lightweight.
//
// Grammar-constrained: the completion is locked to ariadne-grammar.gbnf, so the
// output is always the flat intent JSON that IntentGrounder.ground() parses; an
// invalid intent is impossible.

(() => {
  // Resolve the folder this script lives in (…/llm/) from its own URL, so the
  // wllama runtime + grammar always load relative to it regardless of how the
  // caller invokes download(). This is robust against a cached older caller.
  let SCRIPT_BASE = 'llm/';
  try {
    const self = document.currentScript && document.currentScript.src;
    if (self) SCRIPT_BASE = new URL('.', self).href;
  } catch (_) { /* keep the relative default */ }

  // Pinned model source (mirrors core/common AriadneModelManifest). The web app
  // fetches it on explicit opt-in; only the small runtime ships with the page.
  const DEFAULT_ASSETS = SCRIPT_BASE;
  const DEFAULT_MODEL_URL =
    'https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf';

  const S = { state: 'idle', wllama: null, grammar: '', assets: SCRIPT_BASE, progress: 0 };

  async function load(assets, modelUrl) {
    if (S.state === 'loading' || S.state === 'ready') return;
    assets = assets || SCRIPT_BASE;
    modelUrl = modelUrl || DEFAULT_MODEL_URL;
    S.state = 'loading';
    S.assets = assets;
    try {
      const { Wllama } = await import(assets + 'wllama/index.js');
      const wasm = assets + 'wllama/wllama.wasm';
      S.wllama = new Wllama({
        'default': wasm,
        'single-thread/wllama.wasm': wasm,
        'multi-thread/wllama.wasm': wasm,
      });
      S.grammar = await (await fetch(assets + 'ariadne-grammar.gbnf')).text();
      await S.wllama.loadModelFromUrl(modelUrl, {
        n_ctx: 1024,
        // Multi-thread needs cross-origin isolation (COOP/COEP). Fall back to a
        // single thread when the headers are absent; slower but still works.
        n_threads: self.crossOriginIsolated ? 4 : 1,
        // Report download progress (0..1) for the UI indicator.
        progressCallback: ({ loaded, total }) => {
          S.progress = total > 0 ? Math.min(1, loaded / total) : 0;
        },
      });
      S.progress = 1;
      S.state = 'ready';
    } catch (e) {
      console.warn('[AriadneLLM] load failed, staying on rule parser:', e);
      S.state = 'error';
    }
  }

  window.AriadneLLM = {
    status: () => S.state,
    // Download progress as 0..1 while state === 'loading'.
    progress: () => S.progress,
    // Explicit, user-triggered. Never called automatically. Args optional; the
    // web app can call download() with no args to use the pinned defaults.
    download: (assets, modelUrl) => { load(assets || DEFAULT_ASSETS, modelUrl || DEFAULT_MODEL_URL); },
    classify: async (prompt) => {
      if (S.state !== 'ready') return '';
      try {
        const res = await S.wllama.createCompletion({
          prompt,
          max_tokens: 160,
          temperature: 0.0,
          grammar: S.grammar,
        });
        return (typeof res === 'string') ? res : (res?.choices?.[0]?.text ?? '');
      } catch (e) {
        console.warn('[AriadneLLM] classify failed:', e);
        return '';
      }
    },
  };
})();
