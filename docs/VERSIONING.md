# Inkgram 版本与发布

`version.properties` 是版本号的唯一来源。不要通过环境变量或 `local.properties` 覆盖版本。

| 字段 | 用途 |
| --- | --- |
| `version.name` | Inkgram `MAJOR.MINOR.PATCH`；如 `1.3.1` |
| `version.app` | 每次发布递增的 Android 基础构建号；当前从 1824 延续 |
| `version.upstream` | 上游基线说明，不参与 Inkgram 版本计算 |
| `version.jni` / `version.leveldb` | 原生库缓存版本，变更相应原生实现时维护 |

主版本表示不兼容变更，次版本表示功能增加，补丁版本表示修复。
同一发布的所有 ABI/SDK 产物使用相同的基础构建号。Android 最终
`versionCode = version.app * 1000 + SDK * 100 + ABI`；保留原来的变体编码，
新构建号必须严格递增，最大为 2099999。不要把构建号重置为 1。

应用内基础版本使用 `version.name`。APK 版本名保留 ABI/SDK/调试后缀，
例如 `1.3.1-arm64-v8a`；文件为 `Inkgram-1.3.1-arm64-v8a.apk`。
Release 标题为 `Inkgram 1.3.1`，标签为 `v1.3.1`。

## 日常流程

- 在 `main` 开发，短期功能分支完成后合入。
- 将待发布变更记录在 `CHANGELOG.md` 的 `Unreleased` 下。
- 新仓库当前准备的是 **1.3.1**，无需再升版即可构建该版本。
- 后续升版先提交当前工作，再运行 `./scripts/version_bump.sh patch`
  （也可传 `minor`、`major` 或明确版本号）。脚本会同时递增构建号并把
  Unreleased 内容移入新版本条目，只改文件，不 reset、不提交、不推送。
- 检查并完善新版本更新日志，执行 `python3 scripts/version.py check`，
  测试后提交：`git add version.properties CHANGELOG.md && git commit -m "Release 1.3.2"`。

## 发布流程

1. 在**无空格**路径克隆并初始化依赖：`git submodule update --init --recursive`。
   准备本地 SDK、Rust、Telegram API 和原有签名密钥配置。签名密钥必须长期备份，
   使用相同密钥才能覆盖更新已安装 APK；不要提交密钥或密码。
2. 执行 `./gradlew :app:assembleLatestRelease -Pabi=arm64`。
3. 使用 SDK `aapt dump badging <APK>` 核对包名 `com.inkgram.app`、版本名、构建号和 ABI；
   使用 `apksigner verify --print-certs <APK>` 检查签名，并与上一版比较证书。
   完成安装、覆盖升级和核心翻页功能验证。
4. 创建不可变的附注标签：`git tag -a v1.3.1 -m "Inkgram 1.3.1"`。
   运行 `python3 scripts/version.py release-check --tag v1.3.1`，然后
   `git push origin main && git push origin v1.3.1`。标签 CI 会校验版本与更新日志，主分支和 PR 还会阻止版本/构建号回退。
5. 计算 APK SHA-256；将本版本日志与校验值写入说明文件，用
   `gh release create v1.3.1 <APK> --verify-tag --title "Inkgram 1.3.1" --notes-file <说明文件>` 发布。
   保存 R8 mapping、源码 commit、签名证书指纹与校验值以便排查问题。

已发布的标签和 APK 不覆盖、不移动；修复请发新补丁版本。
历史 `v1.3.0` 的 APK 内版本仍是 `0.29.2.1823-arm64-v8a`，保留该历史事实，
从 `1.3.1` 开始统一。CI 只校验版本元数据，不代替 APK 构建及真机验收。
