# Ariadne Platform Notes

Generated: 2026-07-04T15:27:19.166206+00:00

## Apple Foundation Models

Apple's Foundation Models framework is the cleanest iOS path for selected Apple Intelligence devices. Use it for guided generation and tool calling, not for storing schedule facts. The current iOS code already imports FoundationModels behind availability checks and uses it only for normalization. The next step is to register Ariadne tools that call existing Swift code.

Primary links:

- [Foundation Models](https://developer.apple.com/documentation/foundationmodels)
- [Expanding generation with tool calling](https://developer.apple.com/documentation/foundationmodels/expanding-generation-with-tool-calling)
- [Tool](https://developer.apple.com/documentation/foundationmodels/tool)
- [Meet the Foundation Models framework](https://developer.apple.com/videos/play/wwdc2025/286/)
- [Deep dive into the Foundation Models framework](https://developer.apple.com/videos/play/wwdc2025/301/)

## Android Gemini Nano

For selected Android devices, use Gemini Nano through the Android on-device AI stack. The practical Ariadne path is structured JSON intent output first, then local deterministic tools. If the selected runtime supports local function calling, wire the same tool contracts.

Primary links:

- [Gemini Nano on Android](https://developer.android.com/ai/gemini-nano)
- [ML Kit GenAI APIs overview](https://developers.google.com/ml-kit/genai)
- [ML Kit GenAI Prompt API for Android](https://developers.google.com/ml-kit/genai/prompt/android)
- [ML Kit Prompt API blog](https://developer.android.com/blog/posts/ml-kit-s-prompt-api-unlock-custom-on-device-gemini-nano-experiences)
- [Google AI Edge Function Calling for Android](https://developers.google.com/edge/mediapipe/solutions/genai/function_calling/android)

## Samsung-Class Devices

Treat Samsung phones as Android devices unless the project later adopts a public Samsung-specific app assistant API. Samsung documents on-device AI and lower-level model execution/toolchain options, but this pack does not assume Galaxy AI can directly read Ariadne files or call app tools.

Primary links:

- [Samsung Neural SDK](https://developer.samsung.com/neural/overview.html)
- [Samsung Exynos AI Studio toolchain](https://semiconductor.samsung.com/news-events/tech-blog/unpacking-samsungs-comprehensive-on-device-ai-sdk-toolchain-strategy/)
- [Samsung on-device AI technology](https://semiconductor.samsung.com/technologies/processor/on-device-ai/)

## Implementation Decision

The same data pack can serve all platforms because the model-facing contract is platform-neutral:

- `ariadne_intent_schema.json` for guided classification.
- `ariadne_tool_contracts.json` for deterministic facts and actions.
- `ariadne_rag_chunks.jsonl` for retrieved explanations.
- `ariadne_llm_context_pack.json` for full offline analysis and regeneration.
