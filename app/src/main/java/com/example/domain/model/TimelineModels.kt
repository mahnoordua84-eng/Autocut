package com.example.domain.model

import java.util.UUID

enum class KeyframeInterpolation(val displayName: String) {
  LINEAR("Linear"),
  EASE_IN("Ease In"),
  EASE_OUT("Ease Out"),
  EASE_IN_OUT("Ease In-Out"),
  CUBIC_BEZIER("Cubic Bezier"),
  HOLD("Hold"),
  CUSTOM_CURVE("Custom Curve");

  companion object {
    fun fromString(str: String): KeyframeInterpolation {
      return when (str.trim().lowercase()) {
        "linear" -> LINEAR
        "ease in", "easein", "ease_in" -> EASE_IN
        "ease out", "easeout", "ease_out" -> EASE_OUT
        "ease in-out", "easeinout", "ease_in_out", "smoothease" -> EASE_IN_OUT
        "cubic bezier", "cubic", "bezier" -> CUBIC_BEZIER
        "hold", "step" -> HOLD
        "custom curve", "custom" -> CUSTOM_CURVE
        else -> LINEAR
      }
    }
  }
}

enum class MaskShape(val displayName: String) {
  NONE("None"),
  RECTANGLE("Rectangle (Shape)"),
  CIRCLE("Circle (Radial)"),
  LINEAR("Linear Wipe"),
  MIRROR("Mirror Split"),
  STAR("Star"),
  HEART("Heart")
}

data class MaskSettings(
  val enabled: Boolean = false,
  val shape: MaskShape = MaskShape.RECTANGLE,
  val posX: Float = 0f,         // -1f to 1f normalized center offset
  val posY: Float = 0f,         // -1f to 1f normalized center offset
  val width: Float = 0.6f,       // 0f to 2f
  val height: Float = 0.6f,      // 0f to 2f
  val rotation: Float = 0f,      // 0 to 360 degrees
  val feather: Float = 0.1f,     // 0f to 1f edge softness
  val opacity: Float = 1.0f,     // 0f to 1f
  val isInverted: Boolean = false
)

enum class SpeedCurvePreset(val displayName: String) {
  STANDARD("Standard (Linear)"),
  EASE_IN("Ease In"),
  EASE_OUT("Ease Out"),
  HERO_MONTAGE("Hero Montage (Fast-Slow-Fast)"),
  BULLET_TIME("Bullet Time (Slow-Fast-Slow)"),
  JUMPER("Jumper (Accelerate-Pause)"),
  CUSTOM_BEZIER("Custom Bezier Curve")
}

data class SpeedPoint(
  val x: Float = 0f,
  val y: Float = 0f
)

data class SpeedCurve(
  val preset: SpeedCurvePreset = SpeedCurvePreset.STANDARD,
  val bezierPoints: List<Float> = listOf(0.42f, 0.0f, 0.58f, 1.0f),
  val velocityGraph: List<SpeedPoint> = emptyList()
)

enum class VoiceEffect(val displayName: String) {
  NONE("Normal (Original)"),
  DEEP_VOICE("Deep Voice (Monster)"),
  CHIPMUNK("Chipmunk (High Pitch)"),
  ROBOT("Synthesized Robot"),
  ECHO_REVERB("Echo & Cathedral Reverb"),
  TELEPHONE("Vintage Radio / Telephone"),
  ANONYMOUS("Anonymous Pitch Shift"),
  ALIEN("Alien Extra-Terrestrial"),
  GIANT("Low Giant / Titan"),
  ELF("Tiny Pixie / Elf"),
  MEGAPHONE("Megaphone Bullhorn"),
  AUTOTUNE("AutoTune Melodic"),
  CHIPTUNE("8-Bit Chiptune"),
  VOCODER("Cyberpunk Vocoder"),
  SPEECH_TO_SONG("Speech to Song"),
  DISCO("Disco Harmonizer"),
  RADIO("Vintage AM Radio"),
  CARTOON("Cartoon Character")
}

data class AudioEffectsSettings(
  val noiseReductionDb: Float = 0.0f,
  val voiceEffect: VoiceEffect = VoiceEffect.NONE,
  val pitchShiftSemitones: Float = 0.0f,
  val lowGainDb: Float = 0.0f,
  val midGainDb: Float = 0.0f,
  val highGainDb: Float = 0.0f,
  val normalizeVolume: Boolean = false,
  val compressorThresholdDb: Float = 0.0f
)

