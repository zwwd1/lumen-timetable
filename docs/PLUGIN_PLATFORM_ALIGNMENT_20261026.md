# Lumen 1.2.17 发布前的插件平台对齐

2026-10-26。公开平台 API、静态发布描述、Wiki、SDK 与 starter 下载包均为 SDK / 契约 3.7.2，API 主版本 3，审核规则 `2026-10-15.1`。

本地 10 个运行契约文件中，8 个已经与线上摘要相同；剩余差异是 Gecko 扩展版本和 iframe 窗口包装。此次从公开 SDK 的 `host-api/assets/academic-plugin/gecko/` 同步 `manifest.json` 与 `environment.js`，保持上游原始字节及摘要。

iframe 的 `contentWindow` 现在与 `unsafeWindow` 使用同样的窗口包装，可读取页面提供的函数。原生 DOM、回调接收者与跨源拒绝保持原有边界。没有新增插件权限，没有更改学校认证、查询或写入操作。

`scripts/tests/gecko-environment.cjs` 的四项 Node 回归覆盖原生 DOM / 回调、iframe 页面函数与包装稳定性、空窗口 / 无 iframe 环境，以及跨源访问错误传播，全部通过。真实 Gecko 设备与学校页面未在本轮操作。日常 campus 包仍使用系统 WebView；这次也维护共享扩展平台源码中的运行资源。

下载来源与摘要：

- [SDK](https://plugins.hidisiwa.xyz/downloads/plugin-sdk-v3.zip)：`63abdd82e4286bdebe452a7a5de2674a7925f447c624cc21ed3f7f79b24cabbc`
- [starter](https://plugins.hidisiwa.xyz/downloads/plugin-starter-v3.zip)：`09dc099659c014eb92ce702ebe83c8fd1da56370861e52e9b3944f1fa91cf338`
- [发布描述](https://plugins.hidisiwa.xyz/api/platform-release) · [Wiki 描述](https://plugins.hidisiwa.xyz/wiki/manifest.json)

本地安全 TLS 下载后使用仓库原有 `check_descriptor` / `check_archive` 验证运行资源摘要、下载摘要与契约锁，API、静态描述和 Wiki 一致。Windows curl 的吊销服务连接失败另行记录；GitHub Actions 中的 `scripts/check_plugin_platform.py` 在线门槛保持不变，必须独立通过后才发布。

公开平台描述中的 App 1.0.127 是上游应用版本；Lumen 使用自身包名与版本代码，本记录不将上游设备验收或新增功能算作 Lumen 的验收。
