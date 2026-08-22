import os

from sqlalchemy import create_engine, event
from sqlalchemy.orm import sessionmaker
import pytest

TEST_DATABASE_URL = os.getenv(
    "TEST_DATABASE_URL", "postgresql://reelang:reelang@localhost:5432/reelang_test"
)

engine = create_engine(TEST_DATABASE_URL)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


@pytest.fixture
def session_factory():
    """Real committing sessions, for code that manages its own transactions."""
    sessions = []

    def factory():
        session = TestingSessionLocal()
        sessions.append(session)
        return session

    try:
        yield factory
    finally:
        for session in sessions:
            session.close()


@pytest.fixture
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
