# CamPilot  
## Sony 智能摄影控制 App  
### 技术需求文档（AI 可执行版 · Android · Sony Only）

---

## 1. 项目概述

### 1.1 项目目标

开发一款 **Android 智能摄影控制 App（Android 10+）**，  
通过 **USB 连接 Sony 相机**，以手机作为计算与控制中枢，实现与 Arsenal 2 等价甚至更强的摄影能力，包括：

- AI 取景与参数推荐
- 自动 HDR
- 延时摄影（基础 → Holy Grail）
- 景深合成（对焦堆栈）
- 全景接片

本项目 **第一阶段仅实现 USB 连接与控制**，不依赖独立硬件。

---

### 1.2 设计原则

- **Sony Only**
- **USB First（第一优先级）**
- **Android 原生能力优先**
- **AI 辅助决策，而非强制全自动**
- **模块化、可扩展**

---

## 2. 支持范围定义（强约束）

### 2.1 支持的相机（优先级明确）

#### P0（必须优先支持）
- **Sony A7C2（最高优先级）**

#### P1（第一阶段支持）
- Sony A7 IV  
- Sony A7R V  
- Sony A6700  

#### P2（后续）
- Sony A7 III  
- Sony A1  

#### 明确不支持
- 非 Sony 相机  
- 非 E 卡口  

---

### 2.2 支持的镜头

- Sony 原生 E 卡口镜头  
- 自动识别镜头型号  
- 第三方镜头：
  - 可拍摄
  - 不保证对焦堆栈精度

---

## 3. 平台与系统要求

### 3.1 平台

- **Android App**
- 最低版本：**Android 10（API 29）**
- 推荐架构：
  - MVVM + Repository
  - Kotlin 优先
  - Camera 控制模块 Native 化（可选）

### 3.2 硬件要求（手机）

- USB OTG 支持  
- 推荐：
  - ARM64
  - ≥ 6GB RAM
  - 支持 NNAPI

---

## 4. 系统总体架构

```
UI / UX Layer
│
├─ AI Recommendation Engine
│   ├─ Scene Classification
│   ├─ Composition Analysis
│   └─ Exposure Advisor
│
├─ Photography Workflow Engine
│   ├─ HDR Controller
│   ├─ Focus Stacking Controller
│   ├─ Panorama Controller
│   └─ Timelapse Controller
│
├─ Camera Control Abstraction Layer (核心)
│   ├─ USB PTP Transport
│   └─ Command Normalization
│
└─ Sony Camera Driver Layer
    ├─ Exposure Control
    ├─ Focus Control
    ├─ Live View
    └─ Capture Control
```

---

## 5. 相机连接与状态管理

### 5.1 USB 连接（第一阶段唯一方式）

- USB PTP 扩展协议
- 自动识别 Sony 相机
- 热插拔检测
- 异常断开重连

### 5.2 相机连接状态

| 状态 | 说明 |
|----|----|
| DISCONNECTED | 未连接相机 |
| CONNECTING | 正在连接 |
| CONNECTED | 已连接 |
| BUSY | 相机执行任务中 |
| ERROR | 异常 |

### 5.3 状态图标（UI 强制）

- 所有主界面右上角显示相机状态图标

| 状态 | 表现 |
|----|----|
| 未连接 | 灰色相机 |
| 已连接 | 绿色相机 |
| 拍摄中 | 黄色闪烁 |
| 错误 | 红色警告 |

---

## 6. App 界面设计

### 6.1 主界面（Viewfinder）

- Live View 实时取景  
- AI 构图 / 曝光 Overlay  
- 相机连接状态图标（右上角）  

### 6.2 模式选择

- 单张拍摄（AI）
- HDR
- 延时摄影
- 对焦堆栈
- 全景接片

### 6.3 HDR 界面

- 曝光包围数量
- 高光 / 阴影优先
- 开始拍摄

### 6.4 延时摄影界面

- 间隔 / 时长
- Holy Grail 开关
- 曝光曲线预览

### 6.5 对焦堆栈界面

- 最近 / 最远焦点
- 步数设置
- 自动拍摄

### 6.6 全景接片界面

- 拍摄方向
- 重叠提示
- 自动拼接

---

## 7. MVP 定义（第一阶段）

### 必须完成

- USB 连接 Sony A7C2
- Live View
- 相机状态管理与图标
- AI 参数推荐
- 自动 HDR
- 基础延时摄影

### 暂不实现

- Wi-Fi / 蓝牙控制
- 对焦堆栈
- 全景接片
- Holy Grail

---

## 8. 验收标准

- Sony A7C2 连接稳定性 ≥ 99%
- HDR / 延时成功率 ≥ 95%
- Live View 延迟 < 200ms
- 相机状态图标准确率 100%
