package com.example.data.presets

import com.example.domain.model.BadgeType
import com.example.domain.model.StickerAnimationType

data class StickerPresetItem(
  val id: String,
  val symbolOrAsset: String,
  val name: String,
  val category: String,
  val defaultAnimation: StickerAnimationType = StickerAnimationType.NONE,
  val badgeType: BadgeType? = null,
  val tags: List<String> = emptyList()
)

object StickersCatalog {

  val CATEGORIES = listOf(
    "Badges",
    "Animated Stickers",
    "Trending Stickers",
    "Emoji & Emotions",
    "Love & Hearts",
    "Funny & Memes",
    "Animals",
    "Food & Drinks",
    "Travel",
    "Nature",
    "Flowers",
    "Weather",
    "Sports",
    "Gaming",
    "Celebration",
    "Birthday",
    "Wedding",
    "Islamic",
    "Ramadan & Eid",
    "Business",
    "Shopping",
    "Sale & Discount",
    "Social Media",
    "Arrows & Shapes",
    "Speech Bubbles",
    "Decorative Elements"
  )

  val BADGES = BadgeType.values().map { badge ->
    StickerPresetItem(
      id = "badge_${badge.name.lowercase()}",
      symbolOrAsset = badge.displayName,
      name = badge.displayName,
      category = "Badges",
      defaultAnimation = StickerAnimationType.NONE,
      badgeType = badge,
      tags = listOf("badge", badge.name.lowercase(), badge.subtitle.lowercase())
    )
  }

  val ANIMATED_STICKERS = listOf(
    StickerPresetItem("anim_fire", "🔥", "Blazing Fire", "Animated Stickers", StickerAnimationType.PULSE, tags = listOf("fire", "hot", "burn")),
    StickerPresetItem("anim_heartbeat", "❤️", "Heartbeat", "Animated Stickers", StickerAnimationType.HEARTBEAT, tags = listOf("heart", "love", "pulse")),
    StickerPresetItem("anim_sparkle", "✨", "Magic Sparkle", "Animated Stickers", StickerAnimationType.GLOW_PULSE, tags = listOf("sparkle", "glow", "shine")),
    StickerPresetItem("anim_rocket", "🚀", "Launching Rocket", "Animated Stickers", StickerAnimationType.FLOAT, tags = listOf("rocket", "space", "launch")),
    StickerPresetItem("anim_spin_star", "💫", "Spinning Star", "Animated Stickers", StickerAnimationType.SPIN, tags = listOf("star", "spin", "dizzy")),
    StickerPresetItem("anim_bounce_trophy", "🏆", "Victory Trophy", "Animated Stickers", StickerAnimationType.BOUNCE, tags = listOf("winner", "trophy", "gold")),
    StickerPresetItem("anim_shake_bomb", "💣", "Ticking Bomb", "Animated Stickers", StickerAnimationType.SHAKE, tags = listOf("bomb", "danger", "boom")),
    StickerPresetItem("anim_swing_bell", "🔔", "Subscribe Bell", "Animated Stickers", StickerAnimationType.SWING, tags = listOf("bell", "notify", "subscribe")),
    StickerPresetItem("anim_pop_party", "🎉", "Party Popper", "Animated Stickers", StickerAnimationType.POP_IN, tags = listOf("party", "celebrate", "pop")),
    StickerPresetItem("anim_rainbow_pulse", "🌈", "Dream Rainbow", "Animated Stickers", StickerAnimationType.PULSE, tags = listOf("rainbow", "colorful")),
    StickerPresetItem("anim_laugh_shake", "🤣", "Rolling Laugh", "Animated Stickers", StickerAnimationType.SHAKE, tags = listOf("funny", "lol", "laugh")),
    StickerPresetItem("anim_diamond_glow", "💎", "Glow Diamond", "Animated Stickers", StickerAnimationType.GLOW_PULSE, tags = listOf("diamond", "gem", "luxury"))
  )

  val TRENDING_STICKERS = listOf(
    StickerPresetItem("trend_fire", "🔥", "Fire", "Trending Stickers", StickerAnimationType.PULSE),
    StickerPresetItem("trend_100", "💯", "100 Percent", "Trending Stickers"),
    StickerPresetItem("trend_skull", "💀", "Dead Skull", "Trending Stickers"),
    StickerPresetItem("trend_moyai", "🗿", "Moyai Sigma", "Trending Stickers"),
    StickerPresetItem("trend_rocket", "🚀", "To The Moon", "Trending Stickers", StickerAnimationType.FLOAT),
    StickerPresetItem("trend_star_eyes", "🤩", "Star Struck", "Trending Stickers", StickerAnimationType.PULSE),
    StickerPresetItem("trend_crown", "👑", "King Crown", "Trending Stickers"),
    StickerPresetItem("trend_party", "🥳", "Party Horn", "Trending Stickers", StickerAnimationType.BOUNCE),
    StickerPresetItem("trend_money_wings", "💸", "Money Fly", "Trending Stickers", StickerAnimationType.FLOAT),
    StickerPresetItem("trend_diamond", "💎", "Diamond Gem", "Trending Stickers", StickerAnimationType.GLOW_PULSE),
    StickerPresetItem("trend_popcorn", "🍿", "Popcorn Drama", "Trending Stickers"),
    StickerPresetItem("trend_boom", "💥", "Explosion", "Trending Stickers", StickerAnimationType.SHAKE),
    StickerPresetItem("trend_lightning", "⚡", "Super Bolt", "Trending Stickers"),
    StickerPresetItem("trend_target", "🎯", "Bullseye", "Trending Stickers"),
    StickerPresetItem("trend_trophy", "🏆", "Champion", "Trending Stickers", StickerAnimationType.BOUNCE),
    StickerPresetItem("trend_sunglasses", "😎", "Cool Guy", "Trending Stickers")
  )

