package com.example.domain.model

import java.util.UUID

// --- Microsecond extension accessors on legacy models ---

val VideoClip.timelineStartUs: Long get() = timelineStartMs * 1000L
val VideoClip.durationUs: Long get() = durationMs * 1000L
val VideoClip.timelineEndUs: Long get() = (timelineStartMs + durationMs) * 1000L
val VideoClip.sourceInUs: Long get() = sourceStartMs * 1000L
val VideoClip.sourceOutUs: Long get() = sourceEndMs * 1000L

val AudioClip.timelineStartUs: Long get() = timelineStartMs * 1000L
val AudioClip.durationUs: Long get() = durationMs * 1000L
val AudioClip.timelineEndUs: Long get() = (timelineStartMs + durationMs) * 1000L
val AudioClip.sourceInUs: Long get() = sourceStartMs * 1000L
val AudioClip.sourceOutUs: Long get() = sourceEndMs * 1000L

val TextClip.timelineStartUs: Long get() = timelineStartMs * 1000L
val TextClip.durationUs: Long get() = durationMs * 1000L
val TextClip.timelineEndUs: Long get() = (timelineStartMs + durationMs) * 1000L

val StickerClip.timelineStartUs: Long get() = timelineStartMs * 1000L
val StickerClip.durationUs: Long get() = durationMs * 1000L
val StickerClip.timelineEndUs: Long get() = (timelineStartMs + durationMs) * 1000L

val EffectClip.timelineStartUs: Long get() = timelineStartMs * 1000L
val EffectClip.durationUs: Long get() = durationMs * 1000L
val EffectClip.timelineEndUs: Long get() = (timelineStartMs + durationMs) * 1000L

val Timeline.totalDurationUs: Long get() = totalDurationMs * 1000L

/**
 * Converts a legacy VideoClip to a modern TimelineClip.
 */
fun VideoClip.toTimelineClip(trackId: String, isOverlay: Boolean = false): TimelineClip {
  return TimelineClip(
    id = id,
    sourceMediaId = uri,
    name = name,
    trackId = trackId,
    layerIndex = if (isOverlay) 1 else 0,
    kind = if (isVideo) TrackKind.VIDEO else TrackKind.IMAGE,
    timelineStartUs = timelineStartMs * 1000L,
    timelineDurationUs = durationMs * 1000L,
    sourceInUs = sourceStartMs * 1000L,
    sourceOutUs = sourceEndMs * 1000L,
    speed = speed.toDouble(),
    volume = volume,
    transform = NormalizedTransform(
      scaleX = cropScale,
      scaleY = cropScale,
      normalizedX = cropOffsetX,
      normalizedY = cropOffsetY,
      rotation = rotationDegrees.toFloat(),
      opacity = opacity,
      flipX = flipHorizontal,
      flipY = flipVertical
    ),
    isVisible = !isHidden,
    isEnabled = true,
    opacity = opacity,
    metadata = mapOf(
      "uri" to uri,
      "mimeType" to mimeType,
      "width" to width.toString(),
      "height" to height.toString(),
      "frameRate" to frameRate.toString(),
      "hasAudio" to hasAudio.toString()
    ),
    blendMode = blendMode,
    isLocked = isLocked,
    isMuted = isMuted,
    isReversed = isReversed,
    freezeFrameAtUs = freezeFrameAtMs?.let { it * 1000L },
    keyframes = keyframes,
    speedCurve = speedCurve,
    audioEffects = audioEffects,
    filter = filter,
    mask = mask,
    animation = animation
  )
}

/**
 * Converts a modern TimelineClip back to a legacy VideoClip.
 */
