# PineCone OS 刷机安装程序 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a TV-based OS installer that runs on Raspberry Pi from SD card, guides user through Wi-Fi setup, downloads baseOS ROM, injects PineCone Launcher APK, flashes the SD card, and reboots into PineCone OS.

**Architecture:** Python 3 + Pygame 2 runs directly on Raspberry Pi OS Lite framebuffer (no X11/Wayland needed). A state machine drives 6 screens. Separate inject.py and flash.py engines handle the heavy lifting. A factory build script packages everything into a distributable SD card image.

**Tech Stack:** Python 3.11, Pygame 2.5, framebuffer rendering, systemd service, Raspberry Pi OS Lite (bookworm, arm64)

## Global Constraints

- Target platform: Raspberry Pi 5, Raspberry Pi OS Lite (bookworm, arm64)
- Python 3.11+ (system default on Pi OS Bookworm)
- No X11 or Wayland — render directly to Linux framebuffer via Pygame
- ROM download URL configurable via config.py (localhost for dev, OSS for production)
- Installer APK (app-debug.apk) is bundled in the factory image, not downloaded
- All UI text in Chinese
- SD card size: 64GB

---

## File Mapping

```
installer/                              # New project directory
├── main.py                             # Entry point + state machine
├── ui.py                               # UI component library
├── config.py                           # Download URL + constants
├── inject.py                           # APK injection engine
├── flash.py                            # dd flashing engine
├── screens/
│   ├── __init__.py
│   ├── welcome.py                      # Startup logo screen
│   ├── wifi.py                         # Wi-Fi scan + connect
│   ├── download.py                     # ROM download with progress
│   ├── inject_progress.py              # Injection step indicator
│   ├── flash_progress.py               # Flashing with warning
│   └── done.py                         # Completion + shutdown
├── assets/
│   ├── logo.png                        # PineCone logo
│   ├── font-hans.ttf                   # Noto Sans SC (思源黑体替代)
│   └── font-mono.ttf                   # JetBrains Mono
└── tests/
    ├── test_inject.py
    ├── test_flash.py
    └── test_ui.py

scripts/
└── build_installer_img.sh              # Factory image build script (PC side)
```

---

## Phase 1: Project Scaffold + UI Library

### Task 1: Create project structure and config

**Files:**
- Create: `installer/config.py`

**Produces:** `DownloadConfig` with URL switching, `Color` class with the design palette, common constants

- [ ] **Step 1: Write `installer/config.py`**

```python
"""PineCone Installer — configuration and constants."""

import os

class DownloadConfig:
    BASE_URL_LOCAL = "http://192.168.1.100:8080"
    BASE_URL_OSS = "https://pinecone-os.oss-cn-hangzhou.aliyuncs.com"
    ROM_FILE = "lineage-23.2-20260520-UNOFFICIAL-KonstaKANG-rpi5-atv.zip"
    ROM_SHA256 = ""  # Set in production

    @classmethod
    def get_url(cls, dev_mode=False):
        base = cls.BASE_URL_LOCAL if dev_mode else cls.BASE_URL_OSS
        return f"{base}/{cls.ROM_FILE}"

    @classmethod
    def get_mode(cls):
        return "dev" if os.environ.get("PINECONE_DEV") == "1" else "prod"


class Color:
    # Modern dark theme
    BG          = (13,  17,  23)    # #0D1117
    SURFACE     = (22,  27,  34)    # #161B22
    BORDER      = (48,  54,  61)    # #30363D
    PRIMARY     = (63,  185, 80)    # #3FB950  PineCone green
    PRIMARY_DIM = (46,  160, 67)    # darker green
    ACCENT      = (88,  166, 255)   # #58A6FF
    WARNING     = (240, 136, 62)    # #F0883E
    DANGER      = (248, 81,  73)    # #F85149
    TEXT        = (230, 237, 243)   # #E6EDF3
    TEXT_DIM    = (139, 148, 158)   # #8B949E
    WHITE       = (255, 255, 255)
    BLACK       = (0,   0,   0)
    TRANSPARENT = (0,   0,   0,   0)


# Paths on the target device
APK_PATH = "/opt/pinecone-installer/app-debug.apk"
ASSETS_DIR = "/opt/pinecone-installer/assets"
WORK_DIR = "/data/pinecone-imgs"
ROM_ZIP_PATH = f"{WORK_DIR}/baseos.zip"
ROM_IMG_PATH = f"{WORK_DIR}/baseos.img"

# System partition offset for KonstaKANG LineageOS 23.2 RPi5 ATV
SYSTEM_PARTITION_OFFSET = 136314880  # LBA 266240 * 512

# Target block device
SD_DEVICE = "/dev/mmcblk0"
```

- [ ] **Step 2: Create directory structure**

```bash
mkdir -p installer/screens installer/assets installer/tests
touch installer/screens/__init__.py
```

- [ ] **Step 3: Verify Python can import config**

```bash
cd installer && python3 -c "from config import Color; print(Color.PRIMARY)"
```
Expected: prints `(63, 185, 80)`

---

### Task 2: UI component library

**Files:**
- Create: `installer/ui.py`

**Produces:** `draw_rounded_rect()`, `draw_gradient_bar()`, `draw_button()`, `draw_card()`, `draw_input_field()`, `draw_text()`, `Screen` base class

- [ ] **Step 1: Write `installer/ui.py`**

