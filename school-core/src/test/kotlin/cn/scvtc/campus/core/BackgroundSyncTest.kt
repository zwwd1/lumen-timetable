package cn.scvtc.campus.core

import java.io.IOException
import java.util.concurrent.CancellationException
import kotlin.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class BackgroundSyncTest {
    private fun run(attempt: Int = 0, action: suspend () -> BackgroundSyncOutcome): BackgroundSyncResult {
        var completed: Result<BackgroundSyncResult>? = null
        suspend { executeBackgroundSync(attempt, action) }.startCoroutine(object : Continuation<BackgroundSyncResult> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<BackgroundSyncResult>) { completed = result }
        })
        return checkNotNull(completed).getOrThrow()
    }

    @Test fun failedRefreshDoesNotBecomeSuccessfulWork() {
        val result = run {
            // The real old coordinator returns a failed outcome without throwing.
            BackgroundSyncOutcome(saved = false, retryable = true)
        }
        assertEquals(BackgroundSyncResult.RETRY, result)
    }

    @Test fun temporaryNetworkFailureIsRetriedButHasALimit() {
        for (attempt in 0..2) assertEquals(BackgroundSyncResult.RETRY, run(attempt) { throw IOException("offline") })
        assertEquals(BackgroundSyncResult.FAILED, run(3) { throw IOException("offline") })
        assertEquals(BackgroundSyncResult.FAILED, run(3) { BackgroundSyncOutcome(false, true) })
    }

    @Test fun mandatoryAuthenticationAndProtocolFailuresStopThisAttempt() {
        assertEquals(BackgroundSyncResult.FAILED, run { BackgroundSyncOutcome(false, false) })
        assertEquals(BackgroundSyncResult.FAILED, run { throw IllegalStateException("protocol changed") })
    }

    @Test fun cancellationRemainsCancellation() {
        val cancelled = CancellationException("account changed")
        try { run { throw cancelled }; fail("Cancellation was swallowed") }
        catch (caught: CancellationException) { assertSame(cancelled, caught) }
    }

    @Test fun committedRefreshCompletesWithoutAnotherCall() {
        var calls = 0
        assertEquals(BackgroundSyncResult.SAVED, run { calls++; BackgroundSyncOutcome(true) })
        assertEquals(1, calls)
    }
}
