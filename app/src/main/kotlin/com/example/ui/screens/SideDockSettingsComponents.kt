package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.CobaltSideDockService
import com.example.R
import com.example.data.repository.SettingsRepository
import com.example.ui.components.CollapsibleCommonTopBar
import com.example.ui.components.ExpressiveSettingsGroup
import com.example.ui.components.ExpressiveSettingsNavigationItem
import com.example.ui.components.ExpressiveSettingsSwitchItem
import com.example.ui.components.SettingsIconBadge
import com.example.ui.components.connectedPillShape
import com.example.ui.components.rememberCollapsibleTopBarState
import com.example.ui.dock.SideDockMorphSurface
import com.example.ui.dock.SideDockPhysics
import com.example.ui.dock.sideDockCardGeometry
import com.example.ui.dock.sideDockHandleHeightDp
import com.example.ui.dock.sideDockHandleCenterYDp
import com.example.ui.dock.sideDockHandleOffsetYPx
import com.example.ui.dock.sideDockMorph
import com.example.ui.dock.sideDockPhysics
import com.example.ui.dock.sideDockPlacement
import com.example.utils.PermissionUtils
import kotlinx.coroutines.delay
import java.text.NumberFormat

internal data class SideDockPreviewSettings(
    val side: String = "Right",
    val position: Float = 0.4f,
    val opacity: Float = 0.7f,
    val heightDp: Int = 100,
    val thicknessDp: Int = 6,
    val preset: String = "Smooth",
    val stiffness: Float = 430f,
    val damping: Float = 1f
)

@Composable
internal fun rememberSideDockPreviewSettings(repository: SettingsRepository): SideDockPreviewSettings {
    val side by repository.edgePanelSide.collectAsState()
    val position by repository.edgePanelYPercent.collectAsState()
    val opacity by repository.edgePanelOpacity.collectAsState()
    val height by repository.edgePanelHeight.collectAsState()
    val thickness by repository.edgePanelThickness.collectAsState()
    val preset by repository.edgePanelAnimPreset.collectAsState()
    val stiffness by repository.edgePanelAnimStiffness.collectAsState()
    val damping by repository.edgePanelAnimDamping.collectAsState()
    val physics = sideDockPhysics(preset, stiffness, damping)
    return SideDockPreviewSettings(side, position, opacity, height, thickness, preset, physics.openingStiffness, physics.openingDamping)
}

@Composable
internal fun sideDockPresetLabel(preset: String): String = stringResource(when (preset) {
    "Fluid" -> R.string.settings_dock_preset_fluid
    "Bouncy" -> R.string.settings_dock_preset_bouncy
    "Snappy" -> R.string.settings_dock_preset_snappy
    "Instant" -> R.string.settings_dock_preset_instant
    "Custom" -> R.string.settings_dock_preset_custom
    else -> R.string.settings_dock_preset_smooth
})

internal fun dockDampingDisplay(value: Float): String = NumberFormat.getNumberInstance().apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}.format(value)

/** Opens the production overlay; no simulated capture actions are attached to the miniature. */
internal fun startSideDock(context: Context, open: Boolean = false) {
    ContextCompat.startForegroundService(context, Intent(context, CobaltSideDockService::class.java).apply {
        if (open) action = CobaltSideDockService.ACTION_OPEN_PANEL
    })
}

