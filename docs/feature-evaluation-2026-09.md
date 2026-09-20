# 摸薪 Mocent · 新功能评估

> 评估日期：2026-09-20 · 针对 4 条用户建议
> 评估范围：网页版 `web/index.html`（PWA，三端共用逻辑）、Android 原生 Kotlin、Windows 桌面壳 C#
> 代码基线：桌面壳 v1.4.0 Beta 1 / 网页版与 Android 同版本

## 更新（2026-09-20 第六轮：编译链路打通，两平台首次真正编译通过）

前几轮所有 Kotlin / C# 改动都只做了静态校验（括号配平、符号引用），**从未真正编译过**。本轮把构建链从零搭起来，并因此发现并修复了 3 个真实编译错误。

### 编译揪出的 3 个错误（静态检查无法发现）

| 文件 | 错误 | 根因 | 修法 |
|---|---|---|---|
| `Pages.kt:405` | Kotlin 语法错误 + 12 个级联 `Unresolved reference` | `val tp = a, tc = b, base = c` —— **Kotlin 不支持一条语句声明多个变量**（C/Java 写法） | 拆成 3 个独立 `val` |
| `RegionData.kt:112-143` | 约 190 个 `actual type is Int, but Double was expected` | 工厂 `bd(p1: Double, ...)` 被 `bd(8, 16, 2, 9, ...)` 调用，**Kotlin 不会把 Int 字面量自动提升为 Double** | 参数类型改 `Number`，内部 `.toDouble()` |
| `RegionData.kt` | `compareTo` 缺 `operator` 修饰符等衍生错误 | 上一条的连带结果 | 随上一条一并消失 |

### 构建环境（`.tools/`，约 1.8 GB，已 gitignore）

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | Temurin 21.0.12.1 | **AGP 8.5.2 只支持 JDK 17–22**，系统已装的 JDK 24 用不了 |
| Gradle | 8.7 | AGP 8.5.2 要求 Gradle 8.7+ 且 < 9 |
| Android SDK | platform 34 / build-tools 34.0.0 / platform-tools | `sdkmanager` 安装 |
| WebView2 SDK | 1.0.2903.40 | 桌面端编译依赖，从 NuGet 取 net462 DLL |

### 新增构建入口

- `android/build.sh`：自带工具链，`bash android/build.sh` 一键出 APK
- `desktop/Mocent.csproj`：SDK 风格项目，`csc.exe` 不可用时用 `dotnet build` 回退编译
- `.tools/dl.js` / `.tools/unzip.js`：纯 Node 下载器与 zip 解压（本机无 curl / unzip）

### 网络与路径的 4 个坑

1. **`dl.google.com` 在本网络 TLS 握手被切断**（`Remote host terminated the handshake`）→ 构建全挂。改用阿里云镜像 `https://maven.aliyun.com/repository/google`，写在 `settings.gradle` 的 `pluginManagement` 与 `dependencyResolutionManagement` 两处，且必须放在 `google()` **之前**。
2. **`sdkmanager --licenses` 卡在交互**：环境没有 `yes` 命令 → 用 Node `spawn` 持续喂 `y\n`；`.bat` 在 Node 里必须 `shell:true`，否则 `spawn EINVAL`。
3. **`gradle.bat` 不认 MSYS 路径**：脚本里 `JAVA_HOME=/d/...` 会报 "invalid directory" → 必须用 `D:\...` Windows 形态。
4. Git Bash 缺 `dirname`/`head`/`grep` → 构建脚本统一用 PortableGit 自带 bash 运行；`build.sh` 里版本提取也从 `grep -oP` 换成 `sed`，避免依赖 coreutils。

### 构建结果（已从零清理后重跑验证）

- **桌面端**：`dist/Mocent/`（Mocent.exe + 3 个 DLL + web/）+ `Mocent-v1.4.0_Beta_1-windows-x64.zip`（0.71 MB）。校验 PE 子系统 = 2（Windows GUI）、引用 `mscorlib`（.NET Framework）、引用 WebView2。
- **Android**：`app-debug.apk`（0.98 MB），3 分 34 秒完成。

