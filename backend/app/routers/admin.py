from fastapi import APIRouter, Depends, HTTPException, Request, UploadFile

from ..dependencies import verify_admin_token
from ..rate_limit import limiter

router = APIRouter(prefix="/admin", tags=["admin"])

_COOKIES_PATH = "/tmp/cookies.txt"
_MAX_COOKIES_SIZE = 1_000_000


@router.post("/cookies")
@limiter.limit("3/minute")
async def upload_cookies(
    request: Request,
    file: UploadFile,
    _: None = Depends(verify_admin_token),
):
    contents = await file.read()
    if len(contents) > _MAX_COOKIES_SIZE:
        raise HTTPException(status_code=413, detail="Cookies file too large")
    with open(_COOKIES_PATH, "wb") as f:
        f.write(contents)
    return {"status": "ok"}
