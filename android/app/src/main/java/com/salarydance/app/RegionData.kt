package com.salarydance.app

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 全国地区五险一金参考参数。
 * 数据外置在 web/regions.json（随仓库更新，启动时在线拉取，见 holiday-cn 同款模式），
 * 内置 DEFAULT_PROV 仅作离线兜底；发现数据过时改 JSON 推送即可，无需发版。
 * 数组顺序: [基数下限, 基数上限, 个人社保%, 公司社保%, 个人公积金%, 公司公积金%]
 */
object RegionData {
    const val REMOTE_URL = "https://immkf3.github.io/Mocent/regions.json"

    /** 各险种比例 [个人%, 单位%]，顺序：养老 / 医疗 / 失业 / 工伤 / 生育 */
    val ITEM_KEYS = listOf("pension", "medical", "unemploy", "injury", "maternity")
    val ITEM_NAMES = listOf("养老", "医疗", "失业", "工伤", "生育")

    /** 查不到省级数据时的全国常见兜底 */
    val DEFAULT_BREAKDOWN: Map<String, DoubleArray> = linkedMapOf(
        "pension" to doubleArrayOf(8.0, 16.0), "medical" to doubleArrayOf(2.0, 6.0),
        "unemploy" to doubleArrayOf(0.5, 0.5), "injury" to doubleArrayOf(0.0, 0.4),
        "maternity" to doubleArrayOf(0.0, 0.5))

    private val DEFAULT_PROV: Map<String, DoubleArray> = linkedMapOf(
        "北京" to doubleArrayOf(7270.0, 36348.0, 10.5, 26.7, 12.0, 12.0),
        "天津" to doubleArrayOf(5180.0, 25902.0, 10.5, 26.3, 12.0, 12.0),
        "上海" to doubleArrayOf(7546.0, 37731.0, 10.5, 27.0, 12.0, 12.0),
        "重庆" to doubleArrayOf(4403.0, 22017.0, 10.5, 25.5, 12.0, 12.0),
        "河北" to doubleArrayOf(4076.0, 20382.0, 10.5, 25.0, 12.0, 12.0),
        "山西" to doubleArrayOf(4244.0, 21219.0, 10.5, 25.0, 12.0, 12.0),
        "内蒙古" to doubleArrayOf(4907.0, 24537.0, 10.5, 24.5, 12.0, 12.0),
        "辽宁" to doubleArrayOf(4533.0, 22665.0, 10.5, 25.0, 12.0, 12.0),
        "吉林" to doubleArrayOf(4393.0, 21966.0, 10.5, 24.5, 12.0, 12.0),
        "黑龙江" to doubleArrayOf(4542.0, 22710.0, 10.5, 24.5, 12.0, 12.0),
        "江苏" to doubleArrayOf(4952.0, 24762.0, 10.5, 25.5, 12.0, 12.0),
        "浙江" to doubleArrayOf(4986.0, 25299.0, 10.5, 24.0, 12.0, 12.0),
        "安徽" to doubleArrayOf(4354.0, 21772.0, 10.5, 24.5, 12.0, 12.0),
        "福建" to doubleArrayOf(4579.0, 22893.0, 10.5, 24.0, 12.0, 12.0),
        "江西" to doubleArrayOf(3915.0, 19575.0, 10.5, 24.0, 12.0, 12.0),
        "山东" to doubleArrayOf(4573.0, 22863.0, 10.5, 25.0, 12.0, 12.0),
        "河南" to doubleArrayOf(3831.0, 19155.0, 10.5, 24.5, 12.0, 12.0),
        "湖北" to doubleArrayOf(4498.0, 22488.0, 10.5, 25.0, 12.0, 12.0),
        "湖南" to doubleArrayOf(4106.0, 20529.0, 10.5, 24.5, 12.0, 12.0),
        "广东" to doubleArrayOf(5510.0, 27549.0, 10.2, 22.5, 12.0, 12.0),
        "广西" to doubleArrayOf(4143.0, 20715.0, 10.5, 23.5, 12.0, 12.0),
        "海南" to doubleArrayOf(4912.8, 24564.0, 10.5, 24.0, 12.0, 12.0),
        "四川" to doubleArrayOf(4699.0, 23493.0, 10.5, 23.5, 12.0, 12.0),
        "贵州" to doubleArrayOf(4395.0, 21974.0, 10.5, 24.0, 12.0, 12.0),
        "云南" to doubleArrayOf(4357.0, 21789.0, 10.5, 24.5, 12.0, 12.0),
        "西藏" to doubleArrayOf(7172.4, 35862.0, 10.5, 25.5, 12.0, 12.0),
        "陕西" to doubleArrayOf(4650.0, 23250.0, 10.5, 25.2, 12.0, 12.0),
        "甘肃" to doubleArrayOf(4526.0, 22626.0, 10.5, 24.5, 12.0, 12.0),
        "青海" to doubleArrayOf(5333.0, 26448.0, 10.5, 24.5, 12.0, 12.0),
        "宁夏" to doubleArrayOf(5023.0, 25113.0, 10.5, 25.0, 12.0, 12.0),
        "新疆" to doubleArrayOf(5246.0, 26231.0, 10.5, 24.5, 12.0, 12.0))

