"""PineCone OS Update Server — FastAPI.

Run: uvicorn main:app --host 0.0.0.0 --port 8080 --reload
"""

import logging
from pathlib import Path

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles

from config import STATIC_DIR
from routers.manifest import router as manifest_router

ROM_DIR = STATIC_DIR / "rom"

app = FastAPI(
    title="PineCone OS Update Server",
    version="1.0.0",
    docs_url="/docs",
    redoc_url=None,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET"],
    allow_headers=["*"],
)

app.include_router(manifest_router)
app.mount("/apk", StaticFiles(directory=str(STATIC_DIR / "apk")), name="apk")


@app.get("/rom/{filename:path}")
async def serve_rom(filename: str):
    """Serve ROM files — FileResponse avoids StaticFiles + large-file edge cases."""
    file_path = ROM_DIR / filename
    if not file_path.exists():
        raise HTTPException(status_code=404)
    return FileResponse(
        file_path,
        media_type="application/octet-stream",
        filename=filename,
    )


@app.get("/")
async def root():
    return RedirectResponse(url="/docs")


@app.get("/health")
async def health():
    return {"status": "ok"}
