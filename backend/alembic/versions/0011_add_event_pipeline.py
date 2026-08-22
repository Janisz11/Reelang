"""add event pipeline tables

Revision ID: 0011
Revises: 0010
Create Date: 2026-08-22
"""
import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "0011"
down_revision = "0010"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "reel_events",
        sa.Column("event_id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("event_type", sa.String(length=32), nullable=False),
        sa.Column("user_id", sa.String(), nullable=False),
        sa.Column(
            "reel_id",
            sa.String(),
            sa.ForeignKey("reels.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("session_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("platform", sa.String(length=16), nullable=False),
        sa.Column("client_timestamp", sa.DateTime(timezone=True), nullable=False),
        sa.Column(
            "server_timestamp",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.Column("payload", postgresql.JSONB(astext_type=sa.Text()), nullable=True),
    )
    op.create_index("ix_reel_events_user_reel", "reel_events", ["user_id", "reel_id"])
    op.create_index("ix_reel_events_reel_type", "reel_events", ["reel_id", "event_type"])

    op.create_table(
        "reel_stats",
        sa.Column(
            "reel_id",
            sa.String(),
            sa.ForeignKey("reels.id", ondelete="CASCADE"),
            primary_key=True,
        ),
        sa.Column("impressions", sa.BigInteger(), server_default="0", nullable=False),
        sa.Column("completions", sa.BigInteger(), server_default="0", nullable=False),
        sa.Column("skips", sa.BigInteger(), server_default="0", nullable=False),
        sa.Column("likes", sa.BigInteger(), server_default="0", nullable=False),
        sa.Column("saves", sa.BigInteger(), server_default="0", nullable=False),
        sa.Column("avg_watch_percent", sa.Float(), nullable=True),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
    )

    op.create_table(
        "user_reel_stats",
        sa.Column("user_id", sa.String(), primary_key=True),
        sa.Column(
            "reel_id",
            sa.String(),
            sa.ForeignKey("reels.id", ondelete="CASCADE"),
            primary_key=True,
        ),
        sa.Column("watch_count", sa.Integer(), server_default="0", nullable=False),
        sa.Column("max_watch_percent", sa.Float(), server_default="0", nullable=False),
        sa.Column("liked", sa.Boolean(), server_default="false", nullable=False),
        sa.Column("saved", sa.Boolean(), server_default="false", nullable=False),
        sa.Column("last_interaction", sa.DateTime(timezone=True), nullable=True),
    )


def downgrade() -> None:
    op.drop_table("user_reel_stats")
    op.drop_table("reel_stats")
    op.drop_index("ix_reel_events_reel_type", table_name="reel_events")
    op.drop_index("ix_reel_events_user_reel", table_name="reel_events")
    op.drop_table("reel_events")
