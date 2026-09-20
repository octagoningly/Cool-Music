package moe.ouom.neriplayer.data.source.lxmusic

import org.junit.Assert.assertEquals
import org.junit.Test

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
 * File: moe.ouom.neriplayer.data.source.lxmusic/LxMusicSourceRegistryParserTest
 */

class LxMusicSourceRegistryParserTest {

    @Test
    fun `remote registry keeps only verified source entries`() {
        val registry = LxMusicSourceParser.parseRemoteSourceRegistry(
            """
            {
              "schemaVersion": 1,
              "generatedAt": "2026-09-20T01:02:03Z",
              "minimumHealthyChannels": 1,
              "sources": [
                {
                  "name": "Working JS",
                  "kind": "js",
                  "url": "https://example.com/source.js",
                  "healthyChannels": ["kw", "tx"],
                  "lastValidatedAt": "2026-09-20T01:00:00Z"
                },
                {
                  "name": "Unverified",
                  "kind": "js",
                  "url": "https://example.com/bad.js",
                  "healthyChannels": []
                },
                {
                  "name": "Bad URL",
                  "kind": "json",
                  "url": "ftp://example.com/source.json",
                  "healthyChannels": ["json"]
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals("2026-09-20T01:02:03Z", registry.generatedAt)
        assertEquals(1, registry.sources.size)
        assertEquals("Working JS", registry.sources.single().name)
        assertEquals(listOf("kw", "tx"), registry.sources.single().healthyChannels)
    }
}
