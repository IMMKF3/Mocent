package com.salarydance.app

import java.time.LocalDate
import java.time.LocalTime

/** 年假引擎：基准日起按工作日累积，今日按计薪进度实时增长 */
object Leave {
    class Result(
        val perDayH: Double, val baseH: Double, val pastH: Double,
        val todayH: Double, val usedH: Double, val past: Int,
        val totalH: Double, val stdH: Double,
    )

    fun calc(st: State, now: LocalDate, t: LocalTime): Result {
        val L = st.leave
        val stdH = L.stdHours.takeIf { it > 0 } ?: 8.0
        val perDayH = L.perDay * if (L.perDayUnit == "day") stdH else 1.0
        val baseH = L.base * if (L.baseUnit == "day") stdH else 1.0
        val baseDate = L.baseDate?.let { runCatching { Dates.parseKey(it) }.getOrNull() } ?: now
        val yesterday = now.minusDays(1)
        var past = 0
        val s = baseDate.plusDays(1)
        if (!s.isAfter(yesterday)) past = Holidays.countWorkdays(s, yesterday)
        var todayH = 0.0
        if (Dates.dateKey(now) != Dates.dateKey(baseDate) && Holidays.isWorkday(now)) {
            val paidFull = Pay.paidHours(st.settings) * 60.0
            todayH = perDayH * (Pay.paidElapsedMin(st, now, t) / maxOf(1.0, paidFull))
        }
        val usedH = L.log.sumOf { it.amount }
        return Result(perDayH, baseH, past * perDayH, todayH, usedH, past,
            baseH + past * perDayH + todayH - usedH, stdH)
    }
}
