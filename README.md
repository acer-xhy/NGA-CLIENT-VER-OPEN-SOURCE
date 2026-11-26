# NGA 客户端（二次开发分支）

NGA 论坛的第三方 Android 客户端，基于 [Justwen/NGA-CLIENT-VER-OPEN-SOURCE](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE) 二次开发。

> 本仓库是上游的分支，不是上游的发布渠道。F-Droid、Google Play 上架的版本与本仓库构建的产物无关。

## 与上游的关系

上游的发布分支是 `master_release`（5.1.2 / code 5012），本仓库把它作为唯一的同步来源：从该分支拉取并合并进本地 `master`，`master` 是唯一的开发线。

| 对比基线 | 上游 | 本分支 | 差异 |
| --- | --- | --- | --- |
| `fork/master_release` | 5.1.2 / code 5012 | **6.0.0 / code 6000** | 领先 27 个提交，428 个文件，+3564 / −1022 |

13 个 Gradle 模块与上游一致，改动都发生在模块内部。

## 版本

本分支独立编号，从 `6.0.0`（code 6000）起，与上游的 5.1.2 明确区分。安装包见 [Releases](https://github.com/acer-xhy/NGA-CLIENT-VER-OPEN-SOURCE/releases)。

## 主要变更

- 工具链升到 AGP 9 一代：Gradle 9.9.2、AGP 9.5.2、Kotlin 2.3.21、KSP 取代 kapt，compileSdk 37。
- 清除 support 时代依赖并关闭 Jetifier，三个旧控件改为项目内自实现；ARouter、ButterKnife、Glide 注解处理器全部移除。
- 消息、账号、首页抽屉、板块页改为 Compose + Material3。
- release 构建 552s → 235s，APK 16.8 MB → 15.8 MB。
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
| `:lib_bu_statistics` | 统计上报 |
| `:lib_module_debug` | 调试入口 |

包名沿用历史遗留的多个前缀（`gov.anzong.androidnga`、`sp.phone`、`com.justwen.androidnga`、`com.client.androidnga`），未做统一。

## 构建

环境要求：

- Android Studio（2026.x 或更新）与 JDK 17 及以上，建议直接用 IDE 自带的 JBR
- Android SDK Platform 37
- Gradle 由 wrapper 提供，无需本机安装

```powershell
# release（R8 + 资源压缩，需自备 keystore）
.\gradlew.bat :nga_phone_base_3.0:assembleRelease

# debug（applicationId 自动加 .debug 后缀，可与 release 共存）
.\gradlew.bat :nga_phone_base_3.0:assembleDebug
```

产物：

```
nga_phone_base_3.0/build/outputs/apk/release/nga_phone_6.0.0_<MMDDHHmm>.apk
```

release 使用 `signingConfigs.release`，keystore 路径写在 `nga_phone_base_3.0/build.gradle` 的 `jks_path`（`../keystore/nga.jks`），该文件不在仓库内，构建 release 前需要自备或改这一段配置。

## 许可证

GPLv2，沿用上游。
