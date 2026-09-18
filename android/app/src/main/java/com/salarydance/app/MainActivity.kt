package com.salarydance.app

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.salarydance.app.Settings as AppSettings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class MainActivity : Activity() {

    lateinit var st: State
        private set
    lateinit var todayPage: TodayPage
        private set
    lateinit var wishPage: WishPage
        private set
    lateinit var restPage: RestPage
        private set
    lateinit var leavePage: LeavePage
        private set
    lateinit var settingsPage: SettingsPage
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var lastStatus: Status? = null
    private var curStatus: Status = Status.BEFORE
    private var lastReportAt = 0L
    private var lastWaterAt = 0L
    private var lastMoveAt = 0L
    private var lastFloaterSec = -1
    private var saveCounter = 0
    private var pokeCount = 0
    private var lastPokeAt = 0L
    private var lastRemindAt = 0L
    private var restBreath: ObjectAnimator? = null

    private lateinit var scrollView: ScrollView
    private lateinit var pages: Map<String, View>
    private lateinit var tabs: Map<String, Pair<TextView, TextView>>
    private var curTab = "today"
    private var restDisc: LinearLayout? = null          // 摸鱼中央凸起圆钮
    private var lastRestActive: Boolean? = null

    // ---------- 设置草稿（改了不点保存不生效） ----------
    // 草稿非空时：设置/搭子的写入走 draft 对象，st 始终指向已保存的基准状态；
    // 其它页面读取 st 不受草稿影响，点「保存」才把草稿配置拷回 st 并落盘。
    var draft: State? = null
        private set

    fun editSettings(): AppSettings {
        beginEdit()
        return draft!!.settings
    }

    fun editPet(): PetCfg {
        beginEdit()
        return draft!!.pet
    }

    fun editLeave(): LeaveCfg {
        beginEdit()
        return draft!!.leave
    }

    /** 设置页显示用：有草稿读草稿（保持输入框与预览一致），无草稿读基准 */
    fun viewSettings(): AppSettings = draft?.settings ?: st.settings
    fun viewPet(): PetCfg = draft?.pet ?: st.pet
    fun viewLeave(): LeaveCfg = draft?.leave ?: st.leave

    private fun beginEdit() {
        if (draft != null) return
        draft = Store.clone(st)
        settingsPage.showSaveBar(true)
    }

    fun saveEdit() {
        val d = draft ?: return
        Store.copyConfig(st, d)
        draft = null
        settingsPage.showSaveBar(false)
        save()
        afterConfigChanged()
        say("已保存 ✅ 设置生效啦")
    }

    fun revertEdit() {
        if (draft == null) return
        draft = null
        settingsPage.showSaveBar(false)
        applyLauncherIcon(st.settings.iconDark)
        afterConfigChanged()
        say("已放弃修改，还是原来的配置 😌")
    }

    private fun afterConfigChanged() {
        settingsPage.renderAll()
        wishPage.renderConvert()
        wishPage.renderList()
        restPage.renderRecords()
        leavePage.renderLog()
        leavePage.renderSummary()
        tickNow()
    }

    private val tickRunner = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, if (Store.state.settings.eco) 1000L else 200L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.load(this)
        st = Store.state
        Holidays.rebuild(st.cal)
        lastReportAt = System.currentTimeMillis()
        lastWaterAt = lastReportAt
        lastMoveAt = lastReportAt

        buildUi()
        initNotifications()
        applyLauncherIcon(st.settings.iconDark)

        // 与网页版一致的补账逻辑：崩溃/杀进程期间漏记的金额补回来
        val now = LocalDateTime.now()
        val oldDay = st.todayKey
        val oldSaved = st.savedTodayEarned
        rollDates(now.toLocalDate())
        val cur = Pay.todayEarnedCents(st, now.toLocalDate(), now.toLocalTime())
        if (oldDay == Dates.dateKey(now.toLocalDate()) && cur > oldSaved) {
            st.lifetimeEarned += cur - oldSaved
            st.monthEarned += cur - oldSaved
        }
        st.savedTodayEarned = maxOf(st.savedTodayEarned, cur.toDouble())

        settingsPage.renderAll()
        wishPage.renderConvert()
        wishPage.renderList()
        restPage.syncActive()
        restPage.renderRecords()
        leavePage.renderLog()
        leavePage.renderSummary()

        curStatus = Pay.statusOf(st, now.toLocalDate(), now.toLocalTime())
        lastStatus = curStatus
        say(if ((curStatus == Status.OVERTIME || curStatus == Status.AFTER) && !st.pet.overtimeCare)
            "我在呢，随时叫我 ₍˄·͈༝·͈˄₎◞̑"
        else Pets.line(st, curStatusKind()) ?: "打开摸薪，今天也要好好摸、好好涨呀 ✨")

        tick()
        Holidays.refresh(st) { _, _ -> save(); settingsPage.renderComputed() }  // 启动静默更新
        RegionData.refresh(st) {                                               // 启动静默更新地区参数
            save()
            settingsPage.renderAll()
        }
    }

    /** 切换桌面图标浅色/深色版（activity-alias 方案，桌面可能需要 1-2 秒刷新） */
    fun applyLauncherIcon(dark: Boolean) {
        val pm = packageManager
        pm.setComponentEnabledSetting(
            ComponentName(packageName, "com.salarydance.app.LauncherLight"),
            if (dark) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(
            ComponentName(packageName, "com.salarydance.app.LauncherDark"),
            if (dark) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP)
    }

    private fun initNotifications() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(
            TimerService.CHANNEL_TIMER, "摸鱼计时器", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(
            "reminders", "摸鱼提醒", NotificationManager.IMPORTANCE_DEFAULT))
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
        BreakEvents.onEndedRemotely = {               // 通知栏“结束摸鱼” → 刷新界面
            handler.post {
                restPage.resetUi()
                restPage.renderRecords()
                save()
                say(Pets.line(st, "breakEnd") ?: "摸鱼结束，继续涨 🐟")
            }
        }
    }

    /** 后台权限引导：通知权限 + 电池优化白名单 */
    fun requestBackgroundPermissions() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")))
            } catch (e2: Exception) { }
        }
    }

    private fun pushRemind() {
        try {
            val d = LocalDate.now()
            val n = Notification.Builder(this, "reminders")
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle("摸鱼时间到 🐟")
                .setContentText("已连续工作 ${st.settings.remindMin} 分钟 · 今日已赚 ¥" +
                    Fmt.yuan2(Pay.todayEarnedCents(st, d, LocalTime.now()) / 100.0))
                .setAutoCancel(true)
                .build()
            getSystemService(NotificationManager::class.java).notify(2, n)
        } catch (e: Exception) { }
        say(Pets.line(st, "move") ?: "摸鱼时间到 🐟")
    }

    // ---------- UI 骨架 ----------

    private fun buildUi() {
        val c = this
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }

        // 顶栏
        val topbar = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 18), Ui.dp(c, 8))
        }
        topbar.addView(Ui.text(c, 19, Ui.INK, true).apply {
            text = "🐱 摸薪 Mocent" })
        topbar.addView(Ui.text(c, 12, Ui.SUB, true).apply {
            text = " 摸着摸着就涨薪" })
        topbar.addView(View(c), LinearLayout.LayoutParams(0, 0, 1f))
        val gear = Ui.text(c, 17, Ui.INK).apply {
            text = "⚙️"
            gravity = Gravity.CENTER
            background = Ui.roundBg(Color.WHITE, Ui.dp(c, 12).toFloat())
        }
        gear.setOnClickListener { switchTab("set") }
        topbar.addView(gear, LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)))
        root.addView(topbar)

        // 主滚动区 + 六个页面
        todayPage = TodayPage(this)
        wishPage = WishPage(this)
        restPage = RestPage(this)
        leavePage = LeavePage(this)
        settingsPage = SettingsPage(this)
        pages = linkedMapOf(
            "today" to todayPage.view, "wish" to wishPage.view, "rest" to restPage.view,
            "leave" to leavePage.view, "set" to settingsPage.view)
        val pagesBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        pages.values.forEach { pagesBox.addView(it) }
        scrollView = ScrollView(c).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            isVerticalScrollBarEnabled = false
        }
        scrollView.addView(pagesBox, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scrollView)

        // 底部 Tab
        val defs = listOf(
            Triple("💰", "今日", "today"), Triple("🎁", "心愿", "wish"), Triple("🐟", "摸鱼", "rest"),
            Triple("🌴", "年假", "leave"), Triple("⚙️", "设置", "set"))
        val tabbar = NotchTabbarView(c).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8), Ui.dp(c, 6) + insetsBottom())
        }
        val map = LinkedHashMap<String, Pair<TextView, TextView>>()
        for ((emoji, label, name) in defs) {
            val cell = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            }
            val e: TextView
            val l: TextView
            if (name == "rest") {
                // 闲鱼式中央凸起圆钮：56dp 圆盘仅放 🐟 图标，上浮嵌入底栏缺口
                cell.clipChildren = false
                e = Ui.text(c, 24, Color.WHITE).apply {
                    gravity = Gravity.CENTER; text = emoji
                    translationY = -Ui.dp(c, 2).toFloat()   // 视觉居中微调
                }
                l = e                                        // 圆钮无文字，色值同步复用同一视图
                val disc = LinearLayout(c).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    clipChildren = false
                    background = Ui.ovalGradBg(Color.parseColor("#ffc06e"), Ui.BRAND)
                    translationY = -Ui.dp(c, 12).toFloat()
                }
                disc.addView(e)
                disc.layoutParams = LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 56))
                restDisc = disc
                cell.addView(disc)
            } else {
                e = Ui.text(c, 21, Ui.TAB_OFF).apply { gravity = Gravity.CENTER; text = emoji }
                l = Ui.text(c, 11, Ui.TAB_OFF, true).apply { gravity = Gravity.CENTER; text = label }
                cell.addView(e); cell.addView(l)
            }
            cell.setOnClickListener { switchTab(name) }
            tabbar.addView(cell)
            map[name] = e to l
        }
        tabs = map
        // 圆钮要上浮超出底栏 → 允许各级容器越界绘制
        tabbar.clipChildren = false
        tabbar.clipToPadding = false
        root.clipChildren = false
        root.addView(tabbar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(root)
        switchTab("today")
    }

    private fun insetsBottom(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else Ui.dp(this, 8)
    }

    // ---------- 主循环 ----------

    private fun tick() {
        val now = LocalDateTime.now()
        val d = now.toLocalDate()
        val t = now.toLocalTime()
        rollDates(d)

        if (Pay.daysToPayday(st, d) == 0 && st.paydaySaidKey != Dates.dateKey(d)) {
            st.paydaySaidKey = Dates.dateKey(d)
            say(Pets.line(st, "payday") ?: "今天是发薪日，快乐 🎉")
        }

        if (st.activeBreak == null && restPage.isRunning) {   // 摸鱼被通知栏结束 → 同步界面
            restPage.resetUi()
            restPage.renderRecords()
        }

        val status = Pay.statusOf(st, d, t)
        if (status != lastStatus) {
            if (lastStatus != null) onStatusChange(status)
            lastStatus = status
        }
        curStatus = status

        val cents = Pay.todayEarnedCents(st, d, t)
        if (cents > st.savedTodayEarned) {
            val delta = cents - st.savedTodayEarned
            st.lifetimeEarned += delta
            st.monthEarned += delta
            st.savedTodayEarned = cents.toDouble()
        } else if (cents < st.savedTodayEarned) {
            st.savedTodayEarned = cents.toDouble()
        }

        for (w in st.wishlist) {                          // 心愿攒够 → 搭子庆祝一次
            if (!w.cheered && w.price > 0 && (st.lifetimeEarned - w.baseline) / 100.0 >= w.price - 0.005) {
                w.cheered = true
                say(Pets.line(st, "wishDone", w.name) ?: "「${w.name}」攒够啦，去买吧 🎉")
            }
        }

        todayPage.render(cents, d, t, status)
        leavePage.renderSummary()
        syncRestTab()
        if (st.activeBreak != null) restPage.renderLive(System.currentTimeMillis())

        // 飘字：仅今日页可见时生成（避免切页回来堆积）
        if (t.second != lastFloaterSec && !st.settings.eco &&
            pages["today"]?.visibility == View.VISIBLE) {
            lastFloaterSec = t.second
            if ((status == Status.WORKING || status == Status.OVERTIME) && Pay.perSec(st, d) > 0)
                todayPage.addFloater("+¥" + Fmt.yuan3(Pay.perSec(st, d)))
        }

        if (st.settings.remindEnabled && (status == Status.WORKING || status == Status.OVERTIME)) {
            val n = System.currentTimeMillis()
            if (n - lastRemindAt >= st.settings.remindMin * 60_000L) {
                lastRemindAt = n
                pushRemind()
            }
        }

        // 桌宠：喝水 / 活动 / 定期汇报
        if (status == Status.WORKING || status == Status.OVERTIME) {
            val n = System.currentTimeMillis()
            if (st.pet.water && n - lastWaterAt > 45 * 60_000L) {
                lastWaterAt = n; say(Pets.line(st, "water") ?: "")
            } else if (st.pet.move && n - lastMoveAt > 60 * 60_000L) {
                lastMoveAt = n; say(Pets.line(st, "move") ?: "")
            } else if (n - lastReportAt > st.pet.reportMin * 60_000L) {
                lastReportAt = n; say(Pets.line(st, curStatusKind()) ?: "")
            }
        }

        if (++saveCounter % 10 == 0) save()
    }

    fun tickNow() = tick()

    private fun rollDates(d: LocalDate) {
        val dk = Dates.dateKey(d)
        if (st.todayKey != dk) {
            st.todayKey = dk
            st.breaks.clear()
            st.activeBreak = null
            st.savedTodayEarned = 0.0
            restPage.resetUi()
            restPage.renderRecords()
        }
        val mk = Dates.monthKey(d)
        if (st.monthKey != mk) {
            st.monthKey = mk
            st.monthEarned = 0.0
            st.settings.daysOverride = null       // 跨月回到自动统计
            if (this::settingsPage.isInitialized) settingsPage.renderAll()
            Holidays.refresh(st) { _, _ -> save(); if (this::settingsPage.isInitialized) settingsPage.renderComputed() }
        }
    }

    private fun onStatusChange(status: Status) {
        lastReportAt = System.currentTimeMillis()
        lastWaterAt = lastReportAt
        lastMoveAt = lastReportAt
        if ((status == Status.OVERTIME || status == Status.AFTER) && !st.pet.overtimeCare) return
        Pets.line(st, kindOf(status))?.let { say(it) }
    }

    fun curStatusKind(): String = kindOf(curStatus)

    private fun kindOf(s: Status): String = when (s) {
        Status.BEFORE -> "before"; Status.WORKING -> "working"; Status.LUNCH -> "lunch"
        Status.OVERTIME -> "overtime"; Status.AFTER -> "after"
        Status.WEEKEND -> "weekend"; Status.HOLIDAY -> "holiday"
    }

    // ---------- 共用 ----------

    fun say(text: String) {
        if (text.isEmpty()) return
        todayPage.dockBubble.text = text
        todayPage.dockBubble.alpha = 0.6f
        todayPage.dockBubble.animate().alpha(1f).setDuration(150).start()
    }

    /** 戳一戳：连戳 5 下搭子会翻脸 */
    fun poke() {
        val now = System.currentTimeMillis()
        pokeCount = if (now - lastPokeAt < 2500) pokeCount + 1 else 1
        lastPokeAt = now
        if (pokeCount >= 5) {
            pokeCount = 0
            say(Pets.line(st, "pokeMad") ?: "戳累了吧，歇会儿 😌")
        } else {
            say(Pets.line(st, "poke") ?: Pets.line(st, curStatusKind()) ?: "我在呢")
        }
    }

    fun dockEmojiView(): TextView = todayPage.dockEmoji

    fun save() = Store.save(this)

    fun switchTab(name: String) {
        curTab = name
        pages.forEach { (k, v) -> v.visibility = if (k == name) View.VISIBLE else View.GONE }
        tabs.forEach { (k, p) ->
            if (k == "rest") return@forEach        // 圆钮文字恒白，由 syncRestTab 管理
            val on = k == name
            p.first.setTextColor(if (on) Ui.BRAND_DEEP else Ui.TAB_OFF)
            p.second.setTextColor(if (on) Ui.BRAND_DEEP else Ui.TAB_OFF)
        }
        syncRestTab()
        scrollView.scrollTo(0, 0)
    }

    /** 摸鱼圆钮：常驻凸起（品牌橙）；计时中变深红 */
    private fun syncRestTab() {
        val active = st.activeBreak != null
        if (active == lastRestActive) return
        lastRestActive = active
        restDisc?.let { disc ->
            disc.background = if (active) Ui.ovalGradBg(Color.parseColor("#f87171"), Color.parseColor("#dc2626"))
            else Ui.ovalGradBg(Color.parseColor("#ffc06e"), Ui.BRAND)
        }
        restBreath?.cancel()
        restBreath = null
        restDisc?.let { disc ->
            disc.scaleX = 1f; disc.scaleY = 1f
            if (active) {
                restBreath = ObjectAnimator.ofPropertyValuesHolder(
                    disc,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.1f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.1f)
                ).apply {
                    duration = 850
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    start()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(tickRunner)
        handler.post(tickRunner)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tickRunner)
        save()
    }

    override fun onDestroy() {
        super.onDestroy()
        BreakEvents.onEndedRemotely = null
    }
}

/** 底栏背景：圆角矩形 + 顶中弧形缺口（供凸起圆盘嵌入，CLEAR 抠洞后透出下层） */
class NotchTabbarView(context: android.content.Context) : LinearLayout(context) {
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#fffdf9") }
    private val notchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#f3e3cd") }

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRect(rect, barPaint)
        canvas.drawCircle(width / 2f, 0f, Ui.dp(context, 34).toFloat(), notchPaint)
    }
}
