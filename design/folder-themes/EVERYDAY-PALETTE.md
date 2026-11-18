# Cobalt Everyday 20

Palette proposal · 2026-11-13

Twenty folder color choices organized into four groups of five. Familiar objects provide memorable names; each group mixes brightness and saturation so nearby hues retain different identities. Light and dark accents share their hue, with individually selected lightness and chroma.

The suggested associations and example folders below are optional design cues. Color-emotion research finds broad shared patterns alongside cultural and language differences; it does not establish a universal meaning for these exact swatches. [Jonauskaite et al., 2020](https://doi.org/10.1177/0956797620948810)

| ID | Color | Group | Light icon | Dark icon | Suggested association | Example folder |
| --- | --- | --- | --- | --- | --- | --- |
| F01 | Cherry | Warm | `#C53443` | `#FD5C67` | Attention | Priorities |
| F02 | Coral | Warm | `#C66F51` | `#FEB79E` | Warmth | People |
| F03 | Tangerine | Warm | `#B0600B` | `#FE973C` | Energy | Projects |
| F04 | Marigold | Warm | `#876606` | `#D0A016` | Confidence | Plans |
| F05 | Sunflower | Warm | `#948B04` | `#ECE36D` | Optimism | Ideas |
| F06 | Lime | Nature | `#5E7F0C` | `#A8DC33` | Freshness | Habits |
| F07 | Leaf | Nature | `#2E9752` | `#3FC56D` | Growth | Health |
| F08 | Forest | Nature | `#23593E` | `#5D9476` | Stability | Home |
| F09 | Teal | Nature | `#027A6E` | `#32DDC9` | Balance | Wellbeing |
| F10 | Cyan | Nature | `#278EA4` | `#89E8FF` | Clarity | Learning |
| F11 | Sky | Cool | `#6688C0` | `#A1C2F8` | Openness | Travel |
| F12 | Cobalt | Cool | `#1D4BC0` | `#4E84FF` | Focus | Work |
| F13 | Violet | Cool | `#824FC7` | `#B586FF` | Imagination | Creativity |
| F14 | Orchid | Cool | `#BB4EAF` | `#FA90EC` | Expression | Inspiration |
| F15 | Plum | Cool | `#713A73` | `#AA6CAC` | Reflection | Research |
| F16 | Rose | Earth & personal | `#B46D86` | `#FECFDE` | Care | Personal |
| F17 | Clay | Earth & personal | `#8A4721` | `#CC825B` | Making | Craft |
| F18 | Cocoa | Earth & personal | `#574029` | `#C5A88D` | Comfort | Journal |
| F19 | Sand | Earth & personal | `#948464` | `#EBD5A7` | Ease | Memories |
| F20 | Slate | Earth & personal | `#586D7F` | `#7F99B0` | Order | Admin |

## Applying the palette

Use `folder/<name>` as the stable token identity and select its light/dark role values from `everyday-palette.json`. Each theme includes separate list and pinned card roles for icon accent, card, icon box, border, folder label, item count, and settings icon. Counts remain neutral text. Solid color-picker chips show the accent at full strength; their icon ink is selected independently for contrast.

Folder colors are personalization choices, separate from error, warning, and success status tokens. Keep folder names and icons visible: twenty colors are not a substitute for readable identity. [W3C Use of Color](https://www.w3.org/WAI/WCAG22/Understanding/use-of-color.html)

The set is reviewed as a categorical palette, using hue, lightness, and chroma differences across the whole set. This follows the differentiation principle in [IBM Carbon's categorical palette guidance](https://carbondesignsystem.com/data-visualization/color-palettes/); these swatches are an original Cobalt proposal. The theme roles follow the separation of color roles and contrast decisions described in [Material Color Utilities](https://github.com/material-foundation/material-color-utilities), using OKLCH for this implementation.

## Design checks

The closest accent pair has OKLab distance ×100 of 7.55 in light and 7.54 in dark. The original set measured 2.35 and 1.96, respectively. These distances are comparative diagnostics, not recognition rates, WCAG thresholds, or evidence that every viewer can distinguish all twenty colors.

All 80 color/theme/card combinations meet the 3:1 icon and 4.5:1 label/count targets against their actual opaque sRGB surfaces. Lowest icon contrast: 3.11:1. Lowest count contrast: 5.40:1. The checks use the authored backgrounds `#FDFBF9` / `#1A120E`; wallpaper-derived backgrounds require their own verification.

See `everyday-audit.json` for the nearest pairs, and `previews/everyday-verification.json` for browser verification. User recognition testing is still the right way to validate how quickly people can identify their chosen folders.