data class ClipKeyframe(
  val id: String = UUID.randomUUID().toString(),
  val timeMs: Long,
  val posX: Float = 0f,
  val posY: Float = 0f,
  val scaleX: Float = 1f,
  val scaleY: Float = 1f,
  val rotation: Float = 0f,
  val opacity: Float = 1f,
  val volume: Float = 1f,
  val blur: Float = 0f,
  val brightness: Float = 0f,
  val contrast: Float = 1f,
  val saturation: Float = 1f,
  val effectParam: Float = 0f,
  val maskPosX: Float = 0f,
  val maskPosY: Float = 0f,
  val maskWidth: Float = 0.6f,
  val maskHeight: Float = 0.6f,
  val maskRotation: Float = 0f,
  val maskFeather: Float = 0.1f,
  val maskOpacity: Float = 1.0f,
  val interpolation: KeyframeInterpolation = KeyframeInterpolation.LINEAR,
  val customCurvePoints: List<Float> = listOf(0.42f, 0.0f, 0.58f, 1.0f) // P1x, P1y, P2x, P2y
) {
  val scale: Float get() = (scaleX + scaleY) / 2f

  constructor(
    id: String = UUID.randomUUID().toString(),
    timeMs: Long,
    scale: Float,
    rotation: Float = 0f,
    posX: Float = 0f,
    posY: Float = 0f,
    opacity: Float = 1f,
    volume: Float = 1f,
    interpolation: String = "Linear"
  ) : this(
    id = id,
    timeMs = timeMs,
    posX = posX,
    posY = posY,
    scaleX = scale,
    scaleY = scale,
    rotation = rotation,
    opacity = opacity,
    volume = volume,
    blur = 0f,
    brightness = 0f,
    contrast = 1f,
    saturation = 1f,
    effectParam = 0f,
    interpolation = KeyframeInterpolation.fromString(interpolation)
  )
}

enum class InAnimationType(val displayName: String, val category: String = "Popular") {
  NONE("None", "Basic"),
  FADE_IN("Fade In", "Fade & Zoom"),
  ZOOM_IN("Zoom In", "Fade & Zoom"),
  ZOOM_OUT("Zoom Out", "Fade & Zoom"),
  SLIDE_UP("Slide Up", "Slide"),
  SLIDE_DOWN("Slide Down", "Slide"),
  SLIDE_LEFT("Slide Left", "Slide"),
  SLIDE_RIGHT("Slide Right", "Slide"),
  SPIN_IN("Spin In", "Motion"),
  BOUNCE_IN("Bounce In", "Motion"),
  POP_IN("Pop In", "Motion"),
  FLIP_X("Flip Horizontal", "3D"),
  FLIP_Y("Flip Vertical", "3D"),
  SWING_IN("Swing In", "Motion"),
  ELASTIC_IN("Elastic In", "Dynamic"),
  GLITCH_IN("Glitch In", "Distortion"),
  WIPE_IN("Wipe In", "Basic"),
  BLUR_IN("Blur In", "Blur")
}

enum class OutAnimationType(val displayName: String, val category: String = "Popular") {
  NONE("None", "Basic"),
  FADE_OUT("Fade Out", "Fade & Zoom"),
  ZOOM_OUT("Zoom Out", "Fade & Zoom"),
  ZOOM_IN_OUT("Zoom In Disappear", "Fade & Zoom"),
  SLIDE_UP_OUT("Slide Up", "Slide"),
  SLIDE_DOWN_OUT("Slide Down", "Slide"),
  SLIDE_LEFT_OUT("Slide Left", "Slide"),
  SLIDE_RIGHT_OUT("Slide Right", "Slide"),
  SPIN_OUT("Spin Out", "Motion"),
  BOUNCE_OUT("Bounce Out", "Motion"),
  POP_OUT("Pop Out", "Motion"),
  FLIP_X_OUT("Flip Out", "3D"),
  SWING_OUT("Swing Out", "Motion"),
  GLITCH_OUT("Glitch Out", "Distortion"),
  WIPE_OUT("Wipe Out", "Basic"),
  BLUR_OUT("Blur Out", "Blur")
}

