package com.example.engine.composition

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import com.example.domain.model.*
import com.example.engine.InterpolatedClipTransform
import com.example.engine.KeyframeInterpolator
import com.example.engine.composition.gpu.NativeBlendMode
import com.example.engine.composition.gpu.NativeLayer
import com.example.engine.composition.gpu.NativeLayerType
import kotlin.math.max
import kotlin.math.min

/**
 * Evaluated Layer Composition Node in the scene graph.
 * Represents a single renderable clip in the deterministic compositing stack.
 */
data class LayerCompositionNode(
  val clipId: String,
  val trackId: String,
  val trackKind: TrackKind,
  val trackOrderIndex: Int,
  val layerZIndex: Int,
  val sourceMediaUri: String,
  val sourcePosMs: Long,
  val timelinePosMs: Long,
  val timelineStartMs: Long,
  val durationMs: Long,
  val isVideo: Boolean,
  val isAudioOnly: Boolean,
  val isMuted: Boolean,
  val volume: Float,
  val pan: Float,
  val isLocked: Boolean,
  val isVisible: Boolean,
  val isEnabled: Boolean,
  val isIsolated: Boolean,
  val opacity: Float,
  val blendMode: CompositeBlendMode,
  val transform: NormalizedTransform,
  val evaluatedTransform: InterpolatedClipTransform,
  val filter: FilterSettings?,
  val adjustments: VideoAdjustments,
  val mask: MaskSettings,
  val chromaKey: ChromaKeySettings,
  val textPayload: TextPayload? = null,
  val stickerPayload: StickerPayload? = null,
  val effectPayload: EffectPayload? = null,
  val isAdjustmentLayer: Boolean = false,
  val motionTrackingOffset: Pair<Float, Float> = 0f to 0f
) {
  val isVisual: Boolean
    get() = trackKind != TrackKind.AUDIO && !isAudioOnly

  fun createMaskPath(canvasWidth: Float, canvasHeight: Float): Path? {
    if (!mask.enabled || mask.shape == MaskShape.NONE) return null
    val path = Path()
    val centerX = (canvasWidth / 2f) + (mask.posX * canvasWidth / 2f)
    val centerY = (canvasHeight / 2f) + (mask.posY * canvasHeight / 2f)
    val w = mask.width * canvasWidth / 2f
    val h = mask.height * canvasHeight / 2f

    when (mask.shape) {
      MaskShape.RECTANGLE -> {
        val rect = RectF(centerX - w / 2f, centerY - h / 2f, centerX + w / 2f, centerY + h / 2f)
        path.addRoundRect(rect, mask.feather * 40f, mask.feather * 40f, Path.Direction.CW)
      }
      MaskShape.CIRCLE -> {
        val radius = min(w, h) / 2f
        path.addCircle(centerX, centerY, radius, Path.Direction.CW)
      }
      MaskShape.LINEAR -> {
        val rect = RectF(0f, 0f, centerX, canvasHeight)
        path.addRect(rect, Path.Direction.CW)
      }
      MaskShape.MIRROR -> {
        val topRect = RectF(0f, centerY - h / 2f, canvasWidth, centerY + h / 2f)
        path.addRect(topRect, Path.Direction.CW)
      }
      MaskShape.STAR, MaskShape.HEART -> {
        val rect = RectF(centerX - w / 2f, centerY - h / 2f, centerX + w / 2f, centerY + h / 2f)
        path.addOval(rect, Path.Direction.CW)
      }
      else -> return null
    }

    if (mask.rotation != 0f) {
      val rotateMatrix = Matrix()
      rotateMatrix.postRotate(mask.rotation, centerX, centerY)
      path.transform(rotateMatrix)
    }

    return path
  }
}

/**
 * Evaluated descriptor of an active transition between two clips or across a cut point.
 */
data class ActiveTransitionDescriptor(
  val transition: TimelineTransition,
  val cutPositionUs: Long,
  val startUs: Long,
  val endUs: Long,
  val progress: Float, // 0.0f to 1.0f
  val fromClipId: String?,
  val toClipId: String?,
  val customParams: Map<String, Float> = emptyMap()
) {
  val type: TransitionType get() = transition.type
}

/**
 * Descriptor of the entire evaluated composition frame at timestamp T.
 */
data class CompositionFrameDescriptor(
  val timelinePosMs: Long,
  val timelinePosUs: Long,
  val durationUs: Long,
  val activeLayers: List<LayerCompositionNode>,
  val adjustmentLayers: List<LayerCompositionNode>,
  val audioLayers: List<LayerCompositionNode>,
  val activeTransitions: List<TimelineTransition>,
  val activeTransitionDescriptors: List<ActiveTransitionDescriptor> = emptyList(),
  val globalAdjustments: VideoAdjustments,
  val globalFilter: FilterSettings,
  val globalChromaKey: ChromaKeySettings,
  val canvasBackgroundColor: Long,
  val isolatedTrackId: String? = null,
  val isolatedClipId: String? = null
) {
  val visualLayers: List<LayerCompositionNode>
    get() = activeLayers.filter { it.isVisual }
}

