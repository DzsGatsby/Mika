package com.wangzan101.mika.settings

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class SmsLoginSettingsTest {
    @get:Rule val tmp = TemporaryFolder()
    @Test fun defaultsOffAndCorruptionFailsClosed() {
        val file = File(tmp.root, "sms")
        assertFalse(SmsLoginSettings(file).state.value.enabled)
        file.writeText("true or broken data")
        assertFalse(SmsLoginSettings(file).state.value.enabled)
    }
    @Test fun onlyOptInBitPersistsAndOldAttemptsCannotResumeAfterReenable() {
        val file = File(tmp.root, "sms")
        val store = SmsLoginSettings(file)
        store.setEnabled(true)
        val revision = store.state.value.revision
        assertTrue(SmsLoginSettings(file).state.value.enabled)
        assertTrue(store.dispatch(revision) { true })
        store.setEnabled(false)
        assertFalse(SmsLoginSettings(file).state.value.enabled)
        assertFalse(store.dispatch(revision) { error("disabled must not dispatch") })
        store.setEnabled(true)
        assertFalse(store.dispatch(revision) { error("old consent must not revive") })
        assertEquals("enabled-v1", file.readText())
    }
    @Test fun failedSaveStillRevokesInMemory() {
        val file = File(tmp.root, "sms")
        val store = SmsLoginSettings(file)
        store.setEnabled(true)
        val revision = store.state.value.revision
        File(file.path + ".tmp").mkdir()
        runCatching { store.setEnabled(false) }
        assertFalse(store.state.value.enabled)
        assertFalse(store.dispatch(revision) { error("revoked") })
    }
    @Test fun disablingReturnsOnlyAfterAnAlreadyDispatchedOperationAndBlocksAllLaterOnes() {
        val store = SmsLoginSettings(File(tmp.root, "sms"))
        store.setEnabled(true)
        val revision = store.state.value.revision
        val dispatched = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val worker = thread {
            store.dispatch(revision) { dispatched.countDown(); release.await(5, TimeUnit.SECONDS); true }
        }
        assertTrue(dispatched.await(5, TimeUnit.SECONDS))
        val disabling = thread { store.setEnabled(false); finished.countDown() }
        assertFalse(finished.await(50, TimeUnit.MILLISECONDS))
        release.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        worker.join(); disabling.join()
        assertFalse(store.dispatch(revision) { error("must not dispatch after off returned") })
    }
}
