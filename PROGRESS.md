# In-Car Voice Assistant (车机专属语音助手) 开发进度跟踪表

> **工程路径**：`/opt/projects/in-car-voice-assistant`  
> **更新时间**：2026-10-16  
> **当前状态**：核心模块代码第一阶段落成 (已进入 Git 版本管理)

---

## 模块进度看板 (6/8 已就绪)

| 序号 | 模块名称 | 进度 | 交付文件 / 状态说明 |
| :--- | :--- | :---: | :--- |
| **01** | **工程骨架与清单** | `100%` | `AndroidManifest.xml`, `build.gradle`, `settings.gradle` |
| **02** | **车身硬件本地控制** | `100%` | `LocalCarHardwareManager.kt` (单窗/组合精准控制、探针检测，坚决不接云端) |
| **03** | **高德车机版广播协议** | `100%` | `AmapAutoController.kt` (10007 直达、10038 消歧、10042 途经点) |
| **04** | **系统级运维与强杀** | `100%` | `SystemOptimizeManager.kt` (白名单防误杀、卡死强杀、一键清理运存) |
| **05** | **车载天气双轨引擎** | `100%` | `InCarWeatherManager.kt` (高德广播缓存优先 + 心知天气长途备用) |
| **06** | **复合长指令编译器** | `100%` | `MultiIntentParser.kt` (单句复合指令多意图并行拆解) |
| **07** | **车载防变形 UI** | `90%` | `layout_in_car_card.xml` (ConstraintLayout 弹性停靠， maxWidth 640dp) |
| **08** | **离线语音唤醒/ASR 桥接** | `60%` | 准备挂载 Sherpa-ONNX 离线动态库与主后台 Service |

---

## 阶段检查点日志

- **2026-10-16 17:55**：完成 Git 仓库初始化与第一阶段提交 (`9b11704`)，包含高德广播、硬件探针、双轨天气、系统优化及复合意图解析共 11 个核心工程文件。
