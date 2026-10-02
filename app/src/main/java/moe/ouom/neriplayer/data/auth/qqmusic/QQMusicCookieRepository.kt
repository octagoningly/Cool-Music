@file:Suppress("DEPRECATION")

package moe.ouom.neriplayer.data.auth.qqmusic

/*
 * NeriPlayer - A unified Android player for streaming music and videos from multiple online platforms.
 * Copyright (C) 2025-2025 NeriPlayer developers
 * https://github.com/cwuom/NeriPlayer
 *
 * This software is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
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
 * File: moe.ouom.neriplayer.data.auth.qqmusic/QQMusicCookieRepository
 * Created: 2026/9/28
 */

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.ouom.neriplayer.data.auth.common.SavedCookieAuthHealth
import moe.ouom.neriplayer.data.auth.common.SavedCookieAuthState
import moe.ouom.neriplayer.core.logging.NPLogger
import org.json.JSONObject

private const val QQ_MUSIC_AUTH_PREFS = "qqmusic_auth_secure_prefs"
private const val KEY_QQ_MUSIC_AUTH_BUNDLE = "qqmusic_auth_bundle"

private val QQ_MUSIC_LOGIN_COOKIE_KEYS = listOf(
    "uin",
    "wxuin",
    "qm_keyst",
    "qqmusic_key",
    "music_key",
    "wxskey"
)

