package moe.ouom.neriplayer.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

fun isActualSystemDarkTheme(context: Context): Boolean {
    val applicationContext = context.applicationContext
    val uiModeManager = ContextCompat.getSystemService(applicationContext, UiModeManager::class.java)
    if (uiModeManager != null) {
        when (uiModeManager.nightMode) {
            UiModeManager.MODE_NIGHT_YES -> return true
            UiModeManager.MODE_NIGHT_NO -> return false
            UiModeManager.MODE_NIGHT_AUTO,
            UiModeManager.MODE_NIGHT_CUSTOM -> Unit
        }
    }

    val appNightMode = applicationContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    if (appNightMode == Configuration.UI_MODE_NIGHT_YES) {
        return true
    }
    if (appNightMode == Configuration.UI_MODE_NIGHT_NO) {
        return false
    }

    return (Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
}

@Composable
fun rememberActualSystemDarkTheme(): Boolean {
    val configuration = LocalConfiguration.current
    val uiMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return uiMode == Configuration.UI_MODE_NIGHT_YES
}