    /** 计划单列/口径特殊城市覆盖：深圳、东莞为广东养老二类地区，下限 4775、全省上限 27549 */
    val CITY_FIX: Map<String, DoubleArray> = linkedMapOf(
        "深圳" to doubleArrayOf(4775.0, 27549.0, 10.2, 21.6, 12.0, 12.0),
        "东莞" to doubleArrayOf(4775.0, 27549.0, 10.2, 21.6, 12.0, 12.0))

    /** 医保基数与养老基数不同的地区（反推时必须用医保口径）：下限 / 上限，cap=0 表示不限
     *  内置兜底值，远端 regions.json 的 medicalBase 段会覆盖它 */
    private val MED_BASE: Map<String, DoubleArray> = linkedMapOf(
        "河北" to doubleArrayOf(6794.0, 20382.0), "内蒙古" to doubleArrayOf(6744.0, 25290.0),
        "福建" to doubleArrayOf(4579.0, 22893.0), "浙江" to doubleArrayOf(4986.0, 25299.0),
        "深圳" to doubleArrayOf(6727.0, 33633.0))

    /** 合并远端 medicalBase 段：兼容 {floor,cap} 与 [floor,cap] 两种写法，返回合并条数 */
    private fun mergeMedicalBase(cache: String?): Int {
        if (cache == null) return 0
        var hit = 0
        try {
            val mb = JSONObject(cache).optJSONObject("medicalBase") ?: return 0
            mb.keys().forEach { key ->
                val a = mb.optJSONArray(key)
                val floor: Double
                val cap: Double
                if (a != null) {
                    floor = a.optDouble(0, 0.0); cap = a.optDouble(1, 0.0)
                } else {
                    val o = mb.optJSONObject(key) ?: return@forEach
                    floor = o.optDouble("floor", 0.0); cap = o.optDouble("cap", 0.0)
                }
                if (floor > 0 || cap > 0) { MED_BASE_MERGED[key] = doubleArrayOf(floor, cap); hit++ }
            }
        } catch (e: Exception) { }
        return hit
    }

    /** 远端覆盖后的医保基数表（每次查询前刷新，避免启动顺序问题） */
    private val MED_BASE_MERGED: MutableMap<String, DoubleArray> = LinkedHashMap()

    /** 反推用上下限：医保基数地区用医保口径，否则用当地养老口径现算
     *  （不能直接用 s.tax.baseFloor/baseCap —— 那可能是切地区前的旧值或自定义留下的 0） */
    private fun medicalBase(st: State, prov: String, city: String): DoubleArray {
        mergeMedicalBase(st.regionsCache)
        MED_BASE_MERGED[city]?.let { return it }
        MED_BASE_MERGED[prov]?.let { return it }
        MED_BASE[city]?.let { return it }
        MED_BASE[prov]?.let { return it }
        val v = provinces(st)[city] ?: provinces(st)[prov]
        return v ?: doubleArrayOf(st.settings.tax.baseFloor, st.settings.tax.baseCap)
    }

