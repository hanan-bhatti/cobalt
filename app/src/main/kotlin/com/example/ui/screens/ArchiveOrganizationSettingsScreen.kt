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

package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.ui.components.CollapsibleCommonTopBar
import com.example.ui.components.ExpressiveSettingsGroup
import com.example.ui.components.ExpressiveSettingsSelectorItem
import com.example.ui.components.ExpressiveSettingsSwitchItem
import com.example.ui.components.SettingsSubsectionHeader
import com.example.ui.components.rememberCollapsibleTopBarState
import com.example.ui.viewmodel.CobaltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveOrganizationSettingsScreen(
    viewModel: CobaltViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isFolderRankingEnabled by viewModel.settingsRepository.isFolderRankingEnabled.collectAsState()
    val profileArchiveStyle by viewModel.settingsRepository.profileArchiveStyle.collectAsState()

    val lazyListState = rememberLazyListState()
    val topBarState = rememberCollapsibleTopBarState(lazyListState, maxHeight = 160.dp)
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(topBarState.nestedScrollConnection)
    ) {
        val currentTopBarHeightDp = with(density) { topBarState.heightPx.toDp() }
        val bottomNavPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(
                top = currentTopBarHeightDp + 12.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = bottomNavPadding + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Subsection 1: Smart Folder Ranking
            item {
                SettingsSubsectionHeader(
                    title = "Smart Folder Ranking",
                    description = "Prioritize frequently used folders in capture rows and dialogs.",
                    tooltipText = "When enabled, folders in capture rows and move dialogs are dynamically sorted based on your recent activity, folder assignment frequency, and content affinity."
                )

                ExpressiveSettingsGroup {
                    ExpressiveSettingsSwitchItem(
                        title = "Smart Folder Ranking",
                        subtitle = "Adaptive suggestions & usage-based sorting",
                        checked = isFolderRankingEnabled,
                        onCheckedChange = { viewModel.settingsRepository.setFolderRankingEnabled(it) },
                        shape = RoundedCornerShape(24.dp)
                    )
                }
            }

            // Subsection 2: Profile Archive Display Style
            item {
                SettingsSubsectionHeader(
                    title = "Profile Archive Display Style",
                    description = "Choose how your archive is visualised on the profile screen.",
                    tooltipText = "Switch between category counter tiles and an interactive GitHub-style contribution heatmap."
                )

                val archiveStyleOptions = mapOf(
                    "pills" to "Category Piles Grid",
                    "heatmap" to "GitHub-Style Activity Heatmap"
                )

                ExpressiveSettingsGroup {
                    ExpressiveSettingsSelectorItem(
                        title = "Archive Display Style",
                        description = if (profileArchiveStyle == "heatmap") "GitHub-Style Activity Heatmap" else "Category Piles Grid",
                        options = archiveStyleOptions,
                        selectedKey = profileArchiveStyle,
                        onSelectionChanged = { viewModel.settingsRepository.setProfileArchiveStyle(it) },
                        shape = RoundedCornerShape(24.dp)
                    )
                }
            }
        }

        CollapsibleCommonTopBar(
            title = "Archive & Folders",
            subtitle = "Smart folder ranking & activity style",
            collapseFraction = topBarState.collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = onNavigateBack
        )
    }
}
