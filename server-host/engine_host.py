"""LocalText2Voice Android companion — engine host.

Запускает HTTP-сервер с теми же эндпоинтами, что и
`engine_host.py` в оригинале, плюс упрощённый `/synthesize`
для прямого вызова из Android-клиента (без очереди задач).

Использование:
    pip install -r requirements.txt
    python engine_host.py --port 8765 --allow-lan
"""
from __future__ import annotations

import argparse
import logging
from typing import Any

import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel

from app.core.settings_manager import SettingsManager
from app.server.http_app import create_http_app

log = logging.getLogger("ltv.engine_host")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s :: %(message)s")


class SynthesizeBody(BaseModel):
    engine_id: str
    text: str
    voice: str = ""
    lang: str = ""
    speed: float = 1.0
    options: dict[str, Any] = {}


def create_android_app(settings: SettingsManager | None = None) -> FastAPI:
    """Создаёт FastAPI приложение с дополнительным /synthesize-эндпоинтом
    для прямого вызова из Android (без /jobs очереди).
    """
    base = create_http_app(settings)

    @base.post("/synthesize")
    def synthesize(body: SynthesizeBody) -> FileResponse:
        from pathlib import Path
        from app.server.ltv_service import LocalText2VoiceService
        from app.tts.registry import TTS_ENGINES
        from app.tts.voice_manager import VoiceInfo, VoiceManager
        from app.core.audio_pipeline import AudioGenerationOptions

        settings = settings or SettingsManager()
        service = LocalText2VoiceService(settings, keep_engines_alive=True)

        engine = TTS_ENGINES.get(body.engine_id)
        if engine is None:
            raise HTTPException(404, f"Engine not found: {body.engine_id}")

        out = Path(settings.settings.get("output_dir", "output")) / f"android_{int(__import__('time').time()*1000)}.wav"
        out.parent.mkdir(parents=True, exist_ok=True)

        config = {
            "voice": body.voice,
            "lang": body.lang,
            "speed": body.speed,
            **body.options,
        }
        try:
            engine.synthesize_to_wav(body.text, out, config)
        except Exception as exc:  # noqa: BLE001
            log.exception("synthesize failed")
            raise HTTPException(500, f"synthesize failed: {exc}") from exc
        return FileResponse(out, media_type="audio/wav", filename=out.name)

    @base.get("/info")
    def info() -> dict[str, Any]:
        return {
            "name": "LocalText2Voice Android host",
            "version": "1.2.1",
            "android_endpoint": "/synthesize",
            "engines": list(TTS_ENGINES.keys()),
        }

    return base


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--allow-lan", action="store_true", help="Bind 0.0.0.0")
    args = parser.parse_args()

    settings = SettingsManager()
    app = create_android_app(settings)
    host = "0.0.0.0" if args.allow_lan else args.host
    log.info("Starting LTV engine host on %s:%d", host, args.port)
    uvicorn.run(app, host=host, port=args.port, log_level="info")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