    /** 内置五险比例兜底（与 web/regions.json 的 breakdown 保持一致） */
    private val DEFAULT_BREAKDOWN_BY_REGION: Map<String, Map<String, DoubleArray>> = linkedMapOf(
        "北京" to bd(8, 16, 2, 9, 0.5, 0.5, 0, 0.4, 0, 0.8),
        "天津" to bd(8, 16, 2, 8, 0.5, 0.5, 0, 0.4, 0, 0.5),
        "上海" to bd(8, 16, 2, 9.5, 0.5, 0.5, 0, 0.32, 0, 1),
        "重庆" to bd(8, 16, 2, 8, 0.5, 0.5, 0, 0.6, 0, 0.5),
        "河北" to bd(8, 16, 2, 6.5, 0.3, 0.7, 0, 0.5, 0, 0.5),
        "山西" to bd(8, 16, 2, 7, 0.3, 0.7, 0, 0.5, 0, 0.5),
        "内蒙古" to bd(8, 16, 2, 6, 0.5, 0.5, 0, 0.4, 0, 0.5),
        "辽宁" to bd(8, 16, 2, 6, 0.5, 0.5, 0, 0.5, 0, 0.5),
        "吉林" to bd(8, 16, 2, 6, 0.5, 0.5, 0, 0.4, 0, 0.5),
        "黑龙江" to bd(8, 16, 2, 6, 0.5, 0.5, 0, 0.5, 0, 0.5),
        "江苏" to bd(8, 16, 2, 7, 0.5, 0.5, 0, 0.4, 0, 0.8),
        "浙江" to bd(8, 16, 2, 9.5, 0.5, 0.5, 0, 0.4, 0, 0.5),
        "安徽" to bd(8, 16, 2, 6.4, 0.5, 0.5, 0, 0.4, 0, 0.5),
        "福建" to bd(8, 16, 2, 6, 0.5, 0.5, 0, 0.4, 0, 0.7),
        "江西" to bd(8, 16, 2, 6, 0.5, 0.5, 0, 0.4, 0, 0.5),
        "山东" to bd(8, 16, 2, 7, 0.3, 0.7, 0, 0.4, 0, 1),
        "河南" to bd(8, 16, 2, 6, 0.3, 0.7, 0, 0.4, 0, 1),
        "湖北" to bd(8, 16, 2, 6, 0.3, 0.7, 0, 0.4, 0, 0.7),
        "湖南" to bd(8, 16, 2, 6, 0.5, 0.5, 0, 0.4, 0, 0.7),
        "广东" to bd(8, 16, 2, 5.5, 0.2, 0.8, 0, 0.4, 0, 0.5),
        "广西" to bd(8, 16, 2, 6, 0.5, 0.5, 0, 0.4, 0, 0.5),
        "海南" to bd(8, 14, 2, 6, 0.5, 0.5, 0, 0.4, 0, 0.5),
        "四川" to bd(8, 16, 2, 6, 0.4, 0.6, 0, 0.4, 0, 0.5),
        "贵州" to bd(8, 16, 2, 6, 0.5, 0.5, 0, 0.4, 0, 0.5),
        "云南" to bd(8, 16, 2, 6, 0.3, 0.7, 0, 0.4, 0, 0.5),
        "西藏" to bd(8, 16, 2, 6, 0.5, 0.5, 0, 0.4, 0, 0.5),
        "陕西" to bd(8, 16, 2, 6, 0.5, 0.5, 0, 0.4, 0, 0.5),
        "甘肃" to bd(8, 16, 2, 6, 0.5, 0.5, 0, 0.4, 0, 0.5),
        "青海" to bd(8, 16, 2, 6, 0.5, 0.5, 0, 0.4, 0, 0.5),
        "宁夏" to bd(8, 16, 2, 6, 0.5, 0.5, 0, 0.4, 0, 0.5),
        "新疆" to bd(8, 16, 2, 6, 0.5, 0.5, 0, 0.4, 0, 0.5),
        "深圳" to bd(8, 15, 2, 5.2, 0.2, 0.8, 0, 0.14, 0, 0.45))

    /** 构建某个地区的分险种比例表。
     *  参数用 Number 而非 Double —— Kotlin 不会把 Int 字面量（如 8、16）自动提升为 Double，
     *  写成 Double 会导致下面 30 多行 bd(8, 16, ...) 全部编译报错。 */
    private fun bd(p1: Number, p2: Number, m1: Number, m2: Number,
                   u1: Number, u2: Number, i1: Number, i2: Number,
                   ma1: Number, ma2: Number): Map<String, DoubleArray> = linkedMapOf(
        "pension" to doubleArrayOf(p1.toDouble(), p2.toDouble()),
        "medical" to doubleArrayOf(m1.toDouble(), m2.toDouble()),
        "unemploy" to doubleArrayOf(u1.toDouble(), u2.toDouble()),
        "injury" to doubleArrayOf(i1.toDouble(), i2.toDouble()),
        "maternity" to doubleArrayOf(ma1.toDouble(), ma2.toDouble()))

