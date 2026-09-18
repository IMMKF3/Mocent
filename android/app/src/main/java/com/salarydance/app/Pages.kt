package com.salarydance.app

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import java.time.LocalDate
import java.time.LocalTime

fun pageRoot(c: Activity): LinearLayout = LinearLayout(c).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 16), Ui.dp(c, 34))
}

val STATUS_META = mapOf(
    Status.BEFORE to ("还没上班" to Color.parseColor("#8aa1c1")),
    Status.WORKING to ("计薪中 · 数字在跳舞" to Ui.GREEN),
    Status.LUNCH to ("午休中 · 暂停计薪" to Ui.AMBER),
    Status.OVERTIME to ("加班计薪中" to Ui.RED),
    Status.AFTER to ("已收工" to Ui.PURPLE),
    Status.WEEKEND to ("今天不上班" to Ui.TEAL),
    Status.HOLIDAY to ("法定节假日 · 不计薪" to Color.parseColor("#c9a227")),
)

// ==================== 今日 ====================

class TodayPage(private val act: MainActivity) {
    // 注意：floatersLayer 在 build() 里使用，必须先于 view 初始化（属性按声明顺序初始化）
    private val floatersLayer = FrameLayout(act)
    val view: View = build()

    private lateinit var chipDot: View
    private lateinit var chipText: TextView
    private lateinit var modeGross: TextView
    private lateinit var modeNet: TextView
    private lateinit var money: TextView
    private lateinit var moneySub: TextView
    private lateinit var rateHour: TextView
    private lateinit var rateMin: TextView
    private lateinit var rateSec: TextView
    private lateinit var daysNote: TextView
    private lateinit var taxNote: TextView
    private lateinit var tl: TimelineView
    private lateinit var tlStart: TextView
    private lateinit var tlLunch: TextView
    private lateinit var tlEnd: TextView
    private lateinit var tlNow: TextView
    private lateinit var monthEarned: TextView
    private lateinit var paydayLeft: TextView
    private lateinit var dayTotal: TextView
    private lateinit var leaveStat: TextView
    lateinit var dockBubble: TextView
        private set
    lateinit var dockEmoji: TextView
        private set

