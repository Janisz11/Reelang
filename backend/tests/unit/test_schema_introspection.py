import pytest

from app.services import schema_introspection
from app.services.schema_introspection import (
    SCHEMA_CACHE_TTL_SECONDS,
    clear_schema_cache,
    get_schema_snapshot,
    introspect_schema,
)


class FakeType:
    def __init__(self, rendered: str):
        self.rendered = rendered

    def __str__(self) -> str:
        return self.rendered


def col(name, type_name="VARCHAR", nullable=True):
    return {"name": name, "type": FakeType(type_name), "nullable": nullable}


class FakeInspector:
    def __init__(self, tables):
        self.tables = tables
        self.requested_schemas = []

    def get_table_names(self, schema=None):
        self.requested_schemas.append(schema)
        return list(self.tables)

    def get_pk_constraint(self, table_name, schema=None):
        return {"constrained_columns": self.tables[table_name].get("pk", [])}

    def get_columns(self, table_name, schema=None):
        return self.tables[table_name].get("columns", [])

    def get_foreign_keys(self, table_name, schema=None):
        return self.tables[table_name].get("fks", [])


@pytest.fixture(autouse=True)
def _fresh_cache():
    clear_schema_cache()
    yield
    clear_schema_cache()


@pytest.fixture
def fake_inspect(monkeypatch):
    """Replace inspect() on the reference the introspection module resolves."""
    holder = {}

    def install(tables):
        inspector = FakeInspector(tables)
        holder["inspector"] = inspector
        holder["calls"] = 0

        def _inspect(bind):
            holder["calls"] += 1
            return inspector

        monkeypatch.setattr(schema_introspection, "inspect", _inspect)
        return holder

    return install


class TestIntrospectSchema:
    def test_maps_column_attributes(self, fake_inspect):
        fake_inspect({
            "reels": {
                "pk": ["id"],
                "columns": [
                    col("id", "VARCHAR", nullable=False),
                    col("title", "TEXT", nullable=True),
                ],
            }
        })

        table = introspect_schema(object()).tables[0]

        assert table.name == "reels"
        assert table.columns[0].model_dump() == {
            "name": "id", "type": "VARCHAR", "nullable": False, "primary_key": True
        }
        assert table.columns[1].model_dump() == {
            "name": "title", "type": "TEXT", "nullable": True, "primary_key": False
        }

    def test_composite_primary_key_marks_every_member(self, fake_inspect):
        fake_inspect({
            "user_reel_stats": {
                "pk": ["user_id", "reel_id"],
                "columns": [col("user_id"), col("reel_id"), col("watch_count", "INTEGER")],
            }
        })

        table = introspect_schema(object()).tables[0]

        assert [c.primary_key for c in table.columns] == [True, True, False]

    def test_composite_foreign_key_yields_one_entry_per_column_pair(self, fake_inspect):
        fake_inspect({
            "child": {
                "pk": [],
                "columns": [col("a"), col("b")],
                "fks": [{
                    "constrained_columns": ["a", "b"],
                    "referred_table": "parent",
                    "referred_columns": ["x", "y"],
                }],
            }
        })

        fks = introspect_schema(object()).tables[0].foreign_keys

        assert [(f.column, f.references_table, f.references_column) for f in fks] == [
            ("a", "parent", "x"),
            ("b", "parent", "y"),
        ]

    def test_table_without_foreign_keys_reports_empty_list(self, fake_inspect):
        fake_inspect({"profiles": {"pk": ["user_id"], "columns": [col("user_id")]}})

        assert introspect_schema(object()).tables[0].foreign_keys == []

    def test_tables_are_sorted_by_name(self, fake_inspect):
        fake_inspect({
            "words": {"pk": [], "columns": []},
            "activity_logs": {"pk": [], "columns": []},
            "reels": {"pk": [], "columns": []},
        })

        names = [t.name for t in introspect_schema(object()).tables]

        assert names == ["activity_logs", "reels", "words"]

    def test_only_the_public_schema_is_introspected(self, fake_inspect):
        holder = fake_inspect({"reels": {"pk": [], "columns": []}})

        introspect_schema(object())

        assert holder["inspector"].requested_schemas == ["public"]

    def test_empty_database_yields_no_tables(self, fake_inspect):
        fake_inspect({})

        assert introspect_schema(object()).tables == []


