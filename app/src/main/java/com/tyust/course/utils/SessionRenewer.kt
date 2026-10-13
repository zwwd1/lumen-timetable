package com.tyust.course.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.tyust.course.login.PasswordLoginCallback
import com.tyust.course.login.PasswordLoginGatewayFactory
import com.tyust.course.manager.SessionToken
import com.tyust.course.manager.UserManager

/** All entry points share one recovery operation; Android callbacks are serialized on main. */
object SessionRenewer {
    private val handler = Handler(Looper.getMainLooper())
    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post(action)
    }
    private val coordinator by lazy {
        val user = UserManager.getInstance()
        SessionRecoveryCoordinator(
            sessions = user.sessionState,
            now = SystemClock::elapsedRealtime,
            canRestore = { user.currentSchool != null && (if(user.currentSchool?.id=="scvtc")
                com.tyust.course.scvtc.ScvtcRuntime.canRestoreFrameworkSession() else user.canAutoRelogin()) },
            install = { expected, cookie -> user.saveCookieIfCurrent(expected, cookie) },
            login = { _, done ->
                if(user.currentSchool?.id=="scvtc") {
                    val job=com.tyust.course.scvtc.ScvtcRuntime.restoreFrameworkSession(user.studentId) { result ->
                        onMain { done(result.fold(
                            { LoginRecoveryOutcome.Cookie(it) },
                            { error ->
                                val auth=error as? cn.scvtc.campus.JwxtAuthenticationRequired
                                val reason=when(auth?.stage) {
                                    cn.scvtc.campus.AuthenticationStage.CHALLENGE,cn.scvtc.campus.AuthenticationStage.NOTICE -> RecoveryFailure.VerificationRequired
                                    cn.scvtc.campus.AuthenticationStage.REJECTED -> RecoveryFailure.CredentialsRejected
                                    null -> if(error is java.io.IOException)RecoveryFailure.Network else RecoveryFailure.Login
                                    else -> RecoveryFailure.Login
                                }
                                LoginRecoveryOutcome.Failure(reason,"学校会话未恢复；已保存的密码与本地数据保留，可重试或打开官方认证页")
                            })) }
                    }
                    val cancel:()->Unit={job.cancel()}
                    cancel
                } else {
                val school = requireNotNull(user.currentSchool)
                val gateway = PasswordLoginGatewayFactory.create(school)
                fun complete(outcome: LoginRecoveryOutcome) {
                    gateway.clearSensitiveState()
                    onMain { done(outcome) }
                }
                gateway.login(school, user.username, user.accountPassword, object : PasswordLoginCallback {
                    override fun onSuccess(cookie: String) = complete(LoginRecoveryOutcome.Cookie(cookie))
                    override fun onCaptchaRequired(imageBytes: ByteArray) = complete(LoginRecoveryOutcome.Failure(RecoveryFailure.VerificationRequired))
                    override fun onCaptchaInvalid() = complete(LoginRecoveryOutcome.Failure(RecoveryFailure.VerificationRequired))
                    override fun onInvalidCredentials() = complete(LoginRecoveryOutcome.Failure(RecoveryFailure.CredentialsRejected))
                    override fun onWebLoginRequired(message: String) = complete(LoginRecoveryOutcome.Failure(RecoveryFailure.VerificationRequired, message))
                    override fun onError(message: String) = complete(LoginRecoveryOutcome.Failure(RecoveryFailure.Login, message))
                })
                val cancel: () -> Unit = { gateway.clearSensitiveState() }
                cancel
                }
            }
        )
    }

    val state get() = coordinator.state

    @JvmStatic fun canRenew(): Boolean = coordinator.canAttempt(UserManager.getInstance().sessionState.token)

    fun sessionChanged() = onMain { coordinator.sessionChanged() }

    fun request(
        expected: SessionToken,
        manual: Boolean = false,
        onDone: (SessionRecoveryResult) -> Unit = {}
    ) = onMain { coordinator.request(expected, manual, onDone) }

    /** Compatibility for the existing background service; UI uses the typed result above. */
    @JvmStatic fun renew(context: Context, onDone: (Boolean) -> Unit) {
        request(UserManager.getInstance().sessionState.token) { onDone(it is SessionRecoveryResult.Recovered) }
    }
}
