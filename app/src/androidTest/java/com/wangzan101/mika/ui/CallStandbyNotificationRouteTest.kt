package com.wangzan101.mika.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wangzan101.mika.R
import com.wangzan101.mika.calls.CallStandbyService
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the notification's local navigation without starting the phone service or making calls. */
@RunWith(AndroidJUnit4::class)
class CallStandbyNotificationRouteTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun blockedStandbyNotificationReopensTheExpandedSettings() {
        val activity = compose.activity
        val general = CallStandbyService.notificationContentIntent(activity, opensSettings = false)
        val repair = CallStandbyService.notificationContentIntent(activity, opensSettings = true)
        assertNotEquals(general, repair)
        val background = compose.onNodeWithTag("call-settings-background")
        val details = compose.onNodeWithText(activity.getString(R.string.calls_background_lock_screen_note))
        repair.send()
        background.performScrollTo().assertIsDisplayed()
        details.performScrollTo().assertIsDisplayed()
        background.performScrollTo().performClick()
        details.assertDoesNotExist()
        repair.send()
        details.performScrollTo().assertIsDisplayed()
    }
}
