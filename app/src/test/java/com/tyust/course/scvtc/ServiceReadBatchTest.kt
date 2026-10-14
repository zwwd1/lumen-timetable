package com.tyust.course.scvtc

import cn.scvtc.campus.JwxtAuthenticationRequired
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ServiceReadBatchTest {
    @Test fun successfulModulesCompleteWithoutRetry() = runTest {
        val result = readServiceBatch(listOf("grades", "exams"), { }, { _, _ -> fail("Unexpected failure") })
        assertTrue(result.complete)
        assertEquals(listOf("grades", "exams"), result.saved)
        assertTrue(result.failed.isEmpty()); assertNull(result.retryableFailure)
    }
    @Test fun retriesTargetOnlyNetworkFailuresFromTheCurrentScheduleCommit() {
        val states = listOf(
            ServiceReadStatus("schedule", 100, 100, ServiceReadCode.SAVED),
            ServiceReadStatus("grades", 200, 50, ServiceReadCode.NETWORK_FAILED),
            ServiceReadStatus("exams", 200, 150, ServiceReadCode.SAVED),
            ServiceReadStatus("credits", 200, 50, ServiceReadCode.FAILED),
            ServiceReadStatus("levelExamResults", 99, 50, ServiceReadCode.NETWORK_FAILED),
        )
        assertEquals(listOf("grades"), serviceRetryModules(1, 100, states))
        assertTrue(serviceRetryModules(0, 100, states).isEmpty())
        assertTrue(serviceRetryModules(1, 0, states).isEmpty())
        assertTrue(serviceRetryModules(1, 100, states.map { if (it.module == "schedule") it.copy(code = ServiceReadCode.NETWORK_FAILED) else it }).isEmpty())
        assertTrue(serviceRetryModules(1, 100, states + ServiceReadStatus("authentication", code = ServiceReadCode.AUTH_REQUIRED)).isEmpty())
    }
    @Test fun malformedGradeResponseCannotReportEntireBatchSuccessful() = runTest {
        val committed = mutableListOf<String>(); val errors = mutableListOf<String>()
        val result = readServiceBatch(listOf("grades", "exams", "credits"), { module ->
            if (module == "grades") error("PAGE_CHANGED")
            committed += module
        }, { module, _ -> errors += module })
        assertEquals(listOf("exams", "credits"), committed)
        assertEquals(listOf("grades"), errors)
        assertFalse("Partial service failure was reported as success", result.complete)
        assertNull(result.retryableFailure)
    }

    @Test fun authFailureStopsTheBatchBeforeMoreAutomaticSubmissions() = runTest {
        val calls = mutableListOf<String>()
        try {
            readServiceBatch(listOf("grades", "exams"), { module -> calls += module; throw JwxtAuthenticationRequired() }, { _, _ -> })
            fail("Authentication failure ignored")
        } catch (_: JwxtAuthenticationRequired) { }
        assertEquals(listOf("grades"), calls)
    }

    @Test fun transientFailureKeepsOtherModulesAndRemainsRetryable() = runTest {
        val result = readServiceBatch(listOf("grades", "exams"), { module -> if (module == "grades") throw IOException() }, { _, _ -> })
        assertEquals(listOf("exams"), result.saved)
        assertEquals(listOf("grades"), result.failed)
        assertFalse(result.complete); assertNotNull(result.retryableFailure)
    }

    @Test fun cancellationIsNotASuccessOrServiceFailure() = runTest {
        var reported = false
        try {
            readServiceBatch(listOf("grades"), { throw CancellationException() }, { _, _ -> reported = true })
            fail("Cancellation swallowed")
        } catch (_: CancellationException) { }
        assertFalse(reported)
    }
}
