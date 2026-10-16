package com.incar.assistant.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent

/**
 * 车载音乐多媒体深度控制模块
 * 深度适配 网易云音乐 与 汽水音乐
 */
class MediaMusicController(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    companion object {
        const val PKG_NETEASE = "com.netease.cloudmusic"
        const val PKG_LUNA = "com.luna.music" // 汽水音乐
    }

    /**
     * 系统通用媒体物理按键模拟（支持后台全局播控）
     */
    fun sendMediaKeyEvent(keyCode: Int) {
        val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager.dispatchMediaKeyEvent(down)
        audioManager.dispatchMediaKeyEvent(up)
    }

    fun playOrPause() = sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    fun nextTrack() = sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
    fun prevTrack() = sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    /**
     * 网易云音乐定向点歌 / 歌手 / 歌单
     */
    fun playNeteaseSong(keyword: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("orpheus://search/$keyword")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // 兜底：通用媒体搜索意图
            playBySearchIntent(keyword, PKG_NETEASE)
        }
    }

    /**
     * 汽水音乐拉起与播控
     */
    fun playLunaMusic(keyword: String = "") {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(PKG_LUNA)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                // 模拟自动点击播放
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY)
            }
        } catch (e: Exception) {
            playBySearchIntent(keyword, PKG_LUNA)
        }
    }

    private fun playBySearchIntent(query: String, targetPackage: String) {
        val intent = Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(android.app.SearchManager.QUERY, query)
            setPackage(targetPackage)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (ignored: Exception) {}
    }
}
