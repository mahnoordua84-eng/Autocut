package com.example.data.presets

import com.example.domain.model.*

enum class PlaceholderType {
  VIDEO,
  IMAGE
}

data class MediaPlaceholder(
  val slotId: String,
  val label: String,
  val placeholderType: PlaceholderType,
  val requiredDurationMs: Long,
  val targetClipId: String,
  val defaultName: String = "Placeholder Media",
  val isOverlay: Boolean = false
)

data class TextPlaceholder(
  val slotId: String,
  val label: String,
  val targetClipId: String,
  val defaultText: String
)

data class VideoTemplate(
  val id: String,
  val title: String,
  val category: String,
  val description: String,
  val aspectRatio: AspectRatio,
  val resolution: Resolution = Resolution.RES_1080P,
  val fps: FrameRate = FrameRate.FPS_30,
  val durationMs: Long,
  val thumbnailGradientStart: Long = 0xFF1E293B,
  val thumbnailGradientEnd: Long = 0xFF0F172A,
  val iconEmoji: String = "🎬",
  val mediaPlaceholders: List<MediaPlaceholder> = emptyList(),
  val textPlaceholders: List<TextPlaceholder> = emptyList(),
  val audioTitle: String = "Soundtrack",
  val isPro: Boolean = false,
  val savedTimeline: Timeline? = null,
  val creatorId: String = "system_official",
  val creatorName: String = "Motion Studio",
  val creatorHandle: String = "@motionstudio",
  val creatorAvatarUrl: String? = null,
  val previewVideoUrl: String? = null,
  val previewThumbnailUrl: String? = null,
  val viewsCount: Long = 1240L,
  val usesCount: Long = 380L,
  val createdAt: Long = System.currentTimeMillis(),
  val createTimeline: (
    mediaReplacements: Map<String, String>,
    textReplacements: Map<String, String>
  ) -> Timeline = { _, _ -> savedTimeline ?: Timeline() }
) {
  fun createDefaultTimeline(): Timeline = savedTimeline ?: createTimeline(emptyMap(), emptyMap())
}

object TemplatesCatalog {
  val categories = listOf(
    "All",
    "Reels",
    "TikTok-style short videos",
    "YouTube",
    "YouTube Shorts",
    "Instagram",
    "Business",
    "Product Ads",
    "Birthday",
    "Wedding",
    "Travel",
    "Cinematic"
  )

