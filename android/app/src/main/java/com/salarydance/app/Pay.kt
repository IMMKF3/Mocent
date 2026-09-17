package com.salarydance.app

import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

enum class Status { BEFORE, WORKING, LUNCH, OVERTIME, AFTER, WEEKEND, HOLIDAY }

object Fmt {
    fun yuan2(v: Double): String = String.format(Locale.US, "%,.2f", v)
    fun yuan4(v: Double): String = String.format(Locale.US, "%.4f", v)
    fun yuan3(v: Double): String = String.format(Locale.US, "%.3f", v)
    fun pad2(v: Int): String = "%02d".format(v)
    fun hhmmss(t: LocalTime): String = "%02d:%02d:%02d".format(t.hour, t.minute, t.second)
}

/** 计薪引擎：与网页版同一套口径（含公司缴纳档位） */
object Pay {
    val TIER_NAMES = mapOf(
        "full" to "工资全额", "p80" to "工资 80% 档",
        "p60" to "工资 60% 档", "custom" to "自定义基数",
    )

    // 月度税率表 [上限, 税率, 速算扣除数]
    private val TAX_BRACKETS = arrayOf(
        doubleArrayOf(3000.0, 0.03, 0.0), doubleArrayOf(12000.0, 0.10, 210.0),
        doubleArrayOf(25000.0, 0.20, 1410.0), doubleArrayOf(35000.0, 0.25, 2660.0),
        doubleArrayOf(55000.0, 0.30, 4410.0), doubleArrayOf(80000.0, 0.35, 7160.0),
        doubleArrayOf(Double.MAX_VALUE, 0.45, 15160.0),
    )

    /** 缴费基数：档位折算 + 下限托底 + 上限封顶（个人与公司同基数） */
    fun insBase(s: Settings): Double {
        var base = s.salary
        when (s.tax.baseTier) {
            "p80" -> base *= 0.8
            "p60" -> base *= 0.6
            "custom" -> if (s.tax.customBase > 0) base = s.tax.customBase
        }
        if (s.tax.baseFloor > 0) base = maxOf(base, s.tax.baseFloor)
        if (s.tax.baseCap > 0) base = minOf(base, s.tax.baseCap)
        return maxOf(0.0, base)
    }

    /** 五险一金个人部分 */
    fun insuranceMonthly(s: Settings): Double =
        insBase(s) * (s.tax.socialRate + s.tax.fundRate) / 100.0

    /** 公司缴纳部分（不扣税，隐藏福利） */
    fun companyMonthly(s: Settings): Double =
        insBase(s) * (s.tax.coSocialRate + s.tax.coFundRate) / 100.0

    /** 公积金账户月进账（个人+公司） */
    fun fundMonthlyIn(s: Settings): Double =
        insBase(s) * (s.tax.fundRate + s.tax.coFundRate) / 100.0

    /** 月度个税（估算） */
    fun taxMonthly(s: Settings): Double {
        val taxable = s.salary - insuranceMonthly(s) - s.tax.threshold
        if (taxable <= 0) return 0.0
        for (b in TAX_BRACKETS)
            if (taxable <= b[0]) return maxOf(0.0, taxable * b[1] - b[2])
        return 0.0
    }

    fun netMonthly(s: Settings): Double = s.salary - insuranceMonthly(s) - taxMonthly(s)

    fun taxFactor(s: Settings): Double =
        if (s.tax.enabled && s.salary > 0) netMonthly(s) / s.salary else 1.0

    /** 每日计薪工时 = 上班到下班 − 午休 */
    fun paidHours(s: Settings): Double {
        val st = Dates.toMin(s.start); val en = Dates.toMin(s.end)
        var lunch = 0
        if (s.lunch) {
            val a = maxOf(Dates.toMin(s.lunchStart), st); val b = minOf(Dates.toMin(s.lunchEnd), en)
            if (b > a) lunch = b - a
        }
        return maxOf(0.0, (en - st - lunch) / 60.0)
    }

