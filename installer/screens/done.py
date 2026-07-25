"""Done screen — success + shutdown."""

import pygame
import os
from ui import Screen, draw_text, draw_card, Color


class DoneScreen(Screen):
    def __init__(self, surface, size):
        super().__init__(surface, size)
        self.timer = 0.0
        self.shutdown_at = 30.0

    def update(self, dt):
        self.timer += dt
        if self.timer >= self.shutdown_at:
            os.system("poweroff")
        return None

    def handle_event(self, event):
        if event.type == pygame.KEYDOWN:
            if event.key in (pygame.K_RETURN, pygame.K_SPACE):
                os.system("poweroff")
                return "quit"
        return None

    def draw(self):
        super().draw()
        cx, cy = self.w // 2, self.h // 2

        draw_text(self.surface, "✅", (cx, cy - 120), "h1", Color.PRIMARY)
        draw_text(self.surface, "松果智学 安装完成！", (cx, cy - 50), "h2", Color.TEXT)

        card_rect = (cx - 300, cy + 20, 600, 220)
        draw_card(self.surface, card_rect)

        for i, line in enumerate([
            "① 拔掉电源线",
            "② 重新插上",
            "③ 静静等待",
            "开机约需 2 分钟",
        ]):
            draw_text(self.surface, line, (cx, cy + 60 + i * 36), "body", Color.TEXT)

        draw_text(self.surface, "下次开机 → PineCone OS",
                  (cx, self.h - 120), "body", Color.PRIMARY)

        remaining = max(0, int(self.shutdown_at - self.timer))
        draw_text(self.surface,
                  f"{remaining} 秒后自动关机，按任意键立即关机",
                  (cx, self.h - 60), "small", Color.TEXT_DIM)
