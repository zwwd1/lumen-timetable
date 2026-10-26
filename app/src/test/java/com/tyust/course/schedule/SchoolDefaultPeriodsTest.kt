package com.tyust.course.schedule

import com.tyust.course.manager.ScheduleSettingsManager
import org.junit.Assert.*
import org.junit.Test

class SchoolDefaultPeriodsTest {
    @Test fun newAccountsUseTenTeachingPeriods() {
        val manager = ScheduleSettingsManager(MemoryPreferences())
        assertEquals(10, manager.periodCount)
        assertEquals("08:30", manager.getPeriodTimes().first().startTime)
        assertEquals("21:00", manager.getPeriodTimes().last().endTime)
    }
    @Test fun savedCustomTimesAndCountArePreserved() {
        val manager = ScheduleSettingsManager(MemoryPreferences())
        manager.periodCount = 12
        val custom = listOf(ScheduleSettingsManager.PeriodTime(1, "09:00", "10:00"))
        manager.savePeriodTimes(custom)
        assertEquals(custom, manager.getPeriodTimes())
        assertEquals(12, manager.periodCount)
    }
    @Test fun unknownLegacyPeriodsReceiveKnownTimesWithoutDroppingExtraIndices() {
        val manager = ScheduleSettingsManager(MemoryPreferences())
        manager.savePeriodTimes((1..12).map { ScheduleSettingsManager.PeriodTime(it, "", "") })
        val periods = manager.getPeriodTimes()
        assertEquals(12, periods.size)
        assertEquals("08:30", periods.first().startTime)
        assertEquals("", periods.last().startTime)
    }
    @Test fun blankCachedClocksDoNotHideDefaultsAndCustomClocksTakePrecedence() {
        val periods = ScheduleSettingsManager(MemoryPreferences()).getDefaultPeriodTimes()
        val calendar = ScheduleTimeBase("2026-09-07", mapOf(1 to "", 2 to "09:30"), mapOf(1 to "", 2 to "10:15"))
            .withPeriodDefaults(periods)
        assertEquals("08:30", calendar.periodStarts[1])
        assertEquals("09:15", calendar.periodEnds[1])
        assertEquals("09:30", calendar.periodStarts[2])
        assertEquals("10:15", calendar.periodEnds[2])
    }
}
