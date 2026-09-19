package moe.ouom.neriplayer.ui.screen.tab.settings.about

/*
 * NeriPlayer - A unified Android player for streaming music and videos from multiple online platforms.
 * Copyright (C) 2025-2025 NeriPlayer developers
 * https://github.com/cwuom/NeriPlayer
 *
 * This software is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * File: moe.ouom.neriplayer.ui.screen.tab.settings.about/SettingsAboutSection
 * Updated: 2026/3/23
 */

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.ouom.neriplayer.BuildConfig
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.ui.screen.tab.settings.component.settingsItemClickable
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsDialog
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsDialogContent
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsTextButton
import moe.ouom.neriplayer.util.format.convertTimestampToDate

internal fun LazyListScope.settingsAboutSection(
    devModeEnabled: Boolean,
    onVersionClick: () -> Unit,
    onCopyValue: (String) -> Unit = {},
    onOpenGitHubRepo: () -> Unit
) {
    item {
        SettingsAboutContent(
            devModeEnabled = devModeEnabled,
            onVersionClick = onVersionClick,
            onCopyValue = onCopyValue,
            onOpenGitHubRepo = onOpenGitHubRepo
        )
    }
}

@Composable
internal fun SettingsAboutContent(
    devModeEnabled: Boolean,
    onVersionClick: () -> Unit,
    onCopyValue: (String) -> Unit,
    onOpenGitHubRepo: () -> Unit
) {
    SettingsAboutIntroItem()
    SettingsOriginalAuthorItem()
    SettingsAboutUpdateItem()
    SettingsBuildUuidItem(onCopyValue)
    SettingsVersionItem(
        devModeEnabled = devModeEnabled,
        onVersionClick = onVersionClick,
        onCopyValue = onCopyValue
    )
    SettingsBuildTimeItem(onCopyValue)
    SettingsGitHubItem(onOpenGitHubRepo = onOpenGitHubRepo)
}

@Composable
private fun SettingsAboutIntroItem() {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.settings_about),
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        headlineContent = {
            Text(
                text = stringResource(R.string.settings_about),
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = { Text(stringResource(R.string.about_app_footer)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun SettingsOriginalAuthorItem() {
    var showDetailDialog by remember { mutableStateOf(false) }

    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.Verified,
                contentDescription = stringResource(R.string.about_original_author_title),
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        headlineContent = {
            Text(
                text = stringResource(R.string.about_original_author_title),
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = {
            // 列表行保持固定高度的单行摘要；完整声明点开弹窗查看
            Text(
                text = stringResource(R.string.about_original_author_summary),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = stringResource(R.string.about_original_author_detail_hint),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier
            .heightIn(min = 68.dp)
            .settingsItemClickable { showDetailDialog = true },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )

    if (showDetailDialog) {
        MiuixSettingsDialog(
            onDismissRequest = { showDetailDialog = false },
            confirmButton = {
                MiuixSettingsTextButton(onClick = { showDetailDialog = false }) {
                    Text(stringResource(R.string.about_original_author_confirm))
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.about_original_author_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    MiuixSettingsDialogContent {
                        Text(
                            text = stringResource(R.string.about_original_author_desc),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.about_original_author_upstream_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.about_original_author_upstream),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.about_original_author_license_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun SettingsBuildUuidItem(onCopyValue: (String) -> Unit) {
    val buildUuid = BuildConfig.BUILD_UUID

    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.Verified,
                contentDescription = stringResource(R.string.settings_build_uuid),
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        headlineContent = {
            Text(
                text = stringResource(R.string.settings_build_uuid),
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = { Text(buildUuid) },
        modifier = Modifier.settingsItemClickable {
            onCopyValue(buildUuid)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun SettingsVersionItem(
    devModeEnabled: Boolean,
    onVersionClick: () -> Unit,
    onCopyValue: (String) -> Unit
) {
    val suffix = if (devModeEnabled) {
        " (${stringResource(R.string.settings_version_debug_suffix)})"
    } else {
        ""
    }
    val versionName = "${BuildConfig.VERSION_NAME}$suffix"

    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.Update,
                contentDescription = stringResource(R.string.settings_version),
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        headlineContent = {
            Text(
                text = stringResource(R.string.common_version),
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = { Text(versionName) },
        modifier = Modifier.settingsItemClickable {
            onCopyValue(versionName)
            onVersionClick()
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun SettingsBuildTimeItem(onCopyValue: (String) -> Unit) {
    val buildTime = convertTimestampToDate(BuildConfig.BUILD_TIMESTAMP)

    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.Timer,
                contentDescription = stringResource(R.string.settings_build_time),
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        headlineContent = {
            Text(
                text = stringResource(R.string.common_build_time),
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = { Text(buildTime) },
        modifier = Modifier.settingsItemClickable {
            onCopyValue(buildTime)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun SettingsGitHubItem(onOpenGitHubRepo: () -> Unit) {
    ListItem(
        leadingContent = {
            Icon(
                painter = painterResource(id = R.drawable.ic_github),
                contentDescription = stringResource(R.string.common_github),
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        headlineContent = { Text(stringResource(R.string.common_github)) },
        supportingContent = {
            Text(
                text = stringResource(R.string.settings_github_repo_url),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = Modifier
            .heightIn(min = 68.dp)
            .settingsItemClickable(onClick = onOpenGitHubRepo),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
