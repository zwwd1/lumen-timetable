package cn.scvtc.campus.core

/** Fail closed before any database, projection, reminder or success timestamp write. */
object ScheduleWriteGuard {
  /** A persisted snapshot may be empty after validated single-week cancellations.
   * Its accumulated coverage is not a new full-term query to be committed. */
  fun validateSnapshot(snapshot: Extraction) {
    require(snapshot.module == "schedule" && snapshot.account.isNotBlank() && snapshot.semester.isNotBlank())
    validateRows(snapshot)
    require(snapshot.meetings.isNotEmpty() || snapshot.emptyConfirmed && snapshot.full &&
      snapshot.coverageWeeks.isNotEmpty() && snapshot.coverageWeeks.all { it in 1..100 }) {
      "本机课表缺少有效的已确认范围"
    }
  }

  fun validate(old: Extraction?, next: Extraction) {
    if (next.module != "schedule") return
    validateRows(next)
    require(next.meetings.isNotEmpty() || next.emptyConfirmed) { "没有有效课程，原课表保留" }
    require(next.meetings.isNotEmpty() || (next.full && next.coverageWeeks.size==1 && next.coverageWeeks.single() in 1..100)) {
      "空课表缺少已确认的单周查询范围，原课表及成功时间保留"
    }
    require(!(old?.meetings?.isNotEmpty() == true && next.meetings.isEmpty() && next.full &&
      next.coverageWeeks.size > 1)) { "学校返回整段空课表，已阻止覆盖原课表；请确认官方页的学期与登录状态" }
  }

  private fun validateRows(next: Extraction) {
    require(next.warnings.isEmpty()) { next.warnings.joinToString("；") }
    require(next.meetings.all { it.name.isNotBlank() && it.day in 1..7 &&
      it.startNode >= 1 && it.endNode >= it.startNode && it.weeks.isNotEmpty() &&
      it.weeks.all { w -> w in 1..100 } }) { "课程数据不完整，原课表保留" }
  }
}
