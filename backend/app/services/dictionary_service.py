"""
Word definitions via Free Dictionary API (https://dictionaryapi.dev).
Only supports English definitions.
"""
from __future__ import annotations

from typing import Optional

import httpx

_FREE_DICT_BASE = "https://api.dictionaryapi.dev/api/v2/entries/en"


def fetch_definition(term: str) -> Optional[str]:
    """
    Return the first available definition for *term* from the Free Dictionary API.
    Returns None if the word is not found or the request fails.
    """
    url = f"{_FREE_DICT_BASE}/{term.lower().strip()}"
    try:
        with httpx.Client(timeout=5.0) as client:
            resp = client.get(url)
        if resp.status_code != 200:
            return None
        data = resp.json()
        # data is a list of entry objects
        for entry in data:
            for meaning in entry.get("meanings", []):
                for definition in meaning.get("definitions", []):
                    defn = definition.get("definition")
                    if defn:
                        return defn
    except Exception:
        return None
    return None
