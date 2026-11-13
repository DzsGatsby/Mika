package com.wangzan101.mika.cap.phone.sms

import android.view.Gravity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.R
import com.wangzan101.mika.cap.phone.a11y.OverlayPlacement
import com.wangzan101.mika.cap.phone.a11y.ScreenAccessibility
import com.wangzan101.mika.core.cap.phone.sms.SmsLoginScope
import com.wangzan101.mika.settings.AppAccessStore
import com.wangzan101.mika.ui.theme.MikaTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Secure, local-only form: these values never pass through AskPort, chat or saved state. */
internal suspend fun smsLoginConsent(service: ScreenAccessibility, store: AppAccessStore, appLabel: String,
    packageName: String): SmsLoginScope? = withContext(Dispatchers.Main.immediate) {
    val protection = store.protectUi()
    val reply = CompletableDeferred<SmsLoginScope?>()
    val window = service.overlays().open(OverlayPlacement(Gravity.CENTER),
        onOutside = { reply.complete(null) }, secure = true) {
        MikaTheme {
            Surface { Column(Modifier.widthIn(max = 360.dp).heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var sender by remember { mutableStateOf("") }
                var signature by remember { mutableStateOf("") }
                var account by remember { mutableStateOf("") }
                Text(stringResource(R.string.sms_login_title), style = MaterialTheme.typography.titleLarge)
                Text(appLabel)
                Text(packageName)
                Text(stringResource(R.string.sms_login_consent))
                OutlinedTextField(sender, { sender = it.take(24) }, label = { Text(stringResource(R.string.sms_login_sender)) }, singleLine = true)
                OutlinedTextField(signature, { signature = it.take(30) }, label = { Text(stringResource(R.string.sms_login_signature)) }, singleLine = true)
                OutlinedTextField(account, { account = it.filter(Char::isDigit).take(11) }, label = { Text(stringResource(R.string.sms_login_account)) }, singleLine = true)
                val scope = SmsLoginScope(sender.trim(), signature.trim(), account.trim())
                TextButton({ reply.complete(scope) }, enabled = scope.valid) { Text(stringResource(R.string.sms_login_allow_once)) }
                TextButton({ reply.complete(null) }) { Text(stringResource(R.string.cancel)) }
            } }
        }
    }
    try {
        if (window == null) null else { window.setFocusable(true); reply.await() }
    } finally { window?.close(); protection.close() }
}

internal suspend fun <T> withSmsListening(service: ScreenAccessibility, store: AppAccessStore,
    cancelled: CompletableDeferred<Unit>, block: suspend () -> T): T = withContext(Dispatchers.Main.immediate) {
    val protection = store.protectUi()
    val window = service.overlays().open(OverlayPlacement(Gravity.CENTER), secure = true) {
        MikaTheme { Surface { Column(Modifier.widthIn(max = 340.dp).padding(20.dp)) {
            Text(stringResource(R.string.sms_login_listening))
            TextButton({ cancelled.complete(Unit) }) { Text(stringResource(R.string.cancel)) }
        } } }
    }
    try { check(window != null); block() } finally { window?.close(); protection.close() }
}
