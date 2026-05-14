import os
import shutil
import subprocess
from pathlib import Path
from tempfile import mkdtemp

import edge_tts
from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel, Field
from starlette.background import BackgroundTask


app = FastAPI(title="Reading System Lightweight TTS")

MAX_CHARS = int(os.getenv("TTS_CHUNK_MAX_CHARS", "1000"))
MIN_BREAK_CHARS = int(os.getenv("TTS_CHUNK_MIN_BREAK_CHARS", "450"))
PUNCTUATION = "。！？；，、.!?;,"


class SynthesizeRequest(BaseModel):
    text: str = Field(..., min_length=1)
    voice: str = Field(default="zh-CN-XiaoxiaoNeural")


@app.get("/health")
async def health():
    return JSONResponse({"status": "ok"})


@app.post("/synthesize")
async def synthesize(request: SynthesizeRequest):
    text = request.text.strip()
    if not text:
        raise HTTPException(status_code=400, detail="text must not be empty")

    chunks = split_text(text)
    temp_dir = Path(mkdtemp(prefix="reading_tts_"))
    output_path = temp_dir / "tts.mp3"

    try:
        if len(chunks) == 1:
            await synthesize_chunk(chunks[0], request.voice, output_path)
        else:
            ensure_ffmpeg()
            segment_paths = []
            for index, chunk in enumerate(chunks):
                segment_path = temp_dir / f"segment_{index:04d}.mp3"
                try:
                    await synthesize_chunk(chunk, request.voice, segment_path)
                except Exception as exc:
                    raise HTTPException(
                        status_code=502,
                        detail=f"tts chunk {index + 1}/{len(chunks)} failed: {exc}",
                    ) from exc
                segment_paths.append(segment_path)
            merge_mp3_segments(segment_paths, output_path, temp_dir)

        if not output_path.exists() or output_path.stat().st_size == 0:
            raise HTTPException(status_code=502, detail="tts generation returned empty audio")

        return FileResponse(
            str(output_path),
            media_type="audio/mpeg",
            headers={"Content-Disposition": "inline; filename=tts.mp3"},
            background=BackgroundTask(lambda path: shutil.rmtree(path, ignore_errors=True), str(temp_dir)),
        )
    except HTTPException:
        shutil.rmtree(temp_dir, ignore_errors=True)
        raise
    except Exception as exc:
        shutil.rmtree(temp_dir, ignore_errors=True)
        raise HTTPException(status_code=502, detail=f"tts generation failed: {exc}") from exc


async def synthesize_chunk(text: str, voice: str, output_path: Path):
    communicate = edge_tts.Communicate(text=text, voice=voice)
    await communicate.save(str(output_path))
    if not output_path.exists() or output_path.stat().st_size == 0:
        raise RuntimeError("edge-tts returned empty audio")


def split_text(text: str) -> list[str]:
    max_chars = max(200, MAX_CHARS)
    min_break_chars = max(80, MIN_BREAK_CHARS)
    if len(text) <= max_chars:
        return [text]

    chunks = []
    current = []
    for char in text:
        current.append(char)
        should_break_on_punctuation = char in PUNCTUATION and len(current) >= min_break_chars
        should_hard_break = len(current) >= max_chars
        if should_break_on_punctuation or should_hard_break:
            add_chunk(chunks, current)

    add_chunk(chunks, current)
    return chunks


def add_chunk(chunks: list[str], current: list[str]):
    chunk = "".join(current).strip()
    if chunk:
        chunks.append(chunk)
    current.clear()


def ensure_ffmpeg():
    if not shutil.which("ffmpeg"):
        raise HTTPException(
            status_code=502,
            detail="ffmpeg is required for long TTS audio merge but was not found",
        )


def merge_mp3_segments(segment_paths: list[Path], output_path: Path, temp_dir: Path):
    concat_file = temp_dir / "concat.txt"
    with concat_file.open("w", encoding="utf-8") as file:
        for segment_path in segment_paths:
            escaped_path = segment_path.resolve().as_posix().replace("'", "\\'")
            file.write(f"file '{escaped_path}'\n")

    command = [
        "ffmpeg",
        "-hide_banner",
        "-loglevel",
        "error",
        "-y",
        "-f",
        "concat",
        "-safe",
        "0",
        "-i",
        str(concat_file),
        "-c",
        "copy",
        str(output_path),
    ]
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode != 0:
        detail = result.stderr.strip() or result.stdout.strip() or "unknown ffmpeg error"
        raise HTTPException(status_code=502, detail=f"ffmpeg merge failed: {detail}")
