# Cobalt home-screen widget concepts

[Open the five concepts in Figma](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=77-1320)

Created November 12, 2026 after reviewing the app demo, 19 raw emulator captures, the 15-panel screenshot campaign, earlier campaign concepts, the approved companion design, current Android widget source, theme and typography.

| Concept | Grid target | Purpose |
| --- | --- | --- |
| Capture Ticket | 4×2 | A warm perforated paper ticket with note, voice and OCR capture actions. |
| Pocket Folio | 4×3 | A layered, personalized pinned folder with two directly accessible saved items. |
| Archive Window | 4×3 | A quiet saved-note excerpt that invites rediscovery and return to its source. |
| Screening Card | 4×3 | A cinematic watchlist pick with source artwork, metadata and saved-title access. |
| Focus Page | 2×3 | A narrow pinned checklist showing completed lines and the next unfinished task. |

Five editable widget main components sit beside the launcher comparison board. The concepts use instances of the canonical icon library rather than duplicate icon definitions. Shared action components expose editable labels and icon swaps. Colors use a separate concept collection; repeated typography uses Cobalt's existing Small style and exact shared widget styles in Roboto and Source Serif 4, fonts already supported by the app. The media image is an existing discrete artwork asset from the approved Figma companion.

The board is 2084×1210 and shows each widget on an editable Android launcher. Main widget frames are 316×188, 316×248, 316×274, 316×300, and 156×274 respectively; launcher grid spans are design targets rather than universal Android dimensions.

Pocket Folio and Screening Card are now implemented, debug-built, installed and tested on the running Android emulator. It uses native RemoteViews with the same Canvas renderer as its live settings preview. The other three designs remain concepts and appear as “Coming next” in the app's Home Screen Widgets gallery. Existing icon masters, welcome artwork and separate logo concepts were preserved.

[Approved editable widget settings in Figma](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=92-2204) · [Implementation and validation report](../../docs/pocket-folio-validation-2026-11-12.md)

Each placed Folio has independent folder, color override and item-order preferences. Swipe between Folios in settings. A nonblank folder color wins; an uncolored folder uses its Folio override without changing folder data. App theme and UI-enabled custom fonts apply to both preview and launcher. The previous Quick Capture and Recent Items implementations have been removed.

Validation: editable text, nested canonical icon instances, bound tones, external main components, one discrete media image, expected font families, nonempty copy and 48-pixel action targets. The final board was visually reviewed after correcting hidden icon fills and softening the wallpaper accents.

## Screening Card refinement

[View the refined launcher concept in Figma](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=77-1833) · [Preview](validation/screening-card-refined.png)

The existing Screening Card master was refined in place: the artwork is now 316×156, up from 316×118, with a deliberate crop that removes the printed artwork title. The title has the full content width. View details and Next sit together as labeled 48px pill buttons, replacing the separate sync button and cramped watchlist copy. The existing launcher instance, canonical icons, Roboto/Source Serif 4 styles and shared color tokens remain connected. Screening Card now uses native RemoteViews and the same Canvas renderer in the launcher and its settings preview. Each card stores its source and pick cycle independently.

The Folio color selector now shows a swatch beside every color name, tinted rows, and a swatch inside its selected-value badge. The debug build passed and the picker was visually checked on the emulator; folder-color precedence and stored overrides are unchanged. [Picker screenshot](validation/folio-color-picker.png).

## Screening flow redesign and shared widget headers

[Editable Screening redesign](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=142-1965) · [Shared Folio and Screening headers and tooltips](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=145-2595)

The implemented redesign removes the separate reason and appearance sections, uses small shared headings with an info button, and replaces the response-card stack with three plain feedback rows in a bottom sheet. The source sheet contains Everything, Movies, TV shows, Anime and Manga. TV and Anime filtering are distinct; ranked Manga suggestions remain for the next algorithm pass.

The app now removes Screening from Media & Providers, removes its setup loading bar and reason/appearance sections, and uses the existing SettingsSubsectionHeader with its dismissible information dialog on both Screening Card and Pocket Folio. Screenshots and editable-node metadata are in validation/. [Current validation](../../docs/screening-widget-validation-2026-11-12.md).

The [Screening source picker](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=142-2151) now matches Appearance’s Floating Scrollbar sheet: centered handle, a separate Everything choice, connected text-only media-type rows, exclusive radio selection and a short info footer. [Design notes](screening-source-sheet.md) · [Preview](validation/screening-source-sheet-appearance.png).

## Screening launcher redesign concepts

[Compare four editable concepts in Figma](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=217-1729) · [Preview](validation/screening-launcher-concepts.png) · [Design notes](screening-launcher-concepts.md)

After testing the installed widget, the user requested a wider card and alternatives to the large View details/Next buttons. Four new concepts explore an artwork card, a cover shelf, a quiet text-first pick with feedback, and a three-title shortlist. Each fills a 336 px launcher slot in the same editable 360 px Android context. Titles/artwork are direct entry points, with compact or inline actions as appropriate.

The user edited Artwork, then selected the [floating-feedback refinement A](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=224-2082). It includes reversible thumbs and an optional 3 dp accent progress edge inspired by their YouTube reference, with no additional progress text. The Figma sample is illustrative; actual data controls whether the bar appears. A no-progress state remains available. [Research and implementation requirements](../../docs/screening-widget-research-2026-11-12.md) · [Preview](validation/screening-artwork-feedback.png). This latest launcher design is ready for review and has not yet been implemented.
