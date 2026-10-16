package com.incar.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.incar.assistant.R
import com.incar.assistant.amap.AmapAutoController
import com.incar.assistant.car.LocalCarHardwareManager
import com.incar.assistant.media.MediaMusicController
import com.incar.assistant.parser.MultiIntentParser
import com.incar.assistant.system.SystemOptimizeManager
import com.incar.assistant.weather.InCarWeatherManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 车载语音助手前台主守护服务
 * 整合：音频闪避、无感/悬浮/大字三种 UI 模式、模块协同调度
 */
class VoiceAssistantService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 核心模块
    private lateinit var amapController: AmapAutoController
    private lateinit var carHardware: LocalCarHardwareManager
    private lateinit var mediaController: MediaMusicController
    private lateinit var systemManager: SystemOptimizeManager
    private lateinit var weatherManager: InCarWeatherManager
    private lateinit var configManager: com.incar.assistant.config.AppConfigManager
    private lateinit var cloudLlm: com.incar.assistant.llm.CloudLlmAssistant
    private val intentParser = MultiIntentParser()

    // 悬浮窗口管理
    private lateinit var windowManager: WindowManager
    private var floatView: View? = null
    private var tvAsrText: TextView? = null
    private var tvFeedbackText: TextView? = null

    // UI 模式配置: 0=纯语音无感(Zero UI), 1=极简悬浮, 2=大字居中卡片
    private var currentUiMode = 2

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()

        configManager = com.incar.assistant.config.AppConfigManager(this)
        cloudLlm = com.incar.assistant.llm.CloudLlmAssistant(configManager)
        amapController = AmapAutoController(this)
        carHardware = LocalCarHardwareManager(this)
        mediaController = MediaMusicController(this)
        systemManager = SystemOptimizeManager(this)
        weatherManager = InCarWeatherManager(this, configManager)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private fun startForegroundServiceNotification() {
        val channelId = "in_car_voice_assistant"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "车载语音助手运行中",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("ZeroAuto 守护中")
                .setContentText("0云端·0流量·纯本地闭环")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("ZeroAuto 守护中")
                .setContentText("0云端·0流量·纯本地闭环")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build()
        }

        startForeground(1001, notification)
    }

    /**
     * 语音指令统一调度中枢
     */
    fun handleVoiceCommand(rawText: String) {
        showOverlayUi(rawText, "正在处理...")

        val actions = intentParser.parse(rawText)
        val feedbackList = mutableListOf<String>()

        serviceScope.launch {
            for (action in actions) {
                when (action) {
                    is MultiIntentParser.InCarAction.SetWindow -> {
                        val (ok, msg) = carHardware.setWindowPosition(action.target, action.position)
                        feedbackList.add(msg)
                    }
                    is MultiIntentParser.InCarAction.SetHvac -> {
                        val (ok, msg) = carHardware.setHvacTemperature(action.temp)
                        feedbackList.add(msg)
                    }
                    is MultiIntentParser.InCarAction.StartNavi -> {
                        amapController.startNavigation(action.destination)
                        feedbackList.add("正在为您导航去 ${action.destination}")
                    }
                    is MultiIntentParser.InCarAction.AddWaypoint -> {
                        amapController.addWaypoint(action.waypoint)
                        feedbackList.add("已添加途经点 ${action.waypoint}")
                    }
                    is MultiIntentParser.InCarAction.PlayMusic -> {
                        if (action.app == "luna") {
                            mediaController.playLunaMusic(action.keyword)
                        } else {
                            mediaController.playNeteaseSong(action.keyword)
                        }
                        feedbackList.add("正在为您播放 ${action.keyword}")
                    }
                    is MultiIntentParser.InCarAction.OptimizeCar -> {
                        val res = systemManager.performCarOptimization()
                        feedbackList.add("车机已优化，释放了 ${res.freedMemoryMb}M 内存")
                    }
                    is MultiIntentParser.InCarAction.TurnOffScreen -> {
                        systemManager.turnOffScreen()
                    }
                    is MultiIntentParser.InCarAction.QueryWeather -> {
                        val w = weatherManager.queryWeather(action.city)
                        feedbackList.add(w)
                    }
                    is MultiIntentParser.InCarAction.AskCloudLlm -> {
                        updateOverlayFeedback("思考中...")
                        val ans = cloudLlm.askCloudLlm(action.query)
                        feedbackList.add(ans)
                    }
                }
            }

            val finalMsg = if (feedbackList.isEmpty()) "已为您执行" else feedbackList.joinToString("；")
            updateOverlayFeedback(finalMsg)

            // 播报完毕自动延时隐藏悬浮窗（2秒后隐身）
            delay(2500)
            hideOverlayUi()
        }
    }

    /**
     * 弹出自适应车载 UI
     */
    private fun showOverlayUi(asrText: String, feedback: String) {
        if (currentUiMode == 0) return // 纯语音无感模式不弹窗

        if (floatView == null) {
            val inflater = LayoutInflater.from(this)
            floatView = inflater.inflate(R.layout.layout_in_car_card, null)
            tvAsrText = floatView?.findViewById(R.id.tv_asr_text)
            tvFeedbackText = floatView?.findViewById(R.id.tv_feedback_text)

            val params = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
            }

            windowManager.addView(floatView, params)
        }

        tvAsrText?.text = asrText
        tvFeedbackText?.text = feedback
        floatView?.visibility = View.VISIBLE
    }

    private fun updateOverlayFeedback(feedback: String) {
        tvFeedbackText?.text = feedback
    }

    private fun hideOverlayUi() {
        floatView?.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        floatView?.let { windowManager.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
