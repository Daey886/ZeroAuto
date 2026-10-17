package com.incar.assistant.amap

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * 高德地图车机版 (AmapAuto) 标准协议深度交互控制器
 * 严格依据高德车机版官方开放规范开发：
 * 1. 纯地名/关键字：使用官方标准 URI 协议 `androidauto://keywordNavi?keywords=...&style=2` (搜索+直接导航一体化)
 * 2. 具备经纬度：使用官方标准广播 10007 协议或 10038 POI 导航广播
 * 3. 附近搜索：使用官方标准 URI 协议 `androidauto://arroundpoi?keywords=...&style=2`
 * 4. 特殊目的地 (家/公司)：使用官方标准 URI `androidauto://navi2SpecialDest?dest=home/crop`
 */
class AmapAutoController(private val context: Context) {

    companion object {
        private const val TAG = "AmapAutoController"

        // 高德车机版官方包名与广播组件
        const val PACKAGE_NAME = "com.autonavi.amapauto"
        const val RECEIVER_NAME = "com.autonavi.amapauto.adapter.internal.AmapAutoBroadcastReceiver"
        const val BROADCAST_ACTION = "AUTONAVI_STANDARD_BROADCAST_RECV"

        // 协议指令 Key
        const val KEY_TYPE = "KEY_TYPE"

        // 官方标准指令集
        const val TYPE_START_NAVI = 10007         // 坐标直接算路导航 (必须携带有效经纬度)
        const val TYPE_KEYWORD_SEARCH = 10036     // 关键字搜索/选点
        const val TYPE_POI_NAVI = 10038          // POI 导航
        const val TYPE_GO_HOME_OR_COMP = 10040    // 导航到家或公司
        const val TYPE_CHOOSE_CANDIDATE = 10038   // 候选地点消歧选择 (第几个)
        const val TYPE_EXIT_NAVI = 10010          // 退出导航 (部分车机为 10021)
        const val TYPE_EXIT_NAVI_ALT = 10021      // 退出导航备用码
        const val TYPE_ADD_WAYPOINT = 10042       // 增加途经点
        const val TYPE_SEARCH_ALONG_ROUTE = 10043 // 沿途搜 (加油站/服务区/厕所/充电桩)
        const val TYPE_DEL_WAYPOINT = 10044       // 删除途经点
        const val TYPE_SET_MUTE = 10047           // 播报静音 / 取消静音
        const val TYPE_SET_DAY_NIGHT = 10048      // 昼夜模式 (0=自动, 1=白天, 2=夜晚)
        const val TYPE_ZOOM_IN = 10027            // 放大地图
        const val TYPE_ZOOM_OUT = 10028           // 缩小地图
    }

    /**
     * 发送高德车机版广播
     */
    private fun sendAmapBroadcast(intentBuilder: Intent.() -> Unit) {
        try {
            // 显式组件广播 (最权威接收点)
            val componentIntent = Intent(BROADCAST_ACTION).apply {
                component = ComponentName(PACKAGE_NAME, RECEIVER_NAME)
                setPackage(PACKAGE_NAME)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                intentBuilder()
            }
            context.sendBroadcast(componentIntent)

            // 补充普通显式包广播
            val packageIntent = Intent(BROADCAST_ACTION).apply {
                setPackage(PACKAGE_NAME)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                intentBuilder()
            }
            context.sendBroadcast(packageIntent)
        } catch (e: Exception) {
            Log.e(TAG, "sendAmapBroadcast failed: ${e.message}")
        }
    }

