<div align="center">
<img src="app/src/main/res/drawable-nodpi/campus_icon_portrait.webp" width="88" alt="Lumen 图标">
<h1>Lumen 课程表</h1>
<p>川职课表、成绩、考试与学分查询。</p>

[![下载](https://img.shields.io/badge/version-1.2.17-2788ed)](https://github.com/zwwd1/lumen-timetable/releases/tag/v1.2.17)
![Android](https://img.shields.io/badge/Android-13%2B-34a853?logo=android&logoColor=white)

[简体中文](README.md) · [English](README.en.md)

**[下载 APK](#download) · [界面预览](#screenshots) · [功能总览](#features) · [第一次使用](#start) · [常见问题](#faq) · [文档](#docs)**
</div>

Lumen 把川职的课表、成绩、考试安排和学分信息放在同一个应用里。打开后先显示上次保存的数据，需要时再刷新；本学期还没出分，也可以切到往期记录查看。

这是川职 Next 的非官方适配版，由 zwwd1 维护。应用保留课程编辑、提醒和桌面组件；与 Gnomon 的区别见[两款应用对比](#compare)。

<details>
<summary><strong>目录</strong></summary>

- [下载与安装](#download)
- [界面预览](#screenshots)
- [功能总览](#features)
- [第一次使用](#start)
- [外观与同步设置](#appearance)
- [Gnomon 与 Lumen 怎么选](#compare)
- [数据与隐私](#privacy)
- [实现与构建](#build)
- [维护情况](#maintenance)
- [常见问题](#faq)
- [文档](#docs) · [来源与许可](#credits)

</details>

---

<a id="download"></a>

## 下载与安装

| 方案 | 用途与架构 | 获取方式 |
| :--- | :--- | :--- |
| **campus · 日常版 1.2.17** | 川职查询与日常课表；本次发布为 **ARM64** | [下载 APK](https://github.com/zwwd1/lumen-timetable/releases/download/v1.2.17/Lumen-Timetable-1.2.17.apk) · 17.62 MiB |
| **platform · 扩展平台** | 额外包含 Gecko、ONNX 与模型，用于需要嵌入浏览器的第三方扩展 | [自行构建](#build)，本次发行未附此包 |

[SHA-256 校验](https://github.com/zwwd1/lumen-timetable/releases/download/v1.2.17/Lumen-Timetable-1.2.17.apk.sha256) · [构建来源](https://github.com/zwwd1/lumen-timetable/releases/download/v1.2.17/BUILD_PROVENANCE.json) · [对应源码](https://github.com/zwwd1/lumen-timetable/tree/f3ec799d94fb7cf4e320ff98137ac0978bd10992) · [历史版本](https://github.com/zwwd1/lumen-timetable/releases)

日常使用选择 **campus** 即可：使用系统 WebView，保留本校课表、成绩、学分、官方认证、编辑、提醒、组件及 AI。其他设备架构需自行构建。

> [!NOTE]
> campus 与 platform 共用包名、签名和数据格式，符合版本与原签名要求时可覆盖切换。自行换签名的构建不能直接覆盖正式包。

---

<a id="screenshots"></a>

## 界面预览

<table><tr>
<td align="center"><img src="docs/images/20261017/timetable.png" width="240" alt="Lumen 实机周课表"><br><b>课表</b><br>日 / 周切换，点课程看详情。</td>
<td align="center"><img src="docs/images/20261017/semester-empty.png" width="240" alt="Lumen 实机空学期成绩"><br><b>成绩与考试</b><br>本学期未出分，可看往期与总体。</td>
<td align="center"><img src="docs/images/20261017/effects.png" width="240" alt="Lumen 实机效果设置"><br><b>材质与动效</b><br>调整玻璃效果、回弹和入场动画。</td>
</tr></table>

以上拍摄于 **2026-10-17，Redmi K40 / Android 13，Lumen 1.2.14**。不展示账号、密码与个人成绩。

---

<a id="features"></a>

## 功能总览

| 课程与教务 | 同步与数据 | 外观与扩展 |
| :--- | :--- | :--- |
| 日 / 周课表、课程详情和编辑 | 本机缓存优先显示 | 滚动收放的玻璃标题 |
| 成绩、学期与考试安排 | 按账号、学期和服务保存 | 折射、色散和弹簧参数 |
| 学分概况与实际毕业要求 | 后台尝试更新、手动刷新 | 减少动态效果与校园助手 |
| 课程提醒和桌面组件 | CSV / ICS / HTML 导入导出 | 自行配置的 AI 与可选 platform 扩展 |

[1.2.17 更新](release-notes/v1.2.17.md)调整了多个页面的标题和滚动效果，包括登录页的键盘避让。具体改动见版本说明。

---

<a id="start"></a>

## 第一次使用

首次显示六步中文指南，正文可以滚动，支持上一页、下一页与稍后再看。之后从 **设置 → 使用指南** 随时重看；已有账号可点“完成”关闭。

1. 安装日常版 **campus**，按首次引导输入川职学号和学校统一认证密码。需要额外验证时，在应用的学校认证页完成。
2. 打开底栏 **课表**，再到 **设置 → 当前课表详情设置** 核对开学日期和节次时间。
3. 从 **课程 → 成绩与考试** 查看学期成绩、总体记录和考试安排；从 **课程 → 学分概况** 查看已获学分与学校返回的毕业要求。
4. 到 **设置 → 教务同步与本机备份** 查看各项数据的更新时间。需要导出课程时，使用设置中的 **CSV / ICS / HTML** 入口。

没有网络时先查看已有记录，需要最新信息时手动刷新。登录、文件和外观操作见[使用说明](docs/SCVTC_USAGE.md)。

---

<a id="appearance"></a>

## 外观与同步设置

**设置 → 卡片、动效与校园助手** 可以调整课程卡、底栏模糊、折射、色散和回弹，也能关闭入场动画或隐藏小澄。液态玻璃总开关在 **设置 → 外观**。系统关闭动画时，应用也会跟随。

后台任务每两小时尝试更新，实际时间受 Android、电量、网络和手机后台设置影响。教务同步页分别显示课表、成绩等服务的保存时间和错误；某一项失败时可以单独重试，已有记录会保留。

---

<a id="compare"></a>

## Gnomon 与 Lumen 怎么选

| 你更常做什么 | 本仓库：Lumen | [Gnomon](https://github.com/zwwd1/gnomon-timetable) |
| :--- | :--- | :--- |
| 集中查看教务 | 课表、成绩、考试、学分和校园服务 | 课表与常用成绩查询 |
| 安排上课日常 | 保留编辑、提醒和桌面组件 | 更侧重课程编辑、作息与提醒 |
| 使用已有记录 | 查询结果本机缓存 | 课表离线可看 |

两款可以同时安装，账号、课表和设置分别保存，不会自动互相同步。

---

<a id="privacy"></a>

## 数据与隐私

学校登录和教务查询直接连接学校服务。密码和可恢复会话加密保存在手机里，普通课表导出不包含学校凭据。课程和作息存放在应用私有数据库中；普通课程记录没有做逐字段加密。

上游云同步和匿名统计目前暂停。AI 需要自行配置，使用插件、更新和其他在线功能时仍会联网。导出的课表可能包含教师、教室和课程安排，分享前请自行检查。具体保存方式见[隐私说明](docs/SCVTC_PRIVACY.md)。

---

<a id="build"></a>

## 实现与构建

Kotlin / Compose 展示页面；川职认证在 `cn/scvtc/campus`，同步和页面投影在 `com/tyust/course/scvtc`，课程规则在 `school-core`。身份、学期和数据完整性校验通过后才写入 Room；网络或认证失败时保留最后成功的记录。

![从学校认证、校验到本机保存与页面展示的数据流程](docs/images/project-data-flow.svg)

### 自行构建

准备 **JDK 21、Android SDK 37**，配置 `JAVA_HOME` 与 `ANDROID_HOME`，使用仓库的 Gradle Wrapper：

```powershell
git clone https://github.com/zwwd1/lumen-timetable.git
cd lumen-timetable
.\gradlew.bat :app:assembleCampusRelease
# 需要完整插件平台时
.\gradlew.bat :app:assemblePlatformRelease
```

两类构建共享学校认证与数据格式，扩展平台的专用实现和模型位于 `app/src/platform`。发布签名与配置保存在仓库外；具体要求见[构建原理](docs/SCVTC_ARCHITECTURE.md)及[平台发布约定](spec/PLUGIN-PLATFORM-RELEASE.md)。

复现当前正式包时使用版本标签 `v1.2.17`，也可直接查看[发行对应源码](https://github.com/zwwd1/lumen-timetable/tree/f3ec799d94fb7cf4e320ff98137ac0978bd10992)。自行签名不等于原签名覆盖升级。

---

<a id="maintenance"></a>

## 维护情况

这是个人维护的项目，更新没有固定时间。学校调整登录或教务接口后，部分功能可能暂时用不了。遇到问题可以提 Issue，附上版本、机型和复现步骤；想自己修改，也欢迎 fork。

<details>
<summary><strong>目前测过哪些情况？</strong></summary>

1.2.17 已完成原签名 Release、14 项 APK 检查和12 项数据库 / 日期回归及 4 项 Gecko 窗口回归。本轮未安装手机，小屏与大字体、学校登录、通知、后台和流畅度仍待实机验收。历史实机结果单列于验证记录，不算本版验收。

测试过程和未完成项见 [VERIFICATION.md](VERIFICATION.md)，安装包与源码的对应关系见 [BUILD_PROVENANCE.json](BUILD_PROVENANCE.json)。

</details>

---

<a id="faq"></a>

## 常见问题

<details>
<summary><strong>已有账号和课表，升级需要卸载吗？</strong></summary>

不需要。使用本项目原签名的正式包覆盖安装，并确认没有降级。如果提示签名不一致，先核对下载来源。卸载或清数据会删除本机密钥，之后需要重新登录。

</details>

<details>
<summary><strong>为什么还会要求学校验证码或重新认证？</strong></summary>

应用会尝试恢复普通过期会话；学校要求验证码、安全确认或其他本人操作时，仍需在官方认证页完成。失败时保留离线记录，不要反复提交密码。

</details>

<details>
<summary><strong>为什么后台同步不是准点发生？</strong></summary>

Android、电量、网络和厂商后台限制会影响调度。已有课程可从本机读取，需要最新教务信息时手动刷新。设置与故障处理见[图文上手](docs/SCVTC_USAGE.md)。

</details>

---

<a id="docs"></a>

## 文档

[打开文档目录](docs/README.md)：使用说明、隐私、开发和历史记录分开查阅。

| 想了解什么 | 阅读入口 |
| :--- | :--- |
| 登录、课表与教务操作 | [图文上手](docs/SCVTC_USAGE.md) |
| 本版改动与历史版本 | [1.2.17 更新说明](release-notes/v1.2.17.md) · [全部发行记录](https://github.com/zwwd1/lumen-timetable/releases) |
| 认证、缓存与构建 | [构建与数据流程](docs/SCVTC_ARCHITECTURE.md) |
| campus / platform 差异与发布 | [平台发布约定](spec/PLUGIN-PLATFORM-RELEASE.md) |
| 数据保存与实际验证 | [隐私说明](docs/SCVTC_PRIVACY.md) · [验证记录](VERIFICATION.md) |
| 项目来源与许可 | [原项目介绍](docs/UPSTREAM_README.md) · [LICENSE](LICENSE) · [来源说明](SCVTC-NOTICES.md) |

---

<a id="credits"></a>

## 来源与许可

本项目是川职 Next 的非官方适配版，由 **zwwd1** 维护。原生工程基于 [zhengfang-apk](https://github.com/znjhahaha/zhengfang-apk)，相关移植和组合代码的来源及许可见 [SCVTC-NOTICES.md](SCVTC-NOTICES.md)、[LICENSE](LICENSE) 与 [OpenWakeUp 许可](LICENSE-OpenWakeUp-AGPL-3.0)。感谢上游、Miuix、AndroidLiquidGlass 及其他组件维护者。
