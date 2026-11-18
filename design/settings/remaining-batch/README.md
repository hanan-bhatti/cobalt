# Remaining settings · Figma batch

This directory records the Figma review deliverable. It does not implement the remaining Android screens.

Open the [review overview](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=576-38998). The complete section is `600:40591`, on page `369:76`; the working Settings prototype entry is `576:39001`.

`node-map.json` records current node IDs, icon component sources, styles and Material role variables. `coverage.json` lists 180 new views and the 47 explicitly reused approved Screening views. Four accepted reference clones and the Settings review entry bring the complete board to 232 unique phone roots. `prototype-links.json` records prototype wiring and read-back evidence. Those links illustrate representative states, not editable live forms.

The module JSON files contain the source-informed content and view structure. Search placeholders, gallery descriptions and the empty Projects Folio specimen have been amended to match the current canvas.

The composition files are authoring snapshots, not a turnkey regeneration command. The first 88 views used the original engine; subsequent views used the plan build/post/size stages. `compose-post.archive.js` contains functional `.set(n => …)` callbacks that silently did nothing in this runtime. The supported form is `.each(n => $fig.get(n.id).set({...}))`. Instance descendants also reject relative transforms; intrinsic HUG text sizing works where width overrides do not. The one-time repairs in `repair-history.json` supersede these incomplete stages and include replaced IDs. The current Figma canvas and node map are authoritative.

Native icon SVG clip paths must retain an opaque bound vector inside their mask GROUP. Checking only a vector's own `isMask` misses group masks and makes icons disappear. Folder badge contrast must composite translucent badge paints over the surface before selecting the glyph role.

Review screenshots and reports are under `.impeccable/review/remaining-settings/`. Every current capture was made with one node per Figma tool call. Multi-screen screenshot calls may return images in completion order; never assign their filenames by request index. Only names in `captures.json` are review evidence.

The visual world continues the accepted Cobalt system; no product/design-system file has been replaced. Source code changes wait for the user's feedback. Figma validation does not certify runtime gestures, accessibility semantics or performance.
