package com.sierra.camblock.utils

import android.app.Activity
import android.graphics.Color
import android.os.Build
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Applies dark system bars appearance (dark background with light/white icons).
 * Used for screens with dark backgrounds so status bar icons & text remain visible.
 */
fun Activity.applyDarkSystemBars(@ColorRes colorRes: Int? = null) {
    val barColor: Int = if (colorRes != null) {
        ContextCompat.getColor(this, colorRes)
    } else {
        // Transparent keeps gradient / custom backgrounds visible through the bars.
        Color.TRANSPARENT
    }
    applyDarkSystemBarsColor(barColor)
}

fun Activity.applyDarkSystemBarsColor(@ColorInt barColor: Int) {
    window.statusBarColor = barColor
    window.navigationBarColor = barColor
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }
    WindowInsetsControllerCompat(window, window.decorView).apply {
        isAppearanceLightStatusBars = false
        isAppearanceLightNavigationBars = false
    }
}