    private fun moneyCardParams(c: Activity) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = Ui.dp(c, 14) }

    private fun build(): View {
        val c = act
        val root = pageRoot(c)

        // ---- 金额卡（FrameLayout 以承载飘字层）----
        val moneyCard = FrameLayout(c).apply {
            background = Ui.roundBg(Ui.CARD, Ui.dp(c, 20).toFloat())
            layoutParams = moneyCardParams(c)
            val p = Ui.dp(c, 18)
            setPadding(p, p, p, p)
        }
        val inner = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val chipRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val chip = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.roundBg(Ui.CHIP_BG, Ui.dp(c, 999).toFloat())
            setPadding(Ui.dp(c, 12), Ui.dp(c, 5), Ui.dp(c, 12), Ui.dp(c, 5))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = Ui.dp(c, 10) }
        }
        chipDot = View(c).apply {
            layoutParams = LinearLayout.LayoutParams(Ui.dp(c, 8), Ui.dp(c, 8)).apply {
                marginEnd = Ui.dp(c, 6) }
            background = Ui.ovalBg(Ui.SUB)
        }
        chipText = Ui.text(c, 12, Ui.SUB, true).apply { text = "…" }
        chip.addView(chipDot); chip.addView(chipText)
        chipRow.addView(chip)

        val sw = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.roundBg(Ui.CHIP_BG, Ui.dp(c, 999).toFloat())
            setPadding(Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        modeGross = modeBtn(c, "税前"); modeNet = modeBtn(c, "税后")
        modeGross.setOnClickListener { setMode(false) }
        modeNet.setOnClickListener { setMode(true) }
        sw.addView(modeGross); sw.addView(modeNet)
        chipRow.addView(sw)
        inner.addView(chipRow)

        money = Ui.text(c, 50, Ui.INK, true).apply {
            fontFeatureSettings = "tnum"
            text = "¥0.00"
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = Ui.dp(c, 8) }
        }
        inner.addView(money)

        moneySub = Ui.text(c, 13, Ui.SUB).apply {
            gravity = Gravity.CENTER
            text = "今天已计薪 · --:--:--"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = Ui.dp(c, 6) }
        }
        inner.addView(moneySub)

        val rates = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = Ui.dp(c, 14) }
        }
        rateHour = rateCell(c, rates, "时薪"); rateMin = rateCell(c, rates, "分薪"); rateSec = rateCell(c, rates, "秒薪")
        inner.addView(rates)

        daysNote = Ui.text(c, 11, Ui.NOTE).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = Ui.dp(c, 10) }
        }
        inner.addView(daysNote)

        taxNote = Ui.text(c, 11, Ui.NOTE).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = Ui.dp(c, 8) }
        }
        inner.addView(taxNote)

        moneyCard.addView(inner)
        floatersLayer.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        moneyCard.addView(floatersLayer)
        root.addView(moneyCard)

        // ---- 时间线卡 ----
        val tlCard = Ui.card(c)
        tlCard.addView(Ui.cardTitle(c, "今天的时间线"))
        tl = TimelineView(c).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 14))
                .apply { topMargin = Ui.dp(c, 6); bottomMargin = Ui.dp(c, 6) }
        }
        tlCard.addView(tl)
        val labels = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = Ui.dp(c, 6) }
        }
        tlStart = Ui.text(c, 12, Ui.SUB); tlLunch = Ui.text(c, 12, Ui.SUB); tlEnd = Ui.text(c, 12, Ui.SUB)
        tlStart.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tlLunch.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tlEnd.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tlLunch.gravity = Gravity.CENTER
        tlEnd.gravity = Gravity.END
        labels.addView(tlStart); labels.addView(tlLunch); labels.addView(tlEnd)
        tlCard.addView(labels)
        tlNow = Ui.text(c, 13, Color.parseColor("#7a6a58"), true).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = Ui.dp(c, 10) }
        }
        tlCard.addView(tlNow)
        root.addView(tlCard)

        // ---- 统计行 ----
        val stats = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = Ui.dp(c, 14) }
        }
        monthEarned = statCell(c, stats, "本月已计薪")
        paydayLeft = statCell(c, stats, "距发工资")
        dayTotal = statCell(c, stats, "全天工资")
        leaveStat = statCell(c, stats, "年假（天）")
        root.addView(stats)

        // ---- 桌宠 dock ----
        val dock = Ui.card(c)
        val dockRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        dockEmoji = Ui.text(c, 34, Ui.INK).apply { text = "🐱" }
        dockRow.addView(dockEmoji, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = Ui.dp(c, 12) })
        dockBubble = Ui.text(c, 14, Ui.BUBBLE_TEXT).apply {
            background = Ui.roundBg(Color.WHITE, Ui.dp(c, 16).toFloat(),
                Color.parseColor("#f3e7d0"), Ui.dp(c, 1.5f).toInt().toFloat())
            setPadding(Ui.dp(c, 13), Ui.dp(c, 10), Ui.dp(c, 13), Ui.dp(c, 10))
            minLines = 2
            gravity = Gravity.CENTER_VERTICAL
        }
        dockRow.addView(dockBubble, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        dock.addView(dockRow)
        dock.setOnClickListener {
            dockEmoji.animate().translationYBy(-Ui.dp(c, 8).toFloat()).setDuration(140).withEndAction {
                dockEmoji.animate().translationYBy(Ui.dp(c, 8).toFloat()).setDuration(140).start()
            }.start()
            act.poke()
        }
        root.addView(dock)

        return root
    }

    private fun modeBtn(c: Activity, label: String): TextView = Ui.text(c, 12, Ui.SUB, true).apply {
        text = label
        setPadding(Ui.dp(c, 13), Ui.dp(c, 4), Ui.dp(c, 13), Ui.dp(c, 4))
    }

    private fun rateCell(c: Activity, parent: LinearLayout, label: String): TextView {
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = Ui.roundBg(Ui.SOFT_BG, Ui.dp(c, 14).toFloat())
            setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 10))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = Ui.dp(c, 5); marginEnd = Ui.dp(c, 5) }
        }
        val v = Ui.text(c, 15, Ui.INK, true).apply { gravity = Gravity.CENTER; text = "--" }
        box.addView(v)
        box.addView(Ui.text(c, 11, Ui.SUB).apply { gravity = Gravity.CENTER; text = label })
        parent.addView(box)
        return v
    }

    private fun statCell(c: Activity, parent: LinearLayout, label: String): TextView {
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val v = Ui.text(c, 15, Ui.INK, true).apply {
            gravity = Gravity.CENTER; Ui.ellipsize(this); text = "-" }
        box.addView(v)
        box.addView(Ui.text(c, 10, Ui.SUB).apply { gravity = Gravity.CENTER; text = label })
        parent.addView(box)
        return v
    }

    private fun setMode(net: Boolean) {
        act.st.settings.tax.enabled = net                       // 今日页直接生效，不经草稿
        act.draft?.settings?.tax?.enabled = net                 // 有草稿时同步，避免保存草稿时被旧值覆盖
        act.save()
        act.settingsPage.renderComputed()
        act.tickNow()
    }

    fun renderSyncModeSwitch() {
        val net = act.viewSettings().tax.enabled
        for ((b, active) in listOf(modeGross to !net, modeNet to net)) {
            b.setTextColor(if (active) Ui.BRAND_DEEP else Ui.SUB)
            b.background = if (active) Ui.roundBg(Color.WHITE, Ui.dp(act, 999).toFloat()) else null
        }
    }

    fun addFloater(text: String) {
        val c = act
        while (floatersLayer.childCount >= 5) floatersLayer.removeViewAt(0)   // 同时最多 5 个
        val f = Ui.text(c, 13, Ui.GREEN, true).apply { this.text = text }
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val width = floatersLayer.width.takeIf { it > 0 } ?: Ui.dp(c, 300)
        lp.leftMargin = (width * (0.2 + Math.random() * 0.6)).toInt()
        lp.topMargin = Ui.dp(c, 130)
        f.layoutParams = lp
        floatersLayer.addView(f)
        f.animate().translationYBy(-Ui.dp(c, 46).toFloat()).alpha(0f).setDuration(1700)
            .withEndAction { floatersLayer.removeView(f) }.start()
    }

    fun render(cents: Long, d: LocalDate, t: LocalTime, status: Status) {
        val st = act.st
        val s = st.settings
        money.text = "¥" + Fmt.yuan2(cents / 100.0)
        moneySub.text = "今天已计薪 · " + Fmt.hhmmss(t)

        val meta = STATUS_META[status]!!
        chipText.text = meta.first
        chipText.setTextColor(meta.second)
        chipDot.background = Ui.ovalBg(meta.second)

        dockEmoji.text = Pets.byId(act.st.pet.id).emoji
        renderSyncModeSwitch()
        rateHour.text = "¥" + Fmt.yuan2(Pay.perHour(st, d))
        rateMin.text = "¥" + Fmt.yuan2(Pay.perMin(st, d))
        rateSec.text = "¥" + Fmt.yuan4(Pay.perSec(st, d))
        daysNote.text = "按本月 " + Pay.monthWorkdays(st, d) + " 个工作日动态计算 · 已自动排除法定节假日、计入调休上班"

        val sal = s.salary
        if (sal > 0 && (s.tax.enabled || s.tax.coSocialRate > 0 || s.tax.coFundRate > 0)) {
            taxNote.visibility = View.VISIBLE
            val lines = mutableListOf<String>()
            if (s.tax.enabled)
                lines.add("税后计薪中：五险一金 −¥${Fmt.yuan2(Pay.insuranceMonthly(s))}/月 · 个税 −¥${Fmt.yuan2(Pay.taxMonthly(s))}/月 · 月到手 ¥${Fmt.yuan2(Pay.netMonthly(s))}")
            lines.add("公司为你缴 ¥${Fmt.yuan2(Pay.companyMonthly(s))}/月 · 公积金账户每月进账 ¥${Fmt.yuan2(Pay.fundMonthlyIn(s))}")
            taxNote.text = lines.joinToString("\n")
        } else taxNote.visibility = View.GONE

        // 时间线
        val startM = Dates.toMin(s.start); val endM = Dates.toMin(s.end)
        val span = (endM - startM).coerceAtLeast(1)
        val n = t.hour * 60 + t.minute + t.second / 60.0
        tl.startMin = startM; tl.endMin = endM
        if (s.lunch) {
            val a = Dates.toMin(s.lunchStart).coerceIn(startM, endM)
            val b = Dates.toMin(s.lunchEnd).coerceIn(startM, endM)
            tl.hasLunch = b > a; tl.lunchA = a; tl.lunchB = b
            tlLunch.text = "午休 ${s.lunchStart}-${s.lunchEnd}"
        } else { tl.hasLunch = false; tlLunch.text = "无午休" }
        tlStart.text = s.start; tlEnd.text = s.end
        val fillEnd = when {
            n <= startM -> startM.toDouble()
            !s.lunch -> minOf(n, endM.toDouble())
            else -> {
                val a = Dates.toMin(s.lunchStart); val b = Dates.toMin(s.lunchEnd)
                if (n < a) n else if (n < b) a.toDouble() else minOf(n, endM.toDouble())
            }
        }
        tl.setProgress(((fillEnd - startM) / span).toFloat())

        tlNow.text = when (status) {
            Status.BEFORE -> {
                val m = Math.max(0, Math.round(startM - n)).toInt()
                if (m >= 60) "${s.start} 开始计薪 · 还有 ${m / 60} 小时 ${m % 60} 分"
                else "${s.start} 开始计薪 · 还有 $m 分钟"
            }
            Status.WORKING -> {
                val m = Math.max(0, Math.round(endM - n)).toInt()
                if (m >= 60) "距下班还有 ${m / 60} 小时 ${m % 60} 分" else "距下班还有 $m 分钟"
            }
            Status.LUNCH -> {
                val m = Math.max(0, Math.round(Dates.toMin(s.lunchEnd) - n)).toInt()
                if (m >= 60) "午休到 ${s.lunchEnd}，还有 ${m / 60} 小时 ${m % 60} 分"
                else "午休到 ${s.lunchEnd}，还有 $m 分钟"
            }
            Status.OVERTIME -> {
                val m = Math.max(0, Math.round(n - endM)).toInt()
                "已加班 ${m / 60} 小时 ${Fmt.pad2(m % 60)} 分，心疼你 💛"
            }
            Status.AFTER -> "全天计薪 ¥${Fmt.yuan2(Pay.dailyPay(st, d))} 已到账 🎉"
            Status.HOLIDAY -> "法定节假日 · 好好休息 🏝️"
            Status.WEEKEND -> "周末愉快，周一再涨 💸"
        }

        // 统计
        monthEarned.text = "¥" + Fmt.yuan2(Pay.monthEstimate(st, d, cents))
        dayTotal.text = "¥" + Fmt.yuan2(Pay.dailyPay(st, d))
        paydayLeft.text = Pay.daysToPayday(st, d).toString()
        val lc = Leave.calc(st, d, t)
        leaveStat.text = String.format("%.1f", lc.totalH / lc.stdH)
    }
}

