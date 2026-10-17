package com.incar.assistant.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.incar.assistant.R
import com.incar.assistant.config.AppConfigManager
import com.incar.assistant.service.VoiceAssistantService
import com.incar.assistant.tts.CarTtsManager

class MainActivity : AppCompatActivity() {
    private lateinit var config: AppConfigManager
    private lateinit var preview: ThemeInteractionView
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(R.layout.activity_main); config=AppConfigManager(this); bind() }
    private fun bind() {
        preview=findViewById(R.id.theme_preview); preview.theme=config.visualTheme
        val wake=findViewById<Switch>(R.id.switch_wakeup);val quick=findViewById<Switch>(R.id.switch_quick);val ball=findViewById<Switch>(R.id.switch_float);val duck=findViewById<Switch>(R.id.switch_duck);val reactive=findViewById<Switch>(R.id.switch_reactive);val boot=findViewById<Switch>(R.id.switch_boot)
        wake.isChecked=config.isWakeupEnabled;quick.isChecked=config.isQuickCommandEnabled;ball.isChecked=config.isFloatBallEnabled;duck.isChecked=config.isAudioDuckEnabled;reactive.isChecked=config.isMusicReactiveEnabled;boot.isChecked=config.isBootStartEnabled
        findViewById<EditText>(R.id.edit_wakeup).setText(config.wakeupWord);findViewById<EditText>(R.id.edit_llm_url).setText(config.llmBaseUrl);findViewById<EditText>(R.id.edit_llm_key).setText(config.llmApiKey);findViewById<EditText>(R.id.edit_model).setText(config.llmModel);findViewById<EditText>(R.id.edit_weather_uid).setText(config.seniverseUid);findViewById<EditText>(R.id.edit_weather_key).setText(config.seniverseKey)
        val music = findViewById<Spinner>(R.id.spinner_music); val voice = findViewById<Spinner>(R.id.spinner_voice)
        music.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("通用媒体", "网易云音乐", "汽水音乐", "QQ音乐", "酷我音乐", "酷狗音乐"))
        voice.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, CarTtsManager.VoiceRole.values().map { it.displayName })
        music.setSelection(listOf("universal", "netease", "luna", "qqmusic", "kuwo", "kugou").indexOf(config.musicPlatform).coerceAtLeast(0))
        voice.setSelection(CarTtsManager.VoiceRole.values().indexOfFirst { it.id == config.voiceRole }.coerceAtLeast(0))
        val musicSeek = findViewById<SeekBar>(R.id.seek_music); val voiceSeek = findViewById<SeekBar>(R.id.seek_voice); musicSeek.progress = config.musicVolume; voiceSeek.progress = config.voiceVolume
        findViewById<Button>(R.id.button_preview_voice).setOnClickListener { CarTtsManager(this).previewRole(CarTtsManager.VoiceRole.values().getOrElse(voice.selectedItemPosition) { CarTtsManager.VoiceRole.FEMALE_GENTLE }.id) }
        listOf(R.id.theme_orb to AppConfigManager.THEME_ORB,R.id.theme_capsule to AppConfigManager.THEME_CAPSULE,R.id.theme_aurora to AppConfigManager.THEME_AURORA).forEach{(id,value)->findViewById<Button>(id).setOnClickListener{config.visualTheme=value;preview.theme=value}}
        findViewById<Button>(R.id.button_save).setOnClickListener{config.isWakeupEnabled=wake.isChecked;config.isQuickCommandEnabled=quick.isChecked;config.isFloatBallEnabled=ball.isChecked;config.isAudioDuckEnabled=duck.isChecked;config.isMusicReactiveEnabled=reactive.isChecked;config.isBootStartEnabled=boot.isChecked;config.wakeupWord=findViewById<EditText>(R.id.edit_wakeup).text.toString();config.llmBaseUrl=findViewById<EditText>(R.id.edit_llm_url).text.toString();config.llmApiKey=findViewById<EditText>(R.id.edit_llm_key).text.toString();config.llmModel=findViewById<EditText>(R.id.edit_model).text.toString();config.seniverseUid=findViewById<EditText>(R.id.edit_weather_uid).text.toString();config.seniverseKey=findViewById<EditText>(R.id.edit_weather_key).text.toString();config.musicVolume=musicSeek.progress;config.voiceVolume=voiceSeek.progress;config.voiceRole=CarTtsManager.VoiceRole.values().getOrElse(voice.selectedItemPosition){CarTtsManager.VoiceRole.FEMALE_GENTLE}.id;config.musicPlatform=listOf("universal","netease","luna","qqmusic","kuwo","kugou").getOrElse(music.selectedItemPosition){"universal"};startService(Intent(this,VoiceAssistantService::class.java));Toast.makeText(this,"设置已保存并应用",Toast.LENGTH_SHORT).show()}
        preview.onTap={preview.state=VoiceVisualState.LISTENING;preview.transcript="正在聆听..."}
        findViewById<Button>(R.id.button_overlay).setOnClickListener{if(!Settings.canDrawOverlays(this))startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))}
    }
}