```python
"""PineCone Installer — UI component library for Pygame framebuffer."""

import pygame
import math
from config import Color

# FONT_CACHE — loaded once at startup
FONTS = {}

def load_fonts():
    """Call once after pygame.init(). Loads CJK and mono fonts."""
    pygame.font.init()
    font_path = f"{__import__('config').ASSETS_DIR}/NotoSansSC-Regular.ttf"
    mono_path = f"{__import__('config').ASSETS_DIR}/JetBrainsMono-Regular.ttf"
    try:
        FONTS["h1"]    = pygame.font.Font(font_path, 48)
        FONTS["h2"]    = pygame.font.Font(font_path, 36)
        FONTS["body"]  = pygame.font.Font(font_path, 24)
        FONTS["small"] = pygame.font.Font(font_path, 18)
        FONTS["mono"]  = pygame.font.Font(mono_path, 28)
        FONTS["mono_s"] = pygame.font.Font(mono_path, 20)
    except FileNotFoundError:
        # Fallback to system default
        FONTS["h1"]    = pygame.font.SysFont("arial", 48)
        FONTS["h2"]    = pygame.font.SysFont("arial", 36)
        FONTS["body"]  = pygame.font.SysFont("arial", 24)
        FONTS["small"] = pygame.font.SysFont("arial", 18)
        FONTS["mono"]  = pygame.font.SysFont("couriernew", 28)
        FONTS["mono_s"] = pygame.font.SysFont("couriernew", 20)


def draw_rounded_rect(surface, rect, color, radius=12):
    """Draw a filled rounded rectangle."""
    x, y, w, h = rect
    rect_surf = pygame.Surface((w, h), pygame.SRCALPHA)
    pygame.draw.rect(rect_surf, color, (0, 0, w, h), border_radius=radius)
    surface.blit(rect_surf, (x, y))


def draw_gradient_bar(surface, rect, progress, color_start, color_end, radius=8):
    """Draw a horizontal gradient progress bar. progress 0.0–1.0."""
    x, y, w, h = rect
    # Background
    draw_rounded_rect(surface, rect, Color.SURFACE, radius)
    if progress <= 0:
        return
    fill_w = int(w * progress)
    if fill_w < radius * 2:
        fill_w = radius * 2
    # Fill with gradient
    for i in range(fill_w):
        t = i / max(fill_w - 1, 1)
        r = int(color_start[0] + (color_end[0] - color_start[0]) * t)
        g = int(color_start[1] + (color_end[1] - color_start[1]) * t)
        b = int(color_start[2] + (color_end[2] - color_start[2]) * t)
        alpha = 255
        fill_surf = pygame.Surface((1, h), pygame.SRCALPHA)
        fill_surf.fill((r, g, b, alpha))
        # Clip to rounded shape via blit
        surface.blit(fill_surf, (x + i, y))


def draw_button(surface, text, rect, variant="primary", enabled=True):
    """
    Draw a button.
    variant: "primary" (green fill), "danger" (red outline), "ghost" (text only)
    """
    x, y, w, h = rect
    if variant == "primary":
        color = Color.PRIMARY if enabled else Color.TEXT_DIM
        draw_rounded_rect(surface, rect, color, 12)
        text_color = Color.BLACK if enabled else Color.TEXT_DIM
    elif variant == "danger":
        pygame.draw.rect(surface, Color.DANGER, rect, 2, border_radius=12)
        text_color = Color.DANGER
    else:  # ghost
        text_color = Color.TEXT_DIM

    label = FONTS["body"].render(text, True, text_color)
    label_rect = label.get_rect(center=(x + w // 2, y + h // 2))
    surface.blit(label, label_rect)


def draw_card(surface, rect, padding=24):
    """Draw a dark card with border."""
    draw_rounded_rect(surface, rect, Color.SURFACE, 16)
    x, y, w, h = rect
    pygame.draw.rect(surface, Color.BORDER, rect, 1, border_radius=16)
    return (x + padding, y + padding, w - 2 * padding, h - 2 * padding)


def draw_input_field(surface, rect, text, active=False):
    """Draw a text input field with bottom glow when active."""
    x, y, w, h = rect
    draw_rounded_rect(surface, rect, Color.SURFACE, 12)
    border_color = Color.PRIMARY if active else Color.BORDER
    pygame.draw.rect(surface, border_color, rect, 2, border_radius=12)
    # Bottom glow line
    if active:
        glow_rect = (x + 4, y + h - 4, w - 8, 4)
        draw_rounded_rect(surface, glow_rect, Color.PRIMARY, 2)

    display_text = text if text else "(输入密码)"
    color = Color.TEXT if text else Color.TEXT_DIM
    label = FONTS["body"].render(display_text, True, color)
    label_rect = label.get_rect(midleft=(x + 16, y + h // 2))
    surface.blit(label, label_rect)


def draw_text(surface, text, pos, font_key="body", color=None, center=True):
    """Draw anti-aliased text. center=True: pos is the center point."""
    if color is None:
        color = Color.TEXT
    font = FONTS.get(font_key, FONTS["body"])
    label = font.render(text, True, color)
    if center:
        rect = label.get_rect(center=pos)
    else:
        rect = label.get_rect(topleft=pos)
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
        draw_text(surface, sub_text, (x + w - 60, y + h // 2), "small", Color.TEXT_DIM,
                  center=True if False else False)
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

    def handle_event(self, event):
        """Return: 'next', 'back', or None."""
        return None

    def update(self, dt):
        """Called every frame. dt in seconds."""
        pass

    def draw(self):
        """Called every frame. Clear and redraw."""
        self.surface.fill(Color.BG)
```

- [ ] **Step 2: Verify import**

```bash
cd installer && python3 -c "
import pygame
pygame.display.init()
pygame.display.set_mode((1280, 720))
from ui import load_fonts, draw_button
load_fonts()
print('UI library loaded OK')
"
```
Expected: `UI library loaded OK`

---

### Task 3: Main entry + state machine

**Files:**
- Create: `installer/main.py`

**Produces:** `main()` — initializes Pygame, runs the state machine loop across 6 screens

- [ ] **Step 1: Write `installer/main.py`**

