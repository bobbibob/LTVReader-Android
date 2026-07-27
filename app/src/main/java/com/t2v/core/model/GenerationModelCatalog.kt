package com.t2v.core.model

/**
 * Single source of truth for models shown by T2V.
 *
 * A catalog entry is not considered downloadable until [support] is [Support.Verified].
 * This prevents a Hugging Face repository that needs desktop PyTorch from being
 * presented as an Android model.
 */
object GenerationModelCatalog {
    enum class Category { Voice, Music, Sound }

    enum class Runtime {
        SherpaOnnx,
        LiteRt,
    }

    enum class Capability {
        TextToSpeech,
        VoiceCloning,
        MusicGeneration,
        SoundGeneration,
    }

    enum class Support {
        Verified,
        RuntimeInDevelopment,
        Experimental,
    }

    data class Requirements(
        val supportedAbis: Set<String> = setOf("arm64-v8a"),
        val minimumRamMb: Int,
        val runtime: Runtime,
        val runtimeBundled: Boolean,
    )
    /** Per-model user-facing description of which LTV tags work and how to invoke them. */
    data class TagDocs(
        val tagline: String,
        val supported: List<String>,
        val partial: List<String> = emptyList(),
        val ignored: List<String> = emptyList(),
        val examples: List<String> = emptyList(),
        val promptHelp: String? = null,
    )

    data class Entry(
        val id: String,
        val title: String,
        val categories: Set<Category>,
        val capabilities: Set<Capability>,
        val requirements: Requirements,
        val support: Support,
        val approximateDownloadBytes: Long?,
        val license: String,
        val repository: String,
        val revision: String?,
        val notes: String,
        val tags: TagDocs? = null,
    ) {
        val canInstall: Boolean
            get() = support == Support.Verified
    }

    private val KOKORO_TAGS = TagDocs(
        tagline = "English TTS that runs on the phone. Supports no native emotion tags; the editor maps expressive markup to speed, pitch and volume changes only.",
        supported = listOf(
            "{{voice \"...\"}} - switch between 11 bundled voices",
            "{{lang en-US}} - English only",
            "{{speed 0.5..2.0}} - real-time speed multiplier",
            "{{pitch 0.5..2.0}} - pitch shift",
            "{{volume 0..4}} - gain",
            "{{pause 500ms}} / {{pause 0.7s}} / {{pause.short}} / {{pause.long}}",
            "{{chapter \"...\"}} - named chapter markers in the project timeline",
        ),
        partial = listOf(
            "{{emotion ...}} - silently approximated via speed/pitch; no audible emotional tone",
            "{{delivery whisper|shout|soft|loud|slow|fast}} - approximated via volume + speed",
            "{{emphasis reduced|moderate|strong}} - slight pitch bump",
        ),
        ignored = listOf(
            "{{breath}} {{laugh}} {{sigh}} and other vocal reactions - stripped from text",
            "{{reset ...}} - accepted but ignored",
        ),
        examples = listOf(
            "{{voice \"af_sarah\"}} Hello there.",
            "{{emotion sad}}{{speed 0.9}}I have to tell you something.",
            "{{pause 700ms}}{{delivery whisper}}only us know about this.",
        ),
    )

    private val PIPER_TAGS = TagDocs(
        tagline = "Multilingual offline TTS. Expressive markup is mapped to speed and volume only - there are no native emotion/delivery controls.",
        supported = listOf(
            "{{voice \"...\"}} - pick by prefix from the installed speaker set",
            "{{lang ru-RU|en-US|en-GB|...}} - choose the language to match the speaker",
            "{{speed 0.5..2.0}} - real-time speed multiplier",
            "{{volume 0..4}} - gain",
            "{{pause 500ms}} / {{pause 0.7s}} / {{pause.short}} / {{pause.long}}",
            "{{chapter \"...\"}}",
        ),
        partial = listOf(
            "{{pitch 0.5..2.0}} - applied if the runtime supports it; otherwise no-op",
            "{{emotion ...}} / {{delivery ...}} - mapped to speed/pitch deltas",
        ),
        ignored = listOf(
            "Vocal reactions ({{breath}}, {{laugh}}, ...) are stripped from the spoken text",
        ),
        examples = listOf(
            "{{voice \"ru_RU-irina-medium\"}}{{lang ru-RU}}Privet, mir.",
            "{{speed 0.85}} medlenno i spokoino.",
        ),
    )

