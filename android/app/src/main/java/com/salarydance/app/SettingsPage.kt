package com.salarydance.app

import android.app.Activity
import android.app.TimePickerDialog
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import java.time.LocalDate

class SettingsPage(private val act: MainActivity) {
    // 注意：build() 会用到，必须先于 view 初始化（属性按声明顺序初始化）
    private val TIER_VALUES = listOf("full", "p80", "p60", "custom")
    private val TIER_LABELS = listOf("足额 · 按工资全额", "80% 档", "60% 档 · 常见“最低档”", "自定义基数")

    // 全国地区五险一金参考值：数据由 RegionData 提供（内置兜底 + 在线 regions.json 覆盖）
    private val PROV: Map<String, DoubleArray> get() = RegionData.provinces(act.st)
    private val PROV_CITIES: Map<String, List<String>> = linkedMapOf(
        "北京" to listOf("北京"), "天津" to listOf("天津"), "上海" to listOf("上海"), "重庆" to listOf("重庆"),
        "河北" to listOf("石家庄", "唐山", "秦皇岛", "邯郸", "邢台", "保定", "张家口", "承德", "沧州", "廊坊", "衡水"),
        "山西" to listOf("太原", "大同", "阳泉", "长治", "晋城", "朔州", "晋中", "运城", "忻州", "临汾", "吕梁"),
        "内蒙古" to listOf("呼和浩特", "包头", "乌海", "赤峰", "通辽", "鄂尔多斯", "呼伦贝尔", "巴彦淖尔", "乌兰察布", "兴安盟", "锡林郭勒盟", "阿拉善盟"),
        "辽宁" to listOf("沈阳", "大连", "鞍山", "抚顺", "本溪", "丹东", "锦州", "营口", "阜新", "辽阳", "盘锦", "铁岭", "朝阳", "葫芦岛"),
        "吉林" to listOf("长春", "吉林", "四平", "辽源", "通化", "白山", "松原", "白城", "延边"),
        "黑龙江" to listOf("哈尔滨", "齐齐哈尔", "鸡西", "鹤岗", "双鸭山", "大庆", "伊春", "佳木斯", "七台河", "牡丹江", "黑河", "绥化", "大兴安岭"),
        "江苏" to listOf("南京", "无锡", "徐州", "常州", "苏州", "南通", "连云港", "淮安", "盐城", "扬州", "镇江", "泰州", "宿迁"),
        "浙江" to listOf("杭州", "宁波", "温州", "嘉兴", "湖州", "绍兴", "金华", "衢州", "舟山", "台州", "丽水"),
        "安徽" to listOf("合肥", "芜湖", "蚌埠", "淮南", "马鞍山", "淮北", "铜陵", "安庆", "黄山", "滁州", "阜阳", "宿州", "六安", "亳州", "池州", "宣城"),
        "福建" to listOf("福州", "厦门", "莆田", "三明", "泉州", "漳州", "南平", "龙岩", "宁德"),
        "江西" to listOf("南昌", "景德镇", "萍乡", "九江", "新余", "鹰潭", "赣州", "吉安", "宜春", "抚州", "上饶"),
        "山东" to listOf("济南", "青岛", "淄博", "枣庄", "东营", "烟台", "潍坊", "济宁", "泰安", "威海", "日照", "临沂", "德州", "聊城", "滨州", "菏泽"),
        "河南" to listOf("郑州", "开封", "洛阳", "平顶山", "安阳", "鹤壁", "新乡", "焦作", "濮阳", "许昌", "漯河", "三门峡", "南阳", "商丘", "信阳", "周口", "驻马店"),
        "湖北" to listOf("武汉", "黄石", "十堰", "宜昌", "襄阳", "鄂州", "荆门", "孝感", "荆州", "黄冈", "咸宁", "随州", "恩施"),
        "湖南" to listOf("长沙", "株洲", "湘潭", "衡阳", "邵阳", "岳阳", "常德", "张家界", "益阳", "郴州", "永州", "怀化", "娄底", "湘西"),
        "广东" to listOf("广州", "韶关", "深圳", "珠海", "汕头", "佛山", "江门", "湛江", "茂名", "肇庆", "惠州", "梅州", "汕尾", "河源", "阳江", "清远", "东莞", "中山", "潮州", "揭阳", "云浮"),
        "广西" to listOf("南宁", "柳州", "桂林", "梧州", "北海", "防城港", "钦州", "贵港", "玉林", "百色", "贺州", "河池", "来宾", "崇左"),
        "海南" to listOf("海口", "三亚", "三沙", "儋州"),
        "四川" to listOf("成都", "自贡", "攀枝花", "泸州", "德阳", "绵阳", "广元", "遂宁", "内江", "乐山", "南充", "眉山", "宜宾", "广安", "达州", "雅安", "巴中", "资阳", "阿坝", "甘孜", "凉山"),
        "贵州" to listOf("贵阳", "六盘水", "遵义", "安顺", "毕节", "铜仁", "黔西南", "黔东南", "黔南"),
        "云南" to listOf("昆明", "曲靖", "玉溪", "保山", "昭通", "丽江", "普洱", "临沧", "楚雄", "红河", "文山", "西双版纳", "大理", "德宏", "怒江", "迪庆"),
        "西藏" to listOf("拉萨", "日喀则", "昌都", "林芝", "山南", "那曲", "阿里"),
        "陕西" to listOf("西安", "铜川", "宝鸡", "咸阳", "渭南", "延安", "汉中", "榆林", "安康", "商洛"),
        "甘肃" to listOf("兰州", "嘉峪关", "金昌", "白银", "天水", "武威", "张掖", "平凉", "酒泉", "庆阳", "定西", "陇南", "临夏", "甘南"),
        "青海" to listOf("西宁", "海东", "海北", "黄南", "海南州", "果洛", "玉树", "海西"),
        "宁夏" to listOf("银川", "石嘴山", "吴忠", "固原", "中卫"),
        "新疆" to listOf("乌鲁木齐", "克拉玛依", "吐鲁番", "哈密", "昌吉", "博尔塔拉", "巴音郭楞", "阿克苏", "克孜勒苏", "喀什", "和田", "伊犁", "塔城", "阿勒泰"))
    private val CITY_FIX: Map<String, DoubleArray> = mapOf(
        "深圳" to doubleArrayOf(2360.0, 27234.0, 10.5, 20.6, 12.0, 12.0),
        "东莞" to doubleArrayOf(4546.0, 26421.0, 10.2, 21.0, 12.0, 12.0))

