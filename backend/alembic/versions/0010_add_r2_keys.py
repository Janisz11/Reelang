"""add R2 storage keys to reels

Revision ID: 0010
Revises: 0009
Create Date: 2026-05-27
"""
import sqlalchemy as sa
from alembic import op

revision = "0010"
down_revision = "0009"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("reels", sa.Column("r2_key", sa.String(), nullable=True))
    op.add_column("reels", sa.Column("r2_thumb_key", sa.String(), nullable=True))


def downgrade() -> None:
    op.drop_column("reels", "r2_thumb_key")
    op.drop_column("reels", "r2_key")
