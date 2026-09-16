package com.example.engine.composition

import android.content.Context
import android.graphics.*
import com.example.domain.model.*
import com.example.engine.KeyframeInterpolator
import com.example.engine.composition.gpu.GpuCompositionRenderer
import com.example.engine.text.TextLayerRenderer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class ComposedEffect(
  val clip: EffectClip,
  val effectType: EffectType,
  val intensity: Float,
  val timeInEffectMs: Long,
  val progress: Float
)

data class ComposedFrame(
  val timelinePosMs: Long,
  val activeClip: VideoClip?,
  val clipSourcePosMs: Long,
  val activeOverlays: List<ComposedOverlay>,
  val activeTexts: List<ComposedText>,
  val activeStickers: List<ComposedSticker>,
  val activeTransition: ComposedTransition?,
  val activeEffects: List<ComposedEffect> = emptyList(),
  val colorMatrix: ColorMatrix,
  val colorFilter: ColorMatrixColorFilter,
  val activeClipTransform: com.example.engine.InterpolatedClipTransform? = null
)

data class ComposedOverlay(
  val clip: VideoClip,
  val sourcePosMs: Long,
  val posX: Float,
  val posY: Float,
  val scaleX: Float,
  val scaleY: Float,
  val rotation: Float,
  val opacity: Float,
  val blendMode: String,
  val blur: Float = 0f,
  val brightness: Float = 0f,
  val contrast: Float = 1f,
  val saturation: Float = 1f,
  val effectParam: Float = 0f
) {
  val scale: Float get() = (scaleX + scaleY) / 2f

  constructor(
    clip: VideoClip,
    sourcePosMs: Long,
    posX: Float,
    posY: Float,
    scale: Float,
    rotation: Float,
    opacity: Float,
    blendMode: String
  ) : this(
    clip = clip,
    sourcePosMs = sourcePosMs,
    posX = posX,
    posY = posY,
    scaleX = scale,
    scaleY = scale,
    rotation = rotation,
    opacity = opacity,
    blendMode = blendMode
  )
}

data class ComposedText(
  val clip: TextClip,
  val posX: Float,
  val posY: Float,
  val scale: Float,
  val rotation: Float,
  val opacity: Float,
  val currentPosMs: Long = 0L
)

data class ComposedSticker(
  val clip: StickerClip,
  val posX: Float,
  val posY: Float,
  val scale: Float,
  val rotation: Float,
  val opacity: Float
)

data class ComposedTransition(
  val type: TransitionType,
  val progress: Float, // 0.0f to 1.0f
  val clipBefore: VideoClip,
  val clipAfter: VideoClip
)

class VideoCompositionEngine(private val context: Context) {

  val gpuRenderer: GpuCompositionRenderer by lazy {
    GpuCompositionRenderer(context)
  }

  /**
   * Renders the composed frame on the GPU into the currently active OpenGL surface/framebuffer.
   */
  fun renderGpuFrame(
    frame: ComposedFrame,
    mainTextureId: Int,
    isMainOes: Boolean,
    mainTexMatrix: FloatArray? = null,
    overlayTextures: Map<String, Int> = emptyMap(),
    viewportWidth: Int,
    viewportHeight: Int,
    adjustments: VideoAdjustments = VideoAdjustments(),
    filter: FilterSettings = FilterSettings(),
    chromaKey: ChromaKeySettings = ChromaKeySettings()
  ) {
    gpuRenderer.render(
      frame = frame,
      mainTextureId = mainTextureId,
      isMainOes = isMainOes,
      mainTexMatrix = mainTexMatrix,
      overlayTextures = overlayTextures,
      viewportWidth = viewportWidth,
      viewportHeight = viewportHeight,
      timelineAdjustments = adjustments,
      timelineFilter = filter,
      chromaKey = chromaKey
    )
  }

  fun releaseGpu() {
    gpuRenderer.release()
  }

