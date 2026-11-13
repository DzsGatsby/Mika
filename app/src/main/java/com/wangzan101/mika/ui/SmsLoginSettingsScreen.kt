package com.wangzan101.mika.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wangzan101.mika.R
import com.wangzan101.mika.cap.phone.PhoneServices
import com.wangzan101.mika.ui.components.*
import com.wangzan101.mika.ui.theme.MikaIcons

@Composable
fun SmsLoginSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val runtime = remember { PhoneServices.of(context).smsLogin }
    val access = remember { (context.applicationContext as com.wangzan101.mika.MikaApp).appAccess }
    DisposableEffect(access) { val protection = access.protectUi(); onDispose { protection.close() } }
    val consent by runtime.settings.state.collectAsState()
    var permitted by remember { mutableStateOf(runtime.permitted(context)) }
    var disclosure by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    fun save(enabled: Boolean) {
        error = runCatching { runtime.settings.setEnabled(enabled) }.isFailure
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
        if (granted) save(true) else { save(false); error = true }
    }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permitted = runtime.permitted(context)
                if (!permitted && runtime.settings.state.value.enabled) save(false)
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    fun toggle(on: Boolean) { if (on) disclosure = true else save(false) }
    PageScaffold(stringResource(R.string.sms_login_title), onBack) {
        GroupCard {
            ListRow(MikaIcons.Shield, stringResource(R.string.sms_login_enable),
                stringResource(R.string.sms_login_default_off), first = true, onClick = { toggle(!consent.enabled) }) {
                MikaSwitch(consent.enabled, ::toggle, Modifier.testTag("sms-login-enabled"))
            }
        }
        FooterNote(stringResource(R.string.sms_login_limits))
        GroupCard {
            ListRow(MikaIcons.Shield, stringResource(R.string.sms_login_permission),
                stringResource(if (permitted) R.string.state_on else R.string.state_off), first = true,
                onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { RowChevron() }
        }
        if (error) FooterNote(stringResource(R.string.sms_login_error))
    }
    if (disclosure) AlertDialog(
        onDismissRequest = { disclosure = false },
        title = { Text(stringResource(R.string.sms_login_title)) },
        text = { Text(stringResource(R.string.sms_login_disclosure)) },
        confirmButton = { TextButton({
            disclosure = false
            if (runtime.permitted(context)) { permitted = true; save(true) }
            else request.launch(Manifest.permission.RECEIVE_SMS)
        }) { Text(stringResource(R.string.sms_login_enable)) } },
        dismissButton = { TextButton({ disclosure = false }) { Text(stringResource(R.string.cancel)) } },
    )
}