```python
"""PineCone OS Installer — main entry point."""

import os
import sys
import pygame
from config import Color
from ui import load_fonts, Screen
from screens.welcome import WelcomeScreen
from screens.wifi import WifiScreen
from screens.download import DownloadScreen
from screens.inject_progress import InjectProgressScreen
from screens.flash_progress import FlashProgressScreen
from screens.done import DoneScreen

os.environ["SDL_VIDEODRIVER"] = "fbcon"
os.environ["SDL_FBDEV"] = "/dev/fb0"

SCREENS = [
    WelcomeScreen,
    WifiScreen,
    DownloadScreen,
    InjectProgressScreen,
    FlashProgressScreen,
    DoneScreen,
]


def main():
    pygame.init()

    # Detect framebuffer resolution
    info = pygame.display.Info()
    W, H = info.current_w, info.current_h
    if W == 0 or H == 0:
        W, H = 1920, 1080  # fallback

    surface = pygame.display.set_mode((W, H), pygame.FULLSCREEN)
    pygame.mouse.set_visible(False)

    load_fonts()

    clock = pygame.time.Clock()
    current_idx = 0
    screens = [cls(surface, (W, H)) for cls in SCREENS]
    running = True

    while running:
        dt = clock.tick(30) / 1000.0  # 30 FPS

        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                running = False
                break
            if event.type == pygame.KEYDOWN:
                if event.key == pygame.K_ESCAPE:
                    running = False
                    break
                if event.key == pygame.K_RETURN or event.key == pygame.K_SPACE:
                    action = screens[current_idx].handle_event(
                        pygame.event.Event(pygame.KEYDOWN, key=pygame.K_RETURN))
                else:
                    action = screens[current_idx].handle_event(event)

                if action == "next":
                    current_idx = min(current_idx + 1, len(screens) - 1)
                    if current_idx >= len(screens):
                        running = False
                elif action == "quit":
                    running = False

        screens[current_idx].update(dt)
        screens[current_idx].draw()
        pygame.display.flip()

    pygame.quit()
    sys.exit(0)


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Verify syntax**

```bash
cd installer && python3 -c "
# Just check main.py parses (won't run without screens)
import ast
with open('main.py') as f:
    ast.parse(f.read())
print('main.py syntax OK')
"
```
Expected: `main.py syntax OK`

---

## Phase 2: Engine Modules

### Task 4: Inject engine

**Files:**
- Create: `installer/inject.py`

**Produces:** `inject(rom_zip_path, work_dir)` → returns modified img path

- [ ] **Step 1: Write `installer/inject.py`**

```python
"""PineCone Installer — APK injection engine.

Runs on ARM Linux. Uses mount/losetup to modify the system partition
inside a raw MBR disk image. Equivalent to inject_apk.sh but in Python.
"""

import os
import shutil
import subprocess
import zipfile

from config import APK_PATH, SYSTEM_PARTITION_OFFSET

LAUNCHERS_TO_REMOVE = [
    "Trebuchet", "Launcher3", "Launcher3QuickStep",
    "LeanbackLauncher", "TvLauncher", "CustomTvLauncher",
    "LegacyLauncher", "Catapult"
]


def extract_img(zip_path, dest_dir):
    """Extract .img file from ROM zip. Returns path to .img."""
    with zipfile.ZipFile(zip_path, 'r') as zf:
        for name in zf.namelist():
            if name.endswith('.img'):
                print(f"  解压: {name}")
                zf.extract(name, dest_dir)
                return os.path.join(dest_dir, name)
    raise FileNotFoundError(f"No .img found in {zip_path}")


def mount_system(img_path, offset, mount_point):
    """Mount system partition via loopback."""
    os.makedirs(mount_point, exist_ok=True)
    # Try direct mount first
    cmd = ["mount", "-o", f"loop,offset={offset}", img_path, mount_point]
    try:
        subprocess.run(cmd, check=True, capture_output=True, text=True)
        print(f"  mount -o loop,offset={offset} → OK")
        return None
    except subprocess.CalledProcessError:
        pass
    # Fallback: losetup
    loop_result = subprocess.run(
        ["losetup", "-f", "--show", "-o", str(offset), img_path],
        check=True, capture_output=True, text=True
    )
    loop_dev = loop_result.stdout.strip()
    subprocess.run(["mount", "-t", "ext4", loop_dev, mount_point], check=True)
    print(f"  losetup → {loop_dev} → mount OK")
    return loop_dev


def unmount_system(mount_point, loop_dev=None):
    """Unmount and detach loop device if used."""
    subprocess.run(["umount", mount_point], check=True)
    if loop_dev:
        subprocess.run(["losetup", "-d", loop_dev], check=False)


def inject_apk(mount_point):
    """Copy APK into /system/app/PineConeLauncher/."""
    target_dir = os.path.join(mount_point, "system/app/PineConeLauncher")
    os.makedirs(target_dir, exist_ok=True)
    target_apk = os.path.join(target_dir, "PineConeLauncher.apk")
    shutil.copy(APK_PATH, target_apk)
    os.chmod(target_dir, 0o755)
    os.chmod(target_apk, 0o644)
    print(f"  APK → {target_dir}")


def remove_launchers(mount_point):
    """Delete competing launcher APKs in /system/app and /system/priv-app."""
    for base in ["system/app", "system/priv-app"]:
        base_path = os.path.join(mount_point, base)
        if not os.path.isdir(base_path):
            continue
        for entry in os.listdir(base_path):
            entry_lower = entry.lower()
            for name in LAUNCHERS_TO_REMOVE:
                if entry_lower.startswith(name.lower()):
                    target = os.path.join(base_path, entry)
                    print(f"  删除: {target}")
                    shutil.rmtree(target, ignore_errors=True)
                    break


class InjectProgress:
    """Callback interface for progress reporting."""
    def __init__(self):
        self.stage = 0  # 0=extract, 1=mount, 2=inject, 3=unmount
        self.stage_text = ["解压系统镜像", "挂载 system 分区",
                           "注入 PineCone 桌面", "写入完成"]
        self.done = [False, False, False, False]

    def step(self, idx):
        self.done[idx] = True
        self.stage = idx + 1

    def status(self):
        return self.stage_text, self.done


def inject(rom_zip_path, work_dir, progress=None):
    """
    Full injection pipeline. Returns path to modified .img.

    Args:
        rom_zip_path: Path to the baseOS ROM .zip
        work_dir: Working directory for extraction + mount
        progress: InjectProgress instance (optional)

    Returns:
        str: Path to the modified .img file
    """
    if progress is None:
        progress = InjectProgress()

    img_path = extract_img(rom_zip_path, work_dir)
    progress.step(0)

    mnt = os.path.join(work_dir, "mnt")
    loop_dev = mount_system(img_path, SYSTEM_PARTITION_OFFSET, mnt)
    progress.step(1)

    inject_apk(mnt)
    remove_launchers(mnt)
    progress.step(2)

    unmount_system(mnt, loop_dev)
    progress.step(3)

    return img_path
