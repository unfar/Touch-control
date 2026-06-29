# TouchControl

<div align="center">

## 📱 ↔ 📟 跨设备触控神器

**一个 App，两种模式 — 手机当触摸板，平板当被控端**

[![Build](https://github.com/unfar/Touch-control/actions/workflows/build.yml/badge.svg)](https://github.com/unfar/Touch-control/actions/workflows/build.yml)
[![Platform](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-2024.06-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

---

## 🌟 项目简介

TouchControl 是一个**纯 Android 端**的跨设备触控方案。安装一个 App，在手机上它是**触摸板 + 键盘控制器**，在平板上它是**被控接收端**，通过 **WebSocket + Token 加密配对** 实现安全连接。

> 💡 灵感：当你的 Android 平板没有键盘鼠标时，用手机代替触摸板来操作它。

---

## 🎯 核心架构

```
┌─────────────────────────────┐        WiFi / 局域网        ┌─────────────────────────────┐
│         📱 手机模式           │                              │         📟 平板模式           │
│         (控制器)             │ ◄──── WebSocket 协议 ────► │         (被控端)             │
│                              │                              │                              │
│  ┌───────────────────────┐   │      {"type":"mouse",        │  ┌───────────────────────┐  │
│  │   触摸板手势引擎       │   │       "action":"move",       │  │  嵌入式 WebSocket 服务端 │  │
│  │   • 单指移动/点击     │   │       "dx":10, "dy":5}      │  │  (纯 Kotlin 零依赖)   │  │
│  │   • 双指滚动/右键     │   │                              │  └───────────┬───────────┘  │
│  │   • 长按拖拽/缩放     │   │                              │              ▼              │
│  │   • 三指手势           │   │                              │  ┌───────────────────────┐  │
│  ├───────────────────────┤   │                              │  │  AccessibilityService  │  │
│  │   QWERTY 键盘         │   │                              │  │  dispatchGesture()    │  │
│  │   修饰键 Ctrl/Alt/Shift│   │                              │  │  → 模拟真实触摸/手势   │  │
│  ├───────────────────────┤   │                              │  └───────────────────────┘  │
│  │   UDP 局域网自动发现   │   │                              │                              │
│  │   ✚ 二维码扫码连接    │   │                              │                              │
│  │   ✚ Token 安全配对    │   │                              │                              │
│  └───────────────────────┘   │                              │                              │
└─────────────────────────────┘                              └─────────────────────────────┘
```

### 🔐 安全机制

```
平板端                         手机端
  │                              │
  ├─ 启动服务端                    │
  ├─ 生成随机 Token ──── QR 码 ────► 扫码解析
  │  (如: X7K3P9)                  │
  │                              ├─ 连接 ws://IP:9090/?token=X7K3P9
  │                              │
  ├─ 握手时校验 Token              │
  │  ├─ ✅ 匹配 → 连接成功          │
  │  └─ ❌ 不匹配 → 403 拒绝        │
  │                              │
  ╰─ Token 每次启动刷新，旧码失效    │
```

---

## ✨ 功能特性

### 📱 手机模式（控制器）

| 手势 / 功能 | 效果 |
|:---|:---|
| 👆 **单指滑动** | 鼠标移动 |
| 👆 **单指轻点** | 左键单击 |
| 👆 **单指双击** | 左键双击 |
| 👆 **长按 + 拖动** | 拖拽操作 |
| ✌️ **双指滑动** | 滚轮滚动 |
| ✌️ **双指轻点** | 右键单击 |
| 🤏 **双指捏合** | 缩放 |
| 🖐️ **三指上滑** | 返回桌面 |
| ⌨️ **QWERTY 键盘** | 完整键盘 + Ctrl/Alt/Shift/Win 修饰键 |
| 📝 **文本发送** | 输入文字一键发送到被控端 |
| 📡 **局域网发现** | UDP 广播自动搜索服务端 |
| 📷 **扫码连接** | 扫描平板端二维码即连 |
| 🔐 **Token 配对** | 防同 WiFi 他人劫持 |
| ⚙️ **光标/滚轮速度调节** | 0.3x ~ 3.0x 可调 |
| 🌙 **深色/浅色主题** | 自动跟随系统或手动切换 |

### 📟 平板模式（被控端）

| 功能 | 说明 |
|:---|:---|
| 🖥️ **嵌入式 WebSocket 服务端** | 纯 Kotlin 实现，零外部依赖，完整 RFC 6455 实现 |
| 👆 **手势模拟** | 通过 `AccessibilityService.dispatchGesture()` 模拟真实触摸 |
| 🖱️ **光标追踪** | 维持虚拟光标位置，支持相对位移 |
| ✌️ **双指滚动** | 平滑滚动模拟 |
| 👆 **长按右键** | 长按模拟右键菜单 |
| 🎨 **二维码显示** | 连接信息编码为 QR 码，手机扫码即连 |
| 🔐 **Token 显示** | 显示配对码，也可手动输入 |
| ⌨️ **系统按键** | Home / Back / Recents / 音量 / 粘贴文本 |
| 📋 **文本粘贴** | 通过剪贴板 + GLOBAL_ACTION_PASTE 输入文字 |

---

## 🚀 快速开始

### 安装 App

[![Download](https://img.shields.io/badge/Download-Latest_APK-00D68F?style=for-the-badge&logo=githubactions&logoColor=white)](https://github.com/unfar/Touch-control/actions/workflows/build.yml)

> 进入上方链接 → 点击最新的 **Build APK** 工作流 → 找到 **Artifacts** 下载 APK

或者自行编译：
```bash
git clone https://github.com/unfar/Touch-control.git
# 用 Android Studio 打开 → Sync Gradle → Build APK
```

### 配置使用

#### 平板端（被控）

```
1. 安装并打开 TouchControl
2. 选择「📟 平板模式」
3. 进入 设置 → 无障碍 → TouchControl → 开启服务
4. 回到 App，打开服务端开关
5. 记下屏幕上显示的 IP 和配对码，或直接让手机扫码
```

#### 手机端（控制）

```
1. 安装并打开 TouchControl
2. 选择「📱 手机模式」
3. 点击「扫一扫连接」扫描平板上的二维码
   └ 或手动输入 IP 和配对码
4. 连接成功后 → 滑动触摸板控制平板！
```

---

## 🛠️ 技术栈

| 层面 | 技术选型 |
|:---|:---|
| **语言** | Kotlin 1.9.24 |
| **UI** | Jetpack Compose + Material 3 (Material You) |
| **网络** | OkHttp WebSocket 客户端 + 纯 Kotlin WS 服务端 |
| **发现** | UDP 广播局域网自动发现 |
| **安全** | 随机 Token 配对（6位字母数字，每次启动刷新） |
| **二维码** | ZXing (生成) + ML Kit Barcode Scanning (扫描) |
| **相机** | CameraX |
| **存储** | DataStore Preferences |
| **手势** | 自定义触摸引擎 (TouchpadEngine) |
| **触摸模拟** | AccessibilityService dispatchGesture() |
| **最低版本** | Android 13 (API 33) |
| **CI/CD** | GitHub Actions 自动编译 APK |

---

## 📂 项目结构

```
TouchControl/
├── .github/workflows/
│   └── build.yml                    # GitHub Actions 自动编译
│
├── app/src/main/java/com/touchcontrol/
│   ├── MainActivity.kt              # 主入口 + 模式路由 + 扫码集成
│   ├── TouchControlApp.kt           # Application 类
│   │
│   ├── network/
│   │   ├── WebSocketClient.kt       # OkHttp WS 客户端（手机端）
│   │   ├── EmbeddedWebSocketServer.kt # 纯 Kotlin WS 服务端（平板端）
│   │   └── ServerDiscovery.kt       # UDP 局域网发现
│   │
│   ├── accessibility/
│   │   └── TouchControlService.kt   # AccessibilityService 触摸模拟
│   │
│   ├── gesture/
│   │   ├── GestureProtocol.kt       # JSON 通信协议定义
│   │   └── TouchpadEngine.kt        # 手势识别引擎（单/双指、拖拽、缩放）
│   │
│   ├── data/
│   │   └── SettingsRepository.kt    # DataStore 持久化设置
│   │
│   └── ui/
│       ├── components/
│       │   ├── QrCodeGenerator.kt   # ZXing QR 码生成 & 数据解析
│       │   └── QrCodeScanner.kt     # CameraX + ML Kit 扫码器
│       ├── theme/                   # Material 3 深色/浅色主题
│       ├── navigation/              # 路由定义
│       └── screens/
│           ├── ModeSelectionScreen.kt   # 🏁 角色选择（首次启动）
│           ├── ConnectionScreen.kt      # 🔗 连接管理 + 扫码
│           ├── TouchpadScreen.kt        # 🎯 触摸板主页
│           ├── KeyboardScreen.kt        # ⌨️ 远程键盘
│           ├── TabletReceiverScreen.kt  # 📟 平板接收页面
│           └── SettingsScreen.kt        # ⚙️ 设置页面
│
├── server/                          # 🖥️ (可选) Python 电脑服务端
│   ├── touch_server.py              # 用于控制 Windows/macOS 电脑
│   └── requirements.txt
│
├── build.gradle.kts                 # 项目级 Gradle 配置
├── settings.gradle.kts
└── README.md
```

---

## 🧩 同类项目对比

TouchControl 参考了以下开源项目，并结合自身场景做了优化：

| 项目 | ⭐ | 特点 | TouchControl 借鉴点 |
|:---|:---:|:---|:---:|
| [Kaia-Alenia/Axon](https://github.com/Kaia-Alenia/Axon) | 25 | Go 服务端 + Kotlin 客户端，UDP 低延迟 | 手势系统设计 |
| [burakgon/remotedroid](https://github.com/burakgon/remotedroid) | 新 | Android TV 被控 + PWA 控制器 | Token 配对 + QR 连接 |
| [harimoradiya/WiFi-Mouse-Server](https://github.com/harimoradiya/WiFi-Mouse-Server) | 8 | Kotlin MPP 多平台 | 架构参考 |

---

## 🗺️ 未来路线

### 已完成 ✅
- [x] Android 手机 → Android 平板 触摸控制
- [x] Token 配对防劫持
- [x] 二维码扫码连接
- [x] 手机端完整 QWERTY 键盘
- [x] UDP 局域网自动发现
- [x] 深色/浅色主题
- [x] 嵌入式 WebSocket 服务端（纯 Kotlin，零依赖）
- [x] GitHub Actions 自动编译

### 计划中 🚧
- [ ] Android → PC (Windows/macOS) 控制（Python 服务端已完成）
- [ ] 多点触控平板大屏 UI 优化
- [ ] 横屏适配（手机端平板式布局）
- [ ] 蓝牙连接模式
- [ ] 连接历史记录
- [ ] 碰撞反馈（触感）
- [ ] 屏幕辅助指示（平板端显示鼠标光标位置）

---

<div align="center">

**Made with ❤️ for the Android community**

[![GitHub stars](https://img.shields.io/github/stars/unfar/Touch-control?style=social)](https://github.com/unfar/Touch-control/stargazers)
[![GitHub issues](https://img.shields.io/github/issues/unfar/Touch-control?style=social)](https://github.com/unfar/Touch-control/issues)

</div>