enum class ComboAnimationType(val displayName: String, val category: String = "Loop & Rhythm") {
  NONE("None", "Basic"),
  PULSE("Pulse Beat", "Rhythm"),
  HEARTBEAT("Heartbeat", "Rhythm"),
  PENDULUM("Pendulum Swing", "Motion"),
  FLOAT("Floating Drift", "Motion"),
  SHAKE("Camera Shake", "Distortion"),
  JITTER("Glitch Jitter", "Distortion"),
  FLASH_PULSE("Flash Strobe", "Lighting"),
  WAVE("Wave Wobble", "Motion"),
  SPIN_360("Spin 360 Loop", "Motion"),
  BREATHE("Breathe Flow", "Rhythm"),
  ZOOM_PULSE("Zoom Rhythm", "Rhythm")
}

enum class AnimationEasing(val displayName: String) {
  EASE_OUT("Ease Out (Smooth)"),
  EASE_IN("Ease In (Accelerate)"),
  EASE_IN_OUT("Ease In-Out"),
  LINEAR("Linear (Constant)"),
  OVERSHOOT("Overshoot (Spring)"),
  BOUNCE("Bounce"),
  ELASTIC("Elastic")
}

data class ClipAnimationSettings(
  val inType: InAnimationType = InAnimationType.NONE,
  val inDurationMs: Long = 500L,
  val outType: OutAnimationType = OutAnimationType.NONE,
  val outDurationMs: Long = 500L,
  val comboType: ComboAnimationType = ComboAnimationType.NONE,
  val intensity: Float = 1.0f,
  val easing: AnimationEasing = AnimationEasing.EASE_OUT,
  val speed: Float = 1.0f
) {
  val hasAnimation: Boolean
    get() = inType != InAnimationType.NONE || outType != OutAnimationType.NONE || comboType != ComboAnimationType.NONE
}

data class VideoClip(
  val id: String = UUID.randomUUID().toString(),
  val uri: String = "",
  val name: String,
  val isVideo: Boolean = true,
  val timelineStartMs: Long = 0L,
  val durationMs: Long = 3000L,
  val sourceStartMs: Long = 0L,
  val sourceEndMs: Long = 3000L,
  val speed: Float = 1.0f,
  val volume: Float = 1.0f,
  val rotationDegrees: Int = 0,
  val flipHorizontal: Boolean = false,
  val flipVertical: Boolean = false,
  val isMuted: Boolean = false,
  val cropScale: Float = 1.0f,
  val cropOffsetX: Float = 0f,
  val cropOffsetY: Float = 0f,
  val opacity: Float = 1.0f,
  val blendMode: String = "Normal",
  val width: Int = 1920,
  val height: Int = 1080,
  val naturalRotation: Int = 0,
  val frameRate: Float = 30f,
  val mimeType: String = "video/mp4",
  val hasAudio: Boolean = true,
  val isReversed: Boolean = false,
  val freezeFrameAtMs: Long? = null,
  val sourceTotalDurationMs: Long = 0L,
  val keyframes: List<ClipKeyframe> = emptyList(),
  val filter: FilterSettings? = null,
  val animation: ClipAnimationSettings = ClipAnimationSettings(),
  val mask: MaskSettings = MaskSettings(),
  val speedCurve: SpeedCurve = SpeedCurve(),
  val audioEffects: AudioEffectsSettings = AudioEffectsSettings(),
  val isLocked: Boolean = false,
  val isHidden: Boolean = false
) {
  val totalMediaDurationMs: Long
    get() = if (sourceTotalDurationMs > 0L) sourceTotalDurationMs else maxOf(sourceEndMs, durationMs)

  fun timelineToSourceMs(timelinePosMs: Long): Long {
    val offset = (timelinePosMs - timelineStartMs).coerceIn(0L, durationMs)
    val factor = if (speedCurve.preset != SpeedCurvePreset.STANDARD) {
      evaluateSpeedCurveFactor(offset.toFloat() / durationMs.coerceAtLeast(1L))
    } else 1.0f
    val scaledOffset = (offset * speed * factor).toLong()
    return if (isReversed) {
      (sourceEndMs - scaledOffset).coerceIn(sourceStartMs, sourceEndMs)
    } else {
      (sourceStartMs + scaledOffset).coerceIn(sourceStartMs, sourceEndMs)
    }
  }

  private fun evaluateSpeedCurveFactor(normalizedT: Float): Float {
    return when (speedCurve.preset) {
      SpeedCurvePreset.EASE_IN -> normalizedT * normalizedT
      SpeedCurvePreset.EASE_OUT -> 1f - (1f - normalizedT) * (1f - normalizedT)
      SpeedCurvePreset.HERO_MONTAGE -> if (normalizedT < 0.33f || normalizedT > 0.66f) 2.2f else 0.4f
      SpeedCurvePreset.BULLET_TIME -> if (normalizedT in 0.25f..0.75f) 0.25f else 1.8f
      SpeedCurvePreset.JUMPER -> if (normalizedT < 0.5f) 2.0f else 0.5f
      SpeedCurvePreset.CUSTOM_BEZIER -> {
        val pts = speedCurve.bezierPoints.ifEmpty { listOf(0.42f, 0f, 0.58f, 1f) }
        val p1x = pts.getOrNull(0) ?: 0.42f
        val p1y = pts.getOrNull(1) ?: 0.0f
        val p2x = pts.getOrNull(2) ?: 0.58f
        val p2y = pts.getOrNull(3) ?: 1.0f
        3f * (1f - normalizedT) * (1f - normalizedT) * normalizedT * p1y + 3f * (1f - normalizedT) * normalizedT * normalizedT * p2y + normalizedT * normalizedT * normalizedT
      }
      else -> 1.0f
    }
  }
}

