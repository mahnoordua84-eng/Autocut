package com.example.domain.model

import java.util.UUID
import kotlin.math.roundToLong

/**
 * Rational timebase representing exact frame rates without floating point rounding error.
 * e.g., 30 fps = 30/1, 24 fps = 24/1, 29.97 fps = 30000/1001, 23.976 fps = 24000/1001.
 */
data class Timebase(
  val numerator: Long = 30L,
  val denominator: Long = 1L
) {
  val fpsDouble: Double
    get() = if (denominator > 0L) numerator.toDouble() / denominator.toDouble() else 30.0

  val fps: Double get() = fpsDouble

  val frameDurationUs: Long
    get() = if (numerator > 0L) (1_000_000L * denominator) / numerator else 33_333L

  val frameDurationMs: Long
    get() = (frameDurationUs / 1000L).coerceAtLeast(1L)

  fun frameToUs(frame: Long): Long {
    return if (numerator > 0L) (frame * 1_000_000L * denominator) / numerator else frame * 33_333L
  }

  fun usToFrame(us: Long): Long {
    return if (denominator > 0L) (us * numerator) / (1_000_000L * denominator) else us / 33_333L
  }

  fun microsToFrames(micros: Long): Long = usToFrame(micros)
  fun framesToMicros(frames: Long): Long = frameToUs(frames)

  fun snapUsToFrame(us: Long): Long {
    val frame = usToFrame(us)
    return frameToUs(frame)
  }

  companion object {
    val FPS_24 = Timebase(24, 1)
    val FPS_25 = Timebase(25, 1)
    val FPS_30 = Timebase(30, 1)
    val FPS_50 = Timebase(50, 1)
    val FPS_60 = Timebase(60, 1)
    val NTSC_29_97 = Timebase(30000, 1001)
    val FPS_29_97 = NTSC_29_97
    val NTSC_23_976 = Timebase(24000, 1001)

    fun fromFps(fps: Int): Timebase = when (fps) {
      24 -> FPS_24
      25 -> FPS_25
      50 -> FPS_50
      60 -> FPS_60
      else -> FPS_30
    }

    fun fromFrameRate(frameRate: FrameRate): Timebase = fromFps(frameRate.fps)
  }
}

/**
 * High-precision timeline timestamp utilities (Microsecond / Timebase precision).
 */
object TimecodeUtils {
  const val MICROS_PER_SECOND = 1_000_000L
  const val MICROS_PER_MILLI = 1_000L

  fun msToUs(ms: Long): Long = ms * MICROS_PER_MILLI
  fun usToMs(us: Long): Long = us / MICROS_PER_MILLI

  fun secondsToUs(seconds: Double): Long = (seconds * MICROS_PER_SECOND.toDouble()).roundToLong()
  fun usToSeconds(us: Long): Double = us.toDouble() / MICROS_PER_SECOND.toDouble()

  /**
   * Formats a microsecond timestamp into SMPTE standard timecode: HH:MM:SS:FF
   */
  fun formatSmpteTimecode(us: Long, timebase: Timebase = Timebase.FPS_30): String {
    val safeUs = us.coerceAtLeast(0L)
    val totalSeconds = safeUs / MICROS_PER_SECOND
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val remainingUs = safeUs % MICROS_PER_SECOND
    val frame = timebase.usToFrame(remainingUs) % timebase.numerator.coerceAtLeast(1L)
    return String.format("%02d:%02d:%02d:%02d", hours, minutes, seconds, frame)
  }

  fun formatSMPTE(us: Long, timebase: Timebase = Timebase.FPS_30): String = formatSmpteTimecode(us, timebase)

  /**
   * Formats a microsecond timestamp into user-friendly timeline ruler string: MM:SS.mmm
   */
  fun formatTimelineRuler(us: Long): String {
    val safeUs = us.coerceAtLeast(0L)
    val totalMs = safeUs / 1000L
    val minutes = totalMs / 60000
    val seconds = (totalMs % 60000) / 1000
    val millis = totalMs % 1000
    return String.format("%02d:%02d.%03d", minutes, seconds, millis)
  }

  /**
   * Parses standard SMPTE timecode (HH:MM:SS:FF or MM:SS:FF) into microseconds.
   */
  fun parseSmpteTimecode(timecode: String, timebase: Timebase = Timebase.FPS_30): Long {
    val parts = timecode.split(":").mapNotNull { it.trim().toLongOrNull() }
    return when (parts.size) {
      4 -> {
        val (h, m, s, f) = parts
        val secUs = (h * 3600 + m * 60 + s) * MICROS_PER_SECOND
        val frameUs = timebase.frameToUs(f)
        secUs + frameUs
      }
      3 -> {
        val (m, s, f) = parts
        val secUs = (m * 60 + s) * MICROS_PER_SECOND
        val frameUs = timebase.frameToUs(f)
        secUs + frameUs
      }
      2 -> {
        val (s, f) = parts
        val secUs = s * MICROS_PER_SECOND
        val frameUs = timebase.frameToUs(f)
        secUs + frameUs
      }
      else -> 0L
    }
  }

