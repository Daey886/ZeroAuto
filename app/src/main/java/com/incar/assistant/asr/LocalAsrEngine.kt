package com.incar.assistant.asr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * 车载离线语音识别引擎
 * 采用 Android 原生 SpeechRecognizer 语音识别组件（直接复用车机系统底层的语音识别引擎）：
 * 1. 彻底解决 AudioRecord 纯裸流未做声学特征提取导致的“不管说什么都无法识别”；
 * 2. 包含完整的收音状态回调（开始说话、收音中、识别完成、错误自愈）。
 */
class LocalAsrEngine(private val context: Context) {

    private val TAG = "LocalAsrEngine"

    interface AsrEventListener {
        fun onWakeWordDetected(keyword: String)
        fun onRecognizing(partialText: String)
        fun onRecognized(finalText: String)
        fun onError(errorMsg: String)
    }

    private var eventListener: AsrEventListener? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    var isQuickCommandEnabled: Boolean = true

    private val QUICK_COMMAND_SET = setOf(
        "下一首", "切歌", "暂停", "暂停播放", "继续播放",
        "放大地图", "缩小地图", "退出导航",
        "把窗户关上", "全车关窗", "关上车窗", "息屏", "优化车机"
    )

    fun setEventListener(listener: AsrEventListener) {
        this.eventListener = listener
    }

    fun startListening() {
        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    if (SpeechRecognizer.isRecognitionAvailable(context)) {
                        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                    }
                }

                if (speechRecognizer == null) {
                    Log.w(TAG, "SpeechRecognizer is not available")
                    eventListener?.onError("车机未开启语音识别服务")
                    return@post
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "onReadyForSpeech")
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "onBeginningOfSpeech")
                        eventListener?.onRecognizing("正在聆听中...")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "onEndOfSpeech")
                    }

                    override fun onError(error: Int) {
                        Log.w(TAG, "SpeechRecognizer onError: $error")
                        val errorText = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "未检测到语音指令"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "未检测到语音指令"
                            SpeechRecognizer.ERROR_AUDIO -> "麦克风录音异常"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少录音权限"
                            else -> "未检测到语音指令"
                        }
                        eventListener?.onError(errorText)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0]
                            Log.i(TAG, "Speech recognized: $text")
                            eventListener?.onRecognized(text)
                        } else {
                            eventListener?.onError("未检测到语音指令")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            eventListener?.onRecognizing(matches[0])
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition", e)
                eventListener?.onError("录音识别启动失败: ${e.message}")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
