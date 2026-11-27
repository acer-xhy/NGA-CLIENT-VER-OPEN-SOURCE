# 本工作空间规则

适用范围：本仓库（`NGA-CLIENT-VER-OPEN-SOURCE` 二次开发分支）的所有会话与任务。

## 发版前必须同步 README

在发布新版本到 GitHub（推 tag、创建 Release、把草稿 Publish）之前，**先检查项目根 `README.md`**，逐项与当前代码状态比对；需要修改的，必须在同一次发版提交里改完并一起推送，不允许先发版、后补文档。

比对清单：

| 检查项 | 与什么对比 |
| --- | --- |
| 版本号（对比表与本分支编号） | 根 `build.gradle` 的 `appVersion` |
| 上游基线与版本 | `git show fork/master_release:build.gradle` 的 `appVersionName/appVersionCode` |
| 差异统计（提交数、文件数、增删行） | `git rev-list --count fork/master_release..HEAD`、`git diff --shortstat fork/master_release..HEAD` |
| 模块数量与表格 | `settings.gradle` 的 `include`（增删模块时必须同步） |
| 包体积、构建耗时等量化数据 | 最新 release 产物与 CI 日志实测值 |
| 「主要变更」条目 | 本次新增的功能、修复、移除项是否都已写入 |
| 环境要求、签名说明、CI 触发条件 | `build.gradle`、`.github/workflows/*.yml` 的实际配置 |

## GitHub 远程操作

- 推送远程分支、推送 tag、发布正式版（`--draft=false` 或网页 Publish）之前，必须在本会话取得用户明确确认。
- 发版流水线默认只产出草稿 Release，Publish 一律手动完成。

## 产物

- 构建产物、报告等一律写入本仓库工作目录，按 `名称_YYYYMMDD.扩展名` 命名。
- 不生成飞书云文档、多维表格、飞书幻灯片，不推送群组或云盘；若某项操作必须上云，先询问。