  val EMOJI_EMOTIONS = listOf(
    StickerPresetItem("emo_smile", "😀", "Grinning Face", "Emoji & Emotions"),
    StickerPresetItem("emo_laugh", "🤣", "ROFL", "Emoji & Emotions", StickerAnimationType.SHAKE),
    StickerPresetItem("emo_love_eyes", "😍", "Heart Eyes", "Emoji & Emotions", StickerAnimationType.HEARTBEAT),
    StickerPresetItem("emo_party", "🥳", "Partying Face", "Emoji & Emotions", StickerAnimationType.BOUNCE),
    StickerPresetItem("emo_cool", "😎", "Cool Sunglasses", "Emoji & Emotions"),
    StickerPresetItem("emo_star_struck", "🤩", "Star Eyes", "Emoji & Emotions", StickerAnimationType.PULSE),
    StickerPresetItem("emo_crying", "😭", "Loudly Crying", "Emoji & Emotions"),
    StickerPresetItem("emo_mind_blown", "🤯", "Exploding Head", "Emoji & Emotions", StickerAnimationType.SHAKE),
    StickerPresetItem("emo_sleeping", "😴", "Sleeping Zzz", "Emoji & Emotions"),
    StickerPresetItem("emo_angel", "😇", "Halo Angel", "Emoji & Emotions", StickerAnimationType.FLOAT),
    StickerPresetItem("emo_cowboy", "🤠", "Cowboy Hat", "Emoji & Emotions"),
    StickerPresetItem("emo_devil", "😈", "Cheeky Devil", "Emoji & Emotions"),
    StickerPresetItem("emo_robot", "🤖", "Bot Robot", "Emoji & Emotions"),
    StickerPresetItem("emo_ghost", "👻", "Spooky Ghost", "Emoji & Emotions", StickerAnimationType.FLOAT),
    StickerPresetItem("emo_alien", "👽", "Space Alien", "Emoji & Emotions"),
    StickerPresetItem("emo_crazy", "🤪", "Zany Face", "Emoji & Emotions", StickerAnimationType.SWING),
    StickerPresetItem("emo_shush", "🤫", "Shushing Secret", "Emoji & Emotions"),
    StickerPresetItem("emo_monocle", "🧐", "Fancy Monocle", "Emoji & Emotions"),
    StickerPresetItem("emo_scream", "😱", "Scared Scream", "Emoji & Emotions", StickerAnimationType.SHAKE),
    StickerPresetItem("emo_hug", "🤗", "Warm Hug", "Emoji & Emotions")
  )

  val LOVE_HEARTS = listOf(
    StickerPresetItem("love_red_heart", "❤️", "Red Heart", "Love & Hearts", StickerAnimationType.HEARTBEAT),
    StickerPresetItem("love_sparkle_heart", "💖", "Sparkle Heart", "Love & Hearts", StickerAnimationType.GLOW_PULSE),
    StickerPresetItem("love_two_hearts", "💕", "Two Hearts", "Love & Hearts", StickerAnimationType.FLOAT),
    StickerPresetItem("love_beating_heart", "💓", "Beating Heart", "Love & Hearts", StickerAnimationType.HEARTBEAT),
    StickerPresetItem("love_growing_heart", "💗", "Growing Heart", "Love & Hearts", StickerAnimationType.PULSE),
    StickerPresetItem("love_revolving_hearts", "💞", "Revolving Hearts", "Love & Hearts", StickerAnimationType.SPIN),
    StickerPresetItem("love_cupid_arrow", "💘", "Heart With Arrow", "Love & Hearts"),
    StickerPresetItem("love_letter", "💌", "Love Letter", "Love & Hearts", StickerAnimationType.FLOAT),
    StickerPresetItem("love_ring", "💍", "Diamond Ring", "Love & Hearts", StickerAnimationType.GLOW_PULSE),
    StickerPresetItem("love_bouquet", "💐", "Flower Bouquet", "Love & Hearts"),
    StickerPresetItem("love_rose", "🌹", "Red Rose", "Love & Hearts"),
    StickerPresetItem("love_kiss_mark", "💋", "Kiss Mark", "Love & Hearts"),
    StickerPresetItem("love_gift_box", "💝", "Heart Ribbon Box", "Love & Hearts", StickerAnimationType.BOUNCE),
    StickerPresetItem("love_hands", "🫶", "Heart Hands", "Love & Hearts", StickerAnimationType.PULSE),
    StickerPresetItem("love_broken_heart", "💔", "Broken Heart", "Love & Hearts"),
    StickerPresetItem("love_black_heart", "🖤", "Black Heart", "Love & Hearts")
  )

  val FUNNY_MEMES = listOf(
    StickerPresetItem("meme_moyai", "🗿", "Sigma Moyai", "Funny & Memes"),
    StickerPresetItem("meme_skull", "💀", "I'm Dead", "Funny & Memes"),
    StickerPresetItem("meme_frog", "🐸", "Pepe Frog", "Funny & Memes"),
    StickerPresetItem("meme_doge", "🐶", "Doge Dog", "Funny & Memes", StickerAnimationType.BOUNCE),
    StickerPresetItem("meme_cat_cry", "🐱", "Sad Cat", "Funny & Memes"),
    StickerPresetItem("meme_clown", "🤡", "Clown Check", "Funny & Memes"),
    StickerPresetItem("meme_poop", "💩", "Swag Poop", "Funny & Memes", StickerAnimationType.SWING),
    StickerPresetItem("meme_popcorn", "🍿", "Drama Watching", "Funny & Memes"),
    StickerPresetItem("meme_glasses", "🕶️", "Thug Life Shades", "Funny & Memes"),
    StickerPresetItem("meme_banana", "🍌", "Banana Peel", "Funny & Memes", StickerAnimationType.SWING),
    StickerPresetItem("meme_duck", "🦆", "Peace Was Never Option", "Funny & Memes"),
    StickerPresetItem("meme_unicorn", "🦄", "Rainbow Magic", "Funny & Memes", StickerAnimationType.FLOAT),
    StickerPresetItem("meme_monkey_eyes", "🙈", "See No Evil", "Funny & Memes"),
    StickerPresetItem("meme_monkey_mouth", "🙊", "Speak No Evil", "Funny & Memes"),
    StickerPresetItem("meme_dancer_man", "🕺", "Groovy Dance", "Funny & Memes", StickerAnimationType.BOUNCE),
    StickerPresetItem("meme_dancer_woman", "💃", "Salsa Dance", "Funny & Memes", StickerAnimationType.SWING)
  )

