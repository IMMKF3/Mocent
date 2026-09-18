package com.salarydance.app

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** 日期/时间小工具 */
object Dates {
    fun dateKey(d: LocalDate): String =
        "%04d-%02d-%02d".format(d.year, d.monthValue, d.dayOfMonth)

    fun parseKey(s: String): LocalDate {
        val p = s.split("-").map { it.toInt() }
        return LocalDate.of(p[0], p[1], p[2])
    }

    fun monthKey(d: LocalDate): String = "%04d-%02d".format(d.year, d.monthValue)

    /** "HH:mm" → 当天分钟数，容错返回 0 */
    fun toMin(t: String?): Int {
        if (t.isNullOrBlank()) return 0
        val p = t.split(":")
        val h = p.getOrNull(0)?.toIntOrNull() ?: return 0
        val m = p.getOrNull(1)?.toIntOrNull() ?: 0
        return h * 60 + m
    }
}

/**
 * 法定节假日 + 工作日引擎。
 * 数据来源：国务院办公厅公告（off=放假, work=调休上班）。
 * 内置 2025–2026 离线兜底；state.cal 里 holiday-cn 在线数据优先。
 */
object Holidays {
    private class Raw(val y: Int, val off: Array<Array<String>>, val work: Array<Array<String>>)

    private val RAW = listOf(
        Raw(2026, arrayOf( // 国办发明电〔2025〕7号
            arrayOf("01-01", "01-03", "元旦"), arrayOf("02-15", "02-23", "春节"),
            arrayOf("04-04", "04-06", "清明节"), arrayOf("05-01", "05-05", "劳动节"),
            arrayOf("06-19", "06-21", "端午节"), arrayOf("09-25", "09-27", "中秋节"),
            arrayOf("10-01", "10-07", "国庆节")),
            arrayOf(arrayOf("01-04", "元旦"), arrayOf("02-14", "春节"), arrayOf("02-28", "春节"),
                arrayOf("05-09", "劳动节"), arrayOf("09-20", "国庆"), arrayOf("10-10", "国庆")))
    )

    // 年份 → (off, work)，在线数据在前优先命中
    @Volatile
    private var years: List<Triple<Int, Map<String, String>, Map<String, String>>> = emptyList()

    @Synchronized
    fun rebuild(cal: Map<String, YearCal>) {
        val list = mutableListOf<Triple<Int, Map<String, String>, Map<String, String>>>()
        val seen = mutableSetOf<Int>()
        cal.keys.mapNotNull { it.toIntOrNull() }.sorted().forEach { y ->
            val c = cal[y.toString()] ?: return@forEach
            if (c.off.isNotEmpty()) { list.add(Triple(y, c.off, c.work)); seen.add(y) }
        }
        for (r in RAW) {
            if (r.y in seen) continue
            val off = HashMap<String, String>()
            val work = HashMap<String, String>()
            for ((a, b, name) in r.off) {
                val am = a.substring(0, 2).toInt(); val ad = a.substring(3, 5).toInt()
                val bm = b.substring(0, 2).toInt(); val bd = b.substring(3, 5).toInt()
                var d = LocalDate.of(r.y, am, ad)
                val end = LocalDate.of(r.y, bm, bd)
                while (!d.isAfter(end)) { off[Dates.dateKey(d)] = name; d = d.plusDays(1) }
            }
            for ((a, name) in r.work) work[r.y.toString() + "-" + a] = name
            list.add(Triple(r.y, off, work))
        }
        years = list
    }

    /** (名称, 是否放假)；不在任何节假日表时返回 null */
    fun info(dateKey: String): Pair<String, Boolean>? {
        for ((_, off, work) in years) {
            off[dateKey]?.let { return it to true }
            work[dateKey]?.let { return (it + "调休上班") to false }
        }
        return null
    }

    fun isWorkday(d: LocalDate): Boolean {
        info(Dates.dateKey(d))?.let { return !it.second }
        val dow = d.dayOfWeek.value // 1=周一
        return dow <= 5
    }

    fun countWorkdays(from: LocalDate, to: LocalDate): Int {
        var c = 0
        var d = from
        while (!d.isAfter(to)) { if (isWorkday(d)) c++; d = d.plusDays(1) }
        return c
    }

    class Breakdown(
        val weekdays: Int, val hol: Int, val makeup: Int,
        val net: Int, val names: List<String>, val hasData: Boolean,
    )

    fun breakdown(d: LocalDate): Breakdown {
        val dim = d.lengthOfMonth()
        var weekdays = 0; var hol = 0; var makeup = 0
        val names = mutableListOf<String>()
        for (day in 1..dim) {
            val dt = d.withDayOfMonth(day)
            val isWd = dt.dayOfWeek.value <= 5
            if (isWd) weekdays++
            info(Dates.dateKey(dt))?.let { (name, off) ->
                if (off && isWd) { hol++; if (name !in names) names.add(name) }
                if (!off) makeup++
            }
        }
        return Breakdown(weekdays, hol, makeup, weekdays - hol + makeup, names, hasData(d.year))
    }

    fun hasData(year: Int): Boolean = years.any { it.first == year }

    /** 联网更新当年节假日（holiday-cn，双源兜底），结果回调在主线程 */
    fun refresh(st: State, onDone: (Boolean, String) -> Unit) {
        val year = LocalDate.now().year
        val urls = listOf(
            "https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/$year.json",
            "https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/$year.json",
        )
        Thread {
            var ok = false
            var msg = "暂时连不上节假日数据源，先用内置/缓存数据 📴"
            for (u in urls) {
                try {
                    val body = httpGet(u) ?: continue
                    val j = JSONObject(body)
                    if (j.optInt("year") != year) continue
                    val days = j.optJSONArray("days") ?: continue
                    if (days.length() == 0) continue
                    val off = HashMap<String, String>()
                    val work = HashMap<String, String>()
                    for (i in 0 until days.length()) {
                        val d = days.getJSONObject(i)
                        val date = d.optString("date")
                        if (date.isEmpty()) continue
                        val name = d.optString("name", "")
                        if (d.optBoolean("isOffDay")) off[date] = if (name.isEmpty()) "法定节假日" else name
                        else work[date] = if (name.isEmpty()) "调休" else name
                    }
                    synchronized(st) {
                        st.cal[year.toString()] = YearCal(System.currentTimeMillis(), off, work)
                    }
                    rebuild(st.cal)
                    msg = "节假日数据已更新到 $year 年最新公告 ✅"
                    ok = true
                    break
                } catch (e: Exception) { /* 换下一个源 */ }
            }
            Handler(Looper.getMainLooper()).post { onDone(ok, msg) }
        }.start()
    }

    private fun httpGet(url: String): String? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        conn.requestMethod = "GET"
        try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
    } catch (e: Exception) { null }
}