  fun parseSMPTE(timecode: String, timebase: Timebase = Timebase.FPS_30): Long? {
    if (!timecode.matches(Regex("""\d{2}:\d{2}:\d{2}:\d{2}""")) && !timecode.matches(Regex("""\d{2}:\d{2}:\d{2}"""))) {
      return null
    }
    return parseSmpteTimecode(timecode, timebase)
  }
}

/**
 * Distinct Time Domain value types to strictly prevent accidental mixing of
 * Source Time, Timeline Time, and Playback Time.
 */
@JvmInline
value class SourceMediaTime(val micros: Long) {
  val millis: Long get() = micros / 1000L
  val seconds: Double get() = micros.toDouble() / 1_000_000.0
}

@JvmInline
value class TimelineTime(val micros: Long) {
  val millis: Long get() = micros / 1000L
  val seconds: Double get() = micros.toDouble() / 1_000_000.0
}

@JvmInline
value class PlaybackTime(val micros: Long) {
  val millis: Long get() = micros / 1000L
  val seconds: Double get() = micros.toDouble() / 1_000_000.0
}

/**
 * Supported Track Kinds as requested:
 * VIDEO, AUDIO, TEXT, IMAGE, OVERLAY, EFFECT, ADJUSTMENT
 */
enum class TrackKind(val displayName: String, val category: String) {
  VIDEO("Video Track", "Visual"),
  AUDIO("Audio Track", "Audio"),
  TEXT("Text / Caption Track", "Typography"),
  IMAGE("Image Track", "Visual"),
  OVERLAY("Overlay / PiP Track", "Visual"),
  EFFECT("Effect Track", "FX"),
  ADJUSTMENT("Adjustment Track", "Color & Grade")
}

/**
 * Video / Timeline resolution specification.
 */
data class TimelineResolution(
  val width: Int = 1080,
  val height: Int = 1920,
  val label: String = "1080x1920"
) {
  val aspectRatioFloat: Float get() = if (height > 0) width.toFloat() / height.toFloat() else 9f / 16f

  companion object {
    val HD_720P = TimelineResolution(720, 1280, "720p HD")
    val FULL_HD_1080P = TimelineResolution(1080, 1920, "1080p Full HD")
    val LANDSCAPE_1080P = TimelineResolution(1920, 1080, "1080p Landscape")
    val QHD_2K = TimelineResolution(1440, 2560, "2K QHD")
    val UHD_4K = TimelineResolution(2160, 3840, "4K UHD")
    val SQUARE_1080 = TimelineResolution(1080, 1080, "Square 1:1")

    fun fromResolution(res: Resolution): TimelineResolution {
      return TimelineResolution(res.width, res.height, res.label)
    }
  }
}

/**
 * Text-specific payload for TEXT track clips.
 */
data class TextPayload(
  val text: String = "Tap to edit",
  val fontFamily: String = "Default",
  val customFontPath: String? = null,
  val fontSizeSp: Float = 24f,
  val fontWeight: Int = 700,
  val isItalic: Boolean = false,
  val isUnderline: Boolean = false,
  val isAllCaps: Boolean = false,
  val alignment: String = "Center",
  val letterSpacing: Float = 0f,
  val lineSpacing: Float = 1.0f,
  val textColor: Long = 0xFFFFFFFF,
  val hasGradient: Boolean = false,
  val gradientColorStart: Long = 0xFF00E5FF,
  val gradientColorEnd: Long = 0xFF8B5CF6,
  val gradientDirection: String = "Horizontal",
  val strokeWidth: Float = 0f,
  val strokeColor: Long = 0xFF000000,
  val hasShadow: Boolean = false,
  val shadowColor: Long = 0x88000000,
  val shadowBlur: Float = 4f,
  val shadowOffsetX: Float = 2f,
  val shadowOffsetY: Float = 2f,
  val hasBackground: Boolean = false,
  val backgroundColor: Long = 0xAA000000,
  val cornerRadius: Float = 12f,
  val bgPadding: Float = 16f,
  val animationType: String = "Fade",
  val animDurationMs: Long = 400L,
  val subtitleStyle: String = "Classic",
  val highlightColor: Long = 0xFFFFEB3B,
  val words: List<WordTiming> = emptyList()
)

