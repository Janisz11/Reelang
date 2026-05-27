from __future__ import annotations

import re
from typing import Optional

import httpx

_FREE_DICT_BASE = "https://api.dictionaryapi.dev/api/v2/entries/en"

_WIKTIONARY_LANG_MAP = {
    "es": "es",
    "fr": "fr",
    "de": "de",
    "it": "it",
    "ja": "ja",
    "pt": "pt",
}


def fetch_definition(term: str, language: str = "en") -> Optional[str]:
    clean_term = re.sub(r'^[¿¡\W]+', '', term.strip())
    
    if language.lower() == "en":
        result = _fetch_free_dict(clean_term)
        if result:
            return result
    
    
    result = fetch_definition_wiktionary(clean_term, language)
    if result:
        return result
    
   
    if language.lower() != "en":
        try:
            from deep_translator import GoogleTranslator
            translation = GoogleTranslator(source=language.lower(), target="en").translate(clean_term)
            if translation and translation.lower() != clean_term.lower():
                return f"Translation: {translation}"
        except Exception:
            pass
    
    return None

def _fetch_free_dict(term: str) -> Optional[str]:
    url = f"{_FREE_DICT_BASE}/{term.lower().strip()}"
    try:
        with httpx.Client(timeout=5.0) as client:
            resp = client.get(url)
        if resp.status_code != 200:
            return None
        data = resp.json()
        for entry in data:
            for meaning in entry.get("meanings", []):
                for definition in meaning.get("definitions", []):
                    defn = definition.get("definition")
                    if defn:
                        return defn
    except Exception:
        return None
    return None


def fetch_definition_wiktionary(term: str, language: str) -> Optional[str]:
    """Fetch definition from Wiktionary API for any language."""
    wiki_lang = _WIKTIONARY_LANG_MAP.get(language.lower(), "en")
    url = f"https://{wiki_lang}.wiktionary.org/api/rest_v1/page/definition/{term.lower().strip()}"
    try:
        with httpx.Client(timeout=5.0) as client:
            resp = client.get(url)
        if resp.status_code != 200:
            return None
        data = resp.json()
        for lang_key, entries in data.items():
            if isinstance(entries, list):
                for entry in entries:
                    for defn in entry.get("definitions", []):
                        text = defn.get("definition", "")
                        if text:
                            clean = re.sub(r'<[^>]+>', '', text).strip()
                            if clean:
                                return clean
    except Exception:
        return None
    return None
