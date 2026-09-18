package com.salarydance.app

import java.time.LocalDate
import java.time.LocalTime

/** 七只摸鱼搭子，台词库与网页版一致 */
object Pets {
    class Pet(
        val id: String, val name: String, val emoji: String, val tag: String,
        val lines: Map<String, List<String>>,
    )

    val ALL = listOf(
        Pet("xiaoxin", "小薪", "🐱", "元气报数担当", mapOf(
            "before" to listOf(
                "早呀！{start} 才开始计薪，早饭先吃上 🍞",
                "马上开工，今天目标：把每一秒都赚得明明白白！"),
            "working" to listOf(
                "滴！刚刚 1 分钟又进账 {minute}，今天已经 {today} 啦 ✨",
                "秒薪 {sec}，你看，数字在跳舞 💃",
                "本月 {days} 个工作日，今天全天能赚 {day}！",
                "已赚 {today}，午饭给自己加个腿不过分 🍗",
                "悄悄说：公司这月又帮你交了 {company}，公积金户头月进 {fund} ✨"),
            "lunch" to listOf("午休中，计薪暂停～饭要好好吃 🍚"),
            "overtime" to listOf("加班中！这是 {hour}/小时 之外的坚持，记得心疼自己一点"),
            "after" to listOf("收工！今天一共 {today}，数字交给你保管啦 🌙"),
            "weekend" to listOf("今天不上班，秒薪也放假 🌿 去玩吧！"),
            "holiday" to listOf("法定节假日！不计薪，但快乐是免费的 🎉"),
            "leave" to listOf("年假余额 {leave} 天，攒够了就大胆请！"),
            "water" to listOf("喝水时间！我刚舔了两爪子 💧"),
            "move" to listOf("站起来扭一扭，学我伸个懒腰～"),
            "breakStart" to listOf("好嘞，摸鱼计时开始 ⏱️ 数字我帮你盯着"),
            "breakEnd" to listOf("回来啦！刚才那段值 {cost}，血赚（并没有）😂"),
            "poke" to listOf(
                "喵？戳我干嘛，工资又不会戳一下就翻倍哦 😼",
                "别戳啦，我在帮你数钱呢，已到账 {today}",
                "戳一下，摸鱼值 +1 秒；再戳一下，还是 +1 秒 🐟"),
            "pokeMad" to listOf(
                "喵呜！！再戳我就把你的秒薪叼走 🙀",
                "停！你这手速都够摸三条鱼了 🐟🐟🐟"),
            "payday" to listOf("发薪日！！数字今天涨了就直接到账，快乐翻倍 🎉"),
            "wishDone" to listOf("「{wish}」攒够啦！快去下单，别让心愿过夜 🎉"))),
        Pet("mili", "摸米", "🐰", "攒米碎碎念", mapOf(
            "before" to listOf(
                "今天也要……慢慢地、稳稳地攒米粒呀 🌾",
                "早早到也没关系，摸米会等你～"),
            "working" to listOf(
                "又攒下 {minute} 啦，一粒一粒也是米 ✨",
                "今天的米粒已经 {today} 了，好厉害！",
                "别急别急，秒薪 {sec}，它自己会长大",
                "公积金月进 {fund}，摸米的罐子也在悄悄变满 🌾"),
            "lunch" to listOf("午休啦，胡萝卜时间 🥕 计薪先停一停"),
            "overtime" to listOf("还在忙呀……钱是赚不完的，摸米会一直等你 🥺"),
            "after" to listOf("今天赚到 {today}，装进小口袋，明天见 🌙"),
            "weekend" to listOf("不上班的日子，摸米银行也关门咯 🌿"),
            "holiday" to listOf("放假啦放假啦，摸米要去草地上打滚 🌿"),
            "leave" to listOf("年假有 {leave} 天啦，记得休息呀 🌈"),
            "water" to listOf("咕嘟咕嘟，喝水水～"),
            "move" to listOf("蹦两下！像我一样竖起耳朵活动活动"),
            "breakStart" to listOf("去去就回～摸米帮你看家 🏠"),
            "breakEnd" to listOf("欢迎回来！刚才的 {cost} 就当买个开心啦"),
            "poke" to listOf(
                "哇，摸摸耳朵会带来好运米哦 🌾",
                "人家在攒米……你别晃我呀",
                "戳戳 = 摸摸，摸米感觉被偏爱了"),
            "pokeMad" to listOf(
                "呜……头都被戳晕了，米要撒出来啦 🥺",
                "再戳！再戳我就把米袋藏起来！"),
            "payday" to listOf("发米日！米袋鼓鼓的一天 🌾✨"),
            "wishDone" to listOf("米攒满啦，「{wish}」可以抱回家啦！🌾"))),
        Pet("huanhuan", "缓缓", "🦫", "温柔劝休大师", mapOf(
            "before" to listOf("不急，早一点到也没关系，慢慢来。"),
            "working" to listOf(
                "已经 {today} 了，你看，时间没有白过。",
                "喝口茶，数字会自己往前走的。",
                "{hour} 一小时——你的时间很值钱，别把它花在焦虑上。"),
            "lunch" to listOf("午休了，离开屏幕，让眼睛也歇歇 🍃"),
            "overtime" to listOf("已经很努力啦。喝口水，收好手头的事，就早点休息吧。"),
            "after" to listOf("今天到这里刚刚好。{today}，是你认真生活的一天。"),
            "weekend" to listOf("今天不赚钱，赚休息。"),
            "holiday" to listOf("法定假日，理直气壮地休息吧。"),
            "leave" to listOf("年假是赚来的休息，别舍不得用。{leave} 天在等你。"),
            "water" to listOf("水杯举起来，缓缓陪你喝这一杯 💧"),
            "move" to listOf("站起来，看看窗外，缓一缓～"),
            "breakStart" to listOf("休息不需要理由，去呼吸一下 🍃"),
            "breakEnd" to listOf("回来了就好。慢慢进入状态，不急。"),
            "poke" to listOf(
                "嗯，我在。别急，有我在。",
                "戳我也没用哦，今天已经 {today} 了，安心。",
                "慢慢来，先喝口水再戳。"),
            "pokeMad" to listOf("好啦好啦，我陪你坐一会儿，不戳了啊。"),
            "payday" to listOf("工资到账的日子，记得给自己一点犒劳。"),
            "wishDone" to listOf("「{wish}」攒够了。认真地生活，愿望会兑现的。"))),
        Pet("pudding", "带薪", "🐶", "带薪撒欢小尾巴", mapOf(
            "before" to listOf("汪！今天也要冲在最前面，上班前先遛个弯 🐾"),
            "working" to listOf(
                "刚刚那分钟 +{minute}，尾巴又摇快了一点！",
                "累计 {today}！我去把下一个数字球叼回来 🎾",
                "时薪 {hour}，主人最棒！",
                "公司这月又帮你交了 {company}！跟着我，福利不漏！"),
            "lunch" to listOf("午休！我的碗已经舔干净了，你吃好了吗 🍖"),
            "overtime" to listOf("加班的话……我趴在脚边陪你，但别太晚哦 🐾"),
            "after" to listOf("收工啦收工啦！出门撒欢！🎉"),
            "weekend" to listOf("周末！公园！飞盘！走走走！🥏"),
            "holiday" to listOf("法定节假日！全家出动，汪！🎊"),
            "leave" to listOf("年假 {leave} 天，攒够了带我出去玩呀！"),
            "water" to listOf("喝水！我也在舔水碗，一起 💧"),
            "move" to listOf("去跑两圈！牵引绳我都叼来了"),
            "breakStart" to listOf("你歇着，我放哨 🐕"),
            "breakEnd" to listOf("欢迎回来！尾巴已摇出残影！"),
            "poke" to listOf(
                "汪！带薪！点名就到！",
                "你戳我一下，我摇三下尾巴 🐾",
                "戳我 = 带薪撸狗，这波血赚！"),
            "pokeMad" to listOf(
                "汪汪汪！尾巴要摇出火星子啦！",
                "再戳我就扑上去舔你一脸 🐶"),
            "payday" to listOf("汪汪！！发工资日！零食预算拉满！🍖"),
            "wishDone" to listOf("汪！「{wish}」进袋的速度比狗粮还快！🎉"))),
        Pet("latte", "拿铁", "🦉", "夜班观察员", mapOf(
            "before" to listOf("早。夜里的世界我替你看过了，今天会是好日子。"),
            "working" to listOf(
                "观察记录：{today}，稳定增长中。",
                "秒薪 {sec}，规律的振翅，稳定的进账。",
                "今天的进度条，比昨天更亮一点。"),
            "lunch" to listOf("午休。猫头鹰的下午，从闭目养神开始。"),
            "overtime" to listOf("夜深了。加班的人，值得一份夜宵 🌙"),
            "after" to listOf("今日观察结束。晚安，好梦。"),
            "weekend" to listOf("周末的树上，风景更好。"),
            "holiday" to listOf("法定假日，整片森林都安静了。"),
            "leave" to listOf("年假 {leave} 天，像储备的松果，该用就用。"),
            "water" to listOf("补充水分，夜里飞行的经验之谈。"),
            "move" to listOf("伸展翅膀，你也伸展一下。"),
            "breakStart" to listOf("休息是高效的一部分，去吧。"),
            "breakEnd" to listOf("精力恢复 37%，继续。"),
            "poke" to listOf(
                "……夜间观察员在岗，请勿打扰。",
                "戳击已记录。当前 {today}，一切稳定。",
                "嗯。我在补觉，账我替你盯着。"),
            "pokeMad" to listOf("……你已触发夜枭警告。再戳，明早的班你自己上。"),
            "payday" to listOf("薪资入账确认。今日观测结论：值得庆祝。"),
            "wishDone" to listOf("观测完成：「{wish}」资金到位，可以执行。"))),
        Pet("popo", "划水", "🐧", "科学划水分析师", mapOf(
            "before" to listOf("根据计算，今天 100% 会到账工资（的一部分）。"),
            "working" to listOf(
                "数据更新：{today}，趋势良好。",
                "每秒 +{sec}，南极冰盖都没这么稳定。",
                "本月 {days} 个工作日，今日进度正常。",
                "福利流水：公司月缴 {company}，公积金月进 {fund}，隐藏收入稳定入账。"),
            "lunch" to listOf("午休：系统降频运行，请进食 🐟"),
            "overtime" to listOf("加班模式已检测。建议：完成、保存、下班。"),
            "after" to listOf("今日 KPI：钱到账、人下班，达成 ✅"),
            "weekend" to listOf("周末：冰面滑行模式，启动。"),
            "holiday" to listOf("法定假日。我的分析结论是：休息。"),
            "leave" to listOf("年假余额 {leave} 天，属于已确认资产。"),
            "water" to listOf("喝水提醒。冰块的教训：要液态的。"),
            "move" to listOf("久坐预警。企鹅都知道要走一走。"),
            "breakStart" to listOf("摸鱼时间开始，计时器就位。"),
            "breakEnd" to listOf("休息结束，效率回满。"),
            "poke" to listOf(
                "划水检测：你正在摸鱼，我在陪摸。",
                "数据同步：当前 {today}，可放心划。",
                "戳我 = 划水效率 +0.01%（心理作用）。"),
            "pokeMad" to listOf(
                "警告：戳击频率过高，建议保持优雅划水 🐧",
                "冰面快被你戳裂了。请冷静。"),
            "payday" to listOf("工资到账确认。本月划水成果已落袋 ✅"),
            "wishDone" to listOf("「{wish}」目标达成，划水成功率 100%。"))),
        Pet("tuanzi", "团子", "🐻", "打盹冠军", mapOf(
            "before" to listOf("唔……再眯五分钟就开工……"),
            "working" to listOf(
                "zzz……啊！已经 {today} 了！我盯着呢……",
                "赚 {day} 的一天，从打个哈欠开始。",
                "数字在涨，我稍微……闭一下眼。"),
            "lunch" to listOf("午休＝官方批准的小睡，快睡！"),
            "overtime" to listOf("加班的熊，冬天要多睡两个月……别学我，快收工 🌙"),
            "after" to listOf("收工！进入小睡预备姿势 zzz"),
            "weekend" to listOf("周末＝冬眠迷你版，晚安。"),
            "holiday" to listOf("法定假日，正适合窝着 🧸"),
            "leave" to listOf("年假 {leave} 天，都是没睡够的觉，快用！"),
            "water" to listOf("喝水～蜂蜜水更好 🍯"),
            "move" to listOf("起来走走……好吧，熊也要运动。"),
            "breakStart" to listOf("摸鱼？带我一个，我专业的。"),
            "breakEnd" to listOf("呜……这么快。好吧，继续。"),
            "poke" to listOf(
                "唔……戳熊之前，先看看 {today} 到账没有……",
                "戳着挺软和吧？熊靠冬膘撑起整个工位 🍯",
                "摸鱼搭子……也是可以被摸的……zzz"),
            "pokeMad" to listOf(
                "嗷！熊被戳醒了！你的今日摸鱼额度被我扣一分钟 🐻",
                "再戳……我就、我就睡给你看 zzZ"),
            "payday" to listOf("发薪日……熊的蜂蜜预算到账了 🍯"),
            "wishDone" to listOf("「{wish}」攒够啦……熊的蜂蜜罐都没你满得快 🍯"))),
    )

