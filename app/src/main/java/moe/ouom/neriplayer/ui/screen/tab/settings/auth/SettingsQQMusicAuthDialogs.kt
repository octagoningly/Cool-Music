package moe.ouom.neriplayer.ui.screen.tab.settings.auth

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.activity.auth.QQMusicQrLoginActivity
import moe.ouom.neriplayer.core.di.AppContainer
import org.json.JSONObject

@Composable
internal fun SettingsQQMusicAuthDialogs(
    showSheet: Boolean,
    initialTab: Int,
    inlineMsg: String?,
    onInlineMsgChange: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    if (!showSheet) return
    val context = LocalContext.current
    val resources = LocalResources.current
    val loginLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val json = result.data?.getStringExtra(QQMusicQrLoginActivity.RESULT_COOKIE) ?: "{}"
            AppContainer.qqMusicCookieRepo.saveCookies(parseCookieMap(json))
        } else {
            onInlineMsgChange(resources.getString(R.string.settings_cookie_cancelled))
        }
    }
    SettingsCookieLoginSheet(
        title = stringResource(R.string.settings_qq_music),
        initialTab = initialTab,
        inlineMsg = inlineMsg,
        onInlineMsgChange = onInlineMsgChange,
        onDismiss = onDismiss,
        browserTabLabel = stringResource(R.string.login_qr),
        browserButtonLabel = stringResource(R.string.qq_music_login_choice_qr),
        browserHintContent = {
            Text(
                stringResource(R.string.qq_music_qr_login_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        cookieLabel = stringResource(R.string.login_paste_cookie_hint),
        onBrowserLogin = {
            onInlineMsgChange(null)
            AppContainer.pauseYouTubeBackgroundWebWorkForForegroundLogin()
            loginLauncher.launch(Intent(context, QQMusicQrLoginActivity::class.java))
        },
        onSaveCookie = { rawCookie ->
            if (rawCookie.isBlank()) {
                onInlineMsgChange(resources.getString(R.string.settings_cookie_input_hint))
            } else {
                AppContainer.qqMusicCookieRepo.saveCookies(parseRawCookie(rawCookie))
                onDismiss()
            }
        }
    )
}

private fun parseCookieMap(json: String): Map<String, String> = runCatching {
    val obj = JSONObject(json)
    buildMap {
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            put(key, obj.optString(key, ""))
        }
    }
}.getOrDefault(emptyMap())

private fun parseRawCookie(raw: String): Map<String, String> = raw
    .split(';')
    .mapNotNull { part ->
        val index = part.indexOf('=')
        if (index <= 0) null else part.substring(0, index).trim() to part.substring(index + 1).trim()
    }
    .toMap()
