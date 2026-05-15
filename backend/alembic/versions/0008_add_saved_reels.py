"""add saved_reels table

Revision ID: 0008
Revises: 0007
Create Date: 2026-05-15
"""
import sqlalchemy as sa
from alembic import op

revision = "0008"
down_revision = "0007"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "saved_reels",
        sa.Column("user_id", sa.String(), nullable=False),
        sa.Column(
            "reel_id",
            sa.String(),
            sa.ForeignKey("reels.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("created_at", sa.DateTime(), nullable=False),
    )
    op.create_primary_key("pk_saved_reels", "saved_reels", ["user_id", "reel_id"])
    op.create_index("ix_saved_reels_user_id", "saved_reels", ["user_id"])


def downgrade() -> None:
    op.drop_index("ix_saved_reels_user_id", table_name="saved_reels")
    op.drop_table("saved_reels")