    private val OPENAI_TAGS = TagDocs(
        tagline = "OpenAI TTS. Emotion, delivery, emphasis and vocal reactions are written to the `instructions` parameter; the model improvises the performance.",
        supported = listOf(
            "{{emotion happy|sad|angry|afraid|excited|calm|...}} - native prompt",
            "{{delivery whisper|shout|soft|loud|slow|fast|narrator|conversational|...}} - native prompt",
            "{{emphasis reduced|moderate|strong}} - native prompt",
            "{{breath}} {{sigh}} {{laugh}} {{gasp}} - prompt-dependent, performed when model agrees",
            "{{speed 0.25..4.0}} - native speed multiplier",
            "{{pause 500ms}} / {{pause 0.7s}} / {{pause.short}} / {{pause.long}}",
            "{{voice \"alloy|echo|fable|onyx|nova|shimmer\"}} - built-in voices",
        ),
        ignored = listOf(
            "Reaction names that confuse the model are stripped from the transcript",
        ),
        examples = listOf(
            "{{emotion excited}}{{delivery fast}}This just happened!",
            "{{breath}}Before we begin, a word from our sponsor.",
        ),
        promptHelp = "Sent verbatim as `instructions`; tune it to taste, but keep it short.",
    )

    private val ELEVEN_TAGS = TagDocs(
        tagline = "ElevenLabs v3 understands square-bracket audio tags in the text. Other engines ignore them.",
        supported = listOf(
            "{{emotion sad|happy|angry|excited|...}} - emitted as [sad], [happy], ...",
            "{{delivery whisper|shout}} - emitted as [whispers], [shouts]",
            "{{breath}} {{sigh}} {{laugh}} {{chuckle}} {{giggle}} {{cry}} {{gasp}}",
            "{{speed 0.5..2.0}} - voice_settings",
            "{{pause 500ms}} / {{pause 0.7s}}",
            "{{voice \"<voice-id>\"}} - clone or stock voice id",
        ),
        ignored = listOf(
            "Tags other models understand ({{emphasis}}, {{reset}}, ...) are not translated; v3 reads raw markup if present",
        ),
        examples = listOf(
            "{{emotion sad}}[long pause] [breath] I never got to say goodbye.",
            "{{delivery whisper}}[whispers] [gasp] are we alone?",
        ),
        promptHelp = "Only square-bracket tags land inside the text; everything else is treated as the speech script.",
    )

    private val GEMINI_TAGS = TagDocs(
        tagline = "Gemini TTS receives the expressive markup as a natural-language direction prompt. The model interprets it freely.",
        supported = listOf(
            "{{emotion ...}} - prefix direction",
            "{{delivery ...}} {{emphasis ...}} - prefix direction",
            "{{breath}} {{sigh}} {{laugh}} - prompt-dependent",
            "{{speed 0.5..2.0}} - close to native; clamped server-side",
            "{{pause 500ms}} / {{pause 0.7s}} / {{pause.short}} / {{pause.long}}",
            "{{voice \"<voice-name>\"}} - Kore/Aoede/Leda/etc.",
        ),
        ignored = listOf(
            "Reaction tags are kept in the direction prompt but not necessarily performed by the model",
        ),
        examples = listOf(
            "{{emotion serious}}Read this announcement clearly and with gravitas.",
        ),
    )

    private val AZURE_TAGS = TagDocs(
        tagline = "Azure neural voices accept SSML. Only allowlisted express-as styles are passed; unknown values are dropped to keep the SSML valid.",
        supported = listOf(
            "{{lang en-US|ru-RU|de-DE|...}} - maps to xml:lang",
            "{{speed 0.5..2.0}} / {{volume 0..4}} / {{pitch 0.5..2.0}} - SSML prosody",
            "{{pause 500ms}} / {{pause 0.7s}}",
            "{{voice \"<voice-name>\"}} - swap voice (e.g. en-US-JennyNeural)",
            "{{emotion happy|sad|angry|afraid|excited|calm|friendly|hopeful|terrified|serious|empathetic}} - mapped to mstts:express-as",
            "{{delivery whisper|shout}} - mapped to mstts:express-as whispering/shouting",
        ),
        ignored = listOf(
            "{{delivery conversational|narrator|news|...}} - dropped from SSML",
            "Vocal reactions ({{breath}}, {{laugh}}, ...) - dropped",
        ),
        examples = listOf(
            "{{emotion cheerful}}Hello and welcome!",
            "{{delivery whisper}}[stage whisper] Stay close.",
        ),
        promptHelp = "Voice must be a neural voice that supports the chosen style; otherwise the request fails.",
    )

