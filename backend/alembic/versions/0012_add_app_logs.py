"""add app_logs table

Revision ID: 0012
Revises: 0011
Create Date: 2026-08-24
"""
import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "0012"
down_revision = "0011"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "app_logs",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True),
        sa.Column("level", sa.String(length=16), nullable=False),
        sa.Column("logger_name", sa.String(length=128), nullable=False),
        sa.Column("message", sa.Text(), nullable=False),
        sa.Column("context", postgresql.JSONB(astext_type=sa.Text()), nullable=True),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
    )
    op.create_index(
        "idx_app_logs_created_at",
        "app_logs",
        [sa.text("created_at DESC")],
    )
    op.create_index("idx_app_logs_level", "app_logs", ["level"])


def downgrade() -> None:
    op.drop_index("idx_app_logs_level", table_name="app_logs")
    op.drop_index("idx_app_logs_created_at", table_name="app_logs")
    op.drop_table("app_logs")
