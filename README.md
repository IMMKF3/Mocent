# 摸薪 Mocent 🐱

<p align="center">
  <img src="docs/logo-light.png" width="260" alt="摸薪 Mocent"/>
</p>

> 摸鱼的时候，工资也在涨。实时计薪 · 节假日感知 · 税后计算 · 公司缴纳档位 · 心愿单 · 摸鱼计时 · 年假管理 · 摸鱼搭子

治愈系工资可视化工具：填入月薪和作息，看今天的工资一秒一秒往上涨。**Android 原生（Kotlin）+ 网页版（PWA）** 双端，功能完全对齐。

<p align="center">
  <a href="https://github.com/IMMKF3/Mocent/releases/latest"><img src="https://img.shields.io/github/v/release/IMMKF3/Mocent" alt="Release"/></a>
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow" alt="License: MIT"/></a>
  <a href="https://github.com/IMMKF3/Mocent/releases/latest"><img src="https://img.shields.io/badge/Android-8.0%2B-brightgreen" alt="Android 8.0+"/></a>
  <a href="https://github.com/IMMKF3/Mocent/stargazers"><img src="https://img.shields.io/github/stars/IMMKF3/Mocent" alt="Stars"/></a>
</p>

## ✨ 功能

- 💰 **实时计薪**：今日已计薪逐秒跳动，时薪 / 分薪 / 秒薪实时展示
- 📅 **智能工作日**：周一至周五 − 法定节假日 + 调休上班，动态计算当月工作日；内置 2026 年数据，联网自动更新（[holiday-cn](https://github.com/NateScarlet/holiday-cn)）
- 🧾 **税后模式**：五险一金 + 月度个税估算，税前 / 税后一键切换，全链路数字联动
- 🏢 **公司缴纳与地区预设**：缴费档位可选（足额 / 80% / 60% / 自定义基数）；全国 31 省 + 333 地级市参数预设，在线更新、可手动覆盖；展示公司每月为你缴多少、公积金账户月进账多少
- 🎁 **心愿换算**：想买的东西换算成工作时间（¥1399 ≈ 20 小时 31 分 ≈ 2.56 个工作日），随计薪进度一点点攒，攒够时搭子会来庆祝
- 🐟 **摸鱼计时**：一键记录这段休息"值多少钱"（摸鱼时工资照常在涨）
- 🌴 **年假管理**：按工作日自动累积，当日按计薪进度实时增长；请假一键扣减
- 🐾 **摸鱼搭子**：小薪🐱 摸米🐰 缓缓🦫 带薪🐶 拿铁🦉 划水🐧 团子🐻——定时汇报收入、提醒喝水活动、加班劝休，戳一戳有反应、连戳会翻脸，各有性格台词库
- ⚙️ **可定制**：月薪、发薪日、作息、午休、加班计薪、专项扣除、地区参数……数据仅存本机，网页版支持导出 / 导入

## 🚀 快速开始

- **在线版**：<https://immkf3.github.io/Mocent/web/SalaryDance.html>
- **本地运行**：直接双击打开 `web/SalaryDance.html`，或 `cd web && python -m http.server 8642` 后访问 `http://127.0.0.1:8642/SalaryDance.html`

打开后在「设置」里填上月薪和作息即可。手机浏览器打开可"添加到主屏幕"当轻 App 使用。

## 🧮 计算口径

| 项目 | 规则 |
|---|---|
| 时薪 | 月薪 ÷（当月工作日 × 每日计薪工时），当月工作日 = 周一至周五 − 法定节假日 + 调休上班 |
| 计薪时段 | 上班 → 下班，扣除午休；周末与法定节假日不计；调休上班日照常计 |
| 税后 | 五险一金按个人比例（默认社保 10.5% + 公积金 12%）从基数扣除，个税按月度税率表估算，起征点与基数上下限可调 |
| 缴费档位 | 缴费基数可选足额 / 80% / 60% / 自定义，个人与公司同一基数，档位影响到手与公司缴纳展示 |
| 地区参数 | 省级基数上下限与比例支持预设自动填写（[人社通](https://m12333.cn/policy/pdkif.html) 汇总的年度参考值），手改自动转为自定义 |
| 公司缴纳 | 公司社保（各地常见约 21%~27%，含养老 16%、医疗、失业、工伤）+ 公司公积金（5%~12%，预设常用 12%），两部分按同一基数计算，不扣税、不影响到手，作隐藏福利展示 |
| 年假 | 余额基准日后每个工作日累积，当日按计薪进度实时增长 |

> 个税为月度简化估算，不含年终奖单独计税与企业缴纳部分，请以工资条为准。地区参数为参考值，以当地人社 / 公积金中心公布为准。

## 📁 目录结构

```
web/               # 网页版（单文件 + PWA，零依赖）
  SalaryDance.html
  manifest.webmanifest
  regions.json     # 全国地区五险一金参考参数（在线更新源）
  icon-*.png       # 浅色 / 深色图标
android/           # 安卓原生版（Kotlin，零第三方依赖）
  app/src/main/java/com/salarydance/app/
docs/              # README 展示图
```

## 📲 安卓版

**正式版下载**：[Releases · Mocent v1.0.0](https://github.com/IMMKF3/Mocent/releases/latest)

原生 Kotlin 实现（非网页套壳），仅 Kotlin 标准库 + 系统内置 org.json，零第三方依赖。

- **后台与提醒**：开始摸鱼即启动前台计时服务——通知栏显示实时秒表、锁屏可见、可在通知里一键结束，App 被清理 / 锁屏都照常计时；「摸鱼提醒推送」间隔可调、仅工作时段提醒；「低内存占用模式」降刷新率省电；提供后台权限与电池优化白名单引导
- **桌面图标切换**：设置里可选浅色 / 深色两版图标（深可可棕底，适配深色桌面）
- **全国地区参数**：31 省 + 333 地级行政区预设，启动时自动拉取最新 `regions.json`

自行构建（可选）：仓库自带完整 Gradle 工程，`cd android && gradle assembleRelease` 即可（需 JDK 17 与 Android SDK），产物在 `android/app/build/outputs/apk/release/`。

> 注意：`android/salarydance.keystore` 与 `android/keystore.properties` 已被 gitignore，请自行备份——签名丢了将无法覆盖安装旧版本。

## 🍎 iOS

iPhone / iPad 可直接使用网页版（Safari 打开 → 添加到主屏幕）。后续会根据实际情况考虑增加 iOS 适配。

## 📄 许可证

[MIT](./LICENSE)
