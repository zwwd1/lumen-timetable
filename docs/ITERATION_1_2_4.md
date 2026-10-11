# 1.2.4 交互与实现

| 对应问题 | 修改文件 | 行为 |
| --- | --- | --- |
| 10 月 11 日登录截图 1、2 | LoginScreen.kt、LoginActivity.kt、CasAuthManager.kt | 首次连接弹窗；表单跟随键盘滚动，密码按账号加密记住；采用学校移动页的实际字段与处理器 |
| 成绩与学分（此前 IMG-07） | AcademicGradesRoute.kt、ScvtcRuntime.kt | 先展示本机记录，完整成绩先落库，学期切换在本地完成，学分独立更新 |
| 背景、入口与动效 | AppearanceSettingsManager.kt、CampusAppearanceSettings.kt、StartupLogoAnimation.kt | 原创简洁日夜背景，可关闭入场动画和增减底栏入口，玻璃参数保留 |
| 体积与升级 | app 构建配置、res、UpdateManifest.kt（璟） | 资源重新编码；璟提供 arm64 与通用包，包名、证书和原有数据格式延续 |

完整构建、证书、安装包、源码和发布结果见 [验证记录](../VERIFICATION.md)。没有运行手机或自动化功能测试，首次登录、旧安装覆盖升级、自然会话过期与手势体验仍需用户实际确认。
