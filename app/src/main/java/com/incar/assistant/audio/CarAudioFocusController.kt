package com.incar.assistant.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 车规级音频焦点调度中枢 (Audio Focus Controller)
 * 1. 接入 OnAudioFocusChangeListener 监听第三方抢占（如系统紧急避碰、高德测速播报）；
 * 2. 具备看门狗超时自愈（Watchdog），防止底层 TTS 引擎因未触发 onDone 导致车机音乐永久 Duck 变哑；
 * 3. 线程安全与生命周期强一致性保障。
 */
class CarAudioFocusController(context: Context) {
    private val tag = "CarAudioFocus"
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var hasFocus = false

    // 看门狗：如果语音由于车机异常长时间未结束，强制在 15 秒后释放焦点自愈
    private val focusTimeoutWatchdog = Runnable {
        Log.w(tag, "Audio focus held too long (>15s), triggering watchdog release.")
        end()
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.i(tag, "Permanent focus loss (e.g. Navigation guidance).")
                end()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.i(tag, "Transient focus loss.")
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.i(tag, "Audio focus restored.")
            }
        }
    }

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val request = if (Build.VERSION.SDK_INT >= 26) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(focusChangeListener, mainHandler)
            .build()
    } else null

    @Synchronized
    fun begin(): Boolean {
        mainHandler.removeCallbacks(focusTimeoutWatchdog)
        val res = if (Build.VERSION.SDK_INT >= 26) {
            audio.requestAudioFocus(request!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }

        hasFocus = res
        if (hasFocus) {
            // 设定 15 秒兜底看门狗，超时自动还音
            mainHandler.postDelayed(focusTimeoutWatchdog, 15000)
        }
        return res
    }

    @Synchronized
    fun end() {
        mainHandler.removeCallbacks(focusTimeoutWatchdog)
        if (!hasFocus) return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                audio.abandonAudioFocusRequest(request!!)
            } else {
                @Suppress("DEPRECATION")
                audio.abandonAudioFocus(focusChangeListener)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error abandoning audio focus: ${e.message}")
        } finally {
            hasFocus = false
        }
    }
}

