package com.example.engine.animation

/**
 * Categorization of animatable properties in the keyframe engine.
 */
enum class PropertyCategory(val displayName: String) {
  TRANSFORM("Transform"),
  OPACITY("Opacity"),
  CROP("Crop"),
  AUDIO("Audio"),
  COLOR("Color & Tone"),
  FILTER_EFFECT("Filter & Effect"),
  MASK("Mask & Geometry"),
  CUSTOM("Custom")
}

/**
 * Comprehensive list of supported animatable properties with metadata,
 * default values, and valid ranges.
 */
enum class StandardAnimatableProperty(
  val propertyKey: String,
  val displayName: String,
  val category: PropertyCategory,
  val defaultValue: Float,
  val minValue: Float,
  val maxValue: Float,
  val unit: String = ""
) {
  // 1. Transform Properties
  POSITION_X("posX", "Position X", PropertyCategory.TRANSFORM, 0.0f, -5.0f, 5.0f, "norm"),
  POSITION_Y("posY", "Position Y", PropertyCategory.TRANSFORM, 0.0f, -5.0f, 5.0f, "norm"),
  SCALE_X("scaleX", "Scale X", PropertyCategory.TRANSFORM, 1.0f, 0.01f, 20.0f, "x"),
  SCALE_Y("scaleY", "Scale Y", PropertyCategory.TRANSFORM, 1.0f, 0.01f, 20.0f, "x"),
  SCALE_UNIFORM("scale", "Uniform Scale", PropertyCategory.TRANSFORM, 1.0f, 0.01f, 20.0f, "x"),
  ROTATION("rotation", "Rotation", PropertyCategory.TRANSFORM, 0.0f, -3600.0f, 3600.0f, "°"),
  ANCHOR_X("anchorX", "Anchor X", PropertyCategory.TRANSFORM, 0.5f, -2.0f, 2.0f, "norm"),
  ANCHOR_Y("anchorY", "Anchor Y", PropertyCategory.TRANSFORM, 0.5f, -2.0f, 2.0f, "norm"),

  // 2. Opacity & Blending
  OPACITY("opacity", "Opacity", PropertyCategory.OPACITY, 1.0f, 0.0f, 1.0f, "%"),

  // 3. Crop Properties (Normalized 0.0 to 1.0 from edges)
  CROP_LEFT("cropLeft", "Crop Left", PropertyCategory.CROP, 0.0f, 0.0f, 1.0f, "%"),
  CROP_TOP("cropTop", "Crop Top", PropertyCategory.CROP, 0.0f, 0.0f, 1.0f, "%"),
  CROP_RIGHT("cropRight", "Crop Right", PropertyCategory.CROP, 0.0f, 0.0f, 1.0f, "%"),
  CROP_BOTTOM("cropBottom", "Crop Bottom", PropertyCategory.CROP, 0.0f, 0.0f, 1.0f, "%"),

  // 4. Audio Properties
  VOLUME("volume", "Volume", PropertyCategory.AUDIO, 1.0f, 0.0f, 4.0f, "x"),
  AUDIO_PITCH("audioPitch", "Pitch Shift", PropertyCategory.AUDIO, 0.0f, -24.0f, 24.0f, "semitones"),
  AUDIO_LOW_GAIN("lowGain", "Bass Gain", PropertyCategory.AUDIO, 0.0f, -24.0f, 24.0f, "dB"),
  AUDIO_MID_GAIN("midGain", "Mid Gain", PropertyCategory.AUDIO, 0.0f, -24.0f, 24.0f, "dB"),
  AUDIO_HIGH_GAIN("highGain", "Treble Gain", PropertyCategory.AUDIO, 0.0f, -24.0f, 24.0f, "dB"),

  // 5. Blur & Visual Effects
  BLUR("blur", "Gaussian Blur", PropertyCategory.FILTER_EFFECT, 0.0f, 0.0f, 1.0f, "%"),
  EFFECT_PARAM("effectParam", "Effect Parameter", PropertyCategory.FILTER_EFFECT, 0.0f, 0.0f, 1.0f, "%"),
  EFFECT_INTENSITY("effectIntensity", "Effect Intensity", PropertyCategory.FILTER_EFFECT, 1.0f, 0.0f, 2.0f, "%"),

  // 6. Color & Grading Parameters
  BRIGHTNESS("brightness", "Brightness", PropertyCategory.COLOR, 0.0f, -1.0f, 1.0f, "norm"),
  CONTRAST("contrast", "Contrast", PropertyCategory.COLOR, 1.0f, 0.0f, 3.0f, "x"),
  SATURATION("saturation", "Saturation", PropertyCategory.COLOR, 1.0f, 0.0f, 3.0f, "x"),
  TEMPERATURE("temperature", "Color Temperature", PropertyCategory.COLOR, 0.0f, -1.0f, 1.0f, "norm"),
  TINT("tint", "Tint (Green/Magenta)", PropertyCategory.COLOR, 0.0f, -1.0f, 1.0f, "norm"),
  HUE("hue", "Hue Rotation", PropertyCategory.COLOR, 0.0f, -180.0f, 180.0f, "°"),
  EXPOSURE("exposure", "Exposure", PropertyCategory.COLOR, 0.0f, -3.0f, 3.0f, "EV"),
  VIGNETTE("vignette", "Vignette Amount", PropertyCategory.COLOR, 0.0f, 0.0f, 1.0f, "%"),
  HIGHLIGHTS("highlights", "Highlights", PropertyCategory.COLOR, 0.0f, -1.0f, 1.0f, "norm"),
  SHADOWS("shadows", "Shadows", PropertyCategory.COLOR, 0.0f, -1.0f, 1.0f, "norm"),

  // 7. Mask & Shape Geometry
  MASK_POS_X("maskPosX", "Mask Position X", PropertyCategory.MASK, 0.0f, -2.0f, 2.0f, "norm"),
  MASK_POS_Y("maskPosY", "Mask Position Y", PropertyCategory.MASK, 0.0f, -2.0f, 2.0f, "norm"),
  MASK_WIDTH("maskWidth", "Mask Width", PropertyCategory.MASK, 0.6f, 0.0f, 4.0f, "norm"),
  MASK_HEIGHT("maskHeight", "Mask Height", PropertyCategory.MASK, 0.6f, 0.0f, 4.0f, "norm"),
  MASK_ROTATION("maskRotation", "Mask Rotation", PropertyCategory.MASK, 0.0f, -360.0f, 360.0f, "°"),
  MASK_FEATHER("maskFeather", "Mask Feather", PropertyCategory.MASK, 0.1f, 0.0f, 1.0f, "%"),
  MASK_OPACITY("maskOpacity", "Mask Opacity", PropertyCategory.MASK, 1.0f, 0.0f, 1.0f, "%");

  companion object {
    private val keyMap = values().associateBy { it.propertyKey.lowercase() }

    fun fromKey(key: String): StandardAnimatableProperty? {
      return keyMap[key.lowercase()]
    }
  }
}

