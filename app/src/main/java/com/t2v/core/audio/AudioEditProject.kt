package com.t2v.core.audio

import java.util.UUID

enum class AudioTrackKind { Voice, Music }

data class AudioEditClip(
    val id: String = UUID.randomUUID().toString(),
    val sourcePath: String,
    val startMs: Long = 0,
    /** Zero means until the end of the source. */
    val endMs: Long = 0,
    val speed: Double = 1.0,
)

data class AudioEditProject(
    val voiceClips: List<AudioEditClip> = emptyList(),
    val musicClips: List<AudioEditClip> = emptyList(),
)
