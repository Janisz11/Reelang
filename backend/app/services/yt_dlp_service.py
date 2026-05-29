"""
YouTube metadata + caption extraction via yt-dlp.
"""
from __future__ import annotations

import json
import logging
import re
import os
import tempfile
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

import yt_dlp

logger = logging.getLogger(__name__)

VIDEO_CACHE_DIR = Path("/tmp/reels")

_PREFERRED_LANGS = ["es", "en", "fr", "de", "it", "pt"]

_BASE_OPTS: Dict[str, Any] = {
    "http_headers": {
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/120.0.0.0 Safari/537.36"
        )
    },
    "sleep_interval": 2,
    "max_sleep_interval": 5,
    "cookiefile": "/tmp/cookies.txt",
}


# ── Public API ───────────────────────────────────────────────────────────────

def fetch_video_info(youtube_url: str) -> Dict[str, Any]:
    
    ydl_opts = {
        **_BASE_OPTS,
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
    }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(youtube_url, download=False)
    return info


def extract_metadata(info: Dict[str, Any]) -> Dict[str, Any]:
    
    duration_s = info.get("duration") or 0
    return {
        "youtube_id": info["id"],
        "title": info.get("title", ""),
        "channel_name": info.get("uploader") or info.get("channel") or "",
        "thumbnail_url": _best_thumbnail(info),
        "duration_ms": int(duration_s * 1000),
    }


def download_video_with_captions(youtube_id: str, youtube_url: str) -> List[Dict[str, Any]]:
    
    VIDEO_CACHE_DIR.mkdir(parents=True, exist_ok=True)
    video_path = VIDEO_CACHE_DIR / f"{youtube_id}.mp4"


    ydl_opts_info = {
        **_BASE_OPTS,
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "writeautomaticsub": True,
        "subtitleslangs": ["all"],
    }
    with yt_dlp.YoutubeDL(ydl_opts_info) as ydl:
        info = ydl.extract_info(youtube_url, download=False)

    available_subs = list((info.get("subtitles") or {}).keys())
    available_auto = list((info.get("automatic_captions") or {}).keys())
    logger.info("Manual subtitles: %s", available_subs)
    logger.info("Auto captions: %s", available_auto)

    
    chosen_lang = _pick_language(info)
    logger.info("Chosen caption language: %s", chosen_lang)

    
    try:
        _download_video_file(youtube_url, video_path)
    except Exception as e:
        logger.warning("Video download failed (will use WebView): %s", e)

    
    if not chosen_lang:
        logger.warning("No caption language found for %s", youtube_id)
        return []

    return _fetch_captions_for_lang(youtube_url, youtube_id, chosen_lang)


def ensure_video_downloaded(youtube_id: str, youtube_url: str) -> Path:
  
    VIDEO_CACHE_DIR.mkdir(parents=True, exist_ok=True)
    video_path = VIDEO_CACHE_DIR / f"{youtube_id}.mp4"

    if not video_path.exists():
        logger.info("Video not cached, downloading %s", youtube_id)
        _download_video_file(youtube_url, video_path)

    return video_path


def fetch_captions(youtube_url: str, language: str) -> List[Dict[str, Any]]:
  
    with tempfile.TemporaryDirectory() as tmpdir:
        subtitle_path, fmt = _download_subtitles(youtube_url, language, tmpdir)
        if subtitle_path is None:
            return []
        raw = Path(subtitle_path).read_text(encoding="utf-8")
        if fmt == "vtt":
            return _parse_vtt(raw)
        return _parse_srt(raw)


# ── Internal helpers ──────────────────────────────────────────────────────────

def _best_thumbnail(info: Dict[str, Any]) -> Optional[str]:
    thumbs = info.get("thumbnails") or []
    if thumbs:
        return thumbs[-1].get("url")
    return info.get("thumbnail")


def _pick_language(info: Dict[str, Any]) -> Optional[str]:
    
    auto = info.get("automatic_captions") or {}
    manual = info.get("subtitles") or {}

    
    for lang in _PREFERRED_LANGS:
        if lang in auto:
            return lang

    
    if auto:
        return next(iter(auto))

    
    for lang in _PREFERRED_LANGS:
        if lang in manual:
            return lang

    if manual:
        return next(iter(manual))

    return None


def _fetch_captions_for_lang(
    youtube_url: str, youtube_id: str, lang: str
) -> List[Dict[str, Any]]:
   
    with tempfile.TemporaryDirectory() as tmpdir:
        ydl_opts = {
            **_BASE_OPTS,
            "quiet": True,
            "no_warnings": True,
            "skip_download": True,
            "writeautomaticsub": True,
            "writesubtitles": True,
            "subtitleslangs": [lang],
            "subtitlesformat": "json3/vtt/srt",
            "outtmpl": os.path.join(tmpdir, "%(id)s.%(ext)s"),
        }
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            ydl.extract_info(youtube_url, download=True)

        parsers: List[Tuple[str, Any]] = [
            ("json3", _parse_json3),
            ("vtt", _parse_vtt),
            ("srt", _parse_srt),
        ]

        for ext, parser in parsers:
           
            candidate = os.path.join(tmpdir, f"{youtube_id}.{lang}.{ext}")
            if os.path.exists(candidate):
                segments = parser(Path(candidate).read_text(encoding="utf-8"))
                if segments:
                    logger.info("Parsed %d segments from %s.%s.%s", len(segments), youtube_id, lang, ext)
                    return segments

            # yt-dlp sometimes uses a variant filename — scan the dir
            for fname in os.listdir(tmpdir):
                if fname.endswith(f".{ext}") and youtube_id in fname:
                    segments = parser(
                        Path(os.path.join(tmpdir, fname)).read_text(encoding="utf-8")
                    )
                    if segments:
                        logger.info("Parsed %d segments from %s", len(segments), fname)
                        return segments

    return []


