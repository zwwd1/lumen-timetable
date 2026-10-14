package com.tyust.course.scvtc

import android.content.SharedPreferences
import cn.scvtc.campus.JwxtAuthenticationRequired
import cn.scvtc.campus.core.digest
import java.io.IOException

internal enum class ServiceReadCode { NONE, READING, SAVED, NETWORK_FAILED, AUTH_REQUIRED, FAILED }
internal data class ServiceReadStatus(
    val module: String, val lastAttempt: Long = 0, val lastSuccess: Long = 0,
    val code: ServiceReadCode = ServiceReadCode.NONE,
)

/** Stores outcomes, never exception messages or school response bodies. */
internal class ServiceReadStatusStore(private val prefs: SharedPreferences, private val clock: () -> Long = System::currentTimeMillis) {
    private fun key(account: String, term: String, module: String) = digest("$account|$term|$module")
    fun read(account: String, term: String, module: String, cachedAt: Long = 0): ServiceReadStatus {
        val key = key(account, term, module)
        val code = runCatching { ServiceReadCode.valueOf(prefs.getString("$key:code", "NONE")!!) }.getOrDefault(ServiceReadCode.NONE)
        return ServiceReadStatus(module, prefs.getLong("$key:attempt", 0), maxOf(cachedAt, prefs.getLong("$key:success", 0)), code)
    }
    fun begin(account: String, term: String, module: String) {
        val key = key(account, term, module)
        prefs.edit().putLong("$key:attempt", clock()).putString("$key:code", ServiceReadCode.READING.name).apply()
    }
    fun saved(account: String, term: String, module: String, savedAt: Long) {
        val key = key(account, term, module)
        prefs.edit().putLong("$key:success", savedAt).putString("$key:code", ServiceReadCode.SAVED.name).apply()
    }
    fun failed(account: String, term: String, module: String, error: Exception) {
        val code = when (error) {
            is JwxtAuthenticationRequired -> ServiceReadCode.AUTH_REQUIRED
            is IOException -> ServiceReadCode.NETWORK_FAILED
            else -> ServiceReadCode.FAILED
        }
        prefs.edit().putString("${key(account, term, module)}:code", code.name).apply()
    }
}