> 注：`dl.google.com` 被封是**本机网络**问题，若你的网络能直连 Google Maven，`settings.gradle` 里的镜像可留着（放前面不影响，镜像不可用时会自动回退官方源）。

## 更新（2026-09-20 第四轮：v1.4.0 Beta 1）

本轮三项改动：

1. **版本号**：统一为 `1.4.0 Beta 1`（`desktop/src/Program.cs` `APP_VERSION`、`android/app/build.gradle` `versionName` / `versionCode 19`）。因为版本号带空格，`desktop/build.sh` 拆成两个变量：`VER_SAFE`（空格换下划线，用于文件名）与 `VER_NUM`（只取 `1.4.0`，用于 MSI 的 `Version`——WiX 只接受纯数字段）；同时把提取正则从 `[0-9.]+` 放宽为 `[^"]+`，否则会截断成 `1.4.0`。

2. **实发金额模式升级：新增「社保比例」与「缴纳基数」自动反推**（三端同步）
   - 新输入项：`社保个人比例 %`、`公司社保比例 %`、`缴纳基数（元/月）`；`medicalIn` 的语义从「医保账户到账」改为「**医保每月缴纳总额**」（个人 + 单位）。
   - 反推算法（`web` 的 `reverseFromMedical()` / Android 的 `RegionData.reverseFromMedical()`）：
     `缴费基数 = 医保缴纳总额 ÷ (医疗个人% + 医疗单位%)` → 用上下限钳制 → 按养老/医疗/失业/工伤/生育各自比例算金额 → 各项比例相加得**社保个人总比例**与**公司总比例**，一并回填进输入框。
   - 反推所需的分险种比例来自新增的数据结构：`web/regions.json` 的 `breakdown`（省级 + 深圳）与 `RegionData.DEFAULT_BREAKDOWN_BY_REGION`（离线兜底），每项为 `[个人%, 单位%]`。查不到时回落到 `DEFAULT_BREAKDOWN`。
   - 新增反推明细展示（`netBreakHTML()` / `netBreakText()`）：列出基数、各险种比例与金额、个人/公司合计、医保个人账户到账（= 基数 × 医疗个人%）。反推基数超出当地上下限时给出 ⚠️ 提示并说明取值。
   - **副产品**：有了社保比例与基数，实发金额模式也能算出「公司缴纳」了——今日页税说明同步改为显示社保个人/公司金额（此前该行在该模式下被隐藏）。

3. **修复窗口最小化后位移**（桌面端）
   - 成因：主窗体用 `WM_NCCALCSIZE` 把非客户区归零实现无边框，最小化→还原时系统仍按自带边框厚度做一次位置补偿，于是窗口累积偏移。
   - 修法：新增 `lastNormalLoc` / `locKnown` / `wasMinimized` / `restoringLoc` 四个字段，`Move` 事件里记录**正常态**位置（`restoringLoc` 为真时跳过，避免把已偏移的值当成正确值），`Resize` 事件里在「从最小化恢复」时强制复位；最小化时顺手把记住的位置落盘（`SaveLoc_()`），异常退出也不丢。

4. **社保数据更新 + 一键更新按钮**
   - `web/regions.json` 升到 `version 4`，新增 `updatedAt` / `cities` / `breakdown` / **`medicalBase`** 四段；`applyRegionsData()` 同步支持新字段，缓存格式升级为 `{data, meta, fetchedAt}`（兼容旧的裸数据格式）。
   - 设置页「💾 数据与备份」在「更新节假日数据」下方新增 **「更新社保比例 / 基数 🧾」** 按钮与状态行（`#btnRegion` / `#regionStatus`），显示数据日期；Android 端同样新增按钮与状态行，并在拉取成功后 `renderAll()` 刷新回填值。
   - ⚠️ **数据可靠性说明**：本轮通过联网调研得到各省 2025/2026 年度缴费基数上下限与五险费率，其中北京、广东、江苏、河南、湖北、广西、海南等有官方文件支撑，部分省份（天津、吉林、黑龙江、贵州、陕西、浙江、江西）来自二手汇总，宁夏、西藏等仅有第三方计算器数据。**上线前建议复核**；这正是加「一键更新」按钮的原因——数据改 JSON 即可，无需发版。
   - 已知口径差异：医疗单位比例各地差异大（广东 5.5% vs 上海 9.5%），该值直接决定反推精度，是复核重点。