/**
 * Deterministic Layer Compositor.
 *
 * Implements the deterministic composition order:
 * Timeline
 * → Tracks (ordering, solo, visibility, isolation)
 * → Clips (filtering by time, enabled, overlapping resolution, Z-order)
 * → Effects (per-clip filters, adjustments, intensity)
 * → Transforms (pivot, flip, rotate, crop, scale, translate, keyframes, motion tracking)
 * → Masks (shape, invert, feather, position)
 * → Compositing (blend modes, alpha compositing, Z-index stack)
 * → Final Frame (adjustment layers, global grade, canvas background)
 */
object LayerCompositor {

  private const val BASE_TRACK_Z_STEP = 1000
  private const val BASE_CLIP_Z_STEP = 10

  /**
   * Evaluates the complete multi-layer composition at microsecond precision.
   * Supports unlimited tracks, overlapping clips, layer isolation, solo, mute, lock,
   * blend modes, and keyframed alpha compositing.
   */
  fun evaluateComposition(
    state: CoreTimelineState,
    timeUs: Long,
    isolatedTrackId: String? = null,
    isolatedClipId: String? = null
  ): CompositionFrameDescriptor {
    val timeMs = timeUs / 1000L
    val orderedTracks = state.orderedTracks()

    // 1. Evaluate Track-level Solo & Isolation
    val hasSoloTracks = orderedTracks.any { it.isSolo }
    val effectiveTracks = orderedTracks.filter { track ->
      if (isolatedTrackId != null) {
        track.id == isolatedTrackId
      } else if (hasSoloTracks) {
        track.isSolo
      } else {
        !track.isHidden
      }
    }

    val evaluatedVisualNodes = mutableListOf<LayerCompositionNode>()
    val evaluatedAdjustmentNodes = mutableListOf<LayerCompositionNode>()
    val evaluatedAudioNodes = mutableListOf<LayerCompositionNode>()

    // 2. Evaluate Tracks in deterministic order
    effectiveTracks.forEachIndexed { trackIndex, track ->
      val trackZBase = (track.orderIndex.takeIf { it >= 0 } ?: trackIndex) * BASE_TRACK_Z_STEP

      // 3. Evaluate Clips on this track (including overlapping clips) via accelerated spatial index
      val activeClips = track.getClipsAt(timeUs).filter { clip ->
        isolatedClipId == null || clip.id == isolatedClipId
      }

      for (clipIndex in activeClips.indices) {
        val clip = activeClips[clipIndex]

        // Intra-track Z-ordering: clip.transform.zIndex -> clip.layerIndex -> clipIndex
        val clipZOrder = trackZBase + (clip.transform.zIndex * 100) + (clip.layerIndex * BASE_CLIP_Z_STEP) + clipIndex

        val relTimeMs = (timeMs - clip.timelineStartMs).coerceAtLeast(0L)
        val relTimeUs = (timeUs - clip.timelineStartUs).coerceAtLeast(0L)

        // Compound / Nested Clip Resolution
        if (clip.isCompound && clip.nestedTimeline != null) {
          val nestedMappings = CompoundTimelineTimeMapper.evaluateNestedClipsAtParentTime(
            parentTimelineUs = timeUs,
            compoundClip = clip
          )

          for ((childIdx, nested) in nestedMappings.withIndex()) {
            val child = nested.childClip
            val childZOrder = clipZOrder + (child.transform.zIndex * 10) + childIdx

            val childNode = LayerCompositionNode(
              clipId = child.id,
              trackId = nested.childTrackId,
              trackKind = child.kind,
              trackOrderIndex = track.orderIndex,
              layerZIndex = childZOrder,
              sourceMediaUri = child.sourceMediaId.ifBlank { child.metadata["uri"] ?: "" },
              sourcePosMs = nested.childSourceTimeMs,
              timelinePosMs = timeMs,
              timelineStartMs = clip.timelineStartMs,
              durationMs = clip.durationMs,
              isVideo = child.kind == TrackKind.VIDEO,
              isAudioOnly = child.kind == TrackKind.AUDIO,
              isMuted = track.isMuted || clip.isMuted || child.isMuted,
              volume = nested.cascadedVolume,
              pan = track.pan,
              isLocked = track.isLocked || clip.isLocked || child.isLocked,
              isVisible = !track.isHidden && clip.isVisible && child.isVisible,
              isEnabled = clip.isEnabled && child.isEnabled,
              isIsolated = clip.id == isolatedClipId || child.id == isolatedClipId || track.id == isolatedTrackId,
              opacity = nested.cascadedOpacity,
              blendMode = CompositeBlendMode.fromString(child.blendMode),
              transform = nested.cascadedTransform,
              evaluatedTransform = InterpolatedClipTransform(
                posX = nested.cascadedTransform.normalizedX,
                posY = nested.cascadedTransform.normalizedY,
                scaleX = nested.cascadedTransform.scaleX,
                scaleY = nested.cascadedTransform.scaleY,
                rotation = nested.cascadedTransform.rotation,
                opacity = nested.cascadedOpacity,
                volume = nested.cascadedVolume
              ),
              filter = child.filter ?: clip.filter,
              adjustments = VideoAdjustments(),
              mask = child.mask,
              chromaKey = state.chromaKey,
              textPayload = child.textPayload,
              stickerPayload = child.stickerPayload,
              effectPayload = child.effectPayload,
              isAdjustmentLayer = child.kind == TrackKind.ADJUSTMENT
            )

            if (child.kind == TrackKind.ADJUSTMENT) {
              evaluatedAdjustmentNodes.add(childNode)
            } else if (child.kind == TrackKind.AUDIO) {
              evaluatedAudioNodes.add(childNode)
            } else {
              evaluatedVisualNodes.add(childNode)
            }
          }
          continue
        }

        // Calculate source position accounting for speed, reverse, and freeze frames
        val sourcePosMs = if (clip.freezeFrameAtUs != null) {
          clip.freezeFrameAtUs / 1000L
        } else if (clip.isReversed) {
          val durationMs = clip.timelineDurationUs / 1000L
          val revOffsetMs = (durationMs - relTimeMs).coerceAtLeast(0L)
          (clip.sourceInUs / 1000L) + (revOffsetMs * clip.speed).toLong()
        } else {
          (clip.sourceInUs / 1000L) + (relTimeMs * clip.speed).toLong()
        }

        // 4. Evaluate Keyframe Interpolation for Transforms & Effects
        val keyframeTransform = if (clip.keyframes.isNotEmpty()) {
          val legacyVideoClip = clip.toLegacyVideoClip()
          KeyframeInterpolator.interpolate(legacyVideoClip, relTimeMs)
        } else {
          InterpolatedClipTransform(
            posX = clip.transform.normalizedX,
            posY = clip.transform.normalizedY,
            scaleX = clip.transform.scaleX,
            scaleY = clip.transform.scaleY,
            rotation = clip.transform.rotation,
            opacity = clip.transform.opacity,
            volume = clip.volume,
            blur = 0f,
            brightness = 0f,
            contrast = 1f,
            saturation = 1f,
            effectParam = 0f
          )
        }

        // 5. Evaluate Opacity and Alpha Blending
        val effectiveOpacity = (clip.opacity * keyframeTransform.opacity).coerceIn(0f, 1f)
        val effectiveVolume = if (track.isMuted || clip.isMuted) 0f else (track.volume * clip.volume * keyframeTransform.volume).coerceIn(0f, 2f)

        // 6. Evaluate Adjustments & Filters
        val clipAdjustments = VideoAdjustments(
          brightness = keyframeTransform.brightness,
          contrast = keyframeTransform.contrast,
          saturation = keyframeTransform.saturation
        )

        val blendMode = CompositeBlendMode.fromString(clip.blendMode)

        val node = LayerCompositionNode(
          clipId = clip.id,
          trackId = track.id,
          trackKind = track.kind,
          trackOrderIndex = track.orderIndex,
          layerZIndex = clipZOrder,
          sourceMediaUri = clip.sourceMediaId.ifBlank { clip.metadata["uri"] ?: "" },
          sourcePosMs = sourcePosMs,
          timelinePosMs = timeMs,
          timelineStartMs = clip.timelineStartMs,
          durationMs = clip.durationMs,
          isVideo = clip.kind == TrackKind.VIDEO,
          isAudioOnly = clip.kind == TrackKind.AUDIO,
          isMuted = track.isMuted || clip.isMuted,
          volume = effectiveVolume,
          pan = track.pan,
          isLocked = track.isLocked || clip.isLocked,
          isVisible = !track.isHidden && clip.isVisible,
          isEnabled = clip.isEnabled,
          isIsolated = clip.id == isolatedClipId || track.id == isolatedTrackId,
          opacity = effectiveOpacity,
          blendMode = blendMode,
          transform = clip.transform,
          evaluatedTransform = keyframeTransform,
          filter = clip.filter,
          adjustments = clipAdjustments,
          mask = clip.mask,
          chromaKey = state.chromaKey,
          textPayload = clip.textPayload,
          stickerPayload = clip.stickerPayload,
          effectPayload = clip.effectPayload,
          isAdjustmentLayer = track.kind == TrackKind.ADJUSTMENT
        )

        if (track.kind == TrackKind.ADJUSTMENT) {
          evaluatedAdjustmentNodes.add(node)
        } else if (track.kind == TrackKind.AUDIO) {
          evaluatedAudioNodes.add(node)
        } else {
          evaluatedVisualNodes.add(node)
        }
      }
    }

    // 7. Sort Visual Layers strictly by deterministic Z-Index ascending (Lowest Z drawn first -> Highest Z drawn last)
    val sortedVisualLayers = evaluatedVisualNodes.sortedWith(
      compareBy<LayerCompositionNode> { it.layerZIndex }
        .thenBy { it.trackOrderIndex }
        .thenBy { it.timelineStartMs }
    )

    // 8. Find active transitions and build evaluated descriptors
    val activeTransitionDescriptors = mutableListOf<ActiveTransitionDescriptor>()
    for (tr in state.transitions) {
      val fromClip = tr.fromClipId?.let { state.findClip(it) }
      val toClip = tr.toClipId?.let { state.findClip(it) }
      val cutUs = tr.cutPositionUs ?: fromClip?.timelineEndUs ?: toClip?.timelineStartUs ?: 0L
      if (tr.isActiveAt(timeUs, cutUs)) {
        val progress = tr.getProgressAt(timeUs, cutUs)
        activeTransitionDescriptors.add(
          ActiveTransitionDescriptor(
            transition = tr,
            cutPositionUs = cutUs,
            startUs = tr.calculateStartUs(cutUs),
            endUs = tr.calculateEndUs(cutUs),
            progress = progress,
            fromClipId = tr.fromClipId,
            toClipId = tr.toClipId,
            customParams = tr.customParams
          )
        )
      }
    }

    val activeTransitions = activeTransitionDescriptors.map { it.transition }

    return CompositionFrameDescriptor(
      timelinePosMs = timeMs,
      timelinePosUs = timeUs,
      durationUs = state.durationUs,
      activeLayers = sortedVisualLayers,
      adjustmentLayers = evaluatedAdjustmentNodes,
      audioLayers = evaluatedAudioNodes,
      activeTransitions = activeTransitions,
      activeTransitionDescriptors = activeTransitionDescriptors,
      globalAdjustments = state.adjustments,
      globalFilter = state.filter,
      globalChromaKey = state.chromaKey,
      canvasBackgroundColor = state.canvasBackgroundColor,
      isolatedTrackId = isolatedTrackId,
      isolatedClipId = isolatedClipId
    )
  }

