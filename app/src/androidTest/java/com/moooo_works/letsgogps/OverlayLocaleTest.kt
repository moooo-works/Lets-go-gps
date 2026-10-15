package com.moooo_works.letsgogps

import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.platform.app.InstrumentationRegistry
import com.moooo_works.letsgogps.ui.map.overlayConfiguration
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class OverlayLocaleTest {
    @Test fun appLanguagesUseLocalizedResourcesWithoutChangingBase() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val base = Configuration(app.resources.configuration)
        val before = Configuration(base)
        val titles = mapOf("zh-Hant" to "懸浮工具", "en" to "Floating tools", "ja" to "フローティングツール", "ko" to "플로팅 도구")
        for ((code, title) in titles) {
            val localized = app.createConfigurationContext(overlayConfiguration(base, code))
            assertEquals(Locale.forLanguageTag(code).language, localized.resources.configuration.locales[0].language)
            assertEquals(title, localized.getString(R.string.floating_title))
        }
        assertEquals(before, base)
        assertEquals(base.locales, overlayConfiguration(base, "").locales)
    }
}