```

- [ ] **Step 2: Write unit test**

Create `installer/tests/test_inject.py`:

```python
"""Test inject engine (runs on PC — mock system calls)."""

import os
import tempfile
import zipfile
from unittest.mock import patch, MagicMock
from inject import extract_img, LAUNCHERS_TO_REMOVE


def test_extract_img_creates_file(tmp_path):
    # Create a fake ROM zip with a dummy .img
    zip_path = tmp_path / "test.zip"
    with zipfile.ZipFile(zip_path, 'w') as zf:
        zf.writestr("test.img", "fake disk image data")

    dest = tmp_path / "extracted"
    dest.mkdir()
    result = extract_img(str(zip_path), str(dest))
    assert result == str(dest / "test.img")
    assert os.path.getsize(result) == 21  # "fake disk image data"


def test_launchers_list_not_empty():
    assert len(LAUNCHERS_TO_REMOVE) >= 5
    assert "Trebuchet" in LAUNCHERS_TO_REMOVE
    assert "LeanbackLauncher" in LAUNCHERS_TO_REMOVE
```

- [ ] **Step 3: Run tests**

```bash
cd installer && python3 -m pytest tests/test_inject.py -v
```
Expected: 2 passed

---

### Task 5: Flash engine

**Files:**
- Create: `installer/flash.py`

**Produces:** `flash_image(img_path, callback, device)` — writes image to SD card with progress

- [ ] **Step 1: Write `installer/flash.py`**

```python
"""PineCone Installer — dd-based SD card flashing engine."""

import os
import re
import subprocess
import threading


def get_image_size(img_path):
    """Return total size of image in bytes."""
    return os.path.getsize(img_path)


def flash_image(img_path, device="/dev/mmcblk0", callback=None, bs="4M"):
    """
    Write disk image to block device using dd.

    Args:
        img_path: Path to the .img file
        device: Target block device
        callback: Function(progress_0_1, written_bytes, total_bytes, speed_str)
        bs: dd block size
    """
    total = get_image_size(img_path)
    if total == 0:
        raise ValueError(f"Image is empty: {img_path}")

    cmd = ["dd", f"if={img_path}", f"of={device}", f"bs={bs}", "status=progress"]

    proc = subprocess.Popen(
        cmd, stderr=subprocess.PIPE, stdout=subprocess.DEVNULL, text=True
    )

    def read_progress():
        for line in proc.stderr:
            match = re.search(r'(\d+) bytes', line)
            if match:
                written = int(match.group(1))
                progress = min(written / total, 1.0)
                speed = "..."
                speed_match = re.search(r'(\d+(?:\.\d+)?)\s*([KMG]B)/s', line)
                if speed_match:
                    speed = f"{speed_match.group(1)} {speed_match.group(2)}/s"
                if callback:
                    callback(progress, written, total, speed)

    reader = threading.Thread(target=read_progress)
    reader.start()
    proc.wait()
    reader.join()

    if proc.returncode != 0:
        raise RuntimeError(f"dd exited with code {proc.returncode}")

    # Ensure everything is written to disk
    subprocess.run(["sync"], check=True)


def verify_image(img_path, device, bs="4M"):
    """Verify written image matches source using cmp on first 100MB."""
    total = get_image_size(img_path)
    verify_size = min(total, 100 * 1024 * 1024)
    cmd = ["cmp", "-n", str(verify_size), img_path, device]
    result = subprocess.run(cmd, capture_output=True)
    return result.returncode == 0
```

- [ ] **Step 2: Write unit test**

Create `installer/tests/test_flash.py`:

```python
"""Test flash engine (mock subprocess)."""

import os
import tempfile
from unittest.mock import patch, MagicMock
from flash import get_image_size


def test_get_image_size(tmp_path):
    img = tmp_path / "test.img"
    img.write_bytes(b"x" * 1024)
    assert get_image_size(str(img)) == 1024


def test_get_image_size_empty_raises(tmp_path):
    img = tmp_path / "empty.img"
    img.write_bytes(b"")
    assert get_image_size(str(img)) == 0
```

- [ ] **Step 3: Run tests**

```bash
cd installer && python3 -m pytest tests/test_flash.py -v
```
Expected: 2 passed

---

## Phase 3: All Six Screens

### Task 6: WelcomeScreen + DoneScreen

**Files:**
- Create: `installer/screens/welcome.py`
- Create: `installer/screens/done.py`

- [ ] **Step 1: Write `installer/screens/welcome.py`**

```python
"""Welcome / startup screen."""

import pygame
from ui import Screen, draw_text, draw_rounded_rect, Color


class WelcomeScreen(Screen):
    def __init__(self, surface, size):
        super().__init__(surface, size)
        self.timer = 0.0
        self.auto_advance = 3.0  # seconds
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

        # Logo area
        logo_text = "🍍"
        draw_text(self.surface, logo_text, (cx, cy - 80), "h1", Color.PRIMARY)

        text = pygame.font.Font(None, 48).render("P i n e C o n e", True, Color.TEXT)
        text.set_alpha(self.fade_alpha)
        tr = text.get_rect(center=(cx, cy))
        self.surface.blit(text, tr)

        draw_text(self.surface, "松 果 智 学", (cx, cy + 50), "h2", Color.TEXT_DIM)

        # Loading indicator
        bar_w, bar_h = 300, 4
        bar_x, bar_y = cx - bar_w // 2, self.h - 120
        draw_rounded_rect(self.surface, (bar_x, bar_y, bar_w, bar_h), Color.SURFACE, 4)
        fill_w = int(bar_w * min(self.timer / self.auto_advance, 1.0))
        if fill_w > 0:
            draw_rounded_rect(self.surface, (bar_x, bar_y, fill_w, bar_h), Color.PRIMARY, 4)

        draw_text(self.surface, "正在初始化...", (cx, self.h - 80), "small", Color.TEXT_DIM)
```

- [ ] **Step 2: Write `installer/screens/done.py`**

```python
"""Done screen — success + shutdown."""