5. **第五轮：按用户复核文档校正数据 + 医保基数独立口径**
   - 依据用户提供的《2026 年度社保基数与五险比例汇总》，逐项核对了 `PROV` / `DEFAULT_PROV` 的 31 个省级值，采纳其结论修正了此前有争议的几处（如上海上限 `37731`、浙江下限 `4986`），并把「**医保基数与养老基数可能不一致**」这一此前遗漏的口径落实到代码。
   - 新增 `medicalBase` 数据结构（web `MED_BASE` / Android `MED_BASE`）覆盖 5 个地区：**河北**（省本级医保 6794 / 20382）、**内蒙古**（6744 / 25290）、**福建**（4579 / 22893）、**浙江**（4986 / 25299）、**深圳**（6727 / 33633）。`regions.json` 的该段会被运行时合并覆盖内置兜底（`applyMedicalBase()` / `mergeMedicalBase()`），与 `breakdown` / `cities` 同一套模式，改数据不用发版。
   - 该口径**只用于实发金额模式的反推**（`medicalBaseFrom()` / `medicalBase()`），不影响 `insBaseMonthly()` 的养老口径计算。
   - **本轮修掉的两个真实 bug**（由回代测试发现）：
     a. `medicalBaseFrom()` 原来的兜底直接取 `t.baseFloor/baseCap`（草稿值）——用户切换地区后草稿里可能还是旧值，或手填「自定义」留下 0，导致上下限夹取完全失效（北京总额填 10 元会反推出基数 90.91）。改为用 `regionValues()` 按当前地区**现算**。
     b. 河北、浙江的医保上限原写成 `0`（不限），会让反推基数冲到 35294/17391 而不封顶。改为沿用全省上限 `20382` / `25299`。
   - 验证：从 `index.html` 抽取真实函数 + `regions.json` 真实数据跑回代测试，9 个地区用例全部满足 `基数 × 医保总比例 == 输入总额`（不夹取时误差 < 0.02 元），4 个边界用例（0 / 低于下限 / 超上限 / 未知地区）行为正确。


---

## 更新（2026-09-20 第二轮）

用户已就两条建议拍板，评估结论相应调整：

| 项 | 最终决定 |
|---|---|
| ② 手动输入 | 改为「填税前月薪 + 实际到手 + 医保账户到账」的**实发金额模式**，不再推算比例。三端同步更新。 |
| ③ 浮窗 | **只做 Windows 桌面端**，Android 端不做（含通知栏与桌面小组件）。 |
| ① ④ | 已实现并落地（详见下方「实现状态」）。 |

### 实现状态（本轮已改代码）

- **① 公积金开关**：web（数据模型 / 三个计算函数 / 设置项 / 预览文案 / 搭子台词兜底）+ Android（`Pay.kt` / `Store.kt` / `SettingsPage.kt` / `Pages.kt` / `Pets.kt`）均已实现。
- **④ 首页摸鱼块**：web（抽出 `toggleBreak()` + `renderBreakControls()`，首页新增卡片）+ Android（`TodayPage` 新增卡片，`RestPage.toggle()` 开放为共用入口）均已实现。
- 桌面端共用网页代码，自动同步，无需改动。

### 实现状态（第三轮：② ③ 也已全部完成）

四条建议现已**全部落地**，版本号统一升到 **v1.4.0**（`desktop/src/Program.cs` `APP_VERSION`、`android/app/build.gradle` `versionName` / `versionCode 18`）。

- **② 实发金额模式**：三端同步完成。
  - **web**：`settings.tax` 新增 `mode / netSalary / medicalIn`；设置页加「计税方式」下拉（按五险一金比例估算 / 按实际到手金额），比例类字段整块收进 `#taxRateBlock`、实发类字段放 `#taxNetBlock`，两块互斥显示；新增 `netRatio()`，`taxFactor()` 优先取它，其余计算链不动；`renderToday()` 税说明与 `taxPreview` 按模式分文案。
  - **Android**：`Store.kt` 的 `TaxCfg` 加三个字段并在 `copyFrom` / `toJson` / `parse` 三处同步；`Pay.kt` 新增 `netRatio(s)` 并重写 `taxFactor(s)`；`SettingsPage.kt` 新增「计税方式」下拉与两个输入框，`taxNetBlock` / `taxRateBlock` 互斥（外面再包一层 `taxBlocks`——`fold()` 展开卡片时会把直接子 view 全部置 VISIBLE，不包一层隐藏的块会被误显示）；`Pages.kt` 今日页税说明按模式分支。
  - **桌面端**：共用网页代码，自动同步。
