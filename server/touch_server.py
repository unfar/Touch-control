#!/usr/bin/env python3
"""
TouchControl 服务端
━━━━━━━━━━━━━━━━━━━━━

接收 Android 手机端 WebSocket 连接，将手势指令转化为本机鼠标/键盘操作。

支持平台：Windows / macOS / Linux
依赖安装：pip install websockets pyautogui

启动方式：python touch_server.py

可选参数：
  --host HOST      监听地址 (默认 0.0.0.0)
  --port PORT      监听端口 (默认 9090)
  --cursor-speed N 全局光标速度倍率 (默认 1.0)
  --name NAME      广播的服务名称 (默认 "My Computer")
"""

import asyncio
import json
import logging
import socket
import struct
import sys
import time
import threading

logging.basicConfig(
    level=logging.INFO,
    format="[%(asctime)s] %(levelname)s %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger("TouchServer")

# ─── 平台检测 ─────────────────────────────────────────────

IS_WINDOWS = sys.platform == "win32"
IS_MACOS = sys.platform == "darwin"
IS_LINUX = sys.platform == "linux"

# ─── 鼠标控制模块 ─────────────────────────────────────────

try:
    import pyautogui

    pyautogui.FAILSAFE = False  # 不限制屏幕边界
    HAS_PYAUTOGUI = True
except ImportError:
    logger.warning("pyautogui 未安装，鼠标控制功能不可用")
    HAS_PYAUTOGUI = False


class MouseController:
    """鼠标控制封装"""

    def __init__(self, cursor_speed: float = 1.0):
        self.cursor_speed = cursor_speed
        self.screen_w, self.screen_h = pyautogui.size() if HAS_PYAUTOGUI else (1920, 1080)

    def move(self, dx: float, dy: float):
        if not HAS_PYAUTOGUI:
            return
        dx = dx * self.cursor_speed
        dy = dy * self.cursor_speed
        x, y = pyautogui.position()
        new_x = max(0, min(self.screen_w - 1, x + dx))
        new_y = max(0, min(self.screen_h - 1, y + dy))
        pyautogui.moveTo(int(new_x), int(new_y))

    def click(self, button: str = "left"):
        if not HAS_PYAUTOGUI:
            return
        btn = "left" if button == "left" else ("right" if button == "right" else "middle")
        pyautogui.click(button=btn)

    def down(self, button: str = "left"):
        if not HAS_PYAUTOGUI:
            return
        btn = "left" if button == "left" else "right"
        pyautogui.mouseDown(button=btn)

    def up(self, button: str = "left"):
        if not HAS_PYAUTOGUI:
            return
        btn = "left" if button == "left" else "right"
        pyautogui.mouseUp(button=btn)

    def scroll(self, dy: float, dx: float = 0.0):
        if not HAS_PYAUTOGUI:
            return
        # pyautogui.scroll 的 dy 正=上滚，负=下滚
        clicks = int(dy / 10)  # 10px ≈ 1 齿
        if clicks != 0:
            pyautogui.scroll(-clicks)  # 注意符号：手指上滑 dy 正，对应往上滚
        if dx != 0:
            h_clicks = int(dx / 10)
            if h_clicks != 0:
                # 水平滚动 (Win: Shift+滚轮, macOS: 直接支持)
                if IS_MACOS:
                    # macOS 用 scroll API 的横向参数
                    pyautogui.hscroll(h_clicks)
                else:
                    # Windows/Linux 用 Shift + scroll
                    import pyautogui as pg
                    pg.keyDown("shift")
                    pg.scroll(-h_clicks)
                    pg.keyUp("shift")


# ─── 键盘控制模块 ─────────────────────────────────────────

# 特殊键映射（pyautogui 接受的按键名）
SPECIAL_KEYS = {
    "enter": "enter",
    "tab": "tab",
    "escape": "esc",
    "backspace": "backspace",
    "delete": "delete",
    "left": "left",
    "right": "right",
    "up": "up",
    "down": "down",
    "space": "space",
    "caps_lock": "capslock",
    "shift": "shift",
    "ctrl": "ctrl",
    "alt": "alt",
    "meta": "win" if IS_WINDOWS else "command",
    # 功能键
    "f1": "f1",
    "f2": "f2",
    "f3": "f3",
    "f4": "f4",
    "f5": "f5",
    "f6": "f6",
    "f7": "f7",
    "f8": "f8",
    "f9": "f9",
    "f10": "f10",
    "f11": "f11",
    "f12": "f12",
}

# 需要按住然后松开组合键的修饰符
MODIFIERS = {"ctrl", "alt", "shift", "meta"}


class KeyboardController:

    @staticmethod
    def _resolve_key(key: str) -> str:
        """将协议中的键名转为 pyautogui 接受的键名"""
        return SPECIAL_KEYS.get(key.lower(), key)

    @staticmethod
    def tap(key: str, modifiers: list = None):
        if not HAS_PYAUTOGUI:
            return
        resolved = KeyboardController._resolve_key(key)
        mods = [KeyboardController._resolve_key(m) for m in (modifiers or [])]

        if resolved in MODIFIERS:
            # 按一下修饰键（toggle）
            pyautogui.press(resolved)
            return

        # 按下修饰键 → 按主键 → 释放修饰键
        for m in mods:
            pyautogui.keyDown(m)
        pyautogui.press(resolved)
        for m in reversed(mods):
            pyautogui.keyUp(m)

    @staticmethod
    def press(key: str, modifiers: list = None):
        if not HAS_PYAUTOGUI:
            return
        resolved = KeyboardController._resolve_key(key)
        mods = [KeyboardController._resolve_key(m) for m in (modifiers or [])]
        for m in mods:
            pyautogui.keyDown(m)
        pyautogui.keyDown(resolved)

    @staticmethod
    def release(key: str, modifiers: list = None):
        if not HAS_PYAUTOGUI:
            return
        resolved = KeyboardController._resolve_key(key)
        mods = [KeyboardController._resolve_key(m) for m in (modifiers or [])]
        pyautogui.keyUp(resolved)
        for m in reversed(mods):
            pyautogui.keyUp(m)

    @staticmethod
    def type_text(text: str):
        if not HAS_PYAUTOGUI:
            return
        # 对特殊字符做转义
        pyautogui.write(text, interval=0.01)


# ─── UDP 发现服务 ─────────────────────────────────────────

DISCOVERY_PORT = 9091
DISCOVERY_MSG = b"TOUCHCONTROL_DISCOVER"


def run_udp_discovery(server_port: int, server_name: str):
    """后台线程：监听 UDP 发现广播并回复"""
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)

    try:
        sock.bind(("0.0.0.0", DISCOVERY_PORT))
    except OSError:
        logger.warning(f"UDP 发现端口 {DISCOVERY_PORT} 被占用，跳过局域网发现")
        return

    logger.info(f"UDP 发现服务已启动 (端口 {DISCOVERY_PORT})")

    while True:
        try:
            data, addr = sock.recvfrom(256)
            if data == DISCOVERY_MSG:
                response = f"TOUCHCONTROL_SERVER:{server_port}:{server_name}"
                sock.sendto(response.encode(), addr)
                logger.info(f"发现请求来自 {addr[0]}:{addr[1]} → 已回复")
        except Exception as e:
            logger.error(f"UDP 发现错误: {e}")
            break


