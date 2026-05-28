import logging
from typing import Dict, List

logger = logging.getLogger(__name__)

SCORE_TAG_OVERLAP_PER_TAG = 0.6
SCORE_LEVEL_MATCH_BONUS = 2.0
SCORE_R2_SOURCE_BONUS = 1.0
SCORE_FRESHNESS_BONUS = 0.5
SCORE_DECIMAL_PLACES = 3


def score_reel(reel: Dict, profile: Dict) -> float:
    score = 0.0

    reel_tags = set(t.strip().lower() for t in (reel.get("tags") or "").split(",") if t.strip())
    profile_tags = set(t.lower() for t in profile.get("top_tags", []))
    overlap = len(reel_tags & profile_tags)
    score += overlap * SCORE_TAG_OVERLAP_PER_TAG

    if reel.get("level") == profile.get("level"):
        score += SCORE_LEVEL_MATCH_BONUS

    if reel.get("source") == "r2":
        score += SCORE_R2_SOURCE_BONUS

    if reel.get("is_fresh"):
        score += SCORE_FRESHNESS_BONUS

    return score


def normalize_scores(candidates: List[Dict]) -> List[Dict]:
    if not candidates:
        return candidates

    scores = [c["raw_score"] for c in candidates]
    min_score = min(scores)
    max_score = max(scores)
    score_range = max_score - min_score

    if score_range == 0:
        for c in candidates:
            c["score"] = 1.0
        return candidates

    for c in candidates:
        c["score"] = round((c["raw_score"] - min_score) / score_range, SCORE_DECIMAL_PLACES)

    return candidates


def rank_candidates(candidates: List[Dict], profile: Dict) -> List[Dict]:
    if not candidates:
        return []

    for candidate in candidates:
        candidate["raw_score"] = score_reel(candidate, profile)

    candidates = normalize_scores(candidates)
    candidates.sort(key=lambda r: r["score"], reverse=True)

    logger.info(
        f"Ranked {len(candidates)} candidates. "
        f"Top score: {candidates[0]['score']} "
        f"Bottom score: {candidates[-1]['score']}"
    )

    return candidates
