# Lumen 课程表 1.2.9 发布验证 · 2026-10-13

1.2.9 已发布到 [GitHub Release](https://github.com/zwwd1/lumen-timetable/releases/tag/v1.2.9)。每个项目仅提供一份新版日常 APK，可沿用原包名和原签名覆盖安装。手机仍为 1.2.8；新包实机复测待连接，不把先前结果当作新包验收。

| 项目 | 本轮结果 |
| --- | --- |
| APK | Lumen-Timetable-1.2.9.apk，18,466,981 字节，ARM64 日常包 |
| 包名 / code | cn.scvtc.campus.next / 18 |
| SHA-256 | ac504d50f370205cf84699657dc198fc2fff88698d2aff3d5c8c966c8586c63c |
| 对应源码 | 2ea12c165cd7e5b2623ad513728ef38ed450a984，v1.2.9 标签已核对 |
| 编译输入指纹 | 04145c24d8dcf596d9da4fb17318136bee36a5b112dc0269c1c3706ae5efa806，Windows 与 Git 的纯行尾差别已另行核对，内容差异为零 |
| 构建与安装包检查 | Release、R8、资源压缩、lintVital、原证书、CRC、ZIP / ELF 对齐、资产、mapping、凭据明文检查通过 |
| 原生库 | 与 1.2.6 ARM64 日常版的三个库逐字节相同 |
| 定向回归 | 后台核心 5 项、会话与错误分类 10 项通过 |
| 公开文件 | GitHub 返回的 APK 大小和摘要与最终本机文件一致 |
| 发布门槛 | 原签名发布与 SDK / Wiki / 下载包 / 宿主契约 3.6.0 检查通过 |
| 新包实机 | 首次指南、流畅度、自然会话到期、真正后台提交与覆盖安装尚待复测 |

此前 1.2.8 在 Android 17 的受控慢 CAS 完成回调条件下，原生身份核验 14,937 ms。这不是 1.2.9 实机结果，不能据此承诺学校认证永久有效或系统后台始终准时运行。

本机交付目录 D:/APK/1.2.9-20261013 只放两个 APK。私人账号、签名材料、设备诊断与临时测试组件不进入仓库或发布文件。

# 历史本地交付：璟 1.2.7 · 2026-10-12

修正移动认证路由、连续可选提醒及本机密码与会话续期的交接。最终 ARM64 校园包已在 Android 13 真机覆盖安装。追加修复按用户要求留在本地，没有再次提交、推送或发布；公开提交仅作为构建基线，准确工作树由输入指纹与本地补丁记录。

| 项目 | 最终结果 |
| --- | --- |
| 交付 APK | `璟-课表成绩-1.2.7.apk`，18,463,921 字节，约 17.6 MiB，ARM64 校园日常版 |
| 包名 / 版本码 | `cn.scvtc.campus.next` / 16 |
| APK SHA-256 | `0ca1e88d13837f40bd0b595eb1ff3e2a1f1d6065c3a8828b887afbc57f4ea6de` |
| 原签名证书 | `f4eb44ef0231232918e6bfe2bae00506d9bb77c49b6f516869fadaf2dcff7aed` |
| 本地源码基线 | `dea67f36fe8b51ad0fcf1a2a6cabce70de07a21e`，包含其后的本地修复 |
| 实际编译输入指纹 | `fb90bacc324adc6e2e1bddd8efbf15842d87e786e22a0781d9eeae3d8245b06d`，946 个文件，包含新增源码 |
| Release 编译 / R8 / 资源压缩 / lintVital | 通过；完整插件平台编译入口继续检查 |
| 原签名 / CRC / ZIP、ELF 对齐 / 资产与 mapping | 通过 |
| 原生库与 1.2.6 ARM64 校园包比较 | 三个库逐字节相同 |
| 账号、密码明文扫描 | 未检出；原赞赏码 PNG 保持一致 |
| 定向 JVM 测试 | `CasAuthDiagnosticsTest` 3 项、`JwxtSessionRecoveryTest` 8 项、`SessionRecoveryCoordinatorTest` 10 项，失败与错误均为 0 |
| 认证脚本与官网 Vue 回归 | 会话失效、可选提醒关闭和连续提醒通过，强制验证仍被阻止自动操作 |
| Android 13 真机 | 原生自动登录、关闭进程后免输入重开、当前空学期与总体成绩通过；本轮应用 UID 的 crash 日志为空 |
| Android 17 / API 37 真机 | 原生自动登录、真实课表、历史学期、总体和当前空学期成绩、关闭进程重开和密码保留通过；检查区间无新增崩溃，用户自定义的五项底栏保留 |

没有清除用户手机数据；首次无数据邀请、自然会话到期、后台长时间冻结、全套菜单与帧率尚未完整实测。没有运行全量 Lint。包名、签名和已有数据格式沿用，认证失败保留缓存；学校强制认证仍需本人完成。

本地交付目录：`D:\APK\1.2.7-20261012`，只有晷、璟两个 APK。校园包使用系统 WebView，完整 Gecko / ONNX 插件平台源码仍保留，本次不额外交付平台安装包。[定位和实机结果](docs/LOGIN_NAVIGATION_20261012.md) · [本轮更新](release-notes/v1.2.7.md)。详细指纹、构建日志和私有真机证据在工作区 `output/verification`，不随安装包上传。

# 历史记录：璟 1.2.6

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
