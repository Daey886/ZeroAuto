package com.incar.assistant.amap

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * 高德地图车机版标准广播与 URI 交互控制器
 * 兼容标准广播 AUTONAVI_STANDARD_BROADCAST_RECV 与 androidauto:// 协议
 */
class AmapAutoController(private val context: Context) {

    companion object {
        private const val TAG = "AmapAutoController"
        const val BROADCAST_ACTION = "AUTONAVI_STANDARD_BROADCAST_RECV"
        const val PACKAGE_NAME = "com.autonavi.amapauto"
        const val RECEIVER_NAME = "com.autonavi.amapauto.adapter.internal.AmapAutoBroadcastReceiver"

        // 协议指令 Key
        const val KEY_TYPE = "KEY_TYPE"
        const val TYPE_START_NAVI = 10007         // 免确认直接发起导航算路 (需目标坐标)
        const val TYPE_KEYWORD_SEARCH = 10036     // 关键字搜索/直达导航 (支持纯地名文本)
        const val TYPE_CHOOSE_CANDIDATE = 10038   // 候选地点消歧选择 (第几个)
        const val TYPE_EXIT_NAVI = 10010          // 退出导航
        const val TYPE_ADD_WAYPOINT = 10042       // 增加途经点
        const val TYPE_SEARCH_ALONG_ROUTE = 10043 // 沿途搜 (加油站/服务区/厕所/充电桩)
        const val TYPE_DEL_WAYPOINT = 10044       // 删除途经点
        const val TYPE_SET_MUTE = 10047           // 静音 / 取消静音
        const val TYPE_ZOOM_IN = 10027            // 放大地图
        const val TYPE_ZOOM_OUT = 10028           // 缩小地图
    }

    /**
     * 发送高德车机版广播，增加隐式与显式双发机制，提升各类车机系统兼容性
     */
    private fun sendAmapBroadcast(intentBuilder: Intent.() -> Unit) {
        try {
            // 显式广播 (带组件名，Android 8.0+ 优先推荐)
            val explicitIntent = Intent(BROADCAST_ACTION).apply {
                setPackage(PACKAGE_NAME)
                intentBuilder()
            }
            context.sendBroadcast(explicitIntent)

            // 补充发送标准类名意图，保障部分深度定制车机广播接收器捕获
            val componentIntent = Intent(BROADCAST_ACTION).apply {
                setClassName(PACKAGE_NAME, RECEIVER_NAME)
                intentBuilder()
            }
            context.sendBroadcast(componentIntent)
        } catch (e: Exception) {
            Log.e(TAG, "sendAmapBroadcast failed: ${e.message}")
        }
    }

    /**
     * 发起目的地导航
     * 解决“路线请求失败”痛点：
     * 1. 仅有文本地名时：优先走 10036 关键字直达搜索 + androidauto:// 协议，避免 10007 缺少坐标导致车机报错算路失败
     * 2. 具备经纬度坐标时：走标准 10007 协议并严格规范全量字段 (EXTRA_DNAME, EXTRA_DLAT, EXTRA_DLON, ENTRY_LAT, ENTRY_LON, EXTRA_DEV, EXTRA_M)
     */
    fun startNavigation(destinationName: String, lat: Double = 0.0, lon: Double = 0.0, dev: Int = 0) {
        val dest = destinationName.trim()
        if (dest.isEmpty()) return

        if (lat != 0.0 && lon != 0.0) {
            // 模式 A：带精准坐标的免确认直接导航算路 (严格遵照高德车机版 10007 标准规范)
            sendAmapBroadcast {
                putExtra(KEY_TYPE, TYPE_START_NAVI)
                putExtra("EXTRA_DNAME", dest)
                putExtra("DEST", dest) // 兼容旧版
                putExtra("EXTRA_DLAT", lat)
                putExtra("EXTRA_DLON", lon)
                putExtra("LAT", lat)
                putExtra("LON", lon)
                putExtra("ENTRY_LAT", lat)
                putExtra("ENTRY_LON", lon)
                putExtra("EXTRA_DEV", dev) // 0: GCJ-02 火星坐标, 1: WGS84
                putExtra("EXTRA_M", -1)    // -1: 默认当前路线规划偏好
                putExtra("STYLE", 0)       // 0: 速度最快
                putExtra("SOURCE_APP", "ZeroAuto")
            }
        } else {
            // 模式 B：纯地名关键字模式 (绝大部分语音指令如“导航去玉环市民中心”)
            // 发送 10036 关键字搜索直达协议 (高德车机会自动定位该地名并发起算路)
            sendAmapBroadcast {
                putExtra(KEY_TYPE, TYPE_KEYWORD_SEARCH)
                putExtra("KEYWORD", dest)
                putExtra("KEYWORDS", dest)
                putExtra("SOURCE_APP", "ZeroAuto")
            }

            // 兼容补充发送 10007，但补充默认标识与应用名
            sendAmapBroadcast {
                putExtra(KEY_TYPE, TYPE_START_NAVI)
                putExtra("EXTRA_DNAME", dest)
                putExtra("DEST", dest)
                putExtra("POINAME", dest)
                putExtra("EXTRA_DEV", 0)
                putExtra("EXTRA_M", -1)
                putExtra("SOURCE_APP", "ZeroAuto")
            }

            // 同时通过 URI 协议尝试唤起高德车机版原生导航页 (双通道兜底)
            try {
                val uriString = "androidauto://navi?sourceApplication=ZeroAuto&poiname=${Uri.encode(dest)}&style=0"
                val uriIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    setPackage(PACKAGE_NAME)
                }
                context.startActivity(uriIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Launch Amap Auto via URI failed, fallback to broadcast: ${e.message}")
            }
        }
    }

