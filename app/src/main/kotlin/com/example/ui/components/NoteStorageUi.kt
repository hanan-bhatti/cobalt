package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.text.format.Formatter
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.Alignment
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

fun formatNoteSize(context: Context, bytes: Long): String =
    Formatter.formatShortFileSize(context, bytes.coerceAtLeast(0))

/** Startup and recovery honor appearance preferences without initializing the normal ViewModel or secrets. */
@Composable
fun NoteStorageTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("cobalt_settings", Context.MODE_PRIVATE) }
    var mode by remember(prefs) { mutableStateOf(prefs.getString("theme_mode", "Light")) }
    var dynamic by remember(prefs) { mutableStateOf(prefs.getBoolean("dynamic_color", true)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changed, key ->
            if (key == "theme_mode") mode = changed.getString("theme_mode", "Light")
            if (key == "dynamic_color") dynamic = changed.getBoolean("dynamic_color", true)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val dark = when (mode) { "Dark" -> true; "Light" -> false; else -> isSystemInDarkTheme() }
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MyApplicationTheme(darkTheme = dark, dynamicColor = dynamic) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
    }
}

/** Shared settings geometry: the same collapsible header, margins and connected surfaces as Appearance. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoteSettingsPage(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    itemSpacing: Dp = 20.dp,
    listState: LazyListState = rememberLazyListState(),
    onRefresh: (() -> Unit)? = null,
    isRefreshing: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit
) {
    val bar = rememberCollapsibleTopBarState(listState, maxHeight = 160.dp)
    val height = with(LocalDensity.current) { bar.heightPx.toDp() }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).nestedScroll(bar.nestedScrollConnection)) {
        if (onRefresh != null) {
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                state = pullState,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = height + 4.dp)
                    )
                }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = height + 12.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(itemSpacing),
                    content = content
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = height + 12.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(itemSpacing),
                content = content
            )
        }
        CollapsibleCommonTopBar(title, bar.collapseFraction, height, onBack, subtitle = subtitle, actions = actions)
    }
}

/** Standalone/nested recovery gestures; normal Settings routes retain NavHost's predictive transitions. */
@Composable
fun NoteBackSurface(enabled: Boolean, onBack: () -> Unit, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    var direction by remember { mutableFloatStateOf(1f) }
    val back by rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { event ->
                direction = if (event.swipeEdge == 0) 1f else -1f
                progress.snapTo(event.progress)
            }
            back()
        } catch (_: CancellationException) {
            withContext(NonCancellable) { progress.animateTo(0f, tween(180)) }
        } finally {
            withContext(NonCancellable) { progress.snapTo(0f) }
        }
    }
    Box(Modifier.fillMaxSize().graphicsLayer {
        scaleX = 1f - progress.value * 0.06f
        scaleY = scaleX
        translationX = size.width * progress.value * 0.06f * direction
        shape = RoundedCornerShape(28.dp)
        clip = progress.value > 0f
    }) { content() }
}

@Composable
fun NoteStorageNotice(title: String, message: String) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(com.example.R.drawable.ic_custom_info),
                contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.compose.material3.Text(title, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface)
                androidx.compose.material3.Text(message, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Same neutral surface, 20dp corners and 220dp menu width as a folder-card long press. */
@Composable
fun NoteContextMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
        surfaceContainer = MaterialTheme.colorScheme.surface,
        surfaceContainerLow = MaterialTheme.colorScheme.surface,
        surfaceContainerHigh = MaterialTheme.colorScheme.surface,
        surfaceContainerHighest = MaterialTheme.colorScheme.surface
    )) {
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = onDismiss,
            shape = RoundedCornerShape(20.dp), modifier = Modifier.width(220.dp), content = content)
    }
}

@Composable
fun NoteContextAction(title: String, iconRes: Int, destructive: Boolean = false, onClick: () -> Unit) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    androidx.compose.material3.DropdownMenuItem(onClick = onClick,
        text = { androidx.compose.material3.Text(title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        leadingIcon = { androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(iconRes),
            contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) },
        colors = androidx.compose.material3.MenuDefaults.itemColors(
            textColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            leadingIconColor = tint))
}

/** Same NavHost engine and forward/pop transitions as the main settings navigation. */
@Composable
fun NoteStorageNavHost(navController: androidx.navigation.NavHostController, startDestination: String,
    builder: androidx.navigation.NavGraphBuilder.() -> Unit) {
    androidx.navigation.compose.NavHost(navController = navController, startDestination = startDestination,
        enterTransition = { com.example.ui.navigation.aospSharedAxisEnter() },
        exitTransition = { com.example.ui.navigation.aospSharedAxisExit() },
        popEnterTransition = { com.example.ui.navigation.aospSharedAxisPopEnter() },
        popExitTransition = { com.example.ui.navigation.aospSharedAxisPopExit() }, builder = builder)
}

/** Shared depth/dim/corner treatment; reads only the blur preference, never the normal ViewModel. */
@Composable
fun NoteStorageRoute(navController: androidx.navigation.NavHostController,
    scope: androidx.compose.animation.AnimatedVisibilityScope, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("cobalt_settings", Context.MODE_PRIVATE) }
    val forceDisable = remember(prefs) { prefs.getBoolean("force_disable_blur", false) }
    val capable = remember(context) { com.example.utils.DevicePerformance.isDeviceCapableOfBlur(context) }
    ScreenWrapper(navController = navController, disableBlur = forceDisable || !capable,
        animatedVisibilityScope = scope, content = content)
}