# ─── 指令处理 ─────────────────────────────────────────────

class CommandHandler:
    """解析 WebSocket 收到的 JSON 指令并执行"""

    def __init__(self, mouse: MouseController, keyboard: KeyboardController):
        self.mouse = mouse
        self.keyboard = keyboard

    async def handle(self, data: str) -> str | None:
        try:
            msg = json.loads(data)
        except json.JSONDecodeError:
            return json.dumps({"type": "error", "message": "invalid json"})

        msg_type = msg.get("type", "")
        action = msg.get("action", "")

        # ── 系统指令 ──
        if msg_type == "system":
            if action == "ping":
                return json.dumps({"type": "system", "action": "pong"})

        # ── 鼠标指令 ──
        if msg_type == "mouse":
            if action == "move":
                self.mouse.move(float(msg.get("dx", 0)), float(msg.get("dy", 0)))
            elif action == "click":
                self.mouse.click(msg.get("button", "left"))
            elif action == "down":
                self.mouse.down(msg.get("button", "left"))
            elif action == "up":
                self.mouse.up(msg.get("button", "left"))
            elif action == "scroll":
                self.mouse.scroll(float(msg.get("dy", 0)), float(msg.get("dx", 0)))

        # ── 键盘指令 ──
        if msg_type == "key":
            key = msg.get("key", "")
            modifiers = msg.get("modifiers", [])
            if action == "tap":
                self.keyboard.tap(key, modifiers)
            elif action == "press":
                self.keyboard.press(key, modifiers)
            elif action == "release":
                self.keyboard.release(key, modifiers)
            elif action == "type":
                self.keyboard.type_text(msg.get("text", ""))

        return None


