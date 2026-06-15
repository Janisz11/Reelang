import pytest

from reelang_ai.recommenders.scorer import normalize_scores, rank_candidates, score_reel


class TestScoreReel:
    def test_no_matches_returns_zero(self):
        reel = {"tags": "cooking,travel", "level": "B1", "source": "youtube", "is_fresh": False}
        profile = {"top_tags": ["sports"], "level": "A1"}
        assert score_reel(reel, profile) == 0.0

    def test_tag_overlap_scored_per_tag(self):
        reel = {"tags": "Food, travel, sports", "level": "B1", "source": "youtube", "is_fresh": False}
        profile = {"top_tags": ["food", "sports"], "level": "A1"}
        assert score_reel(reel, profile) == pytest.approx(1.2)

    def test_level_match_bonus(self):
        reel = {"tags": "", "level": "A1", "source": "youtube", "is_fresh": False}
        profile = {"top_tags": [], "level": "A1"}
        assert score_reel(reel, profile) == 2.0

    def test_r2_source_bonus(self):
        reel = {"tags": "", "level": "B1", "source": "r2", "is_fresh": False}
        profile = {"top_tags": [], "level": "A1"}
        assert score_reel(reel, profile) == 1.0

    def test_freshness_bonus(self):
        reel = {"tags": "", "level": "B1", "source": "youtube", "is_fresh": True}
        profile = {"top_tags": [], "level": "A1"}
        assert score_reel(reel, profile) == 0.5

    def test_all_bonuses_combine(self):
        reel = {"tags": "food,sports", "level": "A1", "source": "r2", "is_fresh": True}
        profile = {"top_tags": ["food", "sports"], "level": "A1"}
        assert score_reel(reel, profile) == pytest.approx(2 * 0.6 + 2.0 + 1.0 + 0.5)

    def test_missing_tags_field_treated_as_empty(self):
        reel = {"level": "B1", "source": "youtube", "is_fresh": False}
        profile = {"top_tags": ["food"], "level": "A1"}
        assert score_reel(reel, profile) == 0.0


class TestNormalizeScores:
    def test_empty_list_returns_empty(self):
        assert normalize_scores([]) == []

    def test_single_candidate_normalizes_to_one(self):
        candidates = [{"raw_score": 3.0}]
        result = normalize_scores(candidates)
        assert result[0]["score"] == 1.0

    def test_equal_scores_all_normalize_to_one(self):
        candidates = [{"raw_score": 2.0}, {"raw_score": 2.0}]
        result = normalize_scores(candidates)
        assert all(c["score"] == 1.0 for c in result)

    def test_min_max_normalization(self):
        candidates = [{"raw_score": 0.0}, {"raw_score": 5.0}, {"raw_score": 2.5}]
        result = normalize_scores(candidates)
        assert result[0]["score"] == 0.0
        assert result[1]["score"] == 1.0
        assert result[2]["score"] == 0.5

    def test_scores_rounded_to_three_decimals(self):
        candidates = [{"raw_score": 0.0}, {"raw_score": 3.0}, {"raw_score": 1.0}]
        result = normalize_scores(candidates)
        assert result[2]["score"] == round(1 / 3, 3)


class TestRankCandidates:
    def test_empty_list_returns_empty(self):
        assert rank_candidates([], {"top_tags": [], "level": "A1"}) == []

    def test_sorted_descending_by_score(self):
        profile = {"top_tags": ["food"], "level": "A1"}
        candidates = [
            {"id": "low", "tags": "", "level": "B2", "source": "youtube", "is_fresh": False},
            {"id": "high", "tags": "food", "level": "A1", "source": "r2", "is_fresh": True},
        ]
        ranked = rank_candidates(candidates, profile)
        assert ranked[0]["id"] == "high"
        assert ranked[-1]["id"] == "low"
        assert ranked[0]["score"] == 1.0
        assert ranked[-1]["score"] == 0.0

    def test_adds_raw_score_and_score_keys(self):
        profile = {"top_tags": [], "level": "A1"}
        candidates = [{"tags": "", "level": "A1", "source": "youtube", "is_fresh": False}]
        ranked = rank_candidates(candidates, profile)
        assert "raw_score" in ranked[0]
        assert "score" in ranked[0]
