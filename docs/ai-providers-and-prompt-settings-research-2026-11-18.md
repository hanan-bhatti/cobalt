# AI models: provider support, structured extraction and settings plan

Research date: 2026-11-18. Status: source inspection and documentation research; proposed implementation, not shipped provider support. No builds, tests, paid requests or requests using account keys were run. The Reminders changes are outside this plan.

## Recommendation

Rename the settings destination **AI models**, subtitle **Providers, text capture and voice notes**. Keep Gemini working through its native API. Add a separate OpenAI-compatible Chat Completions adapter, starting with image text extraction and text formatting. Treat audio transcription as its own capability and endpoint; do not assume chat compatibility covers recordings.

Use one Cobalt extraction result contract across adapters. The follow-up decision below supersedes the original two-field proposal: include `title` as a third required field. Custom instructions can affect extraction preferences, but cannot change the required response fields. Implement parsing and error handling before exposing another provider.

## What the current code actually does

Paths below are relative to the repository root.

| Owning source | Current behavior | Consequence |
| --- | --- | --- |
| `app/src/main/kotlin/com/example/data/remote/GeminiApi.kt` | Retrofit URL is fixed to Google's endpoint. Model list uses `v1beta/models`; generation uses `v1beta/{model}:generateContent`. Authentication is a `key` query parameter. | A different base URL alone cannot translate the requests. |
| Same file | Requests use `contents[].parts`, inline image/audio data, and Gemini `generationConfig`. The schema DTO supports uppercase types and `propertyOrdering`, but no `additionalProperties`. | A standard JSON Schema representation/serializer is needed for compatible providers. |
| `app/src/main/kotlin/com/example/data/repository/CobaltRepository.kt`, `buildOcrResponseSchema` | Requires `extractedText: string` and `urls: array`, with `url` and `description` strings on each URL item. | Preserve these field meanings to retain link review behavior. |
| Same file, `extractTextFromRegion` | Crops the bitmap, JPEG encodes at quality 80, sends image plus instructions, requests JSON and the schema, temperature 0.1. | Image preprocessing can be shared; request encoding must be adapter-specific. |
| Same method | Nonblank custom prompt REPLACES `DEFAULT_OCR_PROMPT`. Sensitivity text is then appended. `systemInstruction` exists in the DTO but is not used here. | The screen's “System Prompt” label is misleading. A user can remove the normal extraction rules, though the API schema remains attached. |
| Same method | “High” explicitly asks the model to infer missing characters/fix blurry text. | It is an instruction preset, not a measured OCR confidence/detection threshold. Current copy overstates what it controls. |
| `app/src/main/kotlin/com/example/ui/viewmodel/CobaltViewModel.kt`, `performFullImageOcr` | Full-image extraction hardcodes High; region extraction reads the sensitivity preference. | The existing setting does not apply equally to both paths. Decide a consistent behavior and test it. |
| Same file, `parseGeminiOcrResult` | Removes code fences, uses permissive `JSONObject` access, falls back to raw output on parsing failure. | Invalid output can appear as captured text, or partial extraction can be accepted. |
| Same file, OCR operations | Clears prior extraction/link review before the new request completes. | A failed retry should preserve the last valid result instead. |
| Same file, `fetchAvailableModels` | Filters names containing “gemini” and excludes name patterns; audio support is also inferred from names. Can auto-select another model after refresh. | Model naming is not a reliable cross-provider capability contract. Refresh should not silently reassign tasks. |
| Repository, `extractTextFromAudio` | Sends inline `audio/mp4`; asks for an H1 and organized Markdown notes. Some model names omit the text prompt. | This is not strictly verbatim transcription. Generic compatible audio requires a dedicated request path. |
| Repository, `formatSpeechText` | Separate text-to-Markdown operation, using the image/text model selection. Its usage is recorded as OCR. | Distinguish text formatting from image extraction in task routing and usage. |
| `app/src/main/kotlin/com/example/data/repository/SettingsRepository.kt` | One Gemini key, image/text model, audio model, global custom OCR prompt and sensitivity. | Need provider connection records and per-task model bindings. |
| `app/src/main/kotlin/com/example/util/SecretPreferences.kt` | Keystore-backed AES-GCM credential storage already exists. | Reuse this for each provider's key; do not store keys inside ordinary provider JSON. |
| `app/src/main/kotlin/com/example/util/GeminiUsageTracker.kt` and repository | Tracks Gemini-oriented totals. Missing token metadata gets guessed; speech formatting counts as OCR. | Keep reported usage separate from estimates; legacy totals cannot be reconstructed into exact provider/task history. |
| `app/src/main/kotlin/com/example/ui/screens/ProfileSettings.kt` | Large INTELLIGENCE branch includes credentials, image model, immediately persisted prompt, sensitivity, audio model and usage. | Extract a dedicated settings screen; use draft edits and explicit Save for connection/prompt forms. |
| `app/src/main/kotlin/com/example/ui/screens/ProfileMainContent.kt` | Overview uses Gemini wording and treats nonblank key as setup status. | Rename overview too; key saved is not proof a model works. |

