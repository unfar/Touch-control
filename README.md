# TouchControl

<div align="center">

## 📱 ↔ 📟 跨设备触控神器

**一个 App，两种模式 — 手机当触摸板，平板当被控端**

[![Build](https://github.com/unfar/Touch-control/actions/workflows/build.yml/badge.svg)](https://github.com/unfar/Touch-control/actions/workflows/build.yml)
[![Platform](https://img.shields.io/badge/Android-14%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-2024.06-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

---

## 🌟 项目简介

TouchControl 是一个**纯 Android 端**的跨设备触控方案。安装一个 App，在手机上它是**触摸板控制器**，在平板上它是**被控接收端**，通过 **蓝牙 RFCOMM** 实现安全连接。

> 💡 灵感：当你的 Android 平板没有键盘鼠标时，用手机代替触摸板来操作它。

---

## 🎯 核心架构

```
┌─────────────────────────────┐     Bluetooth RFCOMM     ┌─────────────────────────────┐
│         📱 手机模式           │                            │         📟 平板模式           │
│         (控制器)             │ ◄────── JSON 协议 ──────► │         (被控端)             │
│                              │                            │                              │
│  ┌───────────────────────┐   │  {"type":"mouse",           │  ┌───────────────────────┐  │
│  │   触摸板状态机引擎       │   │   "action":"move",        │  │  蓝牙服务端            │  │
│  │   • TRACKING → MOVING  │   │   "dx":0.05, "dy":0.02}  │  │  (平板接收手机指令)   │  │
│  │   • 指针加速曲线       │   │                            │  └───────────┬───────────┘  │
│  │   • 长按→拖拽(400ms)  │   │                            │              ▼              │
│  │   • 双指滚动/右键     │   │                            │  ┌───────────────────────┐  │
│  ├───────────────────────┤   │                            │  │  AccessibilityService  │  │
│  │   蓝牙自动连接        │   │                            │  │  dispatchGesture()    │  │
│  │   已配对设备快速连    │   │                            │  │  → 模拟真实触摸/手势   │  │
│  └───────────────────────┘   │                            │  └───────────────────────┘  │
│                              │                            │  ┌───────────────────────┐  │
│                              │                            │  │  🖱 光标覆盖层         │  │
│                              │                            │  │  CursorOverlayManager │  │
│                              │                            │  │  cursor_17.png 箭头  │  │
│                              │                            │  └───────────────────────┘  │
└─────────────────────────────┘                            └─────────────────────────────┘
```

---

## ✨ 功能特性

### 📱 手机模式（控制器）

| 手势 / 功能 | 效果 | 说明 |
|:---|:---|:---|
| 👆 **单指滑动** | 鼠标移动 | 指针加速曲线，小移精确定位，大移快速跨屏 |
| 👆 **单指轻点** | 左键单击 | 40ms 静态按压，精准点击图标/按钮 |
| 👆 **长按 400ms + 滑动** | 拖拽操作 | 长按触发拖拽模式，再滑动拖移 |
| ✌️ **双指滑动** | 滚轮滚动 | 平滑滚动，独立滚动加速曲线 |
| ✌️ **双指轻点** | 右键单击 | 双指抬起触发 |
| 🌙 **深色/浅色主题** | 自动跟随系统或手动切换 |

### 📟 平板模式（被控端）

| 功能 | 说明 |
|:---|:---|
| 🔵 **蓝牙服务端** | RFCOMM 协议，手机直连平板，无需局域网 |
| 🖱️ **光标覆盖层** | `CursorOverlayManager` — 悬浮箭头，跟随触摸位置 |
| 🖼️ **cursor_17.png 箭头** | 方形黑色箭头，21dp 大小，热点对齐尖端 |
| 👆 **手势模拟** | 通过 `AccessibilityService.dispatchGesture()` 模拟真实触摸 |
| ✌️ **双指滚动** | 平滑滚动模拟 |
| 👆 **长按右键** | 长按模拟右键菜单 |
| 📳 **震动反馈** | 点击时平板和手机同时震动确认 |
| ⌨️ **系统按键** | Home / Back / Recents / 音量 |

---

## 🚀 快速开始

### 安装 App

```bash
git clone https://github.com/unfar/Touch-control.git
# 用 Android Studio 打开 → Sync Gradle → Build APK
```

或者从 [GitHub Actions](https://github.com/unfar/Touch-control/actions/workflows/build.yml) 下载最新编译的 APK。

### 配置使用

#### 平板端（被控）

```
1. 安装并打开 TouchControl，选择「📟 平板模式」
2. 进入 设置 → 无障碍 → TouchControl → 开启服务
3. 回到 App，打开服务端开关
4. 等待手机连接
```

#### 手机端（控制）

```
1. 安装并打开 TouchControl，选择「📱 手机模式」
2. 搜索并连接平板蓝牙设备
3. 连接成功 → 滑动触摸板控制平板！
```

---

## 🛠️ 技术栈

| 层面 | 技术选型 |
|:---|:---|
| **语言** | Kotlin 2.0.21 |
| **UI** | Jetpack Compose + Material 3 (Material You) |
| **通信** | 蓝牙 RFCOMM |
| **触摸模拟** | AccessibilityService `dispatchGesture()` |
| **光标渲染** | `WindowManager.TYPE_ACCESSIBILITY_OVERLAY` + ImageView |
| **光标图标** | cursor_17.png（黑色箭头，1470×1471 → 密度适配 21dp） |
| **指针加速** | 自定义加速曲线：`output = input × 1.8 + input^1.6 × 3.5` |
| **滚动加速** | 独立柔和曲线，线性 + 指数混合 |
| **手势引擎** | 状态机：IDLE → TRACKING → DRAGGING / SCROLLING |
| **指针跟踪** | `mutableMapOf<PointerId, Offset>` 精确跟踪多指 |
| **震动反馈** | `VibratorManager` / `VibrationEffect`（手机 30ms / 平板 20ms） |
| **最低版本** | Android 14 (API 34) |
| **CI/CD** | GitHub Actions 自动编译 APK |

---

## 📂 项目结构

```
TouchControl/
├── .github/workflows/
│   └── build.yml                    # GitHub Actions 自动编译
│
├── app/src/main/java/com/touchcontrol/
│   ├── MainActivity.kt              # 主入口 + 模式路由
│   ├── TouchControlApp.kt           # Application 类
│   │
│   ├── network/
│   │   ├── BluetoothClient.kt       # 蓝牙客户端（手机→平板）
│   │   └── BluetoothServer.kt       # 蓝牙服务端（平板接收）
│   │
│   ├── accessibility/
│   │   ├── TouchControlService.kt   # AccessibilityService 触摸模拟
│   │   └── CursorOverlayManager.kt  # 🖱 光标覆盖层（cursor_17.png）
│   │
│   ├── gesture/
│   │   ├── GestureProtocol.kt       # JSON 通信协议定义
│   │   ├── PointerAcceleration.kt   # 📈 指针加速曲线算法
│   │   └── TouchpadEngine.kt        # 旧版手势引擎（兼容）
│   │
│   ├── data/
│   │   └── SettingsRepository.kt    # DataStore 持久化设置
│   │
│   └── ui/
│       ├── components/
│       │   ├── QrCodeGenerator.kt   # ZXing QR 码生成
│       │   └── QrCodeScanner.kt     # CameraX + ML Kit 扫码器
│       ├── theme/                   # Material 3 深色/浅色主题
│       ├── navigation/              # 路由定义
│       └── screens/
│           ├── ModeSelectionScreen.kt   # 🏁 角色选择
│           ├── ConnectionScreen.kt      # 🔗 蓝牙连接管理
│           ├── TouchpadScreen.kt        # 🎯 触摸板主页（状态机引擎）
│           ├── TabletReceiverScreen.kt  # 📟 平板接收页面
│           └── SettingsScreen.kt        # ⚙️ 设置页面
│
├── app/src/main/res/
│   └── drawable{-mdpi~xxxhdpi}/
│       └── cursor_arrow.png         # 🖱 光标图标密度适配
│
├── build.gradle.kts                 # 项目级 Gradle 配置
├── settings.gradle.kts
└── README.md
```

---

## 🗺️ 开发路线

### 已完成 ✅
- [x] Android 手机 → Android 平板 触摸控制（蓝牙 RFCOMM）
- [x] 蓝牙设备搜索/配对/自动连接
- [x] 触摸板状态机引擎（TRACKING / DRAGGING / SCROLLING）
- [x] 指针加速曲线（小范围精确 + 大范围快速）
- [x] 光标覆盖层（cursor_17.png 黑色箭头）
- [x] 长按 400ms → 拖拽模式
- [x] 双指滑动滚动 + 双指轻触右键
- [x] 点击震动反馈（手机 + 平板）
- [x] GitHub Actions 自动编译

### 计划中 🚧
- [ ] 横屏适配（手机端平板式布局）
- [ ] 连接历史记录
- [ ] 三指手势（返回桌面/切换应用）

---

<div align="center">

**Made with ❤️ for the Android community**

[![GitHub stars](https://img.shields.io/github/stars/unfar/Touch-control?style=social)](https://github.com/unfar/Touch-control/stargazers)
[![GitHub issues](https://img.shields.io/github/issues/unfar/Touch-control?style=social)](https://github.com/unfar/Touch-control/issues)

</div>
