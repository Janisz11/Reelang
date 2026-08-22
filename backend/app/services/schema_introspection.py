from __future__ import annotations

import time
from typing import Optional, Tuple

from sqlalchemy import inspect

from ..schemas import (
    SchemaColumnResponse,
    SchemaForeignKeyResponse,
    SchemaResponse,
    SchemaTableResponse,
)

PUBLIC_SCHEMA = "public"
SCHEMA_CACHE_TTL_SECONDS = 60
ALEMBIC_TABLE_PREFIX = "alembic_"

_cache: Optional[Tuple[float, SchemaResponse]] = None


def clear_schema_cache() -> None:
    global _cache
    _cache = None


def introspect_schema(bind) -> SchemaResponse:
    """Read the live structure of the public schema. Metadata only, never row data."""
    inspector = inspect(bind)
    tables = []

    for table_name in sorted(inspector.get_table_names(schema=PUBLIC_SCHEMA)):
        if table_name.startswith(ALEMBIC_TABLE_PREFIX):
            continue

        pk_constraint = inspector.get_pk_constraint(table_name, schema=PUBLIC_SCHEMA)
        primary_keys = set(pk_constraint.get("constrained_columns") or [])

        columns = [
            SchemaColumnResponse(
                name=column["name"],
                type=str(column["type"]),
                nullable=bool(column["nullable"]),
                primary_key=column["name"] in primary_keys,
            )
            for column in inspector.get_columns(table_name, schema=PUBLIC_SCHEMA)
        ]

        foreign_keys = [
            SchemaForeignKeyResponse(
                column=source,
                references_table=fk["referred_table"],
                references_column=target,
            )
            for fk in inspector.get_foreign_keys(table_name, schema=PUBLIC_SCHEMA)
            for source, target in zip(fk["constrained_columns"], fk["referred_columns"])
        ]

        tables.append(
            SchemaTableResponse(
                name=table_name, columns=columns, foreign_keys=foreign_keys
            )
        )

    return SchemaResponse(tables=tables)


def get_schema_snapshot(bind) -> SchemaResponse:
    global _cache

    now = time.monotonic()
    if _cache is not None and now - _cache[0] < SCHEMA_CACHE_TTL_SECONDS:
        return _cache[1]

    snapshot = introspect_schema(bind)
    _cache = (now, snapshot)
    return snapshot
