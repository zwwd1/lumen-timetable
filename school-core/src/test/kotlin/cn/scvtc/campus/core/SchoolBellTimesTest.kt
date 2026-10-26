package cn.scvtc.campus.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime
import java.time.Duration

class SchoolBellTimesTest {
    @Test fun matchesSuppliedTeachingTimetable() {
        assertEquals(listOf("08:30–09:15", "09:25–10:10", "10:30–11:15", "11:25–12:10",
            "14:00–14:45", "14:55–15:40", "15:50–16:35", "16:45–17:30",
            "19:20–20:05", "20:15–21:00"), SchoolBellTimes.periods.map { "${it.start}–${it.end}" })
        assertEquals((1..10).toList(), SchoolBellTimes.periods.map { it.number })
    }
    @Test fun lessonsRemain45MinutesAndDoNotOverlap() {
        SchoolBellTimes.periods.forEach {
            assertEquals(45L, Duration.between(LocalTime.parse(it.start), LocalTime.parse(it.end)).toMinutes())
        }
        SchoolBellTimes.periods.zipWithNext().forEach { (a, b) ->
            assertTrue(LocalTime.parse(a.end) < LocalTime.parse(b.start))
        }
    }
}