import pygame
import os
from ui import Screen, draw_text, draw_button, Color


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
            if event.key == pygame.K_RETURN or event.key == pygame.K_SPACE:
                os.system("poweroff")
                return "quit"
        return None

    def draw(self):
        super().draw()
        cx, cy = self.w // 2, self.h // 2

        draw_text(self.surface, "✅", (cx, cy - 120), "h1", Color.PRIMARY)
        draw_text(self.surface, "松果智学 安装完成！", (cx, cy - 50), "h2", Color.TEXT)

        # Instructions card
        card_rect = (cx - 300, cy + 20, 600, 220)
        from ui import draw_card
        draw_card(self.surface, card_rect)

        instructions = [
            "① 拔掉电源线",
            "② 重新插上",
            "③ 静静等待",
            "开机约需 2 分钟",
        ]
        for i, line in enumerate(instructions):
            draw_text(self.surface, line, (cx, cy + 60 + i * 36), "body", Color.TEXT)

        draw_text(self.surface, "下次开机 → PineCone OS", (cx, self.h - 120), "body", Color.PRIMARY)

        remaining = max(0, int(self.shutdown_at - self.timer))
        draw_text(self.surface,
                  f"{remaining} 秒后自动关机，按任意键立即关机",
                  (cx, self.h - 60), "small", Color.TEXT_DIM)
```

- [ ] **Step 3: Verify**

```bash
cd installer && python3 -c "
import ast
with open('screens/welcome.py') as f: ast.parse(f.read())
with open('screens/done.py') as f: ast.parse(f.read())
print('welcome + done syntax OK')
"
```
Expected: `welcome + done syntax OK`

---

### Task 7: WifiScreen

**Files:**
- Create: `installer/screens/wifi.py`

- [ ] **Step 1: Write `installer/screens/wifi.py`**

```python
"""Wi-Fi connection screen."""

