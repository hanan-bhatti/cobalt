# 002 — Smooth Per-Frame Window Morph Animation

- **Status**: TODO
- **Commit**: 68f8fea
- **Severity**: HIGH
- **Category**: Physicality & Performance
- **Estimated scope**: 1 file (`app/src/main/java/com/example/BrainOcrOverlayService.kt`)

## Problem

In `BrainOcrOverlayService.kt`, the previous collapse/expand implementation set window layout bounds upfront or deferred window resizing until `onAnimationEnd`. This created a mismatch between `containerView` (which remained 810px wide) and `handleView` (which shrank to 18px inside it), causing `handleView` to render offset at `onScreenX = 270` during SurfaceFlinger IPC layout passes.

Conversely, the reference implementation in `/home/hanan-bhatti/Downloads/BrainOcrOverlayService.kt` completely eliminates stray handles by updating `params.width`, `params.height`, and `params.y` on **every frame** of `ValueAnimator`. However, its default configuration has minor jerkiness due to a 50ms start delay, 380ms duration, and an aggressive initial curve (`PathInterpolator(0.05f, 0.7f, 0.1f, 1f)`).

## Target

Adopt the per-frame WindowManager layout update architecture from `/home/hanan-bhatti/Downloads/BrainOcrOverlayService.kt` in `BrainOcrOverlayService.kt`, optimized for maximum smoothness:
1. **Per-Frame Lockstep Window Resizing**: Update `params.width`, `params.height`, and `params.y` inside `addUpdateListener` on every animation frame during both `expandPanel()` and `collapsePanel()`.
2. **Eliminate Alpha Zeroing / Layout Delay Hacks**: Keep `handleView` visible naturally throughout the morph transition without artificial `alpha = 0f` or `OnLayoutChangeListener` delays.
3. **Smooth Motion Timing**: Use `duration = 300` with `PathInterpolator(0.32f, 0.72f, 0f, 1f)` (iOS drawer curve) and zero `startDelay` for crisp 60fps/120fps lockstep morphing.

Target code for `expandPanel()` and `collapsePanel()` update listeners:

```kotlin
/* Target per-frame window morph in BrainOcrOverlayService.kt */
addUpdateListener { animation ->
    val fraction = animation.animatedValue as Float

    panel.alpha = startAlpha + (1f - startAlpha) * fraction
    panel.scaleX = startScale + (1f - startScale) * fraction
    panel.scaleY = startScale + (1f - startScale) * fraction

    val handleParams = handleView?.layoutParams as? FrameLayout.LayoutParams
    if (handleParams != null) {
        val startHandleWidth = dpToPx(thickness)
        val endHandleWidth = endWidth
        val startHandleHeight = dpToPx(height)
        val endHandleHeight = endHeight

        handleParams.width = (startHandleWidth + (endHandleWidth - startHandleWidth) * fraction).toInt()
        handleParams.height = (startHandleHeight + (endHandleHeight - startHandleHeight) * fraction).toInt()
        handleView?.layoutParams = handleParams
    }

    // Animate WindowManager window width, height, and Y in lockstep per frame
    params.y = (startY + (targetY - startY) * fraction).toInt()
    params.width = (startWinWidth + (endWidth - startWinWidth) * fraction).toInt()
    params.height = (startWinHeight + (endHeight - startWinHeight) * fraction).toInt()
    try {
        windowManager.updateViewLayout(root, params)
    } catch (_: Exception) {}
}
```

## Repo conventions to follow

- Window layout updates use `windowManager.updateViewLayout(root, params)`.
- Dimensions are dynamically computed using `dpToPx()`.
- System gesture exclusions are refreshed via `updateSystemGestureExclusions()`.

## Steps

1. In `expandPanel()`:
   - Capture `startY`, `targetY`, `startWinWidth`, `startWinHeight`.
   - Remove the upfront `params.width = endWidth + marginPx` resize.
   - Update `params.y`, `params.width`, `params.height` per-frame inside `addUpdateListener`.
   - On `onAnimationEnd`, assign exact target bounds to `params` and call `updateViewLayout(root, params)` once to clean up rounding drift.
2. In `collapsePanel()`:
   - Capture `startY`, `targetY`, `startWidth`, `startHeight`, `endWidth`, `endHeight`.
   - Remove `handleView?.alpha = 0f` and `addOnLayoutChangeListener`.
   - Update `params.y`, `params.width`, `params.height` per-frame inside `addUpdateListener`.
   - On `onAnimationEnd`, remove `panelView`, reapply handle background style, assign final collapsed bounds to `params`, and call `updateViewLayout(root, params)`.

## Boundaries

- Do NOT add `startDelay` or artificial sleep pauses.
- Do NOT alter touch gesture drag thresholds or long press handlers in `createOverlayViews()`.
- Do NOT change the layout hierarchy (`FrameLayout` + `LinearLayout`).

## Verification

- **Mechanical**: `./gradlew assembleDebug` completes with `BUILD SUCCESSFUL`.
- **Feel check**:
  - Expand and collapse side panel on test device: confirm window morphs continuously with zero stray handle jumps or edge pops.
  - Check logcat: confirm `rootW` and `rootH` scale frame-by-frame from 66x300 to 810x1200 and back to 66x300.
- **Done when**: Panel morphs in lockstep with zero stray handle flashes and smooth 300ms motion across screen resolutions.
