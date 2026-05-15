"""add user_feed_queue table

Revision ID: 0007
Revises: 0006
Create Date: 2026-05-15
"""
import sqlalchemy as sa
from alembic import op

revision = "0007"
down_revision = "0006"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "user_feed_queue",
        sa.Column("id", sa.String(), primary_key=True),
        sa.Column("user_id", sa.String(), nullable=False),
        sa.Column(
            "reel_id",
            sa.String(),
            sa.ForeignKey("reels.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("score", sa.Float(), server_default="1.0", nullable=False),
        sa.Column(
            "consumed", sa.Boolean(), server_default="false", nullable=False
        ),
        sa.Column("added_at", sa.DateTime(), nullable=False),
    )
    op.create_index(
        "ix_feed_queue_user_consumed",
        "user_feed_queue",
        ["user_id", "consumed"],
    )


def downgrade() -> None:
    op.drop_index("ix_feed_queue_user_consumed", table_name="user_feed_queue")
    op.drop_table("user_feed_queue")
