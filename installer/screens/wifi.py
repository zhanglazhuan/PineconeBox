"""Wi-Fi connection screen."""

import pygame
import subprocess
import threading
from ui import Screen, draw_text, draw_button, draw_list_item, draw_input_field, draw_card, draw_rounded_rect, Color, FONTS


class WifiScreen(Screen):
    def __init__(self, surface, size, **kwargs):
        super().__init__(surface, size, **kwargs)
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
        self._cursor_timer = 0.0
        self._overlay = None  # lazy init in draw
        self.connect_error = ""
        self.show_error = False

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
        self._cursor_timer += dt
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

    def _do_connect(self, ssid, password):
        """Background thread: connect and handle result."""
        ok, err = self.connect(ssid, password)
        if not ok:
            if "ecret" in err or "password" in err.lower() or "密码" in err:
                self.connect_error = f"密码错误，请重试"
            elif "timeout" in err or "超时" in err:
                self.connect_error = f"连接超时，请检查密码或信号"
            elif "not found" in err.lower() or "找不到" in err:
                self.connect_error = f"未找到网络 {ssid}"
            else:
                self.connect_error = f"连接失败: {err or '未知错误'}"
            self.show_error = True
            self.status_msg = ""

    def handle_event(self, event):
        # Handle TEXTINPUT (IME composition) in password mode
        if event.type == pygame.TEXTINPUT and self.show_password_input:
            self.password += event.text
            return None

        # Mouse click handling
        if event.type == pygame.MOUSEBUTTONDOWN and event.button == 1:
            mx, my = event.pos
            # "下一步" button (only when connected)
            if (self.connected or self.ethernet_ok):
                btn = (self.w - 240, self.h - 70, 180, 44)
                if btn[0] <= mx <= btn[0] + btn[2] and btn[1] <= my <= btn[1] + btn[3]:
                    return "next"
            # "重试" button (placeholder state)
            if len(self.networks) == 1 and self.networks[0]["signal"] == 0:
                ccx = self.content_x + (self.w - self.content_x) // 2
                btn = (ccx - 80, self.h // 2, 160, 48)
                if btn[0] <= mx <= btn[0] + btn[2] and btn[1] <= my <= btn[1] + btn[3]:
                    self.ethernet_ok = self._check_ethernet()
                    self.scan()
                    self.status_msg = "正在重新扫描..."
                    self.status_color = Color.WARNING
            return None

        if event.type != pygame.KEYDOWN:
            return super().handle_event(event)

        if self.show_error:
            if event.key in (pygame.K_RETURN, pygame.K_ESCAPE, pygame.K_SPACE):
                self.show_error = False
                self.connect_error = ""
            return None  # block all input while error is shown

        if self.connecting:
            return None  # block input while connecting

        if self.show_password_input:
            if event.key == pygame.K_RETURN:
                if self.password:
                    ssid = self.networks[self.selected_idx]["ssid"]
                    pwd = self.password
                    self.show_password_input = False
                    self.connecting = True
                    self.status_msg = "正在连接..."
                    self.status_color = Color.WARNING
                    threading.Thread(target=self._do_connect, args=(ssid, pwd), daemon=True).start()
                return None
            elif event.key == pygame.K_BACKSPACE:
                self.password = self.password[:-1]
                return None
            elif event.key == pygame.K_ESCAPE:
                self.show_password_input = False
                self.password = ""
                self.status_msg = ""
                return None  # close dialog only, don't quit
            # Character input is handled by TEXTINPUT; no else needed
            return None
        if event.key == pygame.K_UP:
            self.selected_idx = max(0, self.selected_idx - 1)
        elif event.key == pygame.K_DOWN:
            self.selected_idx = min(len(self.networks) - 1, self.selected_idx + 1)
        elif event.key == pygame.K_RETURN:
            # Retry on placeholder (scan failure / empty)
            if len(self.networks) == 1 and self.networks[0]["signal"] == 0:
                self.ethernet_ok = self._check_ethernet()
                self.scan()
                self.status_msg = "正在重新扫描..."
                self.status_color = Color.WARNING
                return None
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
        return super().handle_event(event)
    def draw(self):
        super().draw()
        title = "← 选择 Wi-Fi" if not self.connected else "← Wi-Fi 已连接"
        if self.ethernet_ok and not self.connected:
            title = "← 有线网络已连接"
        cx0 = self.content_x + 40
        ccx = self.content_x + (self.w - self.content_x) // 2

        draw_text(self.surface, title, (cx0, 60), "h2", Color.TEXT, center=False)

        # Connected Wi-Fi indicator (shown above scan results when connected)
        list_y = 140
        if self.connected:
            conn_y = 150
            conn_rect = (cx0, conn_y, self.w - cx0 - 60, 60)
            draw_card(self.surface, conn_rect, padding=14)
            inner = (conn_rect[0] + 18, conn_rect[1] + 14,
                     conn_rect[2] - 36, conn_rect[3] - 28)
            check = FONTS["body"].render("✓", True, Color.PRIMARY)
            self.surface.blit(check, (inner[0], inner[1] + inner[3] // 2 - check.get_height() // 2))
            label = FONTS["body"].render(
                f"已连接  {self.ssid}", True, Color.TEXT)
            self.surface.blit(label, (inner[0] + 32, inner[1] + inner[3] // 2 - label.get_height() // 2))
            list_y = conn_y + 100

        # Section header
        draw_text(self.surface, "扫描结果", (cx0, list_y), "body", Color.TEXT_DIM, center=False)
        list_y += 44

        # Network list or empty/error state
        is_placeholder = (len(self.networks) == 1 and self.networks[0]["signal"] == 0)

        if is_placeholder:
            draw_text(self.surface, self.networks[0]["ssid"], (ccx, self.h // 2 - 60),
                      "body", Color.TEXT_DIM)
            draw_button(self.surface, "重 试",
                        (ccx - 80, self.h // 2, 160, 48), "primary")
        else:
            # Fixed-height container with clipping
            bottom_reserved = 140  # space for bottom button/help
            container_h = self.h - list_y - bottom_reserved
            container_rect = (cx0 - 8, list_y, self.w - cx0 - 52, container_h)

            # Clip list items to container
            self.surface.set_clip(container_rect)
            item_h = 64
            item_pad = 8
            visible_count = int(container_h // (item_h + item_pad))
            # Keep selected item centered in view
            start_idx = max(0, self.selected_idx - visible_count // 2)
            # Clamp so we don't scroll past the end
            start_idx = min(start_idx, max(0, len(self.networks) - visible_count))

            for i in range(len(self.networks)):
                idx = start_idx + i
                if idx >= len(self.networks):
                    break
                net = self.networks[idx]
                item_rect = (cx0, list_y + 6 + i * (item_h + item_pad),
                             container_rect[2] - 16, item_h)
                level = max(1, min(5, net["signal"] // 20 + 1))
                draw_list_item(self.surface, item_rect, net["ssid"],
                               selected=(idx == self.selected_idx),
                               signal=level)
            self.surface.set_clip(None)

        # Password input - modal overlay
        if self.show_password_input:
            # Dim overlay (created once, reused)
            if self._overlay is None:
                self._overlay = pygame.Surface((self.w, self.h), pygame.SRCALPHA)
            self._overlay.fill((0, 0, 0, 160))
            self.surface.blit(self._overlay, (0, 0))

            # Dialog card
            dlg_w, dlg_h = 420, 290
            dlg_x = ccx - dlg_w // 2
            dlg_y = self.h // 2 - dlg_h // 2 - 30
            draw_card(self.surface, (dlg_x, dlg_y, dlg_w, dlg_h))

            draw_text(self.surface,
                      f"连接到: {self.networks[self.selected_idx]['ssid']}",
                      (ccx, dlg_y + 40), "body", Color.TEXT)
            cursor = 0 if int(self._cursor_timer * 2) % 2 == 0 else 1
            draw_input_field(self.surface,
                             (dlg_x + 40, dlg_y + 85, dlg_w - 80, 50),
                             self.password, active=True,
                             cursor_frame=cursor)
            draw_button(self.surface, "连 接",
                        (dlg_x + dlg_w // 2 - 100, dlg_y + 175, 200, 48), "primary")
            draw_text(self.surface, "按 ESC 返回",
                      (ccx, dlg_y + dlg_h - 30), "small", Color.TEXT_DIM)

        # Error dialog
        if self.show_error:
            if self._overlay is None:
                self._overlay = pygame.Surface((self.w, self.h), pygame.SRCALPHA)
            self._overlay.fill((0, 0, 0, 160))
            self.surface.blit(self._overlay, (0, 0))

            dlg_w, dlg_h = 420, 180
            dlg_x = ccx - dlg_w // 2
            dlg_y = self.h // 2 - dlg_h // 2 - 30
            draw_card(self.surface, (dlg_x, dlg_y, dlg_w, dlg_h))

            draw_text(self.surface, "⚠ 连接失败", (ccx, dlg_y + 40), "h2", Color.WARNING)
            draw_text(self.surface, self.connect_error,
                      (ccx, dlg_y + 85), "body", Color.TEXT)
            draw_button(self.surface, "关 闭",
                        (dlg_x + dlg_w // 2 - 80, dlg_y + 120, 160, 44), "primary")

        # Status
        if self.status_msg and not self.connected:
            draw_text(self.surface, self.status_msg,
                      (ccx, self.h - 100), "body", self.status_color)

        if self.connected or self.ethernet_ok:
            if self.ethernet_ok and not self.connected:
                draw_text(self.surface, "✓ 有线网络已连接，无需 Wi-Fi",
                          (ccx, self.h - 80), "body", Color.PRIMARY)
            else:
                draw_text(self.surface, f"✓ 已连接 {self.ssid}",
                          (ccx, self.h - 80), "body", Color.PRIMARY)
            draw_button(self.surface, "下一步 →",
                        (self.w - 240, self.h - 70, 180, 44), "primary")
        else:
            lines = "[↑↓] 选择  [OK] 连接  [R] 刷新"
            if self.ethernet_ok:
                lines += "   [→] 跳过"
            draw_text(self.surface, lines,
                      (ccx, self.h - 40), "small", Color.TEXT_DIM)


def _key_to_char(key):
    """Fallback: convert pygame key code to character."""
    if pygame.K_a <= key <= pygame.K_z:
        return chr(key)
    if pygame.K_0 <= key <= pygame.K_9:
        return chr(key)
    return ""