/**
 * Effect-specific payload for EFFECT and ADJUSTMENT track clips.
 */
data class EffectPayload(
  val effectType: EffectType = EffectType.GLOW,
  val intensity: Float = 0.8f,
  val effectCategory: String = "Video Effects",
  val targetClipId: String? = null,
  val customParams: Map<String, Float> = emptyMap()
)

/**
 * Sticker/Element payload for sticker/graphics clips.
 */
data class StickerPayload(
  val emojiOrAsset: String = "🎬",
  val category: String = "Emoji & Emotions",
  val elementId: String? = null,
  val customColor: Long? = null,
  val animationType: StickerAnimationType = StickerAnimationType.NONE,
  val badgeType: BadgeType? = null
)

/**
 * Alignment of a transition relative to the edit point between two clips.
 */
enum class TransitionAlignment {
  START_AT_CUT, // Transition starts at cut point (on the incoming clip)
  CENTER,       // Transition centered across cut point (spans outgoing and incoming)
  END_AT_CUT    // Transition ends at cut point (on outgoing clip)
}

/**
 * Professional Timeline Transition model with microsecond precision.
 */
data class TimelineTransition(
  val id: String = UUID.randomUUID().toString(),
  val fromClipId: String? = null,
  val toClipId: String? = null,
  val type: TransitionType = TransitionType.FADE,
  val durationUs: Long = 500_000L,
  val alignment: TransitionAlignment = TransitionAlignment.CENTER
) {
  val durationMs: Long get() = durationUs / 1000L
}

/**
 * Complete, robust Non-Linear Timeline Clip model.
 *
 * Supports:
 * - unique ID
 * - source media ID
 * - timeline start (in microseconds)
 * - timeline duration (in microseconds)
 * - source in/out points (in microseconds)
 * - layer / track
 * - speed
 * - volume
 * - transform (2D position, scale, rotation, crop, flip, zIndex)
 * - visibility
 * - enabled / disabled
 * - opacity
 * - metadata (extensible key-value pairs)
 *
 * Plus:
 * - keyframes
 * - speed curves
 * - audio effects
 * - transitions
 * - nested timeline (for compound clips)
 */
data class TimelineClip(
  val id: String = UUID.randomUUID().toString(),
  val sourceMediaId: String = "",
  val name: String = "",
  val trackId: String = "",
  val layerIndex: Int = 0,
  val kind: TrackKind = TrackKind.VIDEO,
  val timelineStartUs: Long = 0L,
  val timelineDurationUs: Long = 3_000_000L,
  val sourceInUs: Long = 0L,
  val sourceOutUs: Long = 3_000_000L,
  val speed: Double = 1.0,
  val volume: Float = 1.0f,
  val transform: NormalizedTransform = NormalizedTransform(),
  val isVisible: Boolean = true,
  val isEnabled: Boolean = true,
  val opacity: Float = 1.0f,
  val metadata: Map<String, String> = emptyMap(),
  val blendMode: String = "Normal",
  val isLocked: Boolean = false,
  val isMuted: Boolean = false,
  val isReversed: Boolean = false,
  val freezeFrameAtUs: Long? = null,
  val keyframes: List<ClipKeyframe> = emptyList(),
  val speedCurve: SpeedCurve = SpeedCurve(),
  val audioEffects: AudioEffectsSettings = AudioEffectsSettings(),
  val filter: FilterSettings? = null,
  val mask: MaskSettings = MaskSettings(),
  val animation: ClipAnimationSettings = ClipAnimationSettings(),
  val transitionIn: TimelineTransition? = null,
  val transitionOut: TimelineTransition? = null,
  val nestedTimeline: CoreTimelineState? = null,
  val textPayload: TextPayload? = null,
  val effectPayload: EffectPayload? = null,
  val stickerPayload: StickerPayload? = null
) {
  // Convenient time accessors
  val timelineEndUs: Long get() = timelineStartUs + timelineDurationUs
  val timelineStartMs: Long get() = timelineStartUs / 1000L
  val timelineEndMs: Long get() = timelineEndUs / 1000L
  val durationMs: Long get() = timelineDurationUs / 1000L
  val sourceStartMs: Long get() = sourceInUs / 1000L
  val sourceEndMs: Long get() = sourceOutUs / 1000L
  val sourceDurationUs: Long get() = (sourceOutUs - sourceInUs).coerceAtLeast(0L)

  /**
   * Check whether this clip overlaps or intersects another clip in timeline time.
   */
  fun intersects(other: TimelineClip): Boolean {
    return this.timelineStartUs < other.timelineEndUs && other.timelineStartUs < this.timelineEndUs
  }

  /**
   * Check whether this clip contains a given timeline position (in microseconds).
   */
  fun containsTimelineTime(timelineUs: Long): Boolean {
    return timelineUs >= timelineStartUs && timelineUs < timelineEndUs
  }
}

