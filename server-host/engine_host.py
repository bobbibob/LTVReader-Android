"""LocalText2Voice Android companion — engine host.

Endpoints:
  GET  /info
  GET  /engines
  POST /synthesize
  GET  /models                 — список TTS-моделей с HuggingFace
  GET  /models/{repo_id}        — информация о модели
  GET  /models/{repo_id}/files  — файлы в репо
  POST /models/{repo_id}/download — скачать модель на сервер
  GET  /models/{repo_id}/file/{path} — отдать файл
  GET  /local-models            — локально скачанные модели
  GET  /voices                 — голоса всех движков
"""
from __future__ import annotations

import argparse
import json
import logging
import os
import re
import shutil
import time
from pathlib import Path
from typing import Any

import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse, JSONResponse, StreamingResponse
from pydantic import BaseModel

log = logging.getLogger("ltv.engine_host")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s :: %(message)s")


# Конфигурация
HF_TOKEN = os.environ.get("HF_TOKEN", "")  # для приватных репо
MODELS_DIR = Path(os.environ.get("LTV_MODELS_DIR", "./models")).resolve()
MODELS_DIR.mkdir(parents=True, exist_ok=True)
MODELS_DB = MODELS_DIR / ".models.json"


def load_models_db() -> dict:
    if MODELS_DB.exists():
        try:
            return json.loads(MODELS_DB.read_text())
        except Exception:
            pass
    return {}


def save_models_db(db: dict) -> None:
    MODELS_DB.write_text(json.dumps(db, indent=2))


# Каталог известных TTS-моделей (можно расширять)
KNOWN_TTS_MODELS = [
    {
        "id": "onnx-community/Kokoro-82M",
        "name": "Kokoro 82M (ONNX)",
        "engine": "kokoro",
        "size_mb": 175,
        "languages": ["en-us", "en-gb", "es", "fr", "it", "pt", "ja", "zh"],
        "description": "Kokoro-82M — компактная (~150 МБ) высококачественная TTS. 24 kHz, 50+ голосов. Open source (Apache 2.0).",
        "tags": ["english", "multilingual", "fast", "local", "onnx"],
        "files": ["kokoro-v0_19.onnx", "voices.bin", "config.json"],
    },
    {
        "id": "rhasspy/piper-voices",
        "name": "Piper Voices (60+ voices)",
        "engine": "piper",
        "size_mb": 65,
        "languages": ["en", "de", "fr", "es", "ru", "it", "pt", "uk", "pl", "nl"],
        "description": "Коллекция голосов Piper. Скачиваются по одному — выберите нужные.",
        "tags": ["english", "german", "french", "spanish", "russian", "local", "fast"],
        "files": [],  # много файлов
    },
    {
        "id": "resemble-ai/chatterbox",
        "name": "Chatterbox TTS",
        "engine": "chatterbox",
        "size_mb": 1100,
        "languages": ["en"],
        "description": "Chatterbox — voice cloning + TTS от Resemble AI. Тяжёлая модель, требует GPU.",
        "tags": ["voice-cloning", "english", "expressive"],
        "files": ["model.safetensors", "config.json"],
    },
    {
        "id": "Qwen/Qwen2.5-Omni-7B",
        "name": "Qwen2.5-Omni TTS",
        "engine": "qwen",
        "size_mb": 15000,
        "languages": ["en", "zh", "ja", "ko", "es", "fr", "de", "ru", "ar"],
        "description": "Qwen2.5-Omni — мультимодальная модель с TTS. Очень большая.",
        "tags": ["multilingual", "large", "experimental"],
        "files": ["model-00000-of-00004.safetensors"],
    },
]


# ============== API Endpoints ==============

class SynthesizeBody(BaseModel):
    engine_id: str
    text: str
    voice: str = ""
    lang: str = ""
    speed: float = 1.0
    options: dict[str, Any] = {}


class DownloadBody(BaseModel):
    files: list[str] = []  # пустые = скачать все основные


