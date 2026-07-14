package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CaptureScreen
import com.example.ui.screens.DetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.SecondBrainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: SecondBrainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Auto-start Floating OCR service if enabled and allowed
        val settingsRepo = com.example.data.repository.SettingsRepository(applicationContext)
        if (settingsRepo.isFloatingOcrEnabled.value && com.example.utils.PermissionUtils.hasOverlayPermission(applicationContext)) {
            val serviceIntent = Intent(applicationContext, BrainOcrOverlayService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
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

                BackHandler(enabled = activeCaptureItem != null) {
                    viewModel.cancelCapture()
                }

                BackHandler(enabled = activeDetailItem != null) {
                    viewModel.closeDetailItem()
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        @OptIn(ExperimentalSharedTransitionApi::class)
                        SharedTransitionLayout {
                            Surface(modifier = Modifier.fillMaxSize()) {
                                // Standard Home and Authentication navigation flow
                                NavHost(navController = navController, startDestination = "home") {
                                    composable("home") {
                                        HomeScreen(
                                            viewModel = viewModel,
                                            onNavigateToProfile = { navController.navigate("profile") },
                                            sharedTransitionScope = this@SharedTransitionLayout,
                                            animatedVisibilityScope = this@composable
                                        )
                                    }
                                    composable("profile") {
                                        ProfileScreen(
                                            viewModel = viewModel,
                                            onNavigateBack = { navController.popBackStack() },
                                            onNavigateToAuth = { navController.navigate("auth") },
                                            onNavigateToLegal = { route -> navController.navigate(route) }
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
                                        com.example.ui.screens.LegalScreen(
                                            title = "FAQ",
                                            markdownContent = com.example.ui.screens.LegalDocs.faq,
                                            onBack = { navController.popBackStack() }
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
                                    enter = slideInVertically(initialOffsetY = { it }),
                                    exit = slideOutVertically(targetOffsetY = { it })
                                ) {
                                    CaptureScreen(viewModel = viewModel)
                                }

                                // Sliding overlay for the memory detail view screen
                                AnimatedVisibility(
                                    visible = activeDetailItem != null,
                                    enter = slideInVertically(initialOffsetY = { it }),
                                    exit = slideOutVertically(targetOffsetY = { it })
                                ) {
                                    DetailScreen(
                                        viewModel = viewModel,
                                        onClose = { viewModel.closeDetailItem() },
                                        onEdit = { item ->
                                            viewModel.closeDetailItem()
                                            viewModel.startEditItem(item)
                                        }
                                    )
                                }
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
            val mediaUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
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
