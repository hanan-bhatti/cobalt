# Notes & folders

Mode: Operate. This page extends the approved Android Settings system.

The user asked to redesign and build Notes & folders after approving Settings, Appearance, and Text & fonts. Preserve the collapsing serif header, Material color roles, existing rounded icons, tick/cross switches, native choice sheets, and predictive Back. Do not add “Changes apply automatically.”

The page has two existing functions: folder suggestions and the archive summary displayed at the top of Settings. A small, explicitly labeled example shows suggested-first and alphabetical folder order. The explanation sheet describes saved-item count, recency, content matching, item type, and the pinned-folder rule without promising a particular result. The archive selector opens the existing native choice sheet for Activity heatmap or Category totals.

Reuse `SettingsGroupLabel`, `ExpressiveSettingsGroup`, compact Settings rows, `SettingsBottomSheet`, `SettingsChoiceSheet`, `CollapsibleCommonTopBar`, and current app drawables. Use Material typography roles and semantic colors; the native palette derives from the wallpaper. The Figma reference uses the library’s theme variables, so its warm colors are reference-mode values rather than hardcoded Android colors.

Examples are illustrative, localized folder names. They use the real folder picker’s `FolderIcon`/`getFolderIconRes`, `folderPresetColors`, and `parseHexColor`: Work uses the work icon and Royal Indigo; Ideas uses the idea icon and Soft Gold; Reading uses the app’s book option and Emerald Sage. Each keeps its colored icon box when the order changes. The user explicitly requested these real folder identities during implementation. They do not inspect, select, move, or modify saved items. The settings retain their repository keys and defaults. Navigation, transition code, and switches are outside this change.

Recency copy names capture dates because ranking reads saved-item timestamps; opening a folder or moving an old item does not itself refresh that score. The archive choice appears in its row’s description so the location and selected view fit on compact screens.

Figma: [Notes & folders review board](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI/Cobalt?node-id=466-20299), on `Redesign · Android app`. Editable Light, Dark, and suggestions-off states; native sheet states accompany them. This is a code-led extension with the Figma board as a critique reference, not a new visual identity.

Validation covers both preference controls, sheet dismissal, persistence, the resulting Settings archive, Back cancellation and commit, Light/Dark, larger text, and compact and rotated layouts. Native captures and a fresh finish review establish the final visual evidence; the HTML/CSS detector does not apply.