## What compatibility does and does not establish

OpenAI-compatible describes a wire protocol, not a universal feature set. A valid model-list request proves neither image understanding nor schema support. Check their combination for the selected model and endpoint.

| Target | Practical approach | Qualification |
| --- | --- | --- |
| Gemini | Retain existing native adapter and migrate result parsing behind a shared boundary. | Google also has an OpenAI compatibility endpoint, but keeping native avoids changing working audio and extraction transport at the same time. [Google compatibility docs](https://ai.google.dev/gemini-api/docs/openai) |
| OpenAI | Compatible adapter for Chat Completions, image-capable model and JSON Schema response format. | Check the chosen model and strict schema requirements. Responses API has a different envelope and should be a distinct transport if added. [Structured outputs](https://platform.openai.com/docs/guides/structured-outputs), [image input](https://platform.openai.com/docs/guides/images-vision) |
| OpenRouter | Compatible adapter, with model/route capability checks. | Require the needed parameters on routing; its documentation recommends `require_parameters: true` for structured output support. This belongs in an OpenRouter preset, not arbitrary request parameters exposed to everyone. [OpenRouter structured outputs](https://openrouter.ai/docs/guides/features/structured-outputs) |
| Groq or other compatible hosts | Reuse protocol adapter with a provider profile where needed. | Compatibility has exceptions; evaluate the actual model's image plus schema support. No blanket claim that all models qualify. [Groq compatibility](https://console.groq.com/docs/openai), [structured output](https://console.groq.com/docs/structured-outputs) |
| Local Ollama | Compatible endpoint plus a suitable vision model and response-format support. | Ollama supports only a subset of the OpenAI API. Its cloud currently does not support structured outputs, so local and cloud cannot share an unqualified capability badge. [Compatibility](https://docs.ollama.com/api/openai-compatibility), [structured outputs](https://docs.ollama.com/capabilities/structured-outputs) |

These are integration candidates based on documentation, not verified account/model combinations. Do not hardcode today's popular model names as permanent recommendations.

Audio is separate: transcription can use `/audio/transcriptions` with file upload, whereas other models accept audio through multimodal generation. OpenAI documents distinct audio/transcription APIs. Handle the actual recorded container and accepted formats explicitly; do not relabel MP4 bytes as WAV to satisfy an endpoint. A provider's audio support does not imply that Cobalt's current upload format is accepted. [OpenAI audio guide](https://platform.openai.com/docs/guides/audio)

## Preserve structured extraction

Shared domain types should resemble:

```kotlin
data class ExtractionResult(val extractedText: String, val urls: List<ExtractedUrl>)
data class ExtractedUrl(val url: String, val description: String)
```

Use required non-null fields in transport validation; no default empty list that hides a missing `urls` key. Explicit empty results are `{"extractedText":"","urls":[]}`.

Standard JSON Schema for the compatible adapter:

```json
{
  "type": "object",
  "properties": {
    "extractedText": { "type": "string" },
    "urls": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "url": { "type": "string" },
          "description": { "type": "string" }
        },
        "required": ["url", "description"],
        "additionalProperties": false
      }
    }
  },
  "required": ["extractedText", "urls"],
  "additionalProperties": false
}
```

Chat Completions wraps this under `response_format: { type: "json_schema", json_schema: { name: "cobalt_extraction", strict: true, schema: ... } }`. Responses uses `text.format`; do not mix the two formats. OpenAI strict schemas require all fields to be required and `additionalProperties: false` on objects. Keep Gemini's uppercase types/property ordering in its own adapter; don't send those fields to unrelated hosts. [OpenAI structured outputs](https://platform.openai.com/docs/guides/structured-outputs)

Recommended capability policy:

1. Strict schema supported: request it and still validate locally.
2. JSON-object mode only: label it as weaker validation in connection/model details. Require the exact same result shape locally; never claim provider-enforced schema guarantees. Keep this opt-in and secondary to strict support.
3. Neither supported: do not offer that model for structured image capture in the first release. It may still qualify for a text-only task.

Response processing order: HTTP error → refusal/block status → completion/truncation status → text-part collection → JSON decoding → exact required field/type validation → semantic checks → publish the result. Reject partial responses and JSON of the wrong shape. A refused request is not a blank successful extraction. A missing usage field is unknown, not zero tokens.

Check URL schemes/lengths and keep extracted links staged for review. JSON validation cannot establish that a URL was really visible or transcribed accurately. Models can make semantic mistakes even with a valid schema; Google's documentation explicitly calls for application validation. Do not fetch extracted URLs during provider capability tests. [Google structured output best practices](https://ai.google.dev/gemini-api/docs/structured-output)

Use typed failures such as `Authentication`, `RateLimited`, `UnsupportedCapability`, `Blocked`, `Truncated`, `InvalidResponse`, `Network`, and `NoReadableContent`. Avoid returning strings prefixed with “Error” through the successful text channel. Preserve coroutine cancellation and existing valid capture content on failure. Avoid automatic paid retry loops and silent switching to another provider.

## Proposed internal boundary

Keep the existing Retrofit/OkHttp/Moshi stack; this Android app does not need a web AI SDK or a server framework to add user-supplied provider credentials.

```text
Settings / capture ViewModel
        |
AiTaskService (selects saved connection and model per task)
        |
        +-- GeminiNativeAdapter
        +-- OpenAiCompatibleAdapter
        |
Shared extraction decoder + validation
        |
ExtractionResult / typed failure / normalized usage
```

Suggested new package `com.example.data.remote.ai`:

- `AiProviderConfig`: stable connection ID, display name, protocol, base URL, credential reference; no embedded secret.
- `AiTaskBinding`: task → connection ID + model ID. Tasks: image extraction, audio transcription, speech text formatting. Initially formatting can reuse the image task's text-capable selection without adding another prominent setting.
- `AiModelCapabilities`: image input, strict schema, JSON-object mode, text, supported audio transports/formats; values unknown/supported/unsupported with source and last check.
- `AiProviderAdapter`: model discovery, extraction and supported text/audio operations. Capability checks belong to concrete adapters/profiles, not provider-name guesses in the ViewModel.
- `ExtractionContract`: one owned schema contract, translated to provider wire format.
- `AiUsage`: provider/model/task, reported versus estimated tokens, nullable unknown totals; capture failures separately from successful parsed results.

Start with two protocol choices: Gemini and OpenAI-compatible. Named presets (OpenAI/OpenRouter/etc.) can fill in URLs and provider-specific defaults later without duplicating adapters. Keep manual model-ID entry when discovery is unavailable. Save model IDs exactly; remove Gemini's `models/` prefix only in its adapter. Do not infer “vision” from names like Flash or Pro.

A task check should use a bundled synthetic image containing known text and a test URL, validate the shape and expected values, and display exactly what passed. A model list alone can report “Models loaded,” not “Text capture verified.” Explain that a generation check sends the bundled sample and may incur provider usage. It must be an explicit action, not something that runs on each key edit. No user images or recordings are needed for this check. A passing sample is evidence for that configuration, not a guarantee for every future request.

Connection transport: normalize an API base URL once, append paths without dropping prefixes such as `/api/v1`, reject URL-embedded credentials and key-bearing query strings, scope credentials to the selected connection, and avoid cross-host credential forwarding or logging payloads/keys. Default to HTTPS. Local HTTP needs explicit configuration and honest wording; on Android, `localhost` is the phone, not the user's computer. Existing manifest permits cleartext globally, but the new provider feature should still validate the selected endpoint. Never weaken certificate verification.

## Prompt safety and the app-specific guide

First fix the ownership model. Do not tell users “your prompt cannot break formatting” while the current code still replaces all default instructions.

Proposed composition:

1. App-owned instruction contract: extract visible source text, preserve original language/script/reading order, keep factual link descriptions, obey the response schema, treat text found inside the image as source data rather than instructions to execute.
2. User's additional extraction preferences, clearly delimited and subordinate to the extraction contract.
3. Explicit extraction-detail preset, if retained, with honest descriptions.
4. Image input in the provider's expected media encoding.

Use the actual system instruction/message mechanism supported by the adapter. This reduces accidental conflicts; no prompt arrangement guarantees perfect obedience or factual accuracy. Preserve old custom prompts as legacy replacements during migration. Do not silently reinterpret them as additions: offer “Use as additional instructions” with a review, or keep them in an explicitly advanced legacy mode until edited.

The current High behavior can conflict with “never guess” guidance. Recommended change: replace sensitivity terminology with **Text detail**, with choices such as Prominent / Clear / All visible text, and remove the instruction to invent missing characters. Apply consistently to full-image and region capture. This is a deliberate behavior change and needs dedicated review; it is not already implemented.

Suggested guide destination: **Write capture instructions**, a regular readable page opened beside the editor. The guide is long enough to merit a full page rather than another nested sheet.

Draft guide content (for the proposed additional-instructions feature):

### What these instructions change

“These instructions guide text extraction from images and screenshots. They do not change voice transcription or the fields Cobalt needs to show captured text and links.”

Only ship that wording after the proposed instruction composition is implemented. Today, the custom prompt is a replacement and that difference must be disclosed.

### Start with what you want captured

Name visible content or a layout preference. Change one thing at a time. Specific, consistent directions are easier to evaluate than “make it better.” Google recommends clear instructions and iterative prompt refinement. [Prompt design strategies](https://ai.google.dev/gemini-api/docs/prompting-strategies)

Useful examples:

- “For two-column pages, read down the left column before the right column.”
- “Preserve Urdu and Arabic in their original script. Do not translate or transliterate.”
- “Keep visible headings, numbered steps and bullet points in Markdown.”
- “Do not guess unclear characters. Use [unclear] in the captured text where needed.”

These are directions to the model, not guaranteed results. Do not imply the image processor detects columns or uncertainty deterministically.

### Keep capture separate from rewriting

“Extraction should preserve the source. Requests to summarize, translate, fix facts or add missing steps can change what was captured. Use a separate rewriting action if you need a different version.”

Do not link to a rewriting feature unless a suitable user-facing action actually exists. The repository has speech formatting, which is not a general image rewriting tool.

### Keep links accurate

“Ask for visible addresses exactly as shown. Do not ask the model to guess a website or repair a blurry address. Review extracted links before saving them.”

The existing rule prepends `https://` when the source omits a scheme; document that exception rather than claiming returned URLs are always literally identical to the image. Keep URL descriptions based only on visible context. An unclear URL should remain for manual review rather than becoming an invented link with “[unclear]” inserted into its address.

### Let Cobalt handle the output format

“Write instructions in plain language. You do not need JSON, field names or code fences. Cobalt handles the response format.”

Avoid examples demanding a new top-level JSON shape, prose-only output, invented fields or instructions to ignore app rules. Do not reject prompts just because they contain the word “JSON”; warn about likely conflicts without claiming an automatic lint can prove a prompt safe.

### Try a small comparison

“Use a screenshot you know well. Compare its words, reading order and links with the result. Try an Urdu/Arabic example if you capture those scripts. Change one instruction, then compare again.”

An explicit **Try instructions** action can preview a result using a user-selected image without overwriting saved content. Tell the user which provider receives it. Treat this as an optional follow-up feature if it expands the first release too much.

### Restore the default

“Clear additional instructions to use Cobalt’s standard extraction rules.” Keep a draft and an explicit Save action. Restoring a prompt is reversible; provide Undo rather than a five-second destructive hold.

Suggested starter text:

```text
Preserve the original language and reading order.
Keep headings, lists and paragraph breaks visible in the image.
Do not summarize, translate or add information.
Mark unclear text as [unclear] instead of guessing.
Copy visible web addresses carefully; do not invent or repair an address.
```

## Page hierarchy without overdesign

Keep the compact groups, spacing and typography used for Dock/Appearance/Reminders. No dashboard, decorative metrics, or sheet for every toggle.

1. **Providers** — compact saved-connection rows showing name and status; Add provider action. A normal provider detail screen contains type, name, endpoint when applicable, protected key, Save, Load models and an explicit task check. Distinguish “Key saved,” “Models loaded,” and “Text capture checked.” Do not label a saved key “Connected.”
2. **Models** — Text capture and Voice notes rows. Each shows provider plus model and an honest unavailable/unchecked state. Selection uses existing searchable picker patterns. Keep audio on Gemini if another connection only supports image extraction.
3. **Text capture** — Text detail choices inline, Additional instructions preview/editor, and Write capture instructions link. Editor is a normal page with Save and restore/Undo; lengthy help is a normal guide page. Keep contract/schema mechanics out of the main user flow.
4. **Usage** — one navigation row to provider-aware task usage. Label estimates and unknowns. This is not a billing statement.

Overview title: **AI models**. Subtitle: **Providers, text capture and voice notes**. Empty state: **Add a provider to use AI text capture and voice notes.** Avoid saying every provider supports both tasks. Connection setup should explain that selected images/audio are sent to that provider, and capability verification should not use personal capture content.

## Implementation order

1. Introduce provider/task/result/usage types and strict extraction decoding behind the current Gemini path. Keep existing behavior working except deliberate invalid-response handling; preserve last good extraction on failure.
2. Add connection persistence and migrate the existing Gemini key/model selections to a stable Gemini connection without logging/exposing credentials. Preserve usage history and custom prompt legacy semantics.
3. Implement compatible Chat Completions image extraction: bearer authentication, text plus image data URL, JSON Schema format, errors/refusals/finish status, response text and usage. Keep model discovery optional. Add provider-specific routing profiles only when required.
4. Add the task verification flow and distinguish unknown versus verified capabilities. Do not silently downgrade schema or automatically send data to a different connection.
5. Extract/rename the settings screen and overview; implement the four groups, explicit draft/save forms, prompt ownership and detailed guide.
6. Add compatible audio upload adapters only for documented supported endpoint/model/recording combinations. Normalize to the existing note format or define a separate transcript/formatting step clearly; do not assume a transcription endpoint returns Markdown notes.

Validation plan for implementation (not executed during this research):

- Golden payload tests for Gemini/native and compatible formats; equivalent image extraction results reach both full-image and region flows.
- Strict decode cases: valid/no-text/no-links, missing/wrong-type fields, unknown fields, fenced JSON legacy normalization, malformed/truncated JSON, refusal and safety block. No accidental raw JSON capture.
- Urdu/Arabic direction, mixed scripts, multiple columns, punctuation-heavy links, blur, and instructions embedded in screenshots. Human review for accuracy; schema success alone is insufficient.
- Endpoint path joining, bearer scoping, no key logging, auth/rate-limit/timeouts, cancellation, manual model IDs and unsupported parameter errors. No unrestricted HTTP/certificate bypass.
- Keep previous valid extraction on failure; link review remains explicit; migration is repeatable and survives restart; a failed secret write retains old credentials; prompt editing/cancel/restore behavior is predictable.
- Usage actual/estimated/unknown and OCR/audio/formatting classification. Preserve legacy totals without inventing per-provider history.
- UI: compact phone, large text, keyboard and screen reader, readable guide, Save and Back behavior. Live provider checks require configured test accounts and must report precisely which provider/model combination was exercised.

## Decisions to carry into implementation

Recommended first increment: Gemini native + configurable OpenAI-compatible image extraction, with separate audio routing kept on Gemini. Require strict schema support by default, keep JSON-object-only support explicit if later added. Use provider presets to fill details rather than maintaining many copies of the same adapter. Do not claim universal provider support or make capability checks silently paid.

No production source was changed for this research. The first implementation unit should be the shared extraction contract and typed parser, because both the new settings promises and additional providers depend on that boundary.


## Follow-up: title, model discovery and test strategy

The requested new extraction contract is **title + extractedText + urls**. Keep `extractedText` as the wire name for the text body to minimize unnecessary downstream renaming. The earlier two-field schema in this document describes the initial proposal, not the final planned contract.

```json
{
  "title": "Workshop registration details",
  "extractedText": "# Workshop\nRegistration closes on Friday.",
  "urls": [
    {"url": "https://example.com/register", "description": "Workshop registration"}
  ]
}
```

All three top-level fields are required. `title` and `extractedText` are strings; `urls` is always an array, empty when no links are found. No readable content means empty strings and an empty array rather than a fabricated title. The corresponding strict schema must include `title` in properties and required, with additional properties forbidden. Update both adapter schema serializers and the common typed decoder together. Saved historical items need no blanket migration: they already have title/extractedText fields. If retained offline fixtures or an in-flight legacy request must be supported, handle the old shape through an explicitly scoped legacy decoder, not by silently making title optional in the new protocol.

### Title behavior

The inspected OCR success paths update extractedText but normally preserve the existing item title. A region extraction has a special raw-URL title fallback. `saveActiveItem` uses ItemTitleResolver and has first-line derivation for TEXT/CODE, not a general OCR-derived image title. Thus adding a title to the prompt alone will not fix naming; it must be parsed and applied to the active draft and persist through Save/reopen.

Proposed title instructions: prefer a clearly visible meaningful heading; otherwise suggest a short factual label based only on readable content, in the source language. Avoid generic labels, invented claims, Markdown wrapping and unrelated commentary. Aim for a concise phrase rather than enforcing an English word count on every language. Keep genuine headings in the extracted body; do not add a synthetic H1 merely to duplicate the suggested title.

Use the existing title sanitizer and Unicode-safe 120-character limit. A well-typed but blank/unusable title may fall back locally without discarding valid text. A missing or incorrectly typed title is a malformed response under the new contract. Fallback: first useful extracted text line, then original display name, then existing localized item-type fallback. Avoid exposing internal paths/cache filenames.

Never overwrite a user-authored title, including an edit made during the network request. Track draft title provenance (user/AI/import/fallback) and title revision, along with capture/request identity. Only apply a generated title to an eligible automatic title whose revision still matches the request. Preserve explicit user titles when restoring drafts and saving; do not infer provenance from a nonblank string or from generic-name comparisons alone. A retry can refresh an AI-generated title if the user has not edited it. Ignore stale results for another capture, earlier retry or changed provider configuration.

### Discovery and selection

Discovery is a provider-adapter operation using the configured connection, not a globally hardcoded model list:

| Protocol/profile | List request | Interpretation |
| --- | --- | --- |
| Gemini native | GET `/v1beta/models`, follow its pagination | Use supported methods and available metadata; current substring filters should be replaced. [Gemini models](https://ai.google.dev/api/models) |
| OpenAI-compatible | GET `models` relative to the saved API base, commonly `/v1/models`, bearer auth when required | Standard OpenAI model listings provide basic identity/availability data, not a complete vision/schema matrix. Capability may remain unknown. [OpenAI model list](https://platform.openai.com/docs/api-reference/models/list) |
| OpenRouter profile | GET `/api/v1/models`, with documented pagination when used | Can inspect input modalities, supported parameters and endpoint information. Advertised support is not proof of account access or successful generation. [OpenRouter model metadata](https://openrouter.ai/docs/api-reference/models/get-models) |
| Custom endpoint without discovery | Manual model ID | Discovery can fail or be unavailable while generation works. Do not make list support mandatory. |

Normalize into stable model ID, display name, task capabilities and metadata source. Treat absent capability fields as unknown rather than unsupported. Distinguish explicit unsupported models from unchecked models. Do not infer a model's capabilities from the words Flash/Pro/GPT. Do not generate requests for every discovered model to populate badges.

Cache discovery separately per connection/base URL and credential revision; redact secrets, and do not use literal keys as cache keys. Refresh preserves current selection, including an unavailable model, and reports that state instead of silently choosing another. Cancel or ignore stale discovery responses after connection changes. Handle pagination, duplicate IDs, empty pages, rate limits, timeouts, authentication errors and cached offline results. Prevent pagination loops.

Picker flow: open Text capture or Voice notes → choose a saved provider → search its models → choose a model → save that task binding. Show display name plus exact ID, a radio selection, Refresh, and Enter model ID. Reuse the existing searchable selector pattern but remove Gemini-only strings and filtering. Keep known compatible candidates easy to find, explain known unsupported choices, and allow unknown/manual candidates to run an explicit compatibility check. A successful check is scoped to connection, model, task and contract version; it must not be reused blindly after any of those change.

Keep the visible statuses simple: Not checked / Passed sample check / Not supported. A failed check due to quota or networking is not proof of unsupported capability. Show the actual error. Native schema support can be documented/advertised or checked, but one valid sample cannot prove that a server enforces the schema rather than ignoring it.

### Tests and what they establish

1. **Offline contract tests:** schema translations contain all required fields; decoding handles valid text, blank text, no links, missing/wrong-type title, malformed JSON, extra fields, truncation and refusals. Verify known accepted/rejected results exactly. Ensure invalid responses never become raw captured JSON.
2. **Title/draft tests:** generated title reaches draft then saved/reopened item; existing user title wins; user edits during an in-flight request win; stale requests cannot rename another capture; Unicode and RTL titles survive sanitization; blank/path/overlong suggestions use safe fallback. Preserve previous successful capture on retry failure.
3. **HTTP fixture tests:** local fake HTTP server/transport verifies headers, URL prefix joining, image payload and schema, response parsing, usage and error mapping for both adapters. No actual account/key is needed. Check wrong endpoint, malformed/non-JSON error body, 401/403/429/5xx, timeout and cancellation.
4. **Discovery/picker tests:** paginated models, metadata missing, known unsupported model, manual model ID, offline cache, refresh without selection changes, stale response after switching providers, provider/model bindings surviving restart, and no model list request on each key keystroke. Validate picker search, selected state, large text and readable status messages.
5. **Real provider sample check:** explicit action for one chosen connection/model, bundled synthetic screenshot with a visible heading/text/test URL, validates all three result fields. It uses provider quota and can cost money. A test URL is never fetched. Report only what passed; schema shape and source transcription accuracy are separate checks. Do not label every listed model verified.
6. **Real capture evaluation:** small fixed set of screenshots with reference text/link sets and acceptable title criteria: plain document, no heading, several columns, Urdu/Arabic, mixed scripts, blur, no text, no links, several links and instruction-like source text. For deterministic fixtures use exact expectations; for real model-generated titles use a factuality/readability rubric, not an exact wording assertion. Measure text omissions/additions and exact URL accuracy separately. Do not claim measured accuracy before running these evaluations.

The first four layers can be implemented without live credentials. Live tests establish only the provider/model/task combinations actually exercised. Build/test commands still need to run to establish compilation and automated results; the user's current preference is to run builds themselves. Writing tests is not equivalent to having passed them.