  val templates: List<VideoTemplate> = listOf(
    // 1. Reels
    VideoTemplate(
      id = "reels_trending_vibe",
      title = "Dynamic Fast-Cut Reel",
      category = "Reels",
      description = "High-energy beat sync cuts with pop-in titles, dynamic zoom keyframes, and neon glow effects.",
      aspectRatio = AspectRatio.RATIO_9_16,
      durationMs = 9000L,
      thumbnailGradientStart = 0xFF833AB4,
      thumbnailGradientEnd = 0xFFFD1D1D,
      iconEmoji = "⚡",
      audioTitle = "Energetic Beat Drop",
      mediaPlaceholders = listOf(
        MediaPlaceholder("v1", "Opening Hook Clip", PlaceholderType.VIDEO, 2000L, "reels_vid_1"),
        MediaPlaceholder("v2", "Action Motion Clip", PlaceholderType.VIDEO, 2500L, "reels_vid_2"),
        MediaPlaceholder("i1", "Hero Focus Photo", PlaceholderType.IMAGE, 2000L, "reels_img_1"),
        MediaPlaceholder("v3", "Climax Outro Clip", PlaceholderType.VIDEO, 2500L, "reels_vid_3")
      ),
      textPlaceholders = listOf(
        TextPlaceholder("t1", "Main Hook Title", "reels_txt_1", "TRENDING NOW"),
        TextPlaceholder("t2", "Call to Action", "reels_txt_2", "LINK IN BIO")
      ),
      createTimeline = { media, texts ->
        val v1Uri = media["v1"] ?: "content://media/sample_hook.mp4"
        val v2Uri = media["v2"] ?: "content://media/sample_action.mp4"
        val imgUri = media["i1"] ?: "content://media/sample_photo.jpg"
        val v3Uri = media["v3"] ?: "content://media/sample_outro.mp4"

        val t1Text = texts["t1"] ?: "TRENDING NOW"
        val t2Text = texts["t2"] ?: "LINK IN BIO"

        Timeline(
          videoClips = listOf(
            VideoClip(id = "reels_vid_1", name = "Hook Video", uri = v1Uri, timelineStartMs = 0L, durationMs = 2000L, isVideo = true),
            VideoClip(id = "reels_vid_2", name = "Action Video", uri = v2Uri, timelineStartMs = 2000L, durationMs = 2500L, isVideo = true),
            VideoClip(
              id = "reels_img_1",
              name = "Hero Photo",
              uri = imgUri,
              timelineStartMs = 4500L,
              durationMs = 2000L,
              isVideo = false,
              keyframes = listOf(
                ClipKeyframe(timeMs = 4500L, scaleX = 1.0f, scaleY = 1.0f),
                ClipKeyframe(timeMs = 6500L, scaleX = 1.25f, scaleY = 1.25f)
              )
            ),
            VideoClip(id = "reels_vid_3", name = "Outro Video", uri = v3Uri, timelineStartMs = 6500L, durationMs = 2500L, isVideo = true)
          ),
          textClips = listOf(
            TextClip(id = "reels_txt_1", text = t1Text, timelineStartMs = 500L, durationMs = 2500L, animationType = "Pop"),
            TextClip(id = "reels_txt_2", text = t2Text, timelineStartMs = 6000L, durationMs = 3000L, animationType = "Fade")
          ),
          audioClips = listOf(
            AudioClip(id = "reels_audio_1", uri = "asset://audio/beat_drop.mp3", title = "Energetic Beat Drop", timelineStartMs = 0L, durationMs = 9000L)
          ),
          transitions = listOf(
            Transition(id = "reels_trans_1", clipIndexBefore = 0, type = TransitionType.ZOOM_IN, durationMs = 300L)
          ),
          effectClips = listOf(
            EffectClip(id = "reels_fx_1", effectType = EffectType.GLOW, timelineStartMs = 0L, durationMs = 9000L)
          )
        )
      }
    ),

    // 2. TikTok-style short videos
    VideoTemplate(
      id = "tiktok_viral_speed",
      title = "TikTok Viral Speed Ramp",
      category = "TikTok-style short videos",
      description = "Viral TikTok rhythmic cuts with punchy text overlays and trending sound design.",
      aspectRatio = AspectRatio.RATIO_9_16,
      durationMs = 8000L,
      thumbnailGradientStart = 0xFF00F2FE,
      thumbnailGradientEnd = 0xFF4FACFE,
      iconEmoji = "🎵",
      audioTitle = "TikTok Trending Bass",
      mediaPlaceholders = listOf(
        MediaPlaceholder("v1", "Clip 1", PlaceholderType.VIDEO, 4000L, "tt_vid_1"),
        MediaPlaceholder("v2", "Clip 2", PlaceholderType.VIDEO, 4000L, "tt_vid_2")
      ),
      textPlaceholders = listOf(
        TextPlaceholder("t1", "Catchphrase", "tt_txt_1", "WAIT FOR IT...")
      ),
      createTimeline = { media, texts ->
        Timeline(
          videoClips = listOf(
            VideoClip(id = "tt_vid_1", name = "Clip 1", uri = media["v1"] ?: "content://media/tt1.mp4", timelineStartMs = 0L, durationMs = 4000L, speed = 1.2f),
            VideoClip(id = "tt_vid_2", name = "Clip 2", uri = media["v2"] ?: "content://media/tt2.mp4", timelineStartMs = 4000L, durationMs = 4000L, speed = 1.5f)
          ),
          textClips = listOf(
            TextClip(id = "tt_txt_1", text = texts["t1"] ?: "WAIT FOR IT...", timelineStartMs = 500L, durationMs = 3500L, animationType = "Pop")
          ),
          audioClips = listOf(
            AudioClip(id = "tt_audio_1", uri = "asset://audio/tiktok_bass.mp3", title = "TikTok Trending Bass", timelineStartMs = 0L, durationMs = 8000L)
          )
        )
      }
    ),

    // 3. YouTube
    VideoTemplate(
      id = "youtube_modern_intro",
      title = "Modern YouTube Tech Intro",
      category = "YouTube",
      description = "Widescreen 16:9 cinematic presentation with lower-thirds and clean chapter titles.",
      aspectRatio = AspectRatio.RATIO_16_9,
      durationMs = 12000L,
      thumbnailGradientStart = 0xFFFF0000,
      thumbnailGradientEnd = 0xFF282828,
      iconEmoji = "▶️",
      audioTitle = "Tech Ambient Intro",
      mediaPlaceholders = listOf(
        MediaPlaceholder("v1", "B-Roll Showcase", PlaceholderType.VIDEO, 6000L, "yt_vid_1"),
        MediaPlaceholder("v2", "Talking Head Clip", PlaceholderType.VIDEO, 6000L, "yt_vid_2")
      ),
      textPlaceholders = listOf(
        TextPlaceholder("t1", "Episode Title", "yt_txt_1", "TECH REVIEW 2026"),
        TextPlaceholder("t2", "Host Name", "yt_txt_2", "PRESENTED BY ALEX")
      ),
      createTimeline = { media, texts ->
        Timeline(
          videoClips = listOf(
            VideoClip(id = "yt_vid_1", name = "B-Roll", uri = media["v1"] ?: "content://media/yt1.mp4", timelineStartMs = 0L, durationMs = 6000L),
            VideoClip(id = "yt_vid_2", name = "Talking Head", uri = media["v2"] ?: "content://media/yt2.mp4", timelineStartMs = 6000L, durationMs = 6000L)
          ),
          textClips = listOf(
            TextClip(id = "yt_txt_1", text = texts["t1"] ?: "TECH REVIEW 2026", timelineStartMs = 1000L, durationMs = 5000L),
            TextClip(id = "yt_txt_2", text = texts["t2"] ?: "PRESENTED BY ALEX", timelineStartMs = 6500L, durationMs = 4000L)
          ),
          audioClips = listOf(
            AudioClip(id = "yt_audio_1", uri = "asset://audio/tech_intro.mp3", title = "Tech Ambient Intro", timelineStartMs = 0L, durationMs = 12000L)
          )
        )
      }
    ),

    // 4. YouTube Shorts
    VideoTemplate(
      id = "youtube_shorts_viral",
      title = "Shorts Story Highlight",
      category = "YouTube Shorts",
      description = "Vertical fast story format with subtitles style and progress bar.",
      aspectRatio = AspectRatio.RATIO_9_16,
      durationMs = 10000L,
      thumbnailGradientStart = 0xFFFF4E50,
      thumbnailGradientEnd = 0xFFF9D423,
      iconEmoji = "📱",
      audioTitle = "Upbeat Shorts Anthem",
      mediaPlaceholders = listOf(
        MediaPlaceholder("v1", "Highlight Clip", PlaceholderType.VIDEO, 10000L, "ys_vid_1")
      ),
      textPlaceholders = listOf(
        TextPlaceholder("t1", "Top Headline", "ys_txt_1", "DID YOU KNOW THIS?")
      ),
      createTimeline = { media, texts ->
        Timeline(
          videoClips = listOf(
            VideoClip(id = "ys_vid_1", name = "Highlight", uri = media["v1"] ?: "content://media/ys1.mp4", timelineStartMs = 0L, durationMs = 10000L)
          ),
          textClips = listOf(
            TextClip(id = "ys_txt_1", text = texts["t1"] ?: "DID YOU KNOW THIS?", timelineStartMs = 500L, durationMs = 8000L)
          ),
          audioClips = listOf(
            AudioClip(id = "ys_audio_1", uri = "asset://audio/shorts_upbeat.mp3", title = "Upbeat Shorts Anthem", timelineStartMs = 0L, durationMs = 10000L)
          )
        )
      }
    ),

    // 5. Instagram
    VideoTemplate(
      id = "instagram_story_glow",
      title = "Instagram Aesthetic Story",
      category = "Instagram",
      description = "Warm tones with clean serif fonts and smooth fade transitions.",
      aspectRatio = AspectRatio.RATIO_9_16,
      durationMs = 7000L,
      thumbnailGradientStart = 0xFFC13584,
      thumbnailGradientEnd = 0xFFE1306C,
      iconEmoji = "✨",
      audioTitle = "Lo-Fi Chill Hop",
      mediaPlaceholders = listOf(
        MediaPlaceholder("v1", "Story Clip", PlaceholderType.VIDEO, 7000L, "ig_vid_1")
      ),
      textPlaceholders = listOf(
        TextPlaceholder("t1", "Caption", "ig_txt_1", "Sunday Mood")
      ),
      createTimeline = { media, texts ->
        Timeline(
          videoClips = listOf(
            VideoClip(id = "ig_vid_1", name = "Story", uri = media["v1"] ?: "content://media/ig1.mp4", timelineStartMs = 0L, durationMs = 7000L)
          ),
          textClips = listOf(
            TextClip(id = "ig_txt_1", text = texts["t1"] ?: "Sunday Mood", timelineStartMs = 500L, durationMs = 6000L)
          ),
          audioClips = listOf(
            AudioClip(id = "ig_audio_1", uri = "asset://audio/lofi_chill.mp3", title = "Lo-Fi Chill Hop", timelineStartMs = 0L, durationMs = 7000L)
          )
        )
      }
    ),

    // 6. Business
    VideoTemplate(
      id = "business_corporate_pitch",
      title = "Corporate Showcase & Pitch",
      category = "Business",
      description = "Sleek professional template for company highlights and milestone announcements.",
      aspectRatio = AspectRatio.RATIO_16_9,
      durationMs = 15000L,
      thumbnailGradientStart = 0xFF1E3C72,
      thumbnailGradientEnd = 0xFF2A5298,
      iconEmoji = "💼",
      audioTitle = "Corporate Inspiring Piano",
      mediaPlaceholders = listOf(
        MediaPlaceholder("v1", "Office / Team Video", PlaceholderType.VIDEO, 7500L, "biz_vid_1"),
        MediaPlaceholder("v2", "Product Video", PlaceholderType.VIDEO, 7500L, "biz_vid_2")
      ),
      textPlaceholders = listOf(
        TextPlaceholder("t1", "Company Milestone", "biz_txt_1", "BUILDING THE FUTURE"),
        TextPlaceholder("t2", "Website / Contact", "biz_txt_2", "WWW.COMPANY.COM")
      ),
      createTimeline = { media, texts ->
        Timeline(
          videoClips = listOf(
            VideoClip(id = "biz_vid_1", name = "Team", uri = media["v1"] ?: "content://media/biz1.mp4", timelineStartMs = 0L, durationMs = 7500L),
            VideoClip(id = "biz_vid_2", name = "Product", uri = media["v2"] ?: "content://media/biz2.mp4", timelineStartMs = 7500L, durationMs = 7500L)
          ),
          textClips = listOf(
            TextClip(id = "biz_txt_1", text = texts["t1"] ?: "BUILDING THE FUTURE", timelineStartMs = 1000L, durationMs = 5000L),
            TextClip(id = "biz_txt_2", text = texts["t2"] ?: "WWW.COMPANY.COM", timelineStartMs = 8000L, durationMs = 6000L)
          ),
          audioClips = listOf(
            AudioClip(id = "biz_audio_1", uri = "asset://audio/corp_piano.mp3", title = "Corporate Inspiring Piano", timelineStartMs = 0L, durationMs = 15000L)
          )
        )
      }
    ),

    // 7. Product Ads
    VideoTemplate(
      id = "product_ad_commercial",
      title = "E-Commerce Product Promo",
      category = "Product Ads",
      description = "Flash sale discount badges, product feature callouts, and animated price tags.",
      aspectRatio = AspectRatio.RATIO_1_1,
      durationMs = 8000L,
      thumbnailGradientStart = 0xFFFF416C,
      thumbnailGradientEnd = 0xFFFF4B2B,
      iconEmoji = "🛍️",
      audioTitle = "Upbeat Electro Commercial",
      mediaPlaceholders = listOf(
        MediaPlaceholder("v1", "Product Showcase 1", PlaceholderType.VIDEO, 4000L, "prod_vid_1"),
        MediaPlaceholder("v2", "Product Showcase 2", PlaceholderType.VIDEO, 4000L, "prod_vid_2")
      ),
      textPlaceholders = listOf(
        TextPlaceholder("t1", "Sale Banner", "prod_txt_1", "50% OFF TODAY"),
        TextPlaceholder("t2", "Shop Button", "prod_txt_2", "SHOP NOW")
      ),
      createTimeline = { media, texts ->
        Timeline(
          videoClips = listOf(
            VideoClip(id = "prod_vid_1", name = "Product 1", uri = media["v1"] ?: "content://media/prod1.mp4", timelineStartMs = 0L, durationMs = 4000L),
            VideoClip(id = "prod_vid_2", name = "Product 2", uri = media["v2"] ?: "content://media/prod2.mp4", timelineStartMs = 4000L, durationMs = 4000L)
          ),
          textClips = listOf(
            TextClip(id = "prod_txt_1", text = texts["t1"] ?: "50% OFF TODAY", timelineStartMs = 500L, durationMs = 3500L),
            TextClip(id = "prod_txt_2", text = texts["t2"] ?: "SHOP NOW", timelineStartMs = 4500L, durationMs = 3000L)
          ),
          audioClips = listOf(
            AudioClip(id = "prod_audio_1", uri = "asset://audio/ad_electro.mp3", title = "Upbeat Electro Commercial", timelineStartMs = 0L, durationMs = 8000L)
          )
        )
      }
    ),

    // 8. Birthday
    VideoTemplate(
      id = "birthday_celebration",
      title = "Birthday Memory Reel",
      category = "Birthday",
      description = "Festive celebratory animations, confetti glow, and happy memories montage.",
      aspectRatio = AspectRatio.RATIO_9_16,
      durationMs = 10000L,
      thumbnailGradientStart = 0xFFF857A6,
      thumbnailGradientEnd = 0xFFFF5858,
      iconEmoji = "🎂",
      audioTitle = "Happy Birthday Acoustic",
      mediaPlaceholders = listOf(
        MediaPlaceholder("v1", "Birthday Moment Clip", PlaceholderType.VIDEO, 10000L, "bday_vid_1")
      ),
      textPlaceholders = listOf(
        TextPlaceholder("t1", "Birthday Wishes", "bday_txt_1", "HAPPY BIRTHDAY!")
      ),
      createTimeline = { media, texts ->
        Timeline(
          videoClips = listOf(
            VideoClip(id = "bday_vid_1", name = "Birthday", uri = media["v1"] ?: "content://media/bday1.mp4", timelineStartMs = 0L, durationMs = 10000L)
          ),
          textClips = listOf(
            TextClip(id = "bday_txt_1", text = texts["t1"] ?: "HAPPY BIRTHDAY!", timelineStartMs = 500L, durationMs = 8000L)
          ),
          audioClips = listOf(
            AudioClip(id = "bday_audio_1", uri = "asset://audio/birthday.mp3", title = "Happy Birthday Acoustic", timelineStartMs = 0L, durationMs = 10000L)
          )
        )
      }
    ),

    // 9. Wedding
    VideoTemplate(
      id = "wedding_cinematic_story",
      title = "Romantic Wedding Highlights",
      category = "Wedding",
      description = "Elegant romantic transitions, cinematic color grading, and heartfelt orchestral score.",
      aspectRatio = AspectRatio.RATIO_16_9,
      durationMs = 12000L,
      thumbnailGradientStart = 0xFFDECBA4,
      thumbnailGradientEnd = 0xFF3E5151,
      iconEmoji = "💍",
      audioTitle = "Emotional Piano & Orchestra",
      mediaPlaceholders = listOf(
        MediaPlaceholder("v1", "Couple Dance / Vows", PlaceholderType.VIDEO, 6000L, "wedding_vid_1"),
        MediaPlaceholder("v2", "Celebration Clip", PlaceholderType.VIDEO, 6000L, "wedding_vid_2")
      ),
      textPlaceholders = listOf(
        TextPlaceholder("t1", "Couple Names", "wedding_txt_1", "Emma & Oliver"),
        TextPlaceholder("t2", "Date & Venue", "wedding_txt_2", "JUNE 20, 2026")
      ),
      createTimeline = { media, texts ->
        val v1Uri = media["v1"] ?: "file:///couple_dance.mp4"
        val v2Uri = media["v2"] ?: "content://media/wedding2.mp4"
        val t1Text = texts["t1"] ?: "Emma & Oliver"
        val t2Text = texts["t2"] ?: "JUNE 20, 2026"

        Timeline(
          videoClips = listOf(
            VideoClip(id = "wedding_vid_1", name = "Couple Dance", uri = v1Uri, timelineStartMs = 0L, durationMs = 6000L),
            VideoClip(id = "wedding_vid_2", name = "Celebration", uri = v2Uri, timelineStartMs = 6000L, durationMs = 6000L)
          ),
          textClips = listOf(
            TextClip(id = "wedding_txt_1", text = t1Text, timelineStartMs = 500L, durationMs = 5000L),
            TextClip(id = "wedding_txt_2", text = t2Text, timelineStartMs = 6500L, durationMs = 5000L)
          ),
          audioClips = listOf(
            AudioClip(id = "wedding_audio_1", uri = "asset://audio/wedding_piano.mp3", title = "Emotional Piano & Orchestra", timelineStartMs = 0L, durationMs = 12000L)
          )
        )
      }
    ),

    // 10. Travel
    VideoTemplate(
      id = "travel_vlog_adventure",
      title = "Travel Vlog & Adventure Montage",
      category = "Travel",
      description = "Scenic landscape zooms, map pin points, and exhilarating adventure pacing.",
      aspectRatio = AspectRatio.RATIO_16_9,
      durationMs = 10000L,
      thumbnailGradientStart = 0xFF11998E,
      thumbnailGradientEnd = 0xFF38EF7D,
      iconEmoji = "✈️",
      audioTitle = "Adventure Upbeat Guitar",
      mediaPlaceholders = listOf(
        MediaPlaceholder("v1", "Landscape Clip", PlaceholderType.VIDEO, 5000L, "travel_vid_1"),
        MediaPlaceholder("v2", "Adventure Clip", PlaceholderType.VIDEO, 5000L, "travel_vid_2")
      ),
      textPlaceholders = listOf(
        TextPlaceholder("t1", "Destination Name", "travel_txt_1", "BALI ADVENTURE"),
        TextPlaceholder("t2", "Episode Tag", "travel_txt_2", "DAY 1")
      ),
      createTimeline = { media, texts ->
        Timeline(
          videoClips = listOf(
            VideoClip(id = "travel_vid_1", name = "Landscape", uri = media["v1"] ?: "content://media/travel1.mp4", timelineStartMs = 0L, durationMs = 5000L),
            VideoClip(id = "travel_vid_2", name = "Adventure", uri = media["v2"] ?: "content://media/travel2.mp4", timelineStartMs = 5000L, durationMs = 5000L)
          ),
          textClips = listOf(
            TextClip(id = "travel_txt_1", text = texts["t1"] ?: "BALI ADVENTURE", timelineStartMs = 500L, durationMs = 4000L),
            TextClip(id = "travel_txt_2", text = texts["t2"] ?: "DAY 1", timelineStartMs = 5500L, durationMs = 4000L)
          ),
          audioClips = listOf(
            AudioClip(id = "travel_audio_1", uri = "asset://audio/travel_guitar.mp3", title = "Adventure Upbeat Guitar", timelineStartMs = 0L, durationMs = 10000L)
          )
        )
      }
    ),

    // 11. Cinematic
    VideoTemplate(
      id = "cinematic_letterbox_film",
      title = "Cinematic Anamorphic Film",
      category = "Cinematic",
      description = "Widescreen anamorphic aspect ratio with film grain and slow orchestral drone.",
      aspectRatio = AspectRatio.RATIO_16_9,
      durationMs = 14000L,
      thumbnailGradientStart = 0xFF000000,
      thumbnailGradientEnd = 0xFF434343,
      iconEmoji = "🎞️",
      audioTitle = "Cinematic Deep Drone",
      mediaPlaceholders = listOf(
        MediaPlaceholder("v1", "Establishing Shot", PlaceholderType.VIDEO, 7000L, "cine_vid_1"),
        MediaPlaceholder("v2", "Character Close-up", PlaceholderType.VIDEO, 7000L, "cine_vid_2")
      ),
      textPlaceholders = listOf(
        TextPlaceholder("t1", "Film Title", "cine_txt_1", "THE HORIZON"),
        TextPlaceholder("t2", "Director Credit", "cine_txt_2", "A FILM BY DIRECTOR")
      ),
      createTimeline = { media, texts ->
        Timeline(
          videoClips = listOf(
            VideoClip(id = "cine_vid_1", name = "Establishing", uri = media["v1"] ?: "content://media/cine1.mp4", timelineStartMs = 0L, durationMs = 7000L),
            VideoClip(id = "cine_vid_2", name = "Character", uri = media["v2"] ?: "content://media/cine2.mp4", timelineStartMs = 7000L, durationMs = 7000L)
          ),
          textClips = listOf(
            TextClip(id = "cine_txt_1", text = texts["t1"] ?: "THE HORIZON", timelineStartMs = 1000L, durationMs = 5000L),
            TextClip(id = "cine_txt_2", text = texts["t2"] ?: "A FILM BY DIRECTOR", timelineStartMs = 7500L, durationMs = 5000L)
          ),
          audioClips = listOf(
            AudioClip(id = "cine_audio_1", uri = "asset://audio/cine_drone.mp3", title = "Cinematic Deep Drone", timelineStartMs = 0L, durationMs = 14000L)
          )
        )
      }
    )
  )
}