/**
 * Represents an empty gap between clips on a track.
 */
data class TimelineGap(
  val trackId: String,
  val startUs: Long,
  val durationUs: Long
) {
  val endUs: Long get() = startUs + durationUs
  val startMs: Long get() = startUs / 1000L
  val durationMs: Long get() = durationUs / 1000L
}

/**
 * Professional Timeline Track model supporting arbitrary tracks:
 * VIDEO, AUDIO, TEXT, IMAGE, OVERLAY, EFFECT, ADJUSTMENT.
 */
data class TimelineTrack(
  val id: String = UUID.randomUUID().toString(),
  val name: String = "",
  val kind: TrackKind = TrackKind.VIDEO,
  val orderIndex: Int = 0,
  val isLocked: Boolean = false,
  val isHidden: Boolean = false,
  val isMuted: Boolean = false,
  val isSolo: Boolean = false,
  val height: TrackHeight = TrackHeight.NORMAL,
  val volume: Float = 1.0f,
  val pan: Float = 0.0f, // -1.0f (left) to +1.0f (right)
  val clips: List<TimelineClip> = emptyList(),
  val metadata: Map<String, String> = emptyMap()
) {
  val durationUs: Long
    get() = clips.maxOfOrNull { it.timelineEndUs } ?: 0L

  val durationMs: Long
    get() = durationUs / 1000L

  /**
   * Retrieve all clips active at the given timeline timestamp.
   * Multiple clips may be returned if overlapping clips exist on this track.
   */
  fun getClipsAt(timelineUs: Long): List<TimelineClip> {
    if (isHidden) return emptyList()
    return clips.filter { it.isEnabled && it.containsTimelineTime(timelineUs) }
  }

  /**
   * Identifies all overlapping clip pairs on this track.
   */
  fun findOverlaps(): List<Pair<TimelineClip, TimelineClip>> {
    val overlaps = mutableListOf<Pair<TimelineClip, TimelineClip>>()
    val sorted = clips.sortedBy { it.timelineStartUs }
    for (i in 0 until sorted.size) {
      for (j in (i + 1) until sorted.size) {
        val a = sorted[i]
        val b = sorted[j]
        if (a.intersects(b)) {
          overlaps.add(a to b)
        } else if (b.timelineStartUs >= a.timelineEndUs) {
          break
        }
      }
    }
    return overlaps
  }

  /**
   * Finds all empty gaps between clips on this track up to the provided total duration.
   */
  fun findGaps(totalDurationUs: Long = this.durationUs): List<TimelineGap> {
    if (clips.isEmpty()) {
      return if (totalDurationUs > 0L) {
        listOf(TimelineGap(trackId = id, startUs = 0L, durationUs = totalDurationUs))
      } else emptyList()
    }

    val gaps = mutableListOf<TimelineGap>()
    val sorted = clips.sortedBy { it.timelineStartUs }

    var currentHeadUs = 0L
    for (clip in sorted) {
      if (clip.timelineStartUs > currentHeadUs) {
        gaps.add(
          TimelineGap(
            trackId = id,
            startUs = currentHeadUs,
            durationUs = clip.timelineStartUs - currentHeadUs
          )
        )
      }
      currentHeadUs = maxOf(currentHeadUs, clip.timelineEndUs)
    }

    if (totalDurationUs > currentHeadUs) {
      gaps.add(
        TimelineGap(
          trackId = id,
          startUs = currentHeadUs,
          durationUs = totalDurationUs - currentHeadUs
        )
      )
    }

    return gaps
  }
}

/**
 * Master Non-Linear Core Timeline State.
 *
 * Encapsulates:
 * - Timeline duration (microseconds)
 * - Current playhead / CTI position (microseconds)
 * - Project FPS / timebase (rational Timebase)
 * - Timeline resolution (width x height)
 * - Zoom / scale
 * - Track ordering (ordered track IDs and tracks)
 * - Multiple tracks with overlapping clips & gaps support
 * - Transitions
 * - Keyframes & Effects
 * - Nested / compound clip readiness
 */
