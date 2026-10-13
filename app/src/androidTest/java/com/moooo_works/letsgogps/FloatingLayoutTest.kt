package com.moooo_works.letsgogps

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.runtime.mutableStateOf
import androidx.test.platform.app.InstrumentationRegistry
import com.moooo_works.letsgogps.ui.map.*
import com.google.android.gms.maps.model.LatLng
import com.moooo_works.letsgogps.domain.repository.GeocodedLocation
import com.moooo_works.letsgogps.domain.LoopMode
import org.junit.Assert.*
import org.junit.Test

class FloatingLayoutTest {
    @Test fun edgeEntryExposesAllTabsAndSeparatesCollapseFromClose() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val activity = instrument.startActivitySync(Intent(context, ComponentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val locale = context.getSharedPreferences("mockgps_prefs", Context.MODE_PRIVATE).getString("language_pref", "") ?: ""
        val strings = context.createConfigurationContext(overlayConfiguration(context.resources.configuration, locale))
        val prefs = context.getSharedPreferences("joystick_overlay_prefs", Context.MODE_PRIVATE)
        val original = listOf("joystick_x", "joystick_y").associateWith { if (prefs.contains(it)) prefs.getInt(it, 0) else null }
        val automation = instrument.uiAutomation
        val originalServiceFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        val sample = GeocodedLocation("中央公園（示意）", "示意中山路", LatLng(25.03401, 121.56447))
        val state = mutableStateOf(FloatingCompanionState(searchResults = listOf(sample)))
        val mapState = mutableStateOf(MapUiState())
        var copied: LatLng? = null
        var selections = 0
        var closed = false
        lateinit var manager: JoystickOverlayManager
        var joystickClosed = false
        val actions = FloatingCompanionActions(
            onExpandedChange = { state.value = state.value.copy(expanded = it) },
            onTabChange = { state.value = state.value.copy(tab = it) },
            onQueryChange = {}, onSearch = {}, onPaste = {}, onSelect = { selections++ },
            onFolderChange = {}, onFavoritesChange = {}, onLoadRoute = {},
            onLocate = {}, onQueue = {}, onApplyQueue = {}, onSave = {},
            onConfirmReplacement = {}, onCancelReplacement = {}, onPlayPause = {},
            onStop = {}, onSpeedChange = {}, onLoopChange = {
                mapState.value = mapState.value.copy(loopMode = when (mapState.value.loopMode) {
                    LoopMode.NONE -> LoopMode.LOOP
                    LoopMode.LOOP -> LoopMode.BOUNCE
                    LoopMode.BOUNCE -> LoopMode.NONE
                })
            }, onExplore = {},
            onCopyCurrent = {}, onCopySelected = {}, onClose = { closed = true; manager.hide() },
            onOpenApp = {}, onInputFocus = { manager.setInputFocusable(it) }, onWindowDrag = { _, _ -> }, onWindowDragEnd = {}, onCopyLocation = { copied = it }
        )
        fun nodes(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = buildList {
            node.refresh()
            add(node)
            for (index in 0 until node.childCount) node.getChild(index)?.let { addAll(nodes(it)) }
        }
        fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            var current: AccessibilityNodeInfo? = node
            repeat(6) { if (current?.isClickable == true) return current; current = current?.parent }
            return null
        }
        fun find(description: String): AccessibilityNodeInfo {
            val deadline = SystemClock.uptimeMillis() + 5000
            while (SystemClock.uptimeMillis() < deadline) {
                automation.windows.mapNotNull { it.root }.filter { it.packageName?.toString() == context.packageName }.forEach { root ->
                    nodes(root).filter { it.contentDescription?.toString() == description }.mapNotNull(::clickableAncestor).firstOrNull()?.let { return it }
                }
                SystemClock.sleep(100)
            }
            throw AssertionError("Missing clickable: $description")
        }
        fun click(description: String) { assertTrue(find(description).performAction(AccessibilityNodeInfo.ACTION_CLICK)) }
        fun assertSmallWindow(description: String, maximum: Int) {
            val deadline = SystemClock.uptimeMillis() + 5000
            while (SystemClock.uptimeMillis() < deadline) {
                for (window in automation.windows) {
                    val root = window.root ?: continue
                    if (root.packageName?.toString() != context.packageName ||
                        nodes(root).none { it.contentDescription?.toString() == description }) continue
                    val rect = Rect().also(window::getBoundsInScreen)
                    if (rect.width() in 1..maximum && rect.height() in 1..maximum) return
                }
                SystemClock.sleep(100)
            }
            fail("Collapsed WindowManager bounds must be small: $description")
        }
        try {
            instrument.runOnMainSync {
                manager = JoystickOverlayManager(context)
                manager.show { FloatingCompanionView(mapState.value, state.value, actions) {} }
                manager.setInputFocusable(true)
            }
            val title = strings.getString(R.string.floating_title)
            val entry = find(title)
            val bounds = Rect().also(entry::getBoundsInScreen)
            val maxEntry = (64 * context.resources.displayMetrics.density).toInt()
            assertTrue(bounds.width() in 1..maxEntry && bounds.height() in 1..maxEntry)
            assertNotEquals(title, entry.text?.toString())
            assertSmallWindow(title, maxEntry)
            click(title)
            for (label in listOf(R.string.floating_search, R.string.floating_saved, R.string.nav_routes, R.string.floating_control, R.string.floating_map)) {
                val tab = find(strings.getString(label))
                val rect = Rect().also(tab::getBoundsInScreen)
                assertFalse(rect.isEmpty)
            }
            val fieldDeadline = SystemClock.uptimeMillis() + 5000
            var field: AccessibilityNodeInfo? = null
            while (field == null && SystemClock.uptimeMillis() < fieldDeadline) {
                field = automation.windows.mapNotNull { it.root }
                    .filter { it.packageName?.toString() == context.packageName }
                    .flatMap(::nodes).firstOrNull { it.className?.toString() == "android.widget.EditText" }
                if (field == null) SystemClock.sleep(100)
            }
            assertNotNull(field)
            assertTrue(field!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            val keyboardDeadline = SystemClock.uptimeMillis() + 5000
            var keyboard: AccessibilityWindowInfo? = null
            while (keyboard == null && SystemClock.uptimeMillis() < keyboardDeadline) {
                keyboard = automation.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                if (keyboard == null) SystemClock.sleep(100)
            }
            assertNotNull("Search must open the keyboard", keyboard)
            field!!.refresh()
            val fieldBounds = Rect().also(field!!::getBoundsInScreen)
            val keyboardBounds = Rect().also(keyboard!!::getBoundsInScreen)
            assertTrue("Search field must remain above keyboard", fieldBounds.bottom <= keyboardBounds.top)
            click(strings.getString(R.string.floating_collapse))
            val collapsedBounds = Rect().also(find(title)::getBoundsInScreen)
            assertTrue("No transparent full-height window after collapse", collapsedBounds.height() <= maxEntry)
            assertSmallWindow(title, maxEntry)
            click(title)
            click(strings.getString(R.string.floating_copy_selected))
            instrument.runOnMainSync { assertEquals(sample.latLng, copied); assertEquals(0, selections); assertEquals(CompanionTab.SEARCH, state.value.tab) }
            click(strings.getString(R.string.nav_routes))
            instrument.runOnMainSync { mapState.value = mapState.value.copy(speedKmh = 12.4) }
            val speedLabel = strings.getString(R.string.floating_route_speed, 12.4)
            val speedDeadline = SystemClock.uptimeMillis() + 5000
            var speedVisible = false
            while (!speedVisible && SystemClock.uptimeMillis() < speedDeadline) {
                speedVisible = automation.windows.mapNotNull { it.root }
                    .filter { it.packageName?.toString() == context.packageName }
                    .flatMap(::nodes).any { it.text?.toString() == speedLabel && it.isVisibleToUser }
                if (!speedVisible) SystemClock.sleep(100)
            }
            assertFalse(speedLabel.contains("12.4"))
            assertTrue("Fractional speed must be displayed as an integer: $speedLabel", speedVisible)
            instrument.runOnMainSync { assertEquals(12.4, mapState.value.speedKmh, 0.0) }
            val switchMode = strings.getString(R.string.route_loop_cd)
            for ((index, label) in listOf(R.string.route_loop_none, R.string.route_loop_loop, R.string.route_loop_bounce, R.string.route_loop_none).withIndex()) {
                val deadline = SystemClock.uptimeMillis() + 5000
                var visible = false
                while (!visible && SystemClock.uptimeMillis() < deadline) {
                    visible = automation.windows.mapNotNull { it.root }
                        .filter { it.packageName?.toString() == context.packageName }
                        .flatMap(::nodes).any { it.text?.toString() == strings.getString(label) && it.isVisibleToUser }
                    if (!visible) SystemClock.sleep(100)
                }
                assertTrue("Mode label must be visible: ${strings.getString(label)}; state=${mapState.value.loopMode}", visible)
                if (index < 3) {
                    click(switchMode)
                    instrument.waitForIdleSync()
                    SystemClock.sleep(300)
                }
            }
            click(strings.getString(R.string.floating_collapse))
            find(title)
            instrument.runOnMainSync { assertFalse(state.value.expanded); assertFalse(closed) }
            click(title)
            click(strings.getString(R.string.joystick_menu))
            val deadline = SystemClock.uptimeMillis() + 5000
            var close: AccessibilityNodeInfo? = null
            while (close == null && SystemClock.uptimeMillis() < deadline) {
                automation.windows.mapNotNull { it.root }.filter { it.packageName?.toString() == context.packageName }.forEach { root ->
                    close = nodes(root).filter { it.text?.toString() == strings.getString(R.string.floating_close) }.mapNotNull(::clickableAncestor).firstOrNull()
                }
                if (close == null) SystemClock.sleep(100)
            }
            assertNotNull(close)
            assertTrue(close!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            instrument.waitForIdleSync()
            instrument.runOnMainSync {
                assertTrue(closed)
                manager.show {
                    StandaloneJoystickOverlay(
                        onClose = { joystickClosed = true; manager.hide() },
                        onCollapse = { manager.snapToEdge() },
                        onWindowDrag = { dx, dy -> manager.updatePosition(dx, dy) },
                        onWindowDragEnd = { manager.snapToEdge() }
                    ) {
                        JoystickOverlayView(TransportMode.WALKING, 12.0, { _, _ -> }, { _, _ -> }, {}, {}, {})
                    }
                }
            }
            click(strings.getString(R.string.joystick_collapse))
            find(strings.getString(R.string.joystick_title))
            assertSmallWindow(strings.getString(R.string.joystick_title), maxEntry)
            click(strings.getString(R.string.joystick_title))
            click(strings.getString(R.string.joystick_close))
            instrument.runOnMainSync { assertTrue(joystickClosed) }
        } finally {
            instrument.runOnMainSync { manager.hide(); activity.finish() }
            val editor = prefs.edit()
            original.forEach { (key, value) -> if (value == null) editor.remove(key) else editor.putInt(key, value) }
            editor.commit()
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalServiceFlags }
        }
    }
}
