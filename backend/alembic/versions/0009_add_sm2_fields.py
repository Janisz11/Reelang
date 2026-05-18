"""add SM-2 spaced repetition fields to words

Revision ID: 0009
Revises: 0008
Create Date: 2026-05-18
"""
import sqlalchemy as sa
from alembic import op

revision = "0009"
down_revision = "0008"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("words", sa.Column("repetitions", sa.Integer(), server_default="0", nullable=False))
    op.add_column("words", sa.Column("easiness", sa.Float(), server_default="2.5", nullable=False))
    op.add_column("words", sa.Column("interval_days", sa.Integer(), server_default="1", nullable=False))
    op.add_column("words", sa.Column("next_review", sa.DateTime(), nullable=True))


def downgrade() -> None:
    op.drop_column("words", "next_review")
    op.drop_column("words", "interval_days")
    op.drop_column("words", "easiness")
    op.drop_column("words", "repetitions")