class TestAlembicFiltering:
    def test_alembic_version_is_excluded(self, fake_inspect):
        fake_inspect({
            "alembic_version": {"pk": ["version_num"], "columns": [col("version_num")]},
            "reels": {"pk": ["id"], "columns": [col("id")]},
        })

        names = [t.name for t in introspect_schema(object()).tables]

        assert "alembic_version" not in names
        assert names == ["reels"]

    def test_any_alembic_prefixed_table_is_excluded(self, fake_inspect):
        fake_inspect({
            "alembic_version": {"pk": [], "columns": [col("version_num")]},
            "alembic_version_other_schema": {"pk": [], "columns": [col("version_num")]},
            "reels": {"pk": ["id"], "columns": [col("id")]},
        })

        names = [t.name for t in introspect_schema(object()).tables]

        assert names == ["reels"]

    def test_domain_table_with_similar_name_is_kept(self, fake_inspect):
        fake_inspect({
            "alembicreels": {"pk": ["id"], "columns": [col("id")]},
            "alembic": {"pk": ["id"], "columns": [col("id")]},
            "alembic_version": {"pk": [], "columns": [col("version_num")]},
        })

        names = [t.name for t in introspect_schema(object()).tables]

        assert names == ["alembic", "alembicreels"]

    def test_database_with_only_alembic_tables_yields_nothing(self, fake_inspect):
        fake_inspect({"alembic_version": {"pk": [], "columns": [col("version_num")]}})

        assert introspect_schema(object()).tables == []


class TestCache:
    def test_second_call_within_ttl_does_not_reintrospect(self, fake_inspect):
        holder = fake_inspect({"reels": {"pk": ["id"], "columns": [col("id")]}})

        first = get_schema_snapshot(object())
        second = get_schema_snapshot(object())

        assert holder["calls"] == 1
        assert first is second

    def test_cache_expires_after_ttl(self, fake_inspect, monkeypatch):
        holder = fake_inspect({"reels": {"pk": ["id"], "columns": [col("id")]}})
        clock = {"now": 1000.0}
        monkeypatch.setattr(
            schema_introspection.time, "monotonic", lambda: clock["now"]
        )

        get_schema_snapshot(object())
        clock["now"] += SCHEMA_CACHE_TTL_SECONDS + 1
        get_schema_snapshot(object())

        assert holder["calls"] == 2

    def test_cache_still_valid_just_before_ttl(self, fake_inspect, monkeypatch):
        holder = fake_inspect({"reels": {"pk": ["id"], "columns": [col("id")]}})
        clock = {"now": 1000.0}
        monkeypatch.setattr(
            schema_introspection.time, "monotonic", lambda: clock["now"]
        )

        get_schema_snapshot(object())
        clock["now"] += SCHEMA_CACHE_TTL_SECONDS - 1
        get_schema_snapshot(object())

        assert holder["calls"] == 1

    def test_clear_cache_forces_reintrospection(self, fake_inspect):
        holder = fake_inspect({"reels": {"pk": ["id"], "columns": [col("id")]}})

        get_schema_snapshot(object())
        clear_schema_cache()
        get_schema_snapshot(object())

        assert holder["calls"] == 2

    def test_schema_drift_is_picked_up_after_expiry(self, fake_inspect, monkeypatch):
        holder = fake_inspect({"reels": {"pk": ["id"], "columns": [col("id")]}})
        clock = {"now": 1000.0}
        monkeypatch.setattr(
            schema_introspection.time, "monotonic", lambda: clock["now"]
        )

        assert [t.name for t in get_schema_snapshot(object()).tables] == ["reels"]

        holder["inspector"].tables["drifted_table"] = {"pk": [], "columns": []}
        clock["now"] += SCHEMA_CACHE_TTL_SECONDS + 1

        assert [t.name for t in get_schema_snapshot(object()).tables] == [
            "drifted_table", "reels"
        ]
