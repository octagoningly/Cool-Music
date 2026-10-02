package moe.ouom.neriplayer.activity.auth

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.core.api.qqmusic.QQMusicQrLoginClient
import moe.ouom.neriplayer.core.api.qqmusic.QQMusicQrSession
import moe.ouom.neriplayer.core.api.qqmusic.QQMusicQrStatus
import moe.ouom.neriplayer.core.api.qqmusic.isUsableQQMusicCookies
import org.json.JSONObject

class QQMusicQrLoginActivity : ComponentActivity() {
    companion object {
        const val RESULT_COOKIE = QQWebLoginActivity.RESULT_COOKIE
        private const val POLL_MS = 1_500L
    }

    private val client = QQMusicQrLoginClient()
    private var session: QQMusicQrSession? = null
    private var pollJob: Job? = null
    private lateinit var qrImage: ImageView
    private lateinit var status: TextView
    private lateinit var refresh: MaterialButton
    private var returned = false

    private val webFallback = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            setResult(RESULT_OK, result.data)
            finish()
        } else if (!returned) {
            startQrLogin()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
        buildLayout()
        startQrLogin()
    }

    override fun onDestroy() {
        pollJob?.cancel()
        super.onDestroy()
    }

    private fun buildLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(36, 32, 36, 36)
            background = MaterialColors.getColor(this@QQMusicQrLoginActivity,
                com.google.android.material.R.attr.colorSurface, Color.WHITE).toDrawable()
        }
        val title = TextView(this).apply {
            text = getString(R.string.qq_music_qr_login_title)
            textSize = 26f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        val hint = TextView(this).apply {
            text = getString(R.string.qq_music_qr_login_hint)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 18)
        }
        root.addView(hint, LinearLayout.LayoutParams(-1, -2))
        qrImage = ImageView(this).apply { setPadding(12, 12, 12, 12) }
        root.addView(qrImage, LinearLayout.LayoutParams(260, 260))
        status = TextView(this).apply { gravity = Gravity.CENTER; textSize = 15f }
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        refresh = MaterialButton(this).apply {
            text = getString(R.string.qq_music_qr_refresh)
            setOnClickListener { startQrLogin() }
        }
        root.addView(refresh, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 20 })
        val web = MaterialButton(this).apply {
            text = getString(R.string.qq_music_web_login_fallback)
            setOnClickListener {
                webFallback.launch(Intent(this@QQMusicQrLoginActivity, QQWebLoginActivity::class.java))
            }
        }
        root.addView(web, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    private fun startQrLogin() {
        pollJob?.cancel()
        refresh.isEnabled = false
        status.text = getString(R.string.qq_music_qr_loading)
        pollJob = lifecycleScope.launch {
            val created = withContext(Dispatchers.IO) { client.create() }
            if (created == null) {
                status.text = getString(R.string.qq_music_qr_failed)
                refresh.isEnabled = true
                return@launch
            }
            session = created
            qrImage.setImageBitmap(createQrBitmap(created.imageBytes))
            status.text = getString(R.string.qq_music_qr_waiting)
            refresh.isEnabled = true
            while (!returned) {
                delay(POLL_MS)
                val current = session ?: break
                val result = withContext(Dispatchers.IO) { client.poll(current) }
                when (result.status) {
                    QQMusicQrStatus.WAITING -> status.text = getString(R.string.qq_music_qr_waiting)
                    QQMusicQrStatus.SCANNED -> status.text = getString(R.string.qq_music_qr_scanned)
                    QQMusicQrStatus.CONFIRMED -> {
                        if (isUsableQQMusicCookies(result.cookies)) {
                            returned = true
                            val data = Intent().putExtra(RESULT_COOKIE, JSONObject(result.cookies).toString())
                            setResult(Activity.RESULT_OK, data)
                            finish()
                        } else {
                            status.text = getString(R.string.qq_music_qr_failed)
                            refresh.isEnabled = true
                        }
                        // 授权 code 通常只能消费一次。无论凭据交换成功与否都停止轮询，
                        // 避免对同一个已确认二维码反复执行 OAuth 交换直至过期。
                        break
                    }
                    QQMusicQrStatus.EXPIRED -> {
                        status.text = getString(R.string.qq_music_qr_expired)
                        break
                    }
                    QQMusicQrStatus.REFUSED -> {
                        status.text = getString(R.string.qq_music_qr_refused)
                        refresh.isEnabled = true
                        break
                    }
                    QQMusicQrStatus.FAILED -> {
                        status.text = getString(R.string.qq_music_qr_failed)
                        break
                    }
                }
            }
        }
    }

    private fun createQrBitmap(bytes: ByteArray): Bitmap {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: Bitmap.createBitmap(230, 230, Bitmap.Config.ARGB_8888)
    }
}