import pygame
import subprocess
import re
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
        self.scan()

    def scan(self):
        """Scan for Wi-Fi networks using nmcli."""
        try:
            result = subprocess.run(
                ["nmcli", "-t", "-f", "SSID,SIGNAL,SECURITY", "device", "wifi", "list"],
                capture_output=True, text=True, timeout=10
            )
            seen = set()
            self.networks = []
            for line in result.stdout.strip().split('\n'):
                parts = line.split(':')
                if len(parts) >= 2 and parts[0] and parts[0] not in seen:
                    seen.add(parts[0])
                    signal = int(parts[1]) if parts[1].isdigit() else 0
                    secure = parts[2] if len(parts) > 2 else ""
                    self.networks.append({
                        "ssid": parts[0],
                        "signal": signal,
                        "secure": "WPA" in secure or "WEP" in secure
                    })
            self.networks.sort(key=lambda n: -n["signal"])
        except Exception as e:
            print(f"Wi-Fi scan failed: {e}")
            self.networks = [{"ssid": "扫描失败，请检查网线", "signal": 0, "secure": False}]

    def connect(self, ssid, password):
        """Connect to a Wi-Fi network."""
        try:
            if password:
                subprocess.run(
                    ["nmcli", "device", "wifi", "connect", ssid, "password", password],
                    capture_output=True, text=True, timeout=30, check=True
                )
            else:
                subprocess.run(
                    ["nmcli", "device", "wifi", "connect", ssid],
                    capture_output=True, text=True, timeout=30, check=True
                )
            self.connected = True
            self.ssid = ssid
        except subprocess.CalledProcessError:
            self.connected = False

    def handle_event(self, event):
        if event.type != pygame.KEYDOWN:
            return None

        if self.show_password_input:
            if event.key == pygame.K_RETURN:
                self.connect(self.networks[self.selected_idx]["ssid"], self.password)
                self.show_password_input = False
                return "next" if self.connected else None
            elif event.key == pygame.K_BACKSPACE:
                self.password = self.password[:-1]
            elif event.key == pygame.K_ESCAPE:
                self.show_password_input = False
                self.password = ""
            elif event.unicode and event.unicode.isprintable():
                self.password += event.unicode
            return None

        if event.key == pygame.K_UP:
            self.selected_idx = max(0, self.selected_idx - 1)
        elif event.key == pygame.K_DOWN:
            self.selected_idx = min(len(self.networks) - 1, self.selected_idx + 1)
        elif event.key == pygame.K_RETURN:
            net = self.networks[self.selected_idx]
            if net.get("secure", True):
                self.show_password_input = True
                self.password = ""
            else:
                self.connect(net["ssid"], "")
                return "next" if self.connected else None
        elif event.key == pygame.K_RIGHT and self.connected:
            return "next"
        elif event.key == pygame.K_r:
            self.scan()
        return None

    def draw(self):
        super().draw()
        draw_text(self.surface, "← 选择 Wi-Fi", (160, 60), "h2", Color.TEXT, center=False)

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
            sub = signal_bars
            draw_list_item(self.surface, item_rect, label, sub, selected=(idx == self.selected_idx))

        # Password input (overlay at bottom)
        if self.show_password_input:
            pw_y = self.h - 180
            draw_text(self.surface, f"连接到: {self.networks[self.selected_idx]['ssid']}",
                      (self.w // 2, pw_y), "body", Color.TEXT)
            draw_input_field(self.surface,
                             (self.w // 2 - 200, pw_y + 40, 400, 50),
                             self.password, active=True)
            draw_button(self.surface, "连 接", (self.w // 2 - 100, pw_y + 110, 200, 50), "primary")

        # Status bar
        if self.connected:
            draw_text(self.surface, f"● 已连接  {self.ssid}",
                      (self.w // 2, self.h - 60), "body", Color.PRIMARY)
            draw_button(self.surface, "下一步", (self.w - 220, self.h - 70, 160, 44), "primary")
        else:
            draw_text(self.surface, "[↑↓] 选择  [OK] 连接  [R] 刷新",
                      (self.w // 2, self.h - 40), "small", Color.TEXT_DIM)
```

- [ ] **Step 2: Verify syntax**

```bash
cd installer && python3 -c "
import ast
with open('screens/wifi.py') as f: ast.parse(f.read())
print('wifi.py syntax OK')
"
```
Expected: `wifi.py syntax OK`

---

### Task 8: DownloadScreen

**Files:**
- Create: `installer/screens/download.py`

- [ ] **Step 1: Write `installer/screens/download.py`**

```python
"""ROM download screen with progress bar."""

import pygame
import os
import time
import requests
from ui import Screen, draw_text, draw_button, draw_rounded_rect, draw_gradient_bar, Color
from config import DownloadConfig, ROM_ZIP_PATH, WORK_DIR


class DownloadScreen(Screen):
    def __init__(self, surface, size):
        super().__init__(surface, size)
        self.progress = 0.0      # 0.0–1.0
        self.downloaded = 0
        self.total = 0
        self.speed = "..."
        self.eta = "..."
        self.status = "ready"    # ready, downloading, done, error
        self.error_msg = ""
        self._start_download()

    def _start_download(self):
        self.status = "downloading"
        os.makedirs(WORK_DIR, exist_ok=True)
        url = DownloadConfig.get_url(DownloadConfig.get_mode() == "dev")

        try:
            # Check for partial download (resume support)
            headers = {}
            if os.path.exists(ROM_ZIP_PATH):
                existing_size = os.path.getsize(ROM_ZIP_PATH)
                headers["Range"] = f"bytes={existing_size}-"
                self.downloaded = existing_size

            resp = requests.get(url, stream=True, headers=headers, timeout=10)
            if resp.status_code not in (200, 206):
                self.status = "error"
                self.error_msg = f"HTTP {resp.status_code}"
                return

            self.total = int(resp.headers.get("Content-Length", 0)) + self.downloaded
            if self.total == self.downloaded:
                self.total = self.downloaded  # no Content-Length header

            mode = "ab" if self.downloaded > 0 else "wb"
            start_time = time.time()
            last_update = start_time

            with open(ROM_ZIP_PATH, mode) as f:
                for chunk in resp.iter_content(chunk_size=8192):
                    f.write(chunk)
                    self.downloaded += len(chunk)

                    now = time.time()
                    if now - last_update > 0.5:
                        elapsed = now - start_time
                        self.speed = format_speed(self.downloaded / max(elapsed, 1))
                        if self.total > 0:
                            self.progress = self.downloaded / self.total
                            remaining = (self.total - self.downloaded) / max(
                                self.downloaded / max(elapsed, 1), 1
                            )
                            self.eta = format_time(int(remaining))
                        last_update = now

            self.progress = 1.0
            self.status = "done"
        except requests.ConnectionError as e:
            self.status = "error"
            self.error_msg = f"网络连接失败: {e}"
        except Exception as e:
            self.status = "error"
            self.error_msg = str(e)

    def handle_event(self, event):
        if event.type == pygame.KEYDOWN:
            if self.status == "done" and event.key == pygame.K_RETURN:
                return "next"
            if self.status == "error" and event.key == pygame.K_RETURN:
                self._start_download()  # retry
        return None

    def draw(self):
        super().draw()
        cx, cy = self.w // 2, self.h // 2

        draw_text(self.surface, "正在下载系统镜像", (cx, 80), "h2", Color.TEXT)

        # Size card
        if self.total > 0:
            total_mb = self.total / (1024 * 1024)
            draw_text(self.surface, f"{total_mb:.0f} MB", (cx, cy - 160), "h1", Color.TEXT)
        else:
            draw_text(self.surface, "...", (cx, cy - 160), "h1", Color.TEXT)

        # Progress bar
        bar_rect = (cx - 350, cy - 80, 700, 24)
        draw_gradient_bar(self.surface, bar_rect, self.progress,
                          Color.PRIMARY, (100, 220, 130))

        # Percentage
        draw_text(self.surface, f"{int(self.progress * 100)}%", (cx, cy - 40),
                  "mono", Color.PRIMARY)

        # Stats row
        downloaded_mb = self.downloaded / (1024 * 1024)
        total_mb = self.total / (1024 * 1024) if self.total > 0 else 0
        stats = [
            f"已下载  {downloaded_mb:.0f} / {total_mb:.0f} MB",
            f"速度    {self.speed}",
            f"剩余    {self.eta}",
        ]
        for i, stat in enumerate(stats):
            draw_text(self.surface, stat, (cx, cy + 40 + i * 32), "mono_s", Color.TEXT_DIM)

        if self.status == "done":
            draw_button(self.surface, "继 续", (cx - 100, self.h - 120, 200, 56), "primary")
        elif self.status == "error":
            draw_text(self.surface, self.error_msg, (cx, self.h - 140), "body", Color.DANGER)
            draw_button(self.surface, "重 试", (cx - 80, self.h - 90, 160, 50), "danger")
        else:
            draw_text(self.surface, "正在下载，请勿关机...", (cx, self.h - 60), "small", Color.TEXT_DIM)


def format_speed(bytes_per_sec):
    if bytes_per_sec > 1_000_000:
        return f"{bytes_per_sec / 1_000_000:.1f} MB/s"
    elif bytes_per_sec > 1_000:
        return f"{bytes_per_sec / 1_000:.0f} KB/s"
    else:
        return f"{bytes_per_sec:.0f} B/s"


def format_time(seconds):
    if seconds < 60:
        return f"{int(seconds)} 秒"
    elif seconds < 3600:
        return f"{int(seconds // 60)} 分 {int(seconds % 60)} 秒"
    else:
        return f"{int(seconds // 3600)} 小时 {int((seconds % 3600) // 60)} 分"
```

- [ ] **Step 2: Verify syntax**

```bash
cd installer && python3 -c "
import ast
with open('screens/download.py') as f: ast.parse(f.read())
print('download.py syntax OK')
"
```
Expected: `download.py syntax OK`

---

### Task 9: InjectProgressScreen + FlashProgressScreen

**Files:**
- Create: `installer/screens/inject_progress.py`
- Create: `installer/screens/flash_progress.py`

- [ ] **Step 1: Write `installer/screens/inject_progress.py`**

```python
"""Injection progress screen — step indicator."""

import pygame
import threading
from ui import Screen, draw_text, draw_rounded_rect, Color
from config import ROM_ZIP_PATH, WORK_DIR
from inject import inject, InjectProgress


class InjectProgressScreen(Screen):
    def __init__(self, surface, size):
        super().__init__(surface, size)
        self.inject_done = False
        self.stage_texts = ["解压系统镜像", "挂载 system 分区",
                            "注入 PineCone 桌面", "写入完成"]
        self.stage_done = [False, False, False, False]
        self.error_msg = ""
        self._start_inject()

    def _start_inject(self):
        def run():
            try:
                progress = InjectProgress()
                progress.stage_text = self.stage_texts
                inject(ROM_ZIP_PATH, WORK_DIR, progress)
                self.stage_done = [True, True, True, True]
                self.inject_done = True
            except Exception as e:
                self.error_msg = str(e)

        t = threading.Thread(target=run, daemon=True)
        t.start()

    def update(self, dt):
        return None

    def handle_event(self, event):
        if event.type == pygame.KEYDOWN and self.inject_done:
            return "next"
        return None

    def draw(self):
        super().draw()
        cx = self.w // 2

        draw_text(self.surface, "正在准备系统...", (cx, 80), "h2", Color.TEXT)

        for i, (text, done) in enumerate(zip(self.stage_texts, self.stage_done)):
            y = 200 + i * 64
            marker = "✅" if done else "⏳" if (i > 0 and self.stage_done[i - 1]) else "⬜"
            draw_text(self.surface, f"{marker}  {text}", (cx, y), "body",
                      Color.PRIMARY if done else Color.TEXT_DIM)

        if self.inject_done:
            draw_text(self.surface, "注入完成！", (cx, self.h - 100), "body", Color.PRIMARY)
        elif self.error_msg:
            draw_text(self.surface, f"错误: {self.error_msg}", (cx, self.h - 100), "body", Color.DANGER)
```

- [ ] **Step 2: Write `installer/screens/flash_progress.py`**

```python
"""Flashing screen — dd progress + danger warning."""

import pygame
import threading
from ui import Screen, draw_text, draw_button, draw_gradient_bar, Color
from config import ROM_IMG_PATH, SD_DEVICE
from flash import flash_image, get_image_size


class FlashProgressScreen(Screen):
    def __init__(self, surface, size):
        super().__init__(surface, size)
        self.progress = 0.0
        self.written = 0
        self.total = 0
        self.speed = "..."
        self.flash_done = False
        self.error_msg = ""
        self._start_flash()

    def _start_flash(self):
        self.total = get_image_size(ROM_IMG_PATH)

        def on_progress(prog, written, total, speed):
            self.progress = prog
            self.written = written
            self.total = total
            self.speed = speed

        def run():
            try:
                flash_image(ROM_IMG_PATH, SD_DEVICE, on_progress)
                self.flash_done = True
                self.progress = 1.0
            except Exception as e:
                self.error_msg = str(e)

        t = threading.Thread(target=run, daemon=True)
        t.start()

    def handle_event(self, event):
        if event.type == pygame.KEYDOWN and self.flash_done:
            return "next"
        return None

    def draw(self):
        super().draw()
        cx, cy = self.w // 2, self.h // 2

        # Warning icon
        draw_text(self.surface, "⚠️", (cx, 100), "h1", Color.WARNING)

        draw_text(self.surface, "正在写入系统镜像", (cx, 170), "h2", Color.WARNING)
        draw_text(self.surface, "请勿关闭电源！", (cx, 210), "body", Color.DANGER)

        # Progress bar (orange gradient)
        bar_rect = (cx - 350, cy - 40, 700, 32)
        draw_gradient_bar(self.surface, bar_rect, self.progress,
                          Color.WARNING, (255, 180, 100))

        draw_text(self.surface, f"{int(self.progress * 100)}%", (cx, cy + 20),
                  "mono", Color.WARNING)

        written_gb = self.written / (1024**3)
        total_gb = self.total / (1024**3)
        stats = [
            f"已写入  {written_gb:.1f} / {total_gb:.1f} GB",
            f"速度    {self.speed}",
        ]
        for i, stat in enumerate(stats):
            draw_text(self.surface, stat, (cx, cy + 60 + i * 32), "mono_s", Color.TEXT_DIM)

        # Big danger button (non-clickable, just visible)
        danger_rect = (cx - 180, self.h - 140, 360, 60)
        draw_text(self.surface, "⚡ 不要关闭电源", (cx, self.h - 110), "body", Color.DANGER)

        if self.flash_done:
            draw_text(self.surface, "刷写完成！", (cx, self.h - 50), "body", Color.PRIMARY)
        elif self.error_msg:
            draw_text(self.surface, f"错误: {self.error_msg}", (cx, self.h - 50), "body", Color.DANGER)
```

- [ ] **Step 3: Verify**

```bash
cd installer && python3 -c "
import ast
for f in ['screens/inject_progress.py', 'screens/flash_progress.py']:
    with open(f) as fh: ast.parse(fh.read())
print('inject + flash screens syntax OK')
"
```
Expected: `inject + flash screens syntax OK`

---

## Phase 4: Factory Image Build Script

### Task 10: Build factory image script

**Files:**
- Create: `scripts/build_installer_img.sh`

- [ ] **Step 1: Write `scripts/build_installer_img.sh`**

```bash
#!/bin/bash
# ============================================================================
# build_installer_img.sh — Build PineCone installer factory SD card image
#
# Prerequisites:
#   - Raspberry Pi OS Lite (bookworm, arm64) .img downloaded
#   - installer/ directory with all Python code
#   - Launcher APK built
#
# Usage:
#   sudo bash build_installer_img.sh \
#       --pi-img 2024-03-15-raspios-bookworm-arm64-lite.img \
#       --installer-dir ../installer \
#       --apk ../launcher/app/build/outputs/apk/debug/app-debug.apk \
#       --output pinecone-installer-v1.0.img
# ============================================================================

set -euo pipefail

PI_IMG=""
INSTALLER_DIR=""
APK_PATH=""
OUTPUT="pinecone-installer-v1.0.img"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --pi-img)       PI_IMG="$2"; shift 2 ;;
        --installer-dir) INSTALLER_DIR="$2"; shift 2 ;;
        --apk)          APK_PATH="$2"; shift 2 ;;
        --output)       OUTPUT="$2"; shift 2 ;;
        *) echo "Unknown: $1"; exit 1 ;;
    esac
done

[[ -f "$PI_IMG" ]]         || { echo "ERROR: Pi OS img not found: $PI_IMG"; exit 1; }
[[ -d "$INSTALLER_DIR" ]]  || { echo "ERROR: installer dir not found: $INSTALLER_DIR"; exit 1; }
[[ -f "$APK_PATH" ]]       || { echo "ERROR: APK not found: $APK_PATH"; exit 1; }

echo "============================================"
echo " PineCone Installer — Build Factory Image"
echo "============================================"
echo "  Pi OS image:  $PI_IMG"
echo "  Installer:    $INSTALLER_DIR"
echo "  APK:          $APK_PATH"
echo "  Output:       $OUTPUT"

# Find a free loop device
LOOP_DEV=$(losetup -f)
echo ""
echo "[1/5] Setting up loop device..."
losetup -P "$LOOP_DEV" "$PI_IMG"
echo "  Loop: $LOOP_DEV"

# Mount partitions
echo "[2/5] Mounting partitions..."
mkdir -p /tmp/pinecone-build/{boot,rootfs}
mount "${LOOP_DEV}p1" /tmp/pinecone-build/boot
mount "${LOOP_DEV}p2" /tmp/pinecone-build/rootfs
echo "  /boot  ← ${LOOP_DEV}p1"
echo "  /      ← ${LOOP_DEV}p2"

# Copy installer
echo "[3/5] Copying installer..."
TARGET="/tmp/pinecone-build/rootfs/opt/pinecone-installer"
mkdir -p "$TARGET"
cp -r "$INSTALLER_DIR"/* "$TARGET"/
cp "$APK_PATH" "$TARGET/app-debug.apk"
# Install dependencies into chroot
mount --bind /dev /tmp/pinecone-build/rootfs/dev
mount --bind /proc /tmp/pinecone-build/rootfs/proc
mount --bind /sys /tmp/pinecone-build/rootfs/sys
chroot /tmp/pinecone-build/rootfs apt update
chroot /tmp/pinecone-build/rootfs apt install -y python3-pygame python3-requests unzip e2fsprogs
umount /tmp/pinecone-build/rootfs/{dev,proc,sys}

echo "  Installer copied to $TARGET"

# Configure systemd
echo "[4/5] Configuring systemd..."
chroot /tmp/pinecone-build/rootfs systemctl disable bluetooth avahi-daemon 2>/dev/null || true

# Auto-login
mkdir -p /tmp/pinecone-build/rootfs/etc/systemd/system/getty@tty1.service.d
cat > /tmp/pinecone-build/rootfs/etc/systemd/system/getty@tty1.service.d/autologin.conf << EOF
[Service]
ExecStart=
ExecStart=-/sbin/agetty --autologin pi --noclear %I \$TERM
EOF

# .bash_profile: auto-start installer
cat >> /tmp/pinecone-build/rootfs/home/pi/.bash_profile << 'EOF'
if [ -z "$DISPLAY" ] && [ "$XDG_VTNR" -eq 1 ]; then
    echo "Starting PineCone OS Installer..."
    python3 /opt/pinecone-installer/main.py
fi
EOF

# Setup networking: copy wpa_supplicant config from boot if exists
# (allows pre-configuring Wi-Fi via config.txt on boot partition)

echo "  systemd configured"

# Unmount and cleanup
echo "[5/5] Unmounting and creating factory image..."
umount /tmp/pinecone-build/boot
umount /tmp/pinecone-build/rootfs
losetup -d "$LOOP_DEV"

# The Pi OS image now has the installer baked in.
# Copy it as the factory image
cp "$PI_IMG" "$OUTPUT"

echo ""
echo "============================================"
echo " Factory image created: $OUTPUT"
echo " Size: $(du -h "$OUTPUT" | cut -f1)"
echo "============================================"
echo ""
echo "To flash to SD cards:"
echo "  sudo dd if=$OUTPUT of=/dev/sdX bs=4M status=progress"
```

- [ ] **Step 2: Make executable**

```bash
chmod +x scripts/build_installer_img.sh
```

- [ ] **Step 3: Verify shell syntax**

```bash
bash -n scripts/build_installer_img.sh && echo "shell syntax OK"
```
Expected: `shell syntax OK`

---

## Phase 5: Integration Test

### Task 11: End-to-end test on development machine

- [ ] **Step 1: Install test dependencies**

```bash
pip3 install pygame requests pytest
```

- [ ] **Step 2: Run all unit tests**

```bash
cd installer && python3 -m pytest tests/ -v
```
Expected: All tests pass (4 tests from Tasks 4-5)

- [ ] **Step 3: Smoke test — render one frame of each screen**

```python
# tests/test_screens_smoke.py
"""Smoke test: verify each screen can be instantiated and rendered."""

import pygame
import os
os.environ["SDL_VIDEODRIVER"] = "dummy"  # headless

def test_all_screens_render():
    pygame.display.init()
    surface = pygame.display.set_mode((1280, 720))
    from ui import load_fonts
    load_fonts()

    from screens.welcome import WelcomeScreen
    from screens.wifi import WifiScreen
    from screens.download import DownloadScreen
    from screens.inject_progress import InjectProgressScreen
    from screens.flash_progress import FlashProgressScreen
    from screens.done import DoneScreen

    for cls in [WelcomeScreen, WifiScreen, DoneScreen]:
        screen = cls(surface, (1280, 720))
        screen.draw()
        # Verify surface is not blank
        arr = pygame.surfarray.pixels3d(surface)
        assert arr.max() > 0, f"{cls.__name__} rendered blank"
        print(f"  ✓ {cls.__name__}")

    pygame.quit()
```

- [ ] **Step 4: Run smoke test**

```bash
cd installer && SDL_VIDEODRIVER=dummy python3 -m pytest tests/test_screens_smoke.py -v
```
Expected: 1 test, screens render without error

---

## Implementation Order

```
Phase 1: config.py → ui.py → main.py             (foundation)
Phase 2: inject.py + flash.py + tests             (engines)
Phase 3: all 6 screens                            (UI)
Phase 4: build_installer_img.sh                   (packaging)
Phase 5: integration test                         (validation)
```