    private val STABLE_AUDIO_TAGS = TagDocs(
        tagline = "Text-to-music up to 11 seconds. The literal LTV tags do not apply - you describe the loop in plain English and the model composes it.",
        supported = listOf(
            "Free-text prompt: genre, mood, instrumentation, BPM, length",
            "{{duration 1..11}} - hard cap at 11 seconds",
        ),
        ignored = listOf(
            "{{emotion}} / {{delivery}} / vocal cues - music has no speech layer",
            "{{voice}} / {{lang}} - not applicable",
        ),
        examples = listOf(
            "warm ambient pad, 80 BPM, no percussion, 10 seconds",
            "tense cinematic strings with slow crescendo",
        ),
        promptHelp = "Be specific: instruments, tempo, mood, references. Vague prompts get generic results.",
    )

    private val STABLE_AUDIO_CLIP_TAGS = TagDocs(
        tagline = "Short sound effects up to 5 seconds. Plain English description only.",
        supported = listOf(
            "Free-text prompt: material, action, environment",
            "{{duration 1..5}} - hard cap at 5 seconds",
        ),
        ignored = listOf(
            "Speech tags are not relevant; describe the SFX itself",
        ),
        examples = listOf(
            "wooden door closing in a quiet hallway",
            "soft notification chime, two tones",
        ),
    )

    private val ELEVEN_SFX_TAGS = TagDocs(
        tagline = "ElevenLabs Sound Effects API. Plain-English prompt, duration 1-22 seconds.",
        supported = listOf(
            "Free-text prompt",
            "{{duration 1..22}} - seconds",
        ),
        ignored = listOf(
            "All speech-related tags - this API produces SFX, not voice",
        ),
        examples = listOf(
            "heavy wooden door closing, slow creak",
            "wind whoosh transition, 2 seconds",
        ),
    )

    private val BUNDLED_TAGS = TagDocs(
        tagline = "Offline placeholder that copies a short bundled WAV based on a keyword in the prompt. Useful for offline smoke-tests of the editor.",
        supported = listOf(
            "Keyword match in the prompt picks one of the placeholders (ambient, cinema, uplift for music; door, notification, whoosh for sound)",
        ),
        ignored = listOf(
            "Everything else - no real music/SFX generation",
        ),
        examples = listOf(
            "ambient background pad",
            "wooden door close sfx",
        ),
    )


