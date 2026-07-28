"""Welcome / startup screen."""

import os
import pygame
from ui import Screen, draw_text, draw_rounded_rect, Color, CJK_FONT_PATHS

_SCREENS_DIR = os.path.dirname(os.path.abspath(__file__))
LOGO_PATH = os.path.join(_SCREENS_DIR, "logo.png")


class WelcomeScreen(Screen):
    def __init__(self, surface, size, **kwargs):
        super().__init__(surface, size, **kwargs)
        self.timer = 0.0
        self.auto_advance = 3.0
        self.fade_alpha = 0
        self.logo = self._load_logo()
        self.cjk_72 = self._load_cjk_font(72)

    def _load_logo(self):
        try:
            img = pygame.image.load(LOGO_PATH).convert_alpha()
            target_h = 80
            h = img.get_height()
            w = img.get_width()
            scale = target_h / max(h, 1)
            return pygame.transform.smoothscale(img, (int(w * scale), target_h))
        except Exception:
            return None

    def _load_cjk_font(self, size):
        for p in CJK_FONT_PATHS:
            try:
                if os.path.exists(p):
                    return pygame.font.Font(p, size)
            except Exception:
                continue
        return pygame.font.Font(None, size)

    def update(self, dt):
        self.timer += dt
        self.fade_alpha = min(255, int(self.timer * 128))
        if self.timer >= self.auto_advance:
            return "next"
        return None

    def handle_event(self, event):
        if event.type == pygame.KEYDOWN and event.key == pygame.K_RETURN:
            return "next"
        return super().handle_event(event)

    def draw(self):
        super().draw()
        # Center in content area (past sidebar)
        cx = self.content_x + (self.w - self.content_x) // 2
        cy = self.h // 2

        # Logo image (replaces emoji)
        if self.logo:
            logo_rect = self.logo.get_rect(center=(cx, cy - 150))
            self.surface.blit(self.logo, logo_rect)
        else:
            draw_text(self.surface, "🍍", (cx, cy - 150), "h1", Color.PRIMARY)

        logo_text = pygame.font.Font(None, 72).render("P i n e C o n e", True, Color.TEXT)
        logo_text.set_alpha(self.fade_alpha)
        tr = logo_text.get_rect(center=(cx, cy - 30))
        self.surface.blit(logo_text, tr)

        # 松果智学 — 72px CJK font
        szgx = self.cjk_72.render("松 果 智 学", True, Color.TEXT_DIM)
        szgx_rect = szgx.get_rect(center=(cx, cy + 60))
        self.surface.blit(szgx, szgx_rect)

        bar_w, bar_h = 300, 4
        bar_x, bar_y = cx - bar_w // 2, self.h - 120
        draw_rounded_rect(self.surface, (bar_x, bar_y, bar_w, bar_h), Color.SURFACE, 4)
        fill_w = int(bar_w * min(self.timer / self.auto_advance, 1.0))
        if fill_w > 0:
            draw_rounded_rect(self.surface, (bar_x, bar_y, fill_w, bar_h), Color.PRIMARY, 4)

        draw_text(self.surface, "正在初始化...", (cx, self.h - 80), "small", Color.TEXT_DIM)