  /**
   * Calculates the exact state of all timeline elements at any timestamp.
   */
  fun evaluateFrame(timeline: Timeline, posMs: Long): ComposedFrame {
    val hasSolo = timeline.trackSettings.values.any { it.isSolo }
    val isVideoHidden = if (hasSolo) timeline.trackSettings[TrackType.MAIN_VIDEO]?.isSolo != true else timeline.trackSettings[TrackType.MAIN_VIDEO]?.isHidden == true
    val isOverlayHidden = if (hasSolo) timeline.trackSettings[TrackType.OVERLAY]?.isSolo != true else timeline.trackSettings[TrackType.OVERLAY]?.isHidden == true
    val isTextHidden = if (hasSolo) timeline.trackSettings[TrackType.TEXT]?.isSolo != true else timeline.trackSettings[TrackType.TEXT]?.isHidden == true
    val isStickerHidden = if (hasSolo) timeline.trackSettings[TrackType.STICKER]?.isSolo != true else timeline.trackSettings[TrackType.STICKER]?.isHidden == true

    val activeClip = if (!isVideoHidden) {
      timeline.videoClips.find {
        !it.isHidden && posMs >= it.timelineStartMs && posMs < it.timelineStartMs + it.durationMs
      } ?: timeline.videoClips.lastOrNull { !it.isHidden }
    } else null

    val sourcePosMs = activeClip?.timelineToSourceMs(posMs) ?: 0L

    // Check transition
    var activeTransition: ComposedTransition? = null
    if (!isVideoHidden) {
      for (tr in timeline.transitions) {
        if (tr.clipIndexBefore >= 0 && tr.clipIndexBefore < timeline.videoClips.size - 1) {
          val clipBefore = timeline.videoClips[tr.clipIndexBefore]
          val clipAfter = timeline.videoClips[tr.clipIndexBefore + 1]
          val transitionStart = clipBefore.timelineStartMs + clipBefore.durationMs - (tr.durationMs / 2)
          val transitionEnd = transitionStart + tr.durationMs

          if (posMs in transitionStart until transitionEnd) {
            val progress = ((posMs - transitionStart).toFloat() / tr.durationMs).coerceIn(0f, 1f)
            activeTransition = ComposedTransition(
              type = tr.type,
              progress = progress,
              clipBefore = clipBefore,
              clipAfter = clipAfter
            )
            break
          }
        }
      }
    }

    // Overlays with keyframes
    val overlays = if (!isOverlayHidden) {
      timeline.overlayClips.filter {
        !it.isHidden && posMs >= it.timelineStartMs && posMs < it.timelineStartMs + it.durationMs
      }.map { clip ->
        val rel = posMs - clip.timelineStartMs
        val kf = KeyframeInterpolator.interpolate(clip, rel)
        ComposedOverlay(
          clip = clip,
          sourcePosMs = clip.timelineToSourceMs(posMs),
          posX = kf.posX,
          posY = kf.posY,
          scaleX = kf.scaleX,
          scaleY = kf.scaleY,
          rotation = kf.rotation,
          opacity = kf.opacity,
          blendMode = clip.blendMode,
          blur = kf.blur,
          brightness = kf.brightness,
          contrast = kf.contrast,
          saturation = kf.saturation,
          effectParam = kf.effectParam
        )
      }
    } else emptyList()

    // Texts
    val texts = if (!isTextHidden) {
      timeline.textClips.filter {
        !it.isHidden && posMs >= it.timelineStartMs && posMs < it.timelineStartMs + it.durationMs
      }.sortedWith(compareBy({ it.trackIndex }, { it.timelineStartMs })).map { clip ->
        val state = TextLayerRenderer.evaluateAnimation(clip, posMs)
        ComposedText(
          clip = clip,
          posX = state.posX,
          posY = state.posY,
          scale = state.scale,
          rotation = state.rotation,
          opacity = state.opacity,
          currentPosMs = posMs
        )
      }
    } else emptyList()

    // Stickers
    val stickers = if (!isStickerHidden) {
      timeline.stickerClips.filter {
        !it.isHidden && posMs >= it.timelineStartMs && posMs < it.timelineStartMs + it.durationMs
      }.map { clip ->
        val state = StickerLayerRenderer.evaluateAnimation(clip, posMs)
        ComposedSticker(
          clip = clip,
          posX = state.posX,
          posY = state.posY,
          scale = state.scale,
          rotation = state.rotation,
          opacity = state.opacity
        )
      }
    } else emptyList()

    // Visual Effects with Keyframes
    val isEffectHidden = timeline.trackSettings[TrackType.EFFECT]?.isHidden == true
    val activeEffects = if (!isEffectHidden) {
      timeline.effectClips.filter { clip ->
        !clip.isHidden && (
          (posMs >= clip.timelineStartMs && posMs < clip.timelineStartMs + clip.durationMs) ||
          (clip.targetClipId != null && activeClip != null && clip.targetClipId == activeClip.id)
        )
      }.sortedBy { it.timelineStartMs }.map { clip ->
        val relTime = (posMs - clip.timelineStartMs).coerceAtLeast(0L)
        val intensity = KeyframeInterpolator.interpolateEffectIntensity(clip, relTime)
        ComposedEffect(
          clip = clip,
          effectType = clip.effectType,
          intensity = intensity,
          timeInEffectMs = relTime,
          progress = (relTime.toFloat() / clip.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
        )
      }
    } else emptyList()

    // Adjustments & Filters with active effect color matrix post-concatenated
    val baseMatrix = ColorFilterGenerator.createCombinedMatrix(timeline.adjustments, timeline.filter, activeClip?.filter)
    val colorMatrix = ColorMatrix(baseMatrix)
    if (activeEffects.isNotEmpty()) {
      val effectMat = VideoEffectRenderer.calculateEffectColorMatrix(activeEffects.map { it.clip }, posMs)
      if (effectMat != null) {
        colorMatrix.postConcat(effectMat)
      }
    }
    val colorFilter = ColorMatrixColorFilter(colorMatrix)

    val activeClipTransform = if (activeClip != null) {
      val rel = posMs - activeClip.timelineStartMs
      KeyframeInterpolator.interpolate(activeClip, rel)
    } else null

    return ComposedFrame(
      timelinePosMs = posMs,
      activeClip = activeClip,
      clipSourcePosMs = sourcePosMs,
      activeOverlays = overlays,
      activeTexts = texts,
      activeStickers = stickers,
      activeTransition = activeTransition,
      activeEffects = activeEffects,
      colorMatrix = colorMatrix,
      colorFilter = colorFilter,
      activeClipTransform = activeClipTransform
    )
  }

  /**
   * Renders the composed frame onto an Android Canvas for export or preview capture.
   */
  fun renderFrame(
    canvas: Canvas,
    frame: ComposedFrame,
    mainBitmap: Bitmap?,
    overlayBitmaps: Map<String, Bitmap>,
    canvasWidth: Int,
    canvasHeight: Int,
    chromaKey: ChromaKeySettings = ChromaKeySettings()
  ) {
    // 1. Draw canvas background
    canvas.drawColor(Color.BLACK)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    paint.colorFilter = frame.colorFilter

    // 2. Draw Main Clip Bitmap
    if (mainBitmap != null && !mainBitmap.isRecycled) {
      val clip = frame.activeClip
      val scaleX = canvasWidth.toFloat() / mainBitmap.width
      val scaleY = canvasHeight.toFloat() / mainBitmap.height
      val baseScale = min(scaleX, scaleY)

      val matrix = Matrix()
      // Center bitmap
      matrix.postTranslate(-mainBitmap.width / 2f, -mainBitmap.height / 2f)

      if (clip != null) {
        val rel = frame.timelinePosMs - clip.timelineStartMs
        val kf = frame.activeClipTransform ?: KeyframeInterpolator.interpolate(clip, rel)
        matrix.postScale(
          if (clip.flipHorizontal) -1f else 1f,
          if (clip.flipVertical) -1f else 1f
        )
        matrix.postRotate((clip.rotationDegrees + kf.rotation) % 360)
        matrix.postScale(baseScale * clip.cropScale * kf.scaleX, baseScale * clip.cropScale * kf.scaleY)
        matrix.postTranslate(
          (canvasWidth / 2f) + (clip.cropOffsetX + kf.posX) * (canvasWidth / 2f),
          (canvasHeight / 2f) + (clip.cropOffsetY + kf.posY) * (canvasHeight / 2f)
        )
        paint.alpha = (kf.opacity * 255).toInt().coerceIn(0, 255)
      } else {
        matrix.postScale(baseScale, baseScale)
        matrix.postTranslate(canvasWidth / 2f, canvasHeight / 2f)
      }

      // Apply accumulated camera motion from active effects (Shake, Zoom, Skater Zoom, Vertigo Dolly, Spin, Mirror)
      if (frame.activeEffects.isNotEmpty()) {
        val motion = VideoEffectRenderer.calculateMotionTransform(frame.activeEffects.map { it.clip }, frame.timelinePosMs)
        matrix.postScale(motion.scaleX, motion.scaleY, canvasWidth / 2f, canvasHeight / 2f)
        matrix.postRotate(motion.rotation, canvasWidth / 2f, canvasHeight / 2f)
        matrix.postTranslate(motion.translationX * canvasWidth, motion.translationY * canvasHeight)
        paint.alpha = ((paint.alpha / 255f) * motion.alpha * 255f).toInt().coerceIn(0, 255)
      }

      // Handle transition blending if active
      if (frame.activeTransition != null) {
        val tr = frame.activeTransition
        when (tr.type) {
          TransitionType.FADE, TransitionType.DISSOLVE -> {
            paint.alpha = ((1f - tr.progress) * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(mainBitmap, matrix, paint)
          }
          TransitionType.WIPE -> {
            canvas.save()
            val clipRight = canvasWidth * (1f - tr.progress)
            canvas.clipRect(0f, 0f, clipRight, canvasHeight.toFloat())
            canvas.drawBitmap(mainBitmap, matrix, paint)
            canvas.restore()
          }
          TransitionType.SLIDE_LEFT -> {
            matrix.postTranslate(-canvasWidth * tr.progress, 0f)
            canvas.drawBitmap(mainBitmap, matrix, paint)
          }
          TransitionType.SLIDE_RIGHT -> {
            matrix.postTranslate(canvasWidth * tr.progress, 0f)
            canvas.drawBitmap(mainBitmap, matrix, paint)
          }
          TransitionType.PUSH_UP -> {
            matrix.postTranslate(0f, -canvasHeight * tr.progress)
            canvas.drawBitmap(mainBitmap, matrix, paint)
          }
          TransitionType.ZOOM_IN -> {
            val zoom = 1f + tr.progress * 0.6f
            matrix.postScale(zoom, zoom, canvasWidth / 2f, canvasHeight / 2f)
            paint.alpha = ((1f - tr.progress * 0.5f) * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(mainBitmap, matrix, paint)
          }
          TransitionType.ZOOM_OUT -> {
            val zoom = (1f - tr.progress * 0.4f).coerceAtLeast(0.1f)
            matrix.postScale(zoom, zoom, canvasWidth / 2f, canvasHeight / 2f)
            paint.alpha = ((1f - tr.progress * 0.5f) * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(mainBitmap, matrix, paint)
          }
          TransitionType.SPIN -> {
            matrix.postRotate(tr.progress * 360f, canvasWidth / 2f, canvasHeight / 2f)
            val zoom = (1f - tr.progress * 0.5f).coerceAtLeast(0.1f)
            matrix.postScale(zoom, zoom, canvasWidth / 2f, canvasHeight / 2f)
            paint.alpha = ((1f - tr.progress) * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(mainBitmap, matrix, paint)
          }
          TransitionType.BLUR -> {
            val zoom = 1f + tr.progress * 0.3f
            matrix.postScale(zoom, zoom, canvasWidth / 2f, canvasHeight / 2f)
            paint.alpha = ((1f - tr.progress) * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(mainBitmap, matrix, paint)
          }
          TransitionType.FLASH -> {
            paint.alpha = 255
            canvas.drawBitmap(mainBitmap, matrix, paint)
            // Draw white flash rectangle overlay
            val flashAlpha = (if (tr.progress < 0.5f) tr.progress * 2f else (1f - tr.progress) * 2f).coerceIn(0f, 1f)
            val flashPaint = Paint().apply {
              color = Color.WHITE
              alpha = (flashAlpha * 240).toInt().coerceIn(0, 255)
            }
            canvas.drawRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), flashPaint)
          }
          TransitionType.GLITCH -> {
            val jitterX = if (tr.progress in 0.2f..0.8f) ((Math.random() - 0.5) * 30).toFloat() else 0f
            val jitterY = if (tr.progress in 0.2f..0.8f) ((Math.random() - 0.5) * 20).toFloat() else 0f
            matrix.postTranslate(jitterX, jitterY)
            paint.alpha = ((1f - tr.progress) * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(mainBitmap, matrix, paint)
          }
          else -> {
            paint.alpha = 255
            canvas.drawBitmap(mainBitmap, matrix, paint)
          }
        }
      } else {
        paint.alpha = 255
        canvas.drawBitmap(mainBitmap, matrix, paint)
      }
    }

    // 3. Draw Overlays (PIP)
    for (composedOverlay in frame.activeOverlays) {
      val rawOverlayBitmap = overlayBitmaps[composedOverlay.clip.id]
      if (rawOverlayBitmap != null && !rawOverlayBitmap.isRecycled) {
        val overlayBitmap = if (chromaKey.enabled) {
          ChromaKeyProcessor.applyChromaKey(rawOverlayBitmap, chromaKey)
        } else rawOverlayBitmap

        val overlayMatrix = Matrix()
        overlayMatrix.postTranslate(-overlayBitmap.width / 2f, -overlayBitmap.height / 2f)
        overlayMatrix.postRotate(composedOverlay.rotation)
        val overlayScaleX = (canvasWidth.toFloat() / overlayBitmap.width) * composedOverlay.scaleX * 0.5f
        val overlayScaleY = (canvasWidth.toFloat() / overlayBitmap.width) * composedOverlay.scaleY * 0.5f
        overlayMatrix.postScale(overlayScaleX, overlayScaleY)

        val targetX = (canvasWidth / 2f) + (composedOverlay.posX * canvasWidth / 2f)
        val targetY = (canvasHeight / 2f) + (composedOverlay.posY * canvasHeight / 2f)
        overlayMatrix.postTranslate(targetX, targetY)

        val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        overlayPaint.alpha = (composedOverlay.opacity * 255).toInt().coerceIn(0, 255)

        // Comprehensive Blend Mode Support
        BlendModeEvaluator.applyBlendModeToPaint(overlayPaint, composedOverlay.blendMode)

        canvas.drawBitmap(overlayBitmap, overlayMatrix, overlayPaint)
      }
    }

    // 4. Draw Text Overlays
    for (composedText in frame.activeTexts) {
      drawTextClip(canvas, composedText, canvasWidth, canvasHeight)
    }

    // 5. Draw Stickers
    for (sticker in frame.activeStickers) {
      drawStickerClip(canvas, sticker, canvasWidth, canvasHeight)
    }

    // 6. Apply Active Visual Effects (Flash, Glow, Glitch, Light Leak, Lens Flare, RGB Split)
    for (effect in frame.activeEffects) {
      drawVisualEffect(canvas, effect, canvasWidth, canvasHeight)
    }
  }

  /**
   * Evaluates the complete multi-layer scene graph for arbitrary tracks, clips, and Z-orders.
   */
  fun evaluateComposition(
    state: CoreTimelineState,
    posUs: Long,
    isolatedTrackId: String? = null,
    isolatedClipId: String? = null
  ): CompositionFrameDescriptor {
    return LayerCompositor.evaluateComposition(state, posUs, isolatedTrackId, isolatedClipId)
  }

  /**
   * Deterministically renders a multi-layer composition descriptor onto a Canvas.
   */
  fun renderComposition(
    canvas: Canvas,
    descriptor: CompositionFrameDescriptor,
    bitmaps: Map<String, Bitmap>,
    canvasWidth: Int,
    canvasHeight: Int
  ) {
    // 1. Draw Canvas background color
    canvas.drawColor(descriptor.canvasBackgroundColor.toInt())

    // 2. Render each active visual layer in deterministic Z-order
    for (node in descriptor.visualLayers) {
      if (!node.isVisible || node.opacity <= 0.001f) continue

      // Check if this layer has an active transition applied
      val activeTr = descriptor.activeTransitionDescriptors.firstOrNull {
        it.fromClipId == node.clipId || it.toClipId == node.clipId
      }

      val bmp = bitmaps[node.clipId] ?: bitmaps[node.sourceMediaUri]
      if (bmp != null && !bmp.isRecycled) {
        val transform = node.evaluatedTransform
        val matrix = Matrix()
        matrix.postTranslate(-bmp.width / 2f, -bmp.height / 2f)

        if (node.transform.flipX) matrix.postScale(-1f, 1f)
        if (node.transform.flipY) matrix.postScale(1f, -1f)

        matrix.postRotate((node.transform.rotation + transform.rotation) % 360f)
        val scaleX = (canvasWidth.toFloat() / bmp.width) * transform.scaleX
        val scaleY = (canvasHeight.toFloat() / bmp.height) * transform.scaleY
        matrix.postScale(scaleX, scaleY)

        val targetX = (canvasWidth / 2f) + (transform.posX * canvasWidth / 2f) + (node.motionTrackingOffset.first * canvasWidth / 2f)
        val targetY = (canvasHeight / 2f) + (transform.posY * canvasHeight / 2f) + (node.motionTrackingOffset.second * canvasHeight / 2f)
        matrix.postTranslate(targetX, targetY)

        var layerOpacity = node.opacity
        var shouldClipWipe = false
        var wipeClipRight = canvasWidth.toFloat()

        if (activeTr != null) {
          val isOutgoing = activeTr.fromClipId == node.clipId
          val progress = activeTr.progress
          when (activeTr.type) {
            TransitionType.FADE, TransitionType.DISSOLVE -> {
              layerOpacity *= if (isOutgoing) (1f - progress) else progress
            }
            TransitionType.WIPE -> {
              if (isOutgoing) {
                shouldClipWipe = true
                wipeClipRight = canvasWidth * (1f - progress)
              }
            }
            TransitionType.SLIDE_LEFT -> {
              val offset = if (isOutgoing) -canvasWidth * progress else canvasWidth * (1f - progress)
              matrix.postTranslate(offset, 0f)
            }
            TransitionType.SLIDE_RIGHT -> {
              val offset = if (isOutgoing) canvasWidth * progress else -canvasWidth * (1f - progress)
              matrix.postTranslate(offset, 0f)
            }
            TransitionType.PUSH_UP -> {
              val offset = if (isOutgoing) -canvasHeight * progress else canvasHeight * (1f - progress)
              matrix.postTranslate(0f, offset)
            }
            TransitionType.ZOOM_IN -> {
              val zoomFactor = if (isOutgoing) 1f + progress * 0.5f else 0.5f + progress * 0.5f
              matrix.postScale(zoomFactor, zoomFactor, canvasWidth / 2f, canvasHeight / 2f)
              layerOpacity *= if (isOutgoing) (1f - progress * 0.5f) else progress
            }
            TransitionType.ZOOM_OUT -> {
              val zoomFactor = if (isOutgoing) (1f - progress * 0.4f).coerceAtLeast(0.1f) else 1.4f - (progress * 0.4f)
              matrix.postScale(zoomFactor, zoomFactor, canvasWidth / 2f, canvasHeight / 2f)
              layerOpacity *= if (isOutgoing) (1f - progress * 0.5f) else progress
            }
            TransitionType.SPIN -> {
              val rot = if (isOutgoing) progress * 360f else (progress - 1f) * 360f
              matrix.postRotate(rot, canvasWidth / 2f, canvasHeight / 2f)
              layerOpacity *= if (isOutgoing) (1f - progress) else progress
            }
            TransitionType.BLUR, TransitionType.ZOOM_BLUR -> {
              layerOpacity *= if (isOutgoing) (1f - progress) else progress
            }
            TransitionType.GLITCH, TransitionType.GLITCH_WIPE -> {
              if (progress in 0.15f..0.85f) {
                val jitterX = ((Math.random() - 0.5) * 24).toFloat()
                val jitterY = ((Math.random() - 0.5) * 16).toFloat()
                matrix.postTranslate(jitterX, jitterY)
              }
              layerOpacity *= if (isOutgoing) (1f - progress) else progress
            }
            else -> {}
          }
        }

        val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        layerPaint.alpha = (layerOpacity.coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255)
        BlendModeEvaluator.applyBlendModeToPaint(layerPaint, node.blendMode.id)

        // Clip mask if enabled
        val maskPath = node.createMaskPath(canvasWidth.toFloat(), canvasHeight.toFloat())
        if (maskPath != null || shouldClipWipe) {
          canvas.save()
          if (shouldClipWipe) {
            canvas.clipRect(0f, 0f, wipeClipRight, canvasHeight.toFloat())
          }
          if (maskPath != null) {
            canvas.clipPath(maskPath)
          }
          canvas.drawBitmap(bmp, matrix, layerPaint)
          canvas.restore()
        } else {
          canvas.drawBitmap(bmp, matrix, layerPaint)
        }
      } else if (node.textPayload != null) {
        val textClip = node.textPayload
        val legacyText = TextClip(
          id = node.clipId,
          text = textClip.text,
          timelineStartMs = node.timelineStartMs,
          durationMs = node.durationMs,
          fontSizeSp = textClip.fontSizeSp,
          textColor = textClip.textColor,
          fontFamily = textClip.fontFamily,
          posX = node.evaluatedTransform.posX,
          posY = node.evaluatedTransform.posY,
          scale = node.evaluatedTransform.scaleX,
          rotation = node.evaluatedTransform.rotation,
          opacity = node.opacity
        )
        TextLayerRenderer.draw(
          canvas = canvas,
          clip = legacyText,
          currentPosMs = descriptor.timelinePosMs,
          width = canvasWidth,
          height = canvasHeight,
          context = context
        )
      } else if (node.stickerPayload != null) {
        val sticker = node.stickerPayload
        val legacySticker = StickerClip(
          id = node.clipId,
          emojiOrAsset = sticker.emojiOrAsset,
          timelineStartMs = node.timelineStartMs,
          durationMs = node.durationMs,
          posX = node.evaluatedTransform.posX,
          posY = node.evaluatedTransform.posY,
          scale = node.evaluatedTransform.scaleX,
          rotation = node.evaluatedTransform.rotation,
          opacity = node.opacity,
          animationType = sticker.animationType,
          badgeType = sticker.badgeType
        )
        StickerLayerRenderer.draw(
          canvas = canvas,
          clip = legacySticker,
          currentPosMs = descriptor.timelinePosMs,
          width = canvasWidth,
          height = canvasHeight
        )
      }
    }

    // 3. Post-render Global Transition Overlays (White Flash, Light Leak)
    for (tr in descriptor.activeTransitionDescriptors) {
      if (tr.type == TransitionType.FLASH) {
        val flashAlpha = (if (tr.progress < 0.5f) tr.progress * 2f else (1f - tr.progress) * 2f).coerceIn(0f, 1f)
        val flashPaint = Paint().apply {
          color = Color.WHITE
          alpha = (flashAlpha * 240).toInt().coerceIn(0, 255)
        }
        canvas.drawRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), flashPaint)
      } else if (tr.type == TransitionType.LIGHT_LEAK) {
        val leakAlpha = (if (tr.progress < 0.5f) tr.progress * 2f else (1f - tr.progress) * 2f).coerceIn(0f, 1f)
        val leakPaint = Paint().apply {
          color = Color.rgb(255, 200, 100)
          alpha = (leakAlpha * 180).toInt().coerceIn(0, 255)
        }
        canvas.drawRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), leakPaint)
      }
    }
  }

  private fun drawVisualEffect(canvas: Canvas, effect: ComposedEffect, width: Int, height: Int) {
    if (effect.intensity <= 0.01f) return
    VideoEffectRenderer.renderSingleEffect(
      canvas = canvas,
      effect = effect.clip,
      intensity = effect.intensity,
      relTime = effect.timeInEffectMs,
      width = width,
      height = height
    )
  }

  private fun drawTextClip(canvas: Canvas, composedText: ComposedText, width: Int, height: Int) {
    TextLayerRenderer.draw(
      canvas = canvas,
      clip = composedText.clip,
      currentPosMs = composedText.currentPosMs,
      width = width,
      height = height,
      context = context
    )
  }

  private fun drawStickerClip(canvas: Canvas, sticker: ComposedSticker, width: Int, height: Int) {
    StickerLayerRenderer.draw(
      canvas = canvas,
      clip = sticker.clip.copy(
        posX = sticker.posX,
        posY = sticker.posY,
        scale = sticker.scale,
        rotation = sticker.rotation,
        opacity = sticker.opacity
      ),
      currentPosMs = sticker.clip.timelineStartMs,
      width = width,
      height = height
    )
  }
}
