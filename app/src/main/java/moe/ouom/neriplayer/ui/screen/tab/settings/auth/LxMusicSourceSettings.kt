package moe.ouom.neriplayer.ui.screen.tab.settings.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.core.player.resolver.lxmusic.LxChannelProbeResult
import moe.ouom.neriplayer.data.source.lxmusic.LxImportedSource
import moe.ouom.neriplayer.data.source.lxmusic.LxMusicSourceRepository
import moe.ouom.neriplayer.data.source.lxmusic.LxRemoteSourceEntry
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsButton
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsDialog
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsInlineMessage
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsSwitch
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsTextField
import moe.ouom.neriplayer.ui.screen.tab.settings.state.collectAsStateWithLifecycleCompat
import moe.ouom.neriplayer.ui.util.ClipboardCopyResult
import moe.ouom.neriplayer.ui.util.copyPlainTextSafely
import moe.ouom.neriplayer.ui.viewmodel.settings.LxMusicSourceViewModel

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
 * File: moe.ouom.neriplayer.ui.screen.tab.settings.auth/LxMusicSourceSettings
 */

@Composable
internal fun SettingsLxMusicSourceDialogs(
    showManageDialog: Boolean,
    onDismissManageDialog: () -> Unit,
    showImportDialog: Boolean,
    onDismissImportDialog: () -> Unit,
    onOpenImportDialog: () -> Unit,
    vm: LxMusicSourceViewModel
) {
    val context = LocalContext.current
    if (showManageDialog || showImportDialog) {
        LaunchedEffect(vm, context) {
            vm.initialize(context)
        }
    }

    if (showManageDialog) {
        val state by vm.uiState.collectAsStateWithLifecycleCompat()
        var detailSource by remember { mutableStateOf<LxImportedSource?>(null) }

        MiuixSettingsDialog(
            onDismissRequest = {
                vm.clearMessage()
                onDismissManageDialog()
            },
            confirmButton = {
                MiuixSettingsButton(onClick = {
                    vm.clearMessage()
                    onOpenImportDialog()
                }) {
                    Text(stringResource(R.string.lx_source_add))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.clearMessage()
                    onDismissManageDialog()
                }) {
                    Text(stringResource(R.string.lx_source_close))
                }
            },
            title = { Text(stringResource(R.string.lx_source_manage_title)) },
            text = {
                val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
                // 限制正文高度，保证底部「关闭/添加」始终可见可点
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = screenHeightDp * 0.55f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1) 偏好开关（一句话）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.lx_source_prefer_first),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(R.string.lx_source_prefer_first_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        MiuixSettingsSwitch(
                            checked = state.preferCustomSource,
                            onCheckedChange = vm::setPreferCustomSource
                        )
                    }

                    state.message?.let { message ->
                        val mapped = mapLxSourceMessage(message)
                        MiuixSettingsInlineMessage(
                            message = mapped,
                            isSuccess = !message.startsWith("import_failed") &&
                                !message.startsWith("refresh_failed") &&
                                !message.startsWith("registry_failed"),
                            onClose = vm::clearMessage
                        )
                    }

                    LxSourceRegistrySection(
                        fetching = state.fetchingRegistry,
                        generatedAt = state.registryGeneratedAt,
                        entries = state.registrySources,
                        importing = state.importing,
                        onFetch = vm::fetchRemoteSourceRegistry,
                        onImport = vm::importFromUrl
                    )

                    // 2) 运行状态 / 通道检测（仅在有内容时出现）
                    LxSourceRuntimeStatusRow(status = state.runtimeStatus)
                    LxSourceChannelProbeSection(
                        probing = state.probing,
                        results = state.channelProbes,
                        hasJsSource = state.sources.any { it.isJsSource && it.enabled },
                        onProbe = { vm.probeChannels(context) }
                    )

                    HorizontalDivider()

                    // 3) 音源列表
                    Text(
                        text = stringResource(R.string.lx_source_section_imported),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.sources.isEmpty()) {
                        Text(
                            text = stringResource(R.string.lx_source_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.sources.forEach { source ->
                            LxSourceRow(
                                source = source,
                                refreshing = state.refreshingId == source.id,
                                onToggleEnabled = { enabled ->
                                    vm.setSourceEnabled(source.id, enabled)
                                },
                                onRefresh = { vm.refreshSource(source.id) },
                                onRemove = { vm.removeSource(source.id) },
                                onClick = { detailSource = source }
                            )
                        }
                    }
                }
            }
        )

        detailSource?.let { source ->
            LxSourceDetailDialog(
                source = source,
                onDismiss = { detailSource = null }
            )
        }
    }

    if (showImportDialog) {
        var urlInput by remember(showImportDialog) { mutableStateOf("") }
        val state by vm.uiState.collectAsStateWithLifecycleCompat()

        // 导入成功/失败后清空输入框，方便直接粘贴下一条
        LaunchedEffect(state.message) {
            val message = state.message ?: return@LaunchedEffect
            if (message.startsWith("import_ok") || message.startsWith("import_failed")) {
                urlInput = ""
            }
        }

        MiuixSettingsDialog(
            onDismissRequest = {
                vm.clearMessage()
                onDismissImportDialog()
            },
            confirmButton = {
                MiuixSettingsButton(
                    enabled = !state.importing && urlInput.isNotBlank(),
                    onClick = { vm.importFromUrl(urlInput.trim()) }
                ) {
                    if (state.importing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.lx_source_import))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.clearMessage()
                    onDismissImportDialog()
                }) {
                    Text(stringResource(R.string.lx_source_cancel))
                }
            },
            title = { Text(stringResource(R.string.lx_source_import_title)) },
            text = {
                val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = screenHeightDp * 0.45f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.lx_source_import_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    MiuixSettingsTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text(stringResource(R.string.lx_source_url_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.message?.let { message ->
                        MiuixSettingsInlineMessage(
                            message = mapLxSourceMessage(message),
                            isSuccess = message.startsWith("import_ok"),
                            onClose = vm::clearMessage
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun LxSourceDetailDialog(
    source: LxImportedSource,
    onDismiss: () -> Unit
) {
    MiuixSettingsDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            MiuixSettingsButton(onClick = onDismiss) {
                Text(stringResource(R.string.lx_source_close))
            }
        },
        title = { Text(source.name) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow(
                    label = stringResource(R.string.lx_source_url_dialog_title),
                    value = source.url
                )
                if (source.description.isNotBlank()) {
                    DetailRow(
                        label = stringResource(R.string.lx_source_detail_desc),
                        value = source.description
                    )
                }
                if (source.author.isNotBlank()) {
                    DetailRow(
                        label = stringResource(R.string.lx_source_detail_author),
                        value = source.author
                    )
                }
                if (source.version.isNotBlank()) {
                    DetailRow(
                        label = stringResource(R.string.lx_source_detail_version),
                        value = source.version
                    )
                }
                DetailRow(
                    label = stringResource(R.string.lx_source_detail_kind),
                    value = stringResource(
                        if (source.isJsSource) R.string.lx_source_kind_js
                        else R.string.lx_source_kind_json
                    )
                )
                if (source.searchApiUrl.isNotBlank()) {
                    DetailRow(
                        label = stringResource(R.string.lx_source_detail_search_api),
                        value = source.searchApiUrl
                    )
                }
                if (source.songUrlApiUrl.isNotBlank()) {
                    DetailRow(
                        label = stringResource(R.string.lx_source_detail_song_api),
                        value = source.songUrlApiUrl
                    )
                }
                if (source.lyricApiUrl.isNotBlank()) {
                    DetailRow(
                        label = stringResource(R.string.lx_source_detail_lyric_api),
                        value = source.lyricApiUrl
                    )
                }
                if (source.picApiUrl.isNotBlank()) {
                    DetailRow(
                        label = stringResource(R.string.lx_source_detail_pic_api),
                        value = source.picApiUrl
                    )
                }
                if (source.jsSourceIds.isNotEmpty()) {
                    DetailRow(
                        label = stringResource(R.string.lx_source_detail_platforms),
                        value = source.jsSourceIds.joinToString(" / ")
                    )
                }
                if (source.supportedQualities.isNotEmpty()) {
                    DetailRow(
                        label = stringResource(R.string.lx_source_detail_qualities),
                        value = source.supportedQualities.joinToString(" / ")
                    )
                }
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun LxSourceRegistrySection(
    fetching: Boolean,
    generatedAt: String,
    entries: List<LxRemoteSourceEntry>,
    importing: Boolean,
    onFetch: () -> Unit,
    onImport: (String) -> Unit
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copyMessage by remember { mutableStateOf<String?>(null) }
    var copySucceeded by remember { mutableStateOf(true) }
    val copiedText = stringResource(R.string.toast_copied)
    val copyTruncatedText = stringResource(R.string.toast_copy_truncated)
    val copyFailedText = stringResource(R.string.toast_copy_failed)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.lx_source_registry_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.lx_source_registry_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                enabled = !fetching,
                onClick = {
                    copyMessage = null
                    onFetch()
                }
            ) {
                if (fetching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.lx_source_registry_fetching))
                } else {
                    Text(stringResource(R.string.lx_source_registry_fetch))
                }
            }
        }

        if (generatedAt.isNotBlank()) {
            Text(
                text = stringResource(R.string.lx_source_registry_generated_at, generatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        copyMessage?.let { message ->
            MiuixSettingsInlineMessage(
                message = message,
                isSuccess = copySucceeded,
                onClose = { copyMessage = null }
            )
        }

        entries.forEach { entry ->
            LxRemoteSourceRow(
                entry = entry,
                importing = importing,
                onCopy = { url ->
                    scope.launch {
                        val result = clipboard.copyPlainTextSafely(
                            label = "LX Source URL",
                            text = url
                        )
                        copySucceeded = result is ClipboardCopyResult.Copied
                        copyMessage = when (result) {
                            is ClipboardCopyResult.Copied -> if (result.wasTruncated) {
                                copyTruncatedText
                            } else {
                                copiedText
                            }
                            ClipboardCopyResult.TransactionTooLarge -> copyFailedText
                        }
                    }
                },
                onImport = onImport
            )
        }
    }
}

@Composable
private fun LxRemoteSourceRow(
    entry: LxRemoteSourceEntry,
    importing: Boolean,
    onCopy: (String) -> Unit,
    onImport: (String) -> Unit
) {
    val platformLabels = mapOf(
        "wy" to stringResource(R.string.lx_source_platform_wy),
        "kw" to stringResource(R.string.lx_source_platform_kw),
        "kg" to stringResource(R.string.lx_source_platform_kg),
        "tx" to stringResource(R.string.lx_source_platform_tx),
        "mg" to stringResource(R.string.lx_source_platform_mg)
    )
    val channelText = entry.healthyChannels.joinToString(" / ") { sourceId ->
        platformLabels[sourceId.lowercase()] ?: sourceId
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.lx_source_registry_channels, channelText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onCopy(entry.url) }) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.lx_source_registry_copy))
            }
            TextButton(
                enabled = !importing,
                onClick = { onImport(entry.url) }
            ) {
                if (importing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(stringResource(R.string.lx_source_registry_import))
            }
        }
    }
}