fun TimelineClip.toLegacyVideoClip(): VideoClip {
  return VideoClip(
    id = id,
    uri = sourceMediaId.ifBlank { metadata["uri"] ?: "" },
    name = name.ifBlank { metadata["uri"]?.substringAfterLast("/") ?: "Clip" },
    isVideo = kind == TrackKind.VIDEO,
    timelineStartMs = timelineStartMs,
    durationMs = durationMs,
    sourceStartMs = sourceStartMs,
    sourceEndMs = sourceEndMs,
    speed = speed.toFloat(),
    volume = volume,
    rotationDegrees = transform.rotation.toInt(),
    flipHorizontal = transform.flipX,
    flipVertical = transform.flipY,
    isMuted = isMuted,
    cropScale = transform.uniformScale,
    cropOffsetX = transform.normalizedX,
    cropOffsetY = transform.normalizedY,
    opacity = opacity,
    blendMode = blendMode,
    width = metadata["width"]?.toIntOrNull() ?: 1920,
    height = metadata["height"]?.toIntOrNull() ?: 1080,
    frameRate = metadata["frameRate"]?.toFloatOrNull() ?: 30f,
    mimeType = metadata["mimeType"] ?: "video/mp4",
    hasAudio = metadata["hasAudio"]?.toBooleanStrictOrNull() ?: true,
    isReversed = isReversed,
    freezeFrameAtMs = freezeFrameAtUs?.let { it / 1000L },
    keyframes = keyframes,
    filter = filter,
    animation = animation,
    mask = mask,
    speedCurve = speedCurve,
    audioEffects = audioEffects,
    isLocked = isLocked,
    isHidden = !isVisible
  )
}

/**
 * Converts a legacy AudioClip to a modern TimelineClip.
 */
fun AudioClip.toTimelineClip(trackId: String): TimelineClip {
  return TimelineClip(
    id = id,
    sourceMediaId = uri,
    name = title,
    trackId = trackId,
    kind = TrackKind.AUDIO,
    timelineStartUs = timelineStartMs * 1000L,
    timelineDurationUs = durationMs * 1000L,
    sourceInUs = sourceStartMs * 1000L,
    sourceOutUs = sourceEndMs * 1000L,
    speed = speed.toDouble(),
    volume = volume,
    isVisible = !isHidden,
    isEnabled = true,
    opacity = 1f,
    metadata = mapOf(
      "uri" to uri,
      "title" to title,
      "isVoiceOver" to isVoiceOver.toString(),
      "gainDb" to gainDb.toString(),
      "fadeInMs" to fadeInMs.toString(),
      "fadeOutMs" to fadeOutMs.toString()
    ),
    isLocked = isLocked,
    isMuted = isMuted,
    isReversed = isReversed,
    keyframes = keyframes,
    speedCurve = speedCurve,
    audioEffects = audioEffects
  )
}

/**
 * Converts a modern TimelineClip back to a legacy AudioClip.
 */
fun TimelineClip.toLegacyAudioClip(): AudioClip {
  return AudioClip(
    id = id,
    uri = sourceMediaId.ifBlank { metadata["uri"] ?: "" },
    title = name.ifBlank { metadata["title"] ?: "Audio" },
    timelineStartMs = timelineStartMs,
    durationMs = durationMs,
    sourceStartMs = sourceStartMs,
    sourceEndMs = sourceEndMs,
    volume = volume,
    speed = speed.toFloat(),
    fadeInMs = metadata["fadeInMs"]?.toLongOrNull() ?: 0L,
    fadeOutMs = metadata["fadeOutMs"]?.toLongOrNull() ?: 0L,
    isMuted = isMuted,
    isVoiceOver = metadata["isVoiceOver"]?.toBooleanStrictOrNull() ?: false,
    gainDb = metadata["gainDb"]?.toFloatOrNull() ?: 0f,
    isReversed = isReversed,
    keyframes = keyframes,
    speedCurve = speedCurve,
    audioEffects = audioEffects,
    isLocked = isLocked,
    isHidden = !isVisible
  )
}

/**
 * Converts a legacy TextClip to a modern TimelineClip.
 */
