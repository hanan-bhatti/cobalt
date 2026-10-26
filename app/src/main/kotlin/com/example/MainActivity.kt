/*
 * Cobalt - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.ui.screens.SettingsCategory
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CaptureScreen
import com.example.ui.screens.DetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.FoldersScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.CobaltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import com.example.ui.components.CustomBottomBar
import com.example.ui.components.BottomBarItem
import com.example.ui.components.ScreenWrapper
import com.example.ui.navigation.aospSharedAxisEnter
import com.example.ui.navigation.aospSharedAxisExit
import com.example.ui.navigation.aospSharedAxisPopEnter
import com.example.ui.navigation.aospSharedAxisPopExit
import com.example.ui.navigation.mainRootEnterTransition
import com.example.ui.navigation.mainRootExitTransition
import com.example.ui.navigation.BlurEffectCache
import com.example.ui.navigation.M3EmphasizedEasing
import com.example.ui.navigation.EmphasizedDecelerateEasing
import com.example.ui.navigation.EmphasizedAccelerateEasing
import com.example.utils.DevicePerformance
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Person
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search

class MainActivity : ComponentActivity() {
    private val viewModel: CobaltViewModel by viewModels()
    private val pendingNavDestination = androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingNavDestination.value = intent?.getStringExtra("NAVIGATE_TO")

        // Force widget update on app start to overcome OEM background restrictions
        com.example.widget.WidgetUpdater.update(applicationContext)

        // Auto-start Cobalt SideDock service if enabled and allowed
        val settingsRepo = com.example.data.repository.SettingsRepository(applicationContext)
        if (settingsRepo.isFloatingOcrEnabled.value && com.example.utils.PermissionUtils.hasOverlayPermission(applicationContext)) {
            val serviceIntent = Intent(applicationContext, CobaltSideDockService::class.java)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } catch (e: Exception) {
                android.util.Log.e("Cobalt", "Failed to start Cobalt SideDock service: ${e.message}", e)
            }
        }

        // Handle shared intent if starting via share-sheet capture
        handleIntent(intent)

        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        setContent {
            val themeMode by viewModel.settingsRepository.themeMode.collectAsState()
            val dynamicColorEnabled by viewModel.settingsRepository.dynamicColor.collectAsState()
            val isDarkTheme = when (themeMode) {
                "Dark" -> true
                "Light" -> false
                else -> isSystemInDarkTheme()
            }

            val view = androidx.compose.ui.platform.LocalView.current
            if (!view.isInEditMode) {
                androidx.compose.runtime.SideEffect {
                    val window = (view.context as android.app.Activity).window
                    androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDarkTheme
                }
            }
            MyApplicationTheme(darkTheme = isDarkTheme, dynamicColor = dynamicColorEnabled) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val customTabsUriHandler = remember(context, isDarkTheme) {
                    object : androidx.compose.ui.platform.UriHandler {
                        override fun openUri(uri: String) {
                            com.example.util.CobaltCustomTabsLauncher.launch(
                                context = context,
                                url = uri,
                                isDarkTheme = isDarkTheme
                            )
                        }
                    }
                }
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalUriHandler provides customTabsUriHandler
                ) {
                    val navController = rememberNavController()
                    val activeCaptureItem by viewModel.activeCaptureItem.collectAsState()
                    val activeDetailItem by viewModel.activeDetailItem.collectAsState()
                    val availableUpdate by viewModel.availableUpdate.collectAsState()
                    var pendingCrashReport by remember { mutableStateOf(com.example.util.FossCrashReporter.getPendingCrashReport(context)) }

                BackHandler(enabled = activeCaptureItem != null) {
                    viewModel.cancelCapture()
                }

                val navDest by pendingNavDestination
                LaunchedEffect(navDest) {
                    val dest = navDest
                    if (!dest.isNullOrBlank()) {
                        try {
                            navController.navigate(dest) {
                                launchSingleTop = true
                            }
                        } catch (_: Exception) {}
                        pendingNavDestination.value = null
                    }
                }

                if (pendingCrashReport != null) {
                    com.example.ui.components.ExpressiveCrashReportDialog(
                        crashReportText = pendingCrashReport!!,
                        onDismiss = {
                            com.example.util.FossCrashReporter.clearCrashReport(context)
                            pendingCrashReport = null
                        },
                        onSendEmail = {
                            com.example.util.FossCrashReporter.sendCrashReportEmail(context, pendingCrashReport!!)
                            com.example.util.FossCrashReporter.clearCrashReport(context)
                            pendingCrashReport = null
                        }
                    )
                }

                if (availableUpdate != null) {
                    com.example.ui.components.UpdateAvailableBottomSheet(
                        latestRelease = com.example.util.ReleaseNote(
                            versionName = availableUpdate!!.latestVersionName,
                            versionCode = 999,
                            releaseDate = "Latest Release",
                            tag = com.example.util.AppVersionTag.RC,
                            highlights = listOf(availableUpdate!!.releaseNotes.take(300).ifBlank { "New update v${availableUpdate!!.latestVersionName} is ready to download." })
                        ),
                        onDismissRequest = { viewModel.dismissUpdateBanner() },
                        onUpdateClick = {
                            com.example.util.CobaltCustomTabsLauncher.launch(
                                context = this@MainActivity,
                                url = availableUpdate!!.releaseUrl
                            )
                        },
                        onViewChangelogClick = {
                            viewModel.dismissUpdateBanner()
                            navController.navigate("profile")
                        }
                    )
                }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val hazeState = remember { HazeState() }

                androidx.compose.runtime.DisposableEffect(navController) {
                    val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
                        val route = destination.route ?: return@OnDestinationChangedListener
                        logScreenView(context, route)
                        if (!route.startsWith("detail") && viewModel.activeDetailItem.value != null) {
                            viewModel.closeDetailItem()
                        }
                        com.example.ui.screens.TransitionCoordinator.previousNavRoute = com.example.ui.screens.TransitionCoordinator.currentNavRoute
                        com.example.ui.screens.TransitionCoordinator.currentNavRoute = route
                    }
                    navController.addOnDestinationChangedListener(listener)
                    onDispose {
                        navController.removeOnDestinationChangedListener(listener)
                    }
                }

                LaunchedEffect(activeCaptureItem) {
                    if (activeCaptureItem != null) {
                        logScreenView(context, "note_editor")
                    }
                }

                LaunchedEffect(activeDetailItem) {
                    val item = activeDetailItem
                    if (item != null) {
                        logScreenView(context, "note_detail")
                        if (navController.currentDestination?.route?.startsWith("detail") != true) {
                            navController.navigate("detail/${item.id}")
                        }
                    }
                }

                val blurEffectCache = remember { BlurEffectCache() }
                val forceDisableBlur by viewModel.forceDisableBlur.collectAsState()
                val useBlur = DevicePerformance.isDeviceCapableOfBlur(context) && !forceDisableBlur
                val density = LocalDensity.current.density

                val captureBlurFraction by animateFloatAsState(
                    targetValue = if (activeCaptureItem != null) 1f else 0f,
                    animationSpec = tween(durationMillis = 350, easing = M3EmphasizedEasing),
                    label = "captureBlurFraction"
                )

                Scaffold(
                    contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        @OptIn(ExperimentalSharedTransitionApi::class)
                        SharedTransitionLayout {
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        if (captureBlurFraction > 0.001f) {
                                            val scale = 1f - (captureBlurFraction * 0.05f)
                                            scaleX = scale
                                            scaleY = scale
                                            clip = true
                                            shape = RoundedCornerShape((captureBlurFraction * 28f).dp)
                                            if (useBlur) {
                                                val radiusPx = captureBlurFraction * 24f * density
                                                if (radiusPx > 0.5f) {
                                                    renderEffect = blurEffectCache.get(radiusPx)
                                                }
                                            }
                                        }
                                    }
                                    .hazeSource(hazeState)
                            ) {
                                 // Standard Home and Authentication navigation flow
                                 NavHost(
                                     navController = navController,
                                     startDestination = "home",
                                     enterTransition = {
                                         if (targetState.destination.route?.startsWith("detail") == true) {
                                             fadeIn(
                                                 animationSpec = tween(durationMillis = 280, easing = LinearEasing)
                                             )
                                         } else {
                                             mainRootEnterTransition(
                                                 fromRoute = initialState.destination.route,
                                                 toRoute = targetState.destination.route,
                                                 fallback = aospSharedAxisEnter()
                                             )
                                         }
                                     },
                                     exitTransition = {
                                         if (targetState.destination.route?.startsWith("detail") == true) {
                                             fadeOut(
                                                 animationSpec = tween(durationMillis = 200, easing = LinearEasing)
                                             )
                                         } else {
                                             mainRootExitTransition(
                                                 fromRoute = initialState.destination.route,
                                                 toRoute = targetState.destination.route,
                                                 fallback = aospSharedAxisExit()
                                             )
                                         }
                                     },
                                     popEnterTransition = {
                                         if (initialState.destination.route?.startsWith("detail") == true) {
                                             fadeIn(
                                                 animationSpec = tween(durationMillis = 280, easing = LinearEasing)
                                             )
                                         } else {
                                             mainRootEnterTransition(
                                                 fromRoute = initialState.destination.route,
                                                 toRoute = targetState.destination.route,
                                                 fallback = aospSharedAxisPopEnter()
                                             )
                                         }
                                     },
                                     popExitTransition = {
                                         if (initialState.destination.route?.startsWith("detail") == true) {
                                             fadeOut(
                                                 animationSpec = tween(durationMillis = 220, easing = LinearEasing)
                                             )
                                         } else {
                                             mainRootExitTransition(
                                                 fromRoute = initialState.destination.route,
                                                 toRoute = targetState.destination.route,
                                                 fallback = aospSharedAxisPopExit()
                                             )
                                         }
                                     }
                                 ) {
                                    composable("home") {
                                        ScreenWrapper(navController = navController, viewModel = viewModel, animatedVisibilityScope = this) {
                                            HomeScreen(
                                                onNavigateToSearch = { navController.navigate("search") },
                                                viewModel = viewModel,
                                                onNavigateToProfile = { navController.navigate("profile") },
                                                onNavigateToMediaHub = { navController.navigate("media_hub") },
                                                sharedTransitionScope = this@SharedTransitionLayout,
                                                animatedVisibilityScope = this@composable,
                                                onItemClick = { item ->
                                                    viewModel.showDetailItem(item)
                                                    navController.navigate("detail/${item.id}")
                                                }
                                            )
                                        }
                                    }
                                    composable("search") {
                                        ScreenWrapper(navController = navController, viewModel = viewModel, animatedVisibilityScope = this) {
                                            com.example.ui.screens.SearchScreen(
                                                viewModel = viewModel,
                                                onNavigateBack = { navController.popBackStack() },
                                                onItemClick = { item ->
                                                    viewModel.showDetailItem(item)
                                                    navController.navigate("detail/${item.id}")
                                                }
                                            )
                                        }
                                    }
                                    composable("folders") {
                                        ScreenWrapper(navController = navController, viewModel = viewModel, animatedVisibilityScope = this) {
                                            FoldersScreen(
                                                viewModel = viewModel,
                                                hazeState = hazeState,
                                                sharedTransitionScope = this@SharedTransitionLayout,
                                                animatedVisibilityScope = this@composable,
                                                onOpenFolder = { folderName ->
                                                    navController.navigate("folder/${android.net.Uri.encode(folderName)}")
                                                },
                                                onItemClick = { item ->
                                                    viewModel.showDetailItem(item)
                                                    navController.navigate("detail/${item.id}")
                                                }
                                            )
                                        }
                                    }
                                    composable(
                                        route = "folder/{folderName}",
                                        arguments = listOf(navArgument("folderName") { type = NavType.StringType })
                                    ) { backStackEntry ->
                                        val folderName = backStackEntry.arguments?.getString("folderName")?.let { android.net.Uri.decode(it) } ?: ""
                                        ScreenWrapper(navController = navController, viewModel = viewModel, animatedVisibilityScope = this) {
                                            com.example.ui.screens.FolderContentsBrowser(
                                                folderName = folderName,
                                                viewModel = viewModel,
                                                onBack = { navController.popBackStack() },
                                                hazeState = hazeState,
                                                onItemClick = { item ->
                                                    viewModel.showDetailItem(item)
                                                    navController.navigate("detail/${item.id}")
                                                },
                                                sharedTransitionScope = this@SharedTransitionLayout,
                                                animatedVisibilityScope = this@composable
                                            )
                                        }
                                    }
                                    composable("profile") {
                                        ScreenWrapper(navController = navController, viewModel = viewModel, animatedVisibilityScope = this) {
                                            ProfileScreen(
                                                viewModel = viewModel,
                                                onNavigateBack = { navController.popBackStack() },
                                                onNavigateToAuth = { if (viewModel.isFirebaseAvailable) navController.navigate("auth") },
                                                onNavigateToLegal = { route -> navController.navigate(route) },
                                                onNavigateToManageStorage = { if (viewModel.isFirebaseAvailable) navController.navigate("manage_storage") },
                                                onNavigateToCategory = { category ->
                                                    viewModel.setFolderFilter(category)
                                                    navController.navigate("home") {
                                                        popUpTo("home") { inclusive = false }
                                                    }
                                                },
                                                onNavigateToSettings = { navController.navigate("settings") },
                                                onNavigateToDevices = { navController.navigate("devices") },
                                                onNavigateToMovieApiKey = { navController.navigate("movie_api_key") }
                                            )
                                        }
                                    }
                                    composable("settings") {
                                        ScreenWrapper(navController = navController, viewModel = viewModel, animatedVisibilityScope = this) {
                                            com.example.ui.screens.SettingsScreen(
                                                viewModel = viewModel,
                                                onNavigateBack = { navController.popBackStack() },
                                                onNavigateToCategory = { category ->
                                                    navController.navigate("settings_category/${category.name}")
                                                },
                                                onNavigateToEdgePanelSettings = { navController.navigate("edge_panel_settings") },
                                                onNavigateToWidgetSettings = { navController.navigate("widget_settings") },
                                                onNavigateToEdgeAnimSettings = { navController.navigate("edge_anim_settings") }
                                            )
                                        }
                                    }
                                    composable(
                                        route = "settings_category/{categoryId}",
                                        arguments = listOf(
                                            navArgument("categoryId") { type = NavType.StringType }
                                        )
                                    ) { backStackEntry ->
                                        val categoryId = backStackEntry.arguments?.getString("categoryId")
                                        val category = SettingsCategory.entries.find { it.name == categoryId } ?: SettingsCategory.APPEARANCE
                                        ScreenWrapper(navController = navController, viewModel = viewModel, animatedVisibilityScope = this) {
                                            com.example.ui.screens.SettingsCategoryDetailScreen(
                                                category = category,
                                                viewModel = viewModel,
                                                onNavigateBack = { navController.popBackStack() },
                                                onNavigateToEdgePanelSettings = { navController.navigate("edge_panel_settings") },
                                                onNavigateToWidgetSettings = { navController.navigate("widget_settings") },
                                                onNavigateToEdgeAnimSettings = { navController.navigate("edge_anim_settings") }
                                            )
                                        }
                                    }
                                    composable("devices") {
                                        ScreenWrapper(navController = navController, viewModel = viewModel, animatedVisibilityScope = this) {
                                            com.example.ui.screens.DevicesScreen(
                                                viewModel = viewModel,
                                                onNavigateBack = { navController.popBackStack() }
                                            )
                                        }
                                    }
                                    composable("movie_api_key") {
                                        ScreenWrapper(navController = navController, viewModel = viewModel, animatedVisibilityScope = this) {
                                            com.example.ui.screens.MovieApiKeyScreen(
                                                viewModel = viewModel,
                                                onNavigateBack = { navController.popBackStack() }
                                            )
                                        }
                                    }
                                    composable("edge_panel_settings") {
                                        ScreenWrapper(navController = navController, viewModel = viewModel, animatedVisibilityScope = this) {
                                            com.example.ui.screens.EdgePanelSettingsScreen(
                                                viewModel = viewModel,
                                                onNavigateBack = { navController.popBackStack() }
                                            )
                                        }
                                    }
                                    composable("widget_settings") {
                                        ScreenWrapper(navController = navController, viewModel = viewModel, animatedVisibilityScope = this) {
                                            com.example.ui.screens.WidgetSettingsScreen(
                                                viewModel = viewModel,
                                                onNavigateBack = { navController.popBackStack() }
                                            )
                                        }
                                    }
                                    composable("edge_anim_settings") {
                                        ScreenWrapper(navController = navController, viewModel = viewModel, animatedVisibilityScope = this) {
                                             com.example.ui.screens.EdgePanelAnimationSettingsScreen(
                                                 viewModel = viewModel,
                                                 onNavigateBack = { navController.popBackStack() }
                                             )
                                        }
                                    }
                                    composable("auth") {
                                        ScreenWrapper(navController = navController, viewModel = viewModel, animatedVisibilityScope = this) {
                                            AuthScreen(
                                                viewModel = viewModel,
                                                onNavigateBack = { navController.popBackStack() },
                                                onNavigateToLegal = { route -> navController.navigate(route) }
                                            )
                                        }
                                    }
                                    composable("privacy") {
                                        ScreenWrapper(navController = navController, viewModel = viewModel, animatedVisibilityScope = this) {
                                            com.example.ui.screens.LegalScreen(
                                                title = "Privacy Policy",
                                                markdownContent = com.example.ui.screens.LegalDocs.privacyPolicy,
                                                onBack = { navController.popBackStack() },
                                                iconRes = com.example.R.drawable.ic_custom_lock,
                                                subtitle = "DATA PROTECTION & PRIVACY"
                                            )
                                        }
                                    }
                                    composable("terms") {
                                        ScreenWrapper(navController = navController, viewModel = viewModel, animatedVisibilityScope = this) {
                                            com.example.ui.screens.LegalScreen(
                                                title = "Terms & Conditions",
                                                markdownContent = com.example.ui.screens.LegalDocs.termsOfConditions,
                                                onBack = { navController.popBackStack() },
                                                iconRes = com.example.R.drawable.ic_custom_terms,
                                                subtitle = "GNU AGPL V3 LICENSE"
                                            )
                                        }
                                    }
                                    composable("faq") {
                                        ScreenWrapper(navController = navController, viewModel = viewModel, animatedVisibilityScope = this) {
                                            com.example.ui.screens.FaqScreen(
                                                markdownContent = com.example.ui.screens.LegalDocs.faq,
                                                onBack = { navController.popBackStack() }
                                            )
                                        }
                                    }
                                    composable("manage_storage") {
                                        ScreenWrapper(navController = navController, viewModel = viewModel, animatedVisibilityScope = this) {
                                            com.example.ui.screens.ManageStorageScreen(
                                                onNavigateBack = { navController.popBackStack() },
                                                viewModel = viewModel
                                            )
                                        }
                                    }
                                    composable("release_updates") {
                                        ScreenWrapper(navController = navController, viewModel = viewModel, animatedVisibilityScope = this) {
                                            com.example.ui.screens.ReleaseUpdatesScreen(
                                                onNavigateBack = { navController.popBackStack() }
                                            )
                                        }
                                    }
                                    composable("support") {
                                        ScreenWrapper(navController = navController, viewModel = viewModel, animatedVisibilityScope = this) {
                                            com.example.ui.screens.SupportScreen(
                                                viewModel = viewModel,
                                                onNavigateBack = { navController.popBackStack() },
                                                onNavigateToFeedback = { tabIndex -> navController.navigate("feedback?tab=$tabIndex") }
                                            )
                                        }
                                    }
                                    composable("feedback") {
                                        ScreenWrapper(navController = navController, viewModel = viewModel, animatedVisibilityScope = this) {
                                            com.example.ui.screens.FeedbackScreen(
                                                viewModel = viewModel,
                                                onNavigateBack = { navController.popBackStack() }
                                            )
                                        }
                                    }
                                    composable(
                                        route = "feedback?tab={tab}",
                                        arguments = listOf(
                                            androidx.navigation.navArgument("tab") {
                                                defaultValue = 0
                                                type = androidx.navigation.NavType.IntType
                                            }
                                        )
                                    ) { backStackEntry ->
                                        val tab = backStackEntry.arguments?.getInt("tab") ?: 0
                                        ScreenWrapper(navController = navController, viewModel = viewModel, animatedVisibilityScope = this) {
                                            com.example.ui.screens.FeedbackScreen(
                                                viewModel = viewModel,
                                                initialTab = tab,
                                                onNavigateBack = { navController.popBackStack() }
                                            )
                                        }
                                    }
                                    composable("about") {
                                        ScreenWrapper(navController = navController, viewModel = viewModel, animatedVisibilityScope = this) {
                                            com.example.ui.screens.LegalScreen(
                                                title = "About",
                                                markdownContent = com.example.ui.screens.LegalDocs.about,
                                                onBack = { navController.popBackStack() },
                                                iconRes = com.example.R.drawable.ic_custom_info,
                                                subtitle = "UNIVERSAL CAPTURE & ARCHIVE"
                                            )
                                        }
                                    }
                                    composable("media_hub") {
                                        ScreenWrapper(navController = navController, viewModel = viewModel, animatedVisibilityScope = this) {
                                            com.example.ui.screens.MediaHubScreen(
                                                viewModel = viewModel,
                                                onMediaClick = { item ->
                                                    viewModel.showDetailItem(item)
                                                    navController.navigate("detail/${item.id}")
                                                },
                                                sharedTransitionScope = this@SharedTransitionLayout,
                                                animatedVisibilityScope = this@composable
                                            )
                                        }
                                    }
                                    composable(
                                        route = "detail/{itemId}",
                                        arguments = listOf(navArgument("itemId") { type = NavType.StringType })
                                    ) { backStackEntry ->
                                        val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
                                        ScreenWrapper(navController = navController, viewModel = viewModel, animatedVisibilityScope = this) {
                                            DetailScreen(
                                                viewModel = viewModel,
                                                itemId = itemId,
                                                onClose = {
                                                    if (navController.currentDestination?.route?.startsWith("detail") == true) {
                                                        navController.popBackStack()
                                                    }
                                                    viewModel.closeDetailItem()
                                                },
                                                onEdit = { item ->
                                                    viewModel.startEditItem(item)
                                                },
                                                hazeState = hazeState,
                                                sharedTransitionScope = this@SharedTransitionLayout,
                                                animatedVisibilityScope = this@composable
                                            )
                                        }
                                    }
                                 }
                            }
                        }

                        if (captureBlurFraction > 0.001f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = captureBlurFraction * 0.45f }
                                    .background(Color.Black)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        viewModel.cancelCapture()
                                    }
                            )
                        }

                        // Sliding overlay for the capture screen - smooth M3 deceleration and fade
                        AnimatedVisibility(
                            visible = activeCaptureItem != null,
                            enter = slideInVertically(
                                initialOffsetY = { (it * 0.35f).toInt() },
                                animationSpec = tween(durationMillis = 350, easing = EmphasizedDecelerateEasing)
                            ) + scaleIn(
                                initialScale = 0.94f,
                                animationSpec = tween(durationMillis = 350, easing = EmphasizedDecelerateEasing)
                            ) + fadeIn(
                                animationSpec = tween(durationMillis = 250, easing = LinearEasing)
                            ),
                            exit = slideOutVertically(
                                targetOffsetY = { (it * 0.35f).toInt() },
                                animationSpec = tween(durationMillis = 280, easing = EmphasizedAccelerateEasing)
                            ) + scaleOut(
                                targetScale = 0.94f,
                                animationSpec = tween(durationMillis = 280, easing = EmphasizedAccelerateEasing)
                            ) + fadeOut(
                                animationSpec = tween(durationMillis = 200, easing = LinearEasing)
                            )
                        ) {
                            CaptureScreen(viewModel = viewModel)
                        }

                        // Floating CustomBottomBar Overlay
                        val routesWithBottomBar = listOf("home", "search", "folders", "profile", "media_hub")
                        if (currentRoute in routesWithBottomBar && activeCaptureItem == null && activeDetailItem == null) {
                            val isFoss = BuildConfig.FLAVOR == "foss"
                            val items = listOf(
                                BottomBarItem("home", R.drawable.ic_nav_home_outline, "Home", selectedIconResId = R.drawable.ic_nav_home_bold),
                                BottomBarItem("search", R.drawable.ic_nav_search_outline, "Search", selectedIconResId = R.drawable.ic_nav_search_bold),
                                BottomBarItem("media_hub", R.drawable.ic_nav_media_outline, "Media", selectedIconResId = R.drawable.ic_nav_media_bold),
                                BottomBarItem("folders", R.drawable.ic_nav_folders_outline, "Folders", selectedIconResId = R.drawable.ic_nav_folders_bold),
                                if (isFoss) {
                                    BottomBarItem("profile", R.drawable.ic_nav_settings_outline, "Settings", selectedIconResId = R.drawable.ic_nav_settings_bold)
                                } else {
                                    BottomBarItem("profile", R.drawable.ic_nav_profile_outline, "Profile", selectedIconResId = R.drawable.ic_nav_profile_bold)
                                }
                            )
                            val navInsets = androidx.compose.foundation.layout.WindowInsets.navigationBars
                            val bottomPadding = navInsets.asPaddingValues().calculateBottomPadding()
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .padding(bottom = bottomPadding)
                            ) {
                                CustomBottomBar(
                                    items = items,
                                    currentRoute = currentRoute,
                                    hazeState = hazeState,
                                    viewModel = viewModel,
                                    onNavigate = { route ->
                                        if (currentRoute == "home" && route == "media_hub") {
                                            com.example.ui.screens.TransitionCoordinator.requestScrollHalfVisibleHomeItem?.invoke()
                                        }
                                        navController.navigate(route) {
                                            popUpTo("home") {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }

                            val routesWithExpandingFab = listOf("home", "search", "profile")
                            if (currentRoute in routesWithExpandingFab) {
                                com.example.ui.components.GlobalExpandingFab(viewModel = viewModel, hazeState = hazeState)
                            }
                            com.example.ui.components.MediaSearchBottomSheet(viewModel = viewModel)
                        }

                        val showProactiveSurvey by viewModel.showProactiveSurvey.collectAsState()
                        if (showProactiveSurvey) {
                            com.example.ui.components.SurveyBottomSheet(
                                viewModel = viewModel,
                                onDismissRequest = { viewModel.dismissProactiveSurvey() }
                            )
                        }

                        val userFeedback by viewModel.userFeedback.collectAsState()
                        com.example.ui.components.AppFeedbackBanner(
                            feedbackMessage = userFeedback,
                            onDismiss = { viewModel.dismissFeedback() },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                        )
                    }
                }
            }
        }
    }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
        pendingNavDestination.value = intent.getStringExtra("NAVIGATE_TO")
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        val openItemId = intent.getStringExtra("OPEN_ITEM_ID")
        if (openItemId != null) {
            viewModel.openItemById(openItemId)
        }

        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type != null) {
            val textContent = intent.getStringExtra(Intent.EXTRA_TEXT)
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            val mediaUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            viewModel.handleSharedIntent(type, textContent, mediaUri, subject)
        } else if (Intent.ACTION_VIEW == action) {
            val data: Uri? = intent.data
            val linkStr = data?.toString()
            if (linkStr != null) {
                if (linkStr.startsWith("cobalt://item/")) {
                    val itemId = linkStr.substringAfter("cobalt://item/")
                    viewModel.openItemById(itemId)
                } else {
                    viewModel.handleDeepLink(linkStr)
                }
            }
        } else if ("com.example.ACTION_QUICK_TEXT" == action) {
            viewModel.startManualCapture(com.example.data.model.SavedItemType.TEXT)
        } else if ("com.example.ACTION_QUICK_LINK" == action) {
            viewModel.startManualCapture(com.example.data.model.SavedItemType.LINK)
        } else if ("com.example.ACTION_QUICK_IMAGE" == action) {
            viewModel.startManualCapture(com.example.data.model.SavedItemType.IMAGE)
        } else if ("com.example.ACTION_QUICK_CODE" == action) {
            viewModel.startManualCapture(com.example.data.model.SavedItemType.CODE)
        } else if ("com.example.ACTION_QUICK_AUDIO" == action) {
            viewModel.startManualCapture(com.example.data.model.SavedItemType.AUDIO)
        } else if ("com.example.ACTION_QUICK_MEDIA" == action) {
            viewModel.openMediaSearchSheet()
        } else if ("com.example.ACTION_QUICK_OCR" == action) {
            val ocrIntent = Intent(this, OcrCaptureActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(ocrIntent)
        }
    }
}

private fun logScreenView(context: android.content.Context, screenName: String) {
    try {
        val clazz = Class.forName("com.google.firebase.analytics.FirebaseAnalytics")
        val instance = clazz.getMethod("getInstance", android.content.Context::class.java).invoke(null, context)
        val bundle = android.os.Bundle().apply {
            putString("screen_name", screenName)
            putString("screen_class", "MainActivity")
        }
        clazz.getMethod("logEvent", String::class.java, android.os.Bundle::class.java).invoke(instance, "screen_view", bundle)
    } catch (e: Throwable) {
        // Analytics unavailable or disabled in FOSS build
    }
}
