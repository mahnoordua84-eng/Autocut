package com.example.engine.composition

import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import com.example.engine.composition.gpu.NativeBlendMode

/**
 * Standardized blend mode enumeration supporting professional NLE composite operations.
 */
enum class CompositeBlendMode(val id: String, val displayName: String, val nativeMode: NativeBlendMode) {
  NORMAL("normal", "Normal", NativeBlendMode.NORMAL),
  MULTIPLY("multiply", "Multiply", NativeBlendMode.MULTIPLY),
  SCREEN("screen", "Screen", NativeBlendMode.SCREEN),
  OVERLAY("overlay", "Overlay", NativeBlendMode.NORMAL),
  DARKEN("darken", "Darken", NativeBlendMode.NORMAL),
  LIGHTEN("lighten", "Lighten", NativeBlendMode.NORMAL),
  COLOR_DODGE("color_dodge", "Color Dodge", NativeBlendMode.ADDITIVE),
  COLOR_BURN("color_burn", "Color Burn", NativeBlendMode.MULTIPLY),
  HARD_LIGHT("hard_light", "Hard Light", NativeBlendMode.NORMAL),
  SOFT_LIGHT("soft_light", "Soft Light", NativeBlendMode.NORMAL),
  DIFFERENCE("difference", "Difference", NativeBlendMode.NORMAL),
  EXCLUSION("exclusion", "Exclusion", NativeBlendMode.NORMAL),
  ADDITIVE("additive", "Additive (Linear Dodge)", NativeBlendMode.ADDITIVE),
  PREMULTIPLIED("premultiplied", "Premultiplied Alpha", NativeBlendMode.PREMULTIPLIED);

  companion object {
    fun fromString(str: String?): CompositeBlendMode {
      if (str.isNullOrBlank()) return NORMAL
      val normalized = str.trim().lowercase().replace(" ", "_").replace("-", "_")
      return values().firstOrNull { it.id == normalized || it.displayName.lowercase() == str.trim().lowercase() }
        ?: when (normalized) {
          "normal", "srcover", "src_over" -> NORMAL
          "mult", "multiply" -> MULTIPLY
          "screen" -> SCREEN
          "overlay" -> OVERLAY
          "dark", "darken" -> DARKEN
          "light", "lighten" -> LIGHTEN
          "dodge", "colordodge", "color_dodge" -> COLOR_DODGE
          "burn", "colorburn", "color_burn" -> COLOR_BURN
          "hardlight", "hard_light" -> HARD_LIGHT
          "softlight", "soft_light" -> SOFT_LIGHT
          "diff", "difference" -> DIFFERENCE
          "exclusion" -> EXCLUSION
          "add", "additive", "linear_dodge" -> ADDITIVE
          "premult", "premultiplied", "premultiplied_alpha" -> PREMULTIPLIED
          else -> NORMAL
        }
    }
  }
}

/**
 * Evaluates and configures Paint Xfermodes and Android Graphics blend modes for Canvas CPU rendering.
 */
object BlendModeEvaluator {

  fun applyBlendModeToPaint(paint: Paint, blendModeStr: String?) {
    val mode = CompositeBlendMode.fromString(blendModeStr)
    when (mode) {
      CompositeBlendMode.NORMAL -> {
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
      }
      CompositeBlendMode.MULTIPLY -> {
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
      }
      CompositeBlendMode.SCREEN -> {
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
      }
      CompositeBlendMode.ADDITIVE -> {
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
      }
      CompositeBlendMode.LIGHTEN -> {
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
      }
      CompositeBlendMode.DARKEN -> {
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
      }
      CompositeBlendMode.OVERLAY -> {
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
      }
      CompositeBlendMode.PREMULTIPLIED -> {
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
      }
      else -> {
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
      }
    }
  }

  fun toNativeBlendMode(blendModeStr: String?): NativeBlendMode {
    return CompositeBlendMode.fromString(blendModeStr).nativeMode
  }
}