// ==================== 心愿 ====================

class WishPage(private val act: MainActivity) {
    val view: View = build()
    private lateinit var nameEdit: EditText
    private lateinit var priceEdit: EditText
    private lateinit var convertBox: TextView
    private lateinit var listBox: LinearLayout

    private fun build(): View {
        val c = act
        val root = pageRoot(c)

        val card1 = Ui.card(c)
        card1.addView(Ui.cardTitle(c, "把想买的，换成工作时间"))
        nameEdit = Ui.edit(c, 138).apply { hint = "例：降噪耳机" }
        card1.addView(Ui.field(c, "想买什么", nameEdit))
        priceEdit = Ui.edit(c, 112, numeric = true).apply { hint = "例：1399" }
        priceEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = renderConvert()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, cnt: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, cnt: Int) {}
        })
        card1.addView(Ui.field(c, "多少钱（元）", priceEdit))
        convertBox = Ui.text(c, 14, Ui.BUBBLE_TEXT).apply {
            background = Ui.roundBg(Ui.SOFT_BG, Ui.dp(c, 14).toFloat())
            setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12))
            text = "输入价格，看看它值多少个工作日。"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = Ui.dp(c, 12) }
        }
        card1.addView(convertBox)
        val addBtn = Ui.btn(c, "放进心愿清单 🎁")
        addBtn.setOnClickListener { addWish() }
        card1.addView(addBtn)
        root.addView(card1)

        val card2 = Ui.card(c)
        card2.addView(Ui.cardTitle(c, "心愿清单"))
        listBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        card2.addView(listBox)
        card2.addView(Ui.caption(c).apply {
            text = "进度 = 加入心愿后，工资跳动帮你看着计薪一点点积累 ✨" })
        root.addView(card2)
        return root
    }

    fun renderConvert() {
        val price = priceEdit.text.toString().toDoubleOrNull()
        if (price == null || price <= 0 || Pay.perHour(act.st, LocalDate.now()) <= 0) {
            convertBox.text = "输入价格，看看它值多少个工作日。"; return
        }
        val (h, m, days) = Pay.timeFor(act.st, price, LocalDate.now())
        convertBox.text = "¥${Fmt.yuan2(price)} ≈ $h 小时 ${Fmt.pad2(m)} 分 ≈ ${String.format("%.2f", days)} 个工作日\n“我愿意拿这${if (days < 1) "不到一天" else String.format("%.1f", days) + " 天"}的工作时间换它吗？”"
    }

    private fun addWish() {
        val st = act.st
        val name = nameEdit.text.toString().trim().ifEmpty { "小小的心愿" }
        val price = priceEdit.text.toString().toDoubleOrNull()
        if (price == null || price <= 0) { act.say("先填个价格嘛，我才能帮你换算呀～"); return }
        st.wishlist.add(0, Wish(System.currentTimeMillis(), name, price, st.lifetimeEarned, System.currentTimeMillis()))
        act.save(); renderList()
        nameEdit.setText(""); priceEdit.setText(""); renderConvert()
        val (h, m, _) = Pay.timeFor(st, price, LocalDate.now())
        act.say("已放进心愿清单！$h 小时 ${Fmt.pad2(m)} 分，一起攒吧 ✨")
    }

    fun renderList() {
        val c = act
        val st = c.st
        listBox.removeAllViews()
        if (st.wishlist.isEmpty()) {
            listBox.addView(Ui.text(c, 13, Ui.SUB).apply {
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(c, 18), 0, Ui.dp(c, 18))
                text = "还没有心愿，先想想现在最想要什么 ✨"
            })
            return
        }
        val today = LocalDate.now()
        for (w in st.wishlist) {
            val accrued = ((st.lifetimeEarned - w.baseline) / 100.0).coerceIn(0.0, w.price)
            val pct = if (w.price > 0) accrued / w.price * 100 else 100.0
            val (h, m, days) = Pay.timeFor(st, w.price, today)
            val done = accrued >= w.price - 0.005
            val remainDays = if (done) 0.0 else (w.price - accrued) / maxOf(0.01, Pay.dailyPay(st, today))
            val card = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                background = Ui.roundBg(Color.parseColor("#fffefb"), Ui.dp(c, 16).toFloat(),
                    Color.parseColor("#f3e8d4"), Ui.dp(c, 1).toInt().toFloat())
                setPadding(Ui.dp(c, 14), Ui.dp(c, 13), Ui.dp(c, 14), Ui.dp(c, 13))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = Ui.dp(c, 11) }
            }
            val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            head.addView(Ui.text(c, 15, Ui.INK, true).apply { text = w.name; Ui.ellipsize(this) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            head.addView(Ui.text(c, 15, Ui.BRAND_DEEP, true).apply { text = "¥" + Fmt.yuan2(w.price) })
            card.addView(head)
            card.addView(Ui.text(c, 12, Ui.SUB).apply {
                text = "≈ $h 小时 ${Fmt.pad2(m)} 分 · ${String.format("%.2f", days)} 个工作日"
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = Ui.dp(c, 5); bottomMargin = Ui.dp(c, 9) }
            })
            val bar = BarView(c, Color.parseColor("#f2e8d6"), Ui.GREEN).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 9))
            }
            bar.setProgress((pct / 100.0).toFloat())
            card.addView(bar)
            val foot = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = Ui.dp(c, 8) }
            }
            foot.addView(Ui.text(c, 12, Ui.SUB).apply {
                Ui.ellipsize(this)
                text = if (done) "攒够啦，去买吧 🎉"
                else "已攒 ¥${Fmt.yuan2(accrued)} · 还需约 ${String.format("%.1f", remainDays)} 个工作日"
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            foot.addView(Ui.text(c, 12, Color.parseColor("#cba58a")).apply {
                text = "删除"
                setPadding(Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 4))
                setOnClickListener {
                    st.wishlist.remove(w)
                    act.save(); renderList()
                }
            })
            card.addView(foot)
            listBox.addView(card)
        }
    }
}

