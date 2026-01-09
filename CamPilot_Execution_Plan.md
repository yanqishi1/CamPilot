# CamPilot App 可执行实施计划

## 0. 规划基线
- **版本范围**：Android 10+，仅 USB 连接 Sony A7C2（同时兼容 A7 IV / A7R V / A6700）。
- **首要交付**（MVP）：稳定 USB 连接、Live View、相机状态管理、AI 参数推荐、自动 HDR、基础延时摄影。
- **节奏假设**：2 周冲刺，每日站会，代码托管于 Git，CI 仅运行静态检查与单元测试。

## 1. 里程碑与时间表
| 里程碑 | 目标 | 产出 | 预计周期 |
|---|---|---|---|
| M0 环境准备 | 确认开发/测试环境、拉通设备 | 项目脚手架、设备接入 SOP | 3 天 |
| M1 USB & 状态管理 | USB 连接、状态图标、Live View 框架 | USB 模块、状态机、假数据 UI | 1 冲刺 |
| M2 Live View + AI 推荐 | 真机 Live View、初版 AI 曝光建议 | Camera 控制层、AI 引擎 stub | 1 冲刺 |
| M3 HDR / Timelapse | 自动 HDR、基础延时摄影可拍摄 | Workflow 引擎、任务调度 | 1 冲刺 |
| M4 稳定性与验收 | 稳定性测试、指标验证 | 指标测试报告、发布包 | 1 冲刺 |

## 2. 分阶段任务拆解
### Phase 0：环境与基础设施
1. 建立 Android Studio 项目（Kotlin，Min SDK 29），启用 ViewBinding、Hilt、Coroutines、CameraX（作为占位）。
2. 引入 USB PTP 所需权限与 `android.hardware.usb.host` 特性声明。
3. 配置多模块结构：`app`（UI）+`core-camera`（USB/PTP）+`core-ai`（AI 推荐 stub）+`feature-hdr`+`feature-timelapse`.
4. 搭建基础 CI（Gradle lint + unit test），定义分支策略 & 代码规范。

### Phase 1：USB 连接与状态图标
1. 在 `core-camera` 实现 USB 连接管理器：检测/请求权限、建立 PTP 会话、状态回调。
2. 建模状态机（`DISCONNECTED`、`CONNECTING`、`CONNECTED`、`BUSY`、`ERROR`），输出 `Flow<CameraState>`.
3. UI 层实现状态图标组件（右上角），绑定状态。
4. 构建 Live View 占位视图（静态图像），验证状态切换。
5. 集成设备日志记录与错误上报。

### Phase 2：Live View + AI 推荐
1. 完成 PTP Live View 数据拉流、解码（YUV → SurfaceTexture）。
2. 构建取景器 UI（Compose 或 View）+ AI Overlay 占位层。
3. `core-ai`：实现场景分类 placeholder（TensorFlow Lite 模型加载接口、伪数据输出）。
4. `AI Recommendation Engine` 输出曝光建议（ISO/Shutter/Aperture）与信心值。
5. 将建议与 UI 控件联动，提供「采纳」按钮。

### Phase 3：HDR / Timelapse 工作流
1. `feature-hdr`：参数配置 ViewModel、包围拍摄序列生成、串行触发快门。
2. `feature-timelapse`：间隔/时长设置、进度显示、后台计时器、暂停/恢复。
3. `Photography Workflow Engine`：统一调度任务队列，屏蔽 USB 控制细节。
4. 日志 + 指标采集：记录成功率、失败类型。

### Phase 4：稳定性与验收
1. 可靠性测试：连接稳定性、HDR/延时成功率、Live View 延迟评估。
2. Bug Bash & 性能 profiling（CPU/GPU/内存）。
3. 验收报告 & 发布 Candidate。

## 3. 交付清单
- 项目源码（多模块 Kotlin）。
- 自动化测试：核心状态机单测、USB 模块集成测试（Instrumented）。
- 设备接入文档、调试脚本。
- 验收指标报告。
- 发布 APK（Internal Testing Track）。

## 4. 依赖与风险
- 真实 Sony A7C2 设备与 USB-C 线缆。
- PTP 协议细节文档 & SDK（需提前确认授权）。
- 风险：USB 权限申请失败、Live View 数据格式兼容、AI 模型性能；制定 fallback（例如提供手动参数模式）。

## 5. 人力与责任矩阵 (RACI)
| 模块 | Responsible | Accountable | Consulted | Informed |
|---|---|---|---|---|
| USB/Camera Core | Android 平台工程师 | 技术负责人 | Sony SDK 专家 | QA |
| AI Recommendation | ML 工程师 | AI Lead | 产品经理 | 全体 |
| HDR/Timelapse Workflow | Android 功能工程师 | 技术负责人 | UX 设计 | QA |
| 测试与验收 | QA | 产品经理 | 开发 | 全体 |

## 6. 即刻下一步
1. 初始化 Android 多模块项目骨架。
2. 添加 USB 权限、状态机实体与假数据 UI。
3. 搭建基础 DI/日志/测试框架，为 Phase 1 开发做准备。
