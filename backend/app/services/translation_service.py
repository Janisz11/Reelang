"""
GoogleTranslator.

"""
from __future__ import annotations

from typing import List, Optional


def translate_text(text: str, source_lang: str, target_lang: str = "en") -> Optional[str]:
    
    if source_lang == target_lang:
        return text
    try:
        from deep_translator import GoogleTranslator

        translator = GoogleTranslator(source=source_lang, target=target_lang)
        return translator.translate(text)
    except Exception:
        return None


def translate_segments(
    segments: List[dict],
    source_lang: str,
    target_lang: str = "en",
) -> List[dict]:
  
   
    texts = [s["original_text"] for s in segments]
    try:
        from deep_translator import GoogleTranslator

        
        chunk_size = 50
        translated: List[Optional[str]] = []
        for i in range(0, len(texts), chunk_size):
            chunk = texts[i : i + chunk_size]
            translator = GoogleTranslator(source=source_lang, target=target_lang)
            results = translator.translate_batch(chunk)
            translated.extend(results)

        for seg, t in zip(segments, translated):
            seg["translated_text"] = t
    except Exception:
        for seg in segments:
            seg.setdefault("translated_text", None)

    return segments
