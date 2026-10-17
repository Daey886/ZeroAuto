package com.incar.assistant.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * 车规级语音播报合成中枢 (TTS)
 * 支持多款角色音色切换、语速调节、试听与音频路由
 */
class CarTtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    enum class VoiceRole(val id: String, val displayName: String, val pitch: Float, val speechRate: Float) {
        FEMALE_GENTLE("female_gentle", "知性温和女声 (默认)", 1.0f, 1.05f),
        MALE_MAGNETIC("male_magnetic", "沉稳磁性男声", 0.85f, 1.0f),
        MECHA_CYBER("mecha_cyber", "赛博极客机甲", 1.25f, 1.15f),
        CUTE_GIRL("cute_girl", "灵动元气少女", 1.2f, 1.1f),
        ELDER_WARM("elder_warm", "温暖沉着向导", 0.9f, 0.95f);

        companion object {
            fun fromId(id: String): VoiceRole {
                return values().find { it.id == id } ?: FEMALE_GENTLE
            }
        }
    }

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.CHINESE
            isInitialized = true
        }
    }

    /**
     * 应用角色语音发音特征
     */
    fun applyVoiceRole(roleId: String) {
        val role = VoiceRole.fromId(roleId)
        tts?.setPitch(role.pitch)
        tts?.setSpeechRate(role.speechRate)
    }

    /**
     * 播报语音
     */
    fun speak(text: String, roleId: String = "female_gentle", onDone: (() -> Unit)? = null) {
        if (!isInitialized) return
        applyVoiceRole(roleId)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { onDone?.invoke() }
            override fun onError(utteranceId: String?) { onDone?.invoke() }
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zero_auto_tts_${System.currentTimeMillis()}")
    }

    /**
     * 打断并立即停止当前播报
     */
    fun stop() {
        tts?.stop()
    }

    /**
     * 试听音色
     */
    fun previewRole(roleId: String) {
        val role = VoiceRole.fromId(roleId)
        val previewSentence = "您好，我是零度车控。已为您切换至${role.displayName}，祝您一路平安。"
        speak(previewSentence, roleId)
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
