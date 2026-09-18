package com.salarydance.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 五险一金 · 个税配置（baseTier: full | p80 | p60 | custom） */
class TaxCfg(
    var enabled: Boolean = false,
    var fundRate: Double = 12.0,
    var socialRate: Double = 10.5,
    var threshold: Double = 5000.0,
    var baseCap: Double = 0.0,
    var baseTier: String = "full",
    var customBase: Double = 0.0,
    var coSocialRate: Double = 26.5,
    var coFundRate: Double = 12.0,
    var regionProv: String = "custom",
    var regionCity: String = "custom",
    var baseFloor: Double = 0.0,
)

class Settings(
    var salary: Double = 12000.0,
    var payday: Int = 10,
    var start: String = "09:00",
    var lunch: Boolean = true,
    var lunchStart: String = "12:00",
    var lunchEnd: String = "13:00",
    var end: String = "18:00",
    var overtime: Boolean = false,
    var daysOverride: Int? = null,
    var daysMonthKey: String = "",
    val tax: TaxCfg = TaxCfg(),
    var eco: Boolean = false,            // 低内存占用模式：降刷新率、关动画
    var remindEnabled: Boolean = false,  // 摸鱼提醒推送
    var remindMin: Int = 45,             // 提醒间隔（分钟）
    var iconDark: Boolean = false,       // 桌面图标深色版
)

class PetCfg(
    var id: String = "xiaoxin",
    var water: Boolean = true,
    var move: Boolean = true,
    var overtimeCare: Boolean = true,
    var reportMin: Int = 15,
)

class LeaveUse(val id: Long, val ts: Long, val amount: Double, val note: String)

class LeaveCfg(
    var perDay: Double = 0.25,
    var perDayUnit: String = "hour",
    var base: Double = 5.0,
    var baseUnit: String = "day",
    var baseDate: String? = null,
    var stdHours: Double = 8.0,
    val log: MutableList<LeaveUse> = mutableListOf(),
)

class Wish(val id: Long, var name: String, val price: Double, val baseline: Double,
           val createdAt: Long, var cheered: Boolean = false)

class BreakRec(val s: Long, val e: Long)

/** 某一年在线节假日数据（holiday-cn） */
class YearCal(val fetchedAt: Long, val off: Map<String, String>, val work: Map<String, String>)

/** 全量应用状态，字段与网页版 localStorage 结构对齐 */
class State {
    val settings = Settings()
    val pet = PetCfg()
    val leave = LeaveCfg()
    val cal = LinkedHashMap<String, YearCal>()
    val wishlist = mutableListOf<Wish>()
    val breaks = mutableListOf<BreakRec>()
    var activeBreak: Long? = null
    var lifetimeEarned = 0.0
    var monthEarned = 0.0
    var monthKey = ""
    var savedTodayEarned = 0.0
    var todayKey = ""
    var paydaySaidKey = ""
    var regionsCache: String? = null
}

/**
 * 本地持久化：SharedPreferences 存一份 JSON。
 * 读取时先建默认 State 再覆盖文件中存在的字段 —— 等价于网页版 deepMerge，
 * 旧版本数据缺的新字段自动落到默认值。
 */
object Store {
    private const val PREF = "salarydance"
    private const val KEY = "state.v1"
    private val lock = Any()

    var state: State = State()
        private set

