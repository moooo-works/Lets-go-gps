package com.moooo_works.letsgogps.ui.map

import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.annotation.MainThread
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

internal fun clampOverlayPosition(position: Int, screen: Int, view: Int): Int =
    position.coerceIn(0, (screen - view).coerceAtLeast(0))

@Singleton
class JoystickOverlayManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var owner: OverlayLifecycleOwner? = null
    private var pendingInputAction: (() -> Unit)? = null
    private var inputFocusable = false
    private val prefs: SharedPreferences by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    @MainThread
    fun show(content: @Composable () -> Unit) {
        if (composeView != null) return
        val lifecycleOwner = OverlayLifecycleOwner()
        val view = ComposeView(context)
        val layoutParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            format = PixelFormat.TRANSLUCENT
            flags = BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(KEY_X, 100)
            y = prefs.getInt(KEY_Y, 500)
        }
        lifecycleOwner.create()
        view.setViewTreeLifecycleOwner(lifecycleOwner)
        view.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        view.setViewTreeViewModelStoreOwner(lifecycleOwner)
        params = layoutParams
        owner = lifecycleOwner
        composeView = view
        inputFocusable = false
        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> clampPosition() }
        view.viewTreeObserver.addOnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) {
                val action = pendingInputAction
                pendingInputAction = null
                action?.invoke()
            } else if (inputFocusable) {
                setInputFocusable(false)
            }
        }
        try {
            view.setContent { content() }
            windowManager.addView(view, layoutParams)
        } catch (error: RuntimeException) {
            hide()
            throw error
        }
    }

    @MainThread
    fun setInputFocusable(focusable: Boolean) {
        val view = composeView ?: return
        val currentParams = params ?: return
        if (!focusable) {
            pendingInputAction = null
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
        if (inputFocusable == focusable) return
        inputFocusable = focusable
        currentParams.flags = BASE_FLAGS or if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        try {
            windowManager.updateViewLayout(view, currentParams)
            if (focusable) view.requestFocus()
        } catch (_: RuntimeException) { hide() }
    }

    /** 貼上只由使用者點擊發起，待視窗確實取得焦點後才讀剪貼簿。 */
    @MainThread
    fun runWhenFocused(action: () -> Unit) {
        pendingInputAction = action
        setInputFocusable(true)
        val view = composeView ?: return
        if (view.hasWindowFocus()) {
            val pending = pendingInputAction
            pendingInputAction = null
            pending?.invoke()
        }
    }

    @MainThread
    fun hide() {
        val view = composeView
        val lifecycleOwner = owner
        composeView = null
        params = null
        owner = null
        pendingInputAction = null
        inputFocusable = false
        if (view != null) {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
            view.disposeComposition()
            try { windowManager.removeViewImmediate(view) } catch (_: RuntimeException) { /* 已被系統撤銷。 */ }
        }
        lifecycleOwner?.destroy()
    }

    @MainThread
    fun updatePosition(deltaX: Int, deltaY: Int) {
        val currentParams = params ?: return
        currentParams.x += deltaX
        currentParams.y += deltaY
        clampPosition(forceUpdate = true)
    }

    private fun clampPosition(forceUpdate: Boolean = false) {
        val view = composeView ?: return
        val currentParams = params ?: return
        val metrics = context.resources.displayMetrics
        val oldX = currentParams.x
        val oldY = currentParams.y
        currentParams.x = clampOverlayPosition(currentParams.x, metrics.widthPixels, view.width)
        currentParams.y = clampOverlayPosition(currentParams.y, metrics.heightPixels, view.height)
        if (view.isAttachedToWindow && (forceUpdate || oldX != currentParams.x || oldY != currentParams.y)) {
            try { windowManager.updateViewLayout(view, currentParams) } catch (_: RuntimeException) { hide() }
        }
    }

    @MainThread
    fun snapToEdge() {
        val currentParams = params ?: return
        val view = composeView ?: return
        val metrics = context.resources.displayMetrics
        currentParams.x = if (currentParams.x + view.width / 2 < metrics.widthPixels / 2) 0
            else (metrics.widthPixels - view.width).coerceAtLeast(0)
        clampPosition(forceUpdate = true)
        prefs.edit().putInt(KEY_X, currentParams.x).putInt(KEY_Y, currentParams.y).apply()
    }

    companion object {
        private const val PREFS_NAME = "joystick_overlay_prefs"
        private const val KEY_X = "joystick_x"
        private const val KEY_Y = "joystick_y"
        private const val BASE_FLAGS = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
    }

    private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = store
        fun create() {
            controller.performRestore(null)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        fun destroy() {
            if (registry.currentState == Lifecycle.State.DESTROYED) return
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
        }
    }
}
