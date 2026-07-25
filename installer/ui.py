"""PineCone Installer — UI component library for Pygame framebuffer."""

import os
import pygame
from config import Color

FONTS = {}

# Bundled font directory — relative to this file (works regardless of CWD/config)
_FONT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "assets")

# Cross-platform CJK font FILE paths (not font names, to avoid pygame SysFont bug)
CJK_FONT_PATHS = [
    os.path.join(_FONT_DIR, "NotoSansSC-Regular.ttf"),          # Bundled
    "C:/Windows/Fonts/msyh.ttc",                                 # Windows
    "C:/Windows/Fonts/simhei.ttf",
    "C:/Windows/Fonts/simsun.ttc",
    "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",   # Pi / Linux
    "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
]

MONO_FONT_PATHS = [
    os.path.join(_FONT_DIR, "JetBrainsMono-Regular.ttf"),        # Bundled
    "/usr/share/fonts/truetype/noto/NotoSansMono-Regular.ttf",
    "C:/Windows/Fonts/consola.ttf",
]


def _try_load_font(paths, size):
    """Try each path, return first font file that exists and renders CJK."""
    for p in paths:
        try:
            if os.path.exists(p):
                font = pygame.font.Font(p, size)
                # Verify glyph renders with real pixels (not tofu/empty)
                test = font.render("中", True, (255, 255, 255))
                try:
                    arr = pygame.surfarray.pixels_red(test)
                    if arr.any():
                        return font
                except Exception:
                    # surfarray may not be available; fallback to width check
                    if test.get_width() > size // 3:
                        return font
        except Exception:
            continue
    return pygame.font.Font(None, size)


def load_fonts():
    """Call once after pygame.init()."""
    FONTS["h1"]    = _try_load_font(CJK_FONT_PATHS, 48)
    FONTS["h2"]    = _try_load_font(CJK_FONT_PATHS, 36)
    FONTS["body"]  = _try_load_font(CJK_FONT_PATHS, 24)
    FONTS["small"] = _try_load_font(CJK_FONT_PATHS, 18)
    FONTS["mono"]   = _try_load_font(MONO_FONT_PATHS, 28)
    FONTS["mono_s"] = _try_load_font(MONO_FONT_PATHS, 20)


def draw_rounded_rect(surface, rect, color, radius=12):
    """Draw a filled rounded rectangle."""
    x, y, w, h = rect
    if w <= 0 or h <= 0:
        return
    rect_surf = pygame.Surface((w, h), pygame.SRCALPHA)
    pygame.draw.rect(rect_surf, color, (0, 0, w, h), border_radius=radius)
    surface.blit(rect_surf, (x, y))


def draw_gradient_bar(surface, rect, progress, color_start, color_end, radius=8):
    """Draw a horizontal gradient progress bar. progress 0.0-1.0."""
    x, y, w, h = rect
    draw_rounded_rect(surface, rect, Color.SURFACE, radius)
    if progress <= 0:
        return
    fill_w = int(w * progress)
    if fill_w < radius * 2:
        fill_w = radius * 2
    for i in range(fill_w):
        t = i / max(fill_w - 1, 1)
        r = int(color_start[0] + (color_end[0] - color_start[0]) * t)
        g = int(color_start[1] + (color_end[1] - color_start[1]) * t)
        b = int(color_start[2] + (color_end[2] - color_start[2]) * t)
        fill_surf = pygame.Surface((1, h), pygame.SRCALPHA)
        fill_surf.fill((r, g, b, 255))
        surface.blit(fill_surf, (x + i, y))


def draw_button(surface, text, rect, variant="primary", enabled=True):
    """Draw a button. variant: primary / danger / ghost."""
    x, y, w, h = rect
    if variant == "primary":
        color = Color.PRIMARY if enabled else Color.TEXT_DIM
        draw_rounded_rect(surface, rect, color, 12)
        text_color = Color.BLACK if enabled else Color.TEXT_DIM
    elif variant == "danger":
        pygame.draw.rect(surface, Color.DANGER, rect, 2, border_radius=12)
        text_color = Color.DANGER
    else:
        text_color = Color.TEXT_DIM

    label = FONTS["body"].render(text, True, text_color)
    label_rect = label.get_rect(center=(x + w // 2, y + h // 2))
    surface.blit(label, label_rect)


def draw_card(surface, rect, padding=24):
    """Draw a dark card with border. Returns inner content rect."""
    draw_rounded_rect(surface, rect, Color.SURFACE, 16)
    pygame.draw.rect(surface, Color.BORDER, rect, 1, border_radius=16)
    x, y, w, h = rect
    return (x + padding, y + padding, w - 2 * padding, h - 2 * padding)


def draw_input_field(surface, rect, text, active=False):
    """Draw a text input field with bottom glow when active."""
    x, y, w, h = rect
    draw_rounded_rect(surface, rect, Color.SURFACE, 12)
    border_color = Color.PRIMARY if active else Color.BORDER
    pygame.draw.rect(surface, border_color, rect, 2, border_radius=12)
    if active:
        glow_rect = (x + 4, y + h - 4, w - 8, 4)
        draw_rounded_rect(surface, glow_rect, Color.PRIMARY, 2)

    display_text = text if text else "(输入密码)"
    c = Color.TEXT if text else Color.TEXT_DIM
    label = FONTS["body"].render(display_text, True, c)
    label_rect = label.get_rect(midleft=(x + 16, y + h // 2))
    surface.blit(label, label_rect)


def draw_text(surface, text, pos, font_key="body", color=None, center=True):
    """Draw anti-aliased text."""
    if color is None:
        color = Color.TEXT
    font = FONTS.get(font_key, FONTS["body"])
    label = font.render(text, True, color)
    rect = label.get_rect(center=pos) if center else label.get_rect(topleft=pos)
    surface.blit(label, rect)
    return rect


def draw_list_item(surface, rect, text, sub_text=None, selected=False):
    """Draw a selectable list item (for Wi-Fi list)."""
    bg_color = (63, 185, 80, 40) if selected else Color.SURFACE
    draw_rounded_rect(surface, rect, bg_color, 12)
    border_color = Color.PRIMARY if selected else Color.BORDER
    pygame.draw.rect(surface, border_color, rect, 2, border_radius=12)

    x, y, w, h = rect
    draw_text(surface, text, (x + 60, y + h // 2), "body", Color.TEXT, center=False)
    if sub_text:
        r = FONTS["small"].render(sub_text, True, Color.TEXT_DIM)
        r_rect = r.get_rect(midright=(x + w - 24, y + h // 2))
        surface.blit(r, r_rect)


def lerp_color(c1, c2, t):
    """Linear interpolate between two RGB colors."""
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))


class Screen:
    """Base class for installer screens."""
    def __init__(self, surface, size):
        self.surface = surface
        self.w, self.h = size
        self._entered = False

    def on_enter(self):
        """Called when this screen becomes active. Override for init logic."""

    def handle_event(self, event):
        return None

    def update(self, dt):
        pass

    def draw(self):
        self.surface.fill(Color.BG)
