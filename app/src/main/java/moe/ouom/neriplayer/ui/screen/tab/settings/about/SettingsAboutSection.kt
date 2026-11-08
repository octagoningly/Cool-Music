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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import moe.ouom.neriplayer.util.media.offlineCachedImageRequest
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    onOpenGitHubRepo: () -> Unit,
    onOpenDeveloperProfile: () -> Unit = {}
) {
    // 「开发者」版块：头像 + 昵称 + 主页，点击打开 GitHub 主页。
    SettingsDeveloperSection(onOpenProfile = onOpenDeveloperProfile)
    SettingsAboutIntroItem(
        devModeEnabled = devModeEnabled,
        onVersionClick = onVersionClick,
        onCopyValue = onCopyValue
    )
    SettingsOriginalAuthorItem()
    SettingsAboutUpdateItem()
    SettingsGitHubItem(onOpenGitHubRepo = onOpenGitHubRepo)
}

/** 开发者 GitHub 主页。 */
internal const val DEVELOPER_GITHUB_PROFILE = "https://github.com/octagoningly"
/** 开发者头像（GitHub 官方头像地址）。 */
internal const val DEVELOPER_AVATAR_URL = "https://avatars.githubusercontent.com/u/222370581?v=4"

/**
 * 「开发者」版块：分组标题 + 头像卡片（头像 / 昵称 / 主页地址 / 右侧箭头）。
 * 头像走 Coil（与项目其它页面一致，带磁盘缓存，离线时用已缓存的图）。
 */
@Composable
private fun SettingsDeveloperSection(onOpenProfile: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.about_developer_section),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .clickable(onClick = onOpenProfile)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = offlineCachedImageRequest(
                    context = LocalContext.current,
                    data = DEVELOPER_AVATAR_URL,
                    sizePx = 160,
                    allowHardware = false
                ),
                contentDescription = stringResource(R.string.about_developer_section),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.about_developer_name),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.about_developer_handle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = stringResource(R.string.about_developer_profile_hint),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 关于页顶部大图标头部（图标 + 应用名 + 版本）。
 *
 * 由 MiuixSettingsDetailScaffold 放在列表之上，并随列表上滑逐渐淡出/缩小，
 * 与常见的开源「可折叠关于页」一致。头部内容本身不参与列表滚动。
 */
@Composable
internal fun SettingsAboutHeroHeader(devModeEnabled: Boolean) {
    val suffix = if (devModeEnabled) {
        " (${stringResource(R.string.settings_version_debug_suffix)})"
    } else {
        ""
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 使用应用自己的图标位图。
        // 注意不能用 R.mipmap.ic_launcher：它是 adaptive-icon XML，painterResource 只支持
        // VectorDrawable 或 PNG/JPG/WEBP，传 XML 会抛 IllegalArgumentException。
        // ic_launcher_foreground_coolmusic.webp 正是 adaptive icon 的前景位图，即应用图标本体；
        // 它四周自带约 30% 安全区留白，因此容器取 112dp 再按内容裁剪，视觉上接近 88dp 的实心图标。
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground_coolmusic),
            contentDescription = stringResource(R.string.settings_about),
            modifier = Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(26.dp))
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Text(
            text = "v${BuildConfig.VERSION_NAME}$suffix",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SettingsAboutIntroItem(
    devModeEnabled: Boolean,
    onVersionClick: () -> Unit,
    onCopyValue: (String) -> Unit
) {
    var showBuildInfoDialog by remember { mutableStateOf(false) }
    val suffix = if (devModeEnabled) {
        " (${stringResource(R.string.settings_version_debug_suffix)})"
    } else {
        ""
    }
    val versionName = "${BuildConfig.VERSION_NAME}$suffix"
    val buildUuid = BuildConfig.BUILD_UUID
    val buildTime = convertTimestampToDate(BuildConfig.BUILD_TIMESTAMP)

    // 版本号与应用名已由顶部大图标头部（SettingsAboutHeroHeader）展示，
    // 这里只保留「构建信息」入口，避免重复显示同一份版本信息。
    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.settings_build_info),
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        headlineContent = {
            Text(
                text = stringResource(R.string.settings_build_info),
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = { Text(stringResource(R.string.about_app_footer)) },
        trailingContent = {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier
            .heightIn(min = 68.dp)
            .settingsItemClickable { showBuildInfoDialog = true },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )

    if (showBuildInfoDialog) {
        MiuixSettingsDialog(
            onDismissRequest = { showBuildInfoDialog = false },
            // 约 0.85 倍紧凑尺寸；整体上移约三行字高
            maxWidth = 288.dp,
            maxHeight = 357.dp,
            yOffset = (-48).dp,
            title = {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.settings_about),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 36.dp)
                    )
                    IconButton(
                        onClick = { showBuildInfoDialog = false },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_close)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .padding(start = 2.dp, end = 2.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BuildInfoRow(
                        label = stringResource(R.string.common_version),
                        value = versionName,
                        onClick = {
                            onCopyValue(versionName)
                            onVersionClick()
                        }
                    )
                    BuildInfoRow(
                        label = stringResource(R.string.settings_build_uuid),
                        value = buildUuid,
                        onClick = { onCopyValue(buildUuid) }
                    )
                    BuildInfoRow(
                        label = stringResource(R.string.common_build_time),
                        value = buildTime,
                        onClick = { onCopyValue(buildTime) }
                    )
                }
            },
            confirmButton = {
                // 仅保留右上角 X，避免双重关闭
            }
        )
    }
}

@Composable
private fun BuildInfoRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    // 方框点击区，避免圆角裁切吃掉行首文字
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RectangleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
            maxLines = 2,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
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
            // 尽量一次显示完整声明
            maxWidth = 340.dp,
            maxHeight = 480.dp,
            confirmButton = {
                MiuixSettingsTextButton(onClick = { showDetailDialog = false }) {
                    Text(stringResource(R.string.about_original_author_confirm))
                }
            },
            title = {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.about_original_author_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 36.dp)
                    )
                    IconButton(
                        onClick = { showDetailDialog = false },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_close)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
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
