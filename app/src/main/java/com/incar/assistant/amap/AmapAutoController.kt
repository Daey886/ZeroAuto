package com.incar.assistant.amap

import android.content.Context
import android.content.Intent

/**
 * 高德地图车机版标准广播控制封装
 * 广播 Action: AUTONAVI_STANDARD_BROADCAST_RECV
 */
class AmapAutoController(private val context: Context) {

    companion object {
        const val BROADCAST_ACTION = "AUTONAVI_STANDARD_BROADCAST_RECV"
        const val PACKAGE_NAME = "com.autonavi.amapauto"
        const val RECEIVER_NAME = "com.autonavi.amapauto.adapter.internal.AmapAutoBroadcastReceiver"

        // 协议指令 Key
        const val KEY_TYPE = "KEY_TYPE"
        const val TYPE_START_NAVI = 10007         // 免确认直接发起导航
        const val TYPE_EXIT_NAVI = 10010          // 退出导航
        const val TYPE_CHOOSE_CANDIDATE = 10038   // 候选地点消歧选择 (第几个)
        const val TYPE_ADD_WAYPOINT = 10042       // 增加途经点
        const val TYPE_SEARCH_ALONG_ROUTE = 10043 // 沿途搜 (加油站/厕所/洗车)
        const val TYPE_DEL_WAYPOINT = 10044       // 删除途经点
        const val TYPE_SET_MUTE = 10047           // 静音 / 取消静音
        const val TYPE_ZOOM_IN = 10027            // 放大地图
        const val TYPE_ZOOM_OUT = 10028           // 缩小地图
    }

    private fun sendAmapBroadcast(intentBuilder: Intent.() -> Unit) {
        val intent = Intent(BROADCAST_ACTION).apply {
            setClassName(PACKAGE_NAME, RECEIVER_NAME)
            intentBuilder()
        }
        context.sendBroadcast(intent)
    }

    /**
     * 发起目的地导航
     */
    fun startNavigation(destinationName: String, lat: Double = 0.0, lon: Double = 0.0, dev: Int = 0) {
        sendAmapBroadcast {
            putExtra(KEY_TYPE, TYPE_START_NAVI)
            putExtra("DEST", destinationName)
            if (lat != 0.0 && lon != 0.0) {
                putExtra("LAT", lat)
                putExtra("LON", lon)
                putExtra("DEV", dev) // 0: GCJ-02 (火星坐标)
            }
            putExtra("STYLE", 0) // 0: 速度最快, 1: 费用少, 2: 距离短, etc.
        }
    }

    /**
     * 退出导航
     */
    fun exitNavigation() {
        sendAmapBroadcast {
            putExtra(KEY_TYPE, TYPE_EXIT_NAVI)
        }
    }

    /**
     * 多地点消歧选定第 N 个
     */
    fun selectCandidate(index: Int) {
        sendAmapBroadcast {
            putExtra(KEY_TYPE, TYPE_CHOOSE_CANDIDATE)
            putExtra("EXTRA_SEARCH_RESULT_INDEX", index)
        }
    }

    /**
     * 添加途经点
     */
    fun addWaypoint(waypointName: String, lat: Double = 0.0, lon: Double = 0.0) {
        sendAmapBroadcast {
            putExtra(KEY_TYPE, TYPE_ADD_WAYPOINT)
            putExtra("DEST", waypointName)
            if (lat != 0.0 && lon != 0.0) {
                putExtra("LAT", lat)
                putExtra("LON", lon)
                putExtra("DEV", 0)
            }
        }
    }

    /**
     * 沿途搜索
     */
    fun searchAlongRoute(keyword: String) {
        sendAmapBroadcast {
            putExtra(KEY_TYPE, TYPE_SEARCH_ALONG_ROUTE)
            putExtra("KEYWORD", keyword)
        }
    }

    /**
     * 地图缩放控制
     */
    fun zoomIn() {
        sendAmapBroadcast { putExtra(KEY_TYPE, TYPE_ZOOM_IN) }
    }

    fun zoomOut() {
        sendAmapBroadcast { putExtra(KEY_TYPE, TYPE_ZOOM_OUT) }
    }

    /**
     * 静音开关控制
     */
    fun setMute(mute: Boolean) {
        sendAmapBroadcast {
            putExtra(KEY_TYPE, TYPE_SET_MUTE)
            putExtra("EXTRA_MUTE", if (mute) 1 else 0)
        }
    }
}
