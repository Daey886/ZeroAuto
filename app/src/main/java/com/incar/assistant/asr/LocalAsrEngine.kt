package com.incar.assistant.asr

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 本地端侧 ASR 离线引擎
 * 针对车机多麦克风、蓝牙通话抢占等场景，支持麦克风自动探测降级重试与音频流读取
 */
class LocalAsrEngine(private val context: Context) {

    interface AsrEventListener {
        fun onWakeWordDetected(keyword: String)
        fun onRecognizing(partialText: String)
        fun onRecognized(finalText: String)
        fun onError(errorMsg: String)
    }

    private var eventListener: AsrEventListener? = null
    private var isListening = false
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    var isQuickCommandEnabled: Boolean = true

    fun setEventListener(listener: AsrEventListener) {
        this.eventListener = listener
    }

    fun startListening() {
        if (isListening) return
        isListening = true

        recordingJob = scope.launch {
            recordAudioLoop()
        }
    }

    fun stopListening() {
        isListening = false
        recordingJob?.cancel()
        recordingJob = null
    }

    /**
     * 录音主循环：带车机音频源动态探测与降级
     */
    private suspend fun recordAudioLoop() = withContext(Dispatchers.IO) {
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = Math.max(minBufferSize, 2048)

        // 车机音频源探测优先级：MIC -> DEFAULT -> VOICE_RECOGNITION
        val audioSources = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        )

        var audioRecord: AudioRecord? = null

        for (source in audioSources) {
            try {
                val record = AudioRecord(source, sampleRate, channelConfig, audioFormat, bufferSize)
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord = record
                    Log.d("LocalAsrEngine", "AudioRecord initialized successfully with source: $source")
                    break
                } else {
                    record.release()
                }
            } catch (e: SecurityException) {
                withContext(Dispatchers.Main) {
                    eventListener?.onError("缺少录音权限，请在车机设置中开启麦克风权限")
                }
                isListening = false
                return@withContext
            } catch (e: Exception) {
                Log.w("LocalAsrEngine", "AudioSource $source failed: ${e.message}")
            }
        }

        if (audioRecord == null) {
            withContext(Dispatchers.Main) {
                eventListener?.onError("车机麦克风被占用或初始化失败")
            }
            isListening = false
            return@withContext
        }

        val buffer = ShortArray(bufferSize / 2)
        try {
            audioRecord.startRecording()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                eventListener?.onError("启动录音失败: ${e.message}")
            }
            audioRecord.release()
            isListening = false
            return@withContext
        }

        var speechDetected = false
        var speechCount = 0

        while (isActive && isListening) {
            val readCount = audioRecord.read(buffer, 0, buffer.size)
            if (readCount > 0) {
                // 计算音频音量振幅 (RMS)
                var sum = 0.0
                for (i in 0 until readCount) {
                    sum += buffer[i] * buffer[i]
                }
                val rms = Math.sqrt(sum / readCount)

                // 简单的车机噪音门限能量检测（检测车内是否有人说话）
                if (rms > 800) {
                    speechCount++
                    if (speechCount >= 3 && !speechDetected) {
                        speechDetected = true
                        withContext(Dispatchers.Main) {
                            eventListener?.onRecognizing("正在收音中...")
                        }
                    }
                }
            }
        }

        try {
            audioRecord.stop()
            audioRecord.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
