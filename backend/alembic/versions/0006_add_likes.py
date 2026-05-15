"""add reel_likes table and likes_count on reels

Revision ID: 0006
Revises: 0005
Create Date: 2026-05-15
"""
import sqlalchemy as sa
from alembic import op

revision = "0006"
down_revision = "0005"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "reel_likes",
        sa.Column("user_id", sa.String(), nullable=False),
        sa.Column("reel_id", sa.String(), nullable=False),
        sa.Column("created_at", sa.DateTime(), nullable=False),
    )
    op.create_primary_key("pk_reel_likes", "reel_likes", ["user_id", "reel_id"])
    op.create_index("ix_reel_likes_reel_id", "reel_likes", ["reel_id"])
    op.add_column("reels", sa.Column("likes_count", sa.Integer(), server_default="0", nullable=False))


def downgrade() -> None:
    op.drop_column("reels", "likes_count")
    op.drop_index("ix_reel_likes_reel_id", table_name="reel_likes")
    op.drop_table("reel_likes")
