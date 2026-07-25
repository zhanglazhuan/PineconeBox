"""Phase 1: Download assets + trigger recovery mode.

Downloads baseos.img.gz and APK to local disk, then writes a flag file
to /boot/firmware and reboots into the recovery initramfs (Phase 2).
"""

import os
import subprocess
import threading
import time

from ui import Screen, draw_text, draw_button, draw_gradient_bar, Color
from config import DownloadConfig, INSTALLER_DIR

_GZ  = os.path.join(INSTALLER_DIR, "baseos.img.gz")
_APK = os.path.join(INSTALLER_DIR, "app-debug.apk")
_FLAG = "/boot/firmware/pinecone-recovery"


class StreamFlashScreen(Screen):
    def __init__(self, surface, size):
        super().__init__(surface, size)
        self.progress = 0.0
        self.phase = "apk"
        self.stats_line1 = ""
        self.stats_line2 = ""
        self.status = "ready"
        self.error_msg = ""
        self.done = False
        self._done_timer = 0.0

    def update(self, dt):
        if self.done:
            self._done_timer += dt
        return None

    def on_enter(self):
        super().on_enter()
        self._start()

    def _start(self):
        self.status = "working"
        t = threading.Thread(target=self._run, daemon=True)
        t.start()

    def _run(self):
        try:
            # ── Phase 1: Download APK (tiny, 14 MB) ──
            self.phase = "apk"
            if not self._download_apk():
                return

            # ── Phase 2: Download system image (~800 MB) ──
            self.phase = "rom"
            if not self._download_rom():
                return

            # ── Phase 3: Write recovery flag ──
            self.phase = "flag"
            self.stats_line1 = "正在准备重启进入刷机模式..."
            self.stats_line2 = ""
            self.progress = 0.98

            self._write_recovery_flag()

            self.progress = 1.0
            self.done = True
            self.status = "done"

        except Exception as e:
            self.status = "error"
            self.error_msg = f"{type(e).__name__}: {e}"

    # ── download APK ─────────────────────────────────

    def _download_apk(self):
        url = DownloadConfig.apk_url()
        self.stats_line1 = "正在下载 PineCone 桌面..."
        self.stats_line2 = ""

        ok = self._dl_file(url, _APK, 0.05)
        if not ok:
            self.error_msg = "APK 下载失败"
        return ok

    # ── download ROM ─────────────────────────────────

    def _download_rom(self):
        url = DownloadConfig.rom_url()
        self.stats_line1 = "正在下载系统镜像..."

        ok = self._dl_file(url, _GZ, 0.90)
        if not ok:
            self.error_msg = "系统镜像下载失败"
        return ok

    # ── shared download helper ───────────────────────

    def _dl_file(self, url, dest, progress_max):
        """Download a file with progress polling. Returns True on success."""
        if os.path.exists(dest):
            os.remove(dest)

        total = 0
        try:
            h = subprocess.run(["curl", "-sI", url], capture_output=True,
                               text=True, timeout=5)
            for line in h.stdout.splitlines():
                if line.lower().startswith("content-length:"):
                    total = int(line.split(":")[1].strip())
        except Exception:
            pass
        if total == 0:
            total = 800 * 1024 * 1024  # fallback estimate

        proc = subprocess.Popen(
            ["curl", "-sS", "--connect-timeout", "10", "-o", dest, url],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

        t0 = time.time()
        while proc.poll() is None:
            time.sleep(0.2)
            if os.path.exists(dest):
                sz = os.path.getsize(dest)
                self.progress = min(sz / total, 1.0) * progress_max
                el = max(time.time() - t0, 0.1)
                self.stats_line2 = (
                    f"速度  {_fmt_speed(sz / el)}    "
                    f"{sz/(1024**2):.0f} / {total/(1024**2):.0f} MB")

        if proc.returncode != 0:
            self.status = "error"
            return False
        return True

    # ── trigger recovery ─────────────────────────────

    def _write_recovery_flag(self):
        """Write the flag file that tells the initramfs to enter recovery."""
        os.makedirs(os.path.dirname(_FLAG), exist_ok=True)
        try:
            subprocess.run(["sudo", "mount", "-o", "remount,rw",
                            "/boot/firmware"], check=False)
        except Exception:
            pass
        with open(_FLAG, "w") as f:
            f.write("1")
        subprocess.run(["sudo", "sync"], check=False)

    # ── UI ───────────────────────────────────────────

    def handle_event(self, event):
        if event.type == pygame.KEYDOWN:
            if self.done and event.key == pygame.K_RETURN:
                self._reboot()
            if self.status == "error" and event.key == pygame.K_RETURN:
                self._start()
        return None

    @staticmethod
    def _reboot():
        subprocess.run(["sudo", "sync"], check=False)
        try:
            with open("/proc/sysrq-trigger", "w") as f:
                f.write("s")
                f.write("b")
        except Exception:
            subprocess.run(["sudo", "reboot", "-f"], check=False)

    def draw(self):
        super().draw()
        cx, cy = self.w // 2, self.h // 2

        titles = {
            "apk":  "⬇  正在下载 PineCone 桌面",
            "rom":  "⬇  正在下载系统镜像",
            "flag": "🔧 准备重启进入刷机模式",
        }
        draw_text(self.surface, titles.get(self.phase, ""), (cx, 80),
                  "h2", Color.WARNING)
        draw_text(self.surface, "请勿关闭电源！", (cx, 120), "body", Color.DANGER)

        bar_rect = (cx - 350, cy - 40, 700, 32)
        draw_gradient_bar(self.surface, bar_rect, self.progress,
                          Color.WARNING, (255, 180, 100))
        draw_text(self.surface, f"{int(self.progress * 100)}%",
                  (cx, cy + 20), "mono", Color.WARNING)

        if self.stats_line1:
            draw_text(self.surface, self.stats_line1,
                      (cx, cy + 60), "mono_s", Color.TEXT_DIM)
        if self.stats_line2:
            draw_text(self.surface, self.stats_line2,
                      (cx, cy + 92), "mono_s", Color.TEXT_DIM)

        if self.done:
            draw_text(self.surface, "✅ 下载完成，即将重启进入刷机模式",
                      (cx, self.h - 160), "body", Color.PRIMARY)
            draw_button(self.surface, "立即重启刷机",
                        (cx - 120, self.h - 110, 240, 50), "primary")
            draw_text(self.surface, "按 Enter 键重启",
                      (cx, self.h - 60), "small", Color.TEXT_DIM)
        elif self.status != "error":
            draw_text(self.surface, "⚡ 正在下载，请稍候...",
                      (cx, self.h - 120), "small", Color.DANGER)

        if self.error_msg:
            for i, line in enumerate(self.error_msg.split('\n')):
                draw_text(self.surface, f"错误: {line}",
                          (cx, self.h - 60 + i * 24), "body", Color.DANGER)


def _fmt_speed(bps):
    if bps > 1_000_000:
        return f"{bps/1_000_000:.1f} MB/s"
    elif bps > 1_000:
        return f"{bps/1_000:.0f} KB/s"
    return f"{bps:.0f} B/s"