fun TextClip.toTimelineClip(trackId: String): TimelineClip {
  return TimelineClip(
    id = id,
    name = text.take(20),
    trackId = trackId,
    kind = TrackKind.TEXT,
    timelineStartUs = timelineStartMs * 1000L,
    timelineDurationUs = durationMs * 1000L,
    sourceInUs = 0L,
    sourceOutUs = durationMs * 1000L,
    speed = 1.0,
    volume = 0f,
    transform = NormalizedTransform(
      normalizedX = posX,
      normalizedY = posY,
      scaleX = scale,
      scaleY = scale,
      rotation = rotation,
      opacity = opacity
    ),
    isVisible = !isHidden,
    isEnabled = true,
    opacity = opacity,
    isLocked = isLocked,
    textPayload = TextPayload(
      text = text,
      fontFamily = fontFamily,
      customFontPath = customFontPath,
      fontSizeSp = fontSizeSp,
      fontWeight = fontWeight,
      isItalic = isItalic,
      isUnderline = isUnderline,
      isAllCaps = isAllCaps,
      alignment = alignment,
      letterSpacing = letterSpacing,
      lineSpacing = lineSpacing,
      textColor = textColor,
      hasGradient = hasGradient,
      gradientColorStart = gradientColorStart,
      gradientColorEnd = gradientColorEnd,
      gradientDirection = gradientDirection,
      strokeWidth = strokeWidth,
      strokeColor = strokeColor,
      hasShadow = hasShadow,
      shadowColor = shadowColor,
      shadowBlur = shadowBlur,
      shadowOffsetX = shadowOffsetX,
      shadowOffsetY = shadowOffsetY,
      hasBackground = hasBackground,
      backgroundColor = backgroundColor,
      cornerRadius = cornerRadius,
      bgPadding = bgPadding,
      animationType = animationType,
      animDurationMs = animDurationMs,
      subtitleStyle = subtitleStyle,
      highlightColor = highlightColor,
      words = words
    )
  )
}

/**
 * Converts a modern TimelineClip back to a legacy TextClip.
 */
fun TimelineClip.toLegacyTextClip(): TextClip {
  val payload = textPayload ?: TextPayload()
  return TextClip(
    id = id,
    text = payload.text,
    timelineStartMs = timelineStartMs,
    durationMs = durationMs,
    fontFamily = payload.fontFamily,
    customFontPath = payload.customFontPath,
    fontSizeSp = payload.fontSizeSp,
    fontWeight = payload.fontWeight,
    isItalic = payload.isItalic,
    isUnderline = payload.isUnderline,
    isAllCaps = payload.isAllCaps,
    alignment = payload.alignment,
    letterSpacing = payload.letterSpacing,
    lineSpacing = payload.lineSpacing,
    textColor = payload.textColor,
    hasGradient = payload.hasGradient,
    gradientColorStart = payload.gradientColorStart,
    gradientColorEnd = payload.gradientColorEnd,
    gradientDirection = payload.gradientDirection,
    strokeWidth = payload.strokeWidth,
    strokeColor = payload.strokeColor,
    hasShadow = payload.hasShadow,
    shadowColor = payload.shadowColor,
    shadowBlur = payload.shadowBlur,
    shadowOffsetX = payload.shadowOffsetX,
    shadowOffsetY = payload.shadowOffsetY,
    hasBackground = payload.hasBackground,
    backgroundColor = payload.backgroundColor,
    cornerRadius = payload.cornerRadius,
    bgPadding = payload.bgPadding,
    opacity = opacity,
    rotation = transform.rotation,
    posX = transform.normalizedX,
    posY = transform.normalizedY,
    scale = transform.uniformScale,
    animationType = payload.animationType,
    animDurationMs = payload.animDurationMs,
    subtitleStyle = payload.subtitleStyle,
    highlightColor = payload.highlightColor,
    words = payload.words,
    isLocked = isLocked,
    isHidden = !isVisible
  )
}

/**
 * Converts a legacy StickerClip to a modern TimelineClip.
 */
fun StickerClip.toTimelineClip(trackId: String): TimelineClip {
  return TimelineClip(
    id = id,
    name = emojiOrAsset,
    trackId = trackId,
    kind = TrackKind.IMAGE,
    timelineStartUs = timelineStartMs * 1000L,
    timelineDurationUs = durationMs * 1000L,
    sourceInUs = 0L,
    sourceOutUs = durationMs * 1000L,
    speed = 1.0,
    volume = 0f,
    transform = NormalizedTransform(
      normalizedX = posX,
      normalizedY = posY,
      scaleX = scale,
      scaleY = scale,
      rotation = rotation,
      opacity = opacity
    ),
    isVisible = !isHidden,
    isEnabled = true,
    opacity = opacity,
    isLocked = isLocked,
    keyframes = keyframes,
    stickerPayload = StickerPayload(
      emojiOrAsset = emojiOrAsset,
      category = category,
      elementId = elementId,
      customColor = customColor,
      animationType = animationType,
      badgeType = badgeType
    )
  )
}

