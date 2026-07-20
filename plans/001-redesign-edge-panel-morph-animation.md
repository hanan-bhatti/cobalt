# 001 — Redesign Edge Panel Morph Animation for Dynamic Multi-Device Smoothness

- **Status**: TODO
- **Commit**: 68f8fea
- **Severity**: HIGH
- **Category**: Interruptibility & Performance & Physicality
- **Estimated scope**: 1 file (`app/src/main/java/com/example/BrainOcrOverlayService.kt`)

## Problem

In `BrainOcrOverlayService.kt`, expanding and collapsing the floating side panel suffers from IPC surface timing mismatches between `WindowManager.LayoutParams` overlay window resizing and `ValueAnimator` updates:
1. `expandPanel()` resizes `containerView` (`root`) upfront to 810px x 1200px.
2. `collapsePanel()` keeps `containerView` at 810px x 1200px throughout the 380ms collapse animation while `handleView` shrinks inside `containerView`.
3. In `onAnimationEnd`, `windowManager.updateViewLayout` is called to shrink `containerView` back to 66px x 300px (22dp x 100dp).
4. Because `WindowManager` resizes window overlay surfaces asynchronously in SurfaceFlinger, `containerView` remains 810px wide on screen for 48ms after `onAnimationEnd` fires.
5. Using `root.post` attempts to restore `handleView.alpha = opacity` after an arbitrary Handler loop tick, but because it is not frame-synchronized with SurfaceFlinger's actual window layout pass, `handleView` can flash away from the screen edge on devices with varying SurfaceFlinger IPC latency or high refresh rates (90Hz/120Hz).

Current code excerpt (`app/src/main/java/com/example/BrainOcrOverlayService.kt`):

```kotlin
/* app/src/main/java/com/example/BrainOcrOverlayService.kt:1175 — current collapse resize */
params.width = dpToPx(thickness + EXTRA_TOUCH_WIDTH_DP)
params.height = dpToPx(height)
params.y = calculateYPosition(yPercent, height)
try {
    windowManager.updateViewLayout(root, params)
} catch (e: Exception) {}
root.post {
    handleView?.alpha = opacity
    updateSystemGestureExclusions()
}
```

## Target

Redesign the expand/collapse transition in `BrainOcrOverlayService.kt` to be dynamically robust on any Android device resolution or refresh rate:
1. **Dynamic Easing & Duration**: Use Apple-like drawer curve `PathInterpolator(0.32f, 0.72f, 0f, 1f)` (duration 300ms) for responsive, natural panel entry and exit.
2. **Deterministic Frame-Synchronized Un-collapse**: Replace `root.post` guessing with a one-time `View.OnLayoutChangeListener` on `containerView` (`root`) during `collapsePanel()`'s `onAnimationEnd`:
   - Keep `handleView?.alpha = 0f` when triggering `updateViewLayout`.
   - Gate restoring `handleView?.alpha = opacity` and calling `updateSystemGestureExclusions()` on `OnLayoutChangeListener` firing with `(right - left == targetWidth && bottom - top == targetHeight)`.
3. **Clean Layout Margin & Alignment**: Ensure `handleParams` maintains `Gravity.END` (or `Gravity.START` for left side) and zero margin offset upon collapse end, preventing stray offsets.

Target code:

```kotlin
/* target collapse completion in BrainOcrOverlayService.kt */
val targetWidth = dpToPx(thickness + EXTRA_TOUCH_WIDTH_DP)
val targetHeight = dpToPx(height)

params.width = targetWidth
params.height = targetHeight
params.y = calculateYPosition(yPercent, height)

root.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
    override fun onLayoutChange(
        v: View, left: Int, top: Int, right: Int, bottom: Int,
        oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
    ) {
        val currentW = right - left
        val currentH = bottom - top
        if (currentW == targetWidth && currentH == targetHeight) {
            v.removeOnLayoutChangeListener(this)
            handleView?.alpha = opacity
            updateSystemGestureExclusions()
        }
    }
})

try {
    windowManager.updateViewLayout(root, params)
} catch (e: Exception) {}
```

## Repo conventions to follow

- Easing curves in `BrainOcrOverlayService.kt` use `PathInterpolator`.
- Overlay dimensions are dynamically converted using `dpToPx()`.
- System gesture exclusions are managed via `updateSystemGestureExclusions()`.

## Steps

1. In `expandPanel()` and `collapsePanel()`, update animator interpolator to `PathInterpolator(0.32f, 0.72f, 0f, 1f)` with duration `300ms` for responsive, smooth iOS-drawer feel.
2. In `collapsePanel()`'s `onAnimationEnd`, attach a `View.OnLayoutChangeListener` to `root` (`containerView`) before calling `windowManager.updateViewLayout(root, params)` with target collapsed bounds.
3. Inside `onLayoutChange`, check `(right - left == targetWidth && bottom - top == targetHeight)`. When matched, remove listener, restore `handleView?.alpha = opacity`, and invoke `updateSystemGestureExclusions()`. Remove the `root.post { ... }` block.

## Boundaries

- Do NOT alter touch gesture drag thresholds or long press handlers in `createOverlayViews()`.
- Do NOT change the layout hierarchy (`FrameLayout` + `LinearLayout`).
- Do NOT add external animation libraries.

## Verification

- **Mechanical**: `./gradlew assembleDebug` completes with `BUILD SUCCESSFUL`.
- **Feel check**:
  - Perform swipe/tap to expand side panel: smooth 300ms expansion with `PathInterpolator(0.32f, 0.72f, 0f, 1f)`.
  - Perform tap outside / collapse: smooth 300ms morph back to thin handle with zero visual jumps or stray handles on any device resolution/refresh rate (60Hz/90Hz/120Hz).
  - Verify `EDGE_MORPH_DEBUG` logcat lines show `GESTURE_EXCL_CALLED` firing synchronously on the exact frame `rootW=66, rootH=300` is laid out.
- **Done when**: Expand and collapse transitions animate smoothly without stray handle flashes across device rotations and screen resolutions.
