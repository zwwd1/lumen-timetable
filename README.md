<div align="center">
<img src="app/src/main/res/drawable-nodpi/campus_icon_portrait.webp" width="88" alt="Lumen 图标">
<h1>Lumen 课程表</h1>
<p>课程、成绩和考试，少绕几次路。</p>

[![下载](https://img.shields.io/github/v/release/zwwd1/lumen-timetable?label=下载最新版&color=2788ed)](https://github.com/zwwd1/lumen-timetable/releases/latest)
![Android](https://img.shields.io/badge/Android-13%2B-34a853?logo=android&logoColor=white)

**[下载 APK](https://github.com/zwwd1/lumen-timetable/releases/latest) · [图文上手](docs/SCVTC_USAGE.md) · [更新记录](https://github.com/zwwd1/lumen-timetable/releases) · [反馈问题](https://github.com/zwwd1/lumen-timetable/issues)**
</div>

智慧川职用起来有时真挺磨人。只是想看课表、查成绩，却要点来点去。我按自己的习惯折腾了 Lumen，把常用的教务信息放在一起。这是自己随心而动的作品，希望能帮到川职院的同辈。

这是川职 Next 的非官方适配版，由 zwwd1 维护。它更侧重教务查询；如果更喜欢课程编辑、作息和提醒，可以看看 [Gnomon 课程表](https://github.com/zwwd1/gnomon-timetable)。两款各自保存数据，选一款顺手的就行。

**[1.2.16 这次改了什么](release-notes/v1.2.16.md)**：今天、学分、教务同步、外观、文件和登录页接回滚动玻璃标题。上滑收起，下拉展开，内容经过标题下方时有透明模糊；文件和登录表单继续避让键盘。

## 界面和操作放在一起看

<table><tr>
<td align="center"><img src="docs/images/20261017/timetable.png" width="240" alt="Lumen 实机周课表"><br><b>课表</b><br>日 / 周切换，点课程看详情。</td>
<td align="center"><img src="docs/images/20261017/semester-empty.png" width="240" alt="Lumen 实机空学期成绩"><br><b>成绩与考试</b><br>本学期未出分，可看往期与总体。</td>
<td align="center"><img src="docs/images/20261017/effects.png" width="240" alt="Lumen 实机效果设置"><br><b>材质与动效</b><br>折射、色散、回弹及入场由你调整。</td>
</tr></table>

以上拍摄于 **2026-10-17，Redmi K40 / Android 13，Lumen 1.2.14**。不展示账号、密码与个人成绩。

## 第一次打开

| 步骤 | 操作位置 | 说明 |
| --- | --- | --- |
| ① 登录 | 首次连接邀请 → 川职学号与学校密码 | 密码在本机加密记住，后续尝试自动恢复会话 |
| ② 看课表 | 底栏 **课表** | 设置 → 当前课表详情设置，核对开学日期与节次时间 |
| ③ 查成绩 | **课程** → **成绩与考试** | 学期 / 总体 / 考试分别查看；已有记录先显示缓存 |
| ④ 查学分 | **课程** → **学分概况** | 区分已获学分与学校实际返回的毕业要求 |
| ⑤ 导出 | 设置 → **CSV / ICS / HTML** | 选择格式和保存位置；导入先预览再确认 |

学校要求验证码或安全确认时，在应用的**官方认证页**完成后继续。普通会话到期会尝试后台恢复；换手机、清数据、改密码或强制人工验证时仍需本人处理。[完整操作说明](docs/SCVTC_USAGE.md)

## 按自己的习惯调

设置 → **卡片、动效与校园助手**可调课程卡、底栏模糊、折射、色散和弹簧力度，也可开关入场、减少动态效果及小澄。液态玻璃总开关在设置 → 外观。**不按旧机型号锁定效果**；系统关闭动画时，应用仍会遵循。

两小时周期的后台任务会尝试更新，Android 和手机后台限制会影响执行时间。需要最新信息时手动刷新。教务同步页分别显示认证、课表、成绩等服务的保存时间和错误；失败保留缓存。

## 安装选哪个包

日常川职查询选默认 **campus** 包：使用系统 WebView，保留课表、成绩、学分、官方认证、编辑、提醒、组件及 AI。本次提供一份 **ARM64 campus APK**；其他架构需自行构建，请先确认设备支持 ARM64。

需要第三方嵌入浏览器扩展时才选 **platform** 包；它额外包含 Gecko、ONNX 和模型。两类包使用相同包名、签名和数据格式，可覆盖切换。实际附件和大小以[下载页](https://github.com/zwwd1/lumen-timetable/releases/latest)为准。

## 后面还会更新吗

我不会实时维护，更新看时间和精力。学校以后换接口，应用可能会失效，也不保证能及时修。有需要就 fork，**自行 vibe coding**；源码和构建方法都留在这里。

## 实现和构建

Kotlin / Compose 展示页面；川职认证在 cn/scvtc/campus，同步和投影在 com/tyust/course/scvtc，课程规则在 school-core。校验后写入 Room；密码、会话和查询缓存按账号加密，普通导出不带凭据。云服务与匿名统计暂停，AI 使用自行配置的服务。

JDK 21、Android SDK 37 和仓库的 Gradle Wrapper：

~~~powershell
.\gradlew.bat :app:assembleCampusRelease
# 需要完整插件平台时
.\gradlew.bat :app:assemblePlatformRelease
~~~

[构建与数据流程](docs/SCVTC_ARCHITECTURE.md) · [隐私说明](docs/SCVTC_PRIVACY.md) · [验证范围](VERIFICATION.md) · [平台发布约定](spec/PLUGIN-PLATFORM-RELEASE.md)

正式包沿用原包名和签名；自行签名的包不能覆盖正式包。感谢川职 Next、Miuix、AndroidLiquidGlass 及其他组件维护者。来源与许可完整保留：[LICENSE](LICENSE) · [SCVTC-NOTICES.md](SCVTC-NOTICES.md)。
