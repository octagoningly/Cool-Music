package moe.ouom.neriplayer.ui.screen.playlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 任意歌单详情是否在台上（供底栏等对齐迷你栏玻璃）。 */
object PlaylistDetailPresentation {
    var presented by mutableStateOf(false)
}
