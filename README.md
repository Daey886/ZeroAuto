# ZeroAuto 🚗

<div align="center">

**车规级极致性能 · 纯本地离线优先 · 拟人情感化座舱语音伴侣**

[![Platform](https://img.shields.io/badge/Platform-Android%20Automotive%20%2F%20Android%20OS-blue?style=flat-square&logo=android)](https://developer.android.com/training/cars)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-purple?style=flat-square&logo=kotlin)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green?style=flat-square)](LICENSE)
[![Architecture](https://img.shields.io/badge/Design-Dual--Track%20Routing-orange?style=flat-square)](#核心架构)

[在线交互演示 (方案 A/B/C)](http://47.87.64.165:8088/preview/car_assistant_ui_preview.html) · [车载控制中心原型](http://47.87.64.165:8088/preview/car_assistant_settings.html)

</div>

---

## 📖 项目简介

**ZeroAuto** 是一款专为车载大屏深度定制的原生 Android 语音中枢系统。针对车载工况下**弱网/地库断网、座舱胎噪音乐干扰、系统级抢占冲突、驾驶安全防遮挡**等严苛场景，ZeroAuto 彻底抛弃了重度依赖云端的传统方案，采用**全双工纯本地离线闭环 + 智脑外脑双轨制**架构，带给车主毫秒级出声与拟人化座舱交互体验。

---

## ✨ 核心特性

### 1. 🎭 三大多形态座舱视觉交互 (Visual Themes)
严格还原交互原型，提供 3 款原生渲染的车载形态，支持在设置中心无缝切换：
- **方案 A · 拟人灵动光球 (Nomi Style)**：
  - 双层能量正圆渐变核心 + 外圈正弦波呼吸脉冲；
  - 金色灵动双眼（支持随机眨眼、倾听注视跟随）；
  - 全屏指尖拖拽，松手平滑弹性吸附屏幕边缘，对话卡片自适应避让展开（防遮挡导航车速及路线）；
  - 4 态显式状态机：`待命 (IDLE)`、`倾听 (LISTENING)`、`思考 (THINKING)`、`播报 (SPEAKING)`。
- **方案 B · 顶置极简灵动岛 (Capsule Style)**：
  - 580px 磨砂科技悬浮胶囊，麦克风流光光标；
  - 4 柱青蓝音频律动跳跃，极简低干扰，专注安全驾驶。
- **方案 C · 沉浸极光座舱全屏 (Aurora Style)**：
  - 屏幕四周紫青渐变光晕呼吸映射，底部 540px 高质感对话卡片。

### 2. ⚡ 纯本地离线 TTS 与全双工打断 (Zero-Latency TTS)
- **0 毫秒极速发音**：直接调用车机底层原生 `TextToSpeech` 离线发音人，零流量消耗，断网 100% 正常播报；
- **4 款定制车规音色**：知性温和女声（默认）、沉稳磁性男声、赛博极客机甲、灵动元气少女；
- **随时插话打断 (Barge-in)**：新指令发出瞬间，底层立即 `QUEUE_FLUSH` 掐断上一句发音，不拖泥带水。

### 3. 🛡️ 车规级音频焦点看门狗 (Audio Focus & Ducking)
- **智能避让 (Ducking)**：助手倾听与发音时，自动将第三方音乐（网易云/QQ音乐/酷我/酷狗/汽水音乐）音量平滑压低至 20%，播报结束瞬间恢复；
- **双重兜底看门狗 (Watchdog)**：内置 15 秒硬件级超时释放与 `OnAudioFocusChangeListener`，遇系统级紧急避碰或高德导航测速抢占时立即静音让位，彻底杜绝车机音乐被永久压低变哑的通病。

### 4. 🔀 双轨制意图分发器 (Intent Router)
- **离线极速轨 (0ms)**：
  - **高德车机版**：深度封装标准 10036 导航协议与 10007 路线规划广播；
  - **多媒体播控**：系统级 `MediaSession` / `KeyEvent` 切歌、暂停、播放；
  - **车身控制**：车窗升降、空调开闭调节。
- **智脑外脑轨 (Cloud Intelligence)**：
  - 复杂闲聊与知识问答直连 10Router 网关 `cx/gpt-5.6-luna`；
  - 内置网络探测机制：遇地库或弱网断网时，1 秒内直接以本地 TTS 给出清晰反馈兜底。

### 5. 🎛️ 车载设置控制中心 (Settings Center)
完整覆盖 6 大模块的持久化配置：
1. **🎙️ 语音感知与免唤醒**：离线常驻唤醒、自定义唤醒词、免唤醒直达（切歌/关窗等）；
2. **✨ 视觉交互与动效**：形态三选一（orb/capsule/aurora）、常驻悬浮球、音乐律动开关；
3. **🔊 声音通道与车载音乐**：默认音乐平台选择、音频闪避开关、媒体与播报独立音量控制；
4. **🎙️ 角色语音与发音人风格**：4 种本地发音风格即时切换与试听；
5. **🌐 智驾网络与增值授权**：大模型端点配置、心知天气授权与网络诊断；
6. **🛡️ 车机自启与系统守护**：ACC 上电自启、系统环境与硬件诊断。

---

## 🏗️ 核心架构

```
app/src/main/java/com/incar/assistant/
├── audio/
│   └── CarAudioFocusController.kt    # 车规级音频焦点管理与看门狗
├── config/
│   └── AppConfigManager.kt            # 6大模块持久化配置中心
├── media/
│   └── MediaMusicController.kt        # 多音乐平台媒体播控中枢
├── router/
│   └── IntentRouter.kt                # 离线车控/高德 + 智脑双轨意图路由器
├── service/
│   └── VoiceAssistantService.kt       # 系统级悬浮窗常驻与开机守护服务
├── tts/
│   └── CarTtsManager.kt               # 系统原生离线TTS引擎与音色调优
├── ui/
│   ├── MainActivity.kt                # 车载设置控制中心 Activity
│   └── ThemeInteractionView.kt        # A/B/C 三大形态 Canvas 渲染与触控吸边
└── voice/
    └── OfflineVoiceCoordinator.kt     # 语音调度协调器 (打断/焦点联动)
```

---

## 🚀 快速上手与集成

### 1. 运行环境要求
- Android 8.0+ (API Level 26+) 车机系统或 Android 模拟器
- 屏幕分辨率推荐：1024×600 / 1280×720 / 1920×720 / 1920×1080 横屏车载大屏

### 2. 必备系统权限
为保障车规级悬浮窗与开机自启，需在系统设置中授予以下权限：
- **悬浮窗权限**：`SYSTEM_ALERT_WINDOW`（允许在导航及其他 App 上层常驻）
- **开机自启权限**：`RECEIVE_BOOT_COMPLETED` / `QUICKBOOT_POWERON`
- **录音权限**：`RECORD_AUDIO`

### 3. 构建说明
```bash
# 克隆仓库
git clone https://github.com/Baey666/ZeroAuto.git
cd ZeroAuto

# 编译 Debug APK (需要本地 Android SDK 与 Gradle 环境)
./gradlew assembleDebug
```

---

## 📄 开源许可证

本项目基于 [Apache License 2.0](LICENSE) 协议开源。
