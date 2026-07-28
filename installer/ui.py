"""PineCone Installer — UI component library for Pygame framebuffer."""

import os
import pygame
from config import Color

FONTS = {}
SIDEBAR_W = 220

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


def draw_input_field(surface, rect, text, active=False, masked=False, cursor_frame=0):
    """Draw a text input field with bottom glow when active.

    masked: show dots instead of actual text (for passwords).
    cursor_frame: alternates 0/1 to blink the cursor.
    """
    x, y, w, h = rect
    draw_rounded_rect(surface, rect, Color.SURFACE, 12)
    border_color = Color.PRIMARY if active else Color.BORDER
    pygame.draw.rect(surface, border_color, rect, 2, border_radius=12)
    if active:
        glow_rect = (x + 4, y + h - 4, w - 8, 4)
        draw_rounded_rect(surface, glow_rect, Color.PRIMARY, 2)

    if text:
        display = "●" * len(text) if masked else text
        c = Color.TEXT
    else:
        display = "(输入密码)"
        c = Color.TEXT_DIM

    label = FONTS["body"].render(display, True, c)
    label_rect = label.get_rect(midleft=(x + 16, y + h // 2))
    surface.blit(label, label_rect)

    # Blinking cursor
    if active and cursor_frame == 0:
        cursor_x = label_rect.right + 4
        cursor_y1 = y + h // 2 - 12
        cursor_y2 = y + h // 2 + 12
        pygame.draw.line(surface, Color.TEXT, (cursor_x, cursor_y1),
                         (cursor_x, cursor_y2), 2)


def draw_text(surface, text, pos, font_key="body", color=None, center=True):
    """Draw anti-aliased text."""
    if color is None:
        color = Color.TEXT
    font = FONTS.get(font_key, FONTS["body"])
    label = font.render(text, True, color)
    rect = label.get_rect(center=pos) if center else label.get_rect(topleft=pos)
    surface.blit(label, rect)
    return rect


def draw_list_item(surface, rect, text, sub_text=None, selected=False,
                   signal=None):
    """Draw a selectable list item (for Wi-Fi list).

    signal: 0-5 signal strength, draws 5 vertical bars on the right.
    """
    bg_color = (63, 185, 80, 40) if selected else Color.SURFACE
    draw_rounded_rect(surface, rect, bg_color, 12)
    border_color = Color.PRIMARY if selected else Color.BORDER
    pygame.draw.rect(surface, border_color, rect, 2, border_radius=12)

    x, y, w, h = rect
    label = FONTS["body"].render(text, True, Color.TEXT)
    label_rect = label.get_rect(midleft=(x + 60, y + h // 2))
    surface.blit(label, label_rect)

    # Signal strength bars (right side)
    if signal is not None:
        bar_w, max_h = 5, 24
        gap = 3
        total_w = 5 * bar_w + 4 * gap
        start_x = x + w - 28 - total_w
        base_y = y + h // 2 + max_h // 2

        for i in range(5):
            bx = start_x + i * (bar_w + gap)
            bh = int(max_h * (i + 1) / 5)  # graduated: short → tall
            filled = i < signal
            bar_color = Color.PRIMARY if filled else Color.BORDER
            pygame.draw.rect(surface, bar_color,
                             (bx, base_y - bh, bar_w, bh), border_radius=2)

    if sub_text:
        r = FONTS["small"].render(sub_text, True, Color.TEXT_DIM)
        r_rect = r.get_rect(midright=(x + w - 24, y + h // 2))
        surface.blit(r, r_rect)


def lerp_color(c1, c2, t):
    """Linear interpolate between two RGB colors."""
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))


def draw_step_sidebar(surface, current, total, titles, h):
    """Draw a vertical step indicator on the left side.

    current: 0-based index of the active step
    total:   total number of steps
    titles:  list of step label strings
    h:       surface height (for vertical centering)
    """
    # Sidebar background
    sidebar = pygame.Surface((SIDEBAR_W, h), pygame.SRCALPHA)
    sidebar.fill((22, 27, 34, 220))  # semi-transparent surface
    pygame.draw.line(sidebar, Color.BORDER, (SIDEBAR_W - 1, 0), (SIDEBAR_W - 1, h), 1)
    surface.blit(sidebar, (0, 0))

    if total == 0:
        return

    circle_r = 16
    line_h = 48
    step_h = circle_r * 2 + line_h
    total_h = step_h * total - line_h  # no line after last step
    start_y = (h - total_h) // 2
    cx_sidebar = int(SIDEBAR_W * 0.15) + circle_r

    for i in range(total):
        cy = start_y + i * step_h

        # Connecting line (above this circle)
        if i > 0:
            line_top = cy - line_h
            pygame.draw.line(surface, Color.BORDER if i > current else Color.PRIMARY,
                             (cx_sidebar, line_top), (cx_sidebar, cy - circle_r), 2)

        # Circle
        if i < current:
            # Completed — green filled with checkmark
            pygame.draw.circle(surface, Color.PRIMARY, (cx_sidebar, cy), circle_r)
            check = FONTS["small"].render("✓", True, Color.BLACK)
            surface.blit(check, check.get_rect(center=(cx_sidebar, cy)))
        elif i == current:
            # Active — green filled with white number, subtle glow
            glow = pygame.Surface((circle_r * 2 + 12, circle_r * 2 + 12), pygame.SRCALPHA)
            pygame.draw.circle(glow, (63, 185, 80, 60), (circle_r + 6, circle_r + 6), circle_r + 6)
            surface.blit(glow, (cx_sidebar - circle_r - 6, cy - circle_r - 6))
            pygame.draw.circle(surface, Color.PRIMARY, (cx_sidebar, cy), circle_r)
            num = FONTS["body"].render(str(i + 1), True, Color.BLACK)
            surface.blit(num, num.get_rect(center=(cx_sidebar, cy)))
        else:
            # Pending — dimmed border only
            pygame.draw.circle(surface, Color.BORDER, (cx_sidebar, cy), circle_r, 2)
            num = FONTS["small"].render(str(i + 1), True, Color.TEXT_DIM)
            surface.blit(num, num.get_rect(center=(cx_sidebar, cy)))

        # Step title
        label_color = Color.TEXT if i == current else (Color.TEXT_DIM if i < current else Color.BORDER)
        label = FONTS["small"].render(titles[i], True, label_color)
        label_pos = (cx_sidebar + circle_r + 16, cy - label.get_height() // 2)
        surface.blit(label, label_pos)


class Screen:
    """Base class for installer screens."""
    def __init__(self, surface, size, step_index=0, total_steps=0, step_titles=None):
        self.surface = surface
        self.w, self.h = size
        self._entered = False
        self.step_index = step_index
        self.total_steps = total_steps
        self.step_titles = step_titles or []

    @property
    def content_x(self):
        """Left edge of the content area (past the sidebar)."""
        return SIDEBAR_W if self.total_steps > 0 else 0

    def on_enter(self):
        """Called when this screen becomes active. Override for init logic."""

    def handle_event(self, event):
        if event.type == pygame.KEYDOWN and event.key == pygame.K_ESCAPE:
            return "quit"
        return None

    def update(self, dt):
        pass

    def draw(self):
        self.surface.fill(Color.BG)
        if self.total_steps > 0 and self.step_titles:
            draw_step_sidebar(self.surface, self.step_index,
                              self.total_steps, self.step_titles, self.h)