  val ANIMALS = listOf(
    StickerPresetItem("anim_dog", "🐶", "Puppy", "Animals", StickerAnimationType.BOUNCE),
    StickerPresetItem("anim_cat", "🐱", "Kitty", "Animals"),
    StickerPresetItem("anim_lion", "🦁", "Lion King", "Animals"),
    StickerPresetItem("anim_tiger", "🐯", "Tiger", "Animals"),
    StickerPresetItem("anim_panda", "🐼", "Panda Bear", "Animals"),
    StickerPresetItem("anim_koala", "🐨", "Koala", "Animals"),
    StickerPresetItem("anim_fox", "🦊", "Fox", "Animals"),
    StickerPresetItem("anim_rabbit", "🐰", "Bunny Rabbit", "Animals", StickerAnimationType.BOUNCE),
    StickerPresetItem("anim_bear", "🐻", "Grizzly Bear", "Animals"),
    StickerPresetItem("anim_frog2", "🐸", "Tree Frog", "Animals"),
    StickerPresetItem("anim_monkey", "🐵", "Monkey", "Animals", StickerAnimationType.SWING),
    StickerPresetItem("anim_dolphin", "🐬", "Ocean Dolphin", "Animals", StickerAnimationType.FLOAT),
    StickerPresetItem("anim_butterfly", "🦋", "Butterfly", "Animals", StickerAnimationType.FLOAT),
    StickerPresetItem("anim_eagle", "🦅", "Soaring Eagle", "Animals"),
    StickerPresetItem("anim_owl", "🦉", "Wise Owl", "Animals"),
    StickerPresetItem("anim_bee", "🐝", "Honey Bee", "Animals", StickerAnimationType.SHAKE),
    StickerPresetItem("anim_trex", "🦖", "T-Rex Dino", "Animals", StickerAnimationType.SHAKE),
    StickerPresetItem("anim_turtle", "🐢", "Sea Turtle", "Animals"),
    StickerPresetItem("anim_penguin", "🐧", "Penguin", "Animals", StickerAnimationType.SWING),
    StickerPresetItem("anim_flamingo", "🦩", "Pink Flamingo", "Animals")
  )

  val FOOD_DRINKS = listOf(
    StickerPresetItem("food_pizza", "🍕", "Slice Pizza", "Food & Drinks"),
    StickerPresetItem("food_burger", "🍔", "Cheeseburger", "Food & Drinks"),
    StickerPresetItem("food_fries", "🍟", "French Fries", "Food & Drinks"),
    StickerPresetItem("food_hotdog", "🌭", "Hot Dog", "Food & Drinks"),
    StickerPresetItem("food_donut", "🍩", "Glazed Donut", "Food & Drinks"),
    StickerPresetItem("food_icecream", "🍦", "Soft Ice Cream", "Food & Drinks"),
    StickerPresetItem("food_cake", "🍰", "Strawberry Shortcake", "Food & Drinks"),
    StickerPresetItem("food_bday_cake", "🎂", "Celebration Cake", "Food & Drinks", StickerAnimationType.BOUNCE),
    StickerPresetItem("food_choc", "🍫", "Chocolate Bar", "Food & Drinks"),
    StickerPresetItem("food_coffee", "☕", "Hot Espresso", "Food & Drinks"),
    StickerPresetItem("food_boba", "🧋", "Boba Milk Tea", "Food & Drinks"),
    StickerPresetItem("food_cocktail", "🍹", "Tropical Drink", "Food & Drinks"),
    StickerPresetItem("food_beer", "🍺", "Beer Cheers", "Food & Drinks"),
    StickerPresetItem("food_wine", "🍷", "Red Wine", "Food & Drinks"),
    StickerPresetItem("food_sushi", "🍣", "Salmon Sushi", "Food & Drinks"),
    StickerPresetItem("food_taco", "🌮", "Crispy Taco", "Food & Drinks"),
    StickerPresetItem("food_ramen", "🍜", "Hot Ramen", "Food & Drinks"),
    StickerPresetItem("food_avocado", "🥑", "Avocado", "Food & Drinks"),
    StickerPresetItem("food_strawberry", "🍓", "Fresh Berry", "Food & Drinks"),
    StickerPresetItem("food_watermelon", "🍉", "Juicy Melon", "Food & Drinks")
  )

  val TRAVEL = listOf(
    StickerPresetItem("trav_plane", "✈️", "Airplane Flight", "Travel", StickerAnimationType.FLOAT),
    StickerPresetItem("trav_rocket", "🚀", "Rocket Odyssey", "Travel", StickerAnimationType.FLOAT),
    StickerPresetItem("trav_cruise", "🚢", "Luxury Cruise", "Travel"),
    StickerPresetItem("trav_island", "🏝️", "Tropical Island", "Travel"),
    StickerPresetItem("trav_beach", "🏖️", "Sunny Beach", "Travel"),
    StickerPresetItem("trav_mountain", "🏔️", "Snowy Peak", "Travel"),
    StickerPresetItem("trav_eiffel", "🗼", "Tokyo Tower", "Travel"),
    StickerPresetItem("trav_liberty", "🗽", "Statue Liberty", "Travel"),
    StickerPresetItem("trav_map", "🗺️", "World Map", "Travel"),
    StickerPresetItem("trav_luggage", "🧳", "Luggage Bag", "Travel"),
    StickerPresetItem("trav_car", "🚗", "Road Trip Car", "Travel"),
    StickerPresetItem("trav_scooter", "🛵", "Vespa Scooter", "Travel"),
    StickerPresetItem("trav_tent", "⛺", "Camp Tent", "Travel"),
    StickerPresetItem("trav_sunset", "🌅", "Golden Sunrise", "Travel"),
    StickerPresetItem("trav_camera", "📸", "Flash Camera", "Travel", StickerAnimationType.PULSE),
    StickerPresetItem("trav_compass", "🧭", "Nav Compass", "Travel", StickerAnimationType.SPIN),
    StickerPresetItem("trav_ticket", "🎟️", "Flight Ticket", "Travel"),
    StickerPresetItem("trav_train", "🚆", "High-speed Rail", "Travel"),
    StickerPresetItem("trav_globe", "🌍", "Earth Globe", "Travel", StickerAnimationType.SPIN)
  )

