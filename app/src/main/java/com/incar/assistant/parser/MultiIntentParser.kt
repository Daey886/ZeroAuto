package com.incar.assistant.parser

import com.incar.assistant.car.LocalCarHardwareManager

/**
 * 复合多意图并行编译器 (Multi-Intent Pipeline)
 * 将“空调22度，副驾窗开条缝，导航去公司”单句长指令拆解为独立并行执行单元
 */
class MultiIntentParser {

    sealed class InCarAction {
        data class SetWindow(val target: LocalCarHardwareManager.WindowTarget, val position: Int) : InCarAction()
        data class SetHvac(val temp: Float) : InCarAction()
        data class StartNavi(val destination: String) : InCarAction()
        data class AddWaypoint(val waypoint: String) : InCarAction()
        data class PlayMusic(val keyword: String, val app: String = "netease") : InCarAction()
        object OptimizeCar : InCarAction()
        object TurnOffScreen : InCarAction()
        data class QueryWeather(val city: String) : InCarAction()
        data class AskCloudLlm(val query: String) : InCarAction()
    }

    /**
     * 文本意图拆解编译
     */
    fun parse(rawText: String): List<InCarAction> {
        val actions = mutableListOf<InCarAction>()

        // 1. 车窗解析
        if (rawText.contains("主驾") && (rawText.contains("开") || rawText.contains("降"))) {
            val pos = if (rawText.contains("缝") || rawText.contains("透气")) 15 else if (rawText.contains("一半")) 50 else 100
            actions.add(InCarAction.SetWindow(LocalCarHardwareManager.WindowTarget.DRIVER, pos))
        } else if (rawText.contains("副驾") && (rawText.contains("开") || rawText.contains("降"))) {
            val pos = if (rawText.contains("缝") || rawText.contains("透气")) 15 else if (rawText.contains("一半")) 50 else 100
            actions.add(InCarAction.SetWindow(LocalCarHardwareManager.WindowTarget.PASSENGER, pos))
        } else if (rawText.contains("关窗") || rawText.contains("关上车窗")) {
            actions.add(InCarAction.SetWindow(LocalCarHardwareManager.WindowTarget.ALL, 0))
        }

        // 2. 空调解析
        val tempRegex = "(\\d{2})度".toRegex()
        val tempMatch = tempRegex.find(rawText)
        if (tempMatch != null) {
            val temp = tempMatch.groupValues[1].toFloatOrNull()
            if (temp != null && temp in 16.0f..32.0f) {
                actions.add(InCarAction.SetHvac(temp))
            }
        }

        // 3. 导航与途经点解析
        if (rawText.contains("导航去") || rawText.contains("导航到")) {
            val dest = rawText.substringAfter("导航去").substringAfter("导航到").trim()
            if (dest.isNotEmpty()) {
                actions.add(InCarAction.StartNavi(dest))
            }
        } else if (rawText.contains("顺便去") || rawText.contains("途径") || rawText.contains("去趟")) {
            val waypoint = rawText.substringAfter("顺便去").substringAfter("去趟").substringBefore("然后").trim()
            if (waypoint.isNotEmpty()) {
                actions.add(InCarAction.AddWaypoint(waypoint))
            }
        }

        // 4. 车机优化
        if (rawText.contains("优化车机") || rawText.contains("加速车机") || rawText.contains("清理后台")) {
            actions.add(InCarAction.OptimizeCar)
        }

        // 5. 息屏
        if (rawText.contains("息屏") || rawText.contains("关闭屏幕")) {
            actions.add(InCarAction.TurnOffScreen)
        }

        // 6. 云端大模型兜底（未命中任何车控/导航/音乐指令时作为百科外脑问答）
        if (actions.isEmpty() && rawText.length >= 2) {
            actions.add(InCarAction.AskCloudLlm(rawText))
        }

        return actions
    }
}