- **③ Windows 桌面浮窗**：仅桌面壳，按用户决定不做 Android / 网页端。
  - `desktop/src/Program.cs` 新增 `MiniForm`：256×106 无边框置顶小窗，`ShowInTaskbar = false`，整窗可拖（`ReleaseCapture()` + `WM_NCLBUTTONDOWN`），位置记在 `%LOCALAPPDATA%\Mocent\mini.json`，双击回主窗，右键菜单可调置顶与不透明度（100/90/80/70%）；右上角状态点按 working / lunch / overtime 等状态变色。
  - `OnWebMessage` 新增三条消息：`mini:on|off`、`alpha:N`、`tick:金额|摸鱼|状态`。
  - 网页侧设置页新增「🪟 桌面浮窗」分区（`html.app-shell .sec-desktop` 控制，仅桌面壳内可见），开关与不透明度存 `localStorage`，下次启动自动恢复；`uiTick()` 里 500ms 节流推送数据。
- **过程中修掉的两个坑**：
  1. `pushMini()` 里 `fmtC()` 只返回数字、不带 `¥`，小窗会显示裸数字 → 已在推送时补上 `¥`。
  2. `MINI_KEY` / `miniOn` 等 `const`/`let` 原先声明在使用它们的桌面壳初始化块**之后**，`let/const` 的暂时性死区（TDZ）会让初始化时抛 `ReferenceError` → 已把声明整体上移到初始化块之前。

---

## 一、结论速览

| # | 建议 | 价值 | 工作量 | 风险 | 推荐度 | 建议排期 |
|---|---|---|---|---|---|---|
| ① | 公积金开关 | 高 | 极低（web ~25 行 + Android ~30 行） | 低 | ⭐⭐⭐⭐⭐ | **v1.4 首做** |
| ② | 社保金额手动填写 | 高 | 中（web ~80 行 + Android ~90 行） | 中（口径混淆） | ⭐⭐⭐⭐ | **v1.4 尾 / v1.5**，需拆成 2A+2B |
| ③ | 浮窗（折叠小窗） | 很高 | 分端差异极大（桌面中低 / 网页低 / Android 高） | 中高（Android 权限） | ⭐⭐⭐⭐（桌面）· ⭐⭐（Android 悬浮窗不推荐） | **v1.5 起分端推进** |
| ④ | 首页加摸鱼块 | 中高 | 低（web ~50 行 + Android ~60 行） | 低 | ⭐⭐⭐⭐⭐ | **v1.4 首做** |

**一句话建议**：①④ 立刻做（改动小、无口径争议、感知强）；② 做，但必须把「扣款金额」和「医保到账」拆开，否则会把税后工资算错；③ 桌面版做原生浮窗最划算，Android 先把金额塞进已有的前台通知（几乎零成本），系统级悬浮窗不建议做。

---

## 二、现状梳理（评估依据）

### 2.1 公积金相关代码

| 位置 | 内容 |
|---|---|
| `web/index.html:561` | `tax: { enabled, fundRate: 12, socialRate: 10.5, ... coSocialRate: 26.5, coFundRate: 12, ... }` |
| `web/index.html:441,454` | 设置项「公积金个人比例 %」「公司公积金比例 %」 |
| `web/index.html:811-819` | `insuranceMonthly()` / `companyMonthly()` / `fundMonthlyIn()`，三处都直接读 `fundRate`/`coFundRate` |
| `web/index.html:1567` | `taxPreview` 文案里拼接「公积金 ¥x · 公积金账户月进账 ¥x」 |
| `web/index.html:1029` | 搭子台词占位符 `{fund}` 替换为 `fundMonthlyIn()` |
| `android/.../Pay.kt:47,51,55` | 同上三个计算函数 |
| `android/.../Store.kt:10-17,202-206,280-287` | `Tax` 数据类 + JSON 序列化/反序列化 |
| `android/.../SettingsPage.kt:495-502,522-523,585` | 绑定、地区预设回填、预览文案 |

