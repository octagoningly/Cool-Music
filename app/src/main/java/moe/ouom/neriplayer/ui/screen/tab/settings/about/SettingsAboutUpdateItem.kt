package moe.ouom.neriplayer.ui.screen.tab.settings.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.ouom.neriplayer.BuildConfig
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.core.update.AppUpdateInfo
import moe.ouom.neriplayer.core.update.AppUpdateInstaller
import moe.ouom.neriplayer.core.update.GitHubAppUpdateChecker
import moe.ouom.neriplayer.ui.screen.tab.settings.component.settingsItemClickable
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsDialog
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsTextButton
import java.io.File
import java.util.Locale

internal sealed interface AppUpdateUiState {
    data object Idle : AppUpdateUiState
    data object Checking : AppUpdateUiState
    data class Available(val info: AppUpdateInfo) : AppUpdateUiState
    data object UpToDate : AppUpdateUiState
    data class Downloading(
        val info: AppUpdateInfo,
        val percent: Int,
    ) : AppUpdateUiState

    data class ReadyToInstall(
        val info: AppUpdateInfo,
        val file: File,
    ) : AppUpdateUiState

    data class Error(val message: String) : AppUpdateUiState
}

@Composable
internal fun SettingsAboutUpdateItem() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<AppUpdateUiState>(AppUpdateUiState.Idle) }

    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.Update,
                contentDescription = stringResource(R.string.about_check_update),
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        headlineContent = {
            Text(
                text = stringResource(R.string.about_check_update),
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = {
            Text(
                text = when (val s = state) {
                    AppUpdateUiState.Checking -> stringResource(R.string.about_update_checking)
                    is AppUpdateUiState.Available -> context.getString(
                        R.string.about_update_available_fmt,
                        s.info.releaseName.ifBlank { s.info.tagName }
                    )

                    AppUpdateUiState.UpToDate -> stringResource(R.string.about_update_up_to_date)
                    is AppUpdateUiState.Downloading -> {
                        if (s.percent >= 0) {
                            context.getString(R.string.about_update_downloading_fmt, s.percent)
                        } else {
                            stringResource(R.string.about_update_downloading_unknown)
                        }
                    }

                    is AppUpdateUiState.ReadyToInstall ->
                        stringResource(R.string.about_update_ready_install)

                    is AppUpdateUiState.Error -> s.message
                    AppUpdateUiState.Idle ->
                        "${stringResource(R.string.common_version)} ${BuildConfig.VERSION_NAME}"
                }
            )
        },
        modifier = Modifier.settingsItemClickable {
            if (state is AppUpdateUiState.Checking || state is AppUpdateUiState.Downloading) {
                return@settingsItemClickable
            }
            state = AppUpdateUiState.Checking
            scope.launch {
                state = runCatching {
                    val info = GitHubAppUpdateChecker.check(
                        currentVersionCode = BuildConfig.VERSION_CODE,
                        currentVersionName = BuildConfig.VERSION_NAME,
                    )
                    if (info.isNewer) AppUpdateUiState.Available(info)
                    else AppUpdateUiState.UpToDate
                }.getOrElse { e ->
                    AppUpdateUiState.Error(
                        e.message ?: context.getString(R.string.about_update_check_failed)
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )

    when (val s = state) {
        is AppUpdateUiState.Available -> {
            MiuixSettingsDialog(
                onDismissRequest = { state = AppUpdateUiState.Idle },
                title = { Text(stringResource(R.string.about_update_available)) },
                text = {
                    Column {
                        Text(
                            text = context.getString(
                                R.string.about_update_available_fmt,
                                s.info.releaseName.ifBlank { s.info.tagName }
                            )
                        )
                        Spacer(Modifier.height(8.dp))
                        val notes = s.info.notes.take(400)
                        if (notes.isNotBlank()) {
                            Text(
                                text = notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        s.info.apkSizeBytes?.let { size ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = formatApkSize(size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    MiuixSettingsTextButton(
                        onClick = {
                            val info = s.info
                            state = AppUpdateUiState.Downloading(info, percent = 0)
                            scope.launch {
                                state = runCatching {
                                    val file = AppUpdateInstaller.download(
                                        context = context,
                                        url = info.apkUrl,
                                        assetName = info.apkAssetName,
                                    ) { percent ->
                                        state = AppUpdateUiState.Downloading(info, percent)
                                    }
                                    AppUpdateUiState.ReadyToInstall(info, file)
                                }.getOrElse { e ->
                                    AppUpdateUiState.Error(
                                        e.message
                                            ?: context.getString(R.string.about_update_download_failed)
                                    )
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.about_update_download_install))
                    }
                },
                dismissButton = {
                    MiuixSettingsTextButton(onClick = { state = AppUpdateUiState.Idle }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        is AppUpdateUiState.Downloading -> {
            MiuixSettingsDialog(
                onDismissRequest = { },
                title = { Text(stringResource(R.string.about_update_downloading_title)) },
                text = {
                    Column {
                        Text(
                            text = if (s.percent >= 0) {
                                context.getString(R.string.about_update_downloading_fmt, s.percent)
                            } else {
                                stringResource(R.string.about_update_downloading_unknown)
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (s.percent >= 0) s.percent / 100f else 0f
                            },
                            modifier = Modifier.height(6.dp)
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {}
            )
        }

        is AppUpdateUiState.ReadyToInstall -> {
            MiuixSettingsDialog(
                onDismissRequest = { state = AppUpdateUiState.Idle },
                title = { Text(stringResource(R.string.about_update_ready_install_title)) },
                text = {
                    Text(stringResource(R.string.about_update_ready_install_hint))
                },
                confirmButton = {
                    MiuixSettingsTextButton(
                        onClick = {
                            runCatching {
                                AppUpdateInstaller.install(context, s.file)
                            }.onFailure { e ->
                                state = AppUpdateUiState.Error(
                                    e.message
                                        ?: context.getString(R.string.about_update_install_failed)
                                )
                            }
                        }
                    ) {
                        Text(stringResource(R.string.about_update_install_now))
                    }
                },
                dismissButton = {
                    MiuixSettingsTextButton(onClick = { state = AppUpdateUiState.Idle }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        is AppUpdateUiState.UpToDate -> {
            MiuixSettingsDialog(
                onDismissRequest = { state = AppUpdateUiState.Idle },
                title = { Text(stringResource(R.string.about_check_update)) },
                text = { Text(stringResource(R.string.about_update_up_to_date_hint)) },
                confirmButton = {
                    MiuixSettingsTextButton(onClick = { state = AppUpdateUiState.Idle }) {
                        Text(stringResource(R.string.action_confirm))
                    }
                },
                dismissButton = null
            )
        }

        is AppUpdateUiState.Error -> {
            MiuixSettingsDialog(
                onDismissRequest = { state = AppUpdateUiState.Idle },
                title = { Text(stringResource(R.string.about_update_check_failed)) },
                text = {
                    Column {
                        Text(s.message)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.about_update_open_releases_hint),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    MiuixSettingsTextButton(
                        onClick = {
                            AppUpdateInstaller.openReleasesPage(context)
                            state = AppUpdateUiState.Idle
                        }
                    ) {
                        Text(stringResource(R.string.about_update_open_releases))
                    }
                },
                dismissButton = {
                    MiuixSettingsTextButton(onClick = { state = AppUpdateUiState.Idle }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        AppUpdateUiState.Idle, AppUpdateUiState.Checking -> Unit
    }
}

private fun formatApkSize(bytes: Long): String {
    val mb = bytes / 1024.0 / 1024.0
    return String.format(Locale.US, "%.1f MB", mb)
}
