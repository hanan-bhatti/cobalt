# Animation Plans

Prioritized animation and motion improvement plans for Second Brain.

## Plan Catalog

| Plan | Title | Severity | Status |
| --- | --- | --- | --- |
| [001](001-redesign-edge-panel-morph-animation.md) | Redesign Edge Panel Morph Animation for Dynamic Multi-Device Smoothness | HIGH | SUPERSEDED |
| [002](002-per-frame-window-morph-animation.md) | Smooth Per-Frame Window Morph Animation | HIGH | TODO |

## Recommended Execution Order

1. **[002-per-frame-window-morph-animation.md](002-per-frame-window-morph-animation.md)**: Ports the per-frame WindowManager layout parameter animation architecture from `/home/hanan-bhatti/Downloads/BrainOcrOverlayService.kt` into `BrainOcrOverlayService.kt`. Eliminates stray handles by animating `params.width`, `params.height`, and `params.y` in lockstep on every frame with a 300ms `PathInterpolator(0.32f, 0.72f, 0f, 1f)` curve.

To execute a plan, prompt any subagent with the plan filepath.
