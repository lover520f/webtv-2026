<div align="center">

# WebHomeTV

**面向 Android TV / 手机的影视播放器壳子**

用户自己找 JSON / 配置源，再在 App 内导入、管理、同步和使用。

[![Release](https://img.shields.io/github/v/release/motao123/webtv?label=release)](https://github.com/motao123/webtv/releases)
[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84)](#下载安装)
[![TV](https://img.shields.io/badge/Android%20TV-Leanback-4285F4)](#下载安装)

</div>

---

## 这是什么

`WebHomeTV` 是基于 **FongMi / CatVod** 生态增强维护的 Android 影音播放器壳子。

它**不内置内容，不分发 JSON**，而是提供一个更好用的壳子，帮助用户：

- 导入自己的点播 / 直播 / 壁纸配置
- 使用 WebHome 自定义首页
- 管理多个配置
- 在手机和电视之间同步配置与状态
- 继续观看、收藏、历史跨设备迁移

一句话：

> 这是一个“让用户自带配置更容易导入、管理和迁移”的播放器壳子。

---

## 当前亮点

| 能力 | 说明 |
| --- | --- |
| 配置导入增强 | 导入前预检，失败不覆盖当前配置，支持 URL / 文件 / assets |
| 配置管理 | 支持查看当前配置、历史配置、来源类型、最后使用时间 |
| 一键同步 | 局域网同步配置、历史、收藏、WebHome、设置、登录态等壳子状态 |
| 登录态学习 | 学习 Cookie / 登录态文件路径，供一键同步跨设备迁移网盘登录态 |
| 最近设备记忆 | 一键同步会记住上次设备，下次优先选中 |
| 继续观看 / 收藏 | 强化继续观看、收藏恢复与配置缺失时的回退路径 |
| 播放连续性 | 换源、换集、解析播放后保持进度与倍速等播放状态一致 |
| 多内核播放 | 支持 EXO / IJK / MPV 切换，默认 EXO |
| 协议兼容 | 已接入 Force、JianPian、Thunder、TVBus 等 FongMi 协议扩展 |
| 家庭过滤 | 按标签/关键词屏蔽不适合电视客厅展示的内容，并隐藏配置中心里的指定来源项 |
| 网盘检测 | 检测网盘分享链接有效性，WebHome 与本地 HTTP 均可调用（可关闭） |
| CSP 预热与兼容 | 配置加载后预初始化 Jar CSP，并隔离插件自带 protobuf，减少首次打开和版本冲突 |
| 多语言与界面 | 支持英文、简体中文、繁体中文切换，手机端可调界面缩放 |
| 播放记录同步管理 | 管理本机写入、远端同步和 Webhook，上报凭据在备份中自动脱敏并禁用 |
| MPV 配置管理 | 管理 mpv.conf、input.conf 和脚本 profile；安全导入、历史回滚，脚本默认禁用 |
| 搜索相关性 | 搜索结果按关键词匹配度过滤和排序，减少与关键词无关的结果 |
| WebHome 首页 | 每个 CSP 站点都可以配置独立网页首页 |
| Native SDK | 网页可通过 `window.fm` 调用受信任范围内的 App 原生播放、搜索、请求、缓存等能力 |
| 内嵌 VOD 播放 | WebHome 网页可把剧集数据直接交给播放器（`player.playVodInline`），无需站点接口即可开播 |
| 本地管理页 | 局域网访问 App 管理页面，支持同步、文件、调试等能力 |
| 弹幕开关 | 电视/手机设置总开关与播放页显示状态一致；关闭后换集、切内核、云搜或外部注入都不会隐式开启 |
| 播放控制增强 | 播放中重播、EXO/IJK/MPV 内核切换、0.1x–5x 倍速微调、章节选择、播放 OSD 与错误阶段提示 |
| 字幕与解码 | 字幕样式高级设置、双字幕、音视频解码偏好、音频直通与 Dolby Vision 输出策略 |
| 直播增强 | 失败自动换源、自定义 EPG 源与频道信息 |
| 预加载设置 | 按内核配置预加载开关、线程、缓存大小与预加载时长，并自动预加载下一集 |
| 弹幕与集数 | 弹幕密度/样式增强、集数标题紧凑显示 |
| 内置壁纸 | 27 款内置设计壁纸，可在设置中循环切换 |
| 安全加固 | 本地 HTTP 写操作强制 token、SSRF 拦截、CORS 白名单、路径遍历/XXE/DoS 防护 |
| 发布与数据保护 | Release 签名缺失会中止构建，数据库异常恢复前保留失败库副本 |
| Android 兼容性 | 跟进官方 FongMi api37，目标 SDK 更新到 37 |
| 自动更新 | 多源更新，适配不同网络环境 |

---

## 下载安装

最新版本：**v5.9.3**

项目主页（GitHub Pages）：https://motao123.github.io/webtv/

下载地址：

- [GitHub Releases](https://github.com/motao123/webtv/releases)

推荐 APK：

| 设备类型 | 推荐 APK |
| --- | --- |
| Android TV / 不确定架构 | `leanback-universal.apk` |
| Android TV / 新电视盒子 | `leanback-arm64_v8a.apk` |
| Android TV / 老盒子 | `leanback-armeabi_v7a.apk` |
| Android 手机 / 不确定架构 | `mobile-universal.apk` |
| Android 手机 / 新设备 | `mobile-arm64_v8a.apk` |
| Android 手机 / 老设备 | `mobile-armeabi_v7a.apk` |

> 不确定 CPU 架构时，优先下载对应设备的 `universal` 通用包。

---

## 首次使用

安装后先导入你自己的配置源。

常见方式：

- 粘贴配置 URL
- 选择本地配置文件
- 通过局域网同步从另一台设备导入

可导入的配置类型：

- 点播配置
- 直播配置
- 壁纸配置

项目本身不附带内容源，用户需要自行准备合法可用的 JSON / 配置地址。

---

## 适合谁

适合这些用户：

- 已经有自己的 TVBox / FongMi / CatVod 配置源
- 想在电视端使用 WebHome 自定义首页
- 想把手机和电视上的配置、历史、收藏同步起来
- 想把播放器壳子和内容源彻底分离

不适合这些用户：

- 希望安装后自带影视内容
- 希望仓库直接提供 JSON / 站源

---

## 主要功能

### 1. 配置导入与管理

- 导入前预检
- 导入失败不覆盖当前配置
- 查看当前配置 / 历史配置
- 显示来源类型和最后使用时间
- 删除配置前提示关联影响

### 2. 一键同步

可同步的壳子状态包括：

- 配置与站源
- 本地脚本 / Jar 数据
- WebHome 数据
- 搜索记录
- 继续观看
- 收藏
- 应用设置
- 登录态（Cookie / 登录态文件路径）

### 3. 登录态学习

- 在增强功能中打开「登录态学习」
- 开始学习 → 完成网盘登录 → 返回完成学习
- 可管理已选路径与待确认候选，并预览文件内容
- 一键同步勾选「登录态」即可跨设备迁移

### 4. 继续观看 / 收藏

- 记录最近观看进度
- 收藏内容可跨配置恢复
- 配置缺失时提供回退路径

### 5. 家庭过滤

- 在增强功能中开启
- 使用关键词屏蔽不适合电视端首页展示的内容
- 适用于原生首页 / 分类 / 搜索等内容流
- WebHome 页面也可以通过 `fm.config()` 读取过滤策略并自行配合隐藏

### 6. WebHome

- 站点可配置独立网页首页
- 支持透明背景
- 支持网页与原生能力联动
- 可通过 `window.fm` 调用搜索、播放、请求、缓存等能力

### 7. 本地管理页

- 局域网打开管理页
- 文件管理
- 同步控制
- 调试日志
- 配置相关操作

---

## WebHome SDK（简要）

常用能力：

| 能力 | 说明 |
| --- | --- |
| `fm.req(url, options)` | 原生请求，绕过普通浏览器 CORS 限制 |
| `fm.res(url, options)` | 生成本地资源网关地址 |
| `fm.play(url, title, options)` | 播放直链或 `push://` 地址 |
| `fm.vod(siteKey, vodId, title, pic)` | 打开原生详情 / 播放链路 |
| `fm.search(keyword, { direct })` | 调用原生搜索 |
| `fm.openLive()` / `fm.openKeep()` / `fm.openSetting()` | 打开原生页面 |
| `fm.history()` | 读取最近观看记录 |
| `fm.config()` | 获取当前配置与家庭过滤状态 |
| `fm.site()` | 获取当前站点信息 |
| `fm.cache` | 本地缓存能力 |
| `fm.back()` / `fm.reload()` | 处理返回与刷新 |

更完整说明见文档。

---

## 文档

- [应用完整开发文档](docs/应用完整开发文档.md)
- [WebHome 扩展脚本开发指南](docs/webhome-extension/README.md)

如果你是开发者，建议先看：

1. WebHome 首页配置
2. Native SDK
3. 配置导入与管理逻辑
4. 本地管理页与同步逻辑

---

## 构建

环境要求：

- JDK 21
- Android SDK 37
- 仓库内置 `gradlew`

常用构建命令：

```bash
bash gradlew :app:assembleMobileUniversalRelease
bash gradlew :app:assembleMobileArm64_v8aRelease
bash gradlew :app:assembleMobileArmeabi_v7aRelease
bash gradlew :app:assembleLeanbackUniversalRelease
bash gradlew :app:assembleLeanbackArm64_v8aRelease
bash gradlew :app:assembleLeanbackArmeabi_v7aRelease
```

GitHub Actions 支持手动触发，也会在推送 `v*` 标签时自动构建六个 APK 并创建 Release。

---

## 更新说明

App 内更新入口：

```text
设置 → 版本检查
```

更新策略：

- 检查到新版本后下载 APK
- 下载完成后交给系统安装器
- 如果自动安装失败，APK 会导出到 Downloads 目录，用户可手动安装

---

## 安全加固

针对代码安全审计结果，v5.6.0 修复了以下高危项：

- **本地 HTTP 认证**：管理/写操作端点（`/manage`、`/file`、`/upload`、`/action`、`/debug`、`/cache`、`/pan/check`、播放进度 API 等）现在即使来自本机回环地址也强制要求服务器 token；播放记录/进度端点纳入 token 保护范围。
- **DNS rebinding 防护**：回环请求校验 `Host` 头必须为 `127.0.0.1`/`localhost`/`::1`。
- **SSRF 防护**：WebResource 网关禁用自动重定向并逐跳校验；Webhook、网盘检测、WebHome 扩展加载、MPV HLS 代理均拒绝私有/回环/链路本地地址。
- **CORS 收紧**：播放进度/记录/网盘检测接口不再反射任意 Origin，仅对本地回环 Origin 允许携带凭据。
- **路径遍历防护**：`file://` 播放入口拒绝目录穿越与应用私有目录，仅接受媒体扩展名。
- **XXE 防护**：XML 结果解析拒绝 `DOCTYPE`/`ENTITY`。
- **DoS 防护**：DLNA 服务器与播放进度 API 对请求行、消息体大小设置上限。
- **信息泄露防护**：`site.info` 桥接方法不再对非受信页面开放；`pan.play` 对非受信页面要求播放确认并限制为 http/https。
- **Python 依赖下载**：HTTPS 强制校验证书，并限制重定向跳数。

已知可接受风险（记录在案）：

- 迅雷网盘签名密钥内嵌于客户端，属客户端签名固有限制，无法真正隐藏。
- 直播/媒体源普遍使用明文 HTTP，无法全局禁用；配置源与更新服务器已强制 HTTPS。
- Android `addJavascriptInterface` 无法按 iframe 区分信任来源，跨源 iframe 信任边界为平台限制。

---

## 开源说明

本仓库只提供技术实现和播放器壳子能力：

- 不内置影视内容
- 不维护站源
- 不分发 JSON
- 不提供内容接口

所有内容来源都应由用户自行配置，并确保合法合规。
