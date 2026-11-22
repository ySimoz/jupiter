package com.jupiter.app.core.adaptive

import android.app.Activity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

enum class NavigationLayout {
    BOTTOM_BAR,
    NAVIGATION_RAIL,
    PERMANENT_RAIL,
}

data class JupiterAdaptiveInfo(
    val widthSizeClass: WindowWidthSizeClass,
    val heightSizeClass: WindowHeightSizeClass,
    val navigationLayout: NavigationLayout,
    val useSideSheet: Boolean,
    val gridMinColumnDp: Int,
)

fun computeAdaptiveInfo(
    widthSizeClass: WindowWidthSizeClass,
    heightSizeClass: WindowHeightSizeClass,
): JupiterAdaptiveInfo {
    return when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> JupiterAdaptiveInfo(
            widthSizeClass = widthSizeClass,
            heightSizeClass = heightSizeClass,
            navigationLayout = NavigationLayout.BOTTOM_BAR,
            useSideSheet = false,
            gridMinColumnDp = 160,
        )

        WindowWidthSizeClass.Medium -> JupiterAdaptiveInfo(
            widthSizeClass = widthSizeClass,
            heightSizeClass = heightSizeClass,
            navigationLayout = NavigationLayout.NAVIGATION_RAIL,
            useSideSheet = true,
            gridMinColumnDp = 220,
        )

        WindowWidthSizeClass.Expanded -> JupiterAdaptiveInfo(
            widthSizeClass = widthSizeClass,
            heightSizeClass = heightSizeClass,
            navigationLayout = NavigationLayout.PERMANENT_RAIL,
            useSideSheet = true,
            gridMinColumnDp = 240,
        )

        else -> JupiterAdaptiveInfo(
            widthSizeClass = widthSizeClass,
            heightSizeClass = heightSizeClass,
            navigationLayout = NavigationLayout.BOTTOM_BAR,
            useSideSheet = false,
            gridMinColumnDp = 160,
        )
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun rememberJupiterAdaptiveInfo(activity: Activity): JupiterAdaptiveInfo {
    val windowSizeClass = calculateWindowSizeClass(activity)
    return computeAdaptiveInfo(
        widthSizeClass = windowSizeClass.widthSizeClass,
        heightSizeClass = windowSizeClass.heightSizeClass,
    )
}

val LocalJupiterAdaptiveInfo = staticCompositionLocalOf {
    computeAdaptiveInfo(
        widthSizeClass = WindowWidthSizeClass.Compact,
        heightSizeClass = WindowHeightSizeClass.Medium,
    )
}
