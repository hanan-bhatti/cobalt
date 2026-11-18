# Iconsax replacement shortlist

[Open the Figma visual review](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=47-916)

Status: candidates selected for visual approval. Android assets and existing Figma main components are unchanged.

Recommendation: rounded Linear for normal UI. Nine Bold options are included for comparison at status/selected states. Attachment is Linear-only in this shortlist; the Bold paperclip files inspected add a surrounding container.

| Needed glyph | Selected family | Purpose |
|---|---|---|
| Attachment | paperclip | Attach screenshot / recording |
| Success circle | tick-circle | Success banners and selected model |
| Error circle | info-circle | Error feedback banner |
| Warning | danger | Extraction errors and overlay permission |
| Media unavailable | cloud-cross | Missing media and playback error |
| Thumbs up | like | FAQ answer: helpful |
| Thumbs down | dislike | FAQ answer: not helpful |
| Volume muted | volume-cross | Muted player / unmute action |
| Volume on | volume-high | Audible player / mute action |
| Version tag · optional | tag | Only if version-badge icon is enabled |

The review also includes 13 roles supplied by current Cobalt icons. Those are instances of the existing main components, so their current Figma edits are retained. All 29 Material icon definitions are covered; Change media has its own Edit mapping.

Selection details and original source paths: `selection.json`. The SVG copies use neutral ink and fill in missing rounded cap/join defaults. Original collection files are untouched.

After visual approval, the approved glyphs can be converted into Android vector drawables and the Material references migrated.