data class AudioClip(
  val id: String = UUID.randomUUID().toString(),
  val uri: String,
  val title: String,
  val timelineStartMs: Long = 0L,
  val durationMs: Long = 3000L,
  val sourceStartMs: Long = 0L,
  val sourceEndMs: Long = 3000L,
  val volume: Float = 1.0f,
  val speed: Float = 1.0f,
  val fadeInMs: Long = 0L,
  val fadeOutMs: Long = 0L,
  val isMuted: Boolean = false,
  val isVoiceOver: Boolean = false,
  val waveformData: List<Float> = emptyList(),
  val gainDb: Float = 0.0f,
  val isReversed: Boolean = false,
  val keyframes: List<ClipKeyframe> = emptyList(),
  val speedCurve: SpeedCurve = SpeedCurve(),
  val audioEffects: AudioEffectsSettings = AudioEffectsSettings(),
  val isLocked: Boolean = false,
  val isHidden: Boolean = false
)

data class WordTiming(
  val word: String,
  val startMs: Long,
  val durationMs: Long
)

data class TextClip(
  val id: String = UUID.randomUUID().toString(),
  val text: String = "Tap to edit",
  val timelineStartMs: Long = 0L,
  val durationMs: Long = 3000L,
  val trackIndex: Int = 0,
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
  val opacity: Float = 1.0f,
  val rotation: Float = 0f,
  val posX: Float = 0f, // -1f to 1f normalized
  val posY: Float = 0.35f, // -1f to 1f normalized
  val scale: Float = 1f,
  val animationType: String = "Fade", // "None", "Fade", "Slide", "Zoom", "Bounce", "Typewriter", "Pop", "Shake", "Glow", "Neon", "Glitch", "Cinematic", "Elastic"
  val animDurationMs: Long = 400L,
  val hasGlow: Boolean = false,
  val glowColor: Long = 0xFF00E5FF,
  val glowRadius: Float = 10f,
  val backgroundShape: String = "Rounded",
  val animationIn: String = "Pop",
  val animationOut: String = "Fade",
  val animationDelayMs: Long = 0L,
  val animationEasing: String = "Ease Out",
  val subtitleStyle: String = "Classic", // "Classic", "Bold", "HighlightWord", "Karaoke", "Animated"
  val highlightColor: Long = 0xFFFFEB3B,
  val words: List<WordTiming> = emptyList(),
  val is3D: Boolean = false,
  val depth3D: Float = 0f,
  val bevelAngle3D: Float = 0f,
  val color3D: Long = 0xFF1E293B,
  val animation3D: String = "None",
  val effectStyle: String = "None",
  val isLocked: Boolean = false,
  val isHidden: Boolean = false
)

enum class StickerAnimationType(val displayName: String) {
  NONE("Static"),
  PULSE("Pulse"),
  HEARTBEAT("Heartbeat"),
  BOUNCE("Bounce"),
  SPIN("Spin 360°"),
  SHAKE("Shake"),
  FLOAT("Float"),
  SWING("Swing"),
  GLOW_PULSE("Glow Pulse"),
  POP_IN("Pop In")
}

