package com.incar.assistant.router

import android.content.Context
import com.incar.assistant.amap.AmapAutoController
import com.incar.assistant.car.LocalCarHardwareManager
import com.incar.assistant.media.MediaMusicController

sealed class RouteResult { data class Local(val message: String): RouteResult(); data object Cloud: RouteResult(); data class Unknown(val message: String): RouteResult() }

/** Deterministic local command router. Cloud is only selected when no local intent matches. */
class IntentRouter(context: Context) {
    private val amap = AmapAutoController(context)
    private val media = MediaMusicController(context)
    private val car = LocalCarHardwareManager(context)

    fun route(command: String): RouteResult {
        val text = command.trim()
        if (text.isEmpty()) return RouteResult.Unknown("没有听清，请再说一次")
        when {
            text.startsWith("导航到") || text.startsWith("去") -> {
                amap.startNavigation(text.removePrefix("导航到").removePrefix("去").trim())
                return RouteResult.Local("正在为您导航到${text.removePrefix("导航到").removePrefix("去").trim()}")
            }
            text.contains("下一首") || text.contains("切歌") -> { media.next(); return RouteResult.Local("已切换下一首") }
            text.contains("暂停") -> { media.pause(); return RouteResult.Local("已暂停播放") }
            text.contains("继续") || text.contains("播放") -> { media.play(); return RouteResult.Local("继续播放") }
            text.contains("关窗") -> { car.closeAllWindows(); return RouteResult.Local("已关闭车窗") }
            text.contains("开窗") -> { car.openAllWindows(); return RouteResult.Local("已打开车窗") }
            text.contains("退出导航") -> { amap.exitNavigation(); return RouteResult.Local("已退出导航") }
            else -> return RouteResult.Cloud
        }
    }
}
