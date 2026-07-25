"""PineCone Installer — configuration and constants."""

import os


class DownloadConfig:
    BASE_URL_LOCAL = "http://192.168.1.4:8080"
    BASE_URL_OSS = "https://pinecone-os.oss-cn-hangzhou.aliyuncs.com"
    ROM_FILE = "rom/baseos.img.gz"
    APK_FILE = "apk/app-debug.apk"

    @classmethod
    def _base_url(cls):
        return cls.BASE_URL_LOCAL if cls.get_mode() == "dev" else cls.BASE_URL_OSS

    @classmethod
    def rom_url(cls):
        return f"{cls._base_url()}/{cls.ROM_FILE}"

    @classmethod
    def apk_url(cls):
        return f"{cls._base_url()}/{cls.APK_FILE}"

    @classmethod
    def get_mode(cls):
        return "prod" if os.environ.get("PINECONE_PROD") == "1" else "dev"


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


# Installer root — auto-detected from this file's location.
# Works whether scp'd to /home/zhang/installer/ or built into /opt/pinecone-installer/
_INSTALLER_DIR = os.path.dirname(os.path.abspath(__file__))
INSTALLER_DIR  = _INSTALLER_DIR
ASSETS_DIR     = os.path.join(_INSTALLER_DIR, "assets")

# Target block device
SD_DEVICE = "/dev/mmcblk0"

# System partition byte offset within the disk image / SD card.
# LBA 266240 × 512 bytes = 136,314,880
SD_SYSTEM_OFFSET = 136314880