enum class BadgeType(
  val displayName: String,
  val subtitle: String,
  val primaryColor: Long,
  val secondaryColor: Long,
  val icon: String
) {
  NEW("NEW", "Fresh Arrival", 0xFF06B6D4, 0xFF0284C7, "✨"),
  SALE("SALE", "Special Discount", 0xFFEF4444, 0xFFB91C1C, "🏷️"),
  HOT("HOT", "Trending Now", 0xFFF97316, 0xFFDC2626, "🔥"),
  TRENDING("TRENDING", "Viral Hit", 0xFF8B5CF6, 0xFF6366F1, "📈"),
  BEST_SELLER("BEST SELLER", "#1 Top Choice", 0xFFF59E0B, 0xFFD97706, "👑"),
  LIMITED_EDITION("LIMITED EDITION", "Exclusive Drop", 0xFF334155, 0xFFF59E0B, "⏳"),
  PREMIUM("PREMIUM", "VIP Quality", 0xFF7C3AED, 0xFF4F46E5, "💎"),
  SPECIAL_OFFER("SPECIAL OFFER", "Save Big", 0xFFEAB308, 0xFFCA8A04, "🎁"),
  DISCOUNT("DISCOUNT", "Price Drop", 0xFF10B981, 0xFF059669, "💸"),
  RECOMMENDED("RECOMMENDED", "Staff Pick", 0xFF0284C7, 0xFF0369A1, "👍"),
  FEATURED("FEATURED", "Spotlight", 0xFF6366F1, 0xFF4338CA, "🌟"),
  EXCLUSIVE("EXCLUSIVE", "Members Only", 0xFFBE185D, 0xFF831843, "🔒"),
  VERIFIED("VERIFIED", "Official Badge", 0xFF0EA5E9, 0xFF0284C7, "✔️"),
  TOP_RATED("TOP RATED", "5-Star Quality", 0xFFFBBF24, 0xFFF59E0B, "⭐"),
  FREE("FREE", "No Cost", 0xFF22C55E, 0xFF16A34A, "🆓"),
  COMING_SOON("COMING SOON", "Stay Tuned", 0xFFFB923C, 0xFFEA580C, "🚀")
}

data class StickerClip(
  val id: String = UUID.randomUUID().toString(),
  val emojiOrAsset: String = "🎬",
  val timelineStartMs: Long = 0L,
  val durationMs: Long = 3000L,
  val posX: Float = 0f,
  val posY: Float = 0f,
  val scale: Float = 1f,
  val rotation: Float = 0f,
  val opacity: Float = 1f,
  val animationType: StickerAnimationType = StickerAnimationType.NONE,
  val badgeType: BadgeType? = null,
  val category: String = "Emoji & Emotions",
  val isLocked: Boolean = false,
  val isHidden: Boolean = false,
  val elementId: String? = null,
  val elementCategory: String? = null,
  val customColor: Long? = null,
  val secondaryColor: Long? = null,
  val elementData: String? = null,
  val keyframes: List<ClipKeyframe> = emptyList()
)

enum class EffectType(val category: String, val displayName: String) {
  // Video Effects - Basic & Blur
  BLUR("Video Effects", "Blur"),
  GLOW("Video Effects", "Glow"),
  MOTION_BLUR("Video Effects", "Motion Blur"),
  SHARPEN("Video Effects", "Sharpen"),
  NOISE("Video Effects", "Noise Grain"),
  VIGNETTE("Video Effects", "Vignette Dark"),
  SOFT_FOCUS("Video Effects", "Soft Focus Dream"),
  HALO_GLOW("Video Effects", "Halo Glow"),

  // Video Effects - Motion & Zoom
  SHAKE("Video Effects", "Camera Shake"),
  ZOOM("Video Effects", "Zoom Pulse"),
  SPIN("Video Effects", "360 Spin"),
  CAMERA_MOVEMENT("Video Effects", "Wander Pan"),
  WARP_SPEED("Video Effects", "Warp Speed"),
  SKATER_ZOOM("Video Effects", "Skater Fast Zoom"),
  VERTIGO_DOLLY("Video Effects", "Vertigo Dolly"),

  // Video Effects - Light & Sparkle
  FLASH("Video Effects", "White Flash"),
  LENS_FLARE("Video Effects", "Anamorphic Flare"),
  LIGHT_LEAK("Video Effects", "Vintage Light Leak"),
  SOLAR_FLARE("Video Effects", "Solar Burst"),
  BOKEH("Video Effects", "Bokeh Dreams"),
  GOLDEN_HOUR("Video Effects", "Golden Hour Glow"),
  FIRE_SPARK("Video Effects", "Fire Sparkles"),
  LASER_GRID("Video Effects", "Laser Grid"),
  STROBE("Video Effects", "RGB Strobe"),

