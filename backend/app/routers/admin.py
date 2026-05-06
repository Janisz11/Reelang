from __future__ import annotations

from fastapi import APIRouter, UploadFile

router = APIRouter(prefix="/admin", tags=["admin"])

_COOKIES_PATH = "/tmp/cookies.txt"


@router.post("/cookies")
async def upload_cookies(file: UploadFile):
    contents = await file.read()
    with open(_COOKIES_PATH, "wb") as f:
        f.write(contents)
    return {"status": "ok"}
