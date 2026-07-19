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

package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.components.bounceClick
import com.example.ui.viewmodel.SecondBrainViewModel

enum class ProfileSubScreen {
    MAIN, SETTINGS, DEVICES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: SecondBrainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToLegal: (String) -> Unit = {},
    onNavigateToManageStorage: () -> Unit = {}
) {
    var currentScreen by remember { mutableStateOf(ProfileSubScreen.MAIN) }

    BackHandler(enabled = currentScreen != ProfileSubScreen.MAIN) {
        currentScreen = ProfileSubScreen.MAIN
    }

    when (currentScreen) {
        ProfileSubScreen.MAIN -> {
            ProfileMainContent(
                viewModel = viewModel,
                onNavigateBack = onNavigateBack,
                onNavigateToAuth = onNavigateToAuth,
                onNavigateToLegal = onNavigateToLegal,
                onNavigateToSettings = { currentScreen = ProfileSubScreen.SETTINGS },
                onNavigateToDevices = { currentScreen = ProfileSubScreen.DEVICES },
                onNavigateToManageStorage = onNavigateToManageStorage
            )
        }
        ProfileSubScreen.SETTINGS -> {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { currentScreen = ProfileSubScreen.MAIN }
            )
        }
        ProfileSubScreen.DEVICES -> {
            DevicesScreen(
                viewModel = viewModel,
                onNavigateBack = { currentScreen = ProfileSubScreen.MAIN }
            )
        }
    }
}

