package com.incar.assistant.config

import android.content.Context
import android.content.SharedPreferences

/**
 * ZeroAuto 零度车控 · 全局配置与开关控制中心
 */
class AppConfigManager(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("zero_auto_config", Context.MODE_PRIVATE)

    companion object {
        const val THEME_ORB = "orb"
        const val THEME_CAPSULE = "capsule"
        const val THEME_AURORA = "aurora"
        // 开关配置 Key
        private const val KEY_WAKEUP_ENABLED = "wakeup_enabled"           // 离线语音唤醒开关
        private const val KEY_QUICK_COMMAND_ENABLED = "quick_cmd_enabled"  // 免唤醒快捷指令开关
        private const val KEY_FLOAT_BALL_ENABLED = "float_ball_enabled"    // 桌面悬浮球交互开关
        private const val KEY_BOOT_START_ENABLED = "boot_start_enabled"    // 开机自启动开关
        private const val KEY_AUDIO_DUCK_ENABLED = "audio_duck_enabled"    // 媒体闪避压制开关
        private const val KEY_MUSIC_PLATFORM = "music_platform"            // 默认音乐平台: universal, netease, luna, qqmusic, kuwo, kugou
        private const val KEY_MUSIC_VOLUME = "music_volume"                // 媒体音量 (0~100)
        private const val KEY_VOICE_VOLUME = "voice_volume"                // 播报音量 (0~100)
        private const val KEY_VOICE_ROLE = "voice_role"                    // 角色语音/发音人: female_gentle, male_magnetic, mecha_cyber, cute_girl, elder_warm
        private const val KEY_WAKEUP_WORD = "wakeup_word"                  // 唤醒词
        private const val KEY_UI_MODE = "ui_mode"                          // UI交互模式: 0=纯语音无感(Zero UI), 1=大字居中卡片
        private const val KEY_VISUAL_THEME = "visual_theme"
        private const val KEY_MUSIC_REACTIVE = "music_reactive"

        // 授权与凭证 Key (大模型 + 心知天气)
        private const val KEY_LLM_BASE_URL = "llm_base_url"
        private const val KEY_LLM_API_KEY = "llm_api_key"
        private const val KEY_LLM_MODEL = "llm_model"
        private const val KEY_SENIVERSE_UID = "seniverse_uid"
        private const val KEY_SENIVERSE_KEY = "seniverse_key"

        // 默认预设 (公网 DeepSeek 官方兼容端点，支持自定义修改)
        const val DEFAULT_LLM_URL = "https://api.deepseek.com/v1/chat/completions"
        const val DEFAULT_LLM_MODEL = "deepseek-chat"
        const val DEFAULT_WAKEUP_WORD = "零度零度"
        const val DEFAULT_MUSIC_PLATFORM = "universal"
        const val DEFAULT_VOICE_ROLE = "female_gentle"
    }

    // 1. 语音唤醒与识别开关
    var isWakeupEnabled: Boolean
        get() = sp.getBoolean(KEY_WAKEUP_ENABLED, true)
        set(value) = sp.edit().putBoolean(KEY_WAKEUP_ENABLED, value).apply()

    var isQuickCommandEnabled: Boolean
        get() = sp.getBoolean(KEY_QUICK_COMMAND_ENABLED, true)
        set(value) = sp.edit().putBoolean(KEY_QUICK_COMMAND_ENABLED, value).apply()

    var wakeupWord: String
        get() = sp.getString(KEY_WAKEUP_WORD, DEFAULT_WAKEUP_WORD) ?: DEFAULT_WAKEUP_WORD
        set(value) = sp.edit().putString(KEY_WAKEUP_WORD, value.trim()).apply()

    // 2. 界面与交互开关
    var isFloatBallEnabled: Boolean
        get() = sp.getBoolean(KEY_FLOAT_BALL_ENABLED, true)
        set(value) = sp.edit().putBoolean(KEY_FLOAT_BALL_ENABLED, value).apply()

    var isBootStartEnabled: Boolean
        get() = sp.getBoolean(KEY_BOOT_START_ENABLED, true)
        set(value) = sp.edit().putBoolean(KEY_BOOT_START_ENABLED, value).apply()

    var isAudioDuckEnabled: Boolean
        get() = sp.getBoolean(KEY_AUDIO_DUCK_ENABLED, true)
        set(value) = sp.edit().putBoolean(KEY_AUDIO_DUCK_ENABLED, value).apply()

    var uiMode: Int
        get() = sp.getInt(KEY_UI_MODE, 1) // 默认居中防拉伸大字卡片
        set(value) = sp.edit().putInt(KEY_UI_MODE, value).apply()

    var visualTheme: String
        get() = sp.getString(KEY_VISUAL_THEME, THEME_ORB) ?: THEME_ORB
        set(value) = sp.edit().putString(KEY_VISUAL_THEME, value).apply()

    var isMusicReactiveEnabled: Boolean
        get() = sp.getBoolean(KEY_MUSIC_REACTIVE, true)
        set(value) = sp.edit().putBoolean(KEY_MUSIC_REACTIVE, value).apply()

    // 3. 音乐平台与音量调节
    var musicPlatform: String
        get() = sp.getString(KEY_MUSIC_PLATFORM, DEFAULT_MUSIC_PLATFORM) ?: DEFAULT_MUSIC_PLATFORM
        set(value) = sp.edit().putString(KEY_MUSIC_PLATFORM, value).apply()

    var musicVolume: Int
        get() = sp.getInt(KEY_MUSIC_VOLUME, 60)
        set(value) = sp.edit().putInt(KEY_MUSIC_VOLUME, value.coerceIn(0, 100)).apply()

    var voiceVolume: Int
        get() = sp.getInt(KEY_VOICE_VOLUME, 80)
        set(value) = sp.edit().putInt(KEY_VOICE_VOLUME, value.coerceIn(0, 100)).apply()

    var voiceRole: String
        get() = sp.getString(KEY_VOICE_ROLE, DEFAULT_VOICE_ROLE) ?: DEFAULT_VOICE_ROLE
        set(value) = sp.edit().putString(KEY_VOICE_ROLE, value).apply()

    // 3. 云端大模型与心知天气授权凭据
    var llmBaseUrl: String
        get() = sp.getString(KEY_LLM_BASE_URL, DEFAULT_LLM_URL) ?: DEFAULT_LLM_URL
        set(value) = sp.edit().putString(KEY_LLM_BASE_URL, value.trim()).apply()

    var llmApiKey: String
        get() = sp.getString(KEY_LLM_API_KEY, "") ?: ""
        set(value) = sp.edit().putString(KEY_LLM_API_KEY, value.trim()).apply()

    var llmModel: String
        get() = sp.getString(KEY_LLM_MODEL, DEFAULT_LLM_MODEL) ?: DEFAULT_LLM_MODEL
        set(value) = sp.edit().putString(KEY_LLM_MODEL, value.trim()).apply()

    var seniverseUid: String
        get() = sp.getString(KEY_SENIVERSE_UID, "") ?: ""
        set(value) = sp.edit().putString(KEY_SENIVERSE_UID, value.trim()).apply()

    var seniverseKey: String
        get() = sp.getString(KEY_SENIVERSE_KEY, "") ?: ""
        set(value) = sp.edit().putString(KEY_SENIVERSE_KEY, value.trim()).apply()

    // 状态判定
    fun isLlmAuthorized(): Boolean = llmApiKey.isNotEmpty()
    fun isSeniverseAuthorized(): Boolean = seniverseUid.isNotEmpty() && seniverseKey.isNotEmpty()
}
