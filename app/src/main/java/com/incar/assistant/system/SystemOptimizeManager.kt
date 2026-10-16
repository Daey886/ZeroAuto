package com.incar.assistant.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 车机系统级运维与优化管理
 * 具备 ADB / 系统权限时的毫秒级控制与自愈
 */
class SystemOptimizeManager(private val context: Context) {

    private val TAG = "SystemOptimize"

    // 严禁误杀的核心白名单
    private val PROTECTED_PACKAGES = setOf(
        "com.autonavi.amapauto",           // 高德地图车机版
        "com.incar.assistant",             // 本语音助手
        "android.car",                     // 车身核心服务
        "com.android.systemui",            // 状态栏/系统UI
        "com.android.launcher"             // 车机桌面
    )

    /**
     * 强杀指定卡死软件
     */
    fun forceStopApp(packageName: String): Boolean {
        if (PROTECTED_PACKAGES.contains(packageName)) {
            Log.w(TAG, "Cannot force-stop protected package: $packageName")
            return false
        }
        return executeAdbCommand("am force-stop $packageName")
    }

    /**
     * 车机一键优化自愈
     * 1. 释放闲置运存 (killBackgroundProcesses)
     * 2. 执行内存整理与 GC
     * 3. 清理系统临时碎片与缓存 (pm trim-caches)
     */
    fun performCarOptimization(): OptimizationResult {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfoBefore = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memoryInfoBefore)

        val beforeAvailMb = memoryInfoBefore.availMem / (1024 * 1024)

        // 1. 杀灭非白名单的后台闲置进程
        val runningApps = am.runningAppProcesses ?: emptyList()
        var killedCount = 0
        for (app in runningApps) {
            if (!PROTECTED_PACKAGES.contains(app.processName) &&
                app.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE) {
                am.killBackgroundProcesses(app.processName)
                killedCount++
            }
        }

        // 2. 触发底层 GC 与 Trim Caches (ADB 特权)
        executeAdbCommand("pm trim-caches 500M")
        System.gc()

        val memoryInfoAfter = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memoryInfoAfter)
        val afterAvailMb = memoryInfoAfter.availMem / (1024 * 1024)
        val freedMb = (afterAvailMb - beforeAvailMb).coerceAtLeast(0)

        return OptimizationResult(
            killedProcesses = killedCount,
            freedMemoryMb = freedMb,
            currentAvailMb = afterAvailMb
        )
    }

    /**
     * 行车夜间防眩光息屏
     */
    fun turnOffScreen(): Boolean {
        return executeAdbCommand("input keyevent 26")
    }

    /**
     * 返回桌面
     */
    fun returnToHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    private fun executeAdbCommand(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to exec command: $command, error: ${e.message}")
            false
        }
    }

    data class OptimizationResult(
        val killedProcesses: Int,
        val freedMemoryMb: Long,
        val currentAvailMb: Long
    )
}
