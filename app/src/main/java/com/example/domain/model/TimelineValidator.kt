package com.example.domain.model

import kotlin.math.max

enum class ValidationSeverity {
  ERROR,
  WARNING
}

data class ValidationError(
  val clipId: String? = null,
  val trackId: String? = null,
  val field: String,
  val message: String,
  val severity: ValidationSeverity = ValidationSeverity.ERROR
)

data class TimelineValidationReport(
  val isValid: Boolean,
  val errors: List<ValidationError>,
  val warnings: List<ValidationError>
) {
  val hasErrors: Boolean get() = errors.isNotEmpty()
  val hasWarnings: Boolean get() = warnings.isNotEmpty()

  companion object {
    val VALID = TimelineValidationReport(isValid = true, errors = emptyList(), warnings = emptyList())
  }
}

/**
 * Validation Engine preventing invalid clips, corrupt timestamps, or malformed data
 * from crashing or corrupting the project.
 */
object TimelineValidator {

  const val MIN_CLIP_DURATION_US = 33_333L // ~1 frame at 30fps
  const val MAX_TIMELINE_DURATION_US = 86_400_000_000L // 24 hours in microseconds

  /**
   * Validates a CoreTimelineState and returns an exhaustive report.
   */
  fun validateTimeline(timeline: CoreTimelineState): TimelineValidationReport {
    val errors = mutableListOf<ValidationError>()
    val warnings = mutableListOf<ValidationError>()

    // 1. Timeline Duration
    if (timeline.durationUs < 0L) {
      errors.add(
        ValidationError(
          field = "durationUs",
          message = "Timeline duration cannot be negative (${timeline.durationUs}us)",
          severity = ValidationSeverity.ERROR
        )
      )
    } else if (timeline.durationUs > MAX_TIMELINE_DURATION_US) {
      warnings.add(
        ValidationError(
          field = "durationUs",
          message = "Timeline duration exceeds 24 hours (${timeline.durationUs}us)",
          severity = ValidationSeverity.WARNING
        )
      )
    }

    // 2. Playhead Position
    if (timeline.playheadPositionUs < 0L) {
      errors.add(
        ValidationError(
          field = "playheadPositionUs",
          message = "Playhead position cannot be negative (${timeline.playheadPositionUs}us)",
          severity = ValidationSeverity.ERROR
        )
      )
    }

    // 3. Timebase & FPS
    if (timeline.timebase.numerator <= 0L || timeline.timebase.denominator <= 0L) {
      errors.add(
        ValidationError(
          field = "timebase",
          message = "Invalid rational timebase: ${timeline.timebase.numerator}/${timeline.timebase.denominator}",
          severity = ValidationSeverity.ERROR
        )
      )
    }

    // 4. Resolution
    if (timeline.resolution.width <= 0 || timeline.resolution.height <= 0) {
      errors.add(
        ValidationError(
          field = "resolution",
          message = "Resolution width and height must be positive: ${timeline.resolution.width}x${timeline.resolution.height}",
          severity = ValidationSeverity.ERROR
        )
      )
    }

    // 5. Tracks
    val trackIds = mutableSetOf<String>()
    for (track in timeline.tracks) {
      if (track.id.isBlank()) {
        errors.add(
          ValidationError(
            trackId = track.id,
            field = "id",
            message = "Track ID cannot be blank",
            severity = ValidationSeverity.ERROR
          )
        )
      } else if (!trackIds.add(track.id)) {
        errors.add(
          ValidationError(
            trackId = track.id,
            field = "id",
            message = "Duplicate track ID found: ${track.id}",
            severity = ValidationSeverity.ERROR
          )
        )
      }

      // Validate Track Clips
      val clipIds = mutableSetOf<String>()
      for (clip in track.clips) {
        val clipErrors = validateClip(clip)
        errors.addAll(clipErrors.filter { it.severity == ValidationSeverity.ERROR })
        warnings.addAll(clipErrors.filter { it.severity == ValidationSeverity.WARNING })

        if (clip.id.isNotBlank() && !clipIds.add(clip.id)) {
          errors.add(
            ValidationError(
              clipId = clip.id,
              trackId = track.id,
              field = "id",
              message = "Duplicate clip ID in track: ${clip.id}",
              severity = ValidationSeverity.ERROR
            )
          )
        }
      }
    }

    // 6. Transitions
    for (transition in timeline.transitions) {
      if (transition.durationUs <= 0L) {
        errors.add(
          ValidationError(
            clipId = transition.id,
            field = "transition.durationUs",
            message = "Transition duration must be greater than 0",
            severity = ValidationSeverity.ERROR
          )
        )
      }
    }

    return TimelineValidationReport(
      isValid = errors.isEmpty(),
      errors = errors,
      warnings = warnings
    )
  }

