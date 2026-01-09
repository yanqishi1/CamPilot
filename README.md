# CamPilot

CamPilot 是一款面向 Sony E 卡口无反用户的 **Android 10+ 智能摄影控制 App**。手机通过 USB OTG 与相机建立 PTP 会话，负责 Live View、AI 参数建议以及 HDR、延时等工作流调度，目标体验对标 Arsenal 2，并为后续景深合成、全景等高级玩法奠定架构基础。

## 功能特性
- **USB First**：优先兼容 Sony A7C2（P0），并拓展至 A7 IV / A7R V / A6700。启动即检测设备状态，支持热插拔与权限恢复。
- **Live View 取景器**：内置 `LiveViewStreamer`，在真实视频流接入前提供调试画面和状态提示，UI 由 Jetpack Compose 构建并覆盖状态指示。
- **AI 取景辅助**：`core-ai` 模块輸出 ISO / 快门 / 光圈建议以及构图提示，可一键刷新并在 UI 中以卡片展示。
- **自动 HDR**：`feature-hdr` 管理包围拍摄流程、优先级配置与进度反馈，支持任务中断及错误提示。
- **延时摄影**：`feature-timelapse` 负责间隔、时长与帧数换算，确保连接状态并在 UI 中提示拍摄进度。
- **状态统一管理**：`CameraConnectionManager` 输出 `Flow<CameraState>`，被 UI、Live View 与工作流共享，同时暴露 Busy / Idle 标记以串联任务。

## 模块结构
| 模块 | 作用 |
| --- | --- |
| `app` | Jetpack Compose UI、Home 场景、状态汇总与 Hilt 入口，以及 `CameraStatusIndicator` 等组件。 |
| `core-camera` | Sony USB/ PTP 连接管理、支持机型枚举、Live View Surface 渲染管线骨架。 |
| `core-ai` | AI 推荐引擎占位实现，封装曝光建议与构图提示的数据流。 |
| `feature-hdr` | HDR 工作流控制器，负责任务生命周期与进度回传。 |
| `feature-timelapse` | 延时摄影控制器，负责计划参数校验、帧数调度与连接状态检查。 |

> 完整的业务与技术约束详见 `CamPilot_App_Technical_Requirements.md`；阶段性交付与冲刺拆解记录在 `CamPilot_Execution_Plan.md`。

## 技术栈
- Kotlin + Coroutines + Flow
- Jetpack Compose（Material 3、View interop）与 ViewModel
- Hilt 依赖注入
- Android USB Host API + 自定义 PTP 扩展（规划）
- Gradle Kotlin DSL，多模块结构

## 快速开始
1. **准备环境**
   - Android Studio Iguana+，Android Gradle Plugin 8.2+
   - JDK 17（项目已将 `compileOptions` 配置为 17）
   - 支持 USB OTG 的 Android 10+ 真机，推荐 ARM64 / ≥6GB RAM
   - Sony 相机（A7C2 优先），USB-C 线缆
2. **克隆与同步依赖**
   ```bash
   git clone <repo-url>
   cd CamPilot
   ./gradlew tasks
   ```
   运行一次 Gradle 同步以下载依赖与生成 Hilt 代码。
3. **运行调试版**
   ```bash
   ./gradlew :app:assembleDebug
   ```
   或直接在 Android Studio 中选择 `app` 并点击 Run。首次连接相机时请在弹窗中授予 USB 权限。
4. **连接与调试**
   - 启动 App 后使用首页右下角 FAB 连接/断开 Sony A7CⅡ。
   - 若需要查看状态流，可在 Android Studio 的 Logcat 或 `LiveViewState` 相关组件上设置断点。

## 开发提示
- **状态协同**：`HomeViewModel` 通过 `combine` 汇总相机、AI、HDR、延时与 Live View 状态，保持 UI 单一数据源。
- **占位实现**：当前 Live View 与 AI 推理为调试占位，后续可在 `LiveViewStreamer` 与 `AiRecommendationEngine` 替换为真实 PTP 推流与 TFLite 模型。
- **工作流互斥**：HDR、延时模块在触发快门前会调用 `CameraConnectionManager.markBusy()`，确保不同任务不会并发争抢 USB 通道。
- **权限管理**：`CameraConnectionManager` 注册系统广播，自动处理热插拔以及权限丢失，必要时可调用 `connect(SupportedCamera)` 指定型号。

## 里程碑概览
| 里程碑 | 交付 | 说明 |
| --- | --- | --- |
| M0 环境准备 | 多模块骨架、USB 权限声明、CI 基础 | 3 天 |
| M1 USB & 状态管理 | USB 连接、状态图标、Live View 占位 | 1 冲刺 |
| M2 Live View + AI | 真机取景、AI 曝光建议 | 1 冲刺 |
| M3 HDR / Timelapse | 自动 HDR 与基础延时可拍摄 | 1 冲刺 |
| M4 验收 | 稳定性、性能与发布候选 | 1 冲刺 |

更细的任务拆解与 Owner 见 `CamPilot_Execution_Plan.md`，风险与依赖列于文档第 4 节。

## 测试与质量
- 运行单元测试 / Lint：
  ```bash
  ./gradlew lintDebug testDebugUnitTest
  ```
- 推荐在接入真实相机前使用 `LiveViewStreamer` 的合成画面验证 UI 与状态联动。
- 计划中的仪表（连接成功率、Live View 延迟、HDR/延时成功率）将在 Phase 4 中补充。

## 相关文档
- `CamPilot_App_Technical_Requirements.md`：应用目标、设计原则、MVP 范围
- `CamPilot_Execution_Plan.md`：里程碑、任务拆解、RACI

欢迎根据业务需要扩展更多特色功能（景深合成、全景接片等），但应继续遵守 **Sony Only / USB First / 模块化** 的核心原则。