👉 **关键发现**：公积金比例只在 3 个计算函数里被读取，且都通过 `insBaseMonthly()` 乘比例。加开关只需要在这 3 处加一道门，不需要动基数和税率逻辑——这是①成本极低的原因。

### 2.2 摸鱼相关代码

- `state.breaks[]` 每日自动清空（`index.html:1717`），`state.activeBreak` 存开始时间戳 → **「今日摸鱼已赚」天然可按日统计**，无需新增存储。
- 计薪判定用 `paidBreakSec(startTs, endTs)` 与工作时段求交集，工外/午休/节假日自动不计薪，`BREAK_HINT` 有对应提示文案。
- 入口目前只有 tabbar 中央凸起圆钮 + `page-rest` 页面的大按钮，**逻辑写在 `$('#breakBtn').addEventListener` 里**（`index.html:1336-1357`），与 DOM 强耦合。

👉 **关键发现**：④ 要做的第一件事是把这段逻辑抽成 `toggleBreak()` + `renderBreakControls()`，否则首页和摸鱼页两个按钮会各自操作 DOM、状态不同步。

### 2.3 桌面壳能力

- `MainForm` 已是无边框 WinForms 窗体，已有 `WndProc` 拦截 `WM_NCHITTEST`、`SendMessage` P/Invoke、窗口位置持久化（`window.json`）、托盘图标、`OnWebMessage` 通道（当前只处理 `min` / `close` / `ca:`）。
- `MinimumSize = 400×600` 是主窗体属性，新建浮窗不受影响；单实例 `Mutex` 与"同进程多窗体"不冲突。

👉 **关键发现**：桌面版做浮窗的基建已经 90% 就位，只差一个新窗体 + 一条 `postMessage` 数据通道。

---

## 三、逐项评估

### ① 公积金开关 ⭐⭐⭐⭐⭐

**诉求**：很多公司不交公积金，关闭后不需要设置公积金比例。

**方案设计**

数据模型（在 `settings.tax` 下新增一个布尔字段，**保留原有比例值**）：

```js
tax: { ..., hasFund: true, fundRate: 12, coFundRate: 12, ... }
```

计算层加一道门（不改存储值，关掉再打开能恢复原设置）：

```js
const effFund   = () => state.settings.tax.hasFund ? state.settings.tax.fundRate   : 0;
const effCoFund = () => state.settings.tax.hasFund ? state.settings.tax.coFundRate : 0;

function insuranceMonthly(salary) {          // index.html:811
  const t = state.settings.tax;
  return insBaseMonthly(salary) * (t.socialRate + effFund()) / 100;
}
function companyMonthly(salary) {            // index.html:815
  const t = state.settings.tax;
  return insBaseMonthly(salary) * (t.coSocialRate + effCoFund()) / 100;
}
const fundMonthlyIn = salary =>              // index.html:819
  insBaseMonthly(salary) * (effFund() + effCoFund()) / 100;
```

UI：在「🧾 五险一金 · 个税」区加一行开关（复用现有 `.sw` 开关组件），关闭时给「公积金个人比例」「公司公积金比例」两行加 `.hidden`。

**必须一起改的联动点**（容易漏）：

1. **地区预设**：`applyRegion`（`index.html:777`）继续写入 `fundRate`/`coFundRate`，不特殊处理——开关关着时比例值只是被隐藏，重新打开即恢复。
2. **公司缴纳预览**（`index.html:1567`）：`hasFund=false` 时不能出现「公积金 ¥0.00」，改为「未缴纳公积金」。
3. **搭子台词**（`index.html:1029`）：`{fund}` 会变成「公积金月进 ¥0.00」，很出戏。建议顺手做 0 值兜底——`fundMonthlyIn() <= 0` 时把含 `{fund}` 的候选台词从池子里剔掉（这条同时也覆盖了"用户手动把比例填成 0"的情况）。
4. **Android**：`Pages.kt:341` 的公司缴纳展示条件是 `tax.enabled || coSocialRate > 0 || coFundRate > 0`，需补 `hasFund` 判断。

