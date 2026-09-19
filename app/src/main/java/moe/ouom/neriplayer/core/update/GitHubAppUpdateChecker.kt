package moe.ouom.neriplayer.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.BuildConfig
import moe.ouom.neriplayer.util.network.awaitResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class AppUpdateInfo(
    val tagName: String,
    val releaseName: String,
    val notes: String,
    val apkUrl: String,
    val apkAssetName: String,
    val apkSizeBytes: Long?,
    val remoteVersionCode: Long?,
    val currentVersionCode: Int,
    val currentVersionName: String,
    val isNewer: Boolean,
)

object GitHubAppUpdateChecker {
    const val REPO_OWNER = "octagoningly"
    const val REPO_NAME = "Cool-Music"
    const val RELEASES_PAGE = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/latest"
    private const val LATEST_API =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    private val VERSION_CODE_PATTERN = Pattern.compile(
        "versionCode\\s*[=:]\\s*(\\d+)",
        Pattern.CASE_INSENSITIVE
    )

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    suspend fun check(
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        currentVersionName: String = BuildConfig.VERSION_NAME,
    ): AppUpdateInfo = withContext(Dispatchers.IO) {
        val body = client.newCall(
            Request.Builder()
                .url(LATEST_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "CoolMusic/${BuildConfig.VERSION_NAME}")
                .build()
        ).awaitResponse { response ->
            if (!response.isSuccessful) {
                error("GitHub API HTTP ${response.code}")
            }
            response.body.string().orEmpty()
        }
        if (body.isBlank()) error("empty response")
        parseRelease(
            json = JSONObject(body),
            currentVersionCode = currentVersionCode,
            currentVersionName = currentVersionName,
        )
    }

    internal fun parseRelease(
        json: JSONObject,
        currentVersionCode: Int,
        currentVersionName: String,
    ): AppUpdateInfo {
        val tagName = json.optString("tag_name").trim()
        val releaseName = json.optString("name").ifBlank { tagName }
        val notes = json.optString("body").trim()
        val assets = json.optJSONArray("assets")
        var apkUrl = ""
        var apkName = ""
        var apkSize: Long? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                apkUrl = asset.optString("browser_download_url")
                apkName = name
                apkSize = asset.optLong("size").takeIf { it > 0 }
                break
            }
        }
        if (apkUrl.isBlank()) {
            error("latest release has no APK asset")
        }
        val remoteCode = parseVersionCode(notes) ?: parseVersionCodeFromAsset(apkName)
        val isNewer = when {
            remoteCode != null -> remoteCode > currentVersionCode
            tagName.isBlank() -> false
            tagName.contains(currentVersionName) -> false
            else -> true
        }
        return AppUpdateInfo(
            tagName = tagName,
            releaseName = releaseName,
            notes = notes,
            apkUrl = apkUrl,
            apkAssetName = apkName,
            apkSizeBytes = apkSize,
            remoteVersionCode = remoteCode,
            currentVersionCode = currentVersionCode,
            currentVersionName = currentVersionName,
            isNewer = isNewer,
        )
    }

    private fun parseVersionCode(text: String): Long? {
        if (text.isBlank()) return null
        val matcher = VERSION_CODE_PATTERN.matcher(text)
        return if (matcher.find()) matcher.group(1)?.toLongOrNull() else null
    }

    private fun parseVersionCodeFromAsset(assetName: String): Long? {
        val matcher = Pattern.compile("(\\d{8})").matcher(assetName)
        var last: Long? = null
        while (matcher.find()) {
            last = matcher.group(1)?.toLongOrNull() ?: last
        }
        return last?.takeIf { it in 20000000L..29999999L }
    }
}