// ==================== 摸鱼 ====================

class RestPage(private val act: MainActivity) {
    val view: View = build()
    private lateinit var timer: TextView
    private lateinit var cost: TextView
    private lateinit var note: TextView
    private lateinit var bigBtn: TextView
    private lateinit var totalLine: TextView
    private lateinit var list: LinearLayout

    private fun build(): View {
        val c = act
        val root = pageRoot(c)

        val card = Ui.card(c)
        timer = Ui.text(c, 44, Ui.INK, true).apply {
            text = "00:00"
            gravity = Gravity.CENTER
            fontFeatureSettings = "tnum"
            setPadding(0, Ui.dp(c, 8), 0, 0)
        }
        card.addView(timer)
        cost = Ui.text(c, 14, Ui.SUB).apply {
            gravity = Gravity.CENTER
            text = "这一段摸鱼，对应计薪 ¥0.00"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = Ui.dp(c, 6) }
        }
        card.addView(cost)
        note = Ui.text(c, 11, Color.parseColor("#b8a88f")).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = Ui.dp(c, 4); bottomMargin = Ui.dp(c, 14) }
        }
        card.addView(note)
        bigBtn = Ui.text(c, 17, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            text = "开始\n摸鱼"
            background = Ui.gradBg(Color.parseColor("#8fd3ae"), Ui.GREEN, Ui.dp(c, 55).toFloat())
        }
        bigBtn.setOnClickListener { toggle() }
        card.addView(bigBtn, LinearLayout.LayoutParams(Ui.dp(c, 110), Ui.dp(c, 110)).apply {
            gravity = Gravity.CENTER })
        card.addView(Ui.text(c, 12, Color.parseColor("#b8a88f")).apply {
            gravity = Gravity.CENTER
            text = "摸鱼时工资照常在涨，这里只是换个角度看看自己的时间 😌\n计时随时可以，计薪只落在工作时间里。"
            setPadding(0, Ui.dp(c, 16), 0, 0)
        })
        root.addView(card)

        val card2 = Ui.card(c)
        totalLine = Ui.cardTitle(c, "今日摸鱼记录")
        card2.addView(totalLine)
        list = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        card2.addView(list)
        root.addView(card2)
        return root
    }

    var isRunning: Boolean = false
        private set

    /** 工作时间外开始摸鱼的活泼提示（计时照跑，不计薪） */
    private fun breakHint(status: Status): String? = when (status) {
        Status.BEFORE -> "还没上班就开始摸鱼？这份松弛感先存着，现在不算钱哦 🛋️"
        Status.LUNCH -> "午休摸鱼不计薪～先干饭，米饭才是真的补给 🍚"
        Status.AFTER -> "都下班啦！计时照跑，钱一分不涨，纯纯用爱摸鱼 🌙"
        Status.WEEKEND -> "今天不上班！摸的是快乐鱼，不带薪的那种 🎣"
        Status.HOLIDAY -> "法定节假日摸鱼：不涨钱，但快乐是足额发放的 🎉"
        else -> null
    }

    private fun toggle() {
        val st = act.st
        if (st.activeBreak == null) {
            val hint = breakHint(Pay.statusOf(st, LocalDate.now(), LocalTime.now()))
            if (hint != null)
                android.widget.Toast.makeText(act, hint, android.widget.Toast.LENGTH_LONG).show()
            st.activeBreak = System.currentTimeMillis()
            isRunning = true
            bigBtn.text = "结束\n摸鱼"
            bigBtn.background = Ui.gradBg(Color.parseColor("#f39898"), Ui.RED, Ui.dp(act, 55).toFloat())
            TimerService.start(act, st.activeBreak!!)
            act.say(Pets.line(st, "breakStart") ?: "")
        } else {
            val s = st.activeBreak!!
            val paidSec = Pay.paidBreakSec(st, s, System.currentTimeMillis())
            val costV = paidSec * Pay.perSec(st, LocalDate.now())
            st.breaks.add(BreakRec(s, System.currentTimeMillis()))
            st.activeBreak = null
            isRunning = false
            TimerService.stop(act)
            bigBtn.text = "开始\n摸鱼"
            bigBtn.background = Ui.gradBg(Color.parseColor("#8fd3ae"), Ui.GREEN, Ui.dp(act, 55).toFloat())
            timer.text = "00:00"; cost.text = "这一段摸鱼，对应计薪 ¥0.00"
            note.visibility = View.GONE
            act.say(Pets.line(st, "breakEnd", "¥" + Fmt.yuan2(costV)) ?: "")
            renderRecords()
        }
        act.save()
    }

    fun renderLive(nowMs: Long) {
        val s = act.st.activeBreak ?: return
        val st = act.st
        val el = (nowMs - s) / 1000.0
        val paid = Pay.paidBreakSec(st, s, nowMs)
        timer.text = Fmt.pad2((el / 60).toInt()) + ":" + Fmt.pad2((el % 60).toInt())
        cost.text = "这一段摸鱼，对应计薪 ¥" + Fmt.yuan2(paid * Pay.perSec(st, LocalDate.now()))
        when {
            paid <= 0 -> {
                note.text = "⏰ 现在不在计薪时段：只帮你计时，不算钱～"
                note.visibility = View.VISIBLE
            }
            paid < el - 1 -> {
                note.text = "⏰ 摸出计时时段啦，只有落在工作时间里的部分才计薪"
                note.visibility = View.VISIBLE
            }
            else -> note.visibility = View.GONE
        }
    }

    fun resetUi() {
        isRunning = false
        bigBtn.text = "开始\n摸鱼"
        bigBtn.background = Ui.gradBg(Color.parseColor("#8fd3ae"), Ui.GREEN, Ui.dp(act, 55).toFloat())
        timer.text = "00:00"; cost.text = "这一段摸鱼，对应计薪 ¥0.00"
        note.visibility = View.GONE
    }

    /** 重启/跨日后恢复按钮状态 */
    fun syncActive() {
        val s = act.st.activeBreak
        if (s != null) {
            isRunning = true
            TimerService.start(act, s)
            bigBtn.text = "结束\n摸鱼"
            bigBtn.background = Ui.gradBg(Color.parseColor("#f39898"), Ui.RED, Ui.dp(act, 55).toFloat())
        } else resetUi()
    }

    fun renderRecords() {
        val c = act
        val st = c.st
        val totalPaid = st.breaks.sumOf { Pay.paidBreakSec(st, it.s, it.e) }
        val totalCost = totalPaid * Pay.perSec(st, LocalDate.now())
        totalLine.text = if (st.breaks.isNotEmpty())
            "今日摸鱼记录 · 合计 ${fmtDur(totalPaid)}（计薪时段）≈ ¥${Fmt.yuan2(totalCost)}" else "今日摸鱼记录"
        list.removeAllViews()
        if (st.breaks.isEmpty() && st.activeBreak == null) {
            list.addView(Ui.text(c, 13, Ui.SUB).apply {
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(c, 18), 0, Ui.dp(c, 18))
                text = "今天还没摸鱼，辛苦啦（也不知道该不该夸 🤔）"
            })
            return
        }
        for (b in st.breaks.asReversed()) {
            val sec = (b.e - b.s) / 1000.0
            val paid = Pay.paidBreakSec(st, b.s, b.e)
            val row = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(c, 2), Ui.dp(c, 10), Ui.dp(c, 2), Ui.dp(c, 10))
            }
            val left = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            left.addView(Ui.text(c, 14, Ui.INK).apply { text = fmtHMS(b.s) + " – " + fmtHMS(b.e) })
            left.addView(Ui.text(c, 12, Ui.SUB).apply {
                text = when {
                    paid <= 0 -> "${fmtDur(sec)} · 工作时间外"
                    paid < sec - 1 -> "${fmtDur(sec)} · 计薪 ${fmtDur(paid)}"
                    else -> fmtDur(sec)
                } })
            row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Ui.text(c, 14, Ui.GREEN, true).apply {
                text = if (paid <= 0) "未计薪" else "≈ ¥" + Fmt.yuan2(paid * Pay.perSec(st, LocalDate.now())) })
            list.addView(row)
        }
    }

    private fun fmtDur(sec: Double): String =
        if (sec >= 60) "${Math.round(sec / 60)} 分钟" else "${Math.round(sec)} 秒"

    private fun fmtHMS(ts: Long): String = Fmt.hhmmss(
        java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()).toLocalTime())
}