def _download_video_file(youtube_url: str, video_path: Path) -> None:
   
    _FORMATS = [
        {
            "format": "bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best",
            "merge_output_format": "mp4",
        },
        {
            "format": "bestvideo+bestaudio/best/bestvideo/best",
            "merge_output_format": "mp4",
        },
    ]

    last_exc: Exception = RuntimeError("No format tried")
    for fmt_opts in _FORMATS:
        try:
            ydl_opts = {
                **_BASE_OPTS,
                "quiet": True,
                "no_warnings": True,
                "outtmpl": str(video_path),
                **fmt_opts,
            }
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                ydl.extract_info(youtube_url, download=True)
            logger.info("Downloaded video with format: %s", fmt_opts["format"])
            return
        except Exception as exc:
            logger.warning("Format %r failed (%s), trying next", fmt_opts["format"], exc)
            last_exc = exc
            
            if video_path.exists():
                video_path.unlink()

    raise last_exc


def _download_subtitles(
    youtube_url: str, language: str, tmpdir: str
) -> Tuple[Optional[str], str]:
   
    for auto in (False, True):
        for fmt in ("vtt", "srt"):
            opts = {
                **_BASE_OPTS,
                "quiet": True,
                "no_warnings": True,
                "skip_download": True,
                "writesubtitles": not auto,
                "writeautomaticsub": auto,
                "subtitleslangs": [language],
                "subtitlesformat": fmt,
                "outtmpl": os.path.join(tmpdir, "%(id)s.%(ext)s"),
            }
            with yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(youtube_url, download=True)

            vid_id = info["id"]
            candidate = os.path.join(tmpdir, f"{vid_id}.{language}.{fmt}")
            if os.path.exists(candidate):
                return candidate, fmt

            for fname in os.listdir(tmpdir):
                if fname.endswith(f".{fmt}") and vid_id in fname:
                    return os.path.join(tmpdir, fname), fmt

    return None, ""


# ── JSON3 parser ──────────────────────────────────────────────────────────────

def _parse_json3(raw: str) -> List[Dict[str, Any]]:
    try:
        data = json.loads(raw)
    except (json.JSONDecodeError, ValueError):
        return []

    segments: List[Dict[str, Any]] = []
    for event in data.get("events", []):
        segs = event.get("segs")
        if not segs:  
            continue
        start_ms = event.get("tStartMs", 0)
        end_ms = start_ms + event.get("dDurationMs", 3000)
        text = "".join(s.get("utf8", "") for s in segs).strip()
        if not text or text == "\n":
            continue
        
        if not segments or segments[-1]["original_text"] != text:
            segments.append({"start_ms": start_ms, "end_ms": end_ms, "original_text": text})
    return segments


# ── VTT parser ────────────────────────────────────────────────────────────────

_VTT_TIMESTAMP = re.compile(
    r"(\d{2}):(\d{2}):(\d{2})\.(\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})\.(\d{3})"
)
_VTT_TAG = re.compile(r"<[^>]+>")


def _vtt_to_ms(h: str, m: str, s: str, ms: str) -> int:
    return (int(h) * 3600 + int(m) * 60 + int(s)) * 1000 + int(ms)


def _parse_vtt(raw: str) -> List[Dict[str, Any]]:
    segments: List[Dict[str, Any]] = []
    lines = raw.splitlines()
    i = 0
    while i < len(lines):
        m = _VTT_TIMESTAMP.match(lines[i])
        if m:
            start_ms = _vtt_to_ms(m.group(1), m.group(2), m.group(3), m.group(4))
            end_ms = _vtt_to_ms(m.group(5), m.group(6), m.group(7), m.group(8))
            i += 1
            text_lines = []
            while i < len(lines) and lines[i].strip():
                clean = _VTT_TAG.sub("", lines[i]).strip()
                if clean:
                    text_lines.append(clean)
                i += 1
            text = " ".join(text_lines)
            if text:
                if not segments or segments[-1]["original_text"] != text:
                    segments.append(
                        {"start_ms": start_ms, "end_ms": end_ms, "original_text": text}
                    )
        else:
            i += 1
    return segments


# ── SRT parser ────────────────────────────────────────────────────────────────

_SRT_TIMESTAMP = re.compile(
    r"(\d{2}):(\d{2}):(\d{2}),(\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2}),(\d{3})"
)
_SRT_TAG = re.compile(r"<[^>]+>")


def _srt_to_ms(h: str, m: str, s: str, ms: str) -> int:
    return (int(h) * 3600 + int(m) * 60 + int(s)) * 1000 + int(ms)


def _parse_srt(raw: str) -> List[Dict[str, Any]]:
    segments: List[Dict[str, Any]] = []
    blocks = re.split(r"\n{2,}", raw.strip())
    for block in blocks:
        lines = block.strip().splitlines()
        start_idx = 0
        if lines and lines[0].strip().isdigit():
            start_idx = 1
        if start_idx >= len(lines):
            continue
        m = _SRT_TIMESTAMP.match(lines[start_idx])
        if not m:
            continue
        start_ms = _srt_to_ms(m.group(1), m.group(2), m.group(3), m.group(4))
        end_ms = _srt_to_ms(m.group(5), m.group(6), m.group(7), m.group(8))
        text = " ".join(
            _SRT_TAG.sub("", l).strip()
            for l in lines[start_idx + 1:]
            if l.strip()
        )
        if text:
            segments.append(
                {"start_ms": start_ms, "end_ms": end_ms, "original_text": text}
            )
    return segments
