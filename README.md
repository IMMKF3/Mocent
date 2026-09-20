# 摸薪 Mocent 🐱

<p align="center">
  <img src="docs/logo-light.png" width="260" alt="摸薪 Mocent"/>
</p>

> 摸鱼的时候，工资也在涨。实时计薪 · 节假日感知 · 税后计算 · 公司缴纳档位 · 心愿单 · 摸鱼计时 · 年假管理 · 摸鱼搭子 · 桌面浮窗

治愈系工资可视化工具：填入月薪和作息，看今天的工资一秒一秒往上涨。**Android 原生（Kotlin）+ Windows 桌面版 + 网页版（PWA）** 三端，功能完全对齐。

<p align="center">
  <a href="https://github.com/IMMKF3/Mocent/releases/latest"><img src="https://img.shields.io/github/v/release/IMMKF3/Mocent" alt="Release"/></a>
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow" alt="License: MIT"/></a>
  <a href="https://github.com/IMMKF3/Mocent/releases/latest"><img src="https://img.shields.io/badge/Android-8.0%2B-brightgreen" alt="Android 8.0+"/></a>
  <a href="https://github.com/IMMKF3/Mocent/releases/latest"><img src="https://img.shields.io/badge/Windows-10%2B-blue" alt="Windows 10+"/></a>
  <a href="https://github.com/IMMKF3/Mocent/stargazers"><img src="https://img.shields.io/github/stars/IMMKF3/Mocent" alt="Stars"/></a>
</p>

## ✨ 功能

