# Cobalt icon library

143 canonical assets in 14 categories. The approved rounded Linear additions and the latest edited Figma vectors are now used by Android and the embedded web companion. The welcome artwork is excluded from this update.

[Edit the main components in Figma](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=23-925). The compact board uses four balanced columns. Related actions remain adjacent, including Undo/Redo and muted/audible volume.

`manifest.json` maps each clean ID and friendly name to its canonical Figma component, Android resource, current SVG, and usage locations. `svg/` contains current Figma exports; welcome exports retain their previous state. The 29 Material definitions have been replaced by 10 new Linear glyphs and existing Cobalt icons. No Material icon calls remain in Android source.

Five identical shape pairs share one master and one native vector. `app/src/main/res/values/icon_aliases.xml` preserves existing resource names for navigation folders/media, code blocks, quotations, and bookmarks. These aliases do not duplicate path geometry. Distinct navigation bold states and brand palettes remain intentional.

To sync future Figma refinements:

1. Export the **main component**, rather than its preview tile, as SVG, and use its canonical ID from the manifest.
2. Android Studio's **New → Vector Asset → Local SVG** can import individual refinements. Replace the canonical drawable listed in the manifest; aliases then pick up the change automatically. Preserve `autoMirrored=true` for external-link and volume resources.
3. For a full batch, provide a JSON array of `{ "id": "cobalt-…", "nodeId": "…:…", "svg": "<svg …>…</svg>" }` containing every non-welcome canonical asset, then run `python3 scripts/sync_figma_icon_assets.py bundle.json --apply`. Without `--apply`, it validates conversion only. The script uses cached Android SDK conversion libraries and does not build the app.
4. Run `python3 scripts/web/icons.py` to refresh the embedded web sprite.

Figma does not automatically update Android files. The old `export_figma_icons.py` bootstrap is guarded against overwriting this synced library with Android-origin exports.

Validation and alias mappings are recorded in `sync-report-2026-11-11.json`. The app was not built or installed.
