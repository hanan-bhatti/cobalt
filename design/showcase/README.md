# Cobalt showcase assets

- `exports/`: 15 portrait PNGs, a three-panel panorama, and a 2560 × 1280 README banner.
- `source/`: editable SVG layouts imported as native text/vector/image layers in Figma.
- `raw/`: genuine, lossless emulator captures used by the layouts.
- `previews/all-15.png`: the complete contact sheet.
- `manifest.json`: copy, colors, capture provenance and hashes.
- `seed_debug_heatmap.py`: emulator/debug-only fixture-date utility, with a database backup and integrity/content checks.

Figma: https://www.figma.com/design/Im9uv0k2UCK8B4VJ4irKnv/

Run `python3 design/showcase/build_assets.py` from the repository to render with ImageMagick and the bundled Inter font, then publish to README, F-Droid and website assets. Use `--publish-only` to copy approved exports without rendering, or `--check` to verify that every published image matches and no obsolete screenshot remains. No image-generation model is used.

All app UI remains a genuine raster capture. Layout text, device shapes and backgrounds are editable. Search is a clearly framed close-up of actual search results. Panels 07–09 are crops from one continuous composition; the third panel also shows the note reached from the folder.

The heatmap capture uses the current debug APK (2.2.3/code 19) with 500 disposable captures across 85 days. It was refreshed after an in-place debug update to remove obsolete survey copy, with demo item rows checked for retention. Other product captures use the installed production APK. No root or production-data reseeding was used.

Visual reference: [Project Planner: Milestones by Igor Radivojevic](https://dribbble.com/shots/27476160-Project-Planner-Milestones-App-Store-Screenshots), viewed for headline hierarchy, varying device scale, and sequential storytelling. Cobalt captures and the final vector compositions are original.
