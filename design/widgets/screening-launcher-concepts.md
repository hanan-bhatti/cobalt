# Screening launcher concepts · November 12, 2026

[Editable comparison board](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=217-1729) · [Main components](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=217-1733) · [PNG preview](validation/screening-launcher-concepts.png)

Requested after testing the Android widget: reduce excess side gaps, rethink the card, and replace the large Next/View details buttons. These are four proposed launcher designs awaiting the user's selection.

| Option | Size in shared 360 px launcher | Proposed interaction |
| --- | --- | --- |
| 01 Artwork | 336×264 | Artwork/title opens the saved title. A compact 48 dp corner control changes the pick and disappears when only one candidate exists. |
| 02 Cover shelf | 336×224 | Cover/title opens details. More opens the existing three-option feedback sheet. |
| 03 Quiet pick | 336×200 | Title opens details. Plain Good pick/Later actions record recommendation feedback. No artwork is required. |
| 04 Shortlist | 336×280 | Three saved-title rows open directly. Alternatives are visible, so cycling is unnecessary. |

All options fill the host width; the 12 px inset shown belongs to the shared launcher context. The current provider's fixed-aspect-ratio centering padding should be removed when implementing the selected design. The artwork should crop or reflow with size, and smaller shortlist sizes should show fewer rows. Launcher-owned outer spacing varies by device.

The same Your Name. artwork/title is used for comparison. Shortlist examples add Perfect Days and Look Back; Manga uses format metadata without invented chapter progress. Media-category icons are omitted. Compact controls use canonical Cobalt icon components, with 48 dp targets despite smaller visible glyphs. Title and metadata use existing Source Serif 4/Roboto styles and Cobalt color variables.

Seven reusable main components and their instances keep the concepts editable. Only discrete artwork uses image fills. No screenshot, simulated swipe gesture, ranking change, or new launcher implementation is included in this design pass.

Construction source: [screening-launcher-concepts.js](screening-launcher-concepts.js). Node IDs, dimensions, image inventory and font assertions: [validation metadata](validation/screening-launcher-concepts.json). The final visual correction moved all launcher docks to consistent bottom positions.
