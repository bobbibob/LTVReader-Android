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
    ) {
        val canInstall: Boolean
            get() = support == Support.Verified
    }

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
            support = Support.RuntimeInDevelopment,
            approximateDownloadBytes = null,
            license = "Stability AI Community License",
            repository = "stabilityai/stable-audio-open-small",
            revision = null,
            notes = "One installation serves both music and sound tabs; up to 11 seconds",
        ),
    )

    fun forCategory(category: Category): List<Entry> =
        entries.filter { category in it.categories }

    fun requiredRuntime(modelId: String): Runtime? =
        entries.firstOrNull { it.id == modelId }?.requirements?.runtime
}
