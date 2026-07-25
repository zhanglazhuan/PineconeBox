"""Server configuration."""

from pathlib import Path

SERVER_DIR = Path(__file__).resolve().parent
STATIC_DIR = SERVER_DIR / "static"
MANIFEST_PATH = STATIC_DIR / "manifest.json"

HOST = "0.0.0.0"
PORT = 8080
