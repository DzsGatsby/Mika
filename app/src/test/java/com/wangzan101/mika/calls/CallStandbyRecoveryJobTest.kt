package com.wangzan101.mika.calls

import android.app.ApplicationExitInfo.REASON_LOW_MEMORY
import android.app.ApplicationExitInfo.REASON_USER_REQUESTED
import com.wangzan101.mika.settings.FeatureFlagsCapsuleTest.MemoryPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallStandbyRecoveryJobTest {
    @Test fun onlyAUserRequestedExitAfterTheLastOpenCountsAsAStop() {
        assertTrue(CallStandbyOwnerStop.explicitStopSinceOpen(REASON_USER_REQUESTED, 101L, 100L))
        assertFalse(CallStandbyOwnerStop.explicitStopSinceOpen(REASON_USER_REQUESTED, 101L, 102L))
        assertFalse(CallStandbyOwnerStop.explicitStopSinceOpen(REASON_LOW_MEMORY, 101L, 100L))
    }

    @Test fun anExplicitStopOutlivesTheRotatingExitHistoryUntilTheOwnerOpensMika() {
        val prefs = MemoryPrefs()
        val stop = CallStandbyOwnerStop(prefs)
        stop.opened(100L)
        assertFalse(stop.stopped { listOf(REASON_LOW_MEMORY to 150L) })

        assertTrue(stop.stopped { listOf(REASON_USER_REQUESTED to 200L) })
        // Android dropped the record; a fresh instance on the same storage still refuses recovery.
        assertTrue(CallStandbyOwnerStop(prefs).stopped { emptyList() })

        stop.opened(300L)
        assertFalse(stop.stopped { listOf(REASON_USER_REQUESTED to 200L) })
    }

    @Test fun anUnreadableExitHistoryNeverRevivesStandby() {
        val stop = CallStandbyOwnerStop(MemoryPrefs())
        stop.opened(100L)
        assertTrue(stop.stopped { null })
    }

    @Test fun beforeTheFirstRecordedOpenOnlyTheNewestExitIsAsked() {
        val asked = mutableListOf<Boolean>()
        val stop = CallStandbyOwnerStop(MemoryPrefs())
        assertFalse(stop.stopped { newestOnly -> asked += newestOnly; listOf(REASON_LOW_MEMORY to 5L) })
        stop.opened(10L)
        assertFalse(stop.stopped { newestOnly -> asked += newestOnly; emptyList() })
        assertEquals(listOf(true, false), asked)
    }
}