data class CoreTimelineState(
  val durationUs: Long = 0L,
  val playheadPositionUs: Long = 0L,
  val timebase: Timebase = Timebase.FPS_30,
  val fps: FrameRate = FrameRate.FPS_30,
  val resolution: TimelineResolution = TimelineResolution.FULL_HD_1080P,
  val aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
  val zoomScale: Float = 1.0f,
  val tracks: List<TimelineTrack> = emptyList(),
  val trackOrder: List<String> = emptyList(), // Explicit list of track IDs in display order
  val transitions: List<TimelineTransition> = emptyList(),
  val adjustments: VideoAdjustments = VideoAdjustments(),
  val filter: FilterSettings = FilterSettings(),
  val chromaKey: ChromaKeySettings = ChromaKeySettings(),
  val canvasBackgroundColor: Long = 0xFF000000,
  val metadata: Map<String, String> = emptyMap()
) {
  val durationMs: Long get() = durationUs / 1000L
  val playheadPositionMs: Long get() = playheadPositionUs / 1000L

  /**
   * Tracks returned in guaranteed ordered sequence.
   */
  fun orderedTracks(): List<TimelineTrack> {
    if (trackOrder.isEmpty()) {
      return tracks.sortedBy { it.orderIndex }
    }
    val trackMap = tracks.associateBy { it.id }
    val ordered = trackOrder.mapNotNull { trackMap[it] }
    val remaining = tracks.filter { it.id !in trackOrder }.sortedBy { it.orderIndex }
    return ordered + remaining
  }

  /**
   * Flattened list of all clips across all tracks.
   */
  fun allClips(): List<TimelineClip> = tracks.flatMap { it.clips }

  /**
   * Look up a clip by its unique ID.
   */
  fun findClip(clipId: String): TimelineClip? {
    return allClips().firstOrNull { it.id == clipId }
  }

  /**
   * Look up which track contains a clip by clip ID.
   */
  fun findTrackForClip(clipId: String): TimelineTrack? {
    return tracks.firstOrNull { track -> track.clips.any { it.id == clipId } }
  }

  /**
   * Look up track by track ID.
   */
  fun findTrackById(trackId: String): TimelineTrack? {
    return tracks.firstOrNull { it.id == trackId }
  }

  fun getTrackById(trackId: String): TimelineTrack? = findTrackById(trackId)

  fun reorderTracks(newOrder: List<String>): CoreTimelineState {
    val trackMap = tracks.associateBy { it.id }
    val ordered = newOrder.mapNotNull { trackMap[it] }.mapIndexed { idx, t -> t.copy(orderIndex = idx) }
    val remaining = tracks.filter { it.id !in newOrder }.mapIndexed { idx, t -> t.copy(orderIndex = ordered.size + idx) }
    val combined = ordered + remaining
    return copy(tracks = combined, trackOrder = combined.map { it.id })
  }

  /**
   * Returns all active clips at current playhead position.
   */
  fun getClipsAtPlayhead(): List<TimelineClip> {
    return tracks.flatMap { it.getClipsAt(playheadPositionUs) }
  }

  /**
   * Returns all empty gaps across all tracks up to timeline duration.
   */
  fun findAllGaps(): List<TimelineGap> {
    return tracks.flatMap { it.findGaps(durationUs) }
  }

  /**
   * Computes the calculated duration from all clips across all tracks.
   */
  fun computeCalculatedDurationUs(): Long {
    return tracks.maxOfOrNull { it.durationUs } ?: 0L
  }

  /**
   * Returns a copy with the updated clip replaced on its respective track.
   */
  fun withClipUpdated(updatedClip: TimelineClip): CoreTimelineState {
    val newTracks = tracks.map { track ->
      if (track.id == updatedClip.trackId || track.clips.any { it.id == updatedClip.id }) {
        val newClips = track.clips.map { if (it.id == updatedClip.id) updatedClip else it }
        track.copy(clips = newClips)
      } else {
        track
      }
    }
    val newDurationUs = maxOf(durationUs, newTracks.maxOfOrNull { it.durationUs } ?: 0L)
    return copy(tracks = newTracks, durationUs = newDurationUs)
  }

  /**
   * Returns a copy with track ordering updated.
   */
  fun withTrackReordered(trackId: String, newIndex: Int): CoreTimelineState {
    val currentOrder = orderedTracks().map { it.id }.toMutableList()
    currentOrder.remove(trackId)
    val safeIndex = newIndex.coerceIn(0, currentOrder.size)
    currentOrder.add(safeIndex, trackId)

    val newTracks = tracks.map { track ->
      val idx = currentOrder.indexOf(track.id)
      track.copy(orderIndex = if (idx >= 0) idx else track.orderIndex)
    }
    return copy(tracks = newTracks, trackOrder = currentOrder)
  }
}