  // Video Effects - Distortion & Glitch
  GLITCH("Video Effects", "Glitch Scanline"),
  RGB_SPLIT("Video Effects", "RGB Split"),
  DISTORTION("Video Effects", "Barrel Distortion"),
  WAVE("Video Effects", "Wave Ripple"),
  RIPPLE("Video Effects", "Shockwave"),
  VHS_VINTAGE("Video Effects", "1998 VHS Tape"),
  CRT_TV("Video Effects", "CRT Scanline"),
  MIRROR("Video Effects", "Kaleido Mirror"),
  FISHEYE("Video Effects", "Fisheye Lens"),
  ACID_TRIP("Video Effects", "Psychedelic Acid"),

  // Body Effects
  BODY_AURA("Body Effects", "Neon Body Aura"),
  NEON_OUTLINE("Body Effects", "Cyber Neon Outline"),
  GLOW_EYES("Body Effects", "Laser Glow Eyes"),
  ANGEL_WINGS("Body Effects", "Angelic Wings"),
  CYBER_WINGS("Body Effects", "Mecha Cyber Wings"),
  FACE_BEAUTY("Body Effects", "AI Smooth Skin"),
  ANIME_SILHOUETTE("Body Effects", "Anime Shadow Silhouette"),
  SLIM_SHAPE("Body Effects", "Pro Slim Contour"),
  MUSCLE_GLOW("Body Effects", "Electric Muscle Glow"),
  SKELETON_XRAY("Body Effects", "Neon Skeleton X-Ray"),
  GHOST_CLONE("Body Effects", "Spectral Ghost Clone"),
  CYBER_FACE("Body Effects", "Cybernetic Face Grid"),
  FIRE_AURA("Body Effects", "Super Saiyan Flame"),
  LIGHTNING_BODY("Body Effects", "Thor Lightning Aura"),
  HEART_TRAIL("Body Effects", "Cupid Heart Trail"),
  FLORAL_CROWN("Body Effects", "Goddess Floral Crown"),
  DRAGON_FLAME("Body Effects", "Dragon Breath Flame"),
  FUNNY_BIG_EYES("Body Effects", "Funny Big Eyes"),
  FUNNY_ALIEN_WARP("Body Effects", "Alien Warp"),
  CYBER_VISOR("Body Effects", "Cyber Visor"),
  NEON_SPARKLE_CHEEKS("Body Effects", "Sparkle Cheeks"),
  DARK_SHADOW_AURA("Body Effects", "Dark Shadow Aura"),
  BACKGROUND_NEON_GRID("Body Effects", "Neon Grid BG"),

  // Photo Effects
  POLAROID_VINTAGE("Photo Effects", "Polaroid 1984"),
  DOUBLE_EXPOSURE("Photo Effects", "Double Exposure Silhouette"),
  COMIC_SKETCH("Photo Effects", "Marvel Comic Sketch"),
  MANGA_LINE("Photo Effects", "Shonen Manga Ink"),
  POP_ART_POSTER("Photo Effects", "Andy Warhol Pop Art"),
  THERMAL_CAMERA("Photo Effects", "Predator Thermal"),
  HALFTONE_DOT("Photo Effects", "Newspaper Halftone"),
  OIL_PAINTING("Photo Effects", "Van Gogh Oil Paint"),
  WATERCOLOR("Photo Effects", "Watercolor Splash"),
  CHARCOAL_DRAW("Photo Effects", "Charcoal Portrait"),
  BLUEPRINT_CAD("Photo Effects", "Architectural Blueprint"),
  PASTEL_DREAM("Photo Effects", "Soft Pastel Dream"),
  COLOR_POP_SPLASH("Photo Effects", "Selective Color Pop"),
  STAMP_ART("Photo Effects", "Vintage Rubber Stamp"),
  Y2K_CHROME("Photo Effects", "Y2K Chrome 2000s"),
  DIGICAM_2004("Photo Effects", "Digicam 2004"),
  FACE_SWAP_AI("Photo Effects", "AI Face Swap"),
  SCREEN_SWAP_HOLO("Photo Effects", "Hologram Screen"),
  SEPIA_VINTAGE("Photo Effects", "Sepia 1920"),
  OLD_PAPER_TEXTURE("Photo Effects", "Worn Vintage Paper"),