/**
 * Converts a modern TimelineClip back to a legacy StickerClip.
 */
fun TimelineClip.toLegacyStickerClip(): StickerClip {
  val payload = stickerPayload ?: StickerPayload()
  return StickerClip(
    id = id,
    emojiOrAsset = payload.emojiOrAsset,
    timelineStartMs = timelineStartMs,
    durationMs = durationMs,
    posX = transform.normalizedX,
    posY = transform.normalizedY,
    scale = transform.uniformScale,
    rotation = transform.rotation,
    opacity = opacity,
    animationType = payload.animationType,
    badgeType = payload.badgeType,
    category = payload.category,
    isLocked = isLocked,
    isHidden = !isVisible,
    elementId = payload.elementId,
    customColor = payload.customColor,
    keyframes = keyframes
  )
}

/**
 * Converts a legacy EffectClip to a modern TimelineClip.
 */
fun EffectClip.toTimelineClip(trackId: String): TimelineClip {
  return TimelineClip(
    id = id,
    name = customName.ifBlank { effectType.displayName },
    trackId = trackId,
    kind = TrackKind.EFFECT,
    timelineStartUs = timelineStartMs * 1000L,
    timelineDurationUs = durationMs * 1000L,
    sourceInUs = 0L,
    sourceOutUs = durationMs * 1000L,
    speed = 1.0,
    volume = 0f,
    isVisible = !isHidden,
    isEnabled = true,
    opacity = intensity,
    isLocked = isLocked,
    keyframes = keyframes,
    effectPayload = EffectPayload(
      effectType = effectType,
      intensity = intensity,
      effectCategory = effectCategory,
      targetClipId = targetClipId
    )
  )
}

/**
 * Converts a modern TimelineClip back to a legacy EffectClip.
 */
fun TimelineClip.toLegacyEffectClip(): EffectClip {
  val payload = effectPayload ?: EffectPayload()
  return EffectClip(
    id = id,
    effectType = payload.effectType,
    timelineStartMs = timelineStartMs,
    durationMs = durationMs,
    intensity = payload.intensity,
    keyframes = keyframes,
    customName = name,
    effectCategory = payload.effectCategory,
    targetClipId = payload.targetClipId,
    isLocked = isLocked,
    isHidden = !isVisible
  )
}

/**
 * Two-way conversion: Converts existing legacy Timeline into modern CoreTimelineState.
 * Automatically builds tracks for VIDEO, OVERLAY, AUDIO, TEXT, IMAGE, EFFECT, and ADJUSTMENT.
 */