    /** 当月工作日：手动覆盖（限当月）优先，否则按节假日表动态计算 */
    fun monthWorkdays(st: State, today: LocalDate): Int {
        val s = st.settings
        val override = s.daysOverride
        if (override != null && s.daysMonthKey == Dates.monthKey(today))
            return override.coerceIn(1, 31)
        return Holidays.breakdown(today).net
    }

    fun perSec(st: State, today: LocalDate): Double {
        val h = paidHours(st.settings).takeIf { it > 0 } ?: 8.0
        val salary = maxOf(0.0, st.settings.salary)
        return salary / (monthWorkdays(st, today) * h * 3600.0) * taxFactor(st.settings)
    }

    fun perMin(st: State, today: LocalDate): Double = perSec(st, today) * 60
    fun perHour(st: State, today: LocalDate): Double = perSec(st, today) * 3600
    fun dailyPay(st: State, today: LocalDate): Double = perHour(st, today) * paidHours(st.settings)

    fun statusOf(st: State, d: LocalDate, t: LocalTime): Status {
        if (!Holidays.isWorkday(d))
            return if (Holidays.info(Dates.dateKey(d)) != null) Status.HOLIDAY else Status.WEEKEND
        val s = st.settings
        val n = t.hour * 60 + t.minute + t.second / 60.0
        val start = Dates.toMin(s.start); val end = Dates.toMin(s.end)
        if (n < start) return Status.BEFORE
        if (s.lunch && n >= Dates.toMin(s.lunchStart) && n < Dates.toMin(s.lunchEnd)) return Status.LUNCH
        return if (n < end) Status.WORKING else if (s.overtime) Status.OVERTIME else Status.AFTER
    }

    /** 今日已计薪分钟数 */
    fun paidElapsedMin(st: State, d: LocalDate, t: LocalTime): Double {
        if (!Holidays.isWorkday(d)) return 0.0
        val s = st.settings
        val n = t.hour * 60 + t.minute + t.second / 60.0
        val start = Dates.toMin(s.start); val end = Dates.toMin(s.end)
        var m = maxOf(0.0, minOf(n, end.toDouble()) - start)
        if (s.lunch) {
            val a = Dates.toMin(s.lunchStart); val b = Dates.toMin(s.lunchEnd)
            m -= maxOf(0.0, minOf(n, b.toDouble()) - maxOf(a, start))
        }
        if (s.overtime && n > end) m += n - end
        return maxOf(0.0, m)
    }

    fun todayEarnedCents(st: State, d: LocalDate, t: LocalTime): Long =
        Math.round(paidElapsedMin(st, d, t) * 60.0 * perSec(st, d) * 100.0)

    /** 距发薪日天数（当天为 0） */
    fun daysToPayday(st: State, d: LocalDate): Int {
        val p = (st.settings.payday).coerceIn(1, 31).let { minOf(it, d.lengthOfMonth()) }
        var target = d.withDayOfMonth(p)
        if (target == d) return 0
        if (target.isBefore(d)) {
            val next = d.plusMonths(1)
            target = next.withDayOfMonth(minOf(st.settings.payday.coerceIn(1, 31), next.lengthOfMonth()))
        }
        return java.time.temporal.ChronoUnit.DAYS.between(d, target).toInt()
    }

    /** 本月已计薪 = 已过工作日 × 全天工资 + 今日已计薪 */
    fun monthEstimate(st: State, d: LocalDate, todayCents: Long): Double {
        var wd = 0
        for (day in 1 until d.dayOfMonth)
            if (Holidays.isWorkday(d.withDayOfMonth(day))) wd++
        return wd * dailyPay(st, d) + todayCents / 100.0
    }

    /** 心愿换算：价格 → (小时, 分钟, 工作日数) */
    fun timeFor(st: State, price: Double, today: LocalDate): Triple<Int, Int, Double> {
        val hours = if (perHour(st, today) > 0) price / perHour(st, today) else 0.0
        var h = hours.toInt()
        var m = Math.round((hours - h) * 60).toInt()
        if (m == 60) { h++; m = 0 }
        val ph = paidHours(st.settings)
        val days = if (ph > 0) hours / ph else 0.0
        return Triple(h, m, days)
    }
}
