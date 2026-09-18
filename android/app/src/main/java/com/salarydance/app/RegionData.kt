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

    private val DEFAULT_PROV: Map<String, DoubleArray> = linkedMapOf(
        "北京" to doubleArrayOf(7162.0, 35811.0, 10.5, 26.7, 12.0, 12.0),
        "天津" to doubleArrayOf(5124.0, 25620.0, 10.5, 26.3, 12.0, 12.0),
        "上海" to doubleArrayOf(7460.0, 37302.0, 10.5, 27.0, 12.0, 12.0),
        "重庆" to doubleArrayOf(4403.0, 22017.0, 10.5, 25.5, 12.0, 12.0),
        "河北" to doubleArrayOf(4007.0, 20034.0, 10.5, 25.0, 12.0, 12.0),
        "山西" to doubleArrayOf(4198.0, 20991.0, 10.5, 25.0, 12.0, 12.0),
        "内蒙古" to doubleArrayOf(4907.0, 24537.0, 10.5, 24.5, 12.0, 12.0),
        "辽宁" to doubleArrayOf(4358.0, 21792.0, 10.5, 25.0, 12.0, 12.0),
        "吉林" to doubleArrayOf(4393.0, 21966.0, 10.5, 24.5, 12.0, 12.0),
        "黑龙江" to doubleArrayOf(4542.0, 22710.0, 10.5, 24.5, 12.0, 12.0),
        "江苏" to doubleArrayOf(4952.0, 24762.0, 10.5, 25.5, 12.0, 12.0),
        "浙江" to doubleArrayOf(5060.0, 25299.0, 10.5, 24.0, 12.0, 12.0),
        "安徽" to doubleArrayOf(4311.0, 21555.0, 10.5, 24.5, 12.0, 12.0),
        "福建" to doubleArrayOf(4521.0, 22605.0, 10.5, 24.0, 12.0, 12.0),
        "江西" to doubleArrayOf(3915.0, 19575.0, 10.5, 24.0, 12.0, 12.0),
        "山东" to doubleArrayOf(4504.0, 22518.0, 10.5, 25.0, 12.0, 12.0),
        "河南" to doubleArrayOf(3831.0, 19155.0, 10.5, 24.5, 12.0, 12.0),
        "湖北" to doubleArrayOf(4869.0, 24356.0, 10.5, 25.0, 12.0, 12.0),
        "湖南" to doubleArrayOf(4072.0, 20361.0, 10.5, 24.5, 12.0, 12.0),
        "广东" to doubleArrayOf(4580.0, 26421.0, 10.2, 22.5, 12.0, 12.0),
        "广西" to doubleArrayOf(4143.0, 20715.0, 10.5, 23.5, 12.0, 12.0),
        "海南" to doubleArrayOf(4913.0, 24564.0, 10.5, 24.0, 12.0, 12.0),
        "四川" to doubleArrayOf(4588.0, 22938.0, 10.5, 23.5, 12.0, 12.0),
        "贵州" to doubleArrayOf(4395.0, 21974.0, 10.5, 24.0, 12.0, 12.0),
        "云南" to doubleArrayOf(4358.0, 21789.0, 10.5, 24.5, 12.0, 12.0),
        "西藏" to doubleArrayOf(7066.0, 35331.0, 10.5, 25.5, 12.0, 12.0),
        "陕西" to doubleArrayOf(4650.0, 23250.0, 10.5, 25.2, 12.0, 12.0),
        "甘肃" to doubleArrayOf(4403.0, 22014.0, 10.5, 24.5, 12.0, 12.0),
        "青海" to doubleArrayOf(5290.0, 26448.0, 10.5, 24.5, 12.0, 12.0),
        "宁夏" to doubleArrayOf(4955.0, 24774.0, 10.5, 25.0, 12.0, 12.0),
        "新疆" to doubleArrayOf(5069.0, 25344.0, 10.5, 24.5, 12.0, 12.0))

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
