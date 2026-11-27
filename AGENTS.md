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

## 删除操作（一律直接删除，不进回收站）

本工作空间内**任何**删除动作 —— 删文件、删目录、清构建产物、清临时文件、批量清理 —— 都必须直接删除，不得进入回收站。回收站里再次出现本工作空间的残留文件，即视为某次删除走了非直接路径，按违规处理并排查。

执行方式：

1. **必须**通过 WMI 派生一个独立于当前代理进程的干净进程执行删除，绕开运行环境对删除操作的回收站重定向。优先用：

   ```powershell
   Invoke-CimMethod -ClassName Win32_Process -MethodName Create `
     -Arguments @{ CommandLine = 'cmd.exe /c rd /s /q "D:\workspace\MY-NGA-CLIENT-VER-OPEN-SOURCE\target-dir"' }
   ```

   单文件用 `cmd.exe /c del /f /q "D:\...\file"`。WMI 不可用时再退回 `([wmiclass]'Win32_Process').Create(...)`，两种都失败则停下来向用户说明，不得改用其他删除方式。
2. **禁止**在工作空间内直接调用 `Remove-Item`、`del`、`rd`、IDE 删除、资源管理器删除来完成删除 —— 在本环境下它们可能被重定向进回收站。
3. 删除前先列出目标清单，确认每一项都在本工作空间范围内；不得删除用户自己创建、且本轮未明确要求删除的文件。批量删除前把清单发给用户看一眼。
4. 删除后**不要**清空回收站，也不要去还原或整理回收站内容 —— 那属于用户自己的空间，由用户处理。
5. 需要可回滚时，先复制到工作空间内的备份目录再删，不允许把回收站当兜底。
6. `git` 操作的删除（`git rm`、`git clean`、切分支）按 git 语义执行即可，不受本条约束；本条针对文件系统层面的删除。
