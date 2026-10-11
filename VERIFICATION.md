# 璟 1.2.5 验证记录

本次处理首次连接提示、学校 SSO 回调和失效密钥恢复；璟同时调整教务同步页布局、赞赏入口和安装包分发。

| 项目 | 本次结果 |
| --- | --- |
| APK | `scvtc-zhixue-1.2.5.apk`，30,606,929 字节 |
| 包名 / 版本码 | `cn.scvtc.campus.next` / 14 |
| APK SHA-256 | `12bbafa55665f1563f554da80349246041cb633cc9daa18dc8382cca7b4488c8` |
| 原签名证书 | `f4eb44ef0231232918e6bfe2bae00506d9bb77c49b6f516869fadaf2dcff7aed` |
| 构建源码 | [8c8be0114765a260db93eadff4c39d64325fb478](https://github.com/zwwd1/scvtc-zhixue/commit/8c8be0114765a260db93eadff4c39d64325fb478) |
| Release 编译、R8、资源压缩、lintVital | 通过 |
| APK CRC、签名、ZIP / ELF 对齐、源资产 | 通过 |
| 源码 ZIP | 1066 个文件与构建提交逐个 Git blob 校验一致 |
| 用户账号和密码明文 | 源码及解压 APK 条目扫描未检出 |

未连接手机，未运行应用、自动化功能测试或帧率测量。真实学校登录、验证码、会话到期恢复、清除数据后的首次提示与交互仍需使用者验收；编译通过不代表这些操作已经在设备上验证。未运行全量 Lint，正式打包要求的 lintVital 已完成。

原包名、原签名与本机数据格式沿用。失效密钥对应的旧密文保留，不能宣称重新登录会解密已失效的记录。已保存的普通课表继续保留，登录材料不进入导出、公开源码或 AI 请求。

[本次更新](release-notes/v1.2.5.md) · [使用方法](docs/SCVTC_USAGE.md) · [隐私与加密](docs/SCVTC_PRIVACY.md)

默认川职包使用系统 WebView，保留校园功能、AI 和玻璃效果。完整插件平台包额外包含 Gecko、ONNX 与原模型，专用扩展仍可使用；两种分发均提供通用与 ARM64 文件。完整平台的原生库和模型与 1.2.4 逐项比较，SDK 3.6.0 线上一致性门槛通过。

`scvtc-zhixue-1.2.5-arm64-v8a.apk`：18,439,061 字节，SHA-256 `c8ef633a0bd822dc3a33b2743a751ff7dc27ad16acc010dc5e97d107eb7e499d`。

`scvtc-zhixue-1.2.5-platform.apk`：332,669,951 字节，SHA-256 `2ef1660104fb9d5e751028749b73137a6a0aa837687dfd97686266fea148a25d`。

`scvtc-zhixue-1.2.5-platform-arm64-v8a.apk`：129,834,068 字节，SHA-256 `8c5a9a53b5e134f855686c03a2836ae6ae9f958c4712305c76d6c5261b463bd5`。