@Composable
private fun LxSourceRuntimeStatusRow(status: LxMusicSourceRepository.RuntimeStatus) {
    if (!status.hasActivity) return
    val failed = status.lastAttemptFailed
    val timestampMs = if (failed) status.lastFailureAtMs else status.lastSuccessAtMs
    val timeText = remember(timestampMs) {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(timestampMs))
    }
    Text(
        text = if (failed) {
            stringResource(
                R.string.lx_source_status_failure,
                timeText,
                status.lastFailureReason.orEmpty().ifBlank { "-" }
            )
        } else {
            stringResource(R.string.lx_source_status_success, timeText)
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (failed) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        }
    )
}

@Composable
private fun LxSourceChannelProbeSection(
    probing: Boolean,
    results: List<LxChannelProbeResult>,
    hasJsSource: Boolean,
    onProbe: () -> Unit
) {
    if (!hasJsSource && results.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.lx_source_probe_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            if (probing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.lx_source_probe_running),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                TextButton(onClick = onProbe) {
                    Text(stringResource(R.string.lx_source_probe_button))
                }
            }
        }
        results.forEach { result ->
            val detailText = if (result.healthy) {
                stringResource(R.string.lx_source_probe_ok)
            } else {
                val raw = result.detail.orEmpty().ifBlank { "-" }
                stringResource(R.string.lx_source_probe_failed) + " · " +
                    raw.take(28) + if (raw.length > 28) "…" else ""
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = lxPlatformLabel(result.sourceId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(0.3f)
                )
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.healthy) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.7f)
                )
            }
        }
    }
}