  val NATURE = listOf(
    StickerPresetItem("nat_tree", "🌲", "Evergreen Tree", "Nature"),
    StickerPresetItem("nat_deciduous", "🌳", "Oak Tree", "Nature"),
    StickerPresetItem("nat_palm", "🌴", "Palm Tree", "Nature", StickerAnimationType.SWING),
    StickerPresetItem("nat_herb", "🌿", "Green Herb", "Nature"),
    StickerPresetItem("nat_clover", "🍀", "Four Leaf Clover", "Nature", StickerAnimationType.GLOW_PULSE),
    StickerPresetItem("nat_maple", "🍁", "Maple Leaf", "Nature", StickerAnimationType.SWING),
    StickerPresetItem("nat_autumn_leaf", "🍂", "Fallen Leaf", "Nature", StickerAnimationType.FLOAT),
    StickerPresetItem("nat_wind_leaf", "🍃", "Leaves In Wind", "Nature", StickerAnimationType.FLOAT),
    StickerPresetItem("nat_mushroom", "🍄", "Forest Mushroom", "Nature"),
    StickerPresetItem("nat_cactus", "🌵", "Desert Cactus", "Nature"),
    StickerPresetItem("nat_wave", "🌊", "Ocean Wave", "Nature", StickerAnimationType.FLOAT),
    StickerPresetItem("nat_mountain2", "⛰️", "Mountain Ridge", "Nature"),
    StickerPresetItem("nat_volcano", "🌋", "Active Volcano", "Nature", StickerAnimationType.SHAKE),
    StickerPresetItem("nat_park", "🏞️", "National Park", "Nature"),
    StickerPresetItem("nat_potted_plant", "🪴", "Potted Plant", "Nature"),
    StickerPresetItem("nat_bamboo", "🎋", "Tanabata Bamboo", "Nature")
  )

  val FLOWERS = listOf(
    StickerPresetItem("flw_cherry", "🌸", "Cherry Blossom", "Flowers", StickerAnimationType.FLOAT),
    StickerPresetItem("flw_hibiscus", "🌺", "Tropical Hibiscus", "Flowers"),
    StickerPresetItem("flw_rose", "🌹", "Red Rose", "Flowers"),
    StickerPresetItem("flw_tulip", "🌷", "Pink Tulip", "Flowers"),
    StickerPresetItem("flw_sunflower", "🌻", "Bright Sunflower", "Flowers", StickerAnimationType.PULSE),
    StickerPresetItem("flw_daisy", "🌼", "Yellow Blossom", "Flowers"),
    StickerPresetItem("flw_bouquet2", "💐", "Floral Bouquet", "Flowers"),
    StickerPresetItem("flw_lotus", "🪷", "Sacred Lotus", "Flowers", StickerAnimationType.GLOW_PULSE),
    StickerPresetItem("flw_rosette", "🏵️", "Golden Rosette", "Flowers"),
    StickerPresetItem("flw_white_flower", "💮", "White Flower Stamp", "Flowers"),
    StickerPresetItem("flw_wilted", "🥀", "Wilted Rose", "Flowers"),
    StickerPresetItem("flw_hyacinth", "🪻", "Hyacinth", "Flowers")
  )

  val WEATHER = listOf(
    StickerPresetItem("wea_sun", "☀️", "Bright Sun", "Weather", StickerAnimationType.SPIN),
    StickerPresetItem("wea_sun_cloud", "🌤️", "Sun Behind Cloud", "Weather"),
    StickerPresetItem("wea_clouds", "⛅", "Cloudy Skies", "Weather"),
    StickerPresetItem("wea_rain", "🌧️", "Rain Drops", "Weather"),
    StickerPresetItem("wea_thunder_rain", "⛈️", "Thunderstorm", "Weather", StickerAnimationType.SHAKE),
    StickerPresetItem("wea_lightning", "🌩️", "Lightning Cloud", "Weather", StickerAnimationType.SHAKE),
    StickerPresetItem("wea_snowflake", "❄️", "Snowflake", "Weather", StickerAnimationType.SPIN),
    StickerPresetItem("wea_bolt", "⚡", "High Voltage", "Weather", StickerAnimationType.PULSE),
    StickerPresetItem("wea_rainbow", "🌈", "Rainbow Arc", "Weather"),
    StickerPresetItem("wea_tornado", "🌪️", "Tornado Twister", "Weather", StickerAnimationType.SPIN),
    StickerPresetItem("wea_snowman", "⛄", "Snowman", "Weather"),
    StickerPresetItem("wea_umbrella", "☔", "Umbrella Rain", "Weather"),
    StickerPresetItem("wea_moon", "🌙", "Crescent Moon", "Weather", StickerAnimationType.FLOAT),
    StickerPresetItem("wea_glowing_star", "🌟", "Glowing Star", "Weather", StickerAnimationType.GLOW_PULSE),
    StickerPresetItem("wea_comet", "☄️", "Comet Asteroid", "Weather", StickerAnimationType.FLOAT)
  )

