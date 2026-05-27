import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker
from app.main import app
from app.database import get_db, Base

SQLALCHEMY_TEST_DATABASE_URL = "sqlite:///./test.db"

engine = create_engine(
    SQLALCHEMY_TEST_DATABASE_URL,
    connect_args={"check_same_thread": False},
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


@pytest.fixture(scope="function")
def db():
    Base.metadata.create_all(bind=engine)
    with engine.connect() as conn:
        conn.execute(text(
            "CREATE TABLE IF NOT EXISTS user_feed_queue ("
            "id TEXT, "
            "user_id TEXT NOT NULL, "
            "reel_id TEXT NOT NULL, "
            "consumed INTEGER NOT NULL DEFAULT 0, "
            "score REAL NOT NULL DEFAULT 0.0, "
            "added_at TEXT, "
            "PRIMARY KEY (user_id, reel_id)"
            ")"
        ))
        conn.commit()
    session = TestingSessionLocal()
    try:
        yield session
    finally:
        session.close()
        Base.metadata.drop_all(bind=engine)
        with engine.connect() as conn:
            conn.execute(text("DROP TABLE IF EXISTS user_feed_queue"))
            conn.commit()


@pytest.fixture(scope="function")
def client(db):
    def override_get_db():
        try:
            yield db
        finally:
            pass

    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as c:
        yield c
    app.dependency_overrides.clear()


@pytest.fixture(autouse=True)
def mock_fetch_definition(monkeypatch):
    monkeypatch.setattr("app.routers.words.fetch_definition", lambda term, lang: None)
