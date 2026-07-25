"""Welcome / startup screen."""

import pygame
from ui import Screen, draw_text, draw_rounded_rect, Color


class WelcomeScreen(Screen):
    def __init__(self, surface, size):
        super().__init__(surface, size)
        self.timer = 0.0
        self.auto_advance = 3.0
        self.fade_alpha = 0

    def update(self, dt):
        self.timer += dt
        self.fade_alpha = min(255, int(self.timer * 128))
        if self.timer >= self.auto_advance:
            return "next"
        return None

    def handle_event(self, event):
        if event.type == pygame.KEYDOWN and event.key == pygame.K_RETURN:
            return "next"
        return None

    def draw(self):
        super().draw()
        cx, cy = self.w // 2, self.h // 2

        draw_text(self.surface, "🍍", (cx, cy - 80), "h1", Color.PRIMARY)

        logo_text = pygame.font.Font(None, 48).render("P i n e C o n e", True, Color.TEXT)
        logo_text.set_alpha(self.fade_alpha)
        tr = logo_text.get_rect(center=(cx, cy))
        self.surface.blit(logo_text, tr)

        draw_text(self.surface, "松 果 智 学", (cx, cy + 50), "h2", Color.TEXT_DIM)

        bar_w, bar_h = 300, 4
        bar_x, bar_y = cx - bar_w // 2, self.h - 120
        draw_rounded_rect(self.surface, (bar_x, bar_y, bar_w, bar_h), Color.SURFACE, 4)
        fill_w = int(bar_w * min(self.timer / self.auto_advance, 1.0))
        if fill_w > 0:
            draw_rounded_rect(self.surface, (bar_x, bar_y, fill_w, bar_h), Color.PRIMARY, 4)

        draw_text(self.surface, "正在初始化...", (cx, self.h - 80), "small", Color.TEXT_DIM)
