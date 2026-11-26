package com.wangzan101.mika.calls.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wangzan101.mika.calls.CallStandbyService
import com.wangzan101.mika.ui.MainActivity
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the notification's local navigation without starting the phone service or making calls. */
@RunWith(AndroidJUnit4::class)
class CallStandbyNotificationRouteTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun blockedStandbyNotificationOpensTheExistingChecklistOncePerTap() {
        val activity = compose.activity
        val general = CallStandbyService.notificationContentIntent(activity, opensStandbySettings = false)
        val repair = CallStandbyService.notificationContentIntent(activity, opensStandbySettings = true)
        assertNotEquals(general, repair)
        val checklist = compose.onNodeWithTag(CHECKLIST)

        repair.send()
        waitFor(CHECKLIST)
        checklist.assertIsDisplayed()
        back()
        checklist.assertDoesNotExist()

        // Leaving and re-entering the phone page must not replay the earlier tap.
        back()
        compose.onNodeWithTag(WORKSPACE).assertDoesNotExist()
        general.send()
        waitFor(WORKSPACE)
        checklist.assertDoesNotExist()

        repair.send()
        waitFor(CHECKLIST)
        checklist.assertIsDisplayed()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun waitFor(tag: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    private companion object {
        const val CHECKLIST = "call-checklist"
        const val WORKSPACE = "call-settings"
    }
}