// ==================== 年假 ====================

class LeavePage(private val act: MainActivity) {
    val view: View = build()
    private lateinit var days: TextView
    private lateinit var sub1: TextView
    private lateinit var sub2: TextView
    private lateinit var amtEdit: EditText
    private lateinit var unitSpin: Spinner
    private lateinit var noteEdit: EditText
    private lateinit var logBox: LinearLayout

    private fun build(): View {
        val c = act
        val root = pageRoot(c)

        val card = Ui.card(c)
        val chip = Ui.text(c, 12, Color.parseColor("#5a8a4a"), true).apply {
            text = "🌴 年假余额 · 上班也在自动攒"
            background = Ui.roundBg(Color.parseColor("#eef6e6"), Ui.dp(c, 999).toFloat())
            setPadding(Ui.dp(c, 12), Ui.dp(c, 5), Ui.dp(c, 12), Ui.dp(c, 5))
        }
        card.addView(chip, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER; bottomMargin = Ui.dp(c, 10) })
        val daysRow = LinearLayout(c).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        days = Ui.text(c, 44, Ui.INK, true).apply { text = "0.00"; fontFeatureSettings = "tnum" }
        daysRow.addView(days)
        daysRow.addView(Ui.text(c, 20, Ui.SUB, true).apply { text = " 天" })
        card.addView(daysRow)
        sub1 = Ui.text(c, 14, Ui.SUB).apply { gravity = Gravity.CENTER; setPadding(0, Ui.dp(c, 6), 0, 0) }
        card.addView(sub1)
        sub2 = Ui.text(c, 14, Ui.SUB).apply { gravity = Gravity.CENTER; setPadding(0, Ui.dp(c, 10), 0, 0) }
        card.addView(sub2)
        root.addView(card)

