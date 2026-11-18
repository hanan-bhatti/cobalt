# Side dock

Mode: Operate. Extend the approved Cobalt Android Settings system. Widgets remain outside this task and will be done last.

## Direction contract

**Thesis:** Make handle adjustments direct. Reserve the compact motion demonstration for Animation, where selecting a preset immediately auditions it.

**First viewport:** Reuse the collapsing serif header, the tick/cross switch, and compact Material controls. Without overlay permission, show only the permission row. With permission and Side dock off, show only the enable switch. When enabled, expose Animation, the one-tap edge selector, and four inline sliders. Animation leads with an isolated dock skeleton centered on the page.

**Hierarchy:** Side dock access and Animation, followed by handle position and appearance. Screen edge exposes Left and Right directly as a one-tap Material segmented control. Its label and buttons share a row when there is room, and stack at compact widths or larger text sizes. Position, height, width, and opacity use native inline sliders with current values and meaningful endpoints. No handle setting opens a sheet and the main page has no dock preview. Animation has no enclosing preview card or miniature phone. Its selected preset, description, Replay, Open dock, round preset pills, and spring controls all sit below the preview. Give descriptions room for two lines so selecting a preset does not shift the pill rows. Preset widths remain fixed while selection changes. Wide Side dock layouts put access beside the handle controls only when enabled; Animation and prerequisite-only states use a centered single column capped at 600 dp.

**Motion:** The actual dock and preview share the spring configuration, resting dimensions, placement, morph surface, and content reveal. Smooth is critically damped; Fluid has a slower viscous entrance and a gentle finish; Bouncy has a controlled visible rebound; Snappy arrives quickly without rebound; Instant uses a snap. Exits settle faster than entrances. The shared spring visibility threshold is 0.001, avoiding a multi-dp terminal jump. Selecting a preset or Replay closes and reopens the preview, preserving velocity on interruption; it rests open without an idle loop. Hidden previews stop, and Android's Remove animations is respected. The preview has a transparent stage with room for real spring overshoot. Its dock uses the production aspect ratio and a single uniform scale. The Appearance preview was removed at the user's subsequent request.

**Truth:** The preview's accessible description identifies its illustrative skeleton; it never reads saved user data. Open dock invokes the real overlay. Permission takes priority over enablement, including when access changes while Animation is open. Stop the service when either prerequisite is missing. Explain slider behavior without latency, power, or frame-rate claims.

**Render budget:** Observe frame progress in the small shared surface renderer. Scrim, content reveal, and sheen read progress in drawing layers. Keep the capture content out of frame-by-frame recomposition; stream any progress callback from one snapshot observer. Preserve predictive Back handlers and gesture tracking.

**Continuity:** Preserve existing preference keys, custom spring values, screen routes, predictive Back, Material color/type roles, native interaction semantics, and rounded app icons. The user explicitly requested retuning named presets. Display their actual effective parameters even when stored values predate the tuning. Use source Iconsax vectors when a missing icon has a clear semantic role. No automatic-apply footer and no Widget changes.

The existing Settings, Appearance, Text & fonts, and Notes & folders screens establish the visual system; this task changes no global design identity. Figma screens join `Redesign · Android app` in the supplied Cobalt file.