  /**
   * Validates an individual clip.
   */
  fun validateClip(clip: TimelineClip): List<ValidationError> {
    val results = mutableListOf<ValidationError>()

    if (clip.id.isBlank()) {
      results.add(
        ValidationError(
          clipId = clip.id,
          field = "id",
          message = "Clip ID cannot be blank",
          severity = ValidationSeverity.ERROR
        )
      )
    }

    if (clip.timelineStartUs < 0L) {
      results.add(
        ValidationError(
          clipId = clip.id,
          field = "timelineStartUs",
          message = "Timeline start timestamp cannot be negative (${clip.timelineStartUs}us)",
          severity = ValidationSeverity.ERROR
        )
      )
    }

    if (clip.timelineDurationUs < MIN_CLIP_DURATION_US) {
      results.add(
        ValidationError(
          clipId = clip.id,
          field = "timelineDurationUs",
          message = "Clip duration must be at least $MIN_CLIP_DURATION_US us (${clip.timelineDurationUs}us)",
          severity = ValidationSeverity.ERROR
        )
      )
    }

    if (clip.sourceInUs < 0L) {
      results.add(
        ValidationError(
          clipId = clip.id,
          field = "sourceInUs",
          message = "Source in point cannot be negative (${clip.sourceInUs}us)",
          severity = ValidationSeverity.ERROR
        )
      )
    }

    if (clip.sourceOutUs <= clip.sourceInUs) {
      results.add(
        ValidationError(
          clipId = clip.id,
          field = "sourceOutUs",
          message = "Source out point (${clip.sourceOutUs}us) must be strictly greater than source in point (${clip.sourceInUs}us)",
          severity = ValidationSeverity.ERROR
        )
      )
    }

    if (!clip.speed.isFinite() || clip.speed <= 0.001 || clip.speed > 100.0) {
      results.add(
        ValidationError(
          clipId = clip.id,
          field = "speed",
          message = "Clip speed must be finite and between 0.001 and 100.0 (${clip.speed})",
          severity = ValidationSeverity.ERROR
        )
      )
    }

    if (!clip.volume.isFinite() || clip.volume < 0f || clip.volume > 10f) {
      results.add(
        ValidationError(
          clipId = clip.id,
          field = "volume",
          message = "Clip volume must be between 0.0 and 10.0 (${clip.volume})",
          severity = ValidationSeverity.ERROR
        )
      )
    }

    if (!clip.opacity.isFinite() || clip.opacity < 0f || clip.opacity > 1f) {
      results.add(
        ValidationError(
          clipId = clip.id,
          field = "opacity",
          message = "Clip opacity must be between 0.0 and 1.0 (${clip.opacity})",
          severity = ValidationSeverity.ERROR
        )
      )
    }

    // Keyframes
    var lastKeyframeTime = -1L
    for (kf in clip.keyframes) {
      if (kf.timeMs < 0L || kf.timeMs > clip.durationMs) {
        results.add(
          ValidationError(
            clipId = clip.id,
            field = "keyframes",
            message = "Keyframe at ${kf.timeMs}ms is outside clip duration (0..${clip.durationMs}ms)",
            severity = ValidationSeverity.WARNING
          )
        )
      }
      if (kf.timeMs < lastKeyframeTime) {
        results.add(
          ValidationError(
            clipId = clip.id,
            field = "keyframes",
            message = "Keyframes are not sorted in non-decreasing order: ${kf.timeMs}ms after ${lastKeyframeTime}ms",
            severity = ValidationSeverity.ERROR
          )
        )
      }
      lastKeyframeTime = kf.timeMs
    }

    return results
  }

  /**
   * Sanitizes and repairs a clip, producing a 100% valid instance.
   */
  fun sanitizeClip(clip: TimelineClip): TimelineClip {
    val safeStartUs = clip.timelineStartUs.coerceAtLeast(0L)
    val safeDurationUs = clip.timelineDurationUs.coerceAtLeast(MIN_CLIP_DURATION_US)
    val safeSourceInUs = clip.sourceInUs.coerceAtLeast(0L)
    val safeSourceOutUs = max(safeSourceInUs + MIN_CLIP_DURATION_US, clip.sourceOutUs)

    val safeSpeed = if (clip.speed.isFinite() && clip.speed > 0.001) {
      clip.speed.coerceIn(0.001, 100.0)
    } else 1.0

    val safeVolume = if (clip.volume.isFinite() && clip.volume >= 0f) {
      clip.volume.coerceIn(0f, 10f)
    } else 1.0f

    val safeOpacity = if (clip.opacity.isFinite()) {
      clip.opacity.coerceIn(0f, 1f)
    } else 1.0f

    val safeKeyframes = clip.keyframes
      .map { kf ->
        kf.copy(
          timeMs = kf.timeMs.coerceIn(0L, safeDurationUs / 1000L),
          opacity = if (kf.opacity.isFinite()) kf.opacity.coerceIn(0f, 1f) else 1f,
          volume = if (kf.volume.isFinite()) kf.volume.coerceIn(0f, 10f) else 1f,
          scaleX = if (kf.scaleX.isFinite()) kf.scaleX.coerceIn(0.01f, 20f) else 1f,
          scaleY = if (kf.scaleY.isFinite()) kf.scaleY.coerceIn(0.01f, 20f) else 1f
        )
      }
      .sortedBy { it.timeMs }

    return clip.copy(
      timelineStartUs = safeStartUs,
      timelineDurationUs = safeDurationUs,
      sourceInUs = safeSourceInUs,
      sourceOutUs = safeSourceOutUs,
      speed = safeSpeed,
      volume = safeVolume,
      opacity = safeOpacity,
      keyframes = safeKeyframes
    )
  }

