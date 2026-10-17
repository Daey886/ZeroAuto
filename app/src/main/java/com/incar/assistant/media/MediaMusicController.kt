package com.incar.assistant.media

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.view.KeyEvent

/**
 * 车载音乐多媒体控制中枢
 * 架构：深度定制 (网易云/汽水/QQ/酷我/酷狗) + 全局通用 (MediaSession/AudioManager) + Audio Ducking
 */
class MediaMusicController(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    companion object {
        // 主流车机版与移动端包名
        const val PKG_NETEASE_PHONE = "com.netease.cloudmusic"
        const val PKG_NETEASE_CAR = "com.netease.cloudmusiccar"
        const val PKG_LUNA = "com.luna.music" // 汽水音乐
        const val PKG_QQ_PHONE = "com.tencent.qqmusic"
        const val PKG_QQ_CAR = "com.tencent.qqmusiccar"
        const val PKG_KUWO_CAR = "cn.kuwo.kwplayer.car"
        const val PKG_KUWO_PHONE = "cn.kuwo.player"
        const val PKG_KUGOU_CAR = "com.kugou.android.auto"
        const val PKG_KUGOU_PHONE = "com.kugou.android"
    }

    /**
     * 1. 音频智能闪避 (Audio Ducking)
     * 唤醒或助手播报时压低背景音乐至 20%~30%，交互结束后恢复
     */
    fun startAudioDucking() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .build()
                audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (ignored: Exception) {}
    }

    fun stopAudioDucking() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (ignored: Exception) {}
    }

    /**
     * 2. 音量硬件总线精细调节 (0~100 百分比)
     */
    fun setMusicVolumePercent(percent: Int) {
        try {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = (maxVol * (percent.coerceIn(0, 100) / 100f)).toInt()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        } catch (ignored: Exception) {}
    }

    fun setVoiceVolumePercent(percent: Int) {
        try {
            val streamType = AudioManager.STREAM_NOTIFICATION
            val maxVol = audioManager.getStreamMaxVolume(streamType)
            val target = (maxVol * (percent.coerceIn(0, 100) / 100f)).toInt()
            audioManager.setStreamVolume(streamType, target, 0)
        } catch (ignored: Exception) {}
    }

    /**
     * 3. 系统通用媒体物理按键模拟（支持后台全局播控，100%全兼容）
     */
    fun sendMediaKeyEvent(keyCode: Int) {
        val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager.dispatchMediaKeyEvent(down)
        audioManager.dispatchMediaKeyEvent(up)
    }

    fun playOrPause() = sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    fun play() = sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY)
    fun pause() = sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE)
    fun next() = nextTrack()
    fun nextTrack() = sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
    fun prevTrack() = sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    /**
     * 4. 动态分发点歌 (根据用户设置的音乐平台或语音显式指定)
     */
    fun dispatchPlayMusic(keyword: String, targetPlatform: String = "universal") {
        when (targetPlatform.lowercase()) {
            "netease" -> playNeteaseSong(keyword)
            "luna" -> playLunaMusic(keyword)
            "qqmusic" -> playQQMusic(keyword)
            "kuwo" -> playKuwoMusic(keyword)
            "kugou" -> playKugouMusic(keyword)
            else -> playUniversalMusic(keyword)
        }
    }

    /**
     * 网易云音乐 (车机版/手机版双向兼容拉起)
     */
    fun playNeteaseSong(keyword: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("orpheus://search/$keyword")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val pkg = resolveInstalledPackage(PKG_NETEASE_CAR, PKG_NETEASE_PHONE)
            playBySearchIntent(keyword, pkg)
        }
    }

    /**
     * 汽水音乐
     */
    fun playLunaMusic(keyword: String = "") {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(PKG_LUNA)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY)
            } else {
                playBySearchIntent(keyword, PKG_LUNA)
            }
        } catch (e: Exception) {
            playBySearchIntent(keyword, PKG_LUNA)
        }
    }

    /**
     * QQ音乐车机版 / 手机版
     */
    fun playQQMusic(keyword: String) {
        val pkg = resolveInstalledPackage(PKG_QQ_CAR, PKG_QQ_PHONE)
        try {
            // QQ音乐专有拉起协议
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("qqmusic://qq.com/other/search?keyword=$keyword")
                setPackage(pkg)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            playBySearchIntent(keyword, pkg)
        }
    }

    /**
     * 酷我音乐车机版
     */
    fun playKuwoMusic(keyword: String) {
        val pkg = resolveInstalledPackage(PKG_KUWO_CAR, PKG_KUWO_PHONE)
        playBySearchIntent(keyword, pkg)
    }

    /**
     * 酷狗音乐车机版
     */
    fun playKugouMusic(keyword: String) {
        val pkg = resolveInstalledPackage(PKG_KUGOU_CAR, PKG_KUGOU_PHONE)
        playBySearchIntent(keyword, pkg)
    }

    /**
     * 通用标准搜索意图 (适用于车机所有遵循 Android Media 规范的播放器)
     */
    fun playUniversalMusic(query: String) {
        val intent = Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(android.app.SearchManager.QUERY, query)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // 若无响应，模拟发送播放按键
            sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY)
        }
    }

    private fun playBySearchIntent(query: String, targetPackage: String) {
        val intent = Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(android.app.SearchManager.QUERY, query)
            if (targetPackage.isNotEmpty()) {
                setPackage(targetPackage)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY)
        }
    }

    private fun resolveInstalledPackage(carPkg: String, phonePkg: String): String {
        return try {
            context.packageManager.getPackageInfo(carPkg, 0)
            carPkg
        } catch (e: Exception) {
            try {
                context.packageManager.getPackageInfo(phonePkg, 0)
                phonePkg
            } catch (ex: Exception) {
                carPkg
            }
        }
    }
}
