package com.wangzan101.mika.calls

import android.app.ApplicationExitInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallStandbyRecoveryJobTest {
    @Test fun scheduledStandbyRespectsOwnerStopUntilTheNextOpen() {
        assertTrue(CallStandbyRecoveryJob.explicitStopSinceOpen(ApplicationExitInfo.REASON_USER_REQUESTED, 101L, 100L))
        assertFalse(CallStandbyRecoveryJob.explicitStopSinceOpen(ApplicationExitInfo.REASON_USER_REQUESTED, 101L, 102L))
        assertFalse(CallStandbyRecoveryJob.explicitStopSinceOpen(ApplicationExitInfo.REASON_LOW_MEMORY, 101L, 100L))
    }
}
