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
import importlib.util
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


# Закрытый каталог: каждая запись привязана к реализованному runtime.
# Произвольные Hugging Face репозитории намеренно не принимаются.
KNOWN_TTS_MODELS = [
    {
        "id": "Qwen/Qwen3-TTS-12Hz-0.6B-CustomVoice",
        "name": "Qwen3 TTS 0.6B CustomVoice",
        "engine": "qwen",
        "runtime": "qwen-tts",
        "target": "remote-host",
        "size_mb": 1800,
        "languages": ["zh", "en", "ja", "ko", "de", "fr", "ru", "pt", "es", "it"],
        "description": "Official compact Qwen3-TTS model with nine built-in voices.",
        "tags": ["official", "multilingual", "custom-voice", "remote-only"],
        "files": [],
    },
    {
        "id": "Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice",
        "name": "Qwen3 TTS 1.7B CustomVoice",
        "engine": "qwen",
        "runtime": "qwen-tts",
        "target": "remote-host",
        "size_mb": 4200,
        "languages": ["zh", "en", "ja", "ko", "de", "fr", "ru", "pt", "es", "it"],
        "description": "Official full-size Qwen3-TTS model with instruction-controlled voices.",
        "tags": ["official", "multilingual", "custom-voice", "remote-only"],
        "files": [],
    },
]
MODEL_CATALOG = {model["id"]: model for model in KNOWN_TTS_MODELS}
DEFAULT_QWEN_MODEL = "Qwen/Qwen3-TTS-12Hz-0.6B-CustomVoice"
QWEN_SPEAKERS = ["Vivian", "Serena", "Uncle_Fu", "Dylan", "Eric", "Ryan", "Aiden", "Ono_Anna", "Sohee"]
_qwen_models: dict[str, Any] = {}


def require_supported_model(repo_id: str) -> dict[str, Any]:
    model = MODEL_CATALOG.get(repo_id)
    if model is None:
        raise HTTPException(404, "Model is not in the verified LTV compatibility catalog")
    return model


def qwen_runtime_available() -> bool:
    return importlib.util.find_spec("qwen_tts") is not None


def load_qwen_model(model_id: str) -> Any:
    require_supported_model(model_id)
    if not qwen_runtime_available():
        raise HTTPException(503, "Qwen runtime unavailable; install requirements-qwen.txt")
    if model_id in _qwen_models:
        return _qwen_models[model_id]

    import torch
    from qwen_tts import Qwen3TTSModel

    local_dir = MODELS_DIR / model_id.replace("/", "_")
    source = str(local_dir) if local_dir.is_dir() and any(local_dir.iterdir()) else model_id
    cuda = torch.cuda.is_available()
    kwargs: dict[str, Any] = {
        "device_map": "cuda:0" if cuda else "cpu",
        "dtype": torch.bfloat16 if cuda else torch.float32,
    }
    if cuda:
        kwargs["attn_implementation"] = "sdpa"
    try:
        model = Qwen3TTSModel.from_pretrained(source, **kwargs)
    except Exception as exc:
        log.exception("Qwen model loading failed")
        raise HTTPException(503, f"Qwen model loading failed: {exc}") from exc
    _qwen_models[model_id] = model
    return model


def synthesize_qwen(body: "SynthesizeBody") -> FileResponse:
    import soundfile as sf

    model_id = str(body.options.get("model_id", DEFAULT_QWEN_MODEL))
    model = load_qwen_model(model_id)
    speaker = body.voice or str(body.options.get("speaker", "Ryan"))
    if speaker not in QWEN_SPEAKERS:
        raise HTTPException(422, f"Unsupported Qwen speaker: {speaker}")
    language = body.lang or str(body.options.get("language", "Auto"))
    instruct = str(body.options.get("instruct", ""))
    try:
        wavs, sample_rate = model.generate_custom_voice(
            text=body.text,
            language=language,
            speaker=speaker,
            instruct=instruct,
        )
        output_dir = MODELS_DIR / ".output"
        output_dir.mkdir(parents=True, exist_ok=True)
        output = output_dir / f"qwen_{int(time.time() * 1000)}.wav"
        sf.write(output, wavs[0], sample_rate)
        return FileResponse(output, media_type="audio/wav", filename=output.name)
    except HTTPException:
        raise
    except Exception as exc:
        log.exception("Qwen synthesis failed")
        raise HTTPException(500, f"Qwen synthesis failed: {exc}") from exc


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
            "capabilities": {
                "qwen_tts": qwen_runtime_available(),
                "ollama_tts": False,
                "arbitrary_huggingface_models": False,
            },
        }

    @app.get("/engines")
    def engines() -> list[str]:
        return ["qwen"] if qwen_runtime_available() else []

    @app.get("/engines/qwen/voices")
    def qwen_voices() -> list[dict[str, Any]]:
        if not qwen_runtime_available():
            raise HTTPException(503, "Qwen runtime unavailable; install requirements-qwen.txt")
        return [
            {
                "id": speaker,
                "display_name": speaker,
                "language": "multilingual",
                "sample_rate": 24000,
                "download_model_id": DEFAULT_QWEN_MODEL,
                "download_size_bytes": 1800 * 1024 * 1024,
            }
            for speaker in QWEN_SPEAKERS
        ]

    @app.post("/engines/qwen/preload")
    def preload_qwen(body: dict[str, Any]) -> dict[str, str]:
        model_id = str(body.get("options", {}).get("model_id", DEFAULT_QWEN_MODEL))
        require_supported_model(model_id)
        load_qwen_model(model_id)
        return {"status": "ready", "model_id": model_id}

    @app.post("/engines/qwen/unload")
    def unload_qwen() -> dict[str, str]:
        _qwen_models.clear()
        return {"status": "unloaded"}

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
        require_supported_model(repo_id)
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
        require_supported_model(repo_id)
        root = (MODELS_DIR / repo_id.replace("/", "_")).resolve()
        target = (root / file_path).resolve()
        if root not in target.parents or not target.is_file():
            raise HTTPException(404, "Model file not found; download it on the host first")
        return FileResponse(target)

    @app.post("/models/{repo_id:path}/download")
    def download_model(repo_id: str, body: DownloadBody) -> dict[str, Any]:
        """Скачать файлы модели из HuggingFace на локальный диск сервера."""
        model_info = require_supported_model(repo_id)
        try:
            from huggingface_hub import hf_hub_download, snapshot_download
        except ImportError:
            raise HTTPException(503, "huggingface_hub not installed")

        # Каталог для модели
        model_dir = MODELS_DIR / repo_id.replace("/", "_")
        model_dir.mkdir(parents=True, exist_ok=True)

        # Найти информацию о модели
        default_files = model_info.get("files", [])

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
        require_supported_model(model_id)
        model_dir = MODELS_DIR / model_id.replace("/", "_")
        if not model_dir.exists():
            raise HTTPException(404, f"Model not found: {model_id}")
        shutil.rmtree(model_dir)
        return {"deleted": model_id}

    # --- Синтез через движки (как раньше) ---
    @app.post("/synthesize")
    def synthesize(body: SynthesizeBody) -> FileResponse:
        if body.engine_id == "qwen":
            return synthesize_qwen(body)

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
