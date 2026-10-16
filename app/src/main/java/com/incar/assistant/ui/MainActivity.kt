package com.incar.assistant.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.incar.assistant.R
import com.incar.assistant.config.AppConfigManager
import com.incar.assistant.service.VoiceAssistantService

class MainActivity : AppCompatActivity() {

    private lateinit var configManager: AppConfigManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        configManager = AppConfigManager(this)

        val etLlmUrl = findViewById<EditText>(R.id.et_llm_url)
        val etLlmModel = findViewById<EditText>(R.id.et_llm_model)
        val etLlmKey = findViewById<EditText>(R.id.et_llm_key)
        val etSeniverseUid = findViewById<EditText>(R.id.et_seniverse_uid)
        val etSeniverseKey = findViewById<EditText>(R.id.et_seniverse_key)
        val btnSave = findViewById<Button>(R.id.btn_save)

        // 回显已有配置
        etLlmUrl.setText(configManager.llmBaseUrl)
        etLlmModel.setText(configManager.llmModel)
        etLlmKey.setText(configManager.llmApiKey)
        etSeniverseUid.setText(configManager.seniverseUid)
        etSeniverseKey.setText(configManager.seniverseKey)

        btnSave.setOnClickListener {
            configManager.llmBaseUrl = etLlmUrl.text.toString().trim()
            configManager.llmModel = etLlmModel.text.toString().trim()
            configManager.llmApiKey = etLlmKey.text.toString().trim()
            configManager.seniverseUid = etSeniverseUid.text.toString().trim()
            configManager.seniverseKey = etSeniverseKey.text.toString().trim()

            Toast.makeText(this, "配置已保存，正在启动车机助手服务...", Toast.LENGTH_SHORT).show()

            // 启动前台主守护服务
            val serviceIntent = Intent(this, VoiceAssistantService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            // 提示后移至后台
            moveTaskToBack(true)
        }
    }
}
