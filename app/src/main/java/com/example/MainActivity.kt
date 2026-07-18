/*
 * Second Brain - A universal capture and personal knowledge archive
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.ProfileScreen
import androidx.compose.runtime.DisposableEffect
import com.google.firebase.analytics.FirebaseAnalytics
import androidx.navigation.NavController
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CaptureScreen
import com.example.ui.screens.DetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.FoldersScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.SecondBrainViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import com.example.ui.components.CustomBottomBar
import com.example.ui.components.BottomBarItem
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
    private val viewModel: SecondBrainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Auto-start Floating OCR service if enabled and allowed
        val settingsRepo = com.example.data.repository.SettingsRepository(applicationContext)
        if (settingsRepo.isFloatingOcrEnabled.value && com.example.utils.PermissionUtils.hasOverlayPermission(applicationContext)) {
            val serviceIntent = Intent(applicationContext, BrainOcrOverlayService::class.java)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } catch (e: Exception) {
                android.util.Log.e("SecondBrain", "Failed to start OCR overlay service: ${e.message}", e)
            }
        }

        // Handle shared intent if starting via share-sheet capture
        handleIntent(intent)

        setContent {
            val themeMode by viewModel.settingsRepository.themeMode.collectAsState()
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
            MyApplicationTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()
                val activeCaptureItem by viewModel.activeCaptureItem.collectAsState()
                val activeDetailItem by viewModel.activeDetailItem.collectAsState()

                val snackbarHostState = remember { SnackbarHostState() }
                val uiToast by viewModel.uiToast.collectAsState()
                LaunchedEffect(uiToast) {
                    uiToast?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.clearUiToast()
                    }
                }

BackHandler(enabled = activeDetailItem != null) {
                    viewModel.closeDetailItem()
                }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val hazeState = remember { HazeState() }

                val context = androidx.compose.ui.platform.LocalContext.current
                DisposableEffect(navController) {
                    val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                        val route = destination.route ?: return@OnDestinationChangedListener
                        val bundle = Bundle().apply {
                            putString(FirebaseAnalytics.Param.SCREEN_NAME, route)
                            putString(FirebaseAnalytics.Param.SCREEN_CLASS, "MainActivity")
                        }
                        FirebaseAnalytics.getInstance(context).logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
                    }
                    navController.addOnDestinationChangedListener(listener)
                    onDispose {
                        navController.removeOnDestinationChangedListener(listener)
                    }
                }

                LaunchedEffect(activeCaptureItem) {
                    if (activeCaptureItem != null) {
                        val bundle = Bundle().apply {
                            putString(FirebaseAnalytics.Param.SCREEN_NAME, "note_editor")
                            putString(FirebaseAnalytics.Param.SCREEN_CLASS, "MainActivity")
                        }
                        FirebaseAnalytics.getInstance(context).logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
                    }
                }

                LaunchedEffect(activeDetailItem) {
                    if (activeDetailItem != null) {
                        val bundle = Bundle().apply {
                            putString(FirebaseAnalytics.Param.SCREEN_NAME, "note_detail")
                            putString(FirebaseAnalytics.Param.SCREEN_CLASS, "MainActivity")
                        }
                        FirebaseAnalytics.getInstance(context).logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        @OptIn(ExperimentalSharedTransitionApi::class)
                        SharedTransitionLayout {
                            Surface(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
                                 // Standard Home and Authentication navigation flow
                                 NavHost(
                                     navController = navController,
                                     startDestination = "home",
                                     enterTransition = {
                                         slideIntoContainer(
                                             towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                             animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                         ) + fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                                     },
                                     exitTransition = {
                                         slideOutOfContainer(
                                             towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                             animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                         ) + fadeOut(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                                     },
                                     popEnterTransition = {
                                         slideIntoContainer(
                                             towards = AnimatedContentTransitionScope.SlideDirection.End,
                                             animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                         ) + fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                                     },
                                     popExitTransition = {
                                         slideOutOfContainer(
                                             towards = AnimatedContentTransitionScope.SlideDirection.End,
                                             animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                         ) + fadeOut(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                                     }
                                 ) {
                                    composable("home") {
                                        HomeScreen(
                                            onNavigateToSearch = { navController.navigate("search") },
                                            viewModel = viewModel,
                                            onNavigateToProfile = { navController.navigate("profile") },
                                            sharedTransitionScope = this@SharedTransitionLayout,
                                            animatedVisibilityScope = this@composable
                                        )
                                    }
                                    composable("search") {
                                        com.example.ui.screens.SearchScreen(
                                            viewModel = viewModel,
                                            onNavigateBack = { navController.popBackStack() },
                                            onItemClick = { item -> viewModel.showDetailItem(item) }
                                        )
                                    }
                                     composable("folders") {
                                        FoldersScreen(
                                            viewModel = viewModel,
                                            hazeState = hazeState
                                        )
                                    }
                                    composable("profile") {
                                        ProfileScreen(
                                            viewModel = viewModel,
                                            onNavigateBack = { navController.popBackStack() },
                                            onNavigateToAuth = { navController.navigate("auth") },
                                            onNavigateToLegal = { route -> navController.navigate(route) },
                                            onNavigateToManageStorage = { navController.navigate("manage_storage") }
                                        )
                                    }
                                    composable("auth") {
                                        AuthScreen(
                                            viewModel = viewModel,
                                            onNavigateBack = { navController.popBackStack() },
                                            onNavigateToLegal = { route -> navController.navigate(route) }
                                        )
                                    }
                                    composable("privacy") {
                                        com.example.ui.screens.LegalScreen(
                                            title = "Privacy Policy",
                                            markdownContent = com.example.ui.screens.LegalDocs.privacyPolicy,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }
                                    composable("terms") {
                                        com.example.ui.screens.LegalScreen(
                                            title = "Terms & Conditions",
                                            markdownContent = com.example.ui.screens.LegalDocs.termsOfConditions,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }
                                    composable("faq") {
                                        com.example.ui.screens.FaqScreen(

                                            markdownContent = com.example.ui.screens.LegalDocs.faq,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }
                                    composable("manage_storage") {
                                        com.example.ui.screens.ManageStorageScreen(
                                            onNavigateBack = { navController.popBackStack() },
                                            viewModel = viewModel
                                        )
                                    }
                                    composable("about") {
                                        com.example.ui.screens.LegalScreen(
                                            title = "About",
                                            markdownContent = com.example.ui.screens.LegalDocs.about,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }
                                }

                                 // Sliding overlay for the capture screen - mimics Apple's native share action sheet
                                 AnimatedVisibility(
                                     visible = activeCaptureItem != null,
                                     enter = slideInVertically(
                                         initialOffsetY = { it },
                                         animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                                     ) + scaleIn(
                                         initialScale = 0.9f,
                                         animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                                     ) + fadeIn(
                                         animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                     ),
                                     exit = slideOutVertically(
                                         targetOffsetY = { it },
                                         animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                     ) + scaleOut(
                                         targetScale = 0.9f,
                                         animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                     ) + fadeOut(
                                         animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                     )
                                 ) {
                                     CaptureScreen(viewModel = viewModel)
                                 }

                                 // Sliding overlay for the memory detail view screen
                                 AnimatedVisibility(
                                     visible = activeDetailItem != null,
                                     enter = slideInVertically(
                                         initialOffsetY = { it },
                                         animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                                     ) + scaleIn(
                                         initialScale = 0.9f,
                                         animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                                     ) + fadeIn(
                                         animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                     ),
                                     exit = slideOutVertically(
                                         targetOffsetY = { it },
                                         animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                     ) + scaleOut(
                                         targetScale = 0.9f,
                                         animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                     ) + fadeOut(
                                         animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                     )
                                 ) {
                                     DetailScreen(
                                         viewModel = viewModel,
                                         onClose = { viewModel.closeDetailItem() },
                                         onEdit = { item ->
                                             viewModel.closeDetailItem()
                                             viewModel.startEditItem(item)
                                         },
                                         hazeState = hazeState
                                     )
                                }
                            }
                        }

                        // Floating CustomBottomBar Overlay
                        val routesWithBottomBar = listOf("home", "search", "folders", "profile")
                        if (currentRoute in routesWithBottomBar && activeCaptureItem == null && activeDetailItem == null) {
                            val items = listOf(
                                BottomBarItem("home", R.drawable.ic_custom_home, "Home"),
                                BottomBarItem("search", R.drawable.ic_custom_search, "Search"),
                                BottomBarItem("folders", R.drawable.ic_custom_folder, "Folders"),
                                BottomBarItem("profile", R.drawable.ic_custom_profile, "Profile")
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                            ) {
                                CustomBottomBar(
                                    items = items,
                                    currentRoute = currentRoute,
                                    hazeState = hazeState,
                                    onNavigate = { route ->
                                        navController.navigate(route) {
                                            popUpTo("home") {
                                                if (route == "home") {
                                                    inclusive = true
                                                }
                                            }
                                        }
                                    }
                                )
                            }

                            val routesWithExpandingFab = listOf("home", "search", "profile")
                            if (currentRoute in routesWithExpandingFab) {
                                com.example.ui.components.GlobalExpandingFab(viewModel = viewModel, hazeState = hazeState)
                            }
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
                if (linkStr.startsWith("secondbrain://item/")) {
                    val itemId = linkStr.substringAfter("secondbrain://item/")
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
        }
    }
}