        val useCard = Ui.card(c)
        useCard.addView(Ui.cardTitle(c, "请年假（从余额里扣一笔）"))
        val amtRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL }
        amtEdit = Ui.edit(c, 84, numeric = true).apply { hint = "0.5" }
        amtRow.addView(amtEdit)
        unitSpin = Spinner(c).apply {
            adapter = android.widget.ArrayAdapter(c, android.R.layout.simple_spinner_dropdown_item,
                listOf("天", "小时"))
        }
        amtRow.addView(unitSpin, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = Ui.dp(c, 8) })
        useCard.addView(Ui.field(c, "时长", amtRow))
        noteEdit = Ui.edit(c, 138).apply { hint = "例：周五请一天" }
        useCard.addView(Ui.field(c, "备注（可选）", noteEdit))
        val useBtn = Ui.btn(c, "请了！🌴")
        useBtn.setOnClickListener { use() }
        useCard.addView(useBtn)
        root.addView(useCard)

        val logCard = Ui.card(c)
        logCard.addView(Ui.cardTitle(c, "使用记录"))
        logBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        logCard.addView(logBox)
        root.addView(logCard)
        return root
    }

    fun renderSummary() {
        val st = act.st
        val lc = Leave.calc(st, LocalDate.now(), LocalTime.now())
        days.text = String.format("%.2f", lc.totalH / lc.stdH)
        days.setTextColor(if (lc.totalH < 0) Ui.RED else Ui.INK)
        sub1.text = "= ${String.format("%.1f", lc.totalH)} 小时 · 折算带薪假 ≈ ¥${Fmt.yuan2(lc.totalH * Pay.perHour(st, LocalDate.now()))}"
        sub2.text = "今日已攒 +${String.format("%.2f", lc.todayH)} 小时 · 每工作日 +${String.format("%.2f", lc.perDayH)} 小时"
    }

    private fun use() {
        val st = act.st
        val amt = amtEdit.text.toString().toDoubleOrNull()
        if (amt == null || amt <= 0) { act.say("请输入要请的时长呀～"); return }
        val std = st.leave.stdHours.takeIf { it > 0 } ?: 8.0
        val hours = amt * if (unitSpin.selectedItemPosition == 0) std else 1.0
        st.leave.log.add(0, LeaveUse(System.currentTimeMillis(), System.currentTimeMillis(),
            hours, noteEdit.text.toString().trim()))
        act.save(); renderLog(); renderSummary()
        amtEdit.setText(""); noteEdit.setText("")
        val c = Leave.calc(st, LocalDate.now(), LocalTime.now())
        act.say(if (c.totalH < 0) "用到负数啦，老板要找你谈心了 😅"
        else (Pets.line(st, "leave") ?: "已记录！好好休息 🌴"))
    }

    fun renderLog() {
        val c = act
        val st = c.st
        val std = st.leave.stdHours.takeIf { it > 0 } ?: 8.0
        logBox.removeAllViews()
        if (st.leave.log.isEmpty()) {
            logBox.addView(Ui.text(c, 13, Ui.SUB).apply {
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(c, 18), 0, Ui.dp(c, 18))
                text = "还没请过年假，攒着也是一种幸福 ✨"
            })
            return
        }
        val cal = java.util.Calendar.getInstance()
        for (u in st.leave.log) {
            cal.timeInMillis = u.ts
            val row = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(c, 2), Ui.dp(c, 10), Ui.dp(c, 2), Ui.dp(c, 10))
            }
            val left = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            left.addView(Ui.text(c, 14, Ui.INK).apply {
                text = "${cal.get(java.util.Calendar.MONTH) + 1}月${cal.get(java.util.Calendar.DAY_OF_MONTH)}日 · 请了 ${String.format("%.2f", u.amount / std)} 天" })
            left.addView(Ui.text(c, 12, Ui.SUB).apply {
                text = u.note.ifEmpty { "年假" } + " · ${String.format("%.1f", u.amount)} 小时" })
            row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Ui.text(c, 12, Color.parseColor("#cba58a")).apply {
                text = "删除"
                setPadding(Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 4))
                setOnClickListener {
                    st.leave.log.remove(u)
                    act.save(); renderLog(); renderSummary()
                }
            })
            logBox.addView(row)
        }
    }
}

// ==================== 搭子 ====================
