from pathlib import Path
from tempfile import NamedTemporaryFile

import edge_tts
from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel, Field
from starlette.background import BackgroundTask


app = FastAPI(title="Reading System Lightweight TTS")


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

    temp_path = None
    try:
        communicate = edge_tts.Communicate(text=text, voice=request.voice)
        with NamedTemporaryFile(delete=False, suffix=".mp3") as temp_file:
            temp_path = Path(temp_file.name)

        await communicate.save(str(temp_path))
        if not temp_path.exists() or temp_path.stat().st_size == 0:
            raise HTTPException(status_code=502, detail="edge-tts returned empty audio")

        return FileResponse(
            str(temp_path),
            media_type="audio/mpeg",
            headers={"Content-Disposition": "inline; filename=tts.mp3"},
            background=BackgroundTask(lambda path: Path(path).unlink(missing_ok=True), str(temp_path)),
        )
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"tts generation failed: {exc}") from exc