  val SPORTS = listOf(
    StickerPresetItem("spt_soccer", "⚽", "Soccer Ball", "Sports", StickerAnimationType.SPIN),
    StickerPresetItem("spt_basketball", "🏀", "Basketball", "Sports", StickerAnimationType.BOUNCE),
    StickerPresetItem("spt_football", "🏈", "American Football", "Sports"),
    StickerPresetItem("spt_baseball", "⚾", "Baseball", "Sports"),
    StickerPresetItem("spt_tennis", "🎾", "Tennis Ball", "Sports", StickerAnimationType.BOUNCE),
    StickerPresetItem("spt_volleyball", "🏐", "Volleyball", "Sports"),
    StickerPresetItem("spt_boxing", "🥊", "Boxing Glove", "Sports", StickerAnimationType.PULSE),
    StickerPresetItem("spt_martial_arts", "🥋", "Martial Arts Gi", "Sports"),
    StickerPresetItem("spt_trophy2", "🏆", "Champion Cup", "Sports", StickerAnimationType.BOUNCE),
    StickerPresetItem("spt_gold_medal", "🥇", "Gold 1st Medal", "Sports", StickerAnimationType.GLOW_PULSE),
    StickerPresetItem("spt_silver_medal", "🥈", "Silver 2nd Medal", "Sports"),
    StickerPresetItem("spt_bronze_medal", "🥉", "Bronze 3rd Medal", "Sports"),
    StickerPresetItem("spt_bullseye", "🎯", "Target Bullseye", "Sports"),
    StickerPresetItem("spt_skateboard", "🛹", "Skateboard", "Sports"),
    StickerPresetItem("spt_surfing", "🏄", "Surfer Wave", "Sports", StickerAnimationType.FLOAT),
    StickerPresetItem("spt_bike", "🚴", "Bicycle Racing", "Sports"),
    StickerPresetItem("spt_weights", "🏋️", "Weightlifting", "Sports"),
    StickerPresetItem("spt_swimming", "🏊", "Swimming Pool", "Sports"),
    StickerPresetItem("spt_golf", "⛳", "Golf Flag", "Sports")
  )

  val GAMING = listOf(
    StickerPresetItem("gam_controller", "🎮", "Game Controller", "Gaming", StickerAnimationType.PULSE),
    StickerPresetItem("gam_joystick", "🕹️", "Retro Joystick", "Gaming"),
    StickerPresetItem("gam_dice", "🎲", "Lucky Dice", "Gaming", StickerAnimationType.SPIN),
    StickerPresetItem("gam_invader", "👾", "Alien Invader", "Gaming", StickerAnimationType.BOUNCE),
    StickerPresetItem("gam_slot", "🎰", "Slot Jackpot", "Gaming"),
    StickerPresetItem("gam_puzzle", "🧩", "Puzzle Piece", "Gaming"),
    StickerPresetItem("gam_chess", "♟️", "Chess Pawn", "Gaming"),
    StickerPresetItem("gam_swords", "⚔️", "Crossed Swords", "Gaming", StickerAnimationType.SHAKE),
    StickerPresetItem("gam_shield", "🛡️", "Protective Shield", "Gaming"),
    StickerPresetItem("gam_bow", "🏹", "Bow & Arrow", "Gaming"),
    StickerPresetItem("gam_crystal_ball", "🔮", "Magic Orb", "Gaming", StickerAnimationType.GLOW_PULSE),
    StickerPresetItem("gam_bomb2", "💣", "Pixel Bomb", "Gaming", StickerAnimationType.SHAKE),
    StickerPresetItem("gam_gem", "💎", "Power Crystal", "Gaming", StickerAnimationType.GLOW_PULSE),
    StickerPresetItem("gam_crown", "👑", "Royal Victor", "Gaming", StickerAnimationType.BOUNCE),
    StickerPresetItem("gam_coin", "🪙", "Gold Token", "Gaming", StickerAnimationType.SPIN)
  )

  val CELEBRATION = listOf(
    StickerPresetItem("cel_popper", "🎉", "Party Popper", "Celebration", StickerAnimationType.POP_IN),
    StickerPresetItem("cel_confetti", "🎊", "Confetti Ball", "Celebration", StickerAnimationType.FLOAT),
    StickerPresetItem("cel_party_face", "🥳", "Celebrating Face", "Celebration", StickerAnimationType.BOUNCE),
    StickerPresetItem("cel_champagne", "🍾", "Popping Bottle", "Celebration", StickerAnimationType.SHAKE),
    StickerPresetItem("cel_glasses", "🥂", "Clinking Flutes", "Celebration", StickerAnimationType.SWING),
    StickerPresetItem("cel_balloon", "🎈", "Red Balloon", "Celebration", StickerAnimationType.FLOAT),
    StickerPresetItem("cel_gift", "🎁", "Gift Box", "Celebration", StickerAnimationType.BOUNCE),
    StickerPresetItem("cel_fireworks", "🎆", "Night Fireworks", "Celebration", StickerAnimationType.GLOW_PULSE),
    StickerPresetItem("cel_sparkler", "🎇", "Sparkler Stick", "Celebration"),
    StickerPresetItem("cel_pinata", "🪅", "Colorful Piñata", "Celebration", StickerAnimationType.SWING),
    StickerPresetItem("cel_mirror_ball", "🪩", "Disco Mirror Ball", "Celebration", StickerAnimationType.SPIN),
    StickerPresetItem("cel_crown2", "👑", "King of the Party", "Celebration"),
    StickerPresetItem("cel_trumpet", "🎺", "Brass Trumpet", "Celebration"),
    StickerPresetItem("cel_sax", "🎷", "Saxophone Jazz", "Celebration")
  )

  val BIRTHDAY = listOf(
    StickerPresetItem("bday_cake", "🎂", "Birthday Cake", "Birthday", StickerAnimationType.BOUNCE),
    StickerPresetItem("bday_cupcake", "🧁", "Frosted Cupcake", "Birthday"),
    StickerPresetItem("bday_balloons", "🎈", "Helium Balloons", "Birthday", StickerAnimationType.FLOAT),
    StickerPresetItem("bday_gift2", "🎁", "Wrapped Present", "Birthday", StickerAnimationType.BOUNCE),
    StickerPresetItem("bday_party2", "🥳", "Birthday Kid", "Birthday", StickerAnimationType.BOUNCE),
    StickerPresetItem("bday_popper2", "🎉", "Birthday Surprise", "Birthday", StickerAnimationType.POP_IN),
    StickerPresetItem("bday_candle", "🕯️", "Wish Candle", "Birthday"),
    StickerPresetItem("bday_candy", "🍬", "Sweet Candy", "Birthday"),
    StickerPresetItem("bday_lollipop", "🍭", "Swirl Lollipop", "Birthday"),
    StickerPresetItem("bday_ribbon", "🎀", "Pink Ribbon Bow", "Birthday"),
    StickerPresetItem("bday_wand", "🪄", "Magic Birthday Wand", "Birthday", StickerAnimationType.GLOW_PULSE),
    StickerPresetItem("bday_crown3", "👑", "Birthday Crown", "Birthday")
  )

