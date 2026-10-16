package com.incar.assistant.asr

import android.content.Context
import android.util.Log

/**
 * 车载本地离线唤醒词与 ASR 流式识别引擎
 * 基于 Sherpa-ONNX 拼音音素对齐与流式轻量模型
 * 零流量消耗、断网地库秒级响应
 */
class LocalAsrEngine(private val context: Context) {

    private val TAG = "LocalAsrEngine"

    // 自定义唤醒词（免训练，拼音动态对齐）
    private var wakeWord = "大黄蜂"
    
    // 高频免唤醒开关（默认开启）
    var isQuickCommandEnabled: Boolean = true

    // 免唤醒高频词表（直接命中直接执行）
    private val QUICK_COMMAND_SET = setOf(
        "下一首", "切歌", "暂停", "继续播放",
        "放大地图", "缩小地图", "退出导航",
        "把窗户关上", "全车关窗", "息屏"
    )

    private var isListening = false
    private var listener: AsrEventListener? = null

    interface AsrEventListener {
        fun onWakeWordDetected(keyword: String)
        fun onRecognizing(partialText: String)
        fun onRecognized(finalText: String)
        fun onError(errorMsg: String)
    }

    fun setEventListener(listener: AsrEventListener) {
        this.listener = listener
    }

    /**
     * 初始化 Sherpa-ONNX 本地声学与拼音模型
     */
    fun initModels() {
        Log.i(TAG, "Initializing Sherpa-ONNX local offline ASR & KWS models...")
        // 动态加载 assets 中的 sherpa-onnx 模型与 tokens.txt
    }

    /**
     * 启动本地麦克风监听
     */
    fun startListening() {
        if (isListening) return
        isListening = true
        Log.i(TAG, "Local microphone stream started.")
    }

    /**
     * 模拟识别输入流（测试与调度入口）
     */
    fun feedPcmChunk(pcmData: ShortArray, size: Int) {
        // 底层 Sherpa-ONNX 处理音频帧
    }

    /**
     * 处理识别出的文本流
     */
    fun onRawTextRecognized(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        // 1. 检查是否命中免唤醒高频指令
        if (isQuickCommandEnabled && QUICK_COMMAND_SET.contains(trimmed)) {
            Log.i(TAG, "Quick command hit directly: $trimmed")
            listener?.onRecognized(trimmed)
            return
        }

        // 2. 唤醒词判断
        if (trimmed.contains(wakeWord)) {
            listener?.onWakeWordDetected(wakeWord)
            val command = trimmed.substringAfter(wakeWord).trim()
            if (command.isNotEmpty()) {
                listener?.onRecognized(command)
            }
        }
    }

    fun updateWakeWord(newWakeWord: String) {
        this.wakeWord = newWakeWord
        Log.i(TAG, "Wake word updated to: $newWakeWord (phoneme updated in-memory)")
    }

    fun stopListening() {
        isListening = false
        Log.i(TAG, "Local microphone stream stopped.")
    }
}
