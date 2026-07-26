"""T2V Android companion — engine host.

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
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse, JSONResponse, StreamingResponse
from pydantic import BaseModel

log = logging.getLogger("ltv.engine_host")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s :: %(message)s")


# Конфигурация
HF_TOKEN = os.environ.get("HF_TOKEN", "")  # для приватных репо
MODELS_DIR = Path(os.environ.get("LTV_MODELS_DIR", "./models")).resolve()
MODELS_DIR.mkdir(parents=True, exist_ok=True)
os.environ.setdefault("HF_HOME", str(MODELS_DIR / ".hf-cache"))
MODELS_DB = MODELS_DIR / ".models.json"
CLONES_DIR = MODELS_DIR / ".voice-clones"


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
    {
        "id": "Qwen/Qwen3-TTS-12Hz-0.6B-Base",
        "name": "Qwen3 TTS 0.6B Voice Cloning",
        "engine": "qwen",
        "runtime": "qwen-tts",
        "target": "remote-host",
        "capabilities": ["voice-cloning"],
        "size_mb": 1800,
        "languages": ["zh", "en", "ja", "ko", "de", "fr", "ru", "pt", "es", "it"],
        "description": "Official compact Qwen3-TTS Base model for voice cloning.",
        "tags": ["official", "multilingual", "voice-cloning", "remote-only"],
        "files": [],
    },
    {
        "id": "Qwen/Qwen3-TTS-12Hz-1.7B-Base",
        "name": "Qwen3 TTS 1.7B Voice Cloning",
        "engine": "qwen",
        "runtime": "qwen-tts",
        "target": "remote-host",
        "capabilities": ["voice-cloning"],
        "size_mb": 4200,
        "languages": ["zh", "en", "ja", "ko", "de", "fr", "ru", "pt", "es", "it"],
        "description": "Official full-size Qwen3-TTS Base model for voice cloning.",
        "tags": ["official", "multilingual", "voice-cloning", "remote-only"],
        "files": [],
    },
    {
        "id": "facebook/mms-tts-rus",
        "name": "Meta MMS-TTS Russian",
        "engine": "mms",
        "runtime": "transformers",
        "target": "remote-host",
        "size_mb": 145,
        "languages": ["ru"],
        "description": "Compact Russian VITS checkpoint from Meta MMS.",
        "tags": ["official", "russian", "single-voice", "remote-only", "non-commercial"],
        "license": "CC-BY-NC-4.0",
        "files": [],
    },
    {
        "id": "facebook/mms-tts-eng",
        "name": "Meta MMS-TTS English",
        "engine": "mms",
        "runtime": "transformers",
        "target": "remote-host",
        "size_mb": 145,
        "languages": ["en"],
        "description": "Compact English VITS checkpoint from Meta MMS.",
        "tags": ["official", "english", "single-voice", "remote-only", "non-commercial"],
        "license": "CC-BY-NC-4.0",
        "files": [],
    },
    {
        "id": "ResembleAI/chatterbox-turbo",
        "name": "Chatterbox Turbo",
        "engine": "chatterbox",
        "runtime": "chatterbox-tts",
        "target": "remote-host",
        "size_mb": 4040,
        "languages": ["en"],
        "description": "Official 350M low-latency model with paralinguistic tags and voice cloning.",
        "tags": ["official", "english", "voice-cloning", "remote-only"],
        "license": "MIT",
        "files": [],
    },
]
MODEL_CATALOG = {model["id"]: model for model in KNOWN_TTS_MODELS}
DEFAULT_QWEN_MODEL = "Qwen/Qwen3-TTS-12Hz-0.6B-CustomVoice"
QWEN_SPEAKERS = ["Vivian", "Serena", "Uncle_Fu", "Dylan", "Eric", "Ryan", "Aiden", "Ono_Anna", "Sohee"]
_qwen_models: dict[str, Any] = {}
_mms_models: dict[str, Any] = {}
_chatterbox_models: dict[str, Any] = {}
_music_models: dict[str, Any] = {}
MUSIC_MODELS = [
    {
        "id": "stabilityai/stable-audio-open-1.0",
        "name": "Stable Audio Open 1.0",
        "runtime": "diffusers",
        "target": "remote-host",
        "max_seconds": 47,
        "license": "Stability AI Community License",
        "commercial_note": "Commercial use permitted subject to the Community License revenue limit.",
    },
    {
        "id": "facebook/musicgen-small",
        "name": "MusicGen Small",
        "runtime": "transformers",
        "target": "remote-host",
        "max_seconds": 30,
        "license": "CC-BY-NC 4.0 weights",
        "commercial_note": "Non-commercial use only.",
    },
]
MUSIC_MODEL_CATALOG = {model["id"]: model for model in MUSIC_MODELS}


def require_supported_model(repo_id: str) -> dict[str, Any]:
    model = MODEL_CATALOG.get(repo_id)
    if model is None:
        raise HTTPException(404, "Model is not in the verified LTV compatibility catalog")
    return model


def qwen_runtime_available() -> bool:
    return importlib.util.find_spec("qwen_tts") is not None


def transformers_runtime_available() -> bool:
    return importlib.util.find_spec("transformers") is not None


def chatterbox_runtime_available() -> bool:
    return importlib.util.find_spec("chatterbox") is not None


def local_model_source(model_id: str) -> str:
    directory = MODELS_DIR / model_id.replace("/", "_")
    return str(directory) if directory.is_dir() and any(directory.iterdir()) else model_id


def synthesize_mms(body: "SynthesizeBody") -> FileResponse:
    model_id = str(body.options.get("model_id") or body.voice or "facebook/mms-tts-rus")
    info = require_supported_model(model_id)
    if info["engine"] != "mms":
        raise HTTPException(422, "Selected model is not an MMS-TTS model")
    if not transformers_runtime_available():
        raise HTTPException(503, "MMS runtime unavailable; install requirements-extra-tts.txt")
    import torch
    import soundfile as sf
    from transformers import AutoTokenizer, VitsModel

    cached = _mms_models.get(model_id)
    if cached is None:
        source = local_model_source(model_id)
        cached = (AutoTokenizer.from_pretrained(source), VitsModel.from_pretrained(source))
        _mms_models[model_id] = cached
    tokenizer, model = cached
    inputs = tokenizer(body.text, return_tensors="pt")
    with torch.no_grad():
        waveform = model(**inputs).waveform.squeeze().cpu().numpy()
    output_dir = MODELS_DIR / ".output"
    output_dir.mkdir(parents=True, exist_ok=True)
    output = output_dir / f"mms_{int(time.time() * 1000)}.wav"
    sf.write(output, waveform, model.config.sampling_rate)
    return FileResponse(output, media_type="audio/wav", filename=output.name)


def synthesize_chatterbox(body: "SynthesizeBody") -> FileResponse:
    model_id = str(body.options.get("model_id") or "ResembleAI/chatterbox-turbo")
    info = require_supported_model(model_id)
    if info["engine"] != "chatterbox":
        raise HTTPException(422, "Selected model is not a Chatterbox model")
    if not chatterbox_runtime_available():
        raise HTTPException(503, "Chatterbox runtime unavailable; install requirements-extra-tts.txt")
    import torch
    import soundfile as sf
    from chatterbox.tts_turbo import ChatterboxTurboTTS

    model = _chatterbox_models.get(model_id)
    if model is None:
        device = "cuda" if torch.cuda.is_available() else "cpu"
        model = ChatterboxTurboTTS.from_pretrained(device=device)
        _chatterbox_models[model_id] = model
    audio_prompt = body.options.get("audio_prompt_path")
    waveform = model.generate(body.text, audio_prompt_path=audio_prompt or None)
    output_dir = MODELS_DIR / ".output"
    output_dir.mkdir(parents=True, exist_ok=True)
    output = output_dir / f"chatterbox_{int(time.time() * 1000)}.wav"
    sf.write(output, waveform.squeeze().cpu().numpy(), model.sr)
    return FileResponse(output, media_type="audio/wav", filename=output.name)


def generate_stable_audio(body: "MusicGenerateBody", seconds: int) -> tuple[Any, int]:
    if importlib.util.find_spec("diffusers") is None:
        raise HTTPException(503, "Music runtime unavailable; install requirements-music.txt")
    import torch
    from diffusers import StableAudioPipeline

    model_id = "stabilityai/stable-audio-open-1.0"
    pipeline = _music_models.get(model_id)
    if pipeline is None:
        dtype = torch.float16 if torch.cuda.is_available() else torch.float32
        pipeline = StableAudioPipeline.from_pretrained(model_id, torch_dtype=dtype)
        pipeline = pipeline.to("cuda" if torch.cuda.is_available() else "cpu")
        _music_models[model_id] = pipeline
    result = pipeline(
        body.prompt,
        negative_prompt=body.negative_prompt,
        audio_end_in_s=float(seconds),
        num_inference_steps=100,
    )
    return result.audios[0].T.float().cpu().numpy(), pipeline.vae.sampling_rate


def generate_musicgen(body: "MusicGenerateBody", seconds: int) -> tuple[Any, int]:
    if importlib.util.find_spec("transformers") is None:
        raise HTTPException(503, "Music runtime unavailable; install requirements-music.txt")
    import torch
    from transformers import AutoProcessor, MusicgenForConditionalGeneration

    model_id = "facebook/musicgen-small"
    cached = _music_models.get(model_id)
    if cached is None:
        processor = AutoProcessor.from_pretrained(model_id)
        model = MusicgenForConditionalGeneration.from_pretrained(model_id)
        if torch.cuda.is_available():
            model = model.to("cuda")
        cached = (processor, model)
        _music_models[model_id] = cached
    processor, model = cached
    inputs = processor(text=[body.prompt], padding=True, return_tensors="pt")
    if torch.cuda.is_available():
        inputs = {key: value.to("cuda") for key, value in inputs.items()}
    sample_rate = model.config.audio_encoder.sampling_rate
    frame_rate = model.config.audio_encoder.frame_rate
    audio = model.generate(**inputs, max_new_tokens=seconds * frame_rate)
    return audio[0, 0].detach().float().cpu().numpy(), sample_rate


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

    if body.voice.startswith("clone:"):
        return synthesize_qwen_clone(body, body.voice.removeprefix("clone:"))
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


def clone_metadata(clone_id: str) -> tuple[dict[str, Any], Path]:
    if not re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,63}", clone_id):
        raise HTTPException(400, "Invalid clone id")
    directory = CLONES_DIR / clone_id
    metadata = directory / "clone.json"
    if not metadata.is_file():
        raise HTTPException(404, "Voice clone not found")
    return json.loads(metadata.read_text()), directory


def synthesize_qwen_clone(body: "SynthesizeBody", clone_id: str) -> FileResponse:
    import soundfile as sf

    metadata, directory = clone_metadata(clone_id)
    model = load_qwen_model(metadata["model_id"])
    try:
        wavs, sample_rate = model.generate_voice_clone(
            text=body.text,
            language=body.lang or "Auto",
            ref_audio=str(directory / metadata["audio_file"]),
            ref_text=metadata["transcript"],
        )
        output_dir = MODELS_DIR / ".output"
        output_dir.mkdir(parents=True, exist_ok=True)
        output = output_dir / f"qwen_clone_{int(time.time() * 1000)}.wav"
        sf.write(output, wavs[0], sample_rate)
        return FileResponse(output, media_type="audio/wav", filename=output.name)
    except Exception as exc:
        log.exception("Qwen clone synthesis failed")
        raise HTTPException(500, f"Qwen clone synthesis failed: {exc}") from exc


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


class MusicGenerateBody(BaseModel):
    model_id: str = "stabilityai/stable-audio-open-1.0"
    prompt: str
    negative_prompt: str = "vocals, speech, voice, distortion"
    seconds: int = 30


def create_app() -> FastAPI:
    app = FastAPI(title="LTV Engine Host", version="1.2.1")

    # --- Базовые ---
    @app.get("/info")
    def info() -> dict[str, Any]:
        return {
            "name": "T2V Android Host",
            "version": "1.2.1",
            "models_dir": str(MODELS_DIR),
            "known_models": [m["id"] for m in KNOWN_TTS_MODELS],
            "capabilities": {
                "qwen_tts": qwen_runtime_available(),
                "mms_tts": transformers_runtime_available(),
                "chatterbox_tts": chatterbox_runtime_available(),
                "ollama_tts": False,
                "arbitrary_huggingface_models": False,
                "music_generation": importlib.util.find_spec("diffusers") is not None,
            },
            "music_models": [model["id"] for model in MUSIC_MODELS],
        }

    @app.get("/engines")
    def engines() -> list[str]:
        result = []
        if qwen_runtime_available():
            result.append("qwen")
        if transformers_runtime_available():
            result.append("mms")
        if chatterbox_runtime_available():
            result.append("chatterbox")
        return result

    @app.get("/engines/mms/voices")
    def mms_voices() -> list[dict[str, Any]]:
        return [
            {
                "id": model["id"],
                "display_name": model["name"],
                "language": model["languages"][0],
                "sample_rate": 16000,
                "download_model_id": model["id"],
                "download_size_bytes": model["size_mb"] * 1024 * 1024,
            }
            for model in KNOWN_TTS_MODELS if model["engine"] == "mms"
        ]

    @app.get("/engines/chatterbox/voices")
    def chatterbox_voices() -> list[dict[str, Any]]:
        model = MODEL_CATALOG["ResembleAI/chatterbox-turbo"]
        return [{
            "id": "default",
            "display_name": "Chatterbox Turbo",
            "language": "en",
            "sample_rate": 24000,
            "download_model_id": model["id"],
            "download_size_bytes": model["size_mb"] * 1024 * 1024,
        }]

    @app.get("/engines/qwen/voices")
    def qwen_voices() -> list[dict[str, Any]]:
        if not qwen_runtime_available():
            raise HTTPException(503, "Qwen runtime unavailable; install requirements-qwen.txt")
        built_in = [
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
        if CLONES_DIR.is_dir():
            for directory in CLONES_DIR.iterdir():
                metadata = directory / "clone.json"
                if not metadata.is_file():
                    continue
                item = json.loads(metadata.read_text())
                built_in.append({
                    "id": f"clone:{item['id']}",
                    "display_name": item["name"],
                    "language": item.get("language", "multilingual"),
                    "sample_rate": 24000,
                    "is_cloned": True,
                })
        return built_in

    @app.get("/voice-clones")
    def list_voice_clones() -> list[dict[str, Any]]:
        if not CLONES_DIR.is_dir():
            return []
        result = []
        for directory in CLONES_DIR.iterdir():
            metadata = directory / "clone.json"
            if metadata.is_file():
                result.append(json.loads(metadata.read_text()))
        return sorted(result, key=lambda item: item["name"].lower())

    @app.post("/voice-clones")
    async def create_voice_clone(
        name: str = Form(...),
        transcript: str = Form(...),
        language: str = Form("Auto"),
        model_id: str = Form("Qwen/Qwen3-TTS-12Hz-0.6B-Base"),
        audio: UploadFile = File(...),
    ) -> dict[str, Any]:
        model = require_supported_model(model_id)
        if "voice-cloning" not in model.get("capabilities", []):
            raise HTTPException(422, "Selected model does not support voice cloning")
        if not name.strip() or not transcript.strip():
            raise HTTPException(422, "Name and exact reference transcript are required")
        clone_id = re.sub(r"[^a-z0-9_-]+", "-", name.lower()).strip("-")[:64]
        if not clone_id:
            raise HTTPException(422, "Voice name must contain letters or numbers")
        suffix = Path(audio.filename or "reference.wav").suffix.lower()
        if suffix not in {".wav", ".mp3", ".flac", ".m4a", ".ogg"}:
            raise HTTPException(422, "Unsupported reference audio format")
        CLONES_DIR.mkdir(parents=True, exist_ok=True)
        directory = CLONES_DIR / clone_id
        if directory.exists():
            raise HTTPException(409, "A voice clone with this name already exists")
        directory.mkdir()
        audio_name = f"reference{suffix}"
        with (directory / audio_name).open("wb") as output:
            shutil.copyfileobj(audio.file, output)
        metadata = {
            "id": clone_id,
            "name": name.strip(),
            "transcript": transcript.strip(),
            "language": language,
            "model_id": model_id,
            "audio_file": audio_name,
            "created_at": int(time.time()),
        }
        (directory / "clone.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2))
        return metadata

    @app.delete("/voice-clones/{clone_id}")
    def delete_voice_clone(clone_id: str) -> dict[str, str]:
        _, directory = clone_metadata(clone_id)
        shutil.rmtree(directory)
        return {"deleted": clone_id}

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

    @app.post("/engines/mms/preload")
    def preload_mms(body: dict[str, Any]) -> dict[str, str]:
        model_id = str(body.get("options", {}).get("model_id", "facebook/mms-tts-rus"))
        info = require_supported_model(model_id)
        if info["engine"] != "mms":
            raise HTTPException(422, "Selected model is not an MMS-TTS model")
        return {"status": "ready", "model_id": model_id}

    @app.post("/engines/mms/unload")
    def unload_mms() -> dict[str, str]:
        _mms_models.clear()
        return {"status": "unloaded"}

    @app.post("/engines/chatterbox/preload")
    def preload_chatterbox(body: dict[str, Any]) -> dict[str, str]:
        model_id = str(body.get("options", {}).get("model_id", "ResembleAI/chatterbox-turbo"))
        info = require_supported_model(model_id)
        if info["engine"] != "chatterbox":
            raise HTTPException(422, "Selected model is not a Chatterbox model")
        return {"status": "ready", "model_id": model_id}

    @app.post("/engines/chatterbox/unload")
    def unload_chatterbox() -> dict[str, str]:
        _chatterbox_models.clear()
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

    @app.get("/music/models")
    def list_music_models() -> list[dict[str, Any]]:
        return MUSIC_MODELS

    @app.post("/music/generate")
    def generate_music(body: MusicGenerateBody) -> FileResponse:
        model_info = MUSIC_MODEL_CATALOG.get(body.model_id)
        if model_info is None:
            raise HTTPException(404, "Music model is not in the verified catalog")
        if not body.prompt.strip():
            raise HTTPException(422, "Music prompt is required")
        seconds = min(max(body.seconds, 1), model_info["max_seconds"])
        try:
            if body.model_id == "stabilityai/stable-audio-open-1.0":
                audio, sample_rate = generate_stable_audio(body, seconds)
            else:
                audio, sample_rate = generate_musicgen(body, seconds)
            output_dir = MODELS_DIR / ".music"
            output_dir.mkdir(parents=True, exist_ok=True)
            output = output_dir / f"music_{int(time.time() * 1000)}.wav"
            import soundfile as sf
            sf.write(output, audio, sample_rate)
            return FileResponse(output, media_type="audio/wav", filename=output.name)
        except HTTPException:
            raise
        except Exception as exc:
            log.exception("Music generation failed")
            raise HTTPException(500, f"Music generation failed: {exc}") from exc

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
            elif model_info["engine"] == "chatterbox":
                path = snapshot_download(
                    repo_id=repo_id,
                    cache_dir=os.environ["HF_HOME"],
                    token=HF_TOKEN or None,
                )
                (model_dir / ".downloaded").write_text(path)
                return {
                    "repo_id": repo_id,
                    "path": path,
                    "files": [{"name": ".downloaded", "size": 0}],
                }
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
        if body.engine_id == "mms":
            return synthesize_mms(body)
        if body.engine_id == "chatterbox":
            return synthesize_chatterbox(body)

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
