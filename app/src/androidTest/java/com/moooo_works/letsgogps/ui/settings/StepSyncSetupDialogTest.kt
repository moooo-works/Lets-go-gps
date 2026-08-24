package com.moooo_works.letsgogps.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.moooo_works.letsgogps.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StepSyncSetupDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun healthConnectGuide_rendersCurrentFlowAndScrollsToSettingsAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val chain = context.getString(R.string.step_setup_chain)
        val targetAppTitle = context.getString(R.string.step_setup_3_title)
        val targetAppGuidance = context.getString(R.string.step_setup_3_body)
        val pikminGuidance = context.getString(R.string.step_setup_pikmin_note)
        val googleHealthTitle = context.getString(R.string.step_setup_4_title)
        val googleHealthGuidance = context.getString(R.string.step_setup_4_body)
        val openHealthConnect = context.getString(R.string.step_setup_action_open_hc_settings)
        var healthConnectSettingsOpened = false

        composeRule.setContent {
            MaterialTheme {
                StepSyncSetupDialog(
                    onDismiss = {},
                    healthConnectReady = true,
                    writePermissionGranted = true,
                    onOpenHealthConnectStore = {},
                    onGrantPermission = {},
                    onOpenHealthConnectSettings = { healthConnectSettingsOpened = true },
                )
            }
        }

        composeRule.onNodeWithText(chain).assertIsDisplayed()
        composeRule.onNodeWithText(targetAppTitle).assertExists()
        composeRule.onNodeWithText(targetAppGuidance).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(pikminGuidance).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(googleHealthTitle).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(googleHealthGuidance).performScrollTo().assertIsDisplayed()
        val retiredFitLabel = "Google" + " Fit"
        composeRule.onAllNodesWithText(retiredFitLabel, substring = true).assertCountEquals(0)

        composeRule.onNodeWithText(openHealthConnect)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle { assertTrue(healthConnectSettingsOpened) }
    }
}
