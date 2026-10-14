package com.tyust.course.scvtc

import com.tyust.course.schedule.MemoryPreferences
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ServiceReadStatusStoreTest {
    @Test fun failedRefreshRetainsLastSuccessAndDoesNotStoreExceptionDetails() {
        val prefs = MemoryPreferences(); var time = 100L
        val store = ServiceReadStatusStore(prefs) { time }
        store.begin("a", "term", "grades"); store.saved("a", "term", "grades", 200L)
        time = 300L; store.begin("a", "term", "grades")
        store.failed("a", "term", "grades", IOException("private-response-body"))
        val restored = ServiceReadStatusStore(prefs).read("a", "term", "grades")
        assertEquals(200L, restored.lastSuccess); assertEquals(300L, restored.lastAttempt)
        assertEquals(ServiceReadCode.NETWORK_FAILED, restored.code)
        assertFalse(prefs.all.toString().contains("private-response-body"))
        assertEquals(0L, store.read("b", "term", "grades").lastSuccess)
        assertEquals(0L, store.read("a", "other", "grades").lastSuccess)
        assertEquals(0L, store.read("a", "term", "exams").lastSuccess)
    }
}
