package net.trequad.quadtv.core.ui

import android.graphics.Color

/**
 * QuadTV design tokens (docs/DESIGN_SYSTEM.md §1). All screens should pull
 * colors/type/spacing from here instead of inlining Color.rgb() values.
 */
object QuadTvTheme {
    // Colors
    val BACKGROUND = Color.rgb(7, 18, 32)
    val SURFACE = Color.rgb(10, 24, 38)
    val SURFACE_RAISED = Color.rgb(12, 30, 50)
    val FOCUS = Color.rgb(44, 95, 124)
    val FOCUS_RING = Color.rgb(126, 203, 255)
    val ACCENT = Color.rgb(126, 203, 255)
    val ACCENT_WARM = Color.rgb(230, 126, 34)
    val TEXT_PRIMARY = Color.WHITE
    val TEXT_SECONDARY = Color.rgb(184, 199, 214)
    val TEXT_MUTED = Color.rgb(122, 140, 160)
    val DANGER = Color.rgb(169, 68, 66)
    val LINE = Color.rgb(25, 52, 72)

    // Type scale (sp)
    const val TYPE_DISPLAY = 30f
    const val TYPE_TITLE = 22f
    const val TYPE_SECTION = 19f
    const val TYPE_BODY = 17f
    const val TYPE_CAPTION = 14f
    const val TYPE_MICRO = 12f

    // Spacing (dp)
    const val SPACE_XS = 4
    const val SPACE_S = 8
    const val SPACE_M = 14
    const val SPACE_L = 22
    const val SPACE_XL = 32

    // Cards (dp)
    const val POSTER_CARD_WIDTH_TV = 128
    const val POSTER_CARD_WIDTH_PHONE = 110
    const val LOGO_TILE_WIDTH = 150
    const val MIN_FOCUSABLE_SIZE = 48
}
