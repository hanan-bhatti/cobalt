# Cobalt icon update

The approved rounded Linear set and current Figma edits now feed the Android resources and embedded web sprite. The welcome illustration, welcome backdrop, and separate logo concepts being edited in Figma were not changed.

- Ten approved glyphs adopted: attachment, success circle, error circle, warning triangle, unavailable media, thumbs up/down, muted/audible volume, and optional version tag.
- All 29 Material icon definitions replaced across their callers, including shared feedback banners and switch states. The change-media action uses Edit; playback retry and Trakt refresh use Sync.
- Five exact duplicate pairs consolidated into canonical resources with drawable aliases. All 140 synced Android vectors render successfully; the final rendered-vector scan finds no exact duplicates.
- Existing resource names, tint behavior, intrinsic resource sizes, and RTL mirroring are preserved. The version badge stays hidden by default.
- Figma's main board contains 143 app library assets, organized by category across four balanced columns. Bold shortlist samples remain reference comparisons outside the adopted library.

The [main component board](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=23-925) is the editing source. `design/icons/manifest.json` contains source IDs, usage, and aliases. `design/icons/README.md` describes importing future edits.

XML parsing, resource-reference checks, SDK VectorDrawable rendering, duplicate scanning, and welcome-file hash checks were performed. No app build, APK installation, or device runtime test was run, as requested.
