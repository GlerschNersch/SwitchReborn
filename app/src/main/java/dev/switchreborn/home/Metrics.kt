package dev.switchreborn.home

/** Measurements of the Switch HOME menu in a 1920x1080 frame. See docs/RE_NOTES.md. */
object Metrics {
    const val REF_W = 1920f
    const val REF_H = 1080f
    /** Narrowest virtual width: the seven round buttons (1134 px) plus margins. */
    const val MIN_W = 1240f

    // Status row
    const val AVATAR_D = 91f
    const val AVATAR_PITCH = 108f
    const val AVATAR_X = 135f
    const val AVATAR_Y = 96f
    const val AVATAR_RING = 4f
    const val STATUS_RIGHT = 1825f
    const val STATUS_BASELINE = 113f
    const val CLOCK_SIZE = 46f
    const val AMPM_SIZE = 28f
    const val BATTERY_W = 55f
    const val BATTERY_H = 28f
    const val WIFI_W = 43f
    const val STATUS_GAP = 40f
    const val PAGE_LABEL_Y = 182f

    // Portrait (not on console): share of the spare height above the row/button block, and the big clock
    const val PORTRAIT_BLOCK = 0.62f
    const val BIG_CLOCK_SIZE = 230f
    const val BIG_DATE_SIZE = 52f

    // Software row
    const val TILE = 384f
    const val TILE_PITCH = 404f
    const val TILE_X = 162f
    const val TILE_TOP = 290f
    const val FRAME_OUT = 14f
    const val FRAME_STROKE = 7f
    const val FRAME_RADIUS = 2f
    const val TITLE_X = 208f
    const val TITLE_BASELINE = 258f
    const val TITLE_SIZE = 50f
    const val BADGE_X = 146f
    const val BADGE_TOP = 216f
    const val BADGE_W = 40f
    const val BADGE_H = 46f
    const val ROW_APPS = 12
    const val ALL_D = 256f
    const val ALL_SLOT = 296f
    const val MARQUEE_PX_PER_S = 90f
    const val MARQUEE_GAP = 80f
    const val MARQUEE_HOLD_MS = 1500f

    // Round buttons
    const val BTN_D = 128f
    const val BTN_PITCH = 161.8f
    const val BTN_Y = 818f
    const val BTN_ICON = 64f
    const val RING_R = 78f
    const val RING_STROKE = 8f
    const val BTN_LABEL_BASELINE = 935f
    const val BTN_LABEL_SIZE = 36f
    const val DOT_R = 6f
    const val DOT_DX = 57f
    const val DOT_DY = -56f

    // Hint bar
    const val RULE_Y = 970f
    const val RULE_H = 3f
    const val RULE_INSET = 44f
    const val HINT_Y = 1028f
    const val HINT_CIRCLE = 38f
    const val HINT_SIZE = 40f
    const val HINT_LABEL_GAP = 18f
    const val HINT_GAP = 60f
    const val HINT_RIGHT = 1826f
    const val CONTROLLER_X = 135f
    const val CONTROLLER_W = 62f
    const val LED_Y = 993f
    const val LED = 8f

    // All Software grid
    const val ALL_TITLE_X = 90f
    const val ALL_TITLE_BASELINE = 105f
    const val ALL_TITLE_SIZE = 44f
    const val ALL_RULE_Y = 140f
    const val GRID_TILE = 256f
    const val GRID_PITCH = 276f
    const val GRID_TOP = 220f
    const val GRID_NAME_BASELINE = 190f

    // Option popup
    const val POP_W = 760f
    const val POP_ROW = 92f
    const val POP_TITLE = 110f
    const val POP_LABEL = 36f

    // Motion. Row scroll and popups were tuned by eye; the pulse was measured (see RE_NOTES).
    const val PULSE_MS = 1000f
    const val SCROLL_MS = 180f
    const val PRESS_MS = 110f
    const val BUMP_MS = 220f
    const val BUMP_PX = 14f
    const val MODE_MS = 200f
}