**兼容性**：现有 `deepMerge(DEFAULTS, ...)` 保证老数据缺 `hasFund` 时取默认 `true`，**向后兼容，不需要数据版本号**。

**工作量**：web ~25 行、Android ~30 行（Store.kt 加字段 + toJson/parse/copyFrom 各 1 行、SettingsPage.kt 加开关、Pay.kt 加门）、桌面端 0 行。

**风险**：低。唯一要注意的是上面 4 个联动点别漏。

---

### ② 社保金额手动填写 ⭐⭐⭐⭐

**诉求**：不知道公司交的社保比例，想直接填「医保账户到账金额」。

#### ⚠️ 先澄清一个口径问题（这条最重要）

这三个数在工资条上是**完全不同的东西**，混用会把税后工资算错：

| 名词 | 性质 | 典型数值 | 在哪看 |
|---|---|---|---|
| 社保（五险）个人扣除 | **从工资里扣走** | 基数 × ~10.5%（养老 8% + 医疗 2% + 失业 0.5%） | 工资条「个人社保扣款」 |
| 公积金个人扣除 | **从工资里扣走** | 基数 × 5%~12% | 工资条「公积金扣款」 |
| 医保账户月到账 | **打进医保卡** | 多数地区 = 个人缴的 2% 部分（部分地区另加统筹划拨，退休为定额） | 医保 App / 支付宝医保电子凭证 |

如果把「医保账户到账」（约 2%）当成「社保扣除」（约 10.5%）填进税后计算，**少扣了 8.5%，到手工资会明显虚高**。

👉 因此建议拆成两个独立功能，UI 上必须明确区分「扣款」和「到账」。

#### ✅ 最终方案：实发金额模式（用户拍板）

不再让用户推算比例，直接填工资条上看得见的三个数：

| 字段 | 说明 |
|---|---|
| 税前月薪 | **复用现有「月薪（税前）」设置项**，不新增字段——避免两处税前不一致 |
| 每月实际到手 | 新增，工资条上打卡的那个数（已扣五险一金与个税） |
| 医保账户每月到账 | 新增，医保 App / 支付宝医保电子凭证里看到的月度入账 |

计算方式：

```js
netRatio = 实际到手 / 税前月薪            // 净额系数
税后计薪金额 = 税前计薪金额 × netRatio     // 全链路乘这个系数即可
名义总扣除 = 税前月薪 − 实际到手
等效扣除比例 = (1 − netRatio) × 100%
```

**这个方案比原方案好**：

- 口径唯一无歧义——用户填的就是工资条上的数，不存在"医保到账当社保扣除填"的坑，**上一节那个中高风险自动消失**
- 与真实到手 100% 对齐，比"比例 + 税率表估算"更准
- 实现最简单：`taxFactor()` 只要在有 `netRatio` 时优先返回它，其余计算链完全不动

**代价（要如实告知用户）**：

- 拿不到「公司为你缴」的隐藏福利展示——不知道比例就推不出公司缴了多少。该模式下隐藏这一项，或另开一个可选的「公司每月缴多少」输入框让用户自己填
- 不再拆分显示"五险一金扣了多少、个税扣了多少"，只显示"总共扣了多少"

数据模型（建议）：

```js
tax: { ..., mode: 'rate' | 'net', netSalary: 0, medicalIn: 0 }
// netSalary：每月实际到手（元）；medicalIn：医保账户月到账（元，仅展示）
```

`mode` 默认 `'rate'`，老用户无感；两种模式独立存值，切换不丢数据。三端同步：`web/index.html` + `Store.kt`（`TaxCfg` 加字段 + toJson/parse/copyFrom）+ `SettingsPage.kt` + `Pay.kt`。

#### 2B. 医保账户月到账（展示型，不参与计算）

在五险一金区新增一行「医保账户每月到账（元）」，与现有的「公积金账户月进账」并列展示为账户进账。（最终方案里这一项已并入上面的实发金额模式。）