  /**
   * Sanitizes and repairs a full CoreTimelineState.
   */
  fun sanitizeTimeline(timeline: CoreTimelineState): CoreTimelineState {
    val sanitizedTracks = timeline.tracks.map { track ->
      val sanitizedClips = track.clips.map { sanitizeClip(it) }
      track.copy(
        clips = sanitizedClips,
        volume = if (track.volume.isFinite()) track.volume.coerceIn(0f, 2f) else 1f,
        pan = if (track.pan.isFinite()) track.pan.coerceIn(-1f, 1f) else 0f
      )
    }

    val calculatedDuration = sanitizedTracks.maxOfOrNull { it.durationUs } ?: 0L
    val safeDurationUs = max(calculatedDuration, timeline.durationUs.coerceAtLeast(0L))
    val safePlayheadUs = timeline.playheadPositionUs.coerceIn(0L, max(1L, safeDurationUs))

    return timeline.copy(
      durationUs = safeDurationUs,
      playheadPositionUs = safePlayheadUs,
      tracks = sanitizedTracks
    )
  }

  /**
   * Sanitizes an existing legacy Timeline instance, preventing invalid or negative durations.
   */
  fun sanitizeLegacyTimeline(timeline: Timeline): Timeline {
    val cleanVideo = timeline.videoClips.map { clip ->
      clip.copy(
        timelineStartMs = clip.timelineStartMs.coerceAtLeast(0L),
        durationMs = clip.durationMs.coerceAtLeast(33L),
        sourceStartMs = clip.sourceStartMs.coerceAtLeast(0L),
        sourceEndMs = max(clip.sourceStartMs + 33L, clip.sourceEndMs),
        speed = if (clip.speed.isFinite() && clip.speed > 0.01f) clip.speed.coerceIn(0.01f, 100f) else 1f,
        volume = if (clip.volume.isFinite()) clip.volume.coerceIn(0f, 10f) else 1f,
        opacity = if (clip.opacity.isFinite()) clip.opacity.coerceIn(0f, 1f) else 1f
      )
    }

    val cleanOverlay = timeline.overlayClips.map { clip ->
      clip.copy(
        timelineStartMs = clip.timelineStartMs.coerceAtLeast(0L),
        durationMs = clip.durationMs.coerceAtLeast(33L),
        sourceStartMs = clip.sourceStartMs.coerceAtLeast(0L),
        sourceEndMs = max(clip.sourceStartMs + 33L, clip.sourceEndMs),
        speed = if (clip.speed.isFinite() && clip.speed > 0.01f) clip.speed.coerceIn(0.01f, 100f) else 1f,
        volume = if (clip.volume.isFinite()) clip.volume.coerceIn(0f, 10f) else 1f,
        opacity = if (clip.opacity.isFinite()) clip.opacity.coerceIn(0f, 1f) else 1f
      )
    }

    val cleanAudio = timeline.audioClips.map { clip ->
      clip.copy(
        timelineStartMs = clip.timelineStartMs.coerceAtLeast(0L),
        durationMs = clip.durationMs.coerceAtLeast(33L),
        sourceStartMs = clip.sourceStartMs.coerceAtLeast(0L),
        sourceEndMs = max(clip.sourceStartMs + 33L, clip.sourceEndMs),
        volume = if (clip.volume.isFinite()) clip.volume.coerceIn(0f, 10f) else 1f
      )
    }

    val cleanText = timeline.textClips.map { clip ->
      clip.copy(
        timelineStartMs = clip.timelineStartMs.coerceAtLeast(0L),
        durationMs = clip.durationMs.coerceAtLeast(33L),
        opacity = if (clip.opacity.isFinite()) clip.opacity.coerceIn(0f, 1f) else 1f
      )
    }

    val cleanStickers = timeline.stickerClips.map { clip ->
      clip.copy(
        timelineStartMs = clip.timelineStartMs.coerceAtLeast(0L),
        durationMs = clip.durationMs.coerceAtLeast(33L),
        opacity = if (clip.opacity.isFinite()) clip.opacity.coerceIn(0f, 1f) else 1f
      )
    }

    val cleanEffects = timeline.effectClips.map { clip ->
      clip.copy(
        timelineStartMs = clip.timelineStartMs.coerceAtLeast(0L),
        durationMs = clip.durationMs.coerceAtLeast(33L),
        intensity = if (clip.intensity.isFinite()) clip.intensity.coerceIn(0f, 1f) else 0.8f
      )
    }

    return timeline.copy(
      videoClips = cleanVideo,
      overlayClips = cleanOverlay,
      audioClips = cleanAudio,
      textClips = cleanText,
      stickerClips = cleanStickers,
      effectClips = cleanEffects
    )
  }
}