  val WEDDING = listOf(
    StickerPresetItem("wed_ring", "💍", "Engagement Ring", "Wedding", StickerAnimationType.GLOW_PULSE),
    StickerPresetItem("wed_bride", "👰", "Beautiful Bride", "Wedding"),
    StickerPresetItem("wed_groom", "🤵", "Handsome Groom", "Wedding"),
    StickerPresetItem("wed_bouquet", "💐", "Bridal Bouquet", "Wedding"),
    StickerPresetItem("wed_church", "💒", "Wedding Chapel", "Wedding"),
    StickerPresetItem("wed_dove", "🕊️", "Peaceful Dove", "Wedding", StickerAnimationType.FLOAT),
    StickerPresetItem("wed_letter", "💌", "Wedding Invitation", "Wedding"),
    StickerPresetItem("wed_cheers", "🥂", "Wedding Toast", "Wedding", StickerAnimationType.SWING),
    StickerPresetItem("wed_cake", "🎂", "Tiered Wedding Cake", "Wedding"),
    StickerPresetItem("wed_hearts", "💖", "Pure Devotion", "Wedding", StickerAnimationType.HEARTBEAT),
    StickerPresetItem("wed_rose", "🌹", "Eternal Red Rose", "Wedding"),
    StickerPresetItem("wed_diamond", "💎", "Forever Diamond", "Wedding", StickerAnimationType.GLOW_PULSE)
  )

  val ISLAMIC = listOf(
    StickerPresetItem("isl_mosque", "🕌", "Masjid Mosque", "Islamic"),
    StickerPresetItem("isl_kaaba", "🕋", "Holy Kaaba", "Islamic"),
    StickerPresetItem("isl_quran", "📖", "Noble Quran", "Islamic"),
    StickerPresetItem("isl_crescent", "🌙", "Hilal Moon", "Islamic", StickerAnimationType.FLOAT),
    StickerPresetItem("isl_star", "⭐️", "Islamic Star", "Islamic", StickerAnimationType.GLOW_PULSE),
    StickerPresetItem("isl_beads", "📿", "Prayer Beads (Tasbih)", "Islamic"),
    StickerPresetItem("isl_dua", "🤲", "Supplication Dua", "Islamic"),
    StickerPresetItem("isl_lamp", "🪔", "Diya Lantern", "Islamic"),
    StickerPresetItem("isl_palm", "🌴", "Oasis Palm", "Islamic"),
    StickerPresetItem("isl_dove", "🕊️", "Peace Dove", "Islamic", StickerAnimationType.FLOAT),
    StickerPresetItem("isl_scroll", "📜", "Sacred Scroll", "Islamic")
  )

  val RAMADAN_EID = listOf(
    StickerPresetItem("ram_crescent", "🌙", "Ramadan Moon", "Ramadan & Eid", StickerAnimationType.FLOAT),
    StickerPresetItem("ram_lantern", "🏮", "Fanous Lantern", "Ramadan & Eid", StickerAnimationType.SWING),
    StickerPresetItem("ram_mosque", "🕌", "Taraweeh Mosque", "Ramadan & Eid"),
    StickerPresetItem("ram_tasbih", "📿", "Dhikr Tasbih", "Ramadan & Eid"),
    StickerPresetItem("ram_lamp", "🪔", "Oil Lamp Light", "Ramadan & Eid", StickerAnimationType.GLOW_PULSE),
    StickerPresetItem("ram_sheep", "🐑", "Eid Al-Adha Sheep", "Ramadan & Eid"),
    StickerPresetItem("ram_gift", "🎁", "Eidi Gift", "Ramadan & Eid", StickerAnimationType.BOUNCE),
    StickerPresetItem("ram_sweets", "🍬", "Eid Sweets", "Ramadan & Eid"),
    StickerPresetItem("ram_coffee", "☕", "Arabic Qahwa", "Ramadan & Eid"),
    StickerPresetItem("ram_dua", "🤲", "Iftar Dua", "Ramadan & Eid"),
    StickerPresetItem("ram_celebrate", "🎊", "Eid Mubarak Festivities", "Ramadan & Eid", StickerAnimationType.POP_IN),
    StickerPresetItem("ram_fireworks", "🎆", "Eid Fireworks", "Ramadan & Eid", StickerAnimationType.GLOW_PULSE)
  )

  val BUSINESS = listOf(
    StickerPresetItem("biz_briefcase", "💼", "Executive Briefcase", "Business"),
    StickerPresetItem("biz_bar_chart", "📊", "Analytics Chart", "Business"),
    StickerPresetItem("biz_trend_up", "📈", "Growth Trend", "Business", StickerAnimationType.PULSE),
    StickerPresetItem("biz_money_bag", "💰", "Revenue Bag", "Business", StickerAnimationType.BOUNCE),
    StickerPresetItem("biz_card", "💳", "Credit Card", "Business"),
    StickerPresetItem("biz_dollar", "💵", "Cash Stack", "Business"),
    StickerPresetItem("biz_office", "🏢", "HQ Office", "Business"),
    StickerPresetItem("biz_handshake", "🤝", "Deal Handshake", "Business"),
    StickerPresetItem("biz_laptop", "💻", "MacBook Work", "Business"),
    StickerPresetItem("biz_target", "🎯", "KPI Target", "Business"),
    StickerPresetItem("biz_rocket", "🚀", "Startup Launch", "Business", StickerAnimationType.FLOAT),
    StickerPresetItem("biz_idea", "💡", "Innovation Idea", "Business", StickerAnimationType.GLOW_PULSE)
  )