/**
 * Extensible property identifier supporting both standard known properties
 * and custom dynamic plugin/shader properties.
 */
sealed class AnimatablePropertyKey {
  abstract val identifier: String
  abstract val displayName: String
  abstract val defaultValue: Float
  abstract val minValue: Float
  abstract val maxValue: Float
  abstract val category: PropertyCategory

  data class Standard(val property: StandardAnimatableProperty) : AnimatablePropertyKey() {
    override val identifier: String get() = property.propertyKey
    override val displayName: String get() = property.displayName
    override val defaultValue: Float get() = property.defaultValue
    override val minValue: Float get() = property.minValue
    override val maxValue: Float get() = property.maxValue
    override val category: PropertyCategory get() = property.category
  }

  data class Custom(
    override val identifier: String,
    override val displayName: String = identifier,
    override val defaultValue: Float = 0.0f,
    override val minValue: Float = Float.NEGATIVE_INFINITY,
    override val maxValue: Float = Float.POSITIVE_INFINITY,
    override val category: PropertyCategory = PropertyCategory.CUSTOM,
    val unit: String = ""
  ) : AnimatablePropertyKey()

  companion object {
    fun of(property: StandardAnimatableProperty): AnimatablePropertyKey = Standard(property)
    fun custom(key: String, defaultValue: Float = 0f, min: Float = 0f, max: Float = 1f): AnimatablePropertyKey =
      Custom(identifier = key, defaultValue = defaultValue, minValue = min, maxValue = max)
  }
}
