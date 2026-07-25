"""Manifest API — version check endpoint."""

import json
from pathlib import Path
from fastapi import APIRouter, HTTPException
from config import MANIFEST_PATH

router = APIRouter(prefix="/api", tags=["manifest"])


@router.get("/manifest.json")
async def get_manifest():
    """Return the current launcher version manifest."""
    if not MANIFEST_PATH.exists():
        raise HTTPException(status_code=404, detail="Manifest not found")
    return json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