  // Video Effects - Celebrate & Party
  CELEBRATE_CONFETTI("Video Effects", "Golden Confetti"),
  CELEBRATE_FIREWORKS("Video Effects", "Neon Fireworks"),
  PARTY_PRISM("Video Effects", "Prism Rainbow"),
  PARTY_CONFUSED("Video Effects", "Confused Wobble"),

  // AI Effects
  AI_EXPANSION("AI Effects", "AI Canvas Expand"),
  AI_STYLE_MORPH("AI Effects", "AI Universe Morph"),
  AI_BG_SWAP("AI Effects", "AI Cyber City Swap"),
  AI_CYBERPUNK_CITY("AI Effects", "AI Neon Matrix"),
  AI_PARTICLE_DISPERSE("AI Effects", "Thanos Snap Disperse"),
  AI_MANGA_UNIVERSE("AI Effects", "AI Anime Character"),
  AI_ANIME_WORLD("AI Effects", "Ghibli Fantasy World"),
  AI_NEON_TRAIL("AI Effects", "AI Speed Force Trail"),
  AI_FANTASY_KINGDOM("AI Effects", "AI Enchanted Kingdom"),
  AI_SCI_FI_PORTAL("AI Effects", "AI Wormhole Portal"),
  AI_GOLDEN_GOD("AI Effects", "AI Celestial Divinity"),
  AI_LIQUID_GOLD("AI Effects", "AI Metallic Liquid Gold"),
  AI_FREEZE_TIME("AI Effects", "AI Temporal Freeze"),
  AI_SPEED_FORCE("AI Effects", "AI Hyper Lightning"),
  AI_GHOST_MOTION("AI Effects", "AI Chrono Motion"),
  AI_GLITCH_REALITY("AI Effects", "AI Quantum Glitch")
}

data class EffectClip(
  val id: String = UUID.randomUUID().toString(),
  val effectType: EffectType = EffectType.GLOW,
  val timelineStartMs: Long = 0L,
  val durationMs: Long = 3000L,
  val intensity: Float = 0.8f,
  val keyframes: List<ClipKeyframe> = emptyList(),
  val customName: String = "",
  val effectCategory: String = "Video Effects",
  val targetClipId: String? = null,
  val isLocked: Boolean = false,
  val isHidden: Boolean = false
)

enum class TransitionType(val displayName: String) {
  NONE("None"),
  FADE("Fade"),
  DISSOLVE("Dissolve"),
  SLIDE_LEFT("Slide Left"),
  SLIDE_RIGHT("Slide Right"),
  PUSH_UP("Push Up"),
  ZOOM_IN("Zoom In"),
  ZOOM_OUT("Zoom Out"),
  SPIN("Spin 360"),
  BLUR("Blur Zoom"),
  FLASH("White Flash"),
  GLITCH("Glitch Cut"),
  WIPE("Wipe Curtain"),
  WHIP_PAN("Whip Pan"),
  ZOOM_BLUR("Zoom Blur"),
  GLITCH_WIPE("Glitch Wipe"),
  LIGHT_LEAK("Light Leak")
}

data class Transition(
  val id: String = UUID.randomUUID().toString(),
  val clipIndexBefore: Int = 0,
  val type: TransitionType = TransitionType.FADE,
  val durationMs: Long = 500L
)

data class VideoAdjustments(
  val brightness: Float = 0f,      // -1f to 1f
  val contrast: Float = 1f,        // 0f to 2f
  val saturation: Float = 1f,      // 0f to 2f
  val exposure: Float = 0f,        // -1f to 1f
  val temperature: Float = 0f,     // -1f (cool) to 1f (warm)
  val tint: Float = 0f,            // -1f (green) to 1f (magenta)
  val highlights: Float = 0f,      // -1f to 1f
  val shadows: Float = 0f,         // -1f to 1f
  val sharpness: Float = 0f,       // 0f to 1f
  val fade: Float = 0f,            // 0f to 1f
  val vignette: Float = 0f,        // 0f to 1f
  val grain: Float = 0f            // 0f to 1f
)