- 💰 **实时计薪**：今日已计薪逐秒跳动，时薪 / 分薪 / 秒薪实时展示
- 📅 **智能工作日**：周一至周五 − 法定节假日 + 调休上班，动态计算当月工作日；内置 2026 年数据，联网自动更新（[holiday-cn](https://github.com/NateScarlet/holiday-cn)）
- 🧾 **税后模式**：两种方式任选——① 按五险一金比例估算（月度个税表）；② **实发金额模式**，直接填「每月实际到手」+「医保每月缴纳总额」，自动反推缴费基数与各险种比例反算出社保总比例，不用知道公司比例；税前 / 税后一键切换，全链路数字联动（公司没交公积金可一键关掉，不用填比例）
- 🏢 **公司缴纳与地区预设**：缴费档位可选（足额 / 80% / 60% / 自定义基数）；全国 31 省 + 333 地级市参数预设，在线更新、可手动覆盖；展示公司每月为你缴多少、公积金账户月进账多少
- 🎁 **心愿换算**：想买的东西换算成工作时间（¥1399 ≈ 20 小时 31 分 ≈ 2.56 个工作日），随计薪进度一点点攒，攒够时搭子会来庆祝
- 🐟 **摸鱼计时**：一键记录这段休息"值多少钱"（摸鱼时工资照常在涨）；今日页就有快捷入口，不用切页
- 🌴 **年假管理**：按工作日自动累积，当日按计薪进度实时增长；请假一键扣减
- 🐾 **摸鱼搭子**：小薪🐱 摸米🐰 缓缓🦫 带薪🐶 拿铁🦉 划水🐧 团子🐻——定时汇报收入、提醒喝水活动、加班劝休，戳一戳有反应、连戳会翻脸，各有性格台词库
- ⚙️ **可定制**：月薪、发薪日、作息、午休、加班计薪、专项扣除、地区参数……数据仅存本机，网页版支持导出 / 导入

## 🚀 快速开始

- **在线版**：<https://immkf3.github.io/Mocent/web/index.html>
- **本地运行**：直接双击打开 `web/index.html`，或 `cd web && python -m http.server 8642` 后访问 `http://127.0.0.1:8642/`

打开后在「设置」里填上月薪和作息即可。手机浏览器打开可"添加到主屏幕"当轻 App 使用。

## 🧮 计算口径

| 项目 | 规则 |
|---|---|
| 时薪 | 月薪 ÷（当月工作日 × 每日计薪工时），当月工作日 = 周一至周五 − 法定节假日 + 调休上班 |
| 计薪时段 | 上班 → 下班，扣除午休；周末与法定节假日不计；调休上班日照常计 |
| 税后 | 两种方式：① **比例模式**——五险一金按个人比例（默认社保 10.5% + 公积金 12%）从基数扣除，个税按月度税率表估算，起征点与基数上下限可调；② **实发金额模式**——填每月实际到手 + 医保每月缴纳总额，按「医保总额 ÷（医疗个人% + 单位%）」反推缴费基数，再按养老 / 医疗 / 失业 / 工伤 / 生育各险种比例算出金额并相加得到社保个人与公司总比例，自动回填；计薪按「到手 ÷ 税前」折算。关掉「公司有交公积金」后，个人与公司公积金比例均按 0 计，比例值保留备恢复 |
| 缴费档位 | 缴费基数可选足额 / 80% / 60% / 自定义，个人与公司同一基数，档位影响到手与公司缴纳展示 |
| 地区参数 | 省级基数上下限与比例支持预设自动填写（各省人社厅 2025/2026 年度公布的缴费基数上下限），手改自动转为自定义；可在设置里一键联网更新。注意部分地区**医保基数与养老基数不是一套**（河北、内蒙古、福建、浙江、深圳），反推时会自动取医保口径 |
| 五险比例 | 各险种分别按 [个人%, 单位%] 配置：养老 8%/16%、医疗 2%/6%~9.5%、失业 0.2%~0.5% / 0.5%~0.8%、工伤 0 / 0.2%~1.9%（取常见中间值）、生育 0 / 0.5%~1%；实发金额模式的反推即基于这套比例 |
| 公司缴纳 | 公司社保（各地常见约 21%~27%，含养老 16%、医疗、失业、工伤）+ 公司公积金（5%~12%，预设常用 12%），两部分按同一基数计算，不扣税、不影响到手，作隐藏福利展示 |
| 年假 | 余额基准日后每个工作日累积，当日按计薪进度实时增长 |

> 个税为月度简化估算，不含年终奖单独计税与企业缴纳部分，请以工资条为准。地区参数为参考值，以当地人社 / 医保 / 公积金中心公布为准；部分地区医保单独公布基数（见 `web/regions.json` 的 `medicalBase`）。五险比例各地每年调整，可在设置里联网更新。

## 📁 目录结构

```
web/               # 网页版（桌面版共用这一份页面）
  index.html
  manifest.webmanifest
  regions.json     # 地区参数
  icon-*.png
android/           # 安卓版
  app/src/main/java/com/salarydance/app/
desktop/           # Windows 桌面版
  src/Program.cs
  build.sh
docs/              # 展示图
```

## 📲 安卓版

**正式版下载**：[Releases · Mocent v1.0.0](https://github.com/IMMKF3/Mocent/releases/latest)

原生 Kotlin 实现（非网页套壳），仅 Kotlin 标准库 + 系统内置 org.json，零第三方依赖。

- **后台与提醒**：开始摸鱼即启动前台计时服务——通知栏显示实时秒表、锁屏可见、可在通知里一键结束，App 被清理 / 锁屏都照常计时；「摸鱼提醒推送」间隔可调、仅工作时段提醒；「低内存占用模式」降刷新率省电；提供后台权限与电池优化白名单引导
- **桌面图标切换**：设置里可选浅色 / 深色两版图标（深可可棕底，适配深色桌面）
- **全国地区参数**：31 省 + 333 地级行政区预设，启动时自动拉取最新 `regions.json`

自行构建（可选）：`bash android/build.sh` 一把梭——脚本自带工具链（JDK 21 + Gradle 8.7 + Android SDK，放在 gitignore 的 `.tools/` 下），不依赖系统环境；需要 release 包时用 `bash android/build.sh release`。若你本机已配好环境，也可按常规方式 `cd android && gradle assembleDebug`（**注意：AGP 8.5.2 要求 JDK 17–22，JDK 24 会直接报错**）。产物在 `android/app/build/outputs/apk/`。

> 注意：`android/salarydance.keystore` 与 `android/keystore.properties` 已被 gitignore，请自行备份——签名丢了将无法覆盖安装旧版本。

## 🖥️ Windows 桌面版

**正式版下载**：[Releases · Mocent](https://github.com/IMMKF3/Mocent/releases/latest)，两种包任选：

- **便携版** `Mocent-v*.zip`：解压即用，双击 `Mocent\Mocent.exe`，无需安装
- **安装包** `Mocent-v*-installer.msi`：标准 Windows 安装向导，**可选安装目录**，并可勾选是否创建**桌面快捷方式**与**开始菜单快捷方式**（两项默认勾选，取消即不创建）

安装向导流程：欢迎 → 许可协议 → 功能选择（桌面 / 开始菜单快捷方式复选框）→ 选择安装位置（可点「更改」换目录）→ 确认 → 安装。默认装到 `Program Files\摸薪\Mocent`（全机可用）；加 `perUser` 参数可改为按用户安装（免管理员，装到 `%LOCALAPPDATA%\摸薪\Mocent`）。

原生 C# 壳（.NET Framework 系统自带，仅约 1.4MB）+ WebView2 无边框窗口渲染网页版：顶栏即标题栏（拖动移动窗口、双击最大化），右上角集成最小化/最大化/关闭。与网页版共用同一份页面代码，功能永远同步。数据仅存本机（`%LOCALAPPDATA%\Mocent`），卸载重装都不丢设置。

- 🪟 **桌面浮窗**：设置里打开后会出现一个常驻置顶小窗，只显示「今日已计薪」和「摸鱼已赚」，可拖到屏幕任意位置（位置自动记住），双击回主窗口，右键调置顶 / 不透明度 / 关闭。主窗口收进托盘后浮窗依然在。

> 需要 Windows 10 及以上（系统自带 WebView2 运行时，一般无需额外安装）。首次运行如遇 SmartScreen 提示，点「更多信息 → 仍要运行」即可（应用未做代码签名）。

自行构建（可选）：`bash desktop/build.sh` 生成便携版；`bash desktop/build.sh msi` 追加生成 MSI 安装包，`bash desktop/build.sh msi perUser` 生成按用户安装版。系统自带 `csc.exe` 编译（不可用时自动回退 `dotnet build desktop/Mocent.csproj`）。WebView2 SDK 需事先解到 `.tools/webview2/pkg`（见 `build.sh` 顶部注释的下载命令）。

MSI 需要 **WiX Toolset 3.14**，脚本按「环境变量 `WIX` → 系统安装 → `.tools/wix3/wix`」的顺序自动查找；本机没有时会打印下载地址并跳过（不报错）。免安装用法：下载 [wix314-binaries.zip](https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip) 解压到 `.tools/wix3/wix/` 即可。

## 🍎 iOS版

iPhone / iPad 可直接使用网页版（Safari 打开 → 添加到主屏幕）。后续会根据实际情况考虑增加 iOS 适配。

## 📄 许可证

[MIT](./LICENSE)
