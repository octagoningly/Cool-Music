package moe.ouom.neriplayer.util.network

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
 * File: moe.ouom.neriplayer.util.network/GitHubSyncProxySelector
 * Updated: 2026/4/7
 */

import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * ProxySelector used only by GitHub sync traffic.
 *
 * When [useSystemProxy] is true (default), requests go through the system proxy
 * regardless of the global [DynamicProxySelector.bypassProxy] flag.
 * When false, GitHub sync always uses [Proxy.NO_PROXY].
 */
object GitHubSyncProxySelector : ProxySelector() {
    @Volatile
    var useSystemProxy: Boolean = true

    private fun systemDefault(): ProxySelector? {
        val current = getDefault()
        return if (current === this || current === DynamicProxySelector) null else current
    }

    override fun select(uri: URI?): List<Proxy> {
        if (uri == null) return listOf(Proxy.NO_PROXY)
        if (!useSystemProxy) return listOf(Proxy.NO_PROXY)
        return systemDefault()?.select(uri).takeUnless { it.isNullOrEmpty() }
            ?: listOf(Proxy.NO_PROXY)
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        systemDefault()?.connectFailed(uri, sa, ioe)
    }
}
