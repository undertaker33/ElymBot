# 版本 Git 锚点索引

更新时间：2026-05-18 20:40 +08:00

## 定位

本文件是 changelog 的 git 锚点索引。发布正文位于仓库根目录 `changelogs/`。

## 证据来源

本次只读取以下 git / 文件证据：

- `git tag --list 'v*' --sort=version:refname`
- `git for-each-ref refs/tags --sort=version:refname --format=...`
- `git log --all --grep='^Release v[0-9]'`
- `git log --first-parent --reverse --date=short`
- `git log v0.7.5..HEAD --reverse --stat`
- `app/build.gradle.kts`
- `changelogs/v*.md`

本次没有执行 Gradle、测试、发布命令或 Git 写入。

## 命名规则

- `v0.1.x` 到 `v0.7.x`：按小版本聚合，每个小版本一份 `changelogs/v0.N.x.md`。
- `v0.7.5` 之后：每个 patch 版本一份独立正文，例如 `changelogs/v0.7.6.md`、`changelogs/v0.8.14.md`。
- 每份正文只保留 `新增` 和 `修复` 两个章节，内容面向用户，不写成 commit log。

## 当前覆盖

| 范围 | Git 锚点 | 正文文件 | 状态 |
| --- | --- | --- | --- |
| `v0.1.x` | tag `v0.1.0`、`v0.1.5` | `changelogs/v0.1.x.md` | 已有 |
| `v0.2.x` | tag `v0.2.4` 到 `v0.2.9` | `changelogs/v0.2.x.md` | 已有 |
| `v0.3.x` | tag `v0.3.0` 到 `v0.3.9` | `changelogs/v0.3.x.md` | 已有 |
| `v0.4.x` | tag `v0.4.0` 到 `v0.4.2`、`v0.4.5` 到 `v0.4.9` | `changelogs/v0.4.x.md` | 已有 |
| `v0.5.x` | tag `v0.5.0` 到 `v0.5.8` | `changelogs/v0.5.x.md` | 已有 |
| `v0.6.x` | tag `v0.6.0` 到 `v0.6.3`、`v0.6.5` 到 `v0.6.9` | `changelogs/v0.6.x.md` | 已有 |
| `v0.7.0` 到 `v0.7.5` | tag `v0.7.0` 到 `v0.7.5` | `changelogs/v0.7.x.md` | 已有 |
| `v0.7.6` 到 `v0.7.9` | tag `v0.7.6` 到 `v0.7.9` | `changelogs/v0.7.6.md` 到 `changelogs/v0.7.9.md` | 已有 |
| `v0.8.0` 到 `v0.8.14` | tag `v0.8.0` 到 `v0.8.14` | `changelogs/v0.8.0.md` 到 `changelogs/v0.8.14.md` | 已有 |
| `v0.9.0` | tag `v0.9.0`，release commit `a97d398`，merge commit `6078ff3` | `changelogs/v0.9.0.md` | 已有 |
| `v0.9.1` | release commit `e0d0822`，当前 `app/build.gradle.kts` 为 `versionName = "0.9.1"` | `changelogs/v0.9.1.md` | 已有正文，缺少 tag |
| `v0.9.2` | tag `v0.9.2` | `changelogs/v0.9.2.md` | 已有 |
| `v0.9.3` | tag `v0.9.3`，release commit `39a0c10` | `changelogs/v0.9.3.md` | 已有 |
| `v1.0.0` | pending `uth-git` PR merge / tag closure | `changelogs/v1.0.0.md` | 待本轮 Git 闭环确认 |

## 当前缺口

- `v1.0.0` tag 尚未创建；本轮应在 PR 合并到远端 `master` 后创建并推送。
- `v0.9.0` tag 锚在 PR merge commit `6078ff3`，不是直接锚在 `Release v0.9.0` commit `a97d398`；正式发布说明以 tag/merge 闭环为准。
