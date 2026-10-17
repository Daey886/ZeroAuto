# ZeroAuto 2.0

ZeroAuto 2.0 是一个本地优先的 Android Automotive 语音交互原型，按车载横屏场景组织为清晰的运行时分层。

## 架构

- `config/AppConfigManager`：SharedPreferences 持久化六大设置域，视觉主题为 `orb`、`capsule`、`aurora`。
- `ui/ThemeInteractionView`：原生 Canvas 绘制三套视觉形态。光球支持触控拖动、状态波形和自适应气泡；胶囊限制 580dp 宽度并绘制四柱波形；极光绘制边缘呼吸渐变和底部对话卡片。
- `audio/CarAudioFocusController`：统一申请 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`，交给车机音频策略降低媒体并在播报结束释放焦点。
- `tts/CarTtsManager` + `voice/OfflineVoiceCoordinator`：直接使用系统 `TextToSpeech`，`QUEUE_FLUSH` 实现打断；角色通过 pitch / speechRate 调节，不依赖云端 TTS。
- `router/IntentRouter`：先匹配导航、媒体、车身本地指令；未知请求再交给已有云端助手，断网时由本地调用方负责快速兜底。
- `service/VoiceAssistantService`：前台守护、悬浮窗、TouchSlop 防误触和边缘吸附；Android O+ 使用 `TYPE_APPLICATION_OVERLAY`。

## 运行与权限

首次启动需要录音、悬浮窗和前台麦克风权限。车机固件必须开放对应的 Android Automotive / 厂商车身控制权限，未开放时硬件管理器会安全返回“不支持”，不会伪造成功状态。

## 验证

当前工作区没有 Gradle Wrapper，也没有系统 `gradle` 命令；建议在 Android Studio 或补齐 Wrapper 后执行 `./gradlew :app:assembleDebug`。代码按 compileSdk 34、minSdk 26、JVM 17 编写。