    /** 内置兜底 + 远端缓存覆盖 */
    fun provinces(st: State): Map<String, DoubleArray> {
        val out = LinkedHashMap(DEFAULT_PROV)
        val cache = st.regionsCache ?: return out
        return try {
            val provs = JSONObject(cache).getJSONObject("provinces")
            provs.keys().forEach { key ->
                val arr = provs.optJSONArray(key) ?: return@forEach
                if (arr.length() == 6 && out.containsKey(key))
                    out[key] = doubleArrayOf(arr.optDouble(0), arr.optDouble(1), arr.optDouble(2),
                        arr.optDouble(3), arr.optDouble(4), arr.optDouble(5))
            }
            out
        } catch (e: Exception) { out }
    }

    /** 各险种比例：内置兜底 + 远端 breakdown 覆盖 */
    fun breakdown(st: State, prov: String, city: String): Map<String, DoubleArray> {
        val out = LinkedHashMap<String, DoubleArray>()
        val base = DEFAULT_BREAKDOWN_BY_REGION[city] ?: DEFAULT_BREAKDOWN_BY_REGION[prov]
        for (k in ITEM_KEYS) out[k] = (base?.get(k) ?: DEFAULT_BREAKDOWN.getValue(k)).copyOf()
        val cache = st.regionsCache ?: return out
        try {
            val bd = JSONObject(cache).optJSONObject("breakdown") ?: return out
            val node = bd.optJSONObject(city) ?: bd.optJSONObject(prov) ?: return out
            for (k in ITEM_KEYS) {
                val a = node.optJSONArray(k) ?: continue
                if (a.length() == 2) out[k] = doubleArrayOf(a.optDouble(0), a.optDouble(1))
            }
        } catch (e: Exception) { }
        return out
    }

    /** 远端数据日期（用于设置页展示），取不到返回 null */
    fun remoteUpdatedAt(st: State): String? {
        val cache = st.regionsCache ?: return null
        return try { JSONObject(cache).optString("updatedAt").ifEmpty { null } } catch (e: Exception) { null }
    }

    /**
     * 由「医保每月缴纳总额」反推缴费基数与社保总比例。
     * 逻辑：基数 = 医保总额 ÷（医疗个人% + 医疗单位%）；再用各险种比例算金额，各项相加得社保总比例。
     * 与 web/index.html 的 reverseFromMedical() 保持一致。
     */
    data class Reverse(val base: Double, val raw: Double, val clamped: Boolean,
                       val socialP: Double, val socialC: Double,
                       val rows: List<Row>, val medicalAccount: Double, val medRate: Double,
                       val medFloor: Double, val medCap: Double) {
        data class Row(val name: String, val rp: Double, val rc: Double, val pay: Double, val comp: Double)
    }

    fun reverseFromMedical(st: State, prov: String, city: String, total: Double): Reverse? {
        if (total <= 0) return null
        val b = breakdown(st, prov, city)
        val med = b.getValue("medical")
        val medRate = med[0] + med[1]
        if (medRate <= 0) return null
        val s = st.settings
        val raw = total / (medRate / 100.0)
        // 河北/内蒙古/福建/浙江/深圳的医保基数与养老基数不同，反推按医保口径（含远端覆盖）
        val lim = medicalBase(st, prov, city)
        val mFloor = lim[0]
        val mCap = lim[1]
        var base = raw; var clamped = false
        if (mFloor > 0 && base < mFloor) { base = mFloor; clamped = true }
        if (mCap > 0 && base > mCap) { base = mCap; clamped = true }
        var sp = 0.0; var sc = 0.0
        val rows = ITEM_KEYS.mapIndexed { i, k ->
            val r = b.getValue(k); sp += r[0]; sc += r[1]
            Reverse.Row(ITEM_NAMES[i], r[0], r[1], base * r[0] / 100.0, base * r[1] / 100.0)
        }
        return Reverse(base, raw, clamped, sp, sc, rows, base * med[0] / 100.0, medRate, mFloor, mCap)
    }

    /** 启动时拉取最新 regions.json（失败静默沿用内置/缓存），结果回调在主线程 */
    fun refresh(st: State, onDone: () -> Unit) {
        Thread {
            var ok = false
            try {
                val conn = URL(REMOTE_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                try {
                    if (conn.responseCode in 200..299) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        JSONObject(body).getJSONObject("provinces")   // 结构校验
                        synchronized(st) { st.regionsCache = body }
                        ok = true
                    }
                } finally { conn.disconnect() }
            } catch (e: Exception) { }
            Handler(Looper.getMainLooper()).post { if (ok) onDone() }
        }.start()
    }
}
