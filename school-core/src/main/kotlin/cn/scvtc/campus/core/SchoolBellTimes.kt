package cn.scvtc.campus.core

/** Default teaching timetable supplied by the user on 2026-10-26. */
object SchoolBellTimes {
    data class Period(val number: Int, val start: String, val end: String)
    val periods = listOf(
        Period(1, "08:30", "09:15"), Period(2, "09:25", "10:10"),
        Period(3, "10:30", "11:15"), Period(4, "11:25", "12:10"),
        Period(5, "14:00", "14:45"), Period(6, "14:55", "15:40"),
        Period(7, "15:50", "16:35"), Period(8, "16:45", "17:30"),
        Period(9, "19:20", "20:05"), Period(10, "20:15", "21:00")
    )
    const val practicalNote = "实训课：上午 08:30–11:40，下午 14:00–17:10，课间休息 10 分钟；具体节次以学校安排为准。"
}