    /**
     * 发起目的地导航 (核心入口)
     * 解决“路线请求失败”痛点：
     * - 高德车机版规范中，广播 10007 强制要求经纬度 (EXTRA_DLAT, EXTRA_DLON)；若传入 0.0 或空，车机算路内核必报“路线请求失败”！
     * - 因此：
     *   1. 纯文字地名（如“导航到玉环市民中心”）：
     *      严禁发送空坐标的 10007！
     *      优先使用高德车机版专用的 `androidauto://keywordNavi?keywords=...&style=2` URI 拉起；
     *      同步辅助发送 10036 关键字广播；高德车机端会自动检索并选点直接算路，绝不报错。
     *   2. 具备经纬度坐标时（例如来自反查或收藏夹）：
     *      发送严格符合规范的 10007 广播，补齐全套坐标与偏好参数。
     */
    fun startNavigation(destinationName: String, lat: Double = 0.0, lon: Double = 0.0, dev: Int = 0) {
        val dest = destinationName.trim()
        if (dest.isEmpty()) return

        // 特殊快捷目的地：回家 / 去公司
        if (dest == "家" || dest == "回家") {
            navigateSpecial("home")
            return
        } else if (dest == "公司" || dest == "去公司" || dest == "单位") {
            navigateSpecial("crop")
            return
        }

        if (lat != 0.0 && lon != 0.0) {
            // ================= 模式 A：具备物理坐标 (严格遵守 10007 官方广播规范) =================
            sendAmapBroadcast {
                putExtra(KEY_TYPE, TYPE_START_NAVI)
                putExtra("EXTRA_DNAME", dest)
                putExtra("EXTRA_DLAT", lat)
                putExtra("EXTRA_DLON", lon)
                putExtra("ENTRY_LAT", lat)
                putExtra("ENTRY_LON", lon)
                putExtra("EXTRA_DEV", dev) // 0=GCJ-02, 1=WGS84
                putExtra("EXTRA_M", -1)    // -1=默认规划策略
                putExtra("SOURCE_APP", "ZeroAuto")
            }

            // 同步调起 route URI 兜底
            try {
                val routeUri = "androidauto://route?sourceApplication=ZeroAuto&dlat=$lat&dlon=$lon&dname=${Uri.encode(dest)}&dev=$dev&m=0"
                val uriIntent = Intent(Intent.ACTION_VIEW, Uri.parse(routeUri)).apply {
                    setPackage(PACKAGE_NAME)
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(uriIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Launch route URI failed: ${e.message}")
            }
        } else {
            // ================= 模式 B：纯文本地名关键字 (绝大部分语音指令) =================
            // 严禁发送无坐标的 10007！采用高德车机版官方认证的 keywordNavi URI 协议：
            try {
                val encodedDest = Uri.encode(dest)
                // 优先通道 1：keywordNavi（搜索+导航一体化，高德车机专有接口，直接搜出地点并进入算路导航）
                val naviUri = "androidauto://keywordNavi?sourceApplication=ZeroAuto&keywords=$encodedDest&style=2"
                val naviIntent = Intent(Intent.ACTION_VIEW, Uri.parse(naviUri)).apply {
                    setPackage(PACKAGE_NAME)
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(naviIntent)
                Log.i(TAG, "Successfully invoked AmapAuto keywordNavi for: $dest")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start keywordNavi URI: ${e.message}, fallback to poi")
                // 备用通道：poi 搜索页
                try {
                    val poiUri = "androidauto://poi?sourceApplication=ZeroAuto&keywords=${Uri.encode(dest)}"
                    val poiIntent = Intent(Intent.ACTION_VIEW, Uri.parse(poiUri)).apply {
                        setPackage(PACKAGE_NAME)
                        addCategory(Intent.CATEGORY_DEFAULT)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(poiIntent)
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed to start poi URI: ${e2.message}")
                }
            }

            // 同步发送 10036 纯关键字搜索广播 (不传 10007，杜绝算路失败弹窗)
            sendAmapBroadcast {
                putExtra(KEY_TYPE, TYPE_KEYWORD_SEARCH)
                putExtra("KEYWORD", dest)
                putExtra("KEYWORDS", dest)
                putExtra("SOURCE_APP", "ZeroAuto")
            }
        }
    }

    /**
     * 一键导航到家/去公司 (高德车机官方专有 URI)
     * dest: "home" 或 "crop"
     */
    fun navigateSpecial(destType: String) {
        val target = if (destType.contains("home") || destType.contains("家")) "home" else "crop"
        try {
            val uri = "androidauto://navi2SpecialDest?sourceApplication=ZeroAuto&dest=$target"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                setPackage(PACKAGE_NAME)
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.i(TAG, "navigateSpecial invoked: $target")
        } catch (e: Exception) {
            Log.e(TAG, "navigateSpecial failed: ${e.message}")
            // 广播兜底 10040
            sendAmapBroadcast {
                putExtra(KEY_TYPE, TYPE_GO_HOME_OR_COMP)
                putExtra("DEST", if (target == "home") 0 else 1)
                putExtra("IS_START_NAVI", 0)
                putExtra("SOURCE_APP", "ZeroAuto")
            }
        }
    }

    /**
     * 退出导航 (广播 10010 与 10021 双发兜底)
     */
    fun exitNavigation() {
        sendAmapBroadcast {
            putExtra(KEY_TYPE, TYPE_EXIT_NAVI)
            putExtra("SOURCE_APP", "ZeroAuto")
        }
        sendAmapBroadcast {
            putExtra(KEY_TYPE, TYPE_EXIT_NAVI_ALT)
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
     * 沿途搜索 / 附近搜索 (加油站/服务区/厕所/充电桩)
     * 使用高德车机版专用的 androidauto://arroundpoi
     */
    fun searchAlongRoute(keyword: String) {
        try {
            val encoded = Uri.encode(keyword)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("androidauto://arroundpoi?sourceApplication=ZeroAuto&keywords=$encoded&style=2")
                setPackage(PACKAGE_NAME)
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "arroundpoi URI failed, fallback to broadcast: ${e.message}")
            sendAmapBroadcast {
                putExtra(KEY_TYPE, TYPE_SEARCH_ALONG_ROUTE)
                putExtra("KEYWORD", keyword)
                putExtra("KEYWORDS", keyword)
                putExtra("SOURCE_APP", "ZeroAuto")
            }
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

    /**
     * 昼夜模式切换
     * state: 0=自动, 1=白天, 2=夜晚
     */
    fun setDayNightMode(state: Int) {
        sendAmapBroadcast {
            putExtra(KEY_TYPE, TYPE_SET_DAY_NIGHT)
            putExtra("EXTRA_STATE", state)
            putExtra("SOURCE_APP", "ZeroAuto")
        }
    }
}