        val entries: List<Entry> = listOf(
        Entry(
            id = "kokoro-82m",
            title = "Kokoro 82M",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech),
            requirements = Requirements(
                minimumRamMb = 2_048,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = true,
        ),
            tags = KOKORO_TAGS,
            support = Support.Verified,
            approximateDownloadBytes = 369_000_000,
            license = "Apache-2.0",
            repository = "csukuangfj/kokoro-onnx-v1.0",
            revision = null,
            notes = "English, 11 voices",
        ),
        Entry(
            id = "piper-vits",
            title = "Piper/VITS",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech),
            requirements = Requirements(
                minimumRamMb = 1_024,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = true,
        ),
            tags = PIPER_TAGS,
            support = Support.Verified,
            approximateDownloadBytes = 65_000_000,
            license = "Model-specific",
            repository = "k2-fsa/sherpa-onnx releases",
            revision = "tts-models",
            notes = "Each language and speaker is downloaded separately",
        ),
        Entry(
            id = "pocket-tts-int8",
            title = "PocketTTS INT8",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech, Capability.VoiceCloning),
            requirements = Requirements(
                minimumRamMb = 3_072,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = true,
        ),
            support = Support.RuntimeInDevelopment,
            approximateDownloadBytes = null,
            license = "Model-specific",
            repository = "k2-fsa/sherpa-onnx releases",
            revision = null,
            notes = "Do not enable before upgrading and smoke-testing the Android runtime",
        ),
        Entry(
            id = "zipvoice-distill-int8",
            title = "ZipVoice Distill INT8",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech, Capability.VoiceCloning),
            requirements = Requirements(
                minimumRamMb = 4_096,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = true,
        ),
            tags = PIPER_TAGS,
            support = Support.Experimental,
            approximateDownloadBytes = null,
            license = "Model-specific",
            repository = "k2-fsa/sherpa-onnx releases",
            revision = null,
            notes = "Requires reference WAV and transcript; Android device test pending",
        ),
        Entry(
            id = "stable-audio-open-small",
            title = "Stable Audio Open Small",
            categories = setOf(Category.Music, Category.Sound),
            capabilities = setOf(Capability.MusicGeneration, Capability.SoundGeneration),
            requirements = Requirements(
                minimumRamMb = 6_144,
                runtime = Runtime.LiteRt,
                runtimeBundled = false,
        ),
            tags = STABLE_AUDIO_TAGS,
            support = Support.Verified,
            approximateDownloadBytes = 604_000_000,
            license = "Stability AI Community License",
            repository = "stabilityai/stable-audio-open-small",
            revision = null,
            notes = "One installation serves both music and sound tabs; up to 11 seconds",
        ),
        Entry(
            id = "stable-audio-clip",
            title = "Stable Audio Clip",
            categories = setOf(Category.Sound),
            capabilities = setOf(Capability.SoundGeneration),
            requirements = Requirements(
                minimumRamMb = 4_096,
                runtime = Runtime.LiteRt,
                runtimeBundled = false,
        ),
            tags = STABLE_AUDIO_CLIP_TAGS,
            support = Support.Verified,
            approximateDownloadBytes = 96_000_000,
            license = "Stability AI Community License",
            repository = "stabilityai/stable-audio-open-small",
            revision = null,
            notes = "Single-file LiteRT variant for short sound effects",
        ),
    )


    /**
     * Lookup for non-catalog generators (Bundled/ElevenLabs SFX). The map is
     * keyed by generator id so the UI can resolve a "selected music/sound
     * generator" choice back to a TagDocs block.
     */
    private val GENERATOR_TAGS: Map<String, TagDocs> = mapOf(
        "bundled.music" to BUNDLED_TAGS,
        "bundled.sound" to BUNDLED_TAGS,
        "elevenlabs.sound" to ELEVEN_SFX_TAGS,
        "litert.stable-audio-open-small.music" to STABLE_AUDIO_TAGS,
        "litert.stable-audio-clip.sound" to STABLE_AUDIO_CLIP_TAGS,
    )

    /** Returns the TagDocs for a catalog model id, or null if not documented. */
    fun tagDocsFor(modelId: String): TagDocs? =
        entries.firstOrNull { it.id == modelId }?.tags

    /** Returns the TagDocs for a generator id (Bundled, cloud SFX, LiteRT). */
    fun tagDocsForGenerator(generatorId: String): TagDocs? =
        GENERATOR_TAGS[generatorId]


    /**
     * Lookup for cloud TTS engines (whose EngineInfo lives in TtsEngine, not
     * in [entries]). Keyed by engine id.
     */
    private val ENGINE_TAGS: Map<String, TagDocs> = mapOf(
        "openai" to OPENAI_TAGS,
        "elevenlabs" to ELEVEN_TAGS,
        "gemini" to GEMINI_TAGS,
        "azure" to AZURE_TAGS,
        "custom_http" to BUNDLED_TAGS, // best-effort fallback for user-defined engines
    )

    /** Returns the TagDocs for a TTS engine id (cloud or custom). */
    fun tagDocsForEngine(engineId: String): TagDocs? = ENGINE_TAGS[engineId]

    fun forCategory(category: Category): List<Entry> =
        entries.filter { category in it.categories }

    fun requiredRuntime(modelId: String): Runtime? =
        entries.firstOrNull { it.id == modelId }?.requirements?.runtime
}
