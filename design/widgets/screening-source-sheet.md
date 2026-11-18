# Screening source picker · Appearance sheet pattern

[Editable Figma picker](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=142-2151)

The source picker now follows the Floating Scrollbar bottom sheet in Appearance & Display. Reference: FloatingScrollbarSettingsItem in app/src/main/kotlin/com/example/ui/components/ExpressiveSettingsComponents.kt. The reference was opened on the emulator without changing any preference; its screenshot is validation/appearance-scroll-sheet-reference.png.

Use a centered handle and a rounded sheet with the same surface background. The title and short explanation use 24px side padding. Option rows use 16px side margins. Everything is a separate rounded selected row; Movies, TV shows, Anime and Manga form the connected group below TITLE TYPES, with 8px between rows and large outer/small inner corners. Every row uses a canonical leading icon, short title/subtext and a trailing radio selection. Selection remains exclusive. An info footer explains that the choice applies to this card and includes only saved titles. Selecting a source dismisses the sheet.

The editable Figma sheet is 448×599, anchored at y398 in the 448×997 screen. Rows are 416×69. The final preview is validation/screening-source-sheet-appearance.png. This refinement is a Figma design change; the app source picker has not yet been replaced.
