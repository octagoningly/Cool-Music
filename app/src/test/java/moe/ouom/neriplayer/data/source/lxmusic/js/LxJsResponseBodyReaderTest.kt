package moe.ouom.neriplayer.data.source.lxmusic.js

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LxJsResponseBodyReaderTest {

    @Test
    fun `accepts a response at its byte limit`() {
        val body = "12345678".toResponseBody("text/plain".toMediaType())

        assertEquals("12345678", body.readLxJsTextWithLimit(maxBytes = 8L))
    }

    @Test
    fun `rejects an oversized declared response before reading`() {
        val body = "123456789".toResponseBody("text/plain".toMediaType())

        assertThrows(LxJsResponseTooLargeException::class.java) {
            body.readLxJsTextWithLimit(maxBytes = 8L)
        }
    }

    @Test
    fun `rejects an oversized response without content length`() {
        val body = unknownLengthBody("123456789")

        assertThrows(LxJsResponseTooLargeException::class.java) {
            body.readLxJsTextWithLimit(maxBytes = 8L)
        }
    }

    private fun unknownLengthBody(content: String): ResponseBody {
        return object : ResponseBody() {
            private val buffer = Buffer().writeUtf8(content)

            override fun contentType() = "text/plain".toMediaType()

            override fun contentLength(): Long = -1L

            override fun source(): BufferedSource = buffer
        }
    }
}
