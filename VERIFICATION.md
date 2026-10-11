# 璟 1.2.4 验证记录

日期：2026-10-11。本轮重新构建 APK，沿用原签名；构建提交已在自己的仓库分支公开，与 APK 内嵌提交一致。之后追加的发布记录与更新清单不改变生产代码。

| 项目 | 结果 |
| --- | --- |
| APK | `scvtc-zhixue-1.2.4.apk`，332,665,139 字节 |
| 包名 / 版本码 | `cn.scvtc.campus.next` / `13` |
| APK SHA-256 | `477a2a49d533bbb5b72c3ed3ad9222137f9302839fc7ed6611c4291753d8d92c` |
| 原签名证书 SHA-256 | `f4eb44ef0231232918e6bfe2bae00506d9bb77c49b6f516869fadaf2dcff7aed` |
| 构建提交 | [ed8f98c69c5995cb6837d3fd71ca0c0db5cd74a7](https://github.com/zwwd1/scvtc-zhixue/commit/ed8f98c69c5995cb6837d3fd71ca0c0db5cd74a7) |
| 编译、R8、资源处理、lintVital、原签名打包 | 通过 |
| APK 完整性、ZIP 16 KiB 对齐、源资产、赞赏码原图、R8 mapping | 通过 |
| 原生库 | 59 个；64 位库逐个验证 16 KiB LOAD 对齐，32 位库验证 4 KiB；无原生库时不适用 |
| 用户账号及密码明文扫描 | 解压条目 UTF-8 / UTF-16LE 扫描未检出；不是完整安全审计 |
| 源码 ZIP 与发布构建树 | 1060 个文件逐个 Git blob 校验一致；不含历史 APK 或私钥 |

没有连接或控制手机，没有安装、运行自动化功能测试、测量帧率或调用实际 AI 服务。本轮未运行全量 Lint，不能写为通过；正式打包所需的 lintVital 与上述产物检查已完成，没有用历史检查替代本次结果。

设置页、升级后保留数据、两种外观、壁纸一致性、待机状态、底栏参数、成绩页动画、检查更新与下载、自然 Session 过期恢复、不同机型和 Android 17 仍需用户实际验收。AI 需配置自己的 HTTPS 服务及密钥。云服务继续暂停，既有账号、课程、手动编辑和自选壁纸不清除。

[本轮交互修改与使用步骤](docs/ITERATION_1_2_4.md) · [此前逐图修改](docs/OPTIMIZATION_20261010.md) · [使用方法](docs/SCVTC_USAGE.md) · [隐私与加密](docs/SCVTC_PRIVACY.md)。历史验证不能代替本轮验证。

插件平台 3.6.0 线上一致性检查通过。SDK 开发包、契约、文档和规则按原发布门槛校验；工具链自测依据上游公开收据，不是本机执行的应用测试。详情见 [平台同步记录](docs/PLUGIN_PLATFORM_3_6.md)。

arm64 包：`scvtc-zhixue-1.2.4-arm64-v8a.apk`，129,829,256 字节，SHA-256 `b459527bf8b59d44bfd8b5d80bb0706c833a7932d1bf28cada5fcc8cb43a593e`。与通用包的 DEX、资源、资产和 arm64 原生库逐项一致，保留通用包的其他架构；不是删减功能包。

## 实际发布

[璟 1.2.4](https://github.com/zwwd1/scvtc-zhixue/releases/tag/v1.2.4) 已公开；[自动发布任务](https://github.com/zwwd1/scvtc-zhixue/actions/runs/38115199024) 在提交 `428017a4ae77cbe2448f8ce7336189517700e174` 上通过，包括插件平台一致性门槛。标签指向构建提交 `ed8f98c69c5995cb6837d3fd71ca0c0db5cd74a7`，两份 APK 的 GitHub 资产字节数和 SHA-256 与本机结果一致。更新清单沿用原受信任签名密钥，新增 ARM64 下载项；其他架构保留通用包。此次追加只改变发布记录和更新清单。
