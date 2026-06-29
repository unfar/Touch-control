# TouchControl

<div align="center">
  <h3>📱 ↔ 📟 跨设备触控神器</h3>
  <p>一个 App，两种模式：手机当触摸板 · 平板当被控端</p>
</div>

---

## 架构

```
┌───────────────────────┐        WiFi / 局域网        ┌───────────────────────┐
│  手机（控制器模式）      │ ◄──── WebSocket 协议 ────► │  平板（被控端模式）    │
│  ┌─────────────────┐   │                            │  ┌─────────────────┐  │
│  │ 触摸板手势      │   │      {"type":"mouse",       │  │ WebSocket 服务端  │  │
│  │ QWERTY 键盘     │   │       "action":"move",      │  │ AccessibilityService │
│  │ 连接管理        │   │       "dx":10, "dy":5}      │  │ → 模拟触摸/手势    │  │
│  └─────────────────┘   │                            │  └─────────────────┘  │
└───────────────────────┘                            └───────────────────────┘
```

## ✨ 功能

### 📱 手机模式（控制器）
| 手势 | 效果 |
|------|------|
| 👆 单指滑动 | 鼠标移动 |
| 👆 单指轻点 | 左键单击 |
| 👆 单指双击 | 左键双击 |
| 👆 长按 + 拖动 | 拖拽操作 |
| ✌️ 双指滑动 | 滚轮滚动 |
| ✌️ 双指轻点 | 右键单击 |
| 🤏 双指捏合 | 缩放 |
| 键盘页面 | 完整 QWERTY + Ctrl/Alt/Shift + 文本发送 |
| 局域网自动发现 | UDP 广播自动找到服务端 |

### 📟 平板模式（被控端）
- **嵌入式 WebSocket 服务端** — 纯 Kotlin 实现，零依赖
- **AccessibilityService** — 通过 `dispatchGesture()` 模拟真实触摸
- **手势映射**：单击、长按(右键)、拖动、滚动
- **键盘支持**：全局操作（Home/Back/Recent）、文本粘贴输入
- **IP 显示**：自动获取本机 IP 供手机端连接

---

## 使用方式

### 首次启动
1. 安装 App 到手机**和**平板
2. 在**平板**上选择「平板模式」→ 开启无障碍服务 → 启动服务端
3. 在**手机上**选择「手机模式」→ 连接页面扫描或手动输入平板 IP
4. 连接成功后滑动手机即控制平板！

### 平板端设置
```
设置 → 无障碍 → 已安装的应用 → TouchControl → 
开启「TouchControl 服务」开关 → 确认启用
```

---

## 项目结构

```
TouchControl/
├── app/src/main/java/com/touchcontrol/
│   ├── MainActivity.kt          # 主入口 + 模式路由
│   ├── TouchControlApp.kt       # Application
│   ├── network/
│   │   ├── WebSocketClient.kt   # OkHttp WebSocket 客户端（手机端）
│   │   ├── EmbeddedWebSocketServer.kt  # 纯 Kotlin WS 服务端（平板端）
│   │   └── ServerDiscovery.kt   # UDP 局域网发现
│   ├── accessibility/
│   │   └── TouchControlService.kt  # AccessibilityService（平板触摸模拟）
│   ├── gesture/
│   │   ├── GestureProtocol.kt   # JSON 通信协议定义
│   │   └── TouchpadEngine.kt    # 手势识别引擎
│   ├── data/
│   │   └── SettingsRepository.kt  # DataStore 持久化
│   └── ui/
│       ├── theme/               # Material 3 主题
│       ├── screens/
│       │   ├── ModeSelectionScreen.kt  # 🏁 首次启动模式选择
│       │   ├── ConnectionScreen.kt     # 连接页面
│       │   ├── TouchpadScreen.kt       # 🎯 触摸板
│       │   ├── KeyboardScreen.kt       # ⌨️ 键盘
│       │   ├── TabletReceiverScreen.kt # 📟 平板接收页
│       │   └── SettingsScreen.kt       # ⚙️ 设置
├── server/                      # 🖥️ (可选) Python 服务端
│   ├── touch_server.py          #   用于控制 Windows/macOS 电脑
│   └── requirements.txt
├── build.gradle.kts
└── README.md
```

---

## 未来路线

- [x] Android → Android 手机控制平板
- [ ] Android → PC (Python 服务端已完成，等测试)
- [ ] 二维码扫描连接
- [ ] 多点触控平板大屏优化
- [ ] 屏幕镜像（显示平板画面在手机上）