fun Timeline.toCoreTimeline(
  fps: Int = 30,
  resolution: TimelineResolution = TimelineResolution.FULL_HD_1080P,
  currentPlayheadMs: Long = 0L
): CoreTimelineState {
  val trackVideoId = "track_main_video"
  val trackOverlayId = "track_overlay"
  val trackAudioId = "track_audio"
  val trackTextId = "track_text"
  val trackStickerId = "track_stickers"
  val trackEffectId = "track_effects"

  val tracks = mutableListOf<TimelineTrack>()

  // 1. Video Track
  val videoTrackSettings = trackSettings[TrackType.MAIN_VIDEO]
  tracks.add(
    TimelineTrack(
      id = trackVideoId,
      name = "Main Video",
      kind = TrackKind.VIDEO,
      orderIndex = 0,
      isLocked = videoTrackSettings?.isLocked ?: false,
      isHidden = videoTrackSettings?.isHidden ?: false,
      isMuted = videoTrackSettings?.isMuted ?: false,
      isSolo = videoTrackSettings?.isSolo ?: false,
      height = videoTrackSettings?.height ?: TrackHeight.NORMAL,
      clips = videoClips.map { it.toTimelineClip(trackVideoId, isOverlay = false) }
    )
  )

  // 2. Overlay Track
  val overlayTrackSettings = trackSettings[TrackType.OVERLAY]
  tracks.add(
    TimelineTrack(
      id = trackOverlayId,
      name = "Overlay (PiP)",
      kind = TrackKind.OVERLAY,
      orderIndex = 1,
      isLocked = overlayTrackSettings?.isLocked ?: false,
      isHidden = overlayTrackSettings?.isHidden ?: false,
      isMuted = overlayTrackSettings?.isMuted ?: false,
      isSolo = overlayTrackSettings?.isSolo ?: false,
      height = overlayTrackSettings?.height ?: TrackHeight.NORMAL,
      clips = overlayClips.map { it.toTimelineClip(trackOverlayId, isOverlay = true) }
    )
  )

  // 3. Audio Track
  val audioTrackSettings = trackSettings[TrackType.AUDIO]
  tracks.add(
    TimelineTrack(
      id = trackAudioId,
      name = "Audio",
      kind = TrackKind.AUDIO,
      orderIndex = 2,
      isLocked = audioTrackSettings?.isLocked ?: false,
      isHidden = audioTrackSettings?.isHidden ?: false,
      isMuted = audioTrackSettings?.isMuted ?: false,
      isSolo = audioTrackSettings?.isSolo ?: false,
      height = audioTrackSettings?.height ?: TrackHeight.NORMAL,
      clips = audioClips.map { it.toTimelineClip(trackAudioId) }
    )
  )

  // 4. Text Track
  val textTrackSettings = trackSettings[TrackType.TEXT]
  tracks.add(
    TimelineTrack(
      id = trackTextId,
      name = "Text & Subtitles",
      kind = TrackKind.TEXT,
      orderIndex = 3,
      isLocked = textTrackSettings?.isLocked ?: false,
      isHidden = textTrackSettings?.isHidden ?: false,
      isMuted = textTrackSettings?.isMuted ?: false,
      isSolo = textTrackSettings?.isSolo ?: false,
      height = textTrackSettings?.height ?: TrackHeight.NORMAL,
      clips = textClips.map { it.toTimelineClip(trackTextId) }
    )
  )

  // 5. Sticker / Image Track
  val stickerTrackSettings = trackSettings[TrackType.STICKER]
  tracks.add(
    TimelineTrack(
      id = trackStickerId,
      name = "Stickers & Graphics",
      kind = TrackKind.IMAGE,
      orderIndex = 4,
      isLocked = stickerTrackSettings?.isLocked ?: false,
      isHidden = stickerTrackSettings?.isHidden ?: false,
      isMuted = stickerTrackSettings?.isMuted ?: false,
      isSolo = stickerTrackSettings?.isSolo ?: false,
      height = stickerTrackSettings?.height ?: TrackHeight.NORMAL,
      clips = stickerClips.map { it.toTimelineClip(trackStickerId) }
    )
  )

  // 6. Effect Track
  val effectTrackSettings = trackSettings[TrackType.EFFECT]
  tracks.add(
    TimelineTrack(
      id = trackEffectId,
      name = "Effects & Filters",
      kind = TrackKind.EFFECT,
      orderIndex = 5,
      isLocked = effectTrackSettings?.isLocked ?: false,
      isHidden = effectTrackSettings?.isHidden ?: false,
      isMuted = effectTrackSettings?.isMuted ?: false,
      isSolo = effectTrackSettings?.isSolo ?: false,
      height = effectTrackSettings?.height ?: TrackHeight.NORMAL,
      clips = effectClips.map { it.toTimelineClip(trackEffectId) }
    )
  )

  val coreTransitions = transitions.map { t ->
    TimelineTransition(
      id = t.id,
      type = t.type,
      durationUs = t.durationMs * 1000L
    )
  }

  val totalDurUs = totalDurationMs * 1000L
  val playheadUs = (currentPlayheadMs * 1000L).coerceIn(0L, maxOf(1000L, totalDurUs))

  return CoreTimelineState(
    durationUs = totalDurUs,
    playheadPositionUs = playheadUs,
    timebase = Timebase.fromFps(fps),
    fps = FrameRate.FPS_30,
    resolution = resolution,
    aspectRatio = aspectRatio,
    zoomScale = 1.0f,
    tracks = tracks,
    trackOrder = tracks.map { it.id },
    transitions = coreTransitions,
    adjustments = adjustments,
    filter = filter,
    chromaKey = chromaKey,
    canvasBackgroundColor = canvasBackgroundColor
  )
}

