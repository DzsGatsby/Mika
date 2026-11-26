package com.wangzan101.mika.calls

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

internal object CallStandbyPolicy {
    /** A temporary lease belongs to work, not to Activity visibility or the incoming opt-in. */
    fun foregroundOutgoing(userUnlocked: Boolean, enabled: Boolean, lockedPending: Boolean, workDirection: CallDirection?): Boolean =
        userUnlocked && enabled && (lockedPending || workDirection == CallDirection.OUTGOING)

    /** Process/system restart may restore incoming standby only; an outgoing request is never replayed. */
    fun acceptsStart(incomingAuthorized: Boolean, explicitRequest: Boolean, outgoingWork: Boolean): Boolean =
        incomingAuthorized || (explicitRequest && outgoingWork)

    fun optedIn(config: CallConfig, userUnlocked: Boolean): Boolean =
        userUnlocked && config.enabled && config.autoAnswer && config.audioVerified && config.credentialsConfigured

    fun interactiveForeground(visible: Boolean, interactive: Boolean, keyguardShowing: Boolean): Boolean =
        visible && interactive && !keyguardShowing

    fun allowsAutomaticAnswer(userUnlocked: Boolean, interactiveForeground: Boolean, serviceRunning: Boolean, notificationsAllowed: Boolean): Boolean =
        userUnlocked && (interactiveForeground || serviceRunning && notificationsAllowed)

    /**
     * The periodic recovery check stays scheduled while the owner's grant stands (it survives reboot
     * as a persisted job) and they have not stopped Mika. Android 10 keeps the plain sticky restart.
     */
    fun keepsRecoveryScheduled(sdk: Int, authorized: Boolean, ownerStopped: Boolean): Boolean =
        sdk >= 30 && authorized && !ownerStopped

    /**
     * A periodic check only re-requests the incoming standby the owner already granted, and only when
     * Android lets a job start it; otherwise it waits for the next run. It never starts outgoing work.
     */
    fun scheduledStartAllowed(authorized: Boolean, ownerStopped: Boolean, systemAllowsStart: Boolean,
        notificationsAllowed: Boolean, busy: Boolean): Boolean =
        authorized && !ownerStopped && systemAllowsStart && notificationsAllowed && !busy

    /** Android restarting the sticky service is refused after an owner stop, like the periodic check. */
    fun acceptsStickyRestart(sdk: Int, ownerStopped: () -> Boolean): Boolean = sdk < 30 || !ownerStopped()

    /** The repair page can prepare Telecom only after the audio helper connects (FlashPhone v1.1.15). */
    fun needsHelperBeforePreparing(enabled: Boolean, helperConnected: Boolean, configuring: Boolean): Boolean =
        enabled && !helperConnected && !configuring

    /** 不限时长(0)按最大档给预算;锁自然到期后由运行中的看门狗重新拿。 */
    fun activeWakeBudgetMs(maxCallSeconds: Int): Long =
        (if (maxCallSeconds <= 0) CallPolicy.MAX_SECONDS else maxCallSeconds.coerceIn(15, CallPolicy.MAX_SECONDS)) * 1_000L + 10_000L
}

/** A failed automatic restore must not spin; user retry is always available. */
internal class CallStandbyResumeGate {
    private var attemptedWhileVisible = false

    fun take(visible: Boolean, eligible: Boolean): Boolean {
        if (!visible) attemptedWhileVisible = false
        if (!visible || !eligible || attemptedWhileVisible) return false
        attemptedWhileVisible = true
        return true
    }
}

/** Waits for this existing service request only; never starts or retries an Android service. */
internal suspend fun awaitCallForegroundReady(isStarting: () -> Boolean, isReady: () -> Boolean): Boolean =
    withTimeoutOrNull(5_250L) {
        while (isStarting()) delay(25L)
        isReady()
    } ?: false
