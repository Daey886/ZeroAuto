package com.incar.assistant.voice

import android.content.Context
import com.incar.assistant.audio.CarAudioFocusController
import com.incar.assistant.config.AppConfigManager
import com.incar.assistant.tts.CarTtsManager

/** Offline-first speech facade. Every utterance flushes immediately and ducks media. */
class OfflineVoiceCoordinator(context: Context, private val config: AppConfigManager) {
    private val focus = CarAudioFocusController(context)
    private val tts = CarTtsManager(context)

    fun speak(text: String, onStart: (() -> Unit)? = null, onDone: (() -> Unit)? = null) {
        if (config.isAudioDuckEnabled) focus.begin()
        onStart?.invoke()
        tts.speak(text, config.voiceRole, onDone = {
            if (config.isAudioDuckEnabled) focus.end()
            onDone?.invoke()
        })
    }

    fun stop() {
        tts.stop()
        if (config.isAudioDuckEnabled) focus.end()
    }

    fun preview(role: String) = tts.previewRole(role)
    fun release() { tts.release(); focus.end() }
}
