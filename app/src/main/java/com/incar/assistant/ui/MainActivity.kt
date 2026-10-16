package com.incar.assistant.ui

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.incar.assistant.R
import com.incar.assistant.config.AppConfigManager
import com.incar.assistant.service.VoiceAssistantService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class MainActivity : AppCompatActivity() {

    private lateinit var configManager: AppConfigManager
    private lateinit var tvAuthStatusSummary: TextView
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        configManager = AppConfigManager(this)

        // 绑定开关控件
        val switchWakeup = findViewById<Switch>(R.id.switch_wakeup)
        val etWakeupWord = findViewById<EditText>(R.id.et_wakeup_word)
        val switchQuickCmd = findViewById<Switch>(R.id.switch_quick_cmd)
        val switchFloatBall = findViewById<Switch>(R.id.switch_float_ball)
        val switchBootStart = findViewById<Switch>(R.id.switch_boot_start)
        val switchAudioDuck = findViewById<Switch>(R.id.switch_audio_duck)
        val switchCardUi = findViewById<Switch>(R.id.switch_card_ui)

        val btnOpenAuthDialog = findViewById<Button>(R.id.btn_open_auth_dialog)
        tvAuthStatusSummary = findViewById(R.id.tv_auth_status_summary)
        val btnSaveAndApply = findViewById<Button>(R.id.btn_save_and_apply)
        val btnTriggerTestCard = findViewById<Button>(R.id.btn_trigger_test_card)

        // 回显设置开关状态
        switchWakeup.isChecked = configManager.isWakeupEnabled
        etWakeupWord.setText(configManager.wakeupWord)
        switchQuickCmd.isChecked = configManager.isQuickCommandEnabled
        switchFloatBall.isChecked = configManager.isFloatBallEnabled
        switchBootStart.isChecked = configManager.isBootStartEnabled
        switchAudioDuck.isChecked = configManager.isAudioDuckEnabled
        switchCardUi.isChecked = (configManager.uiMode == 1)

        updateAuthStatusSummary()

        // 检查悬浮窗权限与录音权限
        checkAndRequestPermissions()

        // 点击打开独立的授权配置弹窗
        btnOpenAuthDialog.setOnClickListener {
            showAuthDialog()
        }

        // 保存并启动服务
        btnSaveAndApply.setOnClickListener {
            configManager.isWakeupEnabled = switchWakeup.isChecked
            configManager.wakeupWord = etWakeupWord.text.toString().trim()
            configManager.isQuickCommandEnabled = switchQuickCmd.isChecked
            configManager.isFloatBallEnabled = switchFloatBall.isChecked
            configManager.isBootStartEnabled = switchBootStart.isChecked
            configManager.isAudioDuckEnabled = switchAudioDuck.isChecked
            configManager.uiMode = if (switchCardUi.isChecked) 1 else 0

            Toast.makeText(this, "设置已保存，正在启动车机助手服务...", Toast.LENGTH_SHORT).show()

            // 启动前台主守护服务
            val serviceIntent = Intent(this, VoiceAssistantService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            // 延迟退居后台
            moveTaskToBack(true)
        }

        // 测试呼出卡片
        btnTriggerTestCard.setOnClickListener {
            val serviceIntent = Intent(this, VoiceAssistantService::class.java).apply {
                action = "ACTION_TRIGGER_UI_TEST"
            }
            startService(serviceIntent)
            Toast.makeText(this, "已触发车载卡片展示", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestPermissions() {
        // 1. 悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请授予悬浮窗权限以展示悬浮球和车载大字卡片", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        // 2. 动态申请 RECORD_AUDIO 录音声音权限
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                1001
            )
        }
    }

    private fun updateAuthStatusSummary() {
        val llmStatus = if (configManager.isLlmAuthorized()) "已授权 ✓" else "未授权 ✗"
        val seniverseStatus = if (configManager.isSeniverseAuthorized()) "已授权 ✓" else "未授权 ✗"
        tvAuthStatusSummary.text = "当前授权状态：大模型 [$llmStatus] · 心知天气 [$seniverseStatus]"
    }

    private fun showAuthDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_cloud_auth, null)
        val etLlmUrl = dialogView.findViewById<EditText>(R.id.dialog_et_llm_url)
        val etLlmModel = dialogView.findViewById<EditText>(R.id.dialog_et_llm_model)
        val etLlmKey = dialogView.findViewById<EditText>(R.id.dialog_et_llm_key)
        val btnTestLlm = dialogView.findViewById<Button>(R.id.dialog_btn_test_llm)
        val tvLlmResult = dialogView.findViewById<TextView>(R.id.dialog_tv_llm_test_result)

        val etSeniverseUid = dialogView.findViewById<EditText>(R.id.dialog_et_seniverse_uid)
        val etSeniverseKey = dialogView.findViewById<EditText>(R.id.dialog_et_seniverse_key)
        val btnTestWeather = dialogView.findViewById<Button>(R.id.dialog_btn_test_weather)
        val tvWeatherResult = dialogView.findViewById<TextView>(R.id.dialog_tv_weather_test_result)

        val btnConfirm = dialogView.findViewById<Button>(R.id.dialog_btn_confirm)

        etLlmUrl.setText(configManager.llmBaseUrl)
        etLlmModel.setText(configManager.llmModel)
        etLlmKey.setText(configManager.llmApiKey)
        etSeniverseUid.setText(configManager.seniverseUid)
        etSeniverseKey.setText(configManager.seniverseKey)

        // 测试大模型连通性
        btnTestLlm.setOnClickListener {
            val url = etLlmUrl.text.toString().trim()
            val model = etLlmModel.text.toString().trim()
            val key = etLlmKey.text.toString().trim()

            if (url.isEmpty() || key.isEmpty()) {
                tvLlmResult.text = "请先输入 URL 和 Key"
                tvLlmResult.setTextColor(0xFFFF3B30.toInt())
                return@setOnClickListener
            }

            tvLlmResult.text = "正在连通性测试中..."
            tvLlmResult.setTextColor(0xFF0A84FF.toInt())

            scope.launch {
                val (ok, msg) = testLlmConnection(url, model, key)
                if (ok) {
                    tvLlmResult.text = "连接成功 ✓ ($msg)"
                    tvLlmResult.setTextColor(0xFF30D158.toInt())
                } else {
                    tvLlmResult.text = "连接失败: $msg"
                    tvLlmResult.setTextColor(0xFFFF3B30.toInt())
                }
            }
        }

        // 测试心知天气连通性
        btnTestWeather.setOnClickListener {
            val uid = etSeniverseUid.text.toString().trim()
            val key = etSeniverseKey.text.toString().trim()

            if (uid.isEmpty() || key.isEmpty()) {
                tvWeatherResult.text = "请先输入 UID 和 Key"
                tvWeatherResult.setTextColor(0xFFFF3B30.toInt())
                return@setOnClickListener
            }

            tvWeatherResult.text = "正在验证心知天气..."
            tvWeatherResult.setTextColor(0xFF0A84FF.toInt())

            scope.launch {
                val (ok, msg) = testSeniverseConnection(uid, key)
                if (ok) {
                    tvWeatherResult.text = "验证成功 ✓ ($msg)"
                    tvWeatherResult.setTextColor(0xFF30D158.toInt())
                } else {
                    tvWeatherResult.text = "验证失败: $msg"
                    tvWeatherResult.setTextColor(0xFFFF3B30.toInt())
                }
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnConfirm.setOnClickListener {
            configManager.llmBaseUrl = etLlmUrl.text.toString().trim()
            configManager.llmModel = etLlmModel.text.toString().trim()
            configManager.llmApiKey = etLlmKey.text.toString().trim()
            configManager.seniverseUid = etSeniverseUid.text.toString().trim()
            configManager.seniverseKey = etSeniverseKey.text.toString().trim()

            updateAuthStatusSummary()
            Toast.makeText(this, "云端能力授权凭据已更新", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * 连通性测试：调用大模型 OpenAI 兼容接口 /chat/completions
     */
    private suspend fun testLlmConnection(urlStr: String, model: String, apiKey: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            try {
                val endpoint = if (urlStr.endsWith("/chat/completions")) urlStr else "$urlStr/chat/completions"
                val url = URL(endpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.connectTimeout = 5000
                conn.readTimeout = 7000
                conn.doOutput = true

                val payload = JSONObject().apply {
                    put("model", model.ifEmpty { "deepseek-chat" })
                    put("max_tokens", 10)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", "ping")
                        })
                    })
                }

                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

                val code = conn.responseCode
                if (code in 200..299) {
                    Pair(true, "HTTP $code")
                } else {
                    Pair(false, "状态码 $code")
                }
            } catch (e: Exception) {
                Pair(false, e.localizedMessage ?: "连接超时")
            }
        }

    /**
     * 连通性测试：调用心知天气获取当前天气
     */
    private suspend fun testSeniverseConnection(uid: String, key: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            try {
                val ts = System.currentTimeMillis() / 1000
                val ttl = 1800
                val rawParams = "public_key=$uid&ts=$ts&ttl=$ttl"
                val mac = Mac.getInstance("HmacSHA1")
                mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1"))
                val rawHmac = mac.doFinal(rawParams.toByteArray(Charsets.UTF_8))
                val sig = android.util.Base64.encodeToString(rawHmac, android.util.Base64.NO_WRAP)
                val encodedSig = URLEncoder.encode(sig, "UTF-8")

                val queryUrl = "https://api.seniverse.com/v3/weather/now.json?location=taizhou&$rawParams&sig=$encodedSig"
                val url = URL(queryUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                val code = conn.responseCode
                if (code == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val result = json.getJSONArray("results").getJSONObject(0)
                    val now = result.getJSONObject("now")
                    val text = now.getString("text")
                    val temp = now.getString("temperature")
                    Pair(true, "$text ${temp}℃")
                } else {
                    Pair(false, "HTTP $code")
                }
            } catch (e: Exception) {
                Pair(false, e.localizedMessage ?: "连接失败")
            }
        }
}
