# 璟 1.2.6 验证记录

本次处理自动认证长时间等待与成绩页闪退。身份核验不再等待学期接口，登录页支持取消和官方认证交接；本地学期筛选允许当前学期没有成绩。

| 项目 | 本次结果 |
| --- | --- |
| APK | `scvtc-zhixue-1.2.6.apk`，30,611,221 字节 |
| 包名 / 版本码 | `cn.scvtc.campus.next` / 15 |
| APK SHA-256 | `f953d265cf33a0d6f4ccc6c721a2b9234b7eb6ea7a355bbc7e3db96ce6ef0fe2` |
| 原签名证书 | `f4eb44ef0231232918e6bfe2bae00506d9bb77c49b6f516869fadaf2dcff7aed` |
| 构建源码 | [c6b60b92d14a9046a5e67efb4040897618d8ca37](https://github.com/zwwd1/scvtc-zhixue/commit/c6b60b92d14a9046a5e67efb4040897618d8ca37) |
| Release 编译、R8、资源压缩、lintVital | 通过 |
| APK CRC、签名、ZIP / ELF 对齐、源资产 | 通过 |
| 源码 ZIP | 1069 个文件与构建提交逐个 Git blob 校验一致 |
| 用户账号和密码明文 | 源码及解压 APK 条目扫描未检出 |

未连接手机，未运行应用、自动化功能测试或帧率测量。真实学校登录、验证码、会话到期恢复、清除数据后的首次提示与交互仍需使用者验收；编译通过不代表这些操作已经在设备上验证。未运行全量 Lint，正式打包要求的 lintVital 已完成。

原包名、原签名与本机数据格式沿用。失效密钥对应的旧密文保留，不能宣称重新登录会解密已失效的记录。已保存的普通课表继续保留，登录材料不进入导出、公开源码或 AI 请求。

[问题定位与修正](docs/LOGIN_REPAIR_20261011.md) · [本次更新](release-notes/v1.2.6.md) · [使用方法](docs/SCVTC_USAGE.md) · [隐私与加密](docs/SCVTC_PRIVACY.md)

默认川职包使用系统 WebView，保留校园功能、AI 和玻璃效果。完整插件平台包额外包含 Gecko、ONNX 与原模型，专用扩展仍可使用；两种分发均提供通用与 ARM64 文件。完整平台的原生库和模型与 1.2.4 逐项比较，SDK 3.6.0 线上一致性门槛通过。

`scvtc-zhixue-1.2.6-arm64-v8a.apk`：18,443,353 字节，SHA-256 `6e7d49ffec100157855d6fa2f18ed8c95bfcbafc593a4a82280fbc77a93f5e77`。

`scvtc-zhixue-1.2.6-platform.apk`：332,676,399 字节，SHA-256 `5a841e5dca196d53b7107be2fff8361c2e35adf59e48b9b15e7c8ce8861f22da`。

`scvtc-zhixue-1.2.6-platform-arm64-v8a.apk`：129,840,516 字节，SHA-256 `3170c2e397fbcb3784fcf4d769ef506b1c2981e445ced4ba09af8340d4e3d786`。

正式发布收据：[GitHub Release](https://github.com/zwwd1/scvtc-zhixue/releases/tag/v1.2.6)。服务器返回的每个 APK 大小与 SHA-256 均与本机原签名产物一致，源码标签指向构建提交，发布任务通过。应用内更新清单只使用这些已公开文件。
