package com.moooo_works.letsgogps.ui.map

import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JoystickOverlayManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun show(content: @Composable () -> Unit) {
        if (composeView != null) return

        val savedX = prefs.getInt(KEY_X, 100)
        val savedY = prefs.getInt(KEY_Y, 500)

        val layoutParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }
        this.params = layoutParams

        composeView = ComposeView(context).apply {
            val lifecycleOwner = MyLifecycleOwner()
            lifecycleOwner.performRestore(null)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)

            setContent {
                content()
            }
        }

        windowManager.addView(composeView, layoutParams)
    }

    fun hide() {
        composeView?.let {
            windowManager.removeView(it)
            composeView = null
            params = null
        }
    }

    fun updatePosition(deltaX: Int, deltaY: Int) {
        val currentParams = params ?: return
        val currentView = composeView ?: return

        val dm = context.resources.displayMetrics
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        val viewWidth = currentView.width.takeIf { it > 0 }
            ?: (164 * dm.density).toInt()
        val viewHeight = currentView.height.takeIf { it > 0 }
            ?: (200 * dm.density).toInt()

        currentParams.x = (currentParams.x + deltaX).coerceIn(0, screenWidth - viewWidth)
        currentParams.y = (currentParams.y + deltaY).coerceIn(0, screenHeight - viewHeight)

        windowManager.updateViewLayout(currentView, currentParams)
    }

    fun snapToEdge() {
        val currentParams = params ?: return
        val currentView = composeView ?: return

        val dm = context.resources.displayMetrics
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        val viewWidth = currentView.width.takeIf { it > 0 }
            ?: (164 * dm.density).toInt()
        val viewHeight = currentView.height.takeIf { it > 0 }
            ?: (200 * dm.density).toInt()

        val midScreen = screenWidth / 2
        currentParams.x = if (currentParams.x + viewWidth / 2 < midScreen) 0
                           else screenWidth - viewWidth
        currentParams.y = currentParams.y.coerceIn(0, screenHeight - viewHeight)

        windowManager.updateViewLayout(currentView, currentParams)
        prefs.edit()
            .putInt(KEY_X, currentParams.x)
            .putInt(KEY_Y, currentParams.y)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "joystick_overlay_prefs"
        private const val KEY_X = "joystick_x"
        private const val KEY_Y = "joystick_y"
    }

    private class MyLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = store

        fun handleLifecycleEvent(event: Lifecycle.Event) = lifecycleRegistry.handleLifecycleEvent(event)
        fun performRestore(state: android.os.Bundle?) = savedStateRegistryController.performRestore(state)
    }
}
