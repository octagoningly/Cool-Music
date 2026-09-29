package moe.ouom.neriplayer.data

import moe.ouom.neriplayer.data.auth.common.SavedCookieAuthState
import moe.ouom.neriplayer.data.auth.qqmusic.QQMusicAuthBundle
import moe.ouom.neriplayer.data.auth.qqmusic.evaluateQQMusicAuthHealth
import moe.ouom.neriplayer.data.auth.qqmusic.qqMusicGtk
import moe.ouom.neriplayer.data.auth.web.shouldAutoCompleteQQMusicWebLogin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QQMusicAuthRepositoryTest {

    @Test
    fun qqMusicGtk_matchesPrecomputedSyntheticSamples() {
        // 期望值由独立实现（任意精度最终取低 31 位）预计算；
        // 真实登录密钥对值已另行实测验证，但凭据不入库（开发规则 · 禁止提交）。
        assertEquals(193485963, qqMusicGtk("abc"))
        assertEquals(2065130744, qqMusicGtk("test-key-sample-01"))
        assertEquals(
            1168076847,
            qqMusicGtk("Q_H_L_synthetic_sample_key_for_unit_tests_only_000123456789")
        )
        assertEquals(5381, qqMusicGtk(""))
    }

    @Test
    fun qqMusicGtk_isStableAcrossInvocations() {
        val key = "test-key-sample-01"
        assertEquals(qqMusicGtk(key), qqMusicGtk(key))
    }

    @Test
    fun qqMusicAuthBundle_jsonRoundTripDropsBlankKeysAndKeepsSavedAt() {
        val original = QQMusicAuthBundle(
            cookies = linkedMapOf(
                "uin" to "3575053735",
                "qm_keyst" to "synthetic-key",
                "" to "ignored"
            ),
            savedAt = 456L
        )

        val restored = QQMusicAuthBundle.fromJson(original.toJson())

        assertEquals("3575053735", restored.cookies["uin"])
        assertEquals("synthetic-key", restored.cookies["qm_keyst"])
        assertFalse(restored.cookies.containsKey(""))
        assertEquals(456L, restored.savedAt)
        assertTrue(restored.hasLoginCookies())
    }

    @Test
    fun qqMusicAuthBundle_hasLoginCookies_requiresNumericUinAndMusicKey() {
        assertFalse(QQMusicAuthBundle().hasLoginCookies())
        assertFalse(
            QQMusicAuthBundle(cookies = mapOf("qm_keyst" to "key-without-uin")).hasLoginCookies()
        )
        assertFalse(
            QQMusicAuthBundle(
                cookies = mapOf("uin" to "not-a-number", "qm_keyst" to "key")
            ).hasLoginCookies()
        )
        assertFalse(
            QQMusicAuthBundle(cookies = mapOf("uin" to "12345")).hasLoginCookies()
        )
        assertTrue(
            QQMusicAuthBundle(
                cookies = mapOf("uin" to "12345", "qm_keyst" to "key")
            ).hasLoginCookies()
        )
    }

    @Test
    fun qqMusicAuthBundle_musicKeyPrefersQmKeystThenFallsBackToQqmusicKey() {
        val both = QQMusicAuthBundle(
            cookies = mapOf("uin" to "1", "qm_keyst" to "primary", "qqmusic_key" to "secondary")
        )
        assertEquals("primary", both.musicKey())

        val fallbackOnly = QQMusicAuthBundle(
            cookies = mapOf("uin" to "1", "qqmusic_key" to "secondary")
        )
        assertEquals("secondary", fallbackOnly.musicKey())

        assertNull(QQMusicAuthBundle(cookies = mapOf("uin" to "1")).musicKey())
    }

    @Test
    fun qqMusicAuthBundle_gtkDerivesFromMusicKey() {
        val bundle = QQMusicAuthBundle(
            cookies = mapOf("uin" to "12345", "qm_keyst" to "test-key-sample-01")
        )
        assertEquals(qqMusicGtk("test-key-sample-01"), bundle.gtk())
        assertNull(QQMusicAuthBundle(cookies = mapOf("uin" to "12345")).gtk())
    }

    @Test
    fun evaluateQQMusicAuthHealth_returnsMissingWithoutLoginCookies() {
        val health = evaluateQQMusicAuthHealth(
            QQMusicAuthBundle(cookies = mapOf("RK" to "value")),
            now = 1_000L
        )
        assertEquals(SavedCookieAuthState.Missing, health.state)
    }

    @Test
    fun evaluateQQMusicAuthHealth_returnsValidForCompleteLoginCookies() {
        val now = 10_000L
        val health = evaluateQQMusicAuthHealth(
            QQMusicAuthBundle(
                cookies = mapOf("uin" to "3575053735", "qm_keyst" to "synthetic-key"),
                savedAt = now - 1_000L
            ),
            now = now
        )
        assertEquals(SavedCookieAuthState.Valid, health.state)
        assertEquals(1_000L, health.ageMs)
        assertTrue(health.loginCookieKeys.contains("uin"))
        assertTrue(health.loginCookieKeys.contains("qm_keyst"))
    }

    @Test
    fun shouldAutoCompleteQQMusicWebLogin_requiresCompleteLoginCookies() {
        // 登录页清空后，只有 uin(数字) + 音乐密钥同时出现才判定完成
        assertFalse(shouldAutoCompleteQQMusicWebLogin(emptyMap()))
        assertTrue(
            shouldAutoCompleteQQMusicWebLogin(
                mapOf("uin" to "123456", "qm_keyst" to "synthetic-key")
            )
        )
        assertFalse(shouldAutoCompleteQQMusicWebLogin(mapOf("uin" to "123456")))
    }
}
