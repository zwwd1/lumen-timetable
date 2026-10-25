package com.tyust.course.scvtc

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ServiceStatusQueryTest {
    private lateinit var database: ScvtcDatabase

    @Before fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ScvtcDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun closeDatabase() { database.close() }

    @Test fun timestampsStayWithinTheRequestedAccountAndTerm() = runTest {
        val services = database.services()
        // Payloads are deliberately unusable: status display must never decode them.
        services.put(ScvtcServiceSnapshot("first", "2026-2027-1", "grades", "not-ciphertext", 100))
        services.put(ScvtcServiceSnapshot("first", "2026-2027-1", "credits", "not-json", 200))
        services.put(ScvtcServiceSnapshot("other", "2026-2027-1", "grades", "private", 300))
        services.put(ScvtcServiceSnapshot("first", "2025-2026-2", "grades", "old", 400))

        assertEquals(mapOf("grades" to 100L, "credits" to 200L),
            services.savedTimes("first", "2026-2027-1").associate { it.module to it.fetchedAt })
        assertEquals(100L, services.savedAt("first", "2026-2027-1", "grades") ?: -1L)
        assertNull(services.savedAt("missing", "2026-2027-1", "grades"))
        assertTrue(services.savedTimes("first", "missing").isEmpty())
    }

    @Test fun scheduleTimestampDoesNotReadOrChangeThePayload() = runTest {
        val schedules = database.schedules()
        val row = ScvtcSnapshot("first", "2026-2027-1", "preserve-encrypted-data", 123)
        schedules.put(row)
        schedules.put(row.copy(account = "other", fetchedAt = 456))

        assertEquals(123L, schedules.savedAt("first", "2026-2027-1") ?: -1L)
        assertNull(schedules.savedAt("first", "missing"))
        assertEquals(row, schedules.read("first", "2026-2027-1"))
    }
}
