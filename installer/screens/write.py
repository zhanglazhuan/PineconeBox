"""Injection screen — offset-mounts SD system partition, writes APK."""

import subprocess
import threading

import pygame

from ui import Screen, draw_text, draw_button, Color
from config import SD_DEVICE, SD_SYSTEM_OFFSET
from inject import inject, InjectProgress


class InjectProgressScreen(Screen):
    def __init__(self, surface, size):
        super().__init__(surface, size)
        self.inject_done = False
        self.stage_texts = [
            "挂载 system 分区",
            "注入 PineCone 桌面",
            "写入完成",
        ]
        self.stage_done = [False, False, False]
        self.error_msg = ""
        self.apk_data = None

    def set_apk_data(self, apk_bytes):
        self.apk_data = apk_bytes

    def on_enter(self):
        super().on_enter()
        self._start_inject()

    def _start_inject(self):
        if self.apk_data is None:
            self.error_msg = "APK 数据未加载"
            return

        def run():
            try:
                progress = InjectProgress()
                progress.stage_text = self.stage_texts

                inject(self.apk_data, SD_DEVICE, SD_SYSTEM_OFFSET,
                       "/tmp/pinecone-inject", progress)

                self.stage_done = progress.done[:]
                self.inject_done = True
            except Exception as e:
                self.error_msg = str(e)

        t = threading.Thread(target=run, daemon=True)
        t.start()

    def handle_event(self, event):
        if event.type == pygame.KEYDOWN and self.inject_done:
            self._reboot()
        return None

    @staticmethod
    def _reboot():
        """Force immediate reboot via sysrq — skip systemd to avoid ext4 errors."""
        try:
            with open("/proc/sysrq-trigger", "w") as f:
                f.write("s")  # sync (best-effort)
                f.write("b")  # reboot
        except Exception:
            subprocess.run(["sudo", "reboot", "-f"], check=False)

    def draw(self):
        super().draw()
        cx = self.w // 2

        draw_text(self.surface, "正在注入 PineCone 桌面...", (cx, 80), "h2", Color.TEXT)

        for i, (text, done) in enumerate(zip(self.stage_texts, self.stage_done)):
            y = 200 + i * 64
            marker = "✅" if done else ("⏳" if i > 0 and self.stage_done[i - 1] else "⬜")
            color = Color.PRIMARY if done else Color.TEXT_DIM
            draw_text(self.surface, f"{marker}  {text}", (cx, y), "body", color)

        if self.inject_done:
            draw_text(self.surface, "注入完成！系统即将重启", (cx, self.h - 160), "body", Color.PRIMARY)
            draw_text(self.surface, "新系统启动后将自动进入 PineCone 桌面",
                      (cx, self.h - 120), "small", Color.TEXT_DIM)
            draw_button(self.surface, "确认重启",
                        (cx - 100, self.h - 80, 200, 50), "primary")
        elif self.error_msg:
            draw_text(self.surface, f"错误: {self.error_msg}",
                      (cx, self.h - 100), "body", Color.DANGER)
