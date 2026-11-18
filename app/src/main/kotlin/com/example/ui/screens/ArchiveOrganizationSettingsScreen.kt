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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.CollapsibleCommonTopBar
import com.example.ui.components.ExpressiveSettingsGroup
import com.example.ui.components.ExpressiveSettingsNavigationItem
import com.example.ui.components.ExpressiveSettingsSelectorItem
import com.example.ui.components.ExpressiveSettingsSwitchItem
import com.example.ui.components.SettingsChoice
import com.example.ui.components.SettingsChoiceSheet
import com.example.ui.components.SettingsGroupLabel
import com.example.ui.components.SettingsIconBadge
import com.example.ui.components.connectedPillShape
import com.example.ui.components.rememberCollapsibleTopBarState
import com.example.ui.viewmodel.CobaltViewModel

@Composable
fun ArchiveOrganizationSettingsScreen(
    viewModel: CobaltViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = viewModel.settingsRepository
    val folderSuggestionsEnabled by repository.isFolderRankingEnabled.collectAsState()
    val archiveStyle by repository.profileArchiveStyle.collectAsState()

    NotesAndFoldersSettingsContent(
        folderSuggestionsEnabled = folderSuggestionsEnabled,
        archiveStyle = archiveStyle,
        onFolderSuggestionsChanged = repository::setFolderRankingEnabled,
        onArchiveStyleChanged = repository::setProfileArchiveStyle,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

/** Uses the existing Settings route and top bar; navigation owns predictive Back. */
@Composable
internal fun NotesAndFoldersSettingsContent(
    folderSuggestionsEnabled: Boolean,
    archiveStyle: String,
    onFolderSuggestionsChanged: (Boolean) -> Unit,
    onArchiveStyleChanged: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSuggestionsHelp by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val topBarState = rememberCollapsibleTopBarState(listState, maxHeight = 160.dp)
    val topPadding = with(LocalDensity.current) { topBarState.heightPx.toDp() }
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val archiveTitle = stringResource(R.string.settings_notes_archive_view)
    val archiveDescription = stringResource(R.string.settings_notes_archive_sheet_description)
    val archiveChoices = listOf(
        SettingsChoice(
            "heatmap", stringResource(R.string.settings_archive_heatmap_option),
            stringResource(R.string.settings_notes_archive_heatmap_description), R.drawable.ic_archive_heatmap
        ),
        SettingsChoice(
            "pills", stringResource(R.string.settings_archive_categories_option),
            stringResource(R.string.settings_notes_archive_categories_description), R.drawable.ic_archive_categories
        )
    )

    Box(
        modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(topBarState.nestedScrollConnection)
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = topPadding + 12.dp,
                start = 20.dp,
                end = 20.dp,
                bottom = bottomPadding + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize().testTag("notes_folders_settings_list")
        ) {
            item(key = "folder_suggestions") {
                Column {
                    SettingsGroupLabel(stringResource(R.string.settings_folder_suggestions))
                    FolderOrderPreview(folderSuggestionsEnabled)
                    Spacer(Modifier.height(12.dp))
                    ExpressiveSettingsGroup {
                        ExpressiveSettingsSwitchItem(
                            title = stringResource(R.string.settings_use_folder_suggestions),
                            subtitle = stringResource(R.string.settings_folder_suggestions_description),
                            checked = folderSuggestionsEnabled,
                            onCheckedChange = onFolderSuggestionsChanged,
                            shape = connectedPillShape(0, 2), compact = true
                        )
                        ExpressiveSettingsNavigationItem(
                            title = stringResource(R.string.settings_folder_suggestions_how),
                            subtitle = stringResource(R.string.settings_folder_suggestions_how_summary),
                            leadingIcon = { SettingsIconBadge(R.drawable.ic_custom_info) },
                            onClick = { showSuggestionsHelp = true },
                            shape = connectedPillShape(1, 2), compact = true
                        )
                    }
                }
            }
            item(key = "settings_archive") {
                Column {
                    SettingsGroupLabel(stringResource(R.string.settings_notes_archive_section))
                    ExpressiveSettingsSelectorItem(
                        title = archiveTitle,
                        description = stringResource(R.string.settings_notes_archive_location),
                        options = archiveChoices.associate { it.key to it.label },
                        selectedKey = archiveStyle,
                        onSelectionChanged = onArchiveStyleChanged,
                        leadingIcon = {
                            SettingsIconBadge(if (archiveStyle == "heatmap") R.drawable.ic_archive_heatmap else R.drawable.ic_archive_categories)
                        },
                        shape = RoundedCornerShape(24.dp), compact = true,
                        selectedValueInDescription = true,
                        sheetContent = { dismiss ->
                            SettingsChoiceSheet(
                                archiveTitle, archiveDescription, archiveChoices, archiveStyle,
                                onArchiveStyleChanged, dismiss
                            )
                        }
                    )
                }
            }
        }

        CollapsibleCommonTopBar(
            title = stringResource(R.string.settings_notes_folders),
            subtitle = stringResource(R.string.settings_notes_folders_subtitle),
            collapseFraction = topBarState.collapseFraction,
            headerHeight = topPadding,
            onBackClick = onNavigateBack
        )
    }
    if (showSuggestionsHelp) {
        FolderSuggestionsHelpSheet(onDismiss = { showSuggestionsHelp = false })
    }
}