    fun load(ctx: Context) {
        val raw = try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
        } catch (e: Exception) { null }
        state = try {
            if (raw != null) parse(JSONObject(raw)) else State()
        } catch (e: Exception) { State() }
    }

    fun save(ctx: Context) = synchronized(lock) {
        try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY, dump(state)).apply()
        } catch (e: Exception) { /* 存储失败不崩溃，内存态继续用 */ }
    }

    fun reset(ctx: Context) {
        state = State()
        save(ctx)
    }

    // ---------- 草稿（设置保存制） ----------

    /** 深拷贝一份状态作为设置草稿 */
    fun clone(s: State): State = parse(JSONObject(dump(s)))

    /** 把草稿配置写回正式状态（不动运行时记账与各类流水） */
    fun copyConfig(dst: State, src: State) {
        val d = dst.settings; val s = src.settings
        d.salary = s.salary; d.payday = s.payday
        d.start = s.start; d.lunch = s.lunch
        d.lunchStart = s.lunchStart; d.lunchEnd = s.lunchEnd
        d.end = s.end; d.overtime = s.overtime
        d.daysOverride = s.daysOverride; d.daysMonthKey = s.daysMonthKey
        d.eco = s.eco; d.remindEnabled = s.remindEnabled
        d.remindMin = s.remindMin; d.iconDark = s.iconDark
        val t = d.tax; val x = s.tax
        t.enabled = x.enabled; t.fundRate = x.fundRate; t.socialRate = x.socialRate
        t.threshold = x.threshold; t.baseCap = x.baseCap; t.baseTier = x.baseTier
        t.customBase = x.customBase; t.coSocialRate = x.coSocialRate; t.coFundRate = x.coFundRate
        t.regionProv = x.regionProv; t.regionCity = x.regionCity; t.baseFloor = x.baseFloor
        copyPet(dst.pet, src.pet)
        // 年假只复制攒假规则，使用记录 log 属于流水，保留 dst 自己的
        val dl = dst.leave; val sl = src.leave
        dl.perDay = sl.perDay; dl.perDayUnit = sl.perDayUnit
        dl.base = sl.base; dl.baseUnit = sl.baseUnit
        dl.baseDate = sl.baseDate; dl.stdHours = sl.stdHours
    }

    private fun copyPet(dst: PetCfg, src: PetCfg) {
        dst.id = src.id; dst.water = src.water; dst.move = src.move
        dst.overtimeCare = src.overtimeCare; dst.reportMin = src.reportMin
    }

    // ---------- 序列化 ----------

    fun dump(s: State): String {
        val o = JSONObject()
        o.put("settings", dumpSettings(s.settings))
        o.put("pet", JSONObject()
            .put("id", s.pet.id).put("water", s.pet.water).put("move", s.pet.move)
            .put("overtimeCare", s.pet.overtimeCare).put("reportMin", s.pet.reportMin))
        o.put("leave", JSONObject()
            .put("perDay", s.leave.perDay).put("perDayUnit", s.leave.perDayUnit)
            .put("base", s.leave.base).put("baseUnit", s.leave.baseUnit)
            .put("baseDate", s.leave.baseDate ?: JSONObject.NULL)
            .put("stdHours", s.leave.stdHours)
            .put("log", JSONArray().apply { s.leave.log.forEach {
                put(JSONObject().put("id", it.id).put("ts", it.ts)
                    .put("amount", it.amount).put("note", it.note)) } }))
        o.put("cal", JSONObject().apply { s.cal.forEach { (y, c) ->
            put(y, JSONObject().put("fetchedAt", c.fetchedAt)
                .put("off", JSONObject(c.off)).put("work", JSONObject(c.work))) } })
        o.put("wishlist", JSONArray().apply { s.wishlist.forEach {
            put(JSONObject().put("id", it.id).put("name", it.name).put("price", it.price)
                .put("baseline", it.baseline).put("createdAt", it.createdAt).put("cheered", it.cheered)) } })
        o.put("breaks", JSONArray().apply { s.breaks.forEach {
            put(JSONObject().put("s", it.s).put("e", it.e)) } })
        o.put("activeBreak", s.activeBreak ?: JSONObject.NULL)
        o.put("lifetimeEarned", s.lifetimeEarned)
        o.put("monthEarned", s.monthEarned)
        o.put("monthKey", s.monthKey)
        o.put("savedTodayEarned", s.savedTodayEarned)
        o.put("todayKey", s.todayKey)
        o.put("paydaySaidKey", s.paydaySaidKey)
        o.put("regionsCache", s.regionsCache ?: JSONObject.NULL)
        return o.toString()
    }

    private fun dumpSettings(m: Settings): JSONObject {
        val o = JSONObject()
            .put("salary", m.salary).put("payday", m.payday)
            .put("start", m.start).put("lunch", m.lunch)
            .put("lunchStart", m.lunchStart).put("lunchEnd", m.lunchEnd)
            .put("end", m.end).put("overtime", m.overtime)
            .put("daysOverride", m.daysOverride ?: JSONObject.NULL)
            .put("daysMonthKey", m.daysMonthKey)
            .put("eco", m.eco)
            .put("remindEnabled", m.remindEnabled)
            .put("remindMin", m.remindMin)
            .put("iconDark", m.iconDark)
        o.put("tax", JSONObject()
            .put("enabled", m.tax.enabled).put("fundRate", m.tax.fundRate)
            .put("socialRate", m.tax.socialRate).put("threshold", m.tax.threshold)
            .put("baseCap", m.tax.baseCap).put("baseTier", m.tax.baseTier)
            .put("customBase", m.tax.customBase)
            .put("coSocialRate", m.tax.coSocialRate).put("coFundRate", m.tax.coFundRate)
            .put("regionProv", m.tax.regionProv).put("regionCity", m.tax.regionCity)
            .put("baseFloor", m.tax.baseFloor))
        return o
    }

    private fun parse(o: JSONObject): State {
        val s = State()
        if (o.has("settings")) parseSettings(o.getJSONObject("settings"), s.settings)
        o.optJSONObject("pet")?.let { p ->
            s.pet.id = p.optString("id", s.pet.id)
            s.pet.water = p.optBoolean("water", s.pet.water)
            s.pet.move = p.optBoolean("move", s.pet.move)
            s.pet.overtimeCare = p.optBoolean("overtimeCare", s.pet.overtimeCare)
            s.pet.reportMin = p.optInt("reportMin", s.pet.reportMin)
        }
        o.optJSONObject("leave")?.let { l ->
            s.leave.perDay = l.optDouble("perDay", s.leave.perDay)
            s.leave.perDayUnit = l.optString("perDayUnit", s.leave.perDayUnit)
            s.leave.base = l.optDouble("base", s.leave.base)
            s.leave.baseUnit = l.optString("baseUnit", s.leave.baseUnit)
            if (l.has("baseDate") && !l.isNull("baseDate")) s.leave.baseDate = l.getString("baseDate")
            s.leave.stdHours = l.optDouble("stdHours", s.leave.stdHours)
            l.optJSONArray("log")?.forEachObj { it2 ->
                s.leave.log.add(LeaveUse(it2.optLong("id"), it2.optLong("ts"),
                    it2.optDouble("amount", 0.0), it2.optString("note", "")))
            }
        }
        o.optJSONObject("cal")?.let { cal ->
            cal.keys().forEach { y ->
                val c = cal.getJSONObject(y)
                val off = HashMap<String, String>()
                val work = HashMap<String, String>()
                c.optJSONObject("off")?.let { j -> j.keys().forEach { k -> off[k] = j.getString(k) } }
                c.optJSONObject("work")?.let { j -> j.keys().forEach { k -> work[k] = j.getString(k) } }
                s.cal[y] = YearCal(c.optLong("fetchedAt"), off, work)
            }
        }
        o.optJSONArray("wishlist")?.forEachObj { it2 ->
            s.wishlist.add(Wish(it2.optLong("id"), it2.optString("name", ""),
                it2.optDouble("price", 0.0), it2.optDouble("baseline", 0.0),
                it2.optLong("createdAt"), it2.optBoolean("cheered", false)))
        }
        o.optJSONArray("breaks")?.forEachObj { it2 ->
            s.breaks.add(BreakRec(it2.optLong("s"), it2.optLong("e")))
        }
        if (!o.isNull("activeBreak")) s.activeBreak = o.optLong("activeBreak")
        s.lifetimeEarned = o.optDouble("lifetimeEarned", 0.0)
        s.monthEarned = o.optDouble("monthEarned", 0.0)
        s.monthKey = o.optString("monthKey", "")
        s.savedTodayEarned = o.optDouble("savedTodayEarned", 0.0)
        s.todayKey = o.optString("todayKey", "")
        s.paydaySaidKey = o.optString("paydaySaidKey", "")
        if (!o.isNull("regionsCache")) s.regionsCache = o.optString("regionsCache")
        return s
    }

    private fun parseSettings(o: JSONObject, m: Settings) {
        m.salary = o.optDouble("salary", m.salary)
        m.payday = o.optInt("payday", m.payday)
        m.start = o.optString("start", m.start)
        m.lunch = o.optBoolean("lunch", m.lunch)
        m.lunchStart = o.optString("lunchStart", m.lunchStart)
        m.lunchEnd = o.optString("lunchEnd", m.lunchEnd)
        m.end = o.optString("end", m.end)
        m.overtime = o.optBoolean("overtime", m.overtime)
        if (!o.isNull("daysOverride")) m.daysOverride = o.optInt("daysOverride")
        m.daysMonthKey = o.optString("daysMonthKey", "")
        m.eco = o.optBoolean("eco", m.eco)
        m.remindEnabled = o.optBoolean("remindEnabled", m.remindEnabled)
        m.remindMin = o.optInt("remindMin", m.remindMin)
        m.iconDark = o.optBoolean("iconDark", m.iconDark)
        o.optJSONObject("tax")?.let { t ->
            m.tax.enabled = t.optBoolean("enabled", m.tax.enabled)
            m.tax.fundRate = t.optDouble("fundRate", m.tax.fundRate)
            m.tax.socialRate = t.optDouble("socialRate", m.tax.socialRate)
            m.tax.threshold = t.optDouble("threshold", m.tax.threshold)
            m.tax.baseCap = t.optDouble("baseCap", m.tax.baseCap)
            m.tax.baseTier = t.optString("baseTier", m.tax.baseTier)
            m.tax.customBase = t.optDouble("customBase", m.tax.customBase)
            m.tax.coSocialRate = t.optDouble("coSocialRate", m.tax.coSocialRate)
            m.tax.coFundRate = t.optDouble("coFundRate", m.tax.coFundRate)
            m.tax.regionProv = t.optString("regionProv", m.tax.regionProv)
            m.tax.regionCity = t.optString("regionCity", m.tax.regionCity)
            m.tax.baseFloor = t.optDouble("baseFloor", m.tax.baseFloor)
        }
    }

    private fun JSONArray.forEachObj(f: (JSONObject) -> Unit) {
        for (i in 0 until length()) {
            val v = optJSONObject(i) ?: continue
            f(v)
        }
    }
}
