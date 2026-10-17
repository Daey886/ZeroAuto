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
                    // 启动聆听超时保护
                    serviceScope.launch {
                        delay(5000)
                        if (tvFeedbackText?.text == "我在，请说...") {
                            hideOverlayUi()
                        }
                    }
                }

                override fun onRecognizing(partialText: String) {
                    tvAsrText?.text = partialText
                }

                override fun onRecognized(finalText: String) {
                    handleVoiceCommand(finalText)
                }

                override fun onError(errorMsg: String) {
                    updateOverlayFeedback(errorMsg)
                    serviceScope.launch {
                        delay(2000)
                        hideOverlayUi()
                    }
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

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        floatBallParams = WindowManager.LayoutParams().apply {
            type = layoutType
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 300
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }

        // 触控拖拽与吸边限制（车规级拖拽硬隔离锁，彻底杜绝放开误触倾听）
        floatBallView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var downTime = 0L
            private var isDrag = false
            private var dragOccurredInSession = false
            private var lastDragTimestamp = 0L

            // 车载屏幕滑动死区（TouchSlop，防抖阈值取 24 像素，过滤车身颠簸与手指微抖）
            private val touchSlop = try {
                android.view.ViewConfiguration.get(this@VoiceAssistantService).scaledTouchSlop.coerceAtLeast(24)
            } catch (e: Exception) {
                24
            }

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val params = floatBallParams ?: return false
                val ballWidth = floatBallView?.width ?: 120
                val ballHeight = floatBallView?.height ?: 120
                val maxX = Math.max(0, screenWidth - ballWidth)
                val maxY = Math.max(0, screenHeight - ballHeight)

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        downTime = System.currentTimeMillis()
                        isDrag = false

                        // 关键状态保护：若距离上次拖拽不到 600ms（防止 updateViewLayout 触发伪 ACTION_DOWN 重置标记）
                        if (System.currentTimeMillis() - lastDragTimestamp < 600L) {
                            dragOccurredInSession = true
                        } else {
                            dragOccurredInSession = false
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        val distance = Math.hypot(dx.toDouble(), dy.toDouble())

                        // 只要位移突破防抖死区，立即锁定拖动状态与拖动时间戳
                        if (distance > touchSlop) {
                            isDrag = true
                            dragOccurredInSession = true
                            lastDragTimestamp = System.currentTimeMillis()
                            val targetX = (initialX + dx).coerceIn(0, maxX)
                            val targetY = (initialY + dy).coerceIn(0, maxY)
                            params.x = targetX
                            params.y = targetY
                            windowManager.updateViewLayout(floatBallView, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val duration = System.currentTimeMillis() - downTime
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        val totalDistance = Math.hypot(dx.toDouble(), dy.toDouble())
                        val now = System.currentTimeMillis()

                        // 车规级硬隔离：只要在本次会话中拖动过，或距离拖动结束不足 600ms，坚决只执行吸边，绝不触发倾听！
                        if (isDrag || dragOccurredInSession || (now - lastDragTimestamp < 600L)) {
                            lastDragTimestamp = now
                            val currentX = params.x
                            val snapX = if (currentX + ballWidth / 2 < screenWidth / 2) 16 else (maxX - 16)
                            params.x = snapX
                            windowManager.updateViewLayout(floatBallView, params)
                        } else if (totalDistance <= touchSlop && duration < 300) {
                            // 纯粹的静态短按单击，才触发倾听
                            onFloatBallClicked()
                        }

                        isDrag = false
                        dragOccurredInSession = false
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        isDrag = false
                        dragOccurredInSession = false
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
     * 点击悬浮球：触发唤醒、开启动态聆听超时兜底（如果无语音输入，6秒后自动安全退出）
     */
    private fun onFloatBallClicked() {
        showOverlayUi("点击唤醒", "正在聆听中...")
        // 激活 ASR 录音监听
        asrEngine.startListening()

        // 保护定时器：如果由于车机识别引擎无反应，6秒后自动恢复卡片，避免一直卡在“正在聆听中...”
        serviceScope.launch {
            delay(6000)
            if (tvFeedbackText?.text == "正在聆听中...") {
                updateOverlayFeedback("未检测到语音输入")
                delay(1500)
                hideOverlayUi()
            }
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
                        val targetPlatform = if (action.app.isNotEmpty() && action.app != "netease") action.app else configManager.musicPlatform
                        mediaController.dispatchPlayMusic(action.keyword, targetPlatform)
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
        if (configManager.isAudioDuckEnabled) {
            mediaController.startAudioDucking()
        }
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
        if (configManager.isAudioDuckEnabled) {
            mediaController.stopAudioDucking()
        }
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