> 📌 附注口径提醒：严格说医保个人账户里的钱就是自己工资扣的 2% 换了个口袋，**不属于"白给的福利"**（公积金之所以算隐藏收入，是因为公司那部分才是白给的）。所以建议：
> - 把它作为**账户进账**单独展示，与公积金进账并列；
> - **不要**把它并入「公司为你缴」或任何"隐藏福利合计"；
> - 若将来要做"真实隐藏福利"，口径应改为只算公司缴纳部分（现有 `fundMonthlyIn` 把个人+公司都算进去了，属于"账户进账"口径，可保留，但别再叠加）。

**工作量**：web ~50 行、Android ~60 行、桌面端 0 行（比原方案更省，因为不用维护第二条扣除计算链）。

**风险**：低（原方案的口径混淆风险已消除）。剩余注意点：`netSalary > 税前月薪` 时系数 >1 要钳制/提示填写有误。

---

### ③ 浮窗（折叠小窗）⭐⭐⭐⭐（桌面）· ⭐⭐（Android 悬浮窗）

**诉求**：折叠成一个小窗，只显示今日已计薪数额 + 已摸鱼获得的薪资。

小窗内容建议：**今日已计薪 ¥xx.xx**（跟随主界面税前/税后）+ **摸鱼已赚 ¥xx.xx** + 一个状态点/时钟，双击回主窗。

#### ✅ 最终决定：只做 Windows 桌面端

Android 端不做（悬浮窗、通知栏金额、桌面小组件**全部不做**），网页版 PiP 也不在本轮范围内。

| 端 | 决定 | 可行性 | 成本 |
|---|---|---|---|
| **Windows 桌面版** | **做**：新增无边框 `TopMost` 小窗（C# WinForms），主窗 WebView2 每 500ms `postMessage` 推送金额，C# 更新 Label | 高，无权限问题 | 中低（C# ~200 行） |
| ~~Android~~ | **不做**（用户决定） | — | — |
| ~~网页版 PWA~~ | **本轮不做**；`Document PiP`（Chromium 111+）留作后续可选增强 | — | — |

#### Windows 桌面版实现要点

- 新增 `MiniForm`（约 260×100）：`FormBorderStyle.None` + `TopMost = true` + `ShowInTaskbar = false`
- **拖动**：`MouseDown` → `ReleaseCapture()` + `SendMessage(Handle, WM_NCLBUTTONDOWN, HTCAPTION, 0)`——现有代码已有 `SendMessage` P/Invoke 和 `WndProc`，直接复用
- **位置记忆**：照抄现有 `window.json` 的读法（`Num_()` 正则已现成），另存 `mini.json`
- **数据通道**：网页侧在 `uiTick()` 里节流 500ms 发 `postMessage('tick:¥123.45|¥6.78')`；`OnWebMessage` 加一个 `tick:` 分支（当前只处理 `min`/`close`/`ca:`）
- **附带**：右键菜单（置顶开关 / 透明度 / 关闭）、双击回主窗、设置页加「浮窗」开关
- 注意：`MinimumSize = 400×600` 是主窗体属性，不影响新窗体；单实例 Mutex 与新窗体共存无冲突

**风险**：低（限定单端、无权限问题）。**价值**：很高——这正是"摸鱼"场景最核心的体验。

<details>
<summary>已移除的方案（Android / 网页版，本轮不做，保留备查）</summary>

- **Android 通知栏金额**：`TimerService` 已有前台常驻通知，追加「今日已计薪 ¥x · 摸鱼 ¥y」约 20 行。
- **Android AppWidget 桌面小组件**：`AppWidgetProvider` + XML + 定时刷新，约 150-200 行。
- **Android 系统悬浮窗**：需 `SYSTEM_ALERT_WINDOW` 手动授权 + 商店敏感权限审核 + Android 12+ 后台启动限制，投入产出比低。
- **网页版 Document PiP**（Chromium 111+）：`documentPictureInPicture.requestWindow()` 把节点搬进置顶小窗，不支持时降级为页面内迷你条。

</details>

---

### ④ 首页「今日」页加摸鱼功能块 ⭐⭐⭐⭐⭐

**诉求**：快捷摸鱼，不用切换页面。