    val view: View = build()

    private lateinit var setSalary: EditText
    private lateinit var setDays: EditText
    private lateinit var daysAuto: TextView
    private lateinit var setPayday: EditText
    private lateinit var setStart: EditText
    private lateinit var setLunch: Switch
    private lateinit var setLunchStart: EditText
    private lateinit var setLunchEnd: EditText
    private lateinit var setEnd: EditText
    private lateinit var setOvertime: Switch

    private lateinit var taxOn: Switch
    private lateinit var taxFund: EditText
    private lateinit var taxSocial: EditText
    private lateinit var taxThreshold: EditText
    private lateinit var taxCap: EditText
    private lateinit var taxFloor: EditText
    private lateinit var provSpin: Spinner
    private lateinit var citySpin: Spinner
    private var lastProvPos = 0
    private var lastCityPos = 0
    private lateinit var taxTier: Spinner
    private lateinit var taxTierCustomField: LinearLayout
    private lateinit var taxTierCustom: EditText
    private lateinit var taxCoSocial: EditText
    private lateinit var taxCoFund: EditText
    private lateinit var taxPreview: TextView

    private lateinit var leavePerDay: EditText
    private lateinit var leavePerUnit: Spinner
    private lateinit var leaveBase: EditText
    private lateinit var leaveBaseUnit: Spinner
    private lateinit var leaveStd: EditText
    private lateinit var leaveBaseNote: TextView

    private lateinit var calStatus: TextView

    private lateinit var remindOn: Switch
    private lateinit var remindSpin: Spinner
    private lateinit var ecoSw: Switch
    private lateinit var iconDarkSw: Switch

    private var mute = false

