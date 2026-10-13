package cn.scvtc.campus.core

import java.io.IOException
import java.util.concurrent.CancellationException

/** A task succeeds only after the caller has committed its verified result. */
enum class BackgroundSyncResult { SAVED, RETRY, FAILED }

data class BackgroundSyncOutcome(val saved: Boolean, val retryable: Boolean = false)

suspend fun executeBackgroundSync(
    attempt: Int,
    synchronize: suspend () -> BackgroundSyncOutcome
): BackgroundSyncResult {
    val outcome = try {
        synchronize()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: IOException) {
        BackgroundSyncOutcome(saved = false, retryable = true)
    } catch (_: Exception) {
        BackgroundSyncOutcome(saved = false)
    }
    return when {
        outcome.saved -> BackgroundSyncResult.SAVED
        outcome.retryable && attempt < 3 -> BackgroundSyncResult.RETRY
        else -> BackgroundSyncResult.FAILED
    }
}
