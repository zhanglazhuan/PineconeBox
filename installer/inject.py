"""PineCone Installer — APK injection into SD card system partition.

After the SD card has been flashed with the base OS image, this module
mounts the system partition by byte offset and injects the APK.
"""

import os
import shutil
import subprocess

LAUNCHERS_TO_REMOVE = [
    "Trebuchet", "Launcher3", "Launcher3QuickStep",
    "LeanbackLauncher", "TvLauncher", "CustomTvLauncher",
    "LegacyLauncher", "Catapult"
]


def mount_system(device, offset, mount_point):
    """Mount ext4 partition via explicit losetup + mount."""
    os.makedirs(mount_point, exist_ok=True)

    # Let kernel settle after dd
    import time
    time.sleep(1)

    loop_dev = None
    stderr = ""
    for attempt in range(3):
        try:
            loop = subprocess.run(
                ["sudo", "losetup", "--show", "-f", "-o", str(offset), device],
                check=True, capture_output=True, text=True, timeout=30)
            loop_dev = loop.stdout.strip()
            break
        except subprocess.CalledProcessError as e:
            stderr = e.stderr.strip() if e.stderr else str(e)
            time.sleep(2)

    if not loop_dev:
        raise RuntimeError(f"losetup 失败 (3次): {stderr}")

    try:
        subprocess.run(
            ["sudo", "mount", "-t", "ext4", loop_dev, mount_point],
            check=True, capture_output=True, text=True, timeout=30)
    except subprocess.CalledProcessError as e:
        stderr = e.stderr.strip() if e.stderr else str(e)
        # Clean up loop device on mount failure
        subprocess.run(["sudo", "losetup", "-d", loop_dev], check=False)
        raise RuntimeError(f"mount 失败: {stderr}")

    return loop_dev


def unmount_system(mount_point, loop_dev=None):
    """Unmount the system partition and detach loop device."""
    subprocess.run(["sudo", "umount", mount_point], check=True)
    if loop_dev:
        subprocess.run(["sudo", "losetup", "-d", loop_dev], check=False)


def inject_apk(mount_point, apk_bytes):
    """Copy APK from memory into /system/app/PineConeLauncher/."""
    target_dir = os.path.join(mount_point, "system/app/PineConeLauncher")
    os.makedirs(target_dir, exist_ok=True)
    target_apk = os.path.join(target_dir, "PineConeLauncher.apk")
    with open(target_apk, 'wb') as f:
        f.write(apk_bytes)
    os.chmod(target_dir, 0o755)
    os.chmod(target_apk, 0o644)


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
                    shutil.rmtree(target, ignore_errors=True)
                    break


class InjectProgress:
    """Callback interface for progress reporting."""
    def __init__(self):
        self.stage = 0
        self.stage_text = [
            "挂载 system 分区",
            "注入 PineCone 桌面",
            "写入完成",
        ]
        self.done = [False, False, False]

    def step(self, idx):
        self.done[idx] = True
        self.stage = idx + 1

    def status(self):
        return self.stage_text, self.done


def inject(apk_bytes, device, offset, work_dir, progress=None):
    """Injection pipeline: offset-mount system → write APK → remove competitors.

    Args:
        apk_bytes: APK file content in memory.
        device:    Block device, e.g. /dev/mmcblk0.
        offset:    Byte offset of the ext4 system partition within device.
        work_dir:  Working directory for mount point.
        progress:  InjectProgress callback (optional).

    Returns True on success.
    """
    if progress is None:
        progress = InjectProgress()

    mnt = os.path.join(work_dir, "mnt")
    loop_dev = mount_system(device, offset, mnt)
    progress.step(0)

    inject_apk(mnt, apk_bytes)
    remove_launchers(mnt)
    progress.step(1)

    unmount_system(mnt, loop_dev)
    progress.step(2)

    return True