def create_app() -> FastAPI:
    app = FastAPI(title="LTV Engine Host", version="1.2.1")

    # --- Базовые ---
    @app.get("/info")
    def info() -> dict[str, Any]:
        return {
            "name": "LocalText2Voice Android Host",
            "version": "1.2.1",
            "models_dir": str(MODELS_DIR),
            "known_models": [m["id"] for m in KNOWN_TTS_MODELS],
        }

    @app.get("/engines")
    def engines() -> dict[str, list[str]]:
        return {
            "engines": ["kokoro", "piper", "chatterbox", "qwen", "omnivoice", "remote:piper", "remote:chatterbox", "remote:qwen", "remote:omnivoice"],
        }

    # --- Каталог моделей ---
    @app.get("/models")
    def list_models() -> dict[str, Any]:
        db = load_models_db()
        installed = db.get("installed", [])
        return {
            "models": KNOWN_TTS_MODELS,
            "installed": installed,
        }

    @app.get("/models/{repo_id:path}/files")
    def list_repo_files(repo_id: str) -> dict[str, Any]:
        """Получить список файлов в HuggingFace репо (через huggingface_hub)."""
        try:
            from huggingface_hub import list_repo_files
            files = list_repo_files(repo_id, token=HF_TOKEN or None)
            return {"repo_id": repo_id, "files": files}
        except ImportError:
            raise HTTPException(503, "huggingface_hub not installed. pip install huggingface_hub")
        except Exception as e:
            log.exception("list_repo_files failed")
            raise HTTPException(500, f"HuggingFace error: {e}")

    @app.get("/models/{repo_id:path}/file/{file_path:path}")
    def download_model_file(repo_id: str, file_path: str) -> FileResponse:
        """Serve a previously downloaded model file to the Android app."""
        root = (MODELS_DIR / repo_id.replace("/", "_")).resolve()
        target = (root / file_path).resolve()
        if root not in target.parents or not target.is_file():
            raise HTTPException(404, "Model file not found; download it on the host first")
        return FileResponse(target)

    @app.post("/models/{repo_id:path}/download")
    def download_model(repo_id: str, body: DownloadBody) -> dict[str, Any]:
        """Скачать файлы модели из HuggingFace на локальный диск сервера."""
        try:
            from huggingface_hub import hf_hub_download, snapshot_download
        except ImportError:
            raise HTTPException(503, "huggingface_hub not installed")

        # Каталог для модели
        model_dir = MODELS_DIR / repo_id.replace("/", "_")
        model_dir.mkdir(parents=True, exist_ok=True)

        # Найти информацию о модели
        model_info = next((m for m in KNOWN_TTS_MODELS if m["id"] == repo_id), None)
        default_files = model_info.get("files", []) if model_info else []

        try:
            if body.files:
                # Скачать выбранные файлы
                downloaded = []
                for fname in body.files:
                    log.info("downloading %s/%s", repo_id, fname)
                    path = hf_hub_download(
                        repo_id=repo_id,
                        filename=fname,
                        local_dir=str(model_dir),
                        token=HF_TOKEN or None,
                    )
                    downloaded.append({"name": fname, "size": os.path.getsize(path)})
                return {"repo_id": repo_id, "downloaded": downloaded, "path": str(model_dir)}
            else:
                # Скачать все файлы (snapshot)
                log.info("snapshot_download %s to %s", repo_id, model_dir)
                path = snapshot_download(
                    repo_id=repo_id,
                    local_dir=str(model_dir),
                    token=HF_TOKEN or None,
                    allow_patterns=default_files if default_files else None,
                )
                return {
                    "repo_id": repo_id,
                    "path": path,
                    "files": [{"name": f.name, "size": f.stat().st_size} for f in Path(path).rglob("*") if f.is_file()],
                }
        except Exception as e:
            log.exception("download failed")
            raise HTTPException(500, f"Download failed: {e}")

    # Keep this catch-all last: a path converter would otherwise swallow /files and /download.
    @app.get("/models/{repo_id:path}")
    def model_info(repo_id: str) -> dict[str, Any]:
        for m in KNOWN_TTS_MODELS:
            if m["id"] == repo_id:
                return m
        raise HTTPException(404, f"Model not in catalog: {repo_id}")

    @app.get("/local-models")
    def list_local_models() -> dict[str, Any]:
        """Список моделей, скачанных на сервер."""
        if not MODELS_DIR.exists():
            return {"models": []}
        result = []
        for model_dir in MODELS_DIR.iterdir():
            if not model_dir.is_dir() or model_dir.name.startswith("."):
                continue
            files = []
            total_size = 0
            for f in model_dir.rglob("*"):
                if f.is_file():
                    sz = f.stat().st_size
                    total_size += sz
                    if sz < 1_000_000:  # показываем только файлы < 1 МБ
                        files.append({"name": str(f.relative_to(model_dir)), "size": sz})
            result.append({
                "id": model_dir.name.replace("_", "/"),
                "path": str(model_dir),
                "total_size_mb": round(total_size / 1024 / 1024, 1),
                "files_count": len(list(model_dir.rglob("*"))),
            })
        return {"models": result, "dir": str(MODELS_DIR)}

    @app.delete("/local-models/{model_id:path}")
    def delete_local_model(model_id: str) -> dict[str, Any]:
        model_dir = MODELS_DIR / model_id.replace("/", "_")
        if not model_dir.exists():
            raise HTTPException(404, f"Model not found: {model_id}")
        shutil.rmtree(model_dir)
        return {"deleted": model_id}

    # --- Синтез через движки (как раньше) ---
    @app.post("/synthesize")
    def synthesize(body: SynthesizeBody) -> FileResponse:
        from app.core.settings_manager import SettingsManager
        from app.tts.registry import TTS_ENGINES
        from pathlib import Path
        import time as _t

        settings = SettingsManager()
        engine = TTS_ENGINES.get(body.engine_id)
        if engine is None:
            raise HTTPException(404, f"Engine not found: {body.engine_id}")

        out = Path(settings.settings.get("output_dir", "output")) / f"android_{int(_t.time()*1000)}.wav"
        out.parent.mkdir(parents=True, exist_ok=True)

        config = {
            "voice": body.voice,
            "lang": body.lang,
            "speed": body.speed,
            **body.options,
        }
        try:
            engine.synthesize_to_wav(body.text, out, config)
        except Exception as exc:
            log.exception("synthesize failed")
            raise HTTPException(500, f"synthesize failed: {exc}") from exc
        return FileResponse(out, media_type="audio/wav", filename=out.name)

    return app


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--allow-lan", action="store_true")
    parser.add_argument("--models-dir", default=os.environ.get("LTV_MODELS_DIR", "./models"))
    args = parser.parse_args()

    global MODELS_DIR
    MODELS_DIR = Path(args.models_dir).resolve()
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    log.info("Models dir: %s", MODELS_DIR)

    app = create_app()
    host = "0.0.0.0" if args.allow_lan else args.host
    log.info("Starting LTV engine host on %s:%d", host, args.port)
    log.info("Endpoints: /info, /engines, /synthesize, /models, /local-models")
    uvicorn.run(app, host=host, port=args.port, log_level="info")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
