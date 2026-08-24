from __future__ import annotations

import json
import logging
import threading
import traceback
from typing import Any, Dict, Optional

from ..database import SessionLocal
from ..models import AppLog

MAX_LOGGER_NAME_LENGTH = 128
MAX_LEVEL_LENGTH = 16

_STANDARD_RECORD_ATTRS = frozenset(
    {
        "args", "asctime", "created", "exc_info", "exc_text", "filename", "funcName",
        "levelname", "levelno", "lineno", "message", "module", "msecs", "msg", "name",
        "pathname", "process", "processName", "relativeCreated", "stack_info",
        "taskName", "thread", "threadName",
    }
)

_reentrancy = threading.local()


def _extract_context(record: logging.LogRecord) -> Optional[Dict[str, Any]]:
    context: Dict[str, Any] = {}

    for key, value in record.__dict__.items():
        if key not in _STANDARD_RECORD_ATTRS and not key.startswith("_"):
            context[key] = value

    if record.exc_info:
        context["traceback"] = "".join(traceback.format_exception(*record.exc_info)).strip()

    if record.stack_info:
        context["stack_info"] = record.stack_info

    if not context:
        return None

    return json.loads(json.dumps(context, default=str))


class DatabaseLogHandler(logging.Handler):
    """Persists WARNING+ records to app_logs. Swallows every failure: logging must never break callers."""

    def __init__(self, level: int = logging.WARNING):
        super().__init__(level)

    def emit(self, record: logging.LogRecord) -> None:
        if getattr(_reentrancy, "active", False):
            return

        _reentrancy.active = True
        try:
            entry = AppLog(
                level=record.levelname[:MAX_LEVEL_LENGTH],
                logger_name=record.name[:MAX_LOGGER_NAME_LENGTH],
                message=record.getMessage(),
                context=_extract_context(record),
            )

            session = SessionLocal()
            try:
                session.add(entry)
                session.commit()
            finally:
                session.close()
        except Exception:
            pass
        finally:
            _reentrancy.active = False


def install_database_log_handler() -> DatabaseLogHandler:
    """Attach one DatabaseLogHandler to the root logger. Repeat calls are a no-op."""
    root = logging.getLogger()

    for handler in root.handlers:
        if isinstance(handler, DatabaseLogHandler):
            return handler

    handler = DatabaseLogHandler()
    root.addHandler(handler)
    return handler
