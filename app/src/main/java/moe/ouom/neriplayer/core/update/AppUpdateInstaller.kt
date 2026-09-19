package moe.ouom.neriplayer.core.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

object AppUpdateInstaller {
    private const val UPDATE_DIR = "app_updates"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    fun targetFile(context: Context, assetName: String): File {
        val dir = File(context.cacheDir, UPDATE_DIR)
        if (!dir.exists()) dir.mkdirs()
        val safeName = assetName.substringAfterLast('/').ifBlank { "coolmusic-update.apk" }
        return File(dir, safeName)
    }

    suspend fun download(
        context: Context,
        url: String,
        assetName: String,
        onProgress: (percent: Int) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val target = targetFile(context, assetName)
        if (target.exists()) target.delete()
        val tmp = File(target.parentFile, "${target.name}.part")
        if (tmp.exists()) tmp.delete()

        client.newCall(
            Request.Builder()
                .url(url)
                .header("User-Agent", "CoolMusic/${context.packageName}")
                .build()
        ).execute().use { response ->
            if (!response.isSuccessful) {
                error("download failed HTTP ${response.code}")
            }
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 }
            body.byteStream().use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var copied = 0L
                    while (input.read(buffer).also { read = it } >= 0) {
                        coroutineContext.ensureActive()
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        copied += read
                        if (total != null && total > 0) {
                            onProgress(((copied * 100) / total).toInt().coerceIn(0, 100))
                        } else {
                            onProgress(-1)
                        }
                    }
                    output.flush()
                }
            }
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        onProgress(100)
        target
    }

    fun install(context: Context, file: File) {
        if (!file.exists() || file.length() == 0L) {
            error("apk not found")
        }
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openReleasesPage(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(GitHubAppUpdateChecker.RELEASES_PAGE))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
