package com.wangzan101.mika.core.cap.phone.sms

import com.wangzan101.mika.core.uistate.*

/**
 * One-way privacy curtain for this run. No SMS/code is held here. Starting a private login
 * hides subsequent phone observations, images, OCR and notifications from the model/records.
 * The private host operation retains a separate, access-checked port. A new run is needed
 * to resume phone automation; arbitrary shell/MCP are not a sandbox security boundary.
 */
class SmsPrivateScreen(private val inner: ScreenPort) : ScreenPort by inner {
    @Volatile var privateLogin = false
        private set
    fun begin() { privateLogin = true }

    private fun hidden() = ScreenSnapshot("sms-private", null, emptyList(), 1, 1,
        protected = true, captureError = "sms_login_private")
    override suspend fun snapshot() = if (privateLogin) hidden() else inner.snapshot()
    override suspend fun peek() = if (privateLogin) hidden() else inner.peek()
    override suspend fun screenshotPng() = if (privateLogin) null else inner.screenshotPng()
    override suspend fun readVisibleText() = if (privateLogin) null else inner.readVisibleText()
    override suspend fun frameGrid() = if (privateLogin) null else inner.frameGrid()
    override suspend fun checkScreen() = if (privateLogin) null else inner.checkScreen()
    override suspend fun isProtected() = privateLogin || inner.isProtected()
    override suspend fun nextEvent(kind: String, packageName: String?, timeoutMs: Long) =
        if (privateLogin) null else inner.nextEvent(kind, packageName, timeoutMs)
    override suspend fun watchEvents(kind: String, packageName: String?, durationMs: Long, maxEvents: Int) =
        if (privateLogin) emptyList() else inner.watchEvents(kind, packageName, durationMs, maxEvents)
    override suspend fun systemCommand(command: String) = if (privateLogin) null else inner.systemCommand(command)
    // A stale pre-login ref could return the code as relocatedTo/hint. Refuse writes once hidden.
    private val blocked get() = ActionResult.Failed("sms_login_private", "Hand the remaining login step to the user and finish this run.")
    override suspend fun tapRef(ref: String, snapshotId: String) = if (privateLogin) blocked else inner.tapRef(ref, snapshotId)
    override suspend fun tapXY(x: Int, y: Int) = if (privateLogin) blocked else inner.tapXY(x, y)
    override suspend fun doubleTapRef(ref: String, snapshotId: String) = if (privateLogin) blocked else inner.doubleTapRef(ref, snapshotId)
    override suspend fun doubleTapXY(x: Int, y: Int) = if (privateLogin) blocked else inner.doubleTapXY(x, y)
    override suspend fun typeText(ref: String?, snapshotId: String?, text: String) = if (privateLogin) blocked else inner.typeText(ref, snapshotId, text)
    override suspend fun clearText(ref: String?, snapshotId: String?) = if (privateLogin) blocked else inner.clearText(ref, snapshotId)
    override suspend fun typeAt(x: Int, y: Int, text: String) = if (privateLogin) blocked else inner.typeAt(x, y, text)
    override suspend fun clearAt(x: Int, y: Int) = if (privateLogin) blocked else inner.clearAt(x, y)
    override suspend fun back() = if (privateLogin) blocked else inner.back()
    override suspend fun pressKey(keyCode: Int) = if (privateLogin) blocked else inner.pressKey(keyCode)
    override suspend fun scroll(ref: String?, snapshotId: String?, down: Boolean) = if (privateLogin) blocked else inner.scroll(ref, snapshotId, down)
    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) = if (privateLogin) blocked else inner.swipe(x1, y1, x2, y2, durationMs)
    override suspend fun gesture(paths: List<List<Pair<Int, Int>>>, durationMs: Long) = if (privateLogin) blocked else inner.gesture(paths, durationMs)
    override suspend fun idleScroll(fraction: Double) = if (privateLogin) false else inner.idleScroll(fraction)
    override suspend fun waitFor(condition: String, value: String?, timeoutMs: Long) = if (privateLogin) false else inner.waitFor(condition, value, timeoutMs)
    override suspend fun setWheel(ref: String, snapshotId: String, value: String) = if (privateLogin)
        WheelOutcome(false, null, null, 0, "none", error = "sms_login_private") else inner.setWheel(ref, snapshotId, value)
}
