package com.tyust.course.schedule

import cn.scvtc.campus.core.Extraction
import cn.scvtc.campus.core.ScheduleWriteGuard
import com.tyust.course.academic.AcademicScheduleEntry
import com.tyust.course.academic.AcademicStudyBridge
import com.tyust.course.academic.AcademicTerm

/** Projects the committed school snapshot for the timetable and widgets. */
internal object ScvtcScheduleProjection {
    fun canRestore(snapshot: Extraction, account: String, term: String): Boolean =
        snapshot.account == account && snapshot.semester == term &&
            runCatching { ScheduleWriteGuard.validateSnapshot(snapshot) }.isSuccess

    fun save(cache: ScheduleCacheStore, accountKey: String, snapshot: Extraction, fetchedAt: Long) {
        ScheduleWriteGuard.validateSnapshot(snapshot)
        val entries = snapshot.meetings.map {
            AcademicScheduleEntry(it.name, it.teacher, it.room, it.day, it.startNode, it.endNode,
                it.weeks.joinToString("、"), it.stableId())
        }
        val term = AcademicTerm(snapshot.semester)
        cache.save(accountKey, "scvtc", CachedSchedule(term, term, AcademicStudyBridge.scheduleJson(entries), false),
            fetchedAt, confirmedEmpty = snapshot.meetings.isEmpty() && snapshot.emptyConfirmed)
    }
}
