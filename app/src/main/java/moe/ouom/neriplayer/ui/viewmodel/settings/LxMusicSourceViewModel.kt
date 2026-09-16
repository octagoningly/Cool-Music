package moe.ouom.neriplayer.ui.viewmodel.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.data.source.lxmusic.LxImportedSource
import moe.ouom.neriplayer.data.source.lxmusic.LxMusicSourceRepository

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
 * File: moe.ouom.neriplayer.ui.viewmodel.settings/LxMusicSourceViewModel
 * Updated: 2026/3/23
 */

data class LxMusicSourceUiState(
    val sources: List<LxImportedSource> = emptyList(),
    val preferCustomSource: Boolean = true,
    val importing: Boolean = false,
    val refreshingId: String? = null,
    val message: String? = null
)

sealed class LxMusicSourceEvent {
    data class ShowMessage(val message: String) : LxMusicSourceEvent()
}

class LxMusicSourceViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LxMusicSourceUiState())
    val uiState: StateFlow<LxMusicSourceUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LxMusicSourceEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<LxMusicSourceEvent> = _events.asSharedFlow()

    private var repository: LxMusicSourceRepository? = null

    fun initialize(context: Context) {
        if (repository != null) return
        val repo = AppContainer.lxMusicSourceRepository
        repository = repo
        viewModelScope.launch {
            repo.sourcesFlow.collect { sources ->
                _uiState.update { it.copy(sources = sources) }
            }
        }
        viewModelScope.launch {
            repo.preferCustomSourceFlow.collect { enabled ->
                _uiState.update { it.copy(preferCustomSource = enabled) }
            }
        }
    }

    fun setPreferCustomSource(enabled: Boolean) {
        val repo = repository ?: return
        viewModelScope.launch {
            repo.setPreferCustomSource(enabled)
        }
    }

    fun setSourceEnabled(id: String, enabled: Boolean) {
        val repo = repository ?: return
        viewModelScope.launch {
            repo.setSourceEnabled(id, enabled)
        }
    }

    fun removeSource(id: String) {
        val repo = repository ?: return
        viewModelScope.launch {
            repo.removeSource(id)
            emitMessage("removed")
        }
    }

    fun importFromUrl(url: String) {
        val repo = repository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(importing = true, message = null) }
            val result = repo.importFromUrl(url)
            _uiState.update { it.copy(importing = false) }
            result.fold(
                onSuccess = { source ->
                    emitMessage("import_ok:${source.name}")
                },
                onFailure = { error ->
                    val reason = error.message ?: "import_failed"
                    emitMessage("import_failed:$reason")
                }
            )
        }
    }

    fun refreshSource(id: String) {
        val repo = repository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(refreshingId = id) }
            val result = repo.refreshSource(id)
            _uiState.update { it.copy(refreshingId = null) }
            result.fold(
                onSuccess = { emitMessage("refresh_ok") },
                onFailure = { error ->
                    emitMessage("refresh_failed:${error.message ?: "refresh_failed"}")
                }
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun emitMessage(message: String) {
        _uiState.update { it.copy(message = message) }
        viewModelScope.launch {
            _events.emit(LxMusicSourceEvent.ShowMessage(message))
        }
    }
}
