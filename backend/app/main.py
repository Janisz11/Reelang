from dotenv import load_dotenv
load_dotenv()

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .database import Base
from .routers import admin, reels, words, search

app = FastAPI(
    title="ReeLang API",
    description="Learn languages through YouTube reels.",
    version="0.1.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(admin.router, prefix="/api/v1")
app.include_router(reels.router, prefix="/api/v1")
app.include_router(words.router, prefix="/api/v1")
app.include_router(search.router, prefix="/api/v1")


@app.get("/health", tags=["health"])
def health():
    return {"status": "ok"}
