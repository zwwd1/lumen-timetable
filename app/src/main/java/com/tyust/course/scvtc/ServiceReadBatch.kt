package com.tyust.course.scvtc

import cn.scvtc.campus.JwxtAuthenticationRequired
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException

internal data class ServiceBatchResult(
    val saved: List<String>, val failed: List<String>, val retryableFailure: IOException?,
) {
    val complete get() = failed.isEmpty()
}

/** WorkManager retries only the network failures from this committed timetable read. */
internal fun serviceRetryModules(attempt: Int, scheduleSavedAt: Long, statuses: List<ServiceReadStatus>): List<String> {
    val schedule = statuses.firstOrNull { it.module == "schedule" }
    if (attempt <= 0 || scheduleSavedAt <= 0 || schedule?.code != ServiceReadCode.SAVED ||
        schedule.lastSuccess != scheduleSavedAt || statuses.any { it.code == ServiceReadCode.AUTH_REQUIRED }) return emptyList()
    return statuses.filter {
        it.code == ServiceReadCode.NETWORK_FAILED && it.lastAttempt >= scheduleSavedAt &&
            it.module in cn.scvtc.campus.JwxtServices.endpoints
    }.map { it.module }.distinct()
}

/** Reads independent modules without discarding another module's committed data. */
internal suspend fun readServiceBatch(
    modules: List<String>,
    readAndSave: suspend (String) -> Unit,
    onFailure: (String, Exception) -> Unit,
): ServiceBatchResult {
    val saved = mutableListOf<String>(); val failed = mutableListOf<String>()
    var retryableFailure: IOException? = null
    for (module in modules) {
        currentCoroutineContext().ensureActive()
        try {
            readAndSave(module)
            saved += module
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            failed += module
            onFailure(module, error)
            if (error is JwxtAuthenticationRequired) throw error
            if (error is IOException) retryableFailure = error
        }
    }
    return ServiceBatchResult(saved, failed, retryableFailure)
}
