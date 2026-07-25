"""Wi-Fi connection screen."""

import pygame
import subprocess
from ui import Screen, draw_text, draw_button, draw_list_item, draw_input_field, Color


class WifiScreen(Screen):
    def __init__(self, surface, size):
        super().__init__(surface, size)
        self.networks = []
        self.selected_idx = 0
        self.password = ""
        self.connected = False
        self.ssid = ""
        self.show_password_input = False
        self.connecting = False
        self.status_msg = ""
        self.status_color = Color.TEXT_DIM
        self.ethernet_ok = self._check_ethernet()

    def on_enter(self):
        super().on_enter()
        self.ethernet_ok = self._check_ethernet()
        self.scan()

    def update(self, dt):
        if self.ethernet_ok and not self.connected:
            self.connected = True
            self.ssid = "有线网络"
            self.status_msg = "有线网络已连接，自动跳过"
            self.status_color = Color.PRIMARY
            return "next"
        return None

    def _check_ethernet(self):
        """Check if ethernet is already connected."""
        try:
            result = subprocess.run(
                ["nmcli", "-t", "-f", "DEVICE,STATE", "device", "status"],
                capture_output=True, text=True, timeout=5)
            for line in result.stdout.strip().split('\n'):
                if line.startswith('eth') or line.startswith('en'):
                    if ':connected' in line:
                        return True
        except Exception:
            pass
        return False

    def scan(self):
        """Scan for Wi-Fi networks using nmcli."""
        try:
            result = subprocess.run(
                ["nmcli", "-t", "-f", "SSID,SIGNAL,SECURITY", "device", "wifi", "list"],
                capture_output=True, text=True, timeout=10)
            seen = set()
            self.networks = []
            for line in result.stdout.strip().split('\n'):
                parts = line.split(':')
                if len(parts) >= 2 and parts[0] and parts[0] not in seen:
                    seen.add(parts[0])
                    signal = int(parts[1]) if parts[1].isdigit() else 0
                    secure = parts[2] if len(parts) > 2 else ""
                    self.networks.append({
                        "ssid": parts[0], "signal": signal,
                        "secure": "WPA" in secure or "WEP" in secure or True
                    })
            self.networks.sort(key=lambda n: -n["signal"])
            if not self.networks:
                self.networks = [{"ssid": "未扫描到网络，按 R 刷新", "signal": 0, "secure": False}]
        except Exception:
            self.networks = [{"ssid": "扫描失败，按 R 重试", "signal": 0, "secure": False}]

    def connect(self, ssid, password):
        """Connect to a Wi-Fi network. Returns (success, message)."""
        self.connecting = True
        self.status_msg = "正在连接..."
        self.status_color = Color.WARNING
        try:
            cmd = ["nmcli", "device", "wifi", "connect", ssid]
            if password:
                cmd += ["password", password]
            result = subprocess.run(
                cmd, capture_output=True, text=True, timeout=30)
            self.connecting = False
            if result.returncode == 0:
                self.connected = True
                self.ssid = ssid
                self.status_msg = f"已连接 {ssid}"
                self.status_color = Color.PRIMARY
                return True, ""
            else:
                err = result.stderr.strip().replace('Error: ', '')
                self.status_msg = f"连接失败: {err}"
                self.status_color = Color.DANGER
                return False, err
        except subprocess.TimeoutExpired:
            self.connecting = False
            self.status_msg = "连接超时，请检查密码"
            self.status_color = Color.DANGER
            return False, "timeout"
        except Exception as e:
            self.connecting = False
            self.status_msg = f"错误: {e}"
            self.status_color = Color.DANGER
            return False, str(e)

    def handle_event(self, event):
        if event.type != pygame.KEYDOWN:
            return None

        if self.connecting:
            return None  # block input while connecting

        if self.show_password_input:
            if event.key == pygame.K_RETURN:
                if self.password:
                    self.connect(self.networks[self.selected_idx]["ssid"], self.password)
                    self.show_password_input = False
                    return None
            elif event.key == pygame.K_BACKSPACE:
                self.password = self.password[:-1]
            elif event.key == pygame.K_ESCAPE:
                self.show_password_input = False
                self.password = ""
                self.status_msg = ""
            elif event.unicode and event.unicode.isprintable():
                self.password += event.unicode
            return None

        if event.key == pygame.K_UP:
            self.selected_idx = max(0, self.selected_idx - 1)
        elif event.key == pygame.K_DOWN:
            self.selected_idx = min(len(self.networks) - 1, self.selected_idx + 1)
        elif event.key == pygame.K_RETURN:
            if self.connected or self.ethernet_ok:
                return "next"
            net = self.networks[self.selected_idx]
            self.show_password_input = True
            self.password = ""
            self.status_msg = ""
        elif event.key == pygame.K_RIGHT:
            if self.connected or self.ethernet_ok:
                return "next"
        elif event.key == pygame.K_r:
            self.ethernet_ok = self._check_ethernet()
            self.scan()
            self.status_msg = "已刷新"
            self.status_color = Color.TEXT_DIM
        return None

    def draw(self):
        super().draw()
        title = "← 选择 Wi-Fi" if not self.connected else "← Wi-Fi 已连接"
        if self.ethernet_ok and not self.connected:
            title = "← 有线网络已连接"
        draw_text(self.surface, title, (160, 60), "h2", Color.TEXT, center=False)

        # Network list
        item_h = 64
        list_y = 110
        max_visible = 8
        start_idx = max(0, self.selected_idx - max_visible // 2)
        for i in range(min(max_visible, len(self.networks))):
            idx = start_idx + i
            if idx >= len(self.networks):
                break
            net = self.networks[idx]
            item_rect = (80, list_y + i * (item_h + 8), self.w - 160, item_h)
            signal_bars = "▂▄▆█" if net["signal"] > 50 else "▂▄▆" if net["signal"] > 25 else "▂▄"
            label = f"📶 {net['ssid']}"
            draw_list_item(self.surface, item_rect, label, signal_bars,
                           selected=(idx == self.selected_idx))

        # Password input
        if self.show_password_input:
            pw_y = self.h - 200
            draw_text(self.surface,
                      f"连接到: {self.networks[self.selected_idx]['ssid']}",
                      (self.w // 2, pw_y), "body", Color.TEXT)
            draw_input_field(self.surface,
                             (self.w // 2 - 200, pw_y + 40, 400, 50),
                             self.password, active=True)
            draw_button(self.surface, "连 接",
                        (self.w // 2 - 100, pw_y + 110, 200, 50), "primary")

        # Status
        if self.status_msg:
            draw_text(self.surface, self.status_msg,
                      (self.w // 2, self.h - 100), "body", self.status_color)
        if self.connecting:
            draw_text(self.surface, "⏳", (self.w // 2, self.h - 140), "h2", Color.WARNING)

        if self.connected or self.ethernet_ok:
            if self.ethernet_ok and not self.connected:
                draw_text(self.surface, "● 有线网络已连接，无需 Wi-Fi",
                          (self.w // 2, self.h - 60), "body", Color.PRIMARY)
            else:
                draw_text(self.surface, f"● 已连接  {self.ssid}",
                          (self.w // 2, self.h - 60), "body", Color.PRIMARY)
            draw_button(self.surface, "下一步 →",
                        (self.w - 240, self.h - 70, 180, 44), "primary")
        else:
            lines = "[↑↓] 选择  [OK] 连接  [R] 刷新"
            if self.ethernet_ok:
                lines += "   [→] 跳过"
            draw_text(self.surface, lines,
                      (self.w // 2, self.h - 40), "small", Color.TEXT_DIM)
