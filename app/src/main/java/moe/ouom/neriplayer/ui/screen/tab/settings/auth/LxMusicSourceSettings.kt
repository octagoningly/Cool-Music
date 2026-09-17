package moe.ouom.neriplayer.ui.screen.tab.settings.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.data.source.lxmusic.LxImportedSource
import moe.ouom.neriplayer.data.source.lxmusic.LxMusicSourceRepository
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsButton
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsDialog
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsInlineMessage
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsSwitch
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsTextField
import moe.ouom.neriplayer.ui.screen.tab.settings.state.collectAsStateWithLifecycleCompat
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
 * Updated: 2026/3/23
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
        androidx.compose.runtime.LaunchedEffect(vm, context) {
            vm.initialize(context)
        }
    }

    if (showManageDialog) {
        val state by vm.uiState.collectAsStateWithLifecycleCompat()
        MiuixSettingsDialog(
            onDismissRequest = {
                vm.clearMessage()
                onDismissManageDialog()
            },
            confirmButton = {
                MiuixSettingsButton(onClick = onOpenImportDialog) {
                    Text(stringResource(R.string.lx_source_add))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    vm.clearMessage()
                    onDismissManageDialog()
                }) {
                    Text(stringResource(R.string.lx_source_close))
                }
            },
            title = { Text(stringResource(R.string.lx_source_manage_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.lx_source_manage_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    state.message?.let { message ->
                        val mapped = mapLxSourceMessage(message)
                        MiuixSettingsInlineMessage(
                            message = mapped,
                            isSuccess = !message.startsWith("import_failed") &&
                                !message.startsWith("refresh_failed"),
                            onClose = vm::clearMessage
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.lx_source_prefer_first),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(R.string.lx_source_prefer_first_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        MiuixSettingsSwitch(
                            checked = state.preferCustomSource,
                            onCheckedChange = vm::setPreferCustomSource
                        )
                    }
                    HorizontalDivider()
                    LxSourceRuntimeStatusRow(status = state.runtimeStatus)
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
                                onRemove = { vm.removeSource(source.id) }
                            )
                        }
                    }
                }
            }
        )
    }

    if (showImportDialog) {
        var urlInput by remember(showImportDialog) { mutableStateOf("") }
        val state by vm.uiState.collectAsStateWithLifecycleCompat()
        MiuixSettingsDialog(
            onDismissRequest = {
                vm.clearMessage()
                onDismissImportDialog()
            },
            confirmButton = {
                MiuixSettingsButton(
                    enabled = !state.importing && urlInput.isNotBlank(),
                    onClick = { vm.importFromUrl(urlInput) }
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
                androidx.compose.material3.TextButton(onClick = onDismissImportDialog) {
                    Text(stringResource(R.string.lx_source_cancel))
                }
            },
            title = { Text(stringResource(R.string.lx_source_import_title)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.lx_source_import_desc),
                        style = MaterialTheme.typography.bodyMedium
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

/**
 * 显示在线音源最近一次解析结果。
 * 音源站服务端故障时（如网易云通道 502），这里会直接显示原因，
 * 避免「明明启用了音源却一直走平台音源」看起来像功能没生效。
 */
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
private fun LxSourceRow(
    source: LxImportedSource,
    refreshing: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onRemove: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            ) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = buildString {
                        append(source.url)
                        if (source.version.isNotBlank()) {
                            append(" · ")
                            append(source.version)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                source.lastError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
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
        Spacer(modifier = Modifier.height(4.dp))
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
        else -> message
    }
}
