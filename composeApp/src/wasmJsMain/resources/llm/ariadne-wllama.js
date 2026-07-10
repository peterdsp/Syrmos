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
  const S = { state: 'idle', wllama: null, grammar: '', assets: 'llm/' };

  async function load(assets, modelUrl) {
    if (S.state === 'loading' || S.state === 'ready') return;
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
      });
      S.state = 'ready';
    } catch (e) {
      console.warn('[AriadneLLM] load failed, staying on rule parser:', e);
      S.state = 'error';
    }
  }

  window.AriadneLLM = {
    status: () => S.state,
    // Explicit, user-triggered. Never called automatically.
    download: (assets, modelUrl) => { load(assets, modelUrl); },
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
