# NGA 客户端（二次开发分支）

NGA 论坛的第三方 Android 客户端，基于 [Justwen/NGA-CLIENT-VER-OPEN-SOURCE](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE) 二次开发，用于个人使用与学习。

> 本仓库是上游的分支，不是上游的发布渠道。F-Droid、Google Play 上架的版本与本仓库构建的产物无关。

## 与上游的关系

上游有两条线：`master` 停在上一个版本（4.2.2），实际发布走 `master_release`（5.1.2）。本仓库以 5.1.2 为起点继续开发，主线为 `master`。

| 对比基线 | 上游版本 | 本分支领先 | 文件差异 |
| --- | --- | --- | --- |
| `fork/master_release`（发布分支） | 5.1.2 / code 5012 | 27 个提交 | 428 个文件，+3564 / −1022 |
| `fork/master`（主线分支） | 4.2.2 / code 4022 | 59 个提交 | 563 个文件，+8939 / −7531 |

与发布分支之间的 27 个提交才是本仓库的实际增量（其中 23 个在 5.1.2 之后），与主线分支的 59 个差值包含了上游发布分支早期的功能重构。模块划分与上游一致，仍是 13 个 Gradle 模块，改动都发生在模块内部。

## 相对上游的改动

### 工具链：整体升到 AGP 9 一代

| | 上游 | 本分支 |
| --- | --- | --- |
| Gradle | 8.7 | **9.9.2** |
| AGP | 8.x | **9.5.2** |
| Kotlin | 2.0.21 | **2.3.21** |
| 注解处理 | kapt | **KSP 2.3.14** |
| compileSdk | 36 | **37（minor 1）** |
| minSdk / targetSdk | 31 / 36 | 31 / 36 |
| Java 编译目标 | 17 | 17 |

AGP 9 内置 Kotlin 支持，模块不再声明 `kotlin-android`；kapt 先迁到 `com.android.legacy-kapt`，随后被 KSP 取代，Room 的注解处理只跑一轮，不再出现 javac 与 kapt 重复处理。

### 依赖与架构

- 关闭 Jetifier（`android.enableJetifier=false`），清除全部 support 时代依赖。
- 原先依赖 support 时代的三个控件改为项目内自实现：`FloatingActionButton`、`FloatingActionsMenu`、`NgaSwipeRefreshLayout`。
- 移除 ARouter，改用项目内的 `Router`（位于 `lib_base_common`）。
- 消息列表 / 发送 / 详情、账号详情、首页抽屉、板块页等页面为 Compose + Material3（1.5.0）实现。
- fastjson2 升到 `2.0.59.android8`。

### 构建速度与产物体积

- release 全量构建 552s → 235s；缓存命中后的重建约 68s。
- R8 与资源压缩同时开启，APK 由 16.8 MB 降到 15.8 MB。新增 `nga_phone_base_3.0/src/main/res/raw/keep.xml`，保护 90 个用 `getIdentifier` 反射取用的板块图标不被裁掉。
- Gradle 与 Kotlin 守护进程内存分开设定（`-Xmx4g` / `-Xmx3g`），多模块并行时不再互相挤占 metaspace。

### 修复（多为 release 包才暴露的问题）

- R8 混淆导致 suspend 函数返回值被降级，登录态与部分接口返回值异常。
- 抽屉页用 `ViewModelProvider` 反射实例化 Kotlin `object`，启动即闪退。
- `UserManager.isActiveAccount` 未实现，进入个人中心崩溃。
- 消息详情 Paging3 复用 `nextKey`，分页崩溃。
- 侧边栏头像空白：补加载失败兜底，并恢复头像地址写回。
- 页面 JSON 直出被截断时改走网页解析，还原为原生数据渲染。

### 功能调整

- 全应用强制竖屏，不跟随系统旋转。
- 帖子内 B 站视频不再以可点击的播放器样式呈现，改为外链卡片；点击优先用 `bilibili://video/<bvid>` 拉起哔哩哔哩 App（`b23.tv` 短链同样处理），未安装时回退浏览器。
- 首页右上角菜单与左侧抽屉补充「浏览历史」入口，不必先进具体版面才能查看。
- 点赞 / 踩之后同步刷新楼层赞数。

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

命令行构建：

```powershell
# release（R8 + 资源压缩，需自备 keystore）
.\gradlew.bat :nga_phone_base_3.0:assembleRelease

# debug（applicationId 自动加 .debug 后缀，可与 release 共存）
.\gradlew.bat :nga_phone_base_3.0:assembleDebug
```

产物：

```
nga_phone_base_3.0/build/outputs/apk/release/nga_phone_5.1.2_<MMDDHHmm>.apk
```

release 使用 `signingConfigs.release`，keystore 路径写在 `nga_phone_base_3.0/build.gradle` 的 `jks_path`（`../keystore/nga.jks`），该文件不在仓库内，构建 release 前需要自备或改这一段配置。

## 上游同步状态

上游 `fork/master` 上另有 2 个提交尚未合并：

- `8862cdd4` 图片域名从服务端返回的结果中取
- `cdad1abb` 补充遗漏的版本更新提交

两者涉及启动图标格式（png / webp）转换、`settings.gradle` 与若干测试文件的增删，与本分支改动有重叠，合并需要手工处理。

## 许可证

GPLv2，沿用上游。
