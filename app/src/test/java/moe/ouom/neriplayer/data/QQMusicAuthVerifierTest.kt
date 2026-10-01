package moe.ouom.neriplayer.data

import kotlinx.coroutines.runBlocking
import moe.ouom.neriplayer.data.auth.qqmusic.QQMusicAuthVerifyResult
import moe.ouom.neriplayer.data.auth.qqmusic.QQMusicAuthVerifier
import org.junit.Assert.assertEquals
import org.junit.Test

class QQMusicAuthVerifierTest {

    @Test
    fun `verify returns EXPIRED when cookies missing`() = runBlocking {
        val verifier = QQMusicAuthVerifier()
        assertEquals(QQMusicAuthVerifyResult.EXPIRED, verifier.verify(emptyMap()))
        assertEquals(
            QQMusicAuthVerifyResult.EXPIRED,
            verifier.verify(mapOf("uin" to "12345"))
        )
        assertEquals(
            QQMusicAuthVerifyResult.EXPIRED,
            verifier.verify(mapOf("qm_keyst" to "key-without-uin"))
        )
        assertEquals(
            QQMusicAuthVerifyResult.EXPIRED,
            verifier.verify(mapOf("uin" to "not-a-number", "qm_keyst" to "key"))
        )
    }

    @Test
    fun `not logged in code matches profile endpoint contract`() {
        // jsososo/QQMusicApi user.js：code=1000 = 未登录（docs/qq-music-link-notes.md §4）
        assertEquals(1000, QQMusicAuthVerifier.CODE_NOT_LOGGED_IN)
    }
}
