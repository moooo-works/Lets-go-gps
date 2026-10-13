package com.moooo_works.letsgogps.ui.map

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class JoystickOverlayManagerTest {
    @Test fun `repeated show creates one window and hide destroys its owner`() {
        val windows = mockk<WindowManager>(relaxed = true)
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.WINDOW_SERVICE) windows else super.getSystemService(name)
        }
        val view = slot<ComposeView>()
        every { windows.addView(capture(view), any()) } just Runs
        val manager = JoystickOverlayManager(context)
        manager.show { }
        manager.show { }
        verify(exactly = 1) { windows.addView(any(), any()) }
        val owner = view.captured.findViewTreeLifecycleOwner()!!
        assertEquals(Lifecycle.State.RESUMED, owner.lifecycle.currentState)
        manager.hide()
        manager.hide()
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
        verify(exactly = 1) { windows.removeViewImmediate(view.captured) }
    }

    @Test fun `window add failure cleans ownership and permits a retry`() {
        val windows = mockk<WindowManager>(relaxed = true)
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.WINDOW_SERVICE) windows else super.getSystemService(name)
        }
        val views = mutableListOf<ComposeView>()
        every { windows.addView(any(), any()) } answers {
            views.add(firstArg())
            if (views.size == 1) throw SecurityException("revoked")
        }
        val manager = JoystickOverlayManager(context)
        try { manager.show { }; fail("Expected revoked permission") } catch (_: SecurityException) { }
        assertEquals(Lifecycle.State.DESTROYED, views.first().findViewTreeLifecycleOwner()!!.lifecycle.currentState)
        manager.show { }
        assertEquals(2, views.size)
        manager.hide()
    }
    @Test fun `window update failure notifies dismissal once and destroys owner`() {
        val windows=mockk<WindowManager>(relaxed=true)
        val context=object:ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getSystemService(name:String):Any?=if(name==Context.WINDOW_SERVICE) windows else super.getSystemService(name)
        }
        val view=slot<ComposeView>()
        every { windows.addView(capture(view),any()) } just Runs
        every { windows.updateViewLayout(any(),any()) } throws SecurityException("revoked")
        val manager=JoystickOverlayManager(context)
        var dismissed=0
        manager.setOnDismissedListener { dismissed++ }
        manager.show { }
        manager.setInputFocusable(true)
        manager.hide()
        assertEquals(1,dismissed)
        assertEquals(Lifecycle.State.DESTROYED,view.captured.findViewTreeLifecycleOwner()!!.lifecycle.currentState)
    }

    @Test fun `docked overlay remains on same edge after panel shrinks and grows`() {
        val windows = mockk<WindowManager>(relaxed = true)
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("joystick_overlay_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val context = object : ContextWrapper(app) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.WINDOW_SERVICE) windows else super.getSystemService(name)
        }
        val view = slot<ComposeView>()
        val params = slot<WindowManager.LayoutParams>()
        every { windows.addView(capture(view), capture(params)) } just Runs
        val manager = JoystickOverlayManager(context)
        manager.show { }
        val screen = context.resources.displayMetrics.widthPixels
        view.captured.layout(0, 0, screen / 2, 200)
        assertTrue(params.captured.x == 0 || params.captured.x == screen - screen / 2)
        manager.updatePosition(screen, 0)
        manager.snapToEdge()
        view.captured.layout(0, 0, screen / 8, 100)
        assertEquals(screen - screen / 8, params.captured.x)
        view.captured.layout(0, 0, screen / 4, 100)
        assertEquals(screen - screen / 4, params.captured.x)
        manager.setBottomAligned(true)
        val screenHeight = context.resources.displayMetrics.heightPixels
        assertEquals(screenHeight - 100, params.captured.y)
        view.captured.layout(0, 0, screen / 4, 150)
        assertEquals(screenHeight - 150, params.captured.y)
        manager.updatePosition(0, -100)
        view.captured.layout(0, 0, screen / 4, 100)
        assertEquals(screenHeight - 250, params.captured.y)
        manager.updatePosition(-screen, 0)
        manager.snapToEdge()
        view.captured.layout(0, 0, screen / 8, 100)
        assertEquals(0, params.captured.x)
        manager.updatePosition(screen / 4, 0)
        assertEquals(screen / 4, params.captured.x)
        manager.hide()
    }

}
