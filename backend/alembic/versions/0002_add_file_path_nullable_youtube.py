"""add file_path, make youtube_id and channel_name nullable

Revision ID: 0002
Revises: 0001
Create Date: 2026-05-13
"""
from alembic import op
import sqlalchemy as sa

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("reels", sa.Column("file_path", sa.String(), nullable=True))

    # youtube_id: drop unique constraint, alter to nullable, re-add unique
    with op.batch_alter_table("reels") as batch_op:
        batch_op.alter_column("youtube_id", existing_type=sa.String(), nullable=True)
        batch_op.alter_column("channel_name", existing_type=sa.String(), nullable=True)
        batch_op.alter_column("duration_ms", existing_type=sa.Integer(), nullable=True)


def downgrade() -> None:
    with op.batch_alter_table("reels") as batch_op:
        batch_op.alter_column("duration_ms", existing_type=sa.Integer(), nullable=False)
        batch_op.alter_column("channel_name", existing_type=sa.String(), nullable=False)
        batch_op.alter_column("youtube_id", existing_type=sa.String(), nullable=False)

    op.drop_column("reels", "file_path")
