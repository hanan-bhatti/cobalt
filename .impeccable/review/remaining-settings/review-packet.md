# Remaining settings — finish review packet

Review scope: the complete Figma design batch for Cobalt Android settings. This is design only. The user requested all remaining settings pages, subpages and sheets in one batch, then feedback before any implementation.

Artifact: https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=576-38998
Page: 369:76 · Redesign · Android app
Complete review section: 600:40591
Settings prototype entry: 576:39001

Direction: design/settings/remaining-batch/direction.md
Node map: design/settings/remaining-batch/node-map.json
Coverage: design/settings/remaining-batch/coverage.json
Prototype read-back: design/settings/remaining-batch/prototype-links.json
Captures: .impeccable/review/remaining-settings/captures.json
Structural results: .impeccable/review/remaining-settings/structural.json
Text contrast: .impeccable/review/remaining-settings/text-contrast.json

Required screenshots: every phone/compact PNG named in captures.json, except review_index. All 31 were exported one Figma node per call, saved from that call's image bytes, and opened by the build agent. Each filename depicts its claimed screen. Dimensions: 448×997, or 320×712 for the three compact examples. review-index.png is an optional transparent index layer; its Settings phone is a separate top-level sibling to support prototype destinations, so that image alone does not include the phone.

The review is of phone Figma concepts, not an implemented native app. No emulator, keyboard, frame-time, predictive Back or OS permission guarantee is implied by these static views. The later implementation must retain the app's existing collapsing headings and predictive Back.

Coverage: 180 new views across Gemini OCR, Reminders, Media & providers, Storage & recovery, storage update/background activity, Help, Feedback, Survey, updates/About/legal, optional Web companion and Widgets. 47 accepted Screening views are explicitly reused. Four accepted main-page reference clones and one Settings review entry yield 232 unique phone roots.

Confirmed visual world / QUALITY BAR:
- Continue the accepted Cobalt system. Noto Serif headings, Roboto UI text, Material roles and existing rounded Iconsax assets. Source Serif 4 widget-title specimens are accepted.
- Tick/cross switches; compact bottom sheets for choices; direct simple switches.
- No Appearance preview. Text & fonts remains a separate page.
- No “Changes apply automatically.” copy.
- Honest descriptions of what each setting/action actually does.
- Exact approved Folio (316×248) and Screening (336×264) aspect ratios. Widget setup refers to launcher handoff.
- Existing icons come from /home/hanan-bhatti/glyphgoblin/scripts/public/iconsax/ and the app's XML drawables.

Four previously accepted reference clones (originals preserved):
Appearance 601:40591; Fonts 601:40647; Notes & folders 601:40676; Side dock 601:40745.
These are references, not new visual-world proposals. Fonts intentionally shows Inter reading text, JetBrains Mono code and a Noto Sans Arabic fallback specimen inside its accepted reading preview. Those seven specimen layers are not UI-font violations.

Source truth already checked during authoring:
- Installed source version 2.3.0 (20), bundled changelog 11 Nov 2026. 2.2.2 published 27 Oct 2026. 2.3.1 update examples are illustrative and labeled on the review guide outside app UI.
- Gemini usage is recorded locally, not billing/quota. Extraction detail changes instructions, not a measured detection threshold. Saved key and connection status are distinct.
- Feedback prepares a report; browser/email/Android sharing completes sending. Feature-request consent defaults true in current source.
- Survey is optional. Unanswered scores have no thumb/preselected rating.
- Cleanup copy names effects and preserved content. No actual content was deleted.
- Web companion is optional/release gated. Address/PIN are examples. Five failed sign-ins block the IP for 10 minutes. PIN rotation signs out browser sessions.
- Folio and Screening are available; Capture Ticket, Archive Window and Focus Page are unavailable.
- Folder color supplies Folio's accent; widget color is available when the folder has no color.
- Removing saved widget setup does not remove the launcher widget or content.
- Opening or ignoring Screening does not mark watched/read.
- No account data was accessed or changed.

Evidence limits / known observation:
The latest capture restored missing SVG masks and aligned Widget setup buttons. Screening thumbs and Folio's gold icons now render. Some leading icons still render black with low contrast on dark badges in Settings, Widgets, Item order and Screening setup, although Figma's saved paint and resolveForConsumer read back a light bound color. Treat the render as authoritative; this remains an open observation, not a passing icon-contrast claim.

Structural read-back covers 232 roots and 4,728 texts, with no forbidden copy or content overflow. 62 native Material radio/checkbox state layers extend from their 24px visual control to 40×40 by design; their source geometry was inspected. Seven font-preview specimen exceptions are reported separately. 4,701 checked text layers passed computed contrast; 24 image-backed text occurrences were excluded and require visual judgment. These checks do not establish icon contrast.

No design detector ran: this is native Android design, and the detector has no native verdict. Judge Material 3/native conventions using:
/home/hanan-bhatti/.agents/skills/impeccable/reference/android.md
Craft floor:
/home/hanan-bhatti/.agents/skills/impeccable/reference/craft-floor.md

Return a full review with the five contract sections: disposition (ship/fix/rebuild/recapture), material findings with exact screen/node/file evidence, fidelity/brief assessment, viewport/capture assessment, and actionable correction/verification requirements. A recapture response contains only the invalid capture list. Assess the Figma review deliverable at its actual scope. Do not implement application changes or modify the Figma design. Persist your review in .impeccable/review/remaining-settings/finish-review.md.