enum class FilterType(val displayName: String, val category: String = "Pro Enhancements") {
  NONE("Original", "All"),
  FOUR_K("4K", "Pro Enhancements"),
  BLACKLIGHT_FIX("Blacklight Fix", "Pro Enhancements"),
  ENHANCE("Enhance", "Pro Enhancements"),
  HDR("HDR", "Pro Enhancements"),
  GLOW("Glow", "Pro Enhancements"),
  FOCUS("Focus", "Pro Enhancements"),
  QUALITY_RESTORATION("Quality Restoration", "Pro Enhancements"),
  GOLDEN_AUTUMN("Golden Autumn", "Cinematic & Nature"),
  OCEANIC_VIEW("Oceanic View", "Cinematic & Nature"),
  ALMOND("Almond", "Aesthetic Looks"),
  SUNLIGHT_ORANGE_BLUE("Sunlight Orange Blue", "Cinematic & Nature"),
  
  // Classic / Creative presets
  CINEMATIC("Cinematic Teal & Orange", "Cinematic & Nature"),
  WARM("Golden Warm", "Aesthetic Looks"),
  COOL("Arctic Cool", "Aesthetic Looks"),
  PORTRAIT("Portrait Soft", "Aesthetic Looks"),
  BLACK_AND_WHITE("Black & White", "Aesthetic Looks"),
  VINTAGE("Vintage 1970s", "Aesthetic Looks"),
  SATURATION("Saturation Boost", "Pro Enhancements"),
  FILM("35mm Film Grain", "Cinematic & Nature"),
  RETRO("80s Retro Synth", "Aesthetic Looks"),
  NATURE("Vibrant Nature", "Cinematic & Nature"),
  FOOD("Rich Warm Food", "Aesthetic Looks"),
  TRAVEL("Mediterranean Travel", "Cinematic & Nature"),
  SOCIAL_MEDIA("Hyper Vivid", "Pro Enhancements")
}

data class FilterSettings(
  val type: FilterType = FilterType.NONE,
  val intensity: Float = 1.0f // 0f to 1f
)

data class ChromaKeySettings(
  val enabled: Boolean = false,
  val targetColor: Long = 0xFF00FF00, // Green Screen default
  val similarity: Float = 0.4f,       // Similarity / distance threshold (0.0 to 1.0)
  val smoothness: Float = 0.15f,      // Smoothness / feathering (0.0 to 1.0)
  val spillSuppression: Float = 0.5f, // Spill suppression (0.0 to 1.0)
  val edgeControl: Float = 0.0f,      // Edge control: choke/expand (-1.0 to 1.0)
  val backgroundType: String = "SolidColor", // "SolidColor", "Image", "Video", "Transparent"
  val backgroundColor: Long = 0xFF000000,
  val backgroundUri: String? = null,
  val intensity: Float = similarity,
  val shadow: Float = 0.3f,
  val edgeAdjustment: Float = smoothness,
  val spillReduction: Float = spillSuppression
)

enum class TrackType {
  MAIN_VIDEO,
  OVERLAY,
  TEXT,
  AUDIO,
  STICKER,
  EFFECT
}

enum class TrackHeight(val label: String, val heightDp: Int) {
  COMPACT("Compact", 40),
  NORMAL("Normal", 56),
  EXPANDED("Expanded", 78)
}

data class TrackSettings(
  val type: TrackType,
  val isLocked: Boolean = false,
  val isHidden: Boolean = false,
  val isMuted: Boolean = false,
  val isSolo: Boolean = false,
  val height: TrackHeight = TrackHeight.NORMAL
)

fun defaultTrackSettings(): Map<TrackType, TrackSettings> {
  return TrackType.values().associateWith { TrackSettings(it) }
}

data class Timeline(
  val videoClips: List<VideoClip> = emptyList(),
  val overlayClips: List<VideoClip> = emptyList(),
  val audioClips: List<AudioClip> = emptyList(),
  val textClips: List<TextClip> = emptyList(),
  val stickerClips: List<StickerClip> = emptyList(),
  val effectClips: List<EffectClip> = emptyList(),
  val transitions: List<Transition> = emptyList(),
  val adjustments: VideoAdjustments = VideoAdjustments(),
  val filter: FilterSettings = FilterSettings(),
  val chromaKey: ChromaKeySettings = ChromaKeySettings(),
  val canvasBackgroundColor: Long = 0xFF000000,
  val aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
  val trackSettings: Map<TrackType, TrackSettings> = defaultTrackSettings()
) {
  val totalDurationMs: Long
    get() {
      val videoDur = videoClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
      val overlayDur = overlayClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
      val audioDur = audioClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
      val textDur = textClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
      val stickerDur = stickerClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
      val effectDur = effectClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
      return maxOf(videoDur, overlayDur, audioDur, textDur, stickerDur, effectDur)
    }
}