# ─── WebSocket 服务 ───────────────────────────────────────

async def handle_client(websocket, path=None):
    """处理单个 WebSocket 客户端连接"""
    logger.info(f"客户端已连接: {websocket.remote_address}")

    mouse = MouseController()
    keyboard = KeyboardController()
    handler = CommandHandler(mouse, keyboard)

    try:
        async for message in websocket:
            response = await handler.handle(message)
            if response:
                await websocket.send(response)
    except asyncio.CancelledError:
        pass
    except Exception as e:
        logger.error(f"连接异常: {e}")
    finally:
        logger.info(f"客户端断开: {websocket.remote_address}")


async def main():
    import argparse

    parser = argparse.ArgumentParser(description="TouchControl 服务端")
    parser.add_argument("--host", default="0.0.0.0", help="监听地址")
    parser.add_argument("--port", type=int, default=9090, help="监听端口")
    parser.add_argument("--cursor-speed", type=float, default=1.0, help="光标速度倍率")
    parser.add_argument("--name", default=socket.gethostname(), help="服务器名称（用于发现）")
    args = parser.parse_args()

    if not HAS_PYAUTOGUI:
        logger.error("❌ pyautogui 未安装！请执行: pip install pyautogui")
        logger.error("macOS 还需在 系统设置 → 隐私与安全性 → 辅助功能 中授权终端")
        sys.exit(1)

    # 启动 UDP 发现线程
    udp_thread = threading.Thread(
        target=run_udp_discovery,
        args=(args.port, args.name),
        daemon=True,
    )
    udp_thread.start()

    # ASCII Banner
    banner = f"""
╔══════════════════════════════════════╗
║        TouchControl Server           ║
║                                      ║
║   ┌──────────┐      ┌──────────┐    ║
║   │ 📱 Phone  │ ───► │ 🖥️  This PC│    ║
║   └──────────┘  WS  └──────────┘    ║
║                                      ║
║   Host: {args.host:<15s}  ║
║   Port: {args.port:<5d}                       ║
║   Name: {args.name:<19s}  ║
╚══════════════════════════════════════╝
"""
    print(banner)

    # 启动 WebSocket 服务
    try:
        import websockets

        async with websockets.serve(
                handle_client,
                args.host,
                args.port,
                ping_interval=20,
                ping_timeout=10,
        ):
            logger.info(f"🎯 服务已启动！手机端请连接 ws://<本机IP>:{args.port}")
            if IS_MACOS:
                logger.info("💡 macOS 用户：请在 系统设置→隐私与安全性→辅助功能 中授权")
            if IS_WINDOWS:
                logger.info("💡 Windows 用户：如果鼠标控制无效，请以管理员身份运行")

            await asyncio.Future()  # 保持运行
    except ImportError:
        logger.error("❌ websockets 未安装！请执行: pip install websockets")
        sys.exit(1)
    except OSError as e:
        logger.error(f"❌ 端口 {args.port} 被占用: {e}")
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())