@Composable
private fun lxPlatformLabel(sourceId: String): String {
    return stringResource(lxPlatformLabelRes(sourceId))
}

private fun lxPlatformLabelRes(sourceId: String): Int {
    return when (sourceId.lowercase()) {
        "kw" -> R.string.lx_source_platform_kw
        "kg" -> R.string.lx_source_platform_kg
        "tx" -> R.string.lx_source_platform_tx
        "mg" -> R.string.lx_source_platform_mg
        else -> R.string.lx_source_platform_wy
    }
}

@Composable
private fun LxSourceRow(
    source: LxImportedSource,
    refreshing: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onRemove: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.CloudDownload,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (source.enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        ) {
            Text(
                text = source.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val kindLabel = stringResource(
                if (source.isJsSource) R.string.lx_source_kind_js
                else R.string.lx_source_kind_json
            )
            Text(
                text = buildString {
                    append(kindLabel)
                    if (source.version.isNotBlank()) {
                        append(" · ")
                        append(source.version)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            source.lastError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (refreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(4.dp))
        } else {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.lx_source_refresh)
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.lx_source_remove)
            )
        }
        MiuixSettingsSwitch(
            checked = source.enabled,
            onCheckedChange = onToggleEnabled
        )
    }
}

@Composable
private fun mapLxSourceMessage(message: String): String {
    return when {
        message.startsWith("import_ok") ->
            stringResource(R.string.lx_source_import_success)
        message.startsWith("import_failed") -> {
            val reason = message.substringAfter("import_failed:", "").trim()
            if (reason.equals("Invalid LX source", ignoreCase = true) ||
                reason.equals("Invalid LX source JSON", ignoreCase = true)
            ) {
                stringResource(R.string.lx_source_import_failed)
            } else if (reason.isNotBlank()) {
                stringResource(R.string.lx_source_import_failed_detail, reason)
            } else {
                stringResource(R.string.lx_source_import_failed)
            }
        }
        message == "refresh_ok" ->
            stringResource(R.string.lx_source_refresh_success)
        message.startsWith("refresh_failed") ->
            stringResource(R.string.lx_source_refresh_failed)
        message == "removed" ->
            stringResource(R.string.lx_source_removed)
        message.startsWith("registry_ok") -> {
            val count = message.substringAfter("registry_ok:", "").toIntOrNull() ?: 0
            stringResource(R.string.lx_source_registry_success, count)
        }
        message == "registry_empty" ->
            stringResource(R.string.lx_source_registry_empty)
        message.startsWith("registry_failed") -> {
            val reason = message.substringAfter("registry_failed:", "").trim()
            if (reason.isNotBlank()) {
                stringResource(R.string.lx_source_registry_failed_detail, reason)
            } else {
                stringResource(R.string.lx_source_registry_failed)
            }
        }
        else -> message
    }
}
