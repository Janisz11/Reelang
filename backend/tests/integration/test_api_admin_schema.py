import pytest

from app.services.schema_introspection import clear_schema_cache

ADMIN_HEADERS = {"X-Admin-Token": "schema-secret"}


@pytest.fixture(autouse=True)
def _fresh_cache():
    clear_schema_cache()
    yield
    clear_schema_cache()


@pytest.fixture
def admin_token(monkeypatch):
    monkeypatch.setenv("ADMIN_TOKEN", "schema-secret")


def fetch_schema(client) -> dict:
    response = client.get("/api/v1/admin/schema", headers=ADMIN_HEADERS)
    assert response.status_code == 200
    return {table["name"]: table for table in response.json()["tables"]}


def column(table: dict, name: str) -> dict:
    return next(c for c in table["columns"] if c["name"] == name)


class TestAuth:
    def test_missing_token_returns_403(self, client, admin_token):
        assert client.get("/api/v1/admin/schema").status_code == 403

    def test_wrong_token_returns_403(self, client, admin_token):
        response = client.get(
            "/api/v1/admin/schema", headers={"X-Admin-Token": "not-the-secret"}
        )

        assert response.status_code == 403

    def test_correct_token_is_accepted(self, client, admin_token):
        assert client.get("/api/v1/admin/schema", headers=ADMIN_HEADERS).status_code == 200

    def test_unset_admin_token_still_rejects(self, client, monkeypatch):
        monkeypatch.delenv("ADMIN_TOKEN", raising=False)

        response = client.get("/api/v1/admin/schema", headers=ADMIN_HEADERS)

        assert response.status_code == 403

    def test_firebase_user_token_does_not_grant_access(self, client, admin_token):
        response = client.get(
            "/api/v1/admin/schema", headers={"Authorization": "Bearer some-user"}
        )

        assert response.status_code == 403


class TestEventPipelineTables:
    def test_all_three_tables_are_reported(self, client, admin_token):
        tables = fetch_schema(client)

        assert {"reel_events", "reel_stats", "user_reel_stats"} <= set(tables)

    def test_reel_events_foreign_key_targets_reels(self, client, admin_token):
        tables = fetch_schema(client)

        assert tables["reel_events"]["foreign_keys"] == [
            {"column": "reel_id", "references_table": "reels", "references_column": "id"}
        ]

    def test_reel_stats_foreign_key_targets_reels(self, client, admin_token):
        tables = fetch_schema(client)

        assert tables["reel_stats"]["foreign_keys"] == [
            {"column": "reel_id", "references_table": "reels", "references_column": "id"}
        ]

    def test_user_reel_stats_foreign_key_targets_reels(self, client, admin_token):
        tables = fetch_schema(client)

        assert tables["user_reel_stats"]["foreign_keys"] == [
            {"column": "reel_id", "references_table": "reels", "references_column": "id"}
        ]

    def test_user_id_has_no_foreign_key(self, client, admin_token):
        tables = fetch_schema(client)

        for name in ("reel_events", "user_reel_stats"):
            assert not any(
                fk["column"] == "user_id" for fk in tables[name]["foreign_keys"]
            )

    def test_reel_events_primary_key_is_event_id(self, client, admin_token):
        table = fetch_schema(client)["reel_events"]

        assert column(table, "event_id")["primary_key"] is True
        assert column(table, "reel_id")["primary_key"] is False

    def test_user_reel_stats_has_composite_primary_key(self, client, admin_token):
        table = fetch_schema(client)["user_reel_stats"]

        assert column(table, "user_id")["primary_key"] is True
        assert column(table, "reel_id")["primary_key"] is True
        assert column(table, "watch_count")["primary_key"] is False

    def test_nullability_is_reported(self, client, admin_token):
        table = fetch_schema(client)["reel_events"]

        assert column(table, "payload")["nullable"] is True
        assert column(table, "event_type")["nullable"] is False

    def test_types_are_serialized_as_strings(self, client, admin_token):
        table = fetch_schema(client)["reel_events"]

        assert column(table, "event_id")["type"] == "UUID"
        assert column(table, "payload")["type"] == "JSONB"
        assert isinstance(column(table, "client_timestamp")["type"], str)


class TestResponseShape:
    def test_pre_existing_tables_are_included(self, client, admin_token):
        tables = fetch_schema(client)

        assert {"reels", "words", "profiles"} <= set(tables)

    def test_alembic_version_is_not_exposed(self, client, admin_token):
        assert "alembic_version" not in fetch_schema(client)

    def test_no_alembic_table_is_exposed(self, client, admin_token):
        names = list(fetch_schema(client))

        assert [name for name in names if name.startswith("alembic_")] == []

    def test_tables_are_sorted_by_name(self, client, admin_token):
        names = list(fetch_schema(client))

        assert names == sorted(names)

    def test_response_exposes_structure_only(self, client, admin_token):
        response = client.get("/api/v1/admin/schema", headers=ADMIN_HEADERS)
        body = response.json()

        assert set(body) == {"tables"}
        for table in body["tables"]:
            assert set(table) == {"name", "columns", "foreign_keys"}
            for col in table["columns"]:
                assert set(col) == {"name", "type", "nullable", "primary_key"}
            for fk in table["foreign_keys"]:
                assert set(fk) == {"column", "references_table", "references_column"}