    private fun build(): View {
        val c = act
        val root = pageRoot(c)

        // ---- 收入与作息 ----
        val card1 = Ui.card(c)
        card1.addView(Ui.cardTitle(c, "收入与作息"))
        setSalary = numField(card1, "月薪（税前）", 112)
        setDays = numField(card1, "本月工作日（天）", 112)
        daysAuto = Ui.caption(c)
        card1.addView(daysAuto)
        setPayday = numField(card1, "发薪日", 112)
        setStart = timeField(card1, "上班时间")
        setLunch = Ui.switch(c)
        card1.addView(Ui.field(c, "有午休", setLunch))
        setLunchStart = timeField(card1, "午休开始")
        setLunchEnd = timeField(card1, "午休结束")
        setEnd = timeField(card1, "下班时间")
        setOvertime = Ui.switch(c)
        card1.addView(Ui.field(c, "加班也计薪", setOvertime))
        card1.addView(Ui.caption(c).apply {
            text = "计薪时间 = 上班到下班、扣除午休；工作日默认周一至周五。数据保存在本机，不会上传。" })
        root.addView(card1)

        // ---- 五险一金 · 个税 ----
        val card2 = Ui.card(c)
        card2.addView(Ui.cardTitle(c, "五险一金 · 个税（税后计薪）"))
        provSpin = Spinner(c).apply {
            adapter = android.widget.ArrayAdapter(c, android.R.layout.simple_spinner_dropdown_item,
                listOf("自定义 · 手动填写") + PROV.keys.toList())
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    if (mute || pos == lastProvPos) return
                    lastProvPos = pos
                    val prov = if (pos == 0) "custom" else PROV.keys.toList()[pos - 1]
                    st().tax.regionProv = prov
                    st().tax.regionCity = if (prov == "custom") "custom" else (PROV_CITIES[prov]?.first() ?: "custom")
                    applyRegionSelection()
                }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }
        }
        card2.addView(Ui.field(c, "省份（自动填参数）", provSpin))
        citySpin = Spinner(c).apply {
            adapter = android.widget.ArrayAdapter(c, android.R.layout.simple_spinner_dropdown_item, listOf("自定义"))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    if (mute || pos == lastCityPos) return
                    lastCityPos = pos
                    val cities = PROV_CITIES[st().tax.regionProv] ?: emptyList()
                    if (pos == 0) {
                        st().tax.regionProv = "custom"; st().tax.regionCity = "custom"
                    } else {
                        st().tax.regionCity = cities.getOrElse(pos - 1) { return }
                    }
                    applyRegionSelection()
                }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }
        }
        card2.addView(Ui.field(c, "城市", citySpin))
        taxOn = Ui.switch(c)
        card2.addView(Ui.field(c, "按税后计薪", taxOn))
        taxFund = numField(card2, "公积金个人比例 %", 112)
        taxSocial = numField(card2, "社保个人比例 %", 112)
        taxThreshold = numField(card2, "个税起征点（元/月）", 112)
        taxCap = numField(card2, "缴费基数上限", 112)
        taxFloor = numField(card2, "缴费基数下限", 112)
        taxTier = Spinner(c).apply {
            adapter = android.widget.ArrayAdapter(c, android.R.layout.simple_spinner_dropdown_item, TIER_LABELS)
        }
        taxTier.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (mute) return
                act.st.settings.tax.baseTier = TIER_VALUES[pos]
                commitTax()
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        card2.addView(Ui.field(c, "缴费档位（个人与公司同基数）", taxTier))
        taxTierCustom = numField(c = c, parent = card2, label = "自定义缴费基数（元/月）", width = 112)
        taxTierCustomField = taxTierCustom.parent as LinearLayout
        taxTierCustomField.visibility = View.GONE
        taxCoSocial = numField(card2, "公司社保比例 %", 112)
        taxCoFund = numField(card2, "公司公积金比例 %", 112)
        taxPreview = Ui.caption(c)
        card2.addView(taxPreview)
        card2.addView(Ui.caption(c).apply {
            text = "社保默认 10.5% = 养老 8% + 医疗 2% + 失业 0.5%；公司社保默认 26.5% ≈ 养老 16% + 医疗生育 ~9.5% + 失业/工伤 ~1%，各地不同可微调。缴费档位按工资比例近似折算基数（个人与公司同一基数，选“自定义基数”可精确填写，如按当地下限缴纳）；公司缴纳不扣个税、不影响到手，但公积金进账是你的隐藏收入。起征点可加上专项附加扣除；基数上限填 0 表示不限。个税按月度简化税率估算，不含年终奖。" })
        root.addView(card2)

        // ---- 年假 ----
        val card3 = Ui.card(c)
        card3.addView(Ui.cardTitle(c, "年假"))
        val perRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        leavePerDay = numField(c = c, parent = perRow, label = null, width = 84)
        leavePerUnit = Spinner(c).apply {
            adapter = android.widget.ArrayAdapter(c, android.R.layout.simple_spinner_dropdown_item, listOf("小时", "天")) }
        perRow.addView(leavePerUnit, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = Ui.dp(c, 8) })
        card3.addView(Ui.field(c, "每工作日攒", perRow))
        val baseRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        leaveBase = numField(c = c, parent = baseRow, label = null, width = 84)
        leaveBaseUnit = Spinner(c).apply {
            adapter = android.widget.ArrayAdapter(c, android.R.layout.simple_spinner_dropdown_item, listOf("天", "小时")) }
        baseRow.addView(leaveBaseUnit, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = Ui.dp(c, 8) })
        card3.addView(Ui.field(c, "当前余额", baseRow))
        leaveStd = numField(card3, "1 天 = 几小时", 112)
        leaveBaseNote = Ui.caption(c)
        card3.addView(leaveBaseNote)
        root.addView(card3)

        // ---- 提醒与后台 ----
        val cardR = Ui.card(c)
        cardR.addView(Ui.cardTitle(c, "提醒与后台"))
        remindOn = Ui.switch(c)
        cardR.addView(Ui.field(c, "摸鱼提醒推送", remindOn))
        remindSpin = Spinner(c).apply {
            adapter = android.widget.ArrayAdapter(c, android.R.layout.simple_spinner_dropdown_item,
                listOf("每 30 分钟", "每 45 分钟", "每 60 分钟"))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    if (mute) return
                    st().remindMin = listOf(30, 45, 60)[pos]
                    act.save()
                }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }
        }
        cardR.addView(Ui.field(c, "提醒间隔（工作中）", remindSpin))
        ecoSw = Ui.switch(c)
        cardR.addView(Ui.field(c, "低内存占用模式", ecoSw))
        cardR.addView(Ui.caption(c).apply {
            text = "低内存模式：界面刷新降为每秒 1 次、关闭飘字动画，省电省内存；计时与金额精度不受影响。" })
        val btnBg = Ui.btn(c, "后台权限与电池优化 🔋", ghost = true)
        btnBg.setOnClickListener { act.requestBackgroundPermissions() }
        cardR.addView(btnBg)
        cardR.addView(Ui.caption(c).apply {
            text = "开始摸鱼会启动前台计时服务：通知栏显示实时秒表、锁屏可见、可在通知里一键结束，App 被清理也照常计时。建议允许通知、加入电池优化白名单，并在系统设置里允许自启动。" })
        root.addView(cardR)

        // ---- 外观 ----
        val cardL = Ui.card(c)
        cardL.addView(Ui.cardTitle(c, "外观"))
        iconDarkSw = Ui.switch(c)
        cardL.addView(Ui.field(c, "深色桌面图标", iconDarkSw))
        cardL.addView(Ui.caption(c).apply {
            text = "切换后桌面图标变为深可可棕色版本（图案相同）。部分桌面启动器需要 1-2 秒刷新，个别需要重启桌面。" })
        root.addView(cardL)

        // ---- 数据 ----
        val card4 = Ui.card(c)
        calStatus = Ui.caption(c).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(0, 0, 0, Ui.dp(c, 6)) }
        card4.addView(calStatus)
        val btnCal = Ui.btn(c, "更新节假日数据 🔄", ghost = true)
        btnCal.setOnClickListener {
            Holidays.refresh(act.st) { _, msg ->
                act.say(msg); act.save(); renderComputed(); act.tickNow() }
        }
        card4.addView(btnCal)
        root.addView(card4)

        root.addView(Ui.text(c, 11, Color.parseColor("#c4b49e")).apply {
            gravity = android.view.Gravity.CENTER
            setPadding(Ui.dp(c, 20), Ui.dp(c, 4), Ui.dp(c, 20), Ui.dp(c, 16))
            text = "数据保存在本机，不会上传 · 仅供个人参考 💛"
        })

        wire()
        return root
    }

    private fun numField(parent: LinearLayout, label: String, width: Int): EditText {
        val e = Ui.edit(act, width, numeric = true)
        parent.addView(Ui.field(act, label, e))
        return e
    }

    private fun numField(c: Activity, parent: LinearLayout, label: String?, width: Int): EditText {
        val e = Ui.edit(c, width, numeric = true)
        if (label != null) parent.addView(Ui.field(c, label, e))
        else parent.addView(e)
        return e
    }

    private fun timeField(parent: LinearLayout, label: String): EditText {
        val c = act
        val e = Ui.edit(c, 118).apply {
            keyListener = null
            isCursorVisible = false
            setText("09:00")
        }
        e.setOnClickListener {
            val cur = Dates.toMin(e.text.toString())
            TimePickerDialog(c, { _, hh, mm ->
                val v = "%02d:%02d".format(hh, mm)
                e.setText(v)
                when (e) {
                    setStart -> st().start = v
                    setEnd -> st().end = v
                    setLunchStart -> st().lunchStart = v
                    setLunchEnd -> st().lunchEnd = v
                }
                commitTax()
            }, cur / 60, cur % 60, true).show()
        }
        parent.addView(Ui.field(c, label, e))
        return e
    }

    private fun st() = act.st.settings

    /** 所有设置写入后统一走这里：保存 + 重算展示 + 联动其它页 */
    private fun commitTax() {
        act.save()
        renderComputed()
        act.wishPage.renderConvert()
        act.wishPage.renderList()
        act.restPage.renderRecords()
        act.tickNow()
    }

    private fun bind(e: EditText, commit: (Double) -> Unit) {
        e.setOnFocusChangeListener { _, has ->
            if (!has) {
                val v = e.text.toString().toDoubleOrNull()
                if (v != null) commit(v)
                renderComputed()
                commitTax()
            }
        }
        e.setOnEditorActionListener { _, _, _ ->
            e.clearFocus(); true
        }
    }

    private fun wire() {
        setLunch.setOnCheckedChangeListener { _, v -> if (!mute) { st().lunch = v; commitTax() } }
        setOvertime.setOnCheckedChangeListener { _, v -> if (!mute) { st().overtime = v; commitTax() } }
        taxOn.setOnCheckedChangeListener { _, v -> if (!mute) { st().tax.enabled = v; commitTax() } }
        remindOn.setOnCheckedChangeListener { _, v -> if (!mute) { st().remindEnabled = v; act.save() } }
        ecoSw.setOnCheckedChangeListener { _, v -> if (!mute) { st().eco = v; act.save() } }
        iconDarkSw.setOnCheckedChangeListener { _, v ->
            if (mute) return@setOnCheckedChangeListener
            st().iconDark = v
            act.applyLauncherIcon(v)
            act.save()
        }
        leavePerUnit.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (mute) return
                act.st.leave.perDayUnit = if (pos == 0) "hour" else "day"
                commitTax(); renderComputed()
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        leaveBaseUnit.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (mute) return
                act.st.leave.baseUnit = if (pos == 0) "day" else "hour"
                act.st.leave.baseDate = Dates.dateKey(LocalDate.now())
                commitTax(); renderComputed()
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }

        bind(setSalary) { st().salary = it.coerceIn(0.0, 1e8) }
        bind(setDays) {
            val v = it.toInt()
            if (v in 1..31) { st().daysOverride = v; st().daysMonthKey = Dates.monthKey(LocalDate.now()) }
            else { st().daysOverride = null; renderAll() }
        }
        bind(setPayday) { st().payday = it.toInt().coerceIn(1, 31) }
        bind(taxFund) { st().tax.regionProv = "custom"; st().tax.regionCity = "custom"; st().tax.fundRate = it.coerceIn(0.0, 12.0) }
        bind(taxSocial) { st().tax.regionProv = "custom"; st().tax.regionCity = "custom"; st().tax.socialRate = it.coerceIn(0.0, 30.0) }
        bind(taxThreshold) { st().tax.threshold = it.coerceIn(0.0, 1e6) }
        bind(taxCap) { st().tax.regionProv = "custom"; st().tax.regionCity = "custom"; st().tax.baseCap = it.coerceIn(0.0, 1e7) }
        bind(taxFloor) { st().tax.regionProv = "custom"; st().tax.regionCity = "custom"; st().tax.baseFloor = it.coerceIn(0.0, 1e7) }
        bind(taxTierCustom) { st().tax.customBase = it.coerceIn(0.0, 1e7) }
        bind(taxCoSocial) { st().tax.regionProv = "custom"; st().tax.regionCity = "custom"; st().tax.coSocialRate = it.coerceIn(0.0, 30.0) }
        bind(taxCoFund) { st().tax.regionProv = "custom"; st().tax.regionCity = "custom"; st().tax.coFundRate = it.coerceIn(0.0, 12.0) }
        bind(leavePerDay) { act.st.leave.perDay = it.coerceIn(0.0, 24.0) }
        bind(leaveBase) {
            act.st.leave.base = it.coerceIn(0.0, 8760.0)
            act.st.leave.baseDate = Dates.dateKey(LocalDate.now())
        }
        bind(leaveStd) { act.st.leave.stdHours = it.coerceIn(1.0, 24.0) }
    }

    private fun fillCityAdapter(prov: String) {
        val cities = if (prov == "custom") listOf("自定义") else (PROV_CITIES[prov] ?: emptyList())
        citySpin.adapter = android.widget.ArrayAdapter(act, android.R.layout.simple_spinner_dropdown_item, cities)
    }

    private fun applyRegionSelection() {
        val t = st().tax
        val arr = (if (t.regionCity != "custom") CITY_FIX[t.regionCity] else null)
            ?: PROV[t.regionProv]
        if (arr != null) {
            t.baseFloor = arr[0]; t.baseCap = arr[1]
            t.socialRate = arr[2]; t.coSocialRate = arr[3]
            t.fundRate = arr[4]; t.coFundRate = arr[5]
        }
        act.save()
        renderAll()      // 把预设值刷进各输入框
    }

    /** 填充所有输入框（初始化 / 重置后） */
    fun renderAll() {
        mute = true
        val s = st()
        setSalary.setText(fmtIn(s.salary))
        setDays.setText(Pay.monthWorkdays(act.st, LocalDate.now()).toString())
        setPayday.setText(s.payday.toString())
        setStart.setText(s.start); setEnd.setText(s.end)
        setLunchStart.setText(s.lunchStart); setLunchEnd.setText(s.lunchEnd)
        setLunch.isChecked = s.lunch; setOvertime.isChecked = s.overtime
        taxOn.isChecked = s.tax.enabled
        taxFund.setText(fmtIn(s.tax.fundRate)); taxSocial.setText(fmtIn(s.tax.socialRate))
        taxThreshold.setText(fmtIn(s.tax.threshold)); taxCap.setText(fmtIn(s.tax.baseCap))
        taxFloor.setText(fmtIn(s.tax.baseFloor))
        val prov = if (PROV.containsKey(s.tax.regionProv)) s.tax.regionProv else "custom"
        lastProvPos = if (prov == "custom") 0 else PROV.keys.indexOf(prov) + 1
        lastCityPos = if (s.tax.regionCity == "custom") 0 else
            ((PROV_CITIES[prov]?.indexOf(s.tax.regionCity) ?: -1).coerceAtLeast(0)) + 1
        provSpin.setSelection(lastProvPos)
        fillCityAdapter(prov)
        citySpin.setSelection(lastCityPos)
        taxTier.setSelection(TIER_VALUES.indexOf(s.tax.baseTier).coerceAtLeast(0))
        taxTierCustom.setText(fmtIn(s.tax.customBase))
        taxCoSocial.setText(fmtIn(s.tax.coSocialRate)); taxCoFund.setText(fmtIn(s.tax.coFundRate))
        leavePerDay.setText(fmtIn(act.st.leave.perDay))
        leavePerUnit.setSelection(if (act.st.leave.perDayUnit == "hour") 0 else 1)
        leaveBase.setText(fmtIn(act.st.leave.base))
        leaveBaseUnit.setSelection(if (act.st.leave.baseUnit == "day") 0 else 1)
        leaveStd.setText(fmtIn(act.st.leave.stdHours))
        remindOn.isChecked = s.remindEnabled
        remindSpin.setSelection(listOf(30, 45, 60).indexOf(s.remindMin).coerceAtLeast(0))
        ecoSw.isChecked = s.eco
        iconDarkSw.isChecked = s.iconDark
        act.todayPage.renderSyncModeSwitch()
        mute = false
        renderComputed()
    }

    /** 只刷新计算型文案与联动（不动输入框，避免打断编辑） */
    fun renderComputed() {
        val s = st()
        val tier = TIER_VALUES.getOrElse(taxTier.selectedItemPosition) { "full" }
        taxTierCustomField.visibility = if (tier == "custom") View.VISIBLE else View.GONE

        val sal = s.salary
        taxPreview.text = if (sal > 0) {
            val base = Pay.insBase(s)
            val regionName = when {
                s.tax.regionCity != "custom" -> s.tax.regionCity
                s.tax.regionProv != "custom" -> s.tax.regionProv
                else -> null
            }
            "按月薪 ¥${Fmt.yuan2(sal)} 估算：缴费基数 ¥${Fmt.yuan2(base)}（${Pay.TIER_NAMES[s.tax.baseTier] ?: "工资全额"}${if (regionName != null) " · ${regionName}参考" else ""}）\n" +
            "五险一金 −¥${Fmt.yuan2(Pay.insuranceMonthly(s))} · 个税 −¥${Fmt.yuan2(Pay.taxMonthly(s))} · 月到手 ¥${Fmt.yuan2(Pay.netMonthly(s))}（约为税前的 ${String.format("%.1f", Pay.netMonthly(s) / sal * 100)}%）\n" +
            "公司为你缴 ¥${Fmt.yuan2(Pay.companyMonthly(s))}/月 = 社保 ¥${Fmt.yuan2(base * s.tax.coSocialRate / 100)} + 公积金 ¥${Fmt.yuan2(base * s.tax.coFundRate / 100)} · 公积金账户月进账 ¥${Fmt.yuan2(Pay.fundMonthlyIn(s))}"
        } else "填写月薪后，这里显示到手与公司缴纳估算。"

        val now = LocalDate.now()
        val bd = Holidays.breakdown(now)
        daysAuto.text = if (bd.hasData)
            "${now.monthValue} 月：周一至周五 ${bd.weekdays} 天 − 法定节假日 ${bd.hol} 天（${bd.names.joinToString("、").ifEmpty { "无" }}） + 调休上班 ${bd.makeup} 天 = ${bd.net} 个工作日。特殊公司安排可手动微调，下月自动重算。"
        else
            "${now.monthValue} 月暂无节假日数据（明年安排公布后联网自动更新），按周一至周五 ${bd.weekdays} 天统计，可手动微调。"

        val cr = act.st.cal[now.year.toString()]
        calStatus.text = if (cr != null && cr.fetchedAt > 0)
            "节假日数据：已在线更新（holiday-cn · ${java.text.SimpleDateFormat("yyyy/M/d", java.util.Locale.CHINA).format(java.util.Date(cr.fetchedAt))}），与内置数据自动合并"
        else "节假日数据：内置 2026 离线可用，联网时自动更新"

        val L = act.st.leave
        val perDayH = L.perDay * (if (L.perDayUnit == "day") L.stdHours else 1.0)
        leaveBaseNote.text = if (L.baseDate != null)
            "余额基准日：${L.baseDate}（当天不再重复累计，次日起每个工作日自动 +${String.format("%.2f", perDayH)} 小时）"
        else "首次修改“当前余额”时，会把今天记为基准日，之后自动往上攒。"
    }

    private fun fmtIn(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