  /**
   * Converts evaluated CompositionFrameDescriptor into GPU native layers for OpenGL ES rendering.
   */
  fun toNativeLayers(
    descriptor: CompositionFrameDescriptor,
    textureMap: Map<String, Int>,
    viewportWidth: Int,
    viewportHeight: Int
  ): List<NativeLayer> {
    val nativeLayers = mutableListOf<NativeLayer>()

    for (node in descriptor.activeLayers) {
      if (!node.isVisible || node.opacity <= 0.001f) continue

      val texId = textureMap[node.clipId] ?: textureMap[node.sourceMediaUri] ?: 0
      if (texId <= 0) continue

      val layerType = when (node.trackKind) {
        TrackKind.VIDEO -> NativeLayerType.VIDEO
        TrackKind.IMAGE, TrackKind.OVERLAY -> NativeLayerType.VIDEO
        TrackKind.TEXT -> NativeLayerType.TEXT
        TrackKind.EFFECT -> NativeLayerType.EFFECT_OVERLAY
        else -> NativeLayerType.IMAGE_STICKER
      }

      val mvp = FloatArray(16)
      android.opengl.Matrix.setIdentityM(mvp, 0)

      val transform = node.evaluatedTransform
      val normTransform = node.transform

      // Standardize matrix transformation
      android.opengl.Matrix.translateM(mvp, 0, transform.posX, -transform.posY, 0f)
      android.opengl.Matrix.rotateM(mvp, 0, -transform.rotation, 0f, 0f, 1f)
      android.opengl.Matrix.scaleM(mvp, 0, transform.scaleX, transform.scaleY, 1f)

      val layer = NativeLayer(
        id = node.clipId.hashCode().toLong(),
        textureId = texId,
        type = layerType,
        isVisible = node.isVisible,
        zOrder = node.layerZIndex,
        posX = transform.posX,
        posY = transform.posY,
        scaleX = transform.scaleX,
        scaleY = transform.scaleY,
        rotation = transform.rotation,
        opacity = node.opacity,
        blendMode = node.blendMode.nativeMode,
        useCustomMatrix = true,
        transformMatrix = mvp
      )
      nativeLayers.add(layer)
    }

    return nativeLayers.sortedBy { it.zOrder }
  }
}
