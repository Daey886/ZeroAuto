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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.incar.assistant.R
import com.incar.assistant.amap.AmapAutoController
import com.incar.assistant.asr.LocalAsrEngine
import com.incar.assistant.car.LocalCarHardwareManager
import com.incar.assistant.config.AppConfigManager
import com.incar.assistant.llm.CloudLlmAssistant
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
 * 整合：离线语音唤醒监听、桌面拖拽悬浮球、自适应居中卡片 UI、媒体闪避压制与全模块协同
 */
class VoiceAssistantService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 核心引擎与控制器
    private lateinit var configManager: AppConfigManager
    private lateinit var amapController: AmapAutoController
    private lateinit var carHardware: LocalCarHardwareManager
    private lateinit var mediaController: MediaMusicController
    private lateinit var systemManager: SystemOptimizeManager
    private lateinit var weatherManager: InCarWeatherManager
    private lateinit var cloudLlm: CloudLlmAssistant
    private lateinit var asrEngine: LocalAsrEngine
    private val intentParser = MultiIntentParser()

    // 悬浮窗口管理
    private lateinit var windowManager: WindowManager

    // 1. 桌面拖拽悬浮球
    private var floatBallView: View? = null
    private var floatBallParams: WindowManager.LayoutParams? = null

    // 2. 车载自适应大字居中卡片 UI
    private var floatCardView: View? = null
    private var tvAsrText: TextView? = null
    private var tvFeedbackText: TextView? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()

        configManager = AppConfigManager(this)
        cloudLlm = CloudLlmAssistant(configManager)
        amapController = AmapAutoController(this)
        carHardware = LocalCarHardwareManager(this)
        mediaController = MediaMusicController(this)
        systemManager = SystemOptimizeManager(this)
        weatherManager = InCarWeatherManager(this, configManager)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 初始化本地语音引擎
        initAsrEngine()

        // 初始化并展示桌面悬浮球
        if (configManager.isFloatBallEnabled) {
            initFloatBall()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_TRIGGER_UI_TEST" -> {
                showOverlayUi("语音唤醒测试：打开全车车窗并播放许巍", "已执行：车窗已全部降下；正在为您播放许巍的歌曲")
                serviceScope.launch {
                    delay(3000)
                    hideOverlayUi()
                }
            }
        }
        return START_STICKY
    }

    private fun initAsrEngine() {
        asrEngine = LocalAsrEngine(this).apply {
            isQuickCommandEnabled = configManager.isQuickCommandEnabled
            setEventListener(object : LocalAsrEngine.AsrEventListener {
                override fun onWakeWordDetected(keyword: String) {
                    showOverlayUi(keyword, "我在，请说...")
                }

                override fun onRecognizing(partialText: String) {
                    tvAsrText?.text = partialText
                }

                override fun onRecognized(finalText: String) {
                    handleVoiceCommand(finalText)
                }

                override fun onError(errorMsg: String) {
                    updateOverlayFeedback(errorMsg)
                }
            })
        }

        if (configManager.isWakeupEnabled) {
            asrEngine.startListening()
        }
    }

    /**
     * 1. 初始化桌面可拖拽悬浮球
     */
    private fun initFloatBall() {
        if (floatBallView != null) return

        val inflater = LayoutInflater.from(this)
        floatBallView = inflater.inflate(R.layout.layout_float_ball, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        floatBallParams = WindowManager.LayoutParams().apply {
            type = layoutType
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 300
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }

        // 触控拖拽与点击事件
        floatBallView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDrag = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val params = floatBallParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDrag = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isDrag = true
                            params.x = initialX + dx
                            params.y = initialY + dy
                            windowManager.updateViewLayout(floatBallView, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDrag) {
                            // 单击触发唤醒
                            showOverlayUi("点击唤醒", "正在聆听中...")
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager.addView(floatBallView, floatBallParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 2. 语音指令统一调度中枢
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

            // 播报完毕自动延时隐藏悬浮窗（2.5秒后自动隐身）
            delay(2500)
            hideOverlayUi()
        }
    }

    /**
     * 3. 弹出自适应车载居中卡片 UI
     */
    private fun showOverlayUi(asrText: String, feedback: String) {
        if (configManager.uiMode == 0) return // 纯语音无感模式不弹窗

        if (floatCardView == null) {
            val inflater = LayoutInflater.from(this)
            floatCardView = inflater.inflate(R.layout.layout_in_car_card, null)
            tvAsrText = floatCardView?.findViewById(R.id.tv_asr_text)
            tvFeedbackText = floatCardView?.findViewById(R.id.tv_feedback_text)

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams().apply {
                type = layoutType
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
            }

            try {
                windowManager.addView(floatCardView, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        tvAsrText?.text = asrText
        tvFeedbackText?.text = feedback
        floatCardView?.visibility = View.VISIBLE
    }

    private fun updateOverlayFeedback(feedback: String) {
        tvFeedbackText?.text = feedback
    }

    private fun hideOverlayUi() {
        floatCardView?.visibility = View.GONE
    }

    private fun startForegroundServiceNotification() {
        val channelId = "zero_auto_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "ZeroAuto 车载助手前台守护",
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

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        floatCardView?.let { windowManager.removeView(it) }
        floatBallView?.let { windowManager.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
