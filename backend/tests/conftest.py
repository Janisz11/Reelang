import logging
import os
from typing import Optional

import pytest
from fastapi import Header
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, event
from sqlalchemy.orm import sessionmaker
from app.main import app
from app.database import get_db
from app.dependencies import get_current_user_id, get_optional_user_id
from app.services.db_log_handler import DatabaseLogHandler

TEST_USER_ID = "test-user"


@pytest.fixture(scope="session", autouse=True)
def detach_database_log_handler():
    """main.py installs it on import; during tests it would write to the dev database."""
    root = logging.getLogger()
    original = list(root.handlers)
    root.handlers = [h for h in original if not isinstance(h, DatabaseLogHandler)]
    yield
    root.handlers = original


def override_get_current_user_id(authorization: Optional[str] = Header(None)) -> str:
    if authorization and authorization.startswith("Bearer "):
        return authorization[len("Bearer "):]
    return TEST_USER_ID


def override_get_optional_user_id(authorization: Optional[str] = Header(None)) -> Optional[str]:
    if authorization and authorization.startswith("Bearer "):
        return authorization[len("Bearer "):]
    return None

SQLALCHEMY_TEST_DATABASE_URL = os.getenv(
    "TEST_DATABASE_URL", "postgresql://reelang:reelang@localhost:5432/reelang_test"
)

engine = create_engine(SQLALCHEMY_TEST_DATABASE_URL)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


@pytest.fixture(scope="function")
def db():
    connection = engine.connect()
    transaction = connection.begin()
    session = TestingSessionLocal(bind=connection)

    nested = connection.begin_nested()

    @event.listens_for(session, "after_transaction_end")
    def restart_savepoint(sess, trans):
        nonlocal nested
        if not nested.is_active:
            nested = connection.begin_nested()

    try:
        yield session
    finally:
        session.close()
        transaction.rollback()
        connection.close()


@pytest.fixture(scope="function")
def client(db):
    def override_get_db():
        try:
            yield db
        finally:
            pass

    app.dependency_overrides[get_db] = override_get_db
    app.dependency_overrides[get_current_user_id] = override_get_current_user_id
    app.dependency_overrides[get_optional_user_id] = override_get_optional_user_id
    with TestClient(app) as c:
        yield c
    app.dependency_overrides.clear()
