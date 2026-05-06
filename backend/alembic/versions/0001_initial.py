"""initial schema

Revision ID: 0001
Revises:
Create Date: 2026-04-05
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects.postgresql import ENUM as PgEnum

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "reels",
        sa.Column("id", sa.String(), primary_key=True),
        sa.Column("youtube_id", sa.String(), nullable=False, unique=True),
        sa.Column("title", sa.String(), nullable=False),
        sa.Column("channel_name", sa.String(), nullable=False),
        sa.Column("thumbnail_url", sa.String(), nullable=True),
        sa.Column("duration_ms", sa.Integer(), nullable=False),
        sa.Column("language", sa.String(), nullable=False),
        sa.Column("level", sa.String(), nullable=True),
        sa.Column("topic", sa.String(), nullable=True),
        sa.Column("created_at", sa.DateTime(), nullable=False),
    )
    op.create_index("ix_reels_language", "reels", ["language"])
    op.create_index("ix_reels_level", "reels", ["level"])
    op.create_index("ix_reels_topic", "reels", ["topic"])
    op.create_index("ix_reels_youtube_id", "reels", ["youtube_id"])

    op.create_table(
        "caption_segments",
        sa.Column("id", sa.String(), primary_key=True),
        sa.Column("reel_id", sa.String(), sa.ForeignKey("reels.id", ondelete="CASCADE"), nullable=False),
        sa.Column("start_ms", sa.Integer(), nullable=False),
        sa.Column("end_ms", sa.Integer(), nullable=False),
        sa.Column("original_text", sa.Text(), nullable=False),
        sa.Column("translated_text", sa.Text(), nullable=True),
    )
    op.create_index("ix_caption_segments_reel_id", "caption_segments", ["reel_id"])

    op.execute("""
    DO $$ BEGIN
        CREATE TYPE word_status AS ENUM ('learning', 'mastered');
    EXCEPTION
        WHEN duplicate_object THEN null;
    END $$;
    """)
    op.create_table(
        "words",
        sa.Column("id", sa.String(), primary_key=True),
        sa.Column("user_id", sa.String(), nullable=False),
        sa.Column("term", sa.String(), nullable=False),
        sa.Column("definition", sa.Text(), nullable=True),
        sa.Column("language", sa.String(), nullable=False),
        sa.Column("status", PgEnum("learning", "mastered", name="word_status", create_type=False), nullable=False, server_default="learning"),
        sa.Column("reel_id", sa.String(), sa.ForeignKey("reels.id", ondelete="SET NULL"), nullable=True),
        sa.Column("segment_id", sa.String(), sa.ForeignKey("caption_segments.id", ondelete="SET NULL"), nullable=True),
        sa.Column("created_at", sa.DateTime(), nullable=False),
    )
    op.create_index("ix_words_user_id", "words", ["user_id"])


def downgrade() -> None:
    op.drop_table("words")
    op.execute("DROP TYPE word_status")
    op.drop_table("caption_segments")
    op.drop_table("reels")
