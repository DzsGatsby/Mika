package com.wangzan101.mika.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class SmsLoginConsent(val enabled: Boolean = false, val revision: Long = 0)

/** Stores only the opt-in bit. Attempt scopes, messages and codes are never persisted. */
class SmsLoginSettings(private val file: File) {
    private val mutable = MutableStateFlow(SmsLoginConsent(runCatching { file.readText() == "enabled-v1" }.getOrDefault(false)))
    val state = mutable.asStateFlow()

    @Synchronized fun setEnabled(enabled: Boolean) {
        val next = SmsLoginConsent(enabled, state.value.revision + 1)
        // Revocation wins even if persistence fails. An in-flight dispatch shares this lock.
        if (!enabled) mutable.value = next
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        try {
            tmp.writeText(if (enabled) "enabled-v1" else "disabled-v1")
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            mutable.value = next
        } catch (e: Exception) {
            if (!enabled) file.delete()
            throw e
        } finally { tmp.delete() }
    }

    /** The last permission check and one SET_TEXT dispatch are atomic with disabling the setting. */
    @Synchronized fun dispatch(revision: Long, action: () -> Boolean): Boolean =
        state.value.enabled && state.value.revision == revision && action()
}
