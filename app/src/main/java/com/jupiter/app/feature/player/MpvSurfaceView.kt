package com.jupiter.app.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
fun MpvSurfaceView(
    onSurfaceAttached: (Surface) -> Unit,
    onSurfaceSizeChanged: (Int, Int) -> Unit,
    onSurfaceDetached: () -> Unit,
    modifier: Modifier = Modifier,
    onSurfaceViewChanged: (SurfaceView?) -> Unit = {},
) {
    val currentOnAttached by rememberUpdatedState(onSurfaceAttached)
    val currentOnSizeChanged by rememberUpdatedState(onSurfaceSizeChanged)
    val currentOnDetached by rememberUpdatedState(onSurfaceDetached)
    val currentOnSurfaceViewChanged by rememberUpdatedState(onSurfaceViewChanged)

    val context = LocalContext.current
    val surfaceView = remember(context) { SurfaceView(context) }

    DisposableEffect(surfaceView) {
        currentOnSurfaceViewChanged(surfaceView)
        var isAttached = false
        val callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                isAttached = true
                currentOnAttached(holder.surface)
                if (surfaceView.width > 0 && surfaceView.height > 0) {
                    currentOnSizeChanged(surfaceView.width, surfaceView.height)
                }
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int,
            ) {
                if (width > 0 && height > 0) {
                    currentOnSizeChanged(width, height)
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                if (isAttached) {
                    isAttached = false
                    currentOnDetached()
                }
            }
        }

        surfaceView.holder.addCallback(callback)
        if (surfaceView.holder.surface.isValid) {
            isAttached = true
            currentOnAttached(surfaceView.holder.surface)
            if (surfaceView.width > 0 && surfaceView.height > 0) {
                currentOnSizeChanged(surfaceView.width, surfaceView.height)
            }
        }

        onDispose {
            currentOnSurfaceViewChanged(null)
            surfaceView.holder.removeCallback(callback)
            if (isAttached) {
                isAttached = false
                currentOnDetached()
            }
        }
    }

    AndroidView(
        factory = { surfaceView },
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
fun PlayerImmersiveBarsEffect(isHudVisible: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val window = remember(context) { context.findActivity()?.window }

    DisposableEffect(window, view) {
        onDispose {
            if (window != null) {
                val controller = WindowInsetsControllerCompat(window, view)
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(window, view, isHudVisible) {
        if (window != null) {
            val controller = WindowInsetsControllerCompat(window, view)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isHudVisible) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

@Composable
fun PlayerOrientationEffect(orientationLock: OrientationLock) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    DisposableEffect(activity) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(activity, orientationLock) {
        if (activity != null) {
            activity.requestedOrientation = when (orientationLock) {
                OrientationLock.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                OrientationLock.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                OrientationLock.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
