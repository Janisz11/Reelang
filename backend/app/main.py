import logging
import os
from contextlib import asynccontextmanager

from dotenv import load_dotenv
load_dotenv()

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from slowapi import _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded

from .database import Base
from .rate_limit import limiter
from .routers import admin, reels, words, search, profiles, activity, feed, events
from .services.db_log_handler import install_database_log_handler
from .services.event_publisher import get_publisher, shutdown_publisher

logger = logging.getLogger(__name__)

install_database_log_handler()


@asynccontextmanager
async def lifespan(app: FastAPI):
    try:
        await get_publisher().connect()
    except Exception as e:
        logger.warning(f"Event publisher could not connect at startup: {e}")
    yield
    await shutdown_publisher()


app = FastAPI(
    title="ReeLang API",
    description="Learn languages through YouTube reels.",
    version="0.1.0",
    lifespan=lifespan,
)

app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

cors_origins = [o.strip() for o in os.getenv("CORS_ALLOWED_ORIGINS", "").split(",") if o.strip()]

app.add_middleware(
    CORSMiddleware,
    allow_origins=cors_origins,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(admin.router, prefix="/api/v1")
app.include_router(reels.router, prefix="/api/v1")
app.include_router(words.router, prefix="/api/v1")
app.include_router(search.router, prefix="/api/v1")
app.include_router(profiles.router, prefix="/api/v1")
app.include_router(activity.router, prefix="/api/v1")
app.include_router(feed.router, prefix="/api/v1")
app.include_router(events.router, prefix="/api/v1")


@app.get("/health", tags=["health"])
def health():
    return {"status": "ok"}