data class QQMusicAuthBundle(
    val cookies: Map<String, String> = emptyMap(),
    val savedAt: Long = 0L
) {
    fun hasLoginCookies(): Boolean {
        val uin = (cookies["uin"] ?: cookies["wxuin"]).orEmpty().trim().removePrefix("o")
        if (uin.isEmpty() || !uin.all { it.isDigit() }) {
            return false
        }
        val key = musicKey().orEmpty()
        return key.isNotEmpty()
    }

    /** 取流 comm.authst 用的登录密钥；qm_keyst 与 qqmusic_key 实测同值，任一可用 */
    fun musicKey(): String? {
        return cookies["qm_keyst"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: cookies["qqmusic_key"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: cookies["music_key"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: cookies["wxskey"]?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun uin(): String? {
        return (cookies["uin"] ?: cookies["wxuin"])
            ?.trim()?.removePrefix("o")?.takeIf { it.isNotEmpty() }
    }

    /**
     * 取流请求 comm.g_tk：对 [musicKey] 做经典 QQ GTK 哈希。
     * 返回 null 表示无可用登录密钥。
     */
    fun gtk(): Int? = musicKey()?.let(::qqMusicGtk)

    fun normalized(savedAt: Long = this.savedAt): QQMusicAuthBundle {
        val normalizedCookies = LinkedHashMap(cookies.filterKeys { it.isNotBlank() }).apply {
            this["uin"] = this["uin"].orEmpty().trim().removePrefix("o")
            if (this["uin"].isNullOrBlank()) {
                this["uin"] = this["wxuin"].orEmpty().trim().removePrefix("o")
            }
        }.filterValues { it.isNotBlank() }
        return copy(
            cookies = normalizedCookies,
            savedAt = savedAt
        )
    }

    fun toJson(): String {
        return JSONObject().apply {
            put(
                "cookies",
                JSONObject().apply {
                    cookies.forEach { (key, value) -> put(key, value) }
                }
            )
            put("savedAt", savedAt)
        }.toString()
    }

    companion object {
        fun fromJson(json: String): QQMusicAuthBundle {
            return runCatching {
                val root = JSONObject(json)
                val cookiesJson = root.optJSONObject("cookies") ?: JSONObject()
                val cookies = linkedMapOf<String, String>()
                val keys = cookiesJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    cookies[key] = cookiesJson.optString(key, "")
                }
                val savedAt = root.optLong("savedAt", 0L)
                QQMusicAuthBundle(
                    cookies = cookies,
                    savedAt = savedAt
                ).normalized(savedAt = savedAt)
            }.getOrDefault(QQMusicAuthBundle())
        }
    }
}

/**
 * QQ 音乐取流接口（musicu.fcg）comm.g_tk 计算：
 * hash = 5381; 逐字符 hash += (hash shl 5) + code;  最后取低 31 位。
 *
 * 已用真实登录密钥实测与服务端接受值一致（过程见 docs/qq-music-link-notes.md；
 * 凭据不入库，单测仅使用合成样本）。Kotlin Int 溢出自动回绕 ≡ mod 2^32。
 */
fun qqMusicGtk(key: String): Int {
    var hash = 5381
    for (char in key) {
        hash = hash + ((hash shl 5) + char.code)
    }
    return hash and 0x7fffffff
}

internal fun evaluateQQMusicAuthHealth(
    bundle: QQMusicAuthBundle,
    now: Long = System.currentTimeMillis()
): SavedCookieAuthHealth {
    val normalized = bundle.normalized(savedAt = bundle.savedAt)
    val presentKeys = QQ_MUSIC_LOGIN_COOKIE_KEYS.filter { key ->
        !normalized.cookies[key].isNullOrBlank()
    }
    if (!normalized.hasLoginCookies()) {
        return SavedCookieAuthHealth(
            state = SavedCookieAuthState.Missing,
            savedAt = normalized.savedAt,
            checkedAt = now,
            loginCookieKeys = presentKeys
        )
    }

    val savedAt = normalized.savedAt
    val ageMs = if (savedAt > 0L) {
        (now - savedAt).coerceAtLeast(0L)
    } else {
        Long.MAX_VALUE
    }
    return SavedCookieAuthHealth(
        state = SavedCookieAuthState.Valid,
        savedAt = savedAt,
        checkedAt = now,
        ageMs = ageMs,
        loginCookieKeys = presentKeys
    )
}

class QQMusicCookieRepository(private val context: Context) {
    private val encryptedPrefs: SharedPreferences
    private val _authFlow: MutableStateFlow<QQMusicAuthBundle>
    val authFlow: StateFlow<QQMusicAuthBundle>
    private val _cookieFlow: MutableStateFlow<Map<String, String>>
    private val _authHealthFlow: MutableStateFlow<SavedCookieAuthHealth>

    val cookieFlow: StateFlow<Map<String, String>>
        get() = _cookieFlow.asStateFlow()

    val authHealthFlow: StateFlow<SavedCookieAuthHealth>
        get() = _authHealthFlow.asStateFlow()

    init {
        encryptedPrefs = openEncryptedPrefsWithRecovery()
        val initialBundle = loadAuthBundle()
        _authFlow = MutableStateFlow(initialBundle)
        authFlow = _authFlow.asStateFlow()
        _cookieFlow = MutableStateFlow(initialBundle.cookies)
        _authHealthFlow = MutableStateFlow(
            evaluateQQMusicAuthHealth(initialBundle)
        )
    }

    fun getAuthBundleOnce(): QQMusicAuthBundle = _authFlow.value

    fun getAuthHealthOnce(): SavedCookieAuthHealth = _authHealthFlow.value

    fun getAuthHealth(
        now: Long = System.currentTimeMillis()
    ): SavedCookieAuthHealth = evaluateQQMusicAuthHealth(_authFlow.value, now)

    fun saveCookies(
        cookies: Map<String, String>,
        savedAt: Long = System.currentTimeMillis()
    ) {
        val normalized = QQMusicAuthBundle(
            cookies = cookies,
            savedAt = savedAt
        ).normalized(savedAt = savedAt)
        encryptedPrefs.edit {
            putString(KEY_QQ_MUSIC_AUTH_BUNDLE, normalized.toJson())
        }
        _authFlow.value = normalized
        _cookieFlow.value = normalized.cookies
        _authHealthFlow.value = evaluateQQMusicAuthHealth(normalized)
        NPLogger.d(
            "NERI-QQMusicCookieRepo",
            "Saved QQ Music cookies: keys=${cookies.keys.joinToString()}"
        )
    }

    fun clear() {
        encryptedPrefs.edit {
            remove(KEY_QQ_MUSIC_AUTH_BUNDLE)
        }
        val cleared = QQMusicAuthBundle()
        _authFlow.value = cleared
        _cookieFlow.value = cleared.cookies
        _authHealthFlow.value = evaluateQQMusicAuthHealth(cleared)
        NPLogger.d("NERI-QQMusicCookieRepo", "Cleared QQ Music cookies")
    }

    fun refreshHealth(now: Long = System.currentTimeMillis()) {
        _authHealthFlow.value = evaluateQQMusicAuthHealth(
            bundle = _authFlow.value,
            now = now
        )
    }

    private fun loadAuthBundle(): QQMusicAuthBundle {
        val raw = encryptedPrefs.getString(KEY_QQ_MUSIC_AUTH_BUNDLE, null).orEmpty()
        if (raw.isBlank()) {
            return QQMusicAuthBundle()
        }
        return QQMusicAuthBundle.fromJson(raw)
    }

    private fun openEncryptedPrefsWithRecovery(): SharedPreferences {
        return runCatching {
            createEncryptedPrefs()
        }.getOrElse { error ->
            NPLogger.w(
                "NERI-QQMusicCookieRepo",
                "Failed to open QQ Music secure prefs, clearing storage and recreating.",
                error
            )
            clearEncryptedStorage()
            createEncryptedPrefs()
        }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            QQ_MUSIC_AUTH_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun clearEncryptedStorage() {
        runCatching {
            context.deleteSharedPreferences(QQ_MUSIC_AUTH_PREFS)
        }.onFailure { error ->
            NPLogger.w(
                "NERI-QQMusicCookieRepo",
                "Failed to delete corrupted QQ Music secure prefs file.",
                error
            )
        }
    }
}
