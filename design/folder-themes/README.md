# Cobalt folder palette study

Open `index.html` in a browser to review **Everyday 20**, a new folder palette with four groups of five familiar colors. Both light and dark palettes are visible together. Choose a color to update the folder previews, switch between list and pinned cards, or select **Original 20** to compare the existing app palette.

- `EVERYDAY-PALETTE.md`: all 20 IDs, theme pairs, suggested associations, design rationale, and sources.
- `everyday-palette.json`: full light/dark role tokens for list and pinned cards, plus source and audit metadata.
- `everyday-palette.csv`: compact palette table for review or import.
- `everyday-audit.json`: nearest accent pairs and contrast measurements.
- `everyday-palette.template.html`: editable standalone demo source.
- `build_everyday_palette.py`: reproducible color generation, gamut mapping, contrast audit, and HTML build.
- `previews/`: browser screenshots and verification reports.

Build the proposal with:

```sh
python3 design/folder-themes/build_everyday_palette.py
```

To serve it locally:

```sh
python3 -m http.server 8785 --bind 127.0.0.1 --directory design/folder-themes
```

The demo is a standalone document with embedded data and the app's actual folder, pin, and settings SVG paths. It works when opened directly, uses optional Google Fonts with a system-font fallback, and reads no app data. Example folder names, counts, and emotional associations are suggestions. The template does not depend on the conversation's inline visualization renderer.

This is a palette proposal for review. App implementation and the approved Figma variable values are separate from this study. All theme checks use the authored app backgrounds; wallpaper-derived themes need their own checks.

The earlier three-column theme-conversion demo is preserved in `original.html`, with its editable source in `folder-theme-comparison.html`. Its earlier verification is in `previews/verification.json`; verification of Everyday 20 is recorded separately in `previews/everyday-verification.json`.
