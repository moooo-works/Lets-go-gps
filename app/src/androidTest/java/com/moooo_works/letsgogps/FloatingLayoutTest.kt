package com.moooo_works.letsgogps

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.mutableStateOf
import androidx.test.platform.app.InstrumentationRegistry
import com.moooo_works.letsgogps.ui.map.*
import com.google.android.gms.maps.model.LatLng
import com.moooo_works.letsgogps.domain.repository.GeocodedLocation
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
        var copied: LatLng? = null
        var selections = 0
        var closed = false
        lateinit var manager: JoystickOverlayManager
        val actions = FloatingCompanionActions(
            onExpandedChange = { state.value = state.value.copy(expanded = it) },
            onTabChange = { state.value = state.value.copy(tab = it) },
            onQueryChange = {}, onSearch = {}, onPaste = {}, onSelect = { selections++ },
            onFolderChange = {}, onFavoritesChange = {}, onLoadRoute = {},
            onLocate = {}, onQueue = {}, onApplyQueue = {}, onSave = {},
            onConfirmReplacement = {}, onCancelReplacement = {}, onPlayPause = {},
            onStop = {}, onSpeedChange = {}, onLoopChange = {}, onExplore = {},
            onCopyCurrent = {}, onCopySelected = {}, onClose = { closed = true; manager.hide() },
            onOpenApp = {}, onInputFocus = {}, onWindowDrag = { _, _ -> }, onWindowDragEnd = {}, onCopyLocation = { copied = it }
        )
        fun nodes(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = buildList {
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
        try {
            instrument.runOnMainSync {
                manager = JoystickOverlayManager(context)
                manager.show { FloatingCompanionView(MapUiState(), state.value, actions) {} }
                manager.setInputFocusable(true)
            }
            val title = strings.getString(R.string.floating_title)
            val entry = find(title)
            val bounds = Rect().also(entry::getBoundsInScreen)
            val maxEntry = (64 * context.resources.displayMetrics.density).toInt()
            assertTrue(bounds.width() in 1..maxEntry && bounds.height() in 1..maxEntry)
            assertNotEquals(title, entry.text?.toString())
            click(title)
            for (label in listOf(R.string.floating_search, R.string.floating_saved, R.string.nav_routes, R.string.floating_control, R.string.floating_map)) {
                val tab = find(strings.getString(label))
                val rect = Rect().also(tab::getBoundsInScreen)
                assertFalse(rect.isEmpty)
            }
            click(strings.getString(R.string.floating_copy_selected))
            instrument.runOnMainSync { assertEquals(sample.latLng, copied); assertEquals(0, selections); assertEquals(CompanionTab.SEARCH, state.value.tab) }
            click(strings.getString(R.string.nav_routes))
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
            instrument.runOnMainSync { assertTrue(closed) }
        } finally {
            instrument.runOnMainSync { manager.hide(); activity.finish() }
            val editor = prefs.edit()
            original.forEach { (key, value) -> if (value == null) editor.remove(key) else editor.putInt(key, value) }
            editor.commit()
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalServiceFlags }
        }
    }
}
