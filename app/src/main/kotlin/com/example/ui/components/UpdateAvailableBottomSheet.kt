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

package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.screens.ExpressiveMarkdown
import com.example.util.AppVersionManager
import com.example.util.ReleaseNote
import com.example.util.formatFileSize

/**
 * Modern, clean Material 3 Bottom Sheet notifying user when a new app version is available.
 * Renders complete Markdown changelog notes without raw text truncation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateAvailableBottomSheet(
    versionName: String,
    releaseName: String = "",
    releaseDate: String = "",
    releaseNotesMarkdown: String = "",
    apkAssets: List<com.example.util.ReleaseApk> = emptyList(),
    releaseUrl: String? = null,
    apkSizeBytes: Long? = null,
    onDismissRequest: () -> Unit,
    onDownloadOpened: () -> Unit = {},
    onViewChangelogClick: () -> Unit = {},
    sheetState: SheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Graphic & Information
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_download),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "New Update Available",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val metadata = listOfNotNull(
                        "v$versionName",
                        releaseDate.ifBlank { null },
                        formatFileSize(apkSizeBytes)
                    ).joinToString(" • ")
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Rich Markdown Changelog Container
            if (releaseNotesMarkdown.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        ExpressiveMarkdown(
                            markdown = releaseNotesMarkdown,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReleaseApkActions(
                    version = versionName, assets = apkAssets, releaseUrl = releaseUrl,
                    onDownloadOpened = { onDismissRequest(); onDownloadOpened() }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onViewChangelogClick()
                        },
                        modifier = Modifier.testTag("view_full_changelog_button")
                    ) {
                        Text("View Full Changelog", color = MaterialTheme.colorScheme.primary)
                    }

                    TextButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.testTag("remind_me_later_button")
                    ) {
                        Text("Remind Me Later", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * Backward compatibility overload for existing ReleaseNote callers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateAvailableBottomSheet(
    latestRelease: ReleaseNote = AppVersionManager.getLatestRelease(),
    onDismissRequest: () -> Unit,
    onDownloadOpened: () -> Unit = {},
    onViewChangelogClick: () -> Unit = {},
    sheetState: SheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
) {
    val markdown = latestRelease.markdownContent.ifBlank {
        latestRelease.highlights.joinToString("\n") { "- $it" }
    }
    UpdateAvailableBottomSheet(
        versionName = latestRelease.versionName,
        releaseName = "Cobalt v${latestRelease.versionName}",
        releaseDate = latestRelease.releaseDate,
        releaseNotesMarkdown = markdown,
        apkAssets = latestRelease.apkAssets,
        apkSizeBytes = latestRelease.downloadSizeBytes,
        onDismissRequest = onDismissRequest,
        onDownloadOpened = onDownloadOpened,
        onViewChangelogClick = onViewChangelogClick,
        sheetState = sheetState
    )
}