    fun byId(id: String): Pet = ALL.firstOrNull { it.id == id } ?: ALL[0]

    private fun pick(list: List<String>): String = list[(Math.random() * list.size).toInt()]

    /** 台词模板填充：{today} {minute} {sec} {hour} {day} {days} {leave} {start} {fund} {company} {payday} {wish} {cost} */
    fun fill(st: State, tpl: String, extra: String? = null): String {
        val d = LocalDate.now(); val t = LocalTime.now()
        val std = st.leave.stdHours.takeIf { it > 0 } ?: 8.0
        return tpl
            .replace("{today}", "¥" + Fmt.yuan2(Pay.todayEarnedCents(st, d, t) / 100.0))
            .replace("{minute}", "¥" + Fmt.yuan2(Pay.perMin(st, d)))
            .replace("{sec}", "¥" + Fmt.yuan4(Pay.perSec(st, d)))
            .replace("{hour}", "¥" + Fmt.yuan2(Pay.perHour(st, d)))
            .replace("{day}", "¥" + Fmt.yuan2(Pay.dailyPay(st, d)))
            .replace("{days}", Pay.monthWorkdays(st, d).toString())
            .replace("{leave}", String.format("%.1f", Leave.calc(st, d, t).totalH / std))
            .replace("{start}", st.settings.start)
            .replace("{fund}", "¥" + Fmt.yuan2(Pay.fundMonthlyIn(st.settings)))
            .replace("{company}", "¥" + Fmt.yuan2(Pay.companyMonthly(st.settings)))
            .replace("{payday}", Pay.daysToPayday(st, d).toString())
            .replace("{wish}", extra ?: "")
            .replace("{cost}", extra ?: "")
    }

    fun line(st: State, kind: String, extra: String? = null): String? {
        val lines = byId(st.pet.id).lines[kind] ?: return null
        return fill(st, pick(lines), extra)
    }
}
