package com.wangzan101.mika.cap.phone.sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.wangzan101.mika.cap.phone.a11y.AndroidScreen
import com.wangzan101.mika.cap.phone.a11y.ScreenAccessibility
import com.wangzan101.mika.core.cap.phone.sms.*
import com.wangzan101.mika.core.uistate.ScreenPort
import com.wangzan101.mika.settings.AppAccessStore
import com.wangzan101.mika.settings.SmsLoginSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex

/** Process-wide admission prevents two tasks consuming the same incoming SMS. */
class SmsLoginRuntime(context: Context) {
    val settings = SmsLoginSettings(java.io.File(context.filesDir, "sms-login-consent.txt"))
    internal val lock = Mutex()
    private val privatePackages = java.util.concurrent.ConcurrentHashMap<String, String>()
    val privateLoginActive: Boolean get() = privatePackages.isNotEmpty()
    fun isPrivate(pkg: String?) = pkg != null && privatePackages.containsValue(pkg)
    internal fun protect(pkg: String): AutoCloseable {
        val token = java.util.UUID.randomUUID().toString()
        privatePackages[token] = pkg
        return AutoCloseable { privatePackages.remove(token) }
    }
    fun permitted(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
}

/** UI main task only. No inbox query, static receiver, notifications, clipboard, code logs or retries. */
class AndroidSmsLogin(
    private val context: Context,
    private val runtime: SmsLoginRuntime,
    private val access: AppAccessStore,
    private val guarded: ScreenPort,
    private val privacy: SmsPrivateScreen,
    private val screen: () -> AndroidScreen?,
) : SmsLoginPort, AutoCloseable {
    private var protection: AutoCloseable? = null
    override fun close() { protection?.close(); protection = null }
    override suspend fun fill(packageName: String, resourceId: String, sendButtonId: String): SmsLoginStatus {
        if (privacy.privateLogin) return SmsLoginStatus.UNAVAILABLE
        val consent = runtime.settings.state.value
        if (!consent.enabled) return SmsLoginStatus.DISABLED
        if (!runtime.permitted(context)) return SmsLoginStatus.PERMISSION_REQUIRED
        if (!runtime.lock.tryLock()) return SmsLoginStatus.BUSY
        try {
            val device = screen() ?: return SmsLoginStatus.UNAVAILABLE
            guarded.checkAppAccess()
            if (!SmsLoginMatcher.target(guarded.peek(), packageName, resourceId)) return SmsLoginStatus.TARGET_UNVERIFIED
            val a11y = ScreenAccessibility.instance.value ?: return SmsLoginStatus.UNAVAILABLE
            // One-way until this run ends, including declines/timeouts. Never expose post-fill screens.
            privacy.begin()
            if (protection == null) protection = runtime.protect(packageName)
            return withTimeoutOrNull(180_000) {
                coroutineScope {
                    val operation = async {
                        val label = runCatching { context.packageManager.getApplicationLabel(
                            context.packageManager.getApplicationInfo(packageName, 0)).toString() }.getOrDefault(packageName)
                        val scope = smsLoginConsent(a11y, access, label, packageName)
                            ?: return@async SmsLoginStatus.DECLINED_OR_TIMED_OUT
                        guarded.checkAppAccess()
                        if (!SmsLoginMatcher.target(guarded.peek(), packageName, resourceId, scope)) return@async SmsLoginStatus.TARGET_UNVERIFIED
                        val cancelled = CompletableDeferred<Unit>()
                        withSmsListening(a11y, access, cancelled) {
                            receive(scope, cancelled) {
                                device.requestSmsLoginCode(packageName, resourceId, sendButtonId, scope) { action ->
                                    !cancelled.isCompleted && dispatch(consent.revision, packageName, action)
                                }
                            }?.let { code ->
                                try {
                                    currentCoroutineContext().ensureActive()
                                    guarded.checkAppAccess()
                                    val filled = device.fillSmsLoginCode(packageName, resourceId, scope, code) { action ->
                                        !cancelled.isCompleted && dispatch(consent.revision, packageName, action)
                                    }
                                    if (filled) SmsLoginStatus.FILLED else SmsLoginStatus.NOT_FILLED
                                } finally { code.fill('\u0000') }
                            } ?: SmsLoginStatus.NOT_FILLED
                        }
                    }
                    val revoked = async {
                        runtime.settings.state.first { !it.enabled || it.revision != consent.revision }
                        SmsLoginStatus.REVOKED
                    }
                    try { select { operation.onAwait { it }; revoked.onAwait { it } } }
                    finally { operation.cancelAndJoin(); revoked.cancelAndJoin() }
                }
            } ?: SmsLoginStatus.DECLINED_OR_TIMED_OUT
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            // Only a status leaves this boundary. Exception text may include framework input data.
            return SmsLoginStatus.UNAVAILABLE
        } finally { runtime.lock.unlock() }
    }

    private fun dispatch(revision: Long, pkg: String, action: () -> Boolean): Boolean =
        runtime.settings.dispatch(revision) {
            runtime.permitted(context) && access.current().let { it.available && it.denial(pkg) == null } && action()
        }

    private suspend fun receive(scope: SmsLoginScope, cancelled: CompletableDeferred<Unit>,
        requestCode: suspend () -> Boolean): CharArray? = coroutineScope {
        val pending = CompletableDeferred<Unit>()
        val owned = java.util.concurrent.atomic.AtomicReference<CharArray?>()
        val started = System.currentTimeMillis()
        val window = SmsLoginWindow(scope, started)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION || !pending.isActive || !runtime.permitted(this@AndroidSmsLogin.context)) return
                try {
                    val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                    if (parts.isEmpty() || parts.size > 10 || parts.any { it.timestampMillis < started / 1000 * 1000 || it.originatingAddress != scope.sender }) return
                    val code = window.accept(parts.first().originatingAddress.orEmpty(), parts.joinToString("") { it.messageBody.orEmpty() },
                        parts.minOf { it.timestampMillis }, System.currentTimeMillis()) ?: return
                    if (owned.compareAndSet(null, code)) pending.complete(Unit) else code.fill('\u0000')
                } catch (_: Exception) { /* malformed/unsupported message: ignore without logging */ }
            }
        }
        // Exported is needed for privileged telephony UIDs; signature permission blocks app spoofing.
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION),
            Manifest.permission.BROADCAST_SMS, null, ContextCompat.RECEIVER_EXPORTED)
        var transferred = false
        try {
            if (!requestCode()) return@coroutineScope null
            val arrived = withTimeoutOrNull(90_000) { select { pending.onAwait { true }; cancelled.onAwait { false } } } == true
            val result = if (arrived) owned.get() else null
            transferred = result != null
            result
        } finally {
            window.close()
            try { context.unregisterReceiver(receiver) } finally {
                // Own and wipe a delivered code even if timeout/cancellation won the race.
                if (!transferred) owned.getAndSet(null)?.fill('\u0000')
                pending.cancel()
            }
        }
    }
}