  val SHOPPING = listOf(
    StickerPresetItem("shp_bags", "🛍️", "Shopping Bags", "Shopping", StickerAnimationType.BOUNCE),
    StickerPresetItem("shp_cart", "🛒", "Grocery Cart", "Shopping"),
    StickerPresetItem("shp_tag", "🏷️", "Price Tag", "Shopping", StickerAnimationType.SWING),
    StickerPresetItem("shp_card", "💳", "Payment Card", "Shopping"),
    StickerPresetItem("shp_box", "📦", "Amazon Delivery", "Shopping"),
    StickerPresetItem("shp_mall", "🏬", "Department Store", "Shopping"),
    StickerPresetItem("shp_dress", "👗", "Fashion Dress", "Shopping"),
    StickerPresetItem("shp_heel", "👠", "High Heels", "Shopping"),
    StickerPresetItem("shp_sneaker", "👟", "Sneakers", "Shopping"),
    StickerPresetItem("shp_handbag", "👜", "Luxury Handbag", "Shopping"),
    StickerPresetItem("shp_lipstick", "💄", "Cosmetic Lipstick", "Shopping")
  )

  val SALE_DISCOUNT = listOf(
    StickerPresetItem("sale_tag", "🏷️", "Discount Tag", "Sale & Discount", StickerAnimationType.SWING),
    StickerPresetItem("sale_money_fly", "💸", "Save Money", "Sale & Discount", StickerAnimationType.FLOAT),
    StickerPresetItem("sale_cash_bag", "💰", "Cashback", "Sale & Discount", StickerAnimationType.BOUNCE),
    StickerPresetItem("sale_boom", "💥", "Price Crash", "Sale & Discount", StickerAnimationType.SHAKE),
    StickerPresetItem("sale_flash", "⚡", "Flash Sale", "Sale & Discount", StickerAnimationType.PULSE),
    StickerPresetItem("sale_fire", "🔥", "Mega Hot Deal", "Sale & Discount", StickerAnimationType.PULSE),
    StickerPresetItem("sale_100", "💯", "100% Best Price", "Sale & Discount"),
    StickerPresetItem("sale_megaphone", "📣", "Clearance Alert", "Sale & Discount", StickerAnimationType.SHAKE),
    StickerPresetItem("sale_bell", "🔔", "Sale Reminder", "Sale & Discount", StickerAnimationType.SWING),
    StickerPresetItem("sale_gift", "🎁", "Free Gift Included", "Sale & Discount", StickerAnimationType.BOUNCE)
  )

  val SOCIAL_MEDIA = listOf(
    StickerPresetItem("soc_play", "▶️", "Play Video", "Social Media", StickerAnimationType.PULSE),
    StickerPresetItem("soc_rec", "🔴", "REC Live", "Social Media", StickerAnimationType.GLOW_PULSE),
    StickerPresetItem("soc_camera", "📹", "Vlog Camera", "Social Media"),
    StickerPresetItem("soc_photo", "📸", "Instagram Photo", "Social Media", StickerAnimationType.PULSE),
    StickerPresetItem("soc_music", "🎵", "TikTok Sound", "Social Media", StickerAnimationType.SWING),
    StickerPresetItem("soc_bubble", "💬", "Comment Speech", "Social Media"),
    StickerPresetItem("soc_bell", "🔔", "Subscribe Bell", "Social Media", StickerAnimationType.SWING),
    StickerPresetItem("soc_thumbs_up", "👍", "Smash Like", "Social Media", StickerAnimationType.BOUNCE),
    StickerPresetItem("soc_heart", "❤️", "Double Tap Heart", "Social Media", StickerAnimationType.HEARTBEAT),
    StickerPresetItem("soc_star", "🌟", "Follow Star", "Social Media", StickerAnimationType.GLOW_PULSE),
    StickerPresetItem("soc_rocket", "🚀", "Go Viral", "Social Media", StickerAnimationType.FLOAT),
    StickerPresetItem("soc_megaphone", "📢", "Announcement", "Social Media", StickerAnimationType.SHAKE),
    StickerPresetItem("soc_link", "🔗", "Link In Bio", "Social Media"),
    StickerPresetItem("soc_pin", "📌", "Pinned Post", "Social Media"),
    StickerPresetItem("soc_views", "👁️", "1M Views", "Social Media")
  )

  val ARROWS_SHAPES = listOf(
    StickerPresetItem("arr_right", "➡️", "Arrow Right", "Arrows & Shapes"),
    StickerPresetItem("arr_left", "⬅️", "Arrow Left", "Arrows & Shapes"),
    StickerPresetItem("arr_up", "⬆️", "Arrow Up", "Arrows & Shapes"),
    StickerPresetItem("arr_down", "⬇️", "Arrow Down", "Arrows & Shapes"),
    StickerPresetItem("arr_up_right", "↗️", "Diagonal Up", "Arrows & Shapes"),
    StickerPresetItem("arr_curved", "🔄", "Reload Loop", "Arrows & Shapes", StickerAnimationType.SPIN),
    StickerPresetItem("arr_repeat", "🔁", "Repeat All", "Arrows & Shapes"),
    StickerPresetItem("arr_shuffle", "🔀", "Shuffle Track", "Arrows & Shapes"),
    StickerPresetItem("shp_red_triangle", "🔺", "Red Triangle", "Arrows & Shapes"),
    StickerPresetItem("shp_blue_diamond", "🔹", "Blue Diamond", "Arrows & Shapes"),
    StickerPresetItem("shp_star", "⭐", "Gold Star", "Arrows & Shapes", StickerAnimationType.GLOW_PULSE),
    StickerPresetItem("shp_heart_shape", "💖", "Pink Heart", "Arrows & Shapes", StickerAnimationType.HEARTBEAT),
    StickerPresetItem("shp_circle_red", "⭕", "Red Circle Ring", "Arrows & Shapes"),
    StickerPresetItem("shp_cross_mark", "❌", "Cross Reject", "Arrows & Shapes"),
    StickerPresetItem("shp_question", "❓", "Question Mark", "Arrows & Shapes", StickerAnimationType.SWING),
    StickerPresetItem("shp_exclamation", "❗", "Warning Alert", "Arrows & Shapes", StickerAnimationType.SHAKE),
    StickerPresetItem("shp_caution", "⚠️", "Caution Sign", "Arrows & Shapes", StickerAnimationType.PULSE)
  )

