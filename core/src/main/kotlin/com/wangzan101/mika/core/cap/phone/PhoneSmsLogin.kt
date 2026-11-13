package com.wangzan101.mika.core.cap.phone

import com.wangzan101.mika.core.prompt.Prompts
import com.wangzan101.mika.core.tool.*
import com.wangzan101.mika.core.cap.phone.sms.SmsLoginStatus
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object PhoneSmsLogin : PhoneTool {
    override val verb = Verb.SmsLogin
    override val risk = Risk.Write
    override val label = "短信验证码登录"
    override val sandboxCallable = false
    override val def = ToolDef("phone_sms_login", Prompts.tool("phone_sms_login"), mapOf(
        "app" to str("Exact Android package of the app the user asked to log into."),
        "resourceId" to str("Exact resource ID of a single native verification-code editor from the current screen."),
        "sendButtonId" to str("Exact resource ID of the native send/resend-code button. The host listens before clicking it once."),
    ), listOf("app", "resourceId", "sendButtonId"))
    override fun available(ctx: ToolContext): Boolean {
        val run = ctx.config.runSpec ?: return false
        return ctx[PhoneCapability.KEY]?.smsLogin != null && !run.readOnly && run.parent == null &&
            run.provenance.origin == com.wangzan101.mika.core.run.RunOrigin.Ui &&
            run.provenance.attendance != com.wangzan101.mika.core.run.Attendance.Unattended
    }

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        if (!available(ctx)) return ToolExecution(buildJsonObject { put("ok", false); put("status", "unavailable") })
        val pkg = a.str("app", max = 200) ?: throw ArgError("app", "required")
        if (!PACKAGE_NAME_RE.matches(pkg)) throw ArgError("app", "must be an Android package name")
        val id = a.str("resourceId", max = 300) ?: throw ArgError("resourceId", "required")
        val send = a.str("sendButtonId", max = 300) ?: throw ArgError("sendButtonId", "required")
        val status = ctx.phone.smsLogin?.fill(pkg, id, send) ?: SmsLoginStatus.UNAVAILABLE
        return ToolExecution(buildJsonObject {
            put("ok", status == SmsLoginStatus.FILLED)
            put("status", status.name.lowercase())
            put("message", "No code is returned. If not filled, offer manual takeover. After the private login flow starts, phone observations and previews remain hidden for this run; hand over any remaining login step and finish the task. Do not claim login succeeded from filled alone.")
        })
    }
}
