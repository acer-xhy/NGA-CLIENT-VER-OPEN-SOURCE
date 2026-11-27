# NGA 客户端（二次开发分支）

NGA 论坛的第三方 Android 客户端，基于 [Justwen/NGA-CLIENT-VER-OPEN-SOURCE](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE) 二次开发。

> 本仓库是上游的分支，不是上游的发布渠道。F-Droid、Google Play 上架的版本与本仓库构建的产物无关。

## 与上游的关系

上游的发布分支是 `master_release`（5.1.2 / code 5012），本仓库把它作为唯一的同步来源：从该分支拉取并合并进本地 `master`，`master` 是唯一的开发线。

| 对比基线 | 上游 | 本分支 | 差异 |
| --- | --- | --- | --- |
| `fork/master_release` | 5.1.2 / code 5012 | **1.0.0 / code 1000000** | 领先 49 个提交，449 个文件，约 +4130 / −1350 |

上游 13 个 Gradle 模块，本仓库 12 个：移除了统计上报模块 `:lib_bu_statistics`，其余 12 个模块名与上游一致，改动都发生在模块内部。

## 版本

本分支独立编号，从 `1.0.0`（code `1000000`）起，与上游的 5.1.2 明确区分。安装包见 [Releases](https://github.com/acer-xhy/NGA-CLIENT-VER-OPEN-SOURCE/releases)。

版本规则（以后发版固定按此执行）：

- `versionName` 用语义化版本 `MAJOR.MINOR.PATCH`
- `versionCode = MAJOR × 1000000 + MINOR × 1000 + PATCH`，如 `1.0.0 → 1000000`、`1.1.2 → 1001002`
- `MINOR`、`PATCH` 取 0–999；`versionCode` 不得超过 2,100,000,000（Google Play 上限）
- 发版只改根 `build.gradle` 的 `appVersion` 一处，`versionCode` 自动推导，格式和范围在配置阶段校验

> 历史提示：早期试制阶段曾用 `6.0.0`（code `6000000`）编号，该包未对外发布。其 `versionCode` 高于现在的 `1.0.0`（`1000000`），若设备上装过，需先卸载才能安装本版本；此后 `1.x` 内部可正常覆盖升级。

## 主要变更

- 工具链升到 AGP 9 一代：Gradle 9.9.2、AGP 9.5.2、Kotlin 2.3.21、KSP 取代 kapt，compileSdk 37。
- 清除 support 时代依赖并关闭 Jetifier，三个旧控件改为项目内自实现；ARouter、ButterKnife、Glide 注解处理器全部移除。
- 消息、账号、首页抽屉、板块页改为 Compose + Material3。
- release 构建 552s → 235s（本地冷构建；CI 发版流水线实测约 2m48s），APK 16.8 MB → 14.3 MB（开启 R8 与资源压缩，并整块移除统计 SDK）。
- 移除 Bugly 与友盟统计（`:lib_bu_statistics` 整个模块删除），包内不再有任何第三方上报。
- 修复仅 release 包才出现的崩溃与数据异常（R8 混淆 suspend 返回值、抽屉反射实例化、Paging 分页、侧边栏头像等）。
- 强制竖屏（设置页可关闭，关闭后跟随系统旋转）。
- 帖子内 B 站视频改为外链卡片，点击优先拉起哔哩哔哩 App，未安装时回退浏览器。
- 首页右上角菜单与侧边栏补充「浏览历史」入口。

逐条的代码改动与修复细节见对应版本的 Release 说明。

## 模块结构

| 模块 | 职责 |
| --- | --- |
| `:nga_phone_base_3.0` | 主 App：页面、ViewModel、Compose UI、资源与 Manifest |
| `:lib_core` | NGA 页面内容解析（`decode`）与业务核心逻辑 |
| `:lib_core_data` | 数据模型与实体 |
| `:lib_base_common` | 通用工具、`Router`、HTTP 辅助 |
| `:lib_base_network` | 网络栈封装 |
| `:lib_base_service_api` | 服务端接口定义 |
| `:lib_base_ui` | View 体系的基础控件与主题 |
| `:lib_base_ui_compose` | Compose 基础组件与 Material3 主题 |
| `:lib_base_logger` | 日志 |
| `:lib_bu_account` | 账号登录、用户信息 |
| `:lib_bu_message` | 私信 / 消息 |
| `:lib_module_debug` | 调试入口 |

包名沿用历史遗留的多个前缀（`gov.anzong.androidnga`、`sp.phone`、`com.justwen.androidnga`、`com.client.androidnga`），未做统一。

## 构建

环境要求：

- Android Studio 2026.x 或更新，JDK 17 及以上；实测使用 IDE 自带的 JBR（JDK 25）
- Android SDK Platform 37（SDK Manager 里显示为 `android-37.1`）、Build-Tools 37.0.0
- Gradle 由 wrapper 提供，无需本机安装

```powershell
# release（R8 + 资源压缩，需自备 keystore）
.\gradlew.bat :nga_phone_base_3.0:assembleRelease

# debug（applicationId 自动加 .debug 后缀，可与 release 共存）
.\gradlew.bat :nga_phone_base_3.0:assembleDebug
```

产物：

```
nga_phone_base_3.0/build/outputs/apk/release/nga_phone_1.0.0_<MMDDHHmm>.apk
```

## 签名

仓库里不含任何签名凭据（keystore、密码、别名都不入库），`build.gradle` 从外部读取，优先级从高到低：

1. 环境变量：`NGA_STORE_PASSWORD`、`NGA_KEY_ALIAS`、`NGA_KEY_PASSWORD`，`NGA_STORE_FILE` 可选
2. 项目根 `keystore.properties`（推荐本地开发使用，已在 `.gitignore`）
3. Gradle 项目属性：`-PstorePassword=...`，或 `~/.gradle/gradle.properties` 里的同名键
4. `storeFile` 都未指定时回退到根 `build.gradle` 的 `jks_path`（`../keystore/nga.jks`）

本地首次构建 release，在项目根建 `keystore.properties`：

```properties
storeFile=../keystore/nga.jks
storePassword=<keystore 密码>
keyAlias=<key 别名>
keyPassword=<key 密码>
```

缺配置时 release 构建会在任务图阶段直接失败并打印上面的配置方法，不会静默产出未签名的包。CI 侧由 GitHub Secrets 注入：`KEYSTORE_BASE64`、`NGA_STORE_PASSWORD`、`NGA_KEY_ALIAS`、`NGA_KEY_PASSWORD`。

## CI 与发版

`.github/workflows/` 下三个流程：

| 文件 | 触发 | 作用 |
| --- | --- | --- |
| `build.yml` | 推送 `master`、手动触发 | `assembleDebug` 冒烟，上传 debug 包 artifact |
| `release.yml` | 推送任意 tag；手动触发 | 校验 tag 与版本号一致 → 还原 keystore → `assembleRelease` → 验产物版本与签名 → 打包 mapping → 创建**草稿** Release（手动触发只构建、不建 Release） |
| `gradle-wrapper-validation.yml` | 推送、PR | 比对 wrapper jar 的 SHA-256 与 Gradle 官方清单，防止供应链替换 |

发版流程：

1. 改根 `build.gradle` 的 `appVersion`，提交并推送 `master`；
2. 打 annotated tag（必须与 `appVersion` 完全一致，如 `1.0.0`）并推送；
3. 等 `release.yml` 跑完，在 Releases 页面核对草稿的产物与说明；
4. 确认无误后手动 Publish —— 流水线只出草稿，不会自动对外发布。

## 许可证

GPLv2，沿用上游。
