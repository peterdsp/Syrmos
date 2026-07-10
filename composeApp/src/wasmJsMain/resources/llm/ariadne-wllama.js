// Ariadne on-device LLM bridge for the web (Kotlin/wasmJs -> this -> wllama).
//
// Exposes window.AriadneLLM with two methods the Kotlin actual calls:
//   status()                     -> 'idle' | 'loading' | 'ready' | 'error'
//   classify(base, model, prompt)-> JSON string, or '' when the model is not
//                                   ready yet (so Kotlin falls back to the rule
//                                   parser instantly and never blocks a query).
//
// The model is large (~1.1 GB), so the first classify() call only KICKS OFF the
// download+load and returns '' immediately; wllama caches the weights in the
// browser (Cache Storage / OPFS), so subsequent sessions load in ~2 s and later
// queries run the model. The rule parser answers while the model wakes up.
//
// Grammar-constrained: the completion is locked to ariadne-grammar.gbnf, so the
// output is always the flat intent JSON that IntentGrounder.ground() parses; an
// invalid intent is impossible.

(() => {
  const S = { state: 'idle', wllama: null, grammar: '' };

  async function load(base, model) {
    S.state = 'loading';
    try {
      const { Wllama } = await import(base + 'wllama/index.js');
      const wasm = base + 'wllama/wllama.wasm';
      S.wllama = new Wllama({
        'default': wasm,
        'single-thread/wllama.wasm': wasm,
        'multi-thread/wllama.wasm': wasm,
      });
      S.grammar = await (await fetch(base + 'ariadne-grammar.gbnf')).text();
      await S.wllama.loadModelFromUrl(base + model, {
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
    classify: async (base, model, prompt) => {
      if (S.state === 'idle') { load(base, model); return ''; }
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
