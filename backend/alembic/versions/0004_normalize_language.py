"""normalize language to lowercase

Revision ID: 0004
Revises: 0003
Create Date: 2026-05-14
"""
from alembic import op

revision = "0004"
down_revision = "0003"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("UPDATE reels SET language = LOWER(language)")


def downgrade() -> None:
    pass