**现状问题**：摸鱼启停逻辑写在 `$('#breakBtn').addEventListener`（`index.html:1336-1357`）里，与 `page-rest` 的 DOM 强耦合，直接复制一份到首页必然状态不同步。

**方案**

1. **先重构**（这是前提）：抽出两个纯函数
   - `toggleBreak()` —— 只负责改 `state` 并 `save()`
   - `renderBreakControls()` —— 统一刷新所有摸鱼控件（首页块、摸鱼页大按钮、tabbar 圆钮的 `.live` 态）
2. **首页新增卡片**，建议放在 `stats2` 与 `pet-dock` 之间，保持「钱 → 时间线 → 统计 → 摸鱼 → 搭子」的节奏：
   - **未计时**：一行 `🐟 今日摸鱼 3 次 · 已赚 ¥12.34` + 右侧小按钮「开始摸鱼」
   - **计时中**：`00:12` 计时 + 「这段已赚 ¥x.xx」+ 「结束」按钮，非计薪时段复用 `BREAK_HINT` 提示
3. **默认紧凑**：今日页已经比较长，未摸鱼时只占一行，计时中再展开，避免信息过载。
4. 中央凸起圆钮保留不变，两者只是同一状态的两种入口。

**工作量**：web ~50 行（主要是重构）、Android ~60 行、桌面端 0 行。

**风险**：低。唯一要小心的是重构时别破坏现有的 `renderBreaks()` 列表渲染和 `uiTick()` 里的实时刷新。

---

## 四、排期建议

| 版本 | 内容 | 说明 |
|---|---|---|
| **v1.4.0** | ① 公积金开关、④ 首页摸鱼块、② 实发金额模式（三端）、③ Windows 桌面浮窗 | ✅ **全部已完成**；四个功能一次打包发布 |

---

## 五、通用配套事项（四个功能都要跟着做）

1. **数据模型三端同步**：web 改 `DEFAULTS` + `deepMerge`；Android 改 `Store.kt` 的 `Tax` 数据类，并同步 `toJson` / `parse` / `copyFrom` 三处（**这三处最容易漏**）。
2. **导出/导入兼容**：新字段走 `deepMerge` 默认值即可向后兼容，无需迁移代码。但建议**趁这次给导出 JSON 加一个 `version` 字段**（现在没有），后续再有破坏性变更会好办很多。
3. **README 更新**：「🧮 计算口径」表需补充三行——公积金关闭、金额模式、医保到账；功能列表里补上"浮窗""首页快捷摸鱼"。
4. **搭子台词兜底**：`{fund}` 在 0 值时要过滤含该占位的台词（见 ① 联动点 3）。
5. **三端对齐是本项目卖点**：①②③④ 最终都要三端功能一致（③ 浮窗按各端能力分批到位，需要在 README 里注明平台差异）。

---

## 六、风险汇总

| 风险 | 涉及 | 等级 | 应对 |
|---|---|---|---|
| 公积金关闭后文案/台词仍出现「¥0.00 公积金」 | ① | 低 | ✅ 已处理：预览/今日页税说明改文案，`petLine`/`Pets.line` 过滤含 `{fund}` 的台词 |
| 实发金额 > 税前月薪，净额系数 >1 | ② | 中 | ✅ 已处理：`netRatio()` 统一 `clamp`/`coerceIn` 到 `[0,1]`，填反了最多按 100% 计，不会虚高；到手未填时回落比例模式 |
| 实发模式下拿不到「公司为你缴」 | ② | 低 | ✅ 已处理：该模式下隐藏「公司为你缴 / 公积金进账」一行，只展示医保账户到账（标注"仅展示"），今日页税说明同步分支 |
| 浮窗金额漏 `¥` / 状态声明 TDZ | ③ | 低 | ✅ 已处理：见上文「过程中修掉的两个坑」 |
| 首页信息过载 | ④ | 低 | ✅ 已处理：未计时只占一行，计时中再展开 |
| 首页与摸鱼页两个按钮状态不同步 | ④ | 中 | ✅ 已处理：抽出 `toggleBreak()` + `renderBreakControls()`（Android 为 `RestPage.toggle()` + `TodayPage.renderFish()`）|
