"""add profiles, follows, activity_logs; owner_user_id on reels

Revision ID: 0005
Revises: 0004
Create Date: 2026-05-15
"""
import sqlalchemy as sa
from alembic import op

revision = "0005"
down_revision = "0004"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "profiles",
        sa.Column("user_id", sa.String(), primary_key=True),
        sa.Column("username", sa.String(), nullable=False),
        sa.Column("bio", sa.String(), nullable=True),
        sa.Column("avatar_initials", sa.String(), nullable=True),
        sa.Column("followers_count", sa.Integer(), default=0, nullable=False, server_default="0"),
        sa.Column("following_count", sa.Integer(), default=0, nullable=False, server_default="0"),
        sa.Column("total_likes", sa.Integer(), default=0, nullable=False, server_default="0"),
        sa.Column("level", sa.Integer(), default=1, nullable=False, server_default="1"),
        sa.Column("streak_days", sa.Integer(), default=0, nullable=False, server_default="0"),
        sa.Column("created_at", sa.DateTime(), nullable=False),
    )

    op.create_table(
        "follows",
        sa.Column("follower_id", sa.String(), nullable=False),
        sa.Column("following_id", sa.String(), nullable=False),
        sa.Column("created_at", sa.DateTime(), nullable=False),
    )
    op.create_primary_key("pk_follows", "follows", ["follower_id", "following_id"])

    op.create_table(
        "activity_logs",
        sa.Column("id", sa.String(), primary_key=True),
        sa.Column("user_id", sa.String(), nullable=False, index=True),
        sa.Column("date", sa.Date(), nullable=False),
        sa.Column("watch_time_ms", sa.BigInteger(), default=0, server_default="0"),
        sa.Column("reels_watched", sa.Integer(), default=0, server_default="0"),
        sa.Column("words_saved", sa.Integer(), default=0, server_default="0"),
    )
    op.create_index("ix_activity_logs_user_date", "activity_logs", ["user_id", "date"])

    op.add_column("reels", sa.Column("owner_user_id", sa.String(), nullable=True))


def downgrade() -> None:
    op.drop_column("reels", "owner_user_id")
    op.drop_index("ix_activity_logs_user_date", table_name="activity_logs")
    op.drop_table("activity_logs")
    op.drop_table("follows")
    op.drop_table("profiles")