    /**
     * 退出导航
     */
    fun exitNavigation() {
        sendAmapBroadcast {
            putExtra(KEY_TYPE, TYPE_EXIT_NAVI)
            putExtra("SOURCE_APP", "ZeroAuto")
        }
    }

    /**
     * 多地点消歧选定第 N 个 (1-based 或 0-based 容错)
     */
    fun selectCandidate(index: Int) {
        sendAmapBroadcast {
            putExtra(KEY_TYPE, TYPE_CHOOSE_CANDIDATE)
            putExtra("EXTRA_SEARCH_RESULT_INDEX", index)
            putExtra("INDEX", index)
            putExtra("SOURCE_APP", "ZeroAuto")
        }
    }

    /**
     * 添加途经点
     */
    fun addWaypoint(waypointName: String, lat: Double = 0.0, lon: Double = 0.0) {
        sendAmapBroadcast {
            putExtra(KEY_TYPE, TYPE_ADD_WAYPOINT)
            putExtra("DEST", waypointName)
            putExtra("EXTRA_DNAME", waypointName)
            if (lat != 0.0 && lon != 0.0) {
                putExtra("EXTRA_DLAT", lat)
                putExtra("EXTRA_DLON", lon)
                putExtra("LAT", lat)
                putExtra("LON", lon)
                putExtra("EXTRA_DEV", 0)
            }
            putExtra("SOURCE_APP", "ZeroAuto")
        }
    }

    /**
     * 沿途搜索
     */
    fun searchAlongRoute(keyword: String) {
        sendAmapBroadcast {
            putExtra(KEY_TYPE, TYPE_SEARCH_ALONG_ROUTE)
            putExtra("KEYWORD", keyword)
            putExtra("KEYWORDS", keyword)
            putExtra("SOURCE_APP", "ZeroAuto")
        }
    }

    /**
     * 地图缩放控制
     */
    fun zoomIn() {
        sendAmapBroadcast {
            putExtra(KEY_TYPE, TYPE_ZOOM_IN)
            putExtra("SOURCE_APP", "ZeroAuto")
        }
    }

    fun zoomOut() {
        sendAmapBroadcast {
            putExtra(KEY_TYPE, TYPE_ZOOM_OUT)
            putExtra("SOURCE_APP", "ZeroAuto")
        }
    }

    /**
     * 静音开关控制
     */
    fun setMute(mute: Boolean) {
        sendAmapBroadcast {
            putExtra(KEY_TYPE, TYPE_SET_MUTE)
            putExtra("EXTRA_MUTE", if (mute) 1 else 0)
            putExtra("EXTRA_CASUAL_MUTE", if (mute) 1 else 0)
            putExtra("SOURCE_APP", "ZeroAuto")
        }
    }
}