  val SPEECH_BUBBLES = listOf(
    StickerPresetItem("sp_speech", "💬", "Speech Balloon", "Speech Bubbles"),
    StickerPresetItem("sp_left_speech", "🗨️", "Left Speech Bubble", "Speech Bubbles"),
    StickerPresetItem("sp_anger", "🗯️", "Anger Shout", "Speech Bubbles", StickerAnimationType.SHAKE),
    StickerPresetItem("sp_thought", "💭", "Thought Bubble", "Speech Bubbles", StickerAnimationType.FLOAT),
    StickerPresetItem("sp_speaking", "🗣️", "Speaking Head", "Speech Bubbles"),
    StickerPresetItem("sp_loudspeaker", "📢", "Public PA Speaker", "Speech Bubbles", StickerAnimationType.SHAKE),
    StickerPresetItem("sp_megaphone", "📣", "Megaphone Cheer", "Speech Bubbles", StickerAnimationType.SWING),
    StickerPresetItem("sp_mic", "🎙️", "Studio Mic", "Speech Bubbles"),
    StickerPresetItem("sp_radio", "📻", "Vintage Radio", "Speech Bubbles"),
    StickerPresetItem("sp_lightbulb", "💡", "Eureka Moment", "Speech Bubbles", StickerAnimationType.GLOW_PULSE)
  )

  val DECORATIVE_ELEMENTS = listOf(
    StickerPresetItem("dec_sparkles", "✨", "Golden Sparkles", "Decorative Elements", StickerAnimationType.GLOW_PULSE),
    StickerPresetItem("dec_glowing_star", "🌟", "Glitter Star", "Decorative Elements", StickerAnimationType.GLOW_PULSE),
    StickerPresetItem("dec_star", "⭐", "Five Point Star", "Decorative Elements"),
    StickerPresetItem("dec_dizzy", "💫", "Swirling Stars", "Decorative Elements", StickerAnimationType.SPIN),
    StickerPresetItem("dec_lightning", "⚡", "Energy Bolt", "Decorative Elements", StickerAnimationType.PULSE),
    StickerPresetItem("dec_collision", "💥", "Impact Flash", "Decorative Elements", StickerAnimationType.SHAKE),
    StickerPresetItem("dec_anger", "💢", "Anime Anger", "Decorative Elements", StickerAnimationType.PULSE),
    StickerPresetItem("dec_dash", "💨", "Speed Dash", "Decorative Elements", StickerAnimationType.FLOAT),
    StickerPresetItem("dec_sweat", "💦", "Sweat Drops", "Decorative Elements"),
    StickerPresetItem("dec_bubbles", "🫧", "Floating Soap Bubbles", "Decorative Elements", StickerAnimationType.FLOAT),
    StickerPresetItem("dec_wand", "🪄", "Magic Wand", "Decorative Elements", StickerAnimationType.GLOW_PULSE),
    StickerPresetItem("dec_ribbon", "🎀", "Cute Ribbon", "Decorative Elements"),
    StickerPresetItem("dec_medal", "🎖️", "Military Medal", "Decorative Elements"),
    StickerPresetItem("dec_crown", "👑", "Imperial Crown", "Decorative Elements"),
    StickerPresetItem("dec_diamond", "💎", "Precious Diamond", "Decorative Elements", StickerAnimationType.GLOW_PULSE)
  )

  fun getItemsForCategory(category: String): List<StickerPresetItem> {
    return when (category) {
      "Badges" -> BADGES
      "Animated Stickers" -> ANIMATED_STICKERS
      "Trending Stickers" -> TRENDING_STICKERS
      "Emoji & Emotions" -> EMOJI_EMOTIONS
      "Love & Hearts" -> LOVE_HEARTS
      "Funny & Memes" -> FUNNY_MEMES
      "Animals" -> ANIMALS
      "Food & Drinks" -> FOOD_DRINKS
      "Travel" -> TRAVEL
      "Nature" -> NATURE
      "Flowers" -> FLOWERS
      "Weather" -> WEATHER
      "Sports" -> SPORTS
      "Gaming" -> GAMING
      "Celebration" -> CELEBRATION
      "Birthday" -> BIRTHDAY
      "Wedding" -> WEDDING
      "Islamic" -> ISLAMIC
      "Ramadan & Eid" -> RAMADAN_EID
      "Business" -> BUSINESS
      "Shopping" -> SHOPPING
      "Sale & Discount" -> SALE_DISCOUNT
      "Social Media" -> SOCIAL_MEDIA
      "Arrows & Shapes" -> ARROWS_SHAPES
      "Speech Bubbles" -> SPEECH_BUBBLES
      "Decorative Elements" -> DECORATIVE_ELEMENTS
      else -> emptyList()
    }
  }

  fun getAllStickers(): List<StickerPresetItem> {
    val result = mutableListOf<StickerPresetItem>()
    result.addAll(BADGES)
    result.addAll(ANIMATED_STICKERS)
    result.addAll(TRENDING_STICKERS)
    result.addAll(EMOJI_EMOTIONS)
    result.addAll(LOVE_HEARTS)
    result.addAll(FUNNY_MEMES)
    result.addAll(ANIMALS)
    result.addAll(FOOD_DRINKS)
    result.addAll(TRAVEL)
    result.addAll(NATURE)
    result.addAll(FLOWERS)
    result.addAll(WEATHER)
    result.addAll(SPORTS)
    result.addAll(GAMING)
    result.addAll(CELEBRATION)
    result.addAll(BIRTHDAY)
    result.addAll(WEDDING)
    result.addAll(ISLAMIC)
    result.addAll(RAMADAN_EID)
    result.addAll(BUSINESS)
    result.addAll(SHOPPING)
    result.addAll(SALE_DISCOUNT)
    result.addAll(SOCIAL_MEDIA)
    result.addAll(ARROWS_SHAPES)
    result.addAll(SPEECH_BUBBLES)
    result.addAll(DECORATIVE_ELEMENTS)
    return result.distinctBy { it.id }
  }
}