/**
 * Two-way conversion: Converts modern CoreTimelineState back to legacy Timeline
 * to guarantee 100% backward compatibility with all UI rendering, exporters, and controllers.
 */
fun CoreTimelineState.toLegacyTimeline(): Timeline {
  val legacyVideo = mutableListOf<VideoClip>()
  val legacyOverlay = mutableListOf<VideoClip>()
  val legacyAudio = mutableListOf<AudioClip>()
  val legacyText = mutableListOf<TextClip>()
  val legacyStickers = mutableListOf<StickerClip>()
  val legacyEffects = mutableListOf<EffectClip>()

  val legacyTrackSettings = mutableMapOf<TrackType, TrackSettings>()

  for (track in tracks) {
    when (track.kind) {
      TrackKind.VIDEO -> {
        legacyVideo.addAll(track.clips.map { it.toLegacyVideoClip() })
        legacyTrackSettings[TrackType.MAIN_VIDEO] = TrackSettings(
          type = TrackType.MAIN_VIDEO,
          isLocked = track.isLocked,
          isHidden = track.isHidden,
          isMuted = track.isMuted,
          isSolo = track.isSolo,
          height = track.height
        )
      }
      TrackKind.OVERLAY -> {
        legacyOverlay.addAll(track.clips.map { it.toLegacyVideoClip() })
        legacyTrackSettings[TrackType.OVERLAY] = TrackSettings(
          type = TrackType.OVERLAY,
          isLocked = track.isLocked,
          isHidden = track.isHidden,
          isMuted = track.isMuted,
          isSolo = track.isSolo,
          height = track.height
        )
      }
      TrackKind.AUDIO -> {
        legacyAudio.addAll(track.clips.map { it.toLegacyAudioClip() })
        legacyTrackSettings[TrackType.AUDIO] = TrackSettings(
          type = TrackType.AUDIO,
          isLocked = track.isLocked,
          isHidden = track.isHidden,
          isMuted = track.isMuted,
          isSolo = track.isSolo,
          height = track.height
        )
      }
      TrackKind.TEXT -> {
        legacyText.addAll(track.clips.map { it.toLegacyTextClip() })
        legacyTrackSettings[TrackType.TEXT] = TrackSettings(
          type = TrackType.TEXT,
          isLocked = track.isLocked,
          isHidden = track.isHidden,
          isMuted = track.isMuted,
          isSolo = track.isSolo,
          height = track.height
        )
      }
      TrackKind.IMAGE -> {
        legacyStickers.addAll(track.clips.map { it.toLegacyStickerClip() })
        legacyTrackSettings[TrackType.STICKER] = TrackSettings(
          type = TrackType.STICKER,
          isLocked = track.isLocked,
          isHidden = track.isHidden,
          isMuted = track.isMuted,
          isSolo = track.isSolo,
          height = track.height
        )
      }
      TrackKind.EFFECT, TrackKind.ADJUSTMENT -> {
        legacyEffects.addAll(track.clips.map { it.toLegacyEffectClip() })
        legacyTrackSettings[TrackType.EFFECT] = TrackSettings(
          type = TrackType.EFFECT,
          isLocked = track.isLocked,
          isHidden = track.isHidden,
          isMuted = track.isMuted,
          isSolo = track.isSolo,
          height = track.height
        )
      }
    }
  }

  val legacyTransitions = transitions.mapIndexed { index, t ->
    Transition(
      id = t.id,
      clipIndexBefore = index,
      type = t.type,
      durationMs = t.durationMs
    )
  }

  return Timeline(
    videoClips = legacyVideo,
    overlayClips = legacyOverlay,
    audioClips = legacyAudio,
    textClips = legacyText,
    stickerClips = legacyStickers,
    effectClips = legacyEffects,
    transitions = legacyTransitions,
    adjustments = adjustments,
    filter = filter,
    chromaKey = chromaKey,
    canvasBackgroundColor = canvasBackgroundColor,
    aspectRatio = aspectRatio,
    trackSettings = if (legacyTrackSettings.isNotEmpty()) legacyTrackSettings else defaultTrackSettings()
  )
}
