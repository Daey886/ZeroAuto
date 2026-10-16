package com.incar.assistant.ui

import android.app.AlertDialog
import android.content.Intent
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
import com.incar.assistant.R
import com.incar.assistant.config.AppConfigManager
import com.incar.assistant.service.VoiceAssistantService

class MainActivity : AppCompatActivity() {

    private lateinit var configManager: AppConfigManager
    private lateinit var tvAuthStatusSummary: TextView

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

        // 检查悬浮窗权限（如果开启悬浮球）
        checkOverlayPermission()

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

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请授予悬浮窗权限以正常使用悬浮球和卡片", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
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
        val etSeniverseUid = dialogView.findViewById<EditText>(R.id.dialog_et_seniverse_uid)
        val etSeniverseKey = dialogView.findViewById<EditText>(R.id.dialog_et_seniverse_key)
        val btnConfirm = dialogView.findViewById<Button>(R.id.dialog_btn_confirm)

        etLlmUrl.setText(configManager.llmBaseUrl)
        etLlmModel.setText(configManager.llmModel)
        etLlmKey.setText(configManager.llmApiKey)
        etSeniverseUid.setText(configManager.seniverseUid)
        etSeniverseKey.setText(configManager.seniverseKey)

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
}
