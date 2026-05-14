"""add tags to reels

Revision ID: 0003
Revises: 0002
Create Date: 2026-05-14
"""
from alembic import op
import sqlalchemy as sa

revision = "0003"
down_revision = "0002"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("reels", sa.Column("tags", sa.String(), nullable=True))


def downgrade() -> None:
    op.drop_column("reels", "tags")
