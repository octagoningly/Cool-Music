package moe.ouom.neriplayer.data.source.lxmusic.js

import android.content.Context
import android.util.Base64
import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.QuickJSContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.ouom.neriplayer.core.logging.NPLogger
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

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
 * File: moe.ouom.neriplayer.data.source.lxmusic.js/LxJsSourceRuntime
 * Updated: 2026/3/23
 */

/**
 * LX Music JS 音源运行时（对齐落雪 mobile 的 user-api 协议）。
 * 使用 QuickJS 执行脚本，通过 native bridge 提供 lx.request / crypto / setTimeout。
 */
class LxJsSourceRuntime(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val scriptId: String,
    private val scriptName: String,
    private val script: String
) {
    private val key = UUID.randomUUID().toString()
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "LxJsSource-$scriptId").apply { isDaemon = true }
    }
    private val httpRequestClient = okHttpClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var jsContext: QuickJSContext? = null
    private var loaded = false
    private val pendingMusicUrl = ConcurrentHashMap<String, CompletableDeferred<String?>>()
    private val pendingNativeRequests = ConcurrentHashMap<String, okhttp3.Call>()
    private val timeoutIds = AtomicInteger(0)
    private val timeoutHandler = android.os.Handler(context.mainLooper)

    @Volatile
    var supportedSources: Set<String> = emptySet()
        private set

    @Volatile
    var supportedQualities: Set<String> = emptySet()
        private set

    fun load(): Boolean {
        val latch = java.util.concurrent.CountDownLatch(1)
        var success = false
        executor.execute {
            success = runCatching { loadInternal() }.getOrElse { error ->
                NPLogger.e(TAG, "Load LX JS source failed: ${error.message}")
                false
            }
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)
        loaded = success
        return success
    }

    suspend fun getMusicUrl(
        sourceId: String,
        quality: String,
        musicInfoJson: String,
        timeoutMs: Long = 8_000L
    ): String? = withContext(Dispatchers.IO) {
        if (!loaded && !load()) return@withContext null
        val requestKey = "request__${System.nanoTime()}"
        val deferred = CompletableDeferred<String?>()
        pendingMusicUrl[requestKey] = deferred
        val payload = JSONObject().apply {
            put("requestKey", requestKey)
            put(
                "data",
                JSONObject().apply {
                    put("source", sourceId)
                    put("action", "musicUrl")
                    put(
                        "info",
                        JSONObject().apply {
                            put("type", quality)
                            put("musicInfo", JSONObject(musicInfoJson))
                        }
                    )
                }
            )
        }
        executor.execute {
            runCatching {
                jsContext?.getGlobalObject()?.getJSFunction("__lx_native__")
                    ?.call(key, "request", payload.toString())
            }.onFailure {
                NPLogger.w(TAG, "callJS request failed: ${it.message}")
                pendingMusicUrl.remove(requestKey)?.complete(null)
            }
        }
        withTimeoutOrNull(timeoutMs) { deferred.await() }
    }

    fun destroy() {
        executor.execute {
            runCatching { jsContext?.destroy() }
            jsContext = null
            loaded = false
        }
        pendingMusicUrl.values.forEach { it.complete(null) }
        pendingMusicUrl.clear()
        pendingNativeRequests.clear()
        executor.shutdown()
    }

    private fun loadInternal(): Boolean {
        QuickJSLoader.init()
        val ctx = QuickJSContext.create()
        jsContext = ctx
        createEnvObj(ctx)
        val preload = context.assets.open("script/user-api-preload.js")
            .bufferedReader(StandardCharsets.UTF_8)
            .use { it.readText() }
        ctx.evaluate(preload)
        ctx.getGlobalObject().getJSFunction("lx_setup")
            .call(key, scriptId, scriptName, "", "", "", "", script)
        ctx.evaluate(script)
        // 脚本初始化可能稍晚，给一点时间等待 inited
        Thread.sleep(50)
        return true
    }

    private fun createEnvObj(ctx: QuickJSContext) {
        ctx.getGlobalObject().setProperty("__lx_native_call__") { args ->
            val callKey = args.get(0) as? String ?: return@setProperty null
            if (callKey != key) return@setProperty null
            val action = args.get(1) as? String ?: return@setProperty null
            val data = args.get(2) as? String
            handleNativeCall(action, data)
            null
        }
        ctx.getGlobalObject().setProperty("__lx_native_call__utils_str2b64") { args ->
            val input = args.get(0) as? String ?: return@setProperty ""
            String(Base64.encode(input.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP), StandardCharsets.UTF_8)
        }
        ctx.getGlobalObject().setProperty("__lx_native_call__utils_b642buf") { args ->
            val input = args.get(0) as? String ?: return@setProperty ""
            val bytes = Base64.decode(input.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
            bytes.joinToString(prefix = "[", postfix = "]") { (it.toInt() and 0xFF).toString() }
        }
        ctx.getGlobalObject().setProperty("__lx_native_call__utils_str2md5") { args ->
            val input = args.get(0) as? String ?: return@setProperty ""
            md5Hex(input)
        }
        ctx.getGlobalObject().setProperty("__lx_native_call__utils_aes_encrypt") { args ->
            try {
                val data = args.get(0) as String
                val key = args.get(1) as String
                val iv = args.get(2) as String
                val mode = args.get(3) as String
                aesEncrypt(data, key, iv, mode)
            } catch (_: Exception) {
                ""
            }
        }
        ctx.getGlobalObject().setProperty("__lx_native_call__utils_rsa_encrypt") { args ->
            // RSA 暂不完整实现，返回空串让脚本自行失败并回落
            ""
        }
        ctx.getGlobalObject().setProperty("__lx_native_call__set_timeout") { args ->
            val id = args.get(0)
            val delay = (args.get(1) as? Number)?.toLong() ?: 0L
            timeoutHandler.postDelayed({
                executor.execute {
                    runCatching {
                        jsContext?.getGlobalObject()?.getJSFunction("__lx_native__")
                            ?.call(key, "__set_timeout__", id)
                    }
                }
            }, delay)
            null
        }
    }

    private fun handleNativeCall(action: String, data: String?) {
        when (action) {
            "init" -> {
                val json = JSONObject(data ?: "{}")
                val info = json.optJSONObject("info")
                val sources = info?.optJSONObject("sources")
                val ids = mutableSetOf<String>()
                val qualities = mutableSetOf<String>()
                sources?.keys()?.forEach { sourceId ->
                    ids += sourceId
                    val qualitys = sources.optJSONObject(sourceId)?.optJSONArray("qualitys")
                    if (qualitys != null) {
                        for (i in 0 until qualitys.length()) {
                            qualitys.optString(i).takeIf { it.isNotBlank() }?.let { qualities += it }
                        }
                    }
                }
                if (ids.isNotEmpty()) supportedSources = ids
                if (qualities.isNotEmpty()) supportedQualities = qualities
                NPLogger.d(TAG, "LX JS inited: sources=$ids qualities=$qualities")
            }
            "request" -> {
                val json = JSONObject(data ?: return)
                val requestKey = json.optString("requestKey")
                val url = json.optString("url")
                val options = json.optJSONObject("options") ?: JSONObject()
                if (requestKey.isBlank() || url.isBlank()) return
                val request = runCatching { buildHttpRequest(url, options) }.getOrNull() ?: return
                val call = httpRequestClient.newCall(request)
                pendingNativeRequests[requestKey] = call
                Thread {
                    try {
                        call.execute().use { resp ->
                            val body = resp.body.string()
                            val headers = JSONObject()
                            resp.headers.forEach { (name, value) ->
                                headers.put(name, headers.optString(name, "") + value)
                            }
                            val parsedBody = try {
                                JSONObject(body)
                                body
                            } catch (_: Exception) {
                                body
                            }
                            val response = JSONObject().apply {
                                put("requestKey", requestKey)
                                put("error", JSONObject.NULL)
                                put(
                                    "response",
                                    JSONObject().apply {
                                        put("statusCode", resp.code)
                                        put("statusMessage", resp.message)
                                        put("headers", headers)
                                        put("body", parsedBody)
                                    }
                                )
                            }
                            callJs("response", response.toString())
                        }
                    } catch (e: Exception) {
                        val response = JSONObject().apply {
                            put("requestKey", requestKey)
                            put("error", e.message ?: "request failed")
                            put("response", JSONObject.NULL)
                        }
                        callJs("response", response.toString())
                    } finally {
                        pendingNativeRequests.remove(requestKey)
                    }
                }.start()
            }
            "cancelRequest" -> {
                val requestKey = data ?: return
                pendingNativeRequests.remove(requestKey)?.let {
                    runCatching { it.cancel() }
                }
            }
            "response" -> {
                val json = JSONObject(data ?: return)
                val requestKey = json.optString("requestKey")
                val status = json.optBoolean("status", false)
                val result = json.optJSONObject("result")
                val deferred = pendingMusicUrl.remove(requestKey) ?: return
                val url = if (status) {
                    result?.optJSONObject("data")?.optString("url")?.takeIf { it.startsWith("http") }
                } else {
                    null
                }
                deferred.complete(url)
            }
            "log" -> {
                NPLogger.d(TAG, "LX JS log: ${data?.take(400)}")
            }
        }
    }

    private fun buildHttpRequest(url: String, options: JSONObject): Request {
        val method = options.optString("method", "get").lowercase()
        val headers = options.optJSONObject("headers") ?: JSONObject()
        val builder = Request.Builder().url(url)
        headers.keys().forEach { name ->
            builder.header(name, headers.optString(name))
        }
        if (!headers.has("User-Agent") && !headers.has("user-agent")) {
            builder.header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
            )
        }
        val timeoutMs = options.optLong("timeout", 13_000L)
        // timeout 由 client 统一控制，这里仅记录
        if (method == "post") {
            val form = options.optJSONObject("form")
            val bodyObj = options.opt("body")
            val requestBody = when {
                form != null -> {
                    val fb = FormBody.Builder()
                    form.keys().forEach { key -> fb.add(key, form.optString(key)) }
                    fb.build()
                }
                bodyObj is JSONObject ->
                    bodyObj.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                bodyObj is String && bodyObj.isNotBlank() ->
                    bodyObj.toRequestBody("application/json; charset=utf-8".toMediaType())
                else -> ByteArray(0).toRequestBody(null)
            }
            builder.post(requestBody)
        } else {
            builder.get()
        }
        if (timeoutMs > 0) {
            // no-op: client already has timeouts
        }
        return builder.build()
    }

    private fun callJs(action: String, data: String) {
        executor.execute {
            runCatching {
                jsContext?.getGlobalObject()?.getJSFunction("__lx_native__")
                    ?.call(key, action, data)
            }.onFailure {
                NPLogger.w(TAG, "callJs($action) failed: ${it.message}")
            }
        }
    }

    private fun md5Hex(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun aesEncrypt(dataB64: String, keyB64: String, ivB64: String, mode: String): String {
        val data = Base64.decode(dataB64, Base64.NO_WRAP)
        val key = Base64.decode(keyB64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(mode)
        val secretKey = SecretKeySpec(key, "AES")
        if (ivB64.isBlank() || mode.contains("ECB", ignoreCase = true)) {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        } else {
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            val finalIv = ByteArray(16)
            System.arraycopy(iv, 0, finalIv, 0, minOf(iv.size, 16))
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(finalIv))
        }
        return String(Base64.encode(cipher.doFinal(data), Base64.NO_WRAP), StandardCharsets.UTF_8)
    }

    private companion object {
        private const val TAG = "LxJsSourceRuntime"
    }
}
