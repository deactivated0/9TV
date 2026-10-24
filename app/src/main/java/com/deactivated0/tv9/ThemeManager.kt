package com.deactivated0.tv9

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable

object ThemeManager {
    const val COLOR_WHITE: Int = Color.WHITE
    const val COLOR_BLACK: Int = Color.BLACK

    // Dark Mode (AMOLED Black): Button tiles are crisp white with black text
    private const val DARK_SURFACE: Int = 0xFF000000.toInt()
    private const val DARK_TITLE: Int = 0xFFEEEEEE.toInt()
    private const val DARK_DESCRIPTION: Int = 0xB3EEEEEE.toInt()
    private const val DARK_BUTTON_BG: Int = 0xFFFFFFFF.toInt()
    private const val DARK_BUTTON_TEXT: Int = 0xFF000000.toInt()

    // Light Mode (Pure White): Button tiles are black with white text
    private const val LIGHT_SURFACE: Int = 0xFFFFFFFF.toInt()
    private const val LIGHT_TITLE: Int = 0xFF111111.toInt()
    private const val LIGHT_DESCRIPTION: Int = 0xFF555555.toInt()
    private const val LIGHT_BUTTON_BG: Int = 0xFF111111.toInt()
    private const val LIGHT_BUTTON_TEXT: Int = 0xFFFFFFFF.toInt()

    val isDarkMode: Boolean
        get() = SP.darkMode

    val surfaceColor: Int
        get() = if (SP.darkMode) DARK_SURFACE else LIGHT_SURFACE

    val titleColor: Int
        get() = if (SP.darkMode) DARK_TITLE else LIGHT_TITLE

    val descriptionColor: Int
        get() = if (SP.darkMode) DARK_DESCRIPTION else LIGHT_DESCRIPTION

    val buttonNormalBgColor: Int
        get() = if (SP.darkMode) DARK_BUTTON_BG else LIGHT_BUTTON_BG

    val buttonNormalTextColor: Int
        get() = if (SP.darkMode) DARK_BUTTON_TEXT else LIGHT_BUTTON_TEXT

    // White accent in dark mode, Black accent in light mode
    val focusColor: Int
        get() = if (SP.darkMode) COLOR_WHITE else COLOR_BLACK

    val focusTextColor: Int
        get() = if (SP.darkMode) COLOR_WHITE else COLOR_BLACK

    val focusItemTextColor: Int
        get() = if (SP.darkMode) COLOR_BLACK else COLOR_WHITE

    private val darkSurfaceDrawable = ColorDrawable(DARK_SURFACE)
    private val lightSurfaceDrawable = ColorDrawable(LIGHT_SURFACE)

    fun createButtonDrawable(
        bgColor: Int,
        cornerRadiusDp: Int = 6,
        strokeColor: Int = 0,
        strokeWidthDp: Int = 0
    ): GradientDrawable {
        val radius = Utils.dpToPx(cornerRadiusDp).toFloat()
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(bgColor)
            cornerRadius = radius
            if (strokeWidthDp > 0 && strokeColor != 0) {
                setStroke(Utils.dpToPx(strokeWidthDp), strokeColor)
            }
        }
    }

    val surfaceDrawable: Drawable
        get() = if (SP.darkMode) darkSurfaceDrawable else lightSurfaceDrawable

    // Focused button tile: In Dark Mode has a crisp 3dp pure white stroke (white accent).
    // In Light Mode has a crisp 3dp black stroke (black accent).
    val focusDrawable: Drawable
        get() = if (SP.darkMode) {
            createButtonDrawable(DARK_SURFACE, 6, COLOR_WHITE, 3)
        } else {
            createButtonDrawable(LIGHT_SURFACE, 6, COLOR_BLACK, 3)
        }

    // High contrast solid focus tile for list items & dialog buttons
    val focusItemDrawable: Drawable
        get() = if (SP.darkMode) {
            createButtonDrawable(COLOR_WHITE, 6)
        } else {
            createButtonDrawable(COLOR_BLACK, 6)
        }

    val buttonNormalDrawable: Drawable
        get() = createButtonDrawable(buttonNormalBgColor, 6)

    fun createInfoRightDrawable(): GradientDrawable {
        val radius = Utils.dpToPx(4).toFloat()
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(surfaceColor)
            cornerRadii = floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)
        }
    }
}
