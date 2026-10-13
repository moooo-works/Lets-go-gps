package com.moooo_works.letsgogps

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.test.platform.app.InstrumentationRegistry
import com.moooo_works.letsgogps.ui.help.HelpScreen
import com.moooo_works.letsgogps.ui.map.FloatingToolsTipDialog
import org.junit.Assert.*
import org.junit.Test

class HelpGuideTest {
    @Test fun tipGuideSearchAndBackWorkInAllFourLanguages() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val activity = instrument.startActivitySync(Intent(context, ComponentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
        val automation = instrument.uiAutomation
        var language = "initial"
        fun nodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = buildList {
            root.refresh(); add(root)
            for (i in 0 until root.childCount) root.getChild(i)?.let { addAll(nodes(it)) }
        }
        fun find(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
            val deadline = SystemClock.uptimeMillis() + 5000
            while (SystemClock.uptimeMillis() < deadline) {
                automation.rootInActiveWindow?.let { root -> nodes(root).firstOrNull(predicate)?.let { return it } }
                SystemClock.sleep(100)
            }
            throw AssertionError("Missing help UI node in $language")
        }
        fun click(label: String) {
            var node: AccessibilityNodeInfo? = find { !it.isEditable && (it.text?.toString() == label || it.contentDescription?.toString() == label) }
            while (node != null && !node.isClickable) node = node.parent
            assertNotNull(node)
            assertTrue(node!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            instrument.waitForIdleSync()
        }
        try {
            listOf("zh-TW", "en", "ja", "ko").forEachIndexed { index, tag ->
                language = tag
                val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) })
                val strings = localized.resources
                val showTip = mutableStateOf(true)
                var dismissed = false
                var exited = false
                instrument.runOnMainSync {
                    activity.setContent {
                        CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides localized.resources.configuration, LocalResources provides localized.resources) {
                            MaterialTheme(colorScheme = if (index % 2 == 0) lightColorScheme() else darkColorScheme()) {
                                if (showTip.value) FloatingToolsTipDialog(
                                    onDismiss = { dismissed = true }, onReadGuide = { showTip.value = false }
                                ) else HelpScreen(initialTopic = "floating", onBack = { exited = true })
                            }
                        }
                    }
                }
                click(strings.getString(R.string.help_read_guide))
                assertTrue(dismissed)
                find { it.text?.toString() == strings.getString(R.string.help_floating_title) }
                click(strings.getString(R.string.help_back))
                val input = find { it.isEditable }
                val search = strings.getString(R.string.help_pro_title)
                assertTrue(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, search)
                }))
                click(search)
                find { it.text?.toString() == strings.getString(R.string.help_pro_body) }
                click(strings.getString(R.string.help_back))
                val field = find { it.isEditable }
                assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "zzzznomatchzzzz")
                }))
                find { it.text?.toString() == strings.getString(R.string.help_empty) }
                click(strings.getString(R.string.map_search_clear))
                click(strings.getString(R.string.help_faq))
                click(strings.getString(R.string.help_faq_location_title))
                click(strings.getString(R.string.help_back))
                click(strings.getString(R.string.help_back))
                instrument.runOnMainSync { assertTrue(exited) }
            }
        } finally {
            instrument.runOnMainSync { activity.finish() }
        }
    }
}