@Composable
internal fun rememberSideDockOverlayPermission(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    val owner = LocalLifecycleOwner.current
    var allowed by remember(context) { mutableStateOf(PermissionUtils.hasOverlayPermission(context)) }
    DisposableEffect(context, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = PermissionUtils.hasOverlayPermission(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return allowed
}

/** The prerequisite row is the whole settings surface until the dock can be used. */
@Composable
internal fun SideDockAccessRows(
    enabled: Boolean,
    overlayAllowed: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onRequestPermission: () -> Unit,
    onNavigateToAnimation: (() -> Unit)? = null,
    preset: String = ""
) {
    ExpressiveSettingsGroup {
        if (!overlayAllowed) {
            ExpressiveSettingsNavigationItem(
                title = stringResource(R.string.settings_dock_permission),
                subtitle = stringResource(R.string.settings_dock_permission_description),
                leadingIcon = { SettingsIconBadge(R.drawable.ic_custom_lock) },
                onClick = onRequestPermission, shape = connectedPillShape(0, 1), compact = true,
                modifier = Modifier.testTag("side_dock_permission")
            )
        } else {
            val showAnimation = enabled && onNavigateToAnimation != null
            val count = if (showAnimation) 2 else 1
            ExpressiveSettingsSwitchItem(
                title = stringResource(R.string.settings_dock_use),
                subtitle = stringResource(R.string.settings_dock_use_description),
                checked = enabled, onCheckedChange = onEnabledChanged,
                shape = connectedPillShape(0, count), compact = true,
                modifier = Modifier.testTag("side_dock_enable")
            )
            if (showAnimation) {
                ExpressiveSettingsNavigationItem(
                    title = stringResource(R.string.settings_dock_animation),
                    subtitle = stringResource(R.string.settings_dock_animation_summary, preset),
                    leadingIcon = { SettingsIconBadge(R.drawable.ic_custom_play) },
                    onClick = { onNavigateToAnimation?.invoke() }, shape = connectedPillShape(1, count), compact = true,
                    modifier = Modifier.testTag("side_dock_animation_link")
                )
            }
        }
    }
}

/** Keeps the native drag/keyboard control and gives accessibility its full 48 dp target. */
@Composable
internal fun SideDockSlider(
    title: String,
    value: Float,
    valueDisplay: String,
    range: ClosedFloatingPointRange<Float>,
    onValueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val current = value.coerceIn(range)
    Box(modifier.fillMaxWidth().height(48.dp).semantics {
        contentDescription = title
        stateDescription = valueDisplay
        progressBarRangeInfo = ProgressBarRangeInfo(current, range)
        if (!enabled) disabled()
        setProgress { requested ->
            val next = requested.coerceIn(range)
            if (!enabled || next == current) false else { onValueChanged(next); true }
        }
    }, contentAlignment = Alignment.Center) {
        Slider(value = current, onValueChange = onValueChanged, valueRange = range, enabled = enabled,
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { })
    }
}

@Composable
private fun rememberSystemAnimationsEnabled(): Boolean {
    val resolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
    fun isEnabled() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    var enabled by remember(resolver) { mutableStateOf(isEnabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled = isEnabled() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

/** Shares the approved collapsing header, safe-area spacing, and wide-screen arrangement. */
@Composable
internal fun SideDockSettingsLayout(
    title: String,
    subtitle: String,
    listTag: String,
    onNavigateBack: () -> Unit,
    leadingContent: @Composable () -> Unit,
    leadingKey: String = "dock_preview",
    twoColumns: Boolean = true,
    modifier: Modifier = Modifier,
    settingsItems: LazyListScope.() -> Unit
) {
    val listState = rememberLazyListState()
    val topBarState = rememberCollapsibleTopBarState(listState, maxHeight = 160.dp)
    val topPadding = with(LocalDensity.current) { topBarState.heightPx.toDp() }
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (twoColumns && maxWidth >= 640.dp) {
            Box(Modifier.fillMaxSize().nestedScroll(topBarState.nestedScrollConnection)) {
                CollapsibleCommonTopBar(
                    title = title, subtitle = subtitle, collapseFraction = topBarState.collapseFraction,
                    headerHeight = topPadding, onBackClick = onNavigateBack
                )
                Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    LazyColumn(
                        modifier = Modifier.weight(0.85f),
                        contentPadding = PaddingValues(top = topPadding + 12.dp, bottom = bottomPadding + 24.dp)
                    ) { item(key = leadingKey) { leadingContent() } }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1.2f).testTag(listTag),
                        contentPadding = PaddingValues(top = topPadding + 12.dp, bottom = bottomPadding + 24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        content = settingsItems
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize().nestedScroll(topBarState.nestedScrollConnection)) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = topPadding + 12.dp, start = 20.dp, end = 20.dp, bottom = bottomPadding + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 600.dp).fillMaxSize().testTag(listTag)
                ) {
                    item(key = leadingKey) { leadingContent() }
                    settingsItems()
                }
                CollapsibleCommonTopBar(title = title, subtitle = subtitle, collapseFraction = topBarState.collapseFraction,
                    headerHeight = topPadding, onBackClick = onNavigateBack)
            }
        }
    }
}

@Composable
internal fun SideDockAnimationPreview(
    settings: SideDockPreviewSettings,
    canOpenDock: Boolean,
    onOpenDock: () -> Unit,
    modifier: Modifier = Modifier,
    playbackEnabled: Boolean = true
) {
    var replay by remember { mutableIntStateOf(0) }
    val systemAnimationsEnabled = rememberSystemAnimationsEnabled()
    val preset = sideDockPresetLabel(settings.preset)
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Column(modifier.fillMaxWidth().testTag("side_dock_animation_preview"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SideDockMotionPreview(settings, replay, playbackEnabled, maxHeightDp = if (landscape) 192 else 264)
        }
        Text(preset, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(sideDockPresetDescription(settings.preset), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            minLines = 2, modifier = Modifier.widthIn(max = 360.dp))
        if (!systemAnimationsEnabled) {
            Text(stringResource(R.string.settings_dock_system_motion_off), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { replay++ }, enabled = systemAnimationsEnabled && settings.preset != "Instant",
                shape = CircleShape, modifier = Modifier.heightIn(min = 48.dp).testTag("side_dock_replay")) {
                Icon(painterResource(R.drawable.ic_settings_dock_replay), null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_dock_replay))
            }
            TextButton(onClick = onOpenDock, enabled = canOpenDock, shape = CircleShape,
                modifier = Modifier.heightIn(min = 48.dp).testTag("side_dock_open")) {
                Text(stringResource(R.string.settings_dock_open))
            }
        }
    }
}

@Composable
internal fun sideDockPresetDescription(preset: String): String = stringResource(when (preset) {
    "Fluid" -> R.string.settings_dock_preset_fluid_description
    "Bouncy" -> R.string.settings_dock_preset_bouncy_description
    "Snappy" -> R.string.settings_dock_preset_snappy_description
    "Instant" -> R.string.settings_dock_preset_instant_description
    "Custom" -> R.string.settings_dock_preset_custom_description
    else -> R.string.settings_dock_preset_smooth_description
})

/** Isolates the production dock on a transparent stage, preserving its scale ratio and spring. */
@Composable
internal fun SideDockMotionPreview(
    settings: SideDockPreviewSettings,
    replay: Int,
    playbackEnabled: Boolean,
    maxHeightDp: Int,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val rootView = LocalView.current.rootView
    val sourceHeight = if (rootView.height > 0) (rootView.height / density.density).dp else configuration.screenHeightDp.dp
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val card = sideDockCardGeometry(configuration.screenHeightDp, landscape)
    val handleHeight = sideDockHandleHeightDp(settings.heightDp, configuration.screenHeightDp, landscape)
    val offsetYPx = sideDockHandleOffsetYPx(settings.position, with(density) { sourceHeight.roundToPx() }, density.density, landscape)
    val centerY = sideDockHandleCenterYDp(
        sourceHeight.value, offsetYPx / density.density, handleHeight,
        navigationTopDp = WindowInsets.navigationBars.getTop(density) / density.density,
        navigationBottomDp = WindowInsets.navigationBars.getBottom(density) / density.density,
        statusTopDp = WindowInsets.statusBars.getTop(density) / density.density
    )
    val placement = sideDockPlacement(configuration.screenHeightDp.toFloat(), card, centerY, landscape)
    val lifecycleState = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value
    var visible by remember { mutableStateOf(true) }
    val allowed = playbackEnabled && visible && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val systemAnimationsEnabled = rememberSystemAnimationsEnabled()
    val progress = remember { Animatable(0f) }
    var playing by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    val physics = remember(settings.preset, settings.stiffness, settings.damping) {
        sideDockPhysics(settings.preset, settings.stiffness, settings.damping)
    }
    val enterSpec = remember(physics) { physics.openingSpec() }
    val exitSpec = remember(physics) { physics.closingSpec() }
    val request = Triple(physics, settings.side, replay)
    var lastRequest by remember { mutableStateOf<Triple<SideDockPhysics, String, Int>?>(null) }

    LaunchedEffect(request, allowed, systemAnimationsEnabled) {
        if (!allowed) {
            progress.snapTo(1f)
            playing = true
            dismissing = false
            return@LaunchedEffect
        }
        if (!systemAnimationsEnabled || physics.instant) {
            progress.snapTo(1f)
            playing = true
            dismissing = false
            lastRequest = request
            return@LaunchedEffect
        }
        if (lastRequest == request) return@LaunchedEffect
        lastRequest = request
        // Audition both directions. Changing presets while open must not skip the new entrance.
        if (progress.value > 0.001f) {
            playing = true
            dismissing = true
            progress.animateTo(0f, exitSpec)
            playing = false
            dismissing = false
            delay(100)
        }
        playing = true
        dismissing = false
        // Like the overlay, establish the full-size layout before starting its spring clock.
        withFrameNanos { }
        progress.animateTo(1f, enterSpec)
        // Rest open so the actual dock geometry remains visible; there is no idle loop.
    }
    val morph = { sideDockMorph(progress.value, card, handleHeight, settings.thicknessDp, settings.side,
        placement.startOffsetYDp, isDismissing = dismissing) }
    val preset = sideDockPresetLabel(settings.preset)
    val side = stringResource(if (settings.side == "Left") R.string.settings_dock_left_edge else R.string.settings_dock_right_edge)
    val description = stringResource(R.string.settings_dock_preview_accessibility, side, preset)

    BoxWithConstraints(modifier.clearAndSetSemantics { contentDescription = description }.onGloballyPositioned {
        val bounds = it.boundsInWindow()
        visible = bounds.bottom > 0f && bounds.top < rootView.height
    }) {
        // Leave room for Bouncy's real overshoot without clipping the isolated card.
        val stageWidth = (card.widthDp + 96).dp
        val stageHeight = (card.heightDp + 96).dp
        val previewHeight = minOf(maxHeightDp.dp, maxWidth / (stageWidth / stageHeight))
        ScaledAppearanceScene(stageWidth, stageHeight, previewHeight, Modifier.testTag("side_dock_motion_preview")) {
            Box(Modifier.fillMaxSize()) {
                if (playing) {
                    SideDockMorphSurface(
                        side = settings.side, card = card, morphState = morph,
                        modifier = Modifier.offset(48.dp, 48.dp).testTag("side_dock_preview_surface")
                    ) {
                        DockSkeletonContent(placement.isTopDock, Modifier.fillMaxSize().graphicsLayer {
                            val frame = morph()
                            alpha = frame.contentAlpha
                            translationX = frame.contentTranslationXDp.dp.toPx()
                        })
                    }
                } else {
                    Box(Modifier.offset(
                        x = (48 + if (settings.side == "Right") card.widthDp + 12 - settings.thicknessDp else -12).dp,
                        y = (48 + card.heightDp / 2f + placement.startOffsetYDp - handleHeight / 2f).dp
                    ).size(settings.thicknessDp.dp, handleHeight.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = settings.opacity), CircleShape))
                }
            }
        }
    }
}
