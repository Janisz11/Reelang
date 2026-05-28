from __future__ import annotations

import logging
import os
import shutil
import subprocess
import uuid
from typing import Dict, List

import yt_dlp
from groq import Groq

logger = logging.getLogger(__name__)


async def transcribe_audio(youtube_id: str, language: str) -> List[Dict]:
    audio_dir = "/tmp/audio"
    os.makedirs(audio_dir, exist_ok=True)
    audio_path = f"{audio_dir}/{youtube_id}.mp3"

    
    ydl_opts = {
        "format": "bestaudio/best",
        "outtmpl": f"{audio_dir}/{youtube_id}.%(ext)s",
        "postprocessors": [
            {
                "key": "FFmpegExtractAudio",
                "preferredcodec": "mp3",
                "preferredquality": "64",
            }
        ],
        "cookiefile": "/tmp/cookies.txt",
    }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        ydl.download([f"https://www.youtube.com/watch?v={youtube_id}"])

    
    client = Groq(api_key=os.getenv("GROQ_API_KEY"))
    with open(audio_path, "rb") as audio_file:
        transcription = client.audio.transcriptions.create(
            file=audio_file,
            model="whisper-large-v3",
            language=language.lower(),
            response_format="verbose_json",
            timestamp_granularities=["segment"],
        )

    
    logger.info(f"Transcription type: {type(transcription)}")
    logger.info(f"Segments type: {type(transcription.segments)}")
    if transcription.segments:
        logger.info(f"First segment type: {type(transcription.segments[0])}")
        logger.info(f"First segment: {transcription.segments[0]}")

    segments = []
    for seg in transcription.segments:
        if isinstance(seg, dict):
            segments.append({
                "start_ms": int(seg.get("start", 0) * 1000),
                "end_ms": int(seg.get("end", 0) * 1000),
                "original_text": seg.get("text", "").strip(),
            })
        else:
            segments.append({
                "start_ms": int(seg.start * 1000),
                "end_ms": int(seg.end * 1000),
                "original_text": seg.text.strip(),
            })

    # Clean up audio file
    try:
        os.remove(audio_path)
    except OSError:
        pass

    return segments


async def transcribe_file(file_path: str, language: str) -> List[Dict]:
    audio_dir = "/tmp/audio"
    os.makedirs(audio_dir, exist_ok=True)

    ffmpeg_available = shutil.which("ffmpeg") is not None
    cleanup_path: str | None = None

    if ffmpeg_available:
        audio_path = f"{audio_dir}/{uuid.uuid4()}.mp3"
        subprocess.run(
            ["ffmpeg", "-i", file_path, "-q:a", "6", "-map", "a", audio_path, "-y"],
            check=True,
            capture_output=True,
        )
        send_path = audio_path
        cleanup_path = audio_path
    else:
        send_path = file_path

    client = Groq(api_key=os.getenv("GROQ_API_KEY"))
    try:
        with open(send_path, "rb") as f:
            transcription = client.audio.transcriptions.create(
                file=f,
                model="whisper-large-v3",
                language=language.lower(),
                response_format="verbose_json",
                timestamp_granularities=["segment"],
            )
    finally:
        if cleanup_path:
            try:
                os.remove(cleanup_path)
            except OSError:
                pass

    segments = []
    for seg in transcription.segments:
        if isinstance(seg, dict):
            segments.append({
                "start_ms": int(seg.get("start", 0) * 1000),
                "end_ms": int(seg.get("end", 0) * 1000),
                "original_text": seg.get("text", "").strip(),
            })
        else:
            segments.append({
                "start_ms": int(seg.start * 1000),
                "end_ms": int(seg.end * 1000),
                "original_text": seg.text.strip(),
            })

    return segments
