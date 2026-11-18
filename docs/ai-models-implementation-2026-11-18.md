# AI models implementation — 2026-11-18

Status: code edited and source reviewed only. The user explicitly deferred builds, tests, live provider checks and device installation until after their own device review. No validation results are claimed.

## Implemented

- Dedicated AI models settings screen using existing compact settings surfaces and typography.
- Provider picker in a bottom sheet: Google Gemini, OpenAI, OpenRouter, Groq and a configurable OpenAI-compatible endpoint.
- Frameless protected-key field in a card, explicit Save, visibility toggle and provider-specific API-key link. Custom endpoints have an optional key-page URL instead of a fabricated setup link.
- Image and audio model sheets, separate task/provider bindings, search, explicit discovery Refresh and manual model ID entry. Refresh never reassigns a model. Advertised capabilities and unchecked support are distinguished.
- Existing Gemini key/model storage retained. Additional keys use the existing Keystore-backed credential store. Switching providers retains each provider's choices; one custom endpoint profile is currently supported.
- Gateway for Gemini native generation, compatible Chat Completions image/text operations, and compatible file transcription. OpenRouter audio upload is explicitly unavailable; use another provider for voice notes.
- Shared required title/extractedText/urls contract, translated to native Gemini schema or compatible strict JSON Schema. No raw-text fallback for invalid extraction results. No silent downgrade to JSON-object-only mode.
- Generated OCR title applied to the active capture, preserving user title edits and existing saved-item titles. Failed extraction preserves previous valid results. Stale image results cannot update a different capture.
- Advanced screen: additional instructions with explicit Save/default/Undo, inline text detail choices, and a detailed guide. Old prompts remain visible and are now subordinate to required extraction rules, disclosed in the editor. Text detail applies to both region and full-image extraction and no longer asks the model to invent blurry characters.
- Provider-aware recent usage with reported/unknown token counts. Earlier Gemini aggregate history remains separate and labelled as including estimates. Up to 200 recent responses are retained; this is not a billing statement.
- Existing usage navigation route retained; settings overview and capture loading labels renamed.

## Not exercised

Compilation, visual layout, accessibility interaction, real provider/model compatibility, recording-format acceptance and extraction accuracy. There are no automatic generation checks on opening settings, saving keys or loading models. Provider requests occur only through user-triggered discovery or capture operations. No tests were added or run in this implementation pass.

## Main sources

- `app/src/main/kotlin/com/example/ui/screens/AiModelsSettingsScreen.kt`
- `app/src/main/kotlin/com/example/ui/screens/AiAdvancedSettingsScreen.kt`
- `app/src/main/kotlin/com/example/data/remote/ai/AiProviderStore.kt`
- `app/src/main/kotlin/com/example/data/remote/ai/AiGateway.kt`
- `app/src/main/kotlin/com/example/data/repository/CobaltRepository.kt`
- `app/src/main/kotlin/com/example/ui/viewmodel/CobaltViewModel.kt`

Research and proposed later verification: `docs/ai-providers-and-prompt-settings-research-2026-11-18.md`. That research document describes the pre-implementation state and a broader validation plan; it is not evidence of runtime verification.
