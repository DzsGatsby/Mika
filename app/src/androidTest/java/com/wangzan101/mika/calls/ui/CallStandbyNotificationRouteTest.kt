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

    @Test fun blockedStandbyNotificationOpensTheExistingChecklistEachTime() {
        val activity = compose.activity
        val general = CallStandbyService.notificationContentIntent(activity, opensStandbySettings = false)
        val repair = CallStandbyService.notificationContentIntent(activity, opensStandbySettings = true)
        assertNotEquals(general, repair)
        val checklist = compose.onNodeWithTag("call-checklist")

        repair.send()
        compose.waitUntil(5_000) { checklistShown() }
        checklist.assertIsDisplayed()

        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        checklist.assertDoesNotExist()

        repair.send()
        compose.waitUntil(5_000) { checklistShown() }
        checklist.assertIsDisplayed()
    }

    private fun checklistShown() = compose.onAllNodes(hasTestTag("call-checklist")).fetchSemanticsNodes().isNotEmpty()
}
