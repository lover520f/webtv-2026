# Changelog

## 5.9.3 — APP 绿色主题 (2026-08-18)

- 手机端默认主题色调整为绿色 `#43A047`，版本化迁移仅应用于从未明确选择主题的用户，保留系统、壁纸和自定义选择。
- Light Dialog 与硬编码蓝色交互状态改用配套绿阶，成功、健康、错误及编解码语义色保持不变。
- 手机启动页使用深绿色；电视端仅将蓝色实心焦点/动作背景改为深绿，保留白色与黄色焦点。
- 新增纯 Java `ThemeColorPolicy` 回归测试。

## 5.9.2 — 弹幕状态二次修复 (2026-08-18)

- 分离弹幕加载与显示偏好，设置页总开关同步两者，实际启用统一为 `load && show`。
- 播放器 controller 替换时清理旧实例，并原子恢复配置、启用状态与当前选中弹幕源。
- 统一电视、手机、直播与投屏的连接和播放器重建行为；无弹幕源时仍可关闭显示。
- 云搜结果绑定播放 generation 与当前集上下文，拒绝已切集或已取消后投递的旧回调。
- 外部弹幕与文件弹幕只选择来源，不再自动开启显示。
- 新增纯 Java 弹幕状态与 generation 回归测试。

## 5.5.97 — 配置导入 NPE 修复（真机验证发现） (2026-08-12)

真机验证暴露：清数据后重新添加接口配置时，`ConfigImport.preview*` 对可能为 null 的 `config.getName()` 调用 `.isEmpty()` → NullPointerException → "导入校验失败"。

### 修改

- `ConfigImport.previewVod/previewLive/previewWall`：将 `config.getName().isEmpty()` 全部改为 `TextUtils.isEmpty(config.getName())`（对 null 安全）

### 说明

- `Config.create(type, url)` 不设置 name，重新添加接口时 name 为 null 触发此 bug
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.96 — WebHome 视口注入修复（真机验证发现） (2026-08-12)

真机验证暴露问题：WebHome 页面加载后 `window.__fmViewport` 为 undefined、`--fm-*` CSS 变量为空。根因：页面加载前的布局变化先用 EMPTY 视口锁定了 `lastViewportKey`，页面加载后 `injectViewport` 因 key 相同被去重跳过，注入从未落到已加载页面。

### 修改

- `HomeWebController.onPageFinished`：页面加载完成后重置 `lastViewportKey = null`，强制对就绪页面重新注入视口

### 说明

- 修复后 WebHome 页面可获得 `window.__fmViewport` 与 `--fm-safe-*`/`--fm-chrome-mode` 等视口信息
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.95 — WebHome 集成（Step6：Raw 适配 + 内嵌点播） (2026-08-12)

WebHome 集成专项**第六步**：移植并接入 `WebHomeRawAdapter`（raw 内容代理）与 `WebHomeInlineVodStore`（内嵌点播）。

### 新增

- `web/WebHomeRawAdapter`：WebView raw 内容代理（git 仓库内容经 OkHttp 拉取 + 缓存 + 类型适配），接入 `HomeWebController.shouldInterceptRequest`（加载页面时创建、销毁时清除）
- `web/WebHomeInlineVodStore`：内嵌点播存储（Vod 内容按 id 存储，供播放器取用）

### 修改

- `HomeWebBridge`：新增 `player.playVodInline` 分发 + `playVodInline`（存内嵌点播并打开播放器）+ 简单 `resolveInlineEpisode`（直接 mediaUrl 的集数可解析）
- `HomeWebController`：JS 桥暴露 `player.playVodInline`

### 说明

- `WebHomeRawAdapter` 仅对 git 仓库作用域内的 raw 请求生效，不影响其他请求
- 内嵌点播支持**直接 mediaUrl** 的集数；`pageUrl` 需 JS 集数解析的场景暂优雅降级（后续可补 JS 往返解析）
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.94 — WebHome 集成（Step5：openVod 接线） (2026-08-12)

WebHome 集成专项**第五步**：接通 WebHome 页面 → 原生内容的 `openVod` 通道。

### 修改

- `HomeWebController.openVod()` → `Listener.openVod()`；JS 桥 `window.fongmi.app.openVod` 暴露
- `HomeWebBridge`：新增 `app.openVod` 分发 → `App.post(controller::openVod)`
- mobile `VodFragment.openVod()`：退出 WebHome 全屏并切回原生首页内容（`homeContent`）

### 说明

- WebHome 页面可调用 `app.openVod()` 切回原生点播内容；安全非破坏（新增方法/hook，不影响既有调用）
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.93 — WebHome 集成（Step4：宿主真实视口） (2026-08-12)

WebHome 集成专项**第四步**：宿主从窗口 insets 构建**真实安全区视口**并注入 WebHome 页面（此前视口为 EMPTY）。

### 修改

- mobile `VodFragment`：`ViewCompat.setOnApplyWindowInsetsListener` 监听窗口 insets，构建 `WebHomeViewport.from(insets, mode, 0)`（全屏时 chrome 模式为 `immersive`，否则 `normal`）并经 `applyWebHomeViewport` 注入

### 说明

- 本步让 WebHome 页面获得**真实的安全区/状态栏信息**（`--fm-safe-*`/`--fm-chrome-mode` 等 CSS 变量），配合 Step2/3 的视口管道
- 仅读取 insets，不做系统栏隐藏操作，安全非破坏
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.92 — WebHome 集成（Step3：视口管道） (2026-08-12)

WebHome 集成专项**第三步**：补全视口管道，让宿主可向 WebHome 控制器喂入视口数据（实际安全区/chrome 信息）。

### 修改

- `HomeWebController` 新增 `setViewport(WebHomeViewport)` / `getViewport()`（外部可注入视口并触发重新注入）
- mobile `VodFragment`：`getViewport()` 返回当前视口；新增 `applyWebHomeViewport(WebHomeViewport)` 接入 `mWeb.setViewport`

### 说明

- 本步为**视口数据管道**（host → 控制器 → 页面 `fmviewport`），不涉及系统栏操作，安全非破坏
- 真实安全区数据的计算（基于窗口 insets）与 chrome 模式（edge/immersive 隐藏系统栏）属更深 UI 子系统，后续单独评估
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.91 — WebHome 集成（Step2：视口注入 + chrome 管道） (2026-08-12)

WebHome 集成专项**第二步**：把简化版 `fmviewport` 注入升级为 `WebHomeViewport` 驱动的丰富协议，并加入 `normalChrome`/`setChrome`/`restoreChrome` 管道。

### 修改

- `HomeWebController` 新增 `viewport`/`lastViewportKey` 字段
- `injectViewport()`：改用 `viewport.script(...)`（安全区/手势区/chrome 模式 CSS 变量 + `fmviewport` 事件），并加 key 去重
- 新增 `setChrome(JsonObject)` / `restoreChrome()` / `normalChrome()`（经 `Listener` 钩子，当前为 no-op）

### 说明

- 视口协议更丰富（`--fm-chrome-mode`/`--fm-system-bars-hidden`/手势区等），即使 `viewport` 暂为 EMPTY 也输出完整结构
- chrome 模式尚未由宿主 Activity 实际应用（Step3）；`Listener` 钩子默认 no-op，现有行为不变
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.90 — WebHome 集成（Step1：chrome/视口基础） (2026-08-12)

WebHome 完整集成专项的**第一步**：移植 chrome 模式与视口（viewport）基础类，并扩展 `HomeWebController.Listener` 接口（非破坏性）。

### 新增

- `web/WebHomeChrome`：chrome 模式（normal/edge/immersive）常量与判定
- `web/WebHomeViewport`：安全区/手势区/chrome 视口信息构建（JSON/脚本/CSS 注入）

### 修改

- `HomeWebController.Listener` 新增 5 个 default no-op 方法：`applyDefaultChrome`/`setChrome`/`restoreChrome`/`getViewport`/`openVod`（不破坏现有实现者）

### 说明

- 本步为**非破坏性基础**：接口方法均为 default no-op，现有 WebHome 行为不变
- 后续步骤将接入 chrome()/视口注入，并在宿主 Activity 应用 chrome 模式（edge/immersive）
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.89 — WebHome 扩展支持 git 原始地址解析 (2026-08-12)

从 webhtv 移植 `GitRawUrlResolver`（纯 Java URL 解析工具），并接入 WebHome 扩展的远程加载：远程扩展配置里填 `github.com/...`、`cnb.cool/...`、`gitee.com/...` 等 git 仓库地址时，自动解析为 raw 上游地址，扩展清单可正常加载。

### 新增

- `web/GitRawUrlResolver`：解析 GitHub / Gist / Gitee / GitLab / CNB / Gitea 等 git 托管地址 → raw 上游地址（含 scope 标识）

### 修改

- `WebHomeExtensionRegistry.loadRemote`：远程扩展 URL 先经 `GitRawUrlResolver` 转成 raw 地址再加载

### 说明

- 纯 Java 工具，无 WebView 依赖；不影响已配置的 raw 地址或直接网址
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.88 — MPV 音频 DSP 完整链路（响度+稳定器+限幅器） (2026-08-12)

在 v5.5.87 响度归一化基础上，补全 MPV 音频 DSP：均衡器 + `loudnorm`（响度）+ `acompressor`（动态稳定/压缩）+ `alimiter`（输出限幅），全部为 ffmpeg/MPV 标准 `af` 滤镜。

### 修改

- `MpvPlayer.setAudioEqualizer` → 重构为 `setAudioDsp(freq, gainDb, loudness, compressorRatio, limiter)`，按需在 `af` 链追加 `loudnorm`/`acompressor`/`alimiter`；旧方法保留并委托
- `MpvPlayerEngine.applyAudioSetting`：`getStabilityAmount()` 映射为压缩比（1 + 稳定度×2.5），`shouldLimitOutput()` 启用限幅

### 说明

- **默认关闭** → 默认用户 `af` 链与均衡器时代完全一致，零行为变化；仅开启相应音效时才追加对应滤镜
- `safeSetPropertyString` 静默吞异常：即使打包的 MPV 不支持某滤镜也只不生效，不崩溃
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.87 — MPV 音频响度归一化（loudnorm） (2026-08-12)

为 MPV 内核补充音频 DSP 的第一步：开启响度归一化时，在 `af` 滤镜链末尾追加标准的 `loudnorm` 滤镜（默认关闭，不影响默认用户）。

### 修改

- `MpvPlayer.setAudioEqualizer(freq, gain, loudness)`：`loudness=true` 时在均衡器链后追加 `,loudnorm`；旧的 2 参方法保留并委托
- `MpvPlayerEngine.applyAudioSetting`：传入 `config.isLoudnessEnabled()`；`clearAudioEffect` 清空

### 说明

- **默认关闭** → 默认用户 `af` 链与 v5.5.84 完全一致（零行为变化）；仅在用户开启"响度"时才追加 `loudnorm`
- `safeSetPropertyString` 静默吞异常：即使打包的 MPV 不支持 `loudnorm` 也不会崩溃（仅该滤镜不生效）
- 响度/稳定/限幅的完整 DSP 仍需 mpvplayer audio 扩展，后续评估
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.86 — 播放记录删除事件 Webhook 补全 (2026-08-11)

补全播放记录同步的删除事件上报：此前删除记录**不触发** `playback.deleted` Webhook（Step2 时因 DeleteInput 未就绪而跳过）。现在删除/清空历史记录时向已配置的 Webhook 推送删除事件，配合删除墓碑防止远端复活。

### 新增

- `PlaybackRecord.deleted(input, cid)`：构建删除记录（scope/historyKey/siteKey/vodId + dedupeKey）
- `PlaybackEventCollector.onHistoryDeleted(input, cid)`：发送删除事件
- `PlaybackProgressWriter.notifyDeleted(History)` / `notifyCleared(int cid)`：工具方法

### 修改

- `History.delete()`（单条）/ `History.delete(cid)`（批量清空）：删除后触发删除事件 Webhook
- `PlaybackProgressWriter.deleteInternal`：`notify=true` 时触发 `onHistoryDeleted`

### 说明

- Webhook 仅在已配置且同步开启时发送；删除事件受墓碑保护，远端不会复活已删记录
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.85 — 直播源加载失败提示 + startFlow 判空 (2026-08-11)

排查"直播电视无法加载"：直播源（尤其经 GitHub 代理的源）间歇性失效时，App 此前**静默显示空白**、无任何提示。本次改进：

### 修改

- `LiveViewModel.parse`：直播源加载失败时（非 ExtractException 的网络/解析错误）也通过 url 通道 post 出 `Result.error`，让 `LiveActivity.onError` 显示清晰错误提示（新增 `live_load_failed` 字符串，三语言）
- `LiveActivity.startFlow()`（mobile + leanback）：增加 `mChannel == null` 判空，避免直播源加载失败（尚无选中频道）时 NPE

### 说明

- 直播源本身受网络/代理稳定性影响（如 gh.927223.xyz 等 GitHub 代理间歇 403/超时）；App 侧现在能明确提示"加载失败"，用户可切换到其他可用直播源
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.84 — MPV 内核音频均衡器支持 (2026-08-11)

为 MPV 播放内核补齐音频均衡器（此前仅 EXO 可用）：通过 MPV 原生 `af` 音频滤镜链（MPlayer 经典 `equalizer=f=<Hz>:g=<dB>` 语法）实现与 EXO 同款 10 频段均衡。

### 新增

- `MpvPlayer.setAudioEqualizer(frequenciesHz, gainsDb)`：把频段增益映射为 MPV `af=equalizer=...` 链；全零/空时清空 `af`
- `MpvPlayerEngine` 覆盖 `supportsAudioSetting()`/`applyAudioSetting()`/`clearAudioEffect()`，复用既有 `AudioEffectConfig`（频段 32Hz–16kHz，增益 dB = level/100）

### 说明

- 仅均衡器频段部分（纯 MPV `equalizer` af，无 LUT 冲突）；响度/稳定/限幅等 DSP 仍走 EXO 路线
- `af` 为 MPV 标准滤镜语法，设置失败静默（不影响播放）
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.83 — 镜像历史重建：CNB 仓库不再膨胀 (2026-08-11)

根治 CNB APK 镜像仓库此前累积到 ~10 GiB 的问题：每次发布改为**孤儿分支重建历史**（只含最新 APK/JSON）并 **force-push** 到 main，历史从 ~600 MB 重新开始，不再随发布单调增长。

### 修改

- CI "Sync to mirror"：`git checkout --orphan` 重建 + `git push --force`，仅保留最新 6 个 APK + JSON
- APK 仍以 git blob 存储（CNB raw 才能服务真实文件）；LFS 已被证明会返回指针、破坏下载，不采用
- 防呆：dist 无 APK 时跳过重建，避免清空镜像

### 说明

- force-push 失败时自动保持旧历史（`continue-on-error`，不影响发布）
- 首次生效后，镜像 main 分支历史即重置为最新发布的内容；CNB 侧旧对象由其 GC 回收
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.82 — 播放记录远端同步（Step4b，最后一块） (2026-08-11)

移植 webhtv 播放记录同步的最后一块：**远端同步引擎**，从自建中转服务拉取/合并播放记录与删除事件，支持增量游标与站点过滤。

### 新增

- `playback/`：`RemoteSyncConfig`（远端源配置：URL/token/站点过滤/间隔/游标）、`PlaybackRemoteSyncStore`（配置持久化）、`PlaybackRemoteSyncPayload`（响应解析：upserts/deletions/nextSince 多格式兼容）、`PlaybackRemoteSyncResult`、`PlaybackRemoteSyncer`（定时拉取 + 对账）
- `PlaybackProgressWriter` 新增远端变体：`applyFromRemoteSync`/`deleteFromRemoteSync`（带墓碑与远端时间戳校验，防旧写入复活/过期覆盖）

### 修改

- `App.startBackgroundServices` 启动 `PlaybackRemoteSyncer`（首次 3s、周期 5min，受同步总开关与隐身模式保护）

### 说明

- 需自建中转服务（Cloudflare/Deno/Vercel/Go/Rust，见 webhome-devkit 文档）并配置远端源后才生效；无配置时静默不动作
- 至此播放记录同步四步全部完成：只读上报 / Webhook / 进度写入 / 删除墓碑 / 远端同步
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.81 — 更新下载回退：universal APK 超 CNB 限制时走 GitHub (2026-08-11)

universal APK（含 arm64+armv7 双 ABI 原生库）无法安全压到 CNB raw 的 100 MiB 限制内（lxml 为蜘蛛体系必需，不可裁剪）。改为**更新下载时自动探测**：CNB 对 APK 返回 4xx（413 超限）时回退到 GitHub Releases 下载。

### 修改

- `Updater.onConfirm`：对 CNB 的 APK URL 做 HEAD 探测，不可达（413/网络失败）时改用 GitHub Releases URL
- arm64/armv7（<100 MiB，CNB 可服务）仍走 CNB 快源；universal（超限）自动回退 GitHub

### 说明

- 探测在后台线程执行，5s 超时，不影响主线程
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.80 — 镜像同步回滚 LFS（修复 CNB 下载） (2026-08-11)

v5.5.79 引入的 **git-lfs** 镜像同步，导致 CNB raw 对 `*.apk` 返回 **134 字节的 LFS 指针**而非 APK 本体，反而破坏了 App 内更新下载。回滚为 **git blob** 存储（CNB raw 才能服务真实文件），并保留"只留最新 APK"以控制镜像体积。

### 修改

- CI 镜像同步：移除 `git lfs track`，并清理残留 `.gitattributes` 的 LFS 行；APK 作为 git blob 推送
- 仍保留 `resConfigs`（arm64/armv7 APK 已 <100 MiB）与"只保留最新版本 APK"

### 说明

- arm64/armv7 APK **99/86 MiB**（<100 MiB），CNB raw 可正常下载；universal（双 ABI）仍 102-103 MiB 略超限
- 镜像历史此前已累积 ~10 GiB，需在 CNB 仓库手动清理（本版起不再因 LFS 指针占用额外空间）
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.79 — 更新修复：APK 压缩至 CNB 100 MiB 限制内 (2026-08-11)

CNB 镜像的 raw 文件端点有 **100 MiB 硬限制**，此前 APK（100-105 MiB）通过 CNB 更新时报 `raw 文件大小 100 MiB 超过限制`。

### 修改

- `app/build.gradle`：`resConfigs "en", "zh-rCN", "zh-rTW"` 裁剪依赖的未用语言资源
  - arm64 APK：105 → **99 MiB** ✓（<100 MiB）
  - armv7 APK：87 → **86 MiB** ✓
  - universal（双 ABI）：103 → **102-103 MiB**，仍略超限（见说明）
- CI：新增 "Check APK sizes" 步骤，超限输出 warning
- CI 镜像同步：改用 **git-lfs** 存储 APK 并**只保留最新版本**（删除旧 APK），避免镜像仓库继续膨胀（此前累计到 ~10 GiB）

### 说明

- universal APK 因同时含 arm64+armv7 双 ABI 原生库仍略超 100 MiB；arm64/armv7 设备（绝大多数，含 Pixel 7 Pro）已可正常通过 CNB 更新
- 已有 10 GiB 镜像历史需在 CNB 仓库手动清理（重写历史或重建镜像），本版起不再继续增长
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.78 — 播放记录删除 API（构建修复） (2026-08-11)

修复 v5.5.77 编译错误：`HistoryDao.delete(int, String)` 由 `void` 改为返回 `int`（删除处理需统计受影响行数；对忽略返回值的既有调用方源码兼容）。

## 5.5.77 — 播放记录删除 API + 删除墓碑 (2026-08-11)

从 webhtv-main 移植播放记录同步的删除能力：`DELETE` 语义的 `POST /api/playback/progress/delete`，配合删除墓碑防止旧写入复活已删记录。

### 新增

- `bean/PlaybackDeleteTombstone`：删除墓碑模型（config/scope/site/vod/时间戳）
- `playback/PlaybackDeleteTombstoneStore`：墓碑持久化（**Prefers 实现**，避免 Room 迁移）+ 90 天保留 + 单调最新判定
- `playback/PlaybackProgressDeleteInput`：删除请求（全量/站点/单条/多别名解析/confirm 校验）
- `PlaybackProgressWriter.deleteFromLocalApi`：本地删除（先记墓碑再删 History+Track）
- `HistoryDao` 补充 `findAll(cid)`

### 修改

- `PlaybackProgressApi` 新增 `/api/playback/progress/delete` 端点（批量）
- 写入路径增加墓碑检查：已删记录不复活
- `PlaybackProgressApplyResult` 补回删除相关重载

### 说明

- 全量清理需 `confirm=true`，按站点清理需 `siteKey`；受同步总开关 + 本机写入开关 + 隐身三重保护
- 墓碑用 Prefers 而非 Room（webhtv 用 Room 实体需 DB 迁移，此处避免）；远端同步（Step4b）后续
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.76 — 播放进度写入 API（构建修复） (2026-08-11)

修复 v5.5.75 编译错误：`PlaybackProgressApi.doResponse` 补回 `catch (Throwable)` 以捕获 `readBody` 抛出的 checked `Exception`。

## 5.5.75 — 播放进度写入 API（/api/playback/progress） (2026-08-11)

从 webhtv-main 移植播放记录同步的**第三步**：新增本机播放进度写入 API，外部工具/爬虫可将播放进度写入本地历史。

### 新增

- `playback/`：`PlaybackProgressInput`（进度写入请求，含多别名解析/校验）、`PlaybackProgressApplyResult`、`PlaybackProgressBatchResult`、`PlaybackProgressWriter`（本地写入核心：匹配本地历史/按需新建/写盘）
- `server/process/PlaybackProgressApi`：`POST /api/playback/progress`（单条）与 `/api/playback/progress/batch`（批量），含 CORS
- `HistoryDao` 补充 `findByKeyPrefix(cid, prefix)` 查询

### 修改

- `server/Nano` 注册写入端点

### 说明

- 受 `ViewingRecordSyncStore` 总开关 + 本机 API 修改开关 + 隐身模式三重保护
- 仅本地写入；删除/远端同步/墓碑因依赖额外存储与更复杂对账，暂未移植
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.74 — 播放记录 Webhook 上报 (2026-08-11)

从 webhtv-main 移植播放记录同步的**第二步**：新增 Webhook 上报能力，播放开始/进度/暂停/恢复/结束/停止时向配置的 Webhook URL 推送播放记录。

### 新增

- `playback/`：`WebhookConfig`（Webhook 配置，含字段预设 basic/standard/full/anonymous/custom）、`PlaybackWebhookStore`（配置持久化 + 失败熔断）、`PlaybackWebhookSender`（异步上报 + 重试 + 幂等头）、`PlaybackHttpHeaders`、`ViewingRecordSyncStore`（总开关）
- `PlaybackEventCollector`：采集播放事件（start/progress/pause/resume/stopped/ended）并触发 Webhook
- `PlaybackRecord` 补回 `withEvent()`/`copy()`；`PlaybackFieldPolicy` 恢复 `webhook()`/`anonymous()`/`custom()`

### 修改

- mobile/leanback `VideoActivity`：`getPlayer`/`onStateChanged`/`onPlayingChanged`/`onTimeChanged`/`onStop`/`onDestroy` 接线到 `PlaybackEventCollector`

### 说明

- 本步仅 Webhook 上报；进度写入 API 与远端同步因依赖 DB/删除墓碑/远端配置，留待后续
- 默认关闭（`ViewingRecordSyncStore.isEnabled` 默认 true，但无 Webhook 配置时不发）；删除事件处理暂未移植
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.73 — 当前播放记录只读 API（构建修复 2） (2026-08-11)

修复编译错误：恢复 `PlaybackRuntime.playerFor` 方法（此前编辑误删），`PlaybackRuntime` 保持 public 供 `VideoActivity` 接线。

## 5.5.72 — 当前播放记录只读 API（构建修复） (2026-08-11)

修复 v5.5.71 的编译错误：`PlaybackRuntime` 由包私有改为 `public`，并公开 `setPlayer`/`updateHistory`，供 `VideoActivity`（其他包）接线。

## 5.5.71 — 当前播放记录只读 API（/api/playback/current） (2026-08-11)

从 webhtv-main 移植播放记录同步能力的**第一步**：新增只读的"当前播放"上报端点，供爬虫/外部工具查询本机正在播放的片源。

### 新增

- `playback/` 包：`PlaybackRuntime`（当前播放运行时单例）、`PlaybackRecord`（播放记录模型）、`PlaybackFieldPolicy`（字段策略）、`PlaybackConfigIdentity`（配置身份）、`PlaybackApi.current()`（只读获取当前播放记录）
- `server/process/PlaybackRecordApi`：暴露 `GET/POST /api/playback/current?siteKey=xxx`（含 CORS），返回当前播放记录（schema/state/position/duration/progress/speed 等）
- `server/Nano` 注册该端点

### 修改

- mobile/leanback `VideoActivity`：播放启动时 `PlaybackRuntime.setPlayer/updateHistory` 填充运行时，`onDestroy` 清理

### 说明

- 仅只读上报，不含写入/远端同步/Webhook（后续 Step2/Step3 逐步加入）
- 隐身模式或无可读记录时返回 404；字段策略与 webhtv 一致（`apiSafe` 不含 episodeUrl 等敏感字段）
- `client` 字段适配为 webtv 的 `BuildConfig.FLAVOR`
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.70 — 站点弹窗手动排序记忆 (2026-08-11)

从 webhtv-main 移植 `SiteOrderStore`：手机端点播源弹窗支持**长按拖拽排序**，顺序按站点配置持久化（每份配置独立记忆），下次打开弹窗自动恢复。

### 新增

- `setting/SiteOrderStore`：站点顺序持久化（`site_dialog_order_<cid>`，按配置分离），`sortSites` 恢复 / `save` 保存
- mobile `SiteAdapter`：`sortSites` 恢复顺序；新增 `drag(from,to)` 完成拖拽移动并保存
- mobile `SiteDialog`：挂载 `ItemTouchHelper`（上/下拖拽），长按拖动站点即可调整顺序

### 说明

- 仅手机端（leanback 为遥控器 UI，无触屏拖拽，与 webhtv 范围一致）
- 手动顺序优先于站点健康排序（`SiteOrderStore.sortSites` 最后执行，仅在存在已保存顺序时生效）
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.69 — 软件 DSP 音频效果通路（EXO，API 24+） (2026-08-11)

从 TV-fongmi 移植软件 DSP 音频管线，补齐此前「仅硬件均衡器」缺口：不仅 API 28+ 的硬件 `DynamicsProcessing` 均衡器，现还支持 API 24–27 的软件均衡器，以及响度归一、动态稳定、前级/Boost 增益与软限幅等 DSP 处理。

### 新增

- **软件 DSP 管线**（纯 Media3 `BaseAudioProcessor`，无外部依赖）：`AudioEffectProcessor` / `AudioSoftwareEqualizer`（10 频段 IIR 均衡）/ `AudioLimiter` / `AudioLoudnessNormalizer` / `AudioStabilizer`
- `ExoAudioEffectController`：统一调度硬件（API 28+ 均衡器）与软件（API<28 均衡器 + DSP）两条通路
- `AudioEffectProcessor` 内实现声道混合（立体声/单声道/反相，ITU-R BS.775 下混），替代 TV-fongmi 依赖的 mpvplayer 扩展 `AudioChannelMix`

### 修改

- `ExoUtil.buildPlayer` / `buildRenderersFactory` 支持注入 `AudioProcessor`：通过覆写 `NextRenderersFactory.buildAudioSink`，把 `AudioEffectProcessor` 挂进 `DefaultAudioSink` 音频链
- `ExoPlayerEngine` 改用 `ExoAudioEffectController`，把软件 processor 注入音频渲染器；`applyAudioSetting()` / `clearAudioEffect()` / `rebuild()` 同步更新

### 说明

- 音频效果默认关闭，仅用户启用时生效；软件通路主要服务于 API<28 或需要响度/稳定等 DSP 的低版本设备
- 声道混合为内部实现（与 TV-fongmi 的 mpvplayer 扩展算法等效），未引入额外依赖
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.68 — 直播 Python 源 m3u8 兼容代理 (2026-08-11)

从 webhtv-main 移植 `server/process/M3u8`：为 Python 直播源发出的 `127.0.0.1:9978/m3u8?url=` 链接提供本地代理转发，补全 UA/Referer/Origin 头，并重写播放列表内的分片地址回到本代理，保证直播可正常播放。

### 新增

- `server/process/M3u8`：独立 `Process` 实现，命中 `/m3u8` 时转发上游，区分播放列表与分片流
  - 播放列表：重写 `.m3u8` 内分片/`#EXT-X-KEY`URI 为本代理地址，禁缓存
  - 分片流：透传 Range/Content-Range，支持 HEAD 与断点续传
- `server/Nano` 注册 `process.add(new M3u8())`

### 安全/兼容

- 仅接受 `http(s)` 目标，转发异常捕获返回 500，不向外暴露内部请求
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.67 — 播放健壮性加固（Issue #7 闪退修复） (2026-08-11)

针对 Android 17（Pixel 7 Pro，手机版）闪退反馈，加固播放/换源路径的异常防护，避免 Spider/解析或引擎重建抛出的异常直接导致应用崩溃（借鉴 webhtv 的 try/catch(Throwable) 防崩思路）。

### 修改

- `VideoActivity`（mobile 与 leanback）`getPlayer()`：`mViewModel.playerContent(...)` 包裹 `try/catch(Throwable)`，Spider/解析异常不再崩溃，转为走既有错误提示流程（`onError`）
- `VideoActivity`（mobile 与 leanback）`onPlayerKernel()` / `onDecode()`：切换内核/编码时包裹 `try/catch(Throwable)`，引擎重建失败不闪退
- `PlayerManager.switchPlayer()`：重建后 `setMediaItem()` / `seekTo()` 包裹 `try/catch(Throwable)`，媒介装载异常不闪退

### 说明

- 崩溃仅凭 issue 描述（无 logcat 堆栈）无法唯一归因，本版先做整体健壮性加固；若仍复现，需补充崩溃日志定位视频 GL 特效或音频均衡器路径
- 软件 DSP 音频管线（TV-fongmi 完整版）因依赖 `AudioChannelMix` 且 webtv 的 mpvplayer 为精简 shim，暂缓移植
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.66 — 音频效果（EXO 均衡器，API 28+） (2026-08-11)

从 TV-fongmi 移植音频均衡器（首期，EXO 优先）：轨道弹窗新增「设置」入口（音频轨显示），可切换音效预设与开/关。

### 新增

- **音频均衡器**: 基于 Android `DynamicsProcessing`（API 28+）硬件均衡器，挂到 EXO 音频会话，支持 12 个预设（自然/人声/影院/低音/高音/流行/摇滚/舞曲/电子/嘻哈/爵士/古典）+ 关闭
- 移植自包含引擎：`AudioEffectConfig` / `AudioEffectBands` / `AudioEffectPreset` / `AudioPresetLevels` / `AudioChannelMode` / `AudioEqualizerController`；`AudioSetting` 偏好
- 新增 `AudioSettingDialog`（启用开关 + 预设 chips），轨道弹窗音频轨进入

### 修改

- `ExoPlayerEngine` 实现 `applyAudioSetting()` / `clearAudioEffect()` / `supportsAudioSetting()`（读取 EXO 音频声道数构建 EQ 配置）
- `PlayerEngine` 新增 `applyAudioSetting()` / `clearAudioEffect()` / `supportsAudioSetting()` 默认实现；`PlayerManager` 新增对应委托与 `canSetAudioSetting()`

### 说明

- 首期仅含均衡器预设 + 限幅（`DynamicsProcessing`），**不含**软件 DSP 通路（需自定义渲染器注入 `AudioChannelMix`，留待后续）与 MPV 滤镜；API 28 以下优雅降级为「不支持」
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.65 — 视频色彩调节支持 MPV (2026-08-11)

补全视频色彩调节的 MPV 支持：此前仅 EXO 可用，现通过 MPV 原生 VO 属性（亮度/对比度/饱和度/伽马/色相）实现同款调节。

### 新增

- **MPV 视频色彩调节**: `MpvPlayer` 新增 `setVideoEqualizer()`（设置 `brightness`/`contrast`/`saturation`/`gamma`/`hue` VO 属性）
- `MpvPlayerEngine` 重写 `supportsVideoEffects()` / `applyVideoProfile()` / `clearVideoProfile()`，将 `VideoEffectProfile` 换算为 MPV 属性范围
- 引擎统一：`PlayerEngine` 新增 `applyVideoProfile()` / `clearVideoProfile()` 默认实现；`ExoPlayerEngine` 将色彩效果控制器收纳到引擎内部并实现 profile 应用

### 修改

- `PlayerManager` 改用引擎级 `applyVideoProfile()`/`clearVideoProfile()`，`canSetVideoSetting()` 现对 EXO 与 MPV 均可用
- 视频设置对话框在 EXO 与 MPV 下均可开启调节

### 说明

- MPV 采用 VO 属性实现，未覆盖色温/锐度/阴影（MPV 无对应原生属性，仅亮度/对比度/饱和度/伽马/色相生效）；与 LUT 互不冲突
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.64 — 视频色彩调节 (2026-08-11)

从 TV-fongmi 移植视频色彩调节能力（EXO 内核）：轨道弹窗新增「设置」入口，可调整饱和度/对比度/亮度/伽马/色相/色温/锐度/阴影。

### 新增

- **视频色彩调节**: 轨道弹窗新增设置按钮（视频轨显示），基于 Media3 `GlEffect`/`BaseGlShaderProgram` 实现
- 移植自包含效果引擎：`VideoEffectProfile` / `VideoEffectPreset` / `ColorToneAdjustEffect` / `DetailAdjustEffect` 及各自 shader program / `ExoVideoEffectController`
- 新增 `VideoSetting` 偏好与 `VideoSettingDialog`（启用开关 + 8 项滑杆调节 + 重置）；`view_setting_slider` 滑杆行
- 仅 EXO 内核启用（`ExoPlayer.getVideoEffectsSupport()` 校验），MPV 优雅降级为「不支持」提示

### 修改

- `ExoPlayerEngine` 重写 `supportsVideoEffects()` / `setVideoEffects()`
- `PlayerManager` 新增 `canSetVideoSetting()` / `refreshVideoSetting()` / `clearVideoEffect()`
- `BaseBottomSheetDialog` 新增 `getMaxHeight()` 高度上限支持
- 顺带移植 `SliderUtil`（滑杆吸附工具）

### 说明

- 本次为 EXO-first 精简版：未含 TV-fongmi 的完整预设系统（自然/鲜艳等 12 档 chips）与对比预览、未实现 MPV 端均衡器/shader（webtv 内嵌 MpvPlayer 缺 `MpvVideoEqualizer` API，待后续）
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.63 — 在线字幕搜索 (2026-08-11)

从 TV-fongmi 移植 Assrt 在线字幕搜索能力：轨道面板新增「搜索」入口，可在线搜索、下载并套用外部字幕。

### 新增

- **在线字幕搜索**: 轨道弹窗新增搜索按钮（仅点播字幕轨显示），基于 Assrt API 搜索外部字幕
- 支持关键词搜索 / 分页 / 详情解析 / ZIP 字幕包下载解压后选择套用
- 新增 `SubtitleApi` / `SubtitleSearchDialog` / `SubtitleApiDialog`（Token 配置）/ `SubtitleAdapter` 等；`SubtitleSearchItem.toSub()` 复用既有 `Sub` / `PlayerManager.setSub` 套用流程
- 新增 `SubtitleSetting`（Assrt Token 持久化），经搜索弹窗的设置齿轮进入 Token 配置
- 安全底线：下载与解压均带字节/条目上限（32MB 下载、256 条目/128MB 解压）与路径穿越防护

### 修改

- `Sub` 新增 4 参构造 `from(name,url,lang,format)`
- `Download` 新增 `maxBytes` 上限；`FileUtil` 新增带限制与穿越防护的 4 参 `zipDecompress`
- `BaseBottomSheetDialog` 新增预测性返回（back dispatcher）支持

### 说明

- 仅移植在线搜索，不含 TV-fongmi 的字幕外观/样式调整（webtv 已有 `SubtitleDialog`）
- Assrt Token 需用户自行填写（`VodConfig.getAssrt` 配置字段未引入）
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.62 — EXO 自适应降速 + WebView 闪退修复 (2026-08-11)

修复安卓 17 / Pixel 7 Pro 手机版闪退（issue #7），并从上游 WebHomeTV 移植 EXO 自适应降速网络保护（最小可验证集）。

### 修复

- **WebView 渲染进程崩溃闪退**: `CustomWebView` 新增 `onRenderProcessGone` 兜底（镜像 HomeWebController 模式，记日志后正常结束解析并返回 true），避免解析页渲染进程崩溃拖垮整个 App —— 对应 issue #7 安卓 17 手机版闪退

### 新增

- **EXO 自适应降速网络保护**: 从 WebHomeTV 移植 `ExoNetworkGuardController`（含 `ExoNetworkProtectionPolicy` / `ForwardBufferTrend` / `ExoNetworkGuardEligibility` / `ExoNetworkGuardBufferPolicy`）
- 依据缓冲水位快慢趋势，VOD 播放时在网络吃紧自动在 0.85～1.00x 之间动态降速、网络恢复后平滑回升；仅作用于 EXO 内核 + 点播 + 用户 1.0x 场景
- 增强设置新增「网络保护」开关（跟随性能自动档默认开启），经 `ExoPerformanceSetting` 偏好管理，`PlayerManager.setSpeed` 挂钩用户手动调速

### 说明

- 采用纯 buffer 模式（不含遥测/A-B 实验链），最小可验证移植，不影响 IJK/MPV 内核
- **已知后续项**: `librtmp-jni.so`（由 media3-datasource-rtmp 引入）ELF 仅 4KB 对齐，在 16KB 页大小设备（如 Pixel 7 Pro 安卓 17）上播放 RTMP 流时存在 dlopen 崩溃风险；当前应用已开启 `pageSizeCompat="enabled"` 兼容模式，后续需将 RTMP 制品重编至 16KB 对齐或评估移除
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.61 — Phase 3A login-state learning (2026-07-22)

从上游 WebHomeTV 移植登录态学习与一键同步，保留 webtv 安全底线；GitCloud 完整能力仍属后续阶段。

### 新增

- **登录态学习**: 增强设置入口，学习 Cookie / 登录态文件路径（开始学习 → 登录网盘 → 完成学习）
- **路径管理**: 目录树勾选、待确认候选、文本预览/编辑
- **一键同步**: 新增「登录态」勾选项，推送/拉取时打包与恢复登录态 ZIP
- **本地管理页同步**: Manage/Action 推送端与接收端支持 `loginStateFiles`
- **加密 Token 存储**: 最小 `GitCloudTokenStore`（Android Keystore AES/GCM），供登录态 token 导入导出

### 说明

- 仅移植登录态学习，不引入完整 GitCloud / Remote Trust / 观影同步
- 登录态默认勾选同步；路径与 token 不落明文日志
- 家庭过滤、ServerAuth、强制签名、targetSdk 37、Room ≥37 均保留

## 5.5.60 — Multi-kernel player MVP (2026-07-21)

从上游 WebHomeTV 移植 EXO / IJK / MPV 多内核播放能力，默认仍为 EXO；保留 webtv 续播/倍速与安全底线。

### 新增

- **播放器内核**: 设置页可切换 EXO / IJK / MPV；播放页长按“播放器”按钮可快速切换内核
- **IJK 内核**: 内置 jniLibs，兼容更多容器/协议场景
- **MPV 内核**: 内置 libmpv assets，提升软解与特殊片源兼容
- **Media3**: 对齐上游 `1.11.0-alpha01-fongmi`（MediaTitle → MediaEdition）

### 说明

- APK 体积会明显增大（IJK + MPV native）
- 默认内核仍为 EXO，不影响既有播放连续性
- 未整包合入上游完整性能面板 / LUT / karaoke 等周边

## 5.5.59 — Phase 1 enhance features from WebHomeTV (2026-07-21)

从上游 WebHomeTV 移植第一批低风险增强能力，保留 webtv 安全底线（ServerAuth、强制签名、targetSdk 37、FamilyFilter、ConfigImport、Room ≥37）。

### 新增

- **网盘检测 (DriveCheck)**: 增强设置开关；`POST /pan/check`（受 ServerAuth 保护）；WebHome SDK `pan.check` / `pan.play`
- **CSP 预热**: 配置加载后预初始化 Jar CSP 站点，支持默认/自定义站点选择
- **WebHome 主页全屏**: 可关闭网页强制全屏，保留原生工具栏布局
- **播放界面剧照**: 设置开关（默认开），为后续剧照背景能力预留偏好

### 保留

- FamilyFilter、配置导入预检、ServerAuth 鉴权、Release 强制签名、Universal APK

## 5.5.58 — Security audit hardening (2026-07-17)

代码审计发现的高/中/低风险项系统性修复，覆盖远程代码加载、本地服务、WebHome 桥、DLNA、播放器和 CI 构建链。

### 安全加固

- **远程 JAR 加载**: 拒绝明文 `http://` JAR；`file://` JAR 必须带 hash 且经过依赖信任确认
- **依赖信任键**: 信任键从 `hashCode` 改为 SHA-256，避免碰撞；新增非信任页面播放确认
- **路径穿越**: `Path.local()` 校验 canonical 路径必须落在 cache/files/root 安全目录内
- **DNS 重绑定**: WebCall 新增 `FilteringDns`，DNS 解析阶段拒绝私有/回环/链路本地地址
- **DLNA**: `SocketHttpStreamServer` 收紧已知 peer 判定，缺失 `soapaction` 的请求直接拒绝
- **本地服务**: `ServerAuth` 扩展受保护路径（`/media`、`/tvbus`、`/device`），不再信任 `http-client-ip` 头
- **Manage 跳转**: `isValidTarget` 校验所有解析地址均为本机/局域网，避免 DNS 重绑定绕过
- **代理头转发**: `/proxy` 不再向 spider 转发 `authorization`、`cookie`、`x-fongmi-token` 等敏感头
- **日志脱敏**: SiteApi 播放日志和 OkHttp DebugEventListener 的 URL/头信息脱敏
- **WebView**: `CustomWebView` 显式关闭 `file://` 访问和通用文件访问

### 优化

- **搜索相关性**: 预编译噪声正则，扩展 CJK 标点覆盖；相关性阈值可通过 `search_relevance_threshold` 配置
- **搜索取消**: 多源搜索回调在主线程二次校验 epoch，缩小 TOCTOU 窗口
- **DexOpt 隔离**: 每个 spider key 使用独立 `dexopt/<key>` 目录，`clear()` 清理所有子目录
- **CORS 收敛**: `/webResource` 仅允许必要的请求头和暴露头
- **组件导出**: `LiveActivity` 改为 `exported=false`（无 intent-filter，仅内部启动）
- **CI 构建链**: JDK 升级到 21 对齐源码目标，build-tools 升级到 37.0.0，CNB_TOKEN 通过 env 注入避免脚本注入

## 5.5.57 — Search relevance polish (2026-07-18)

优化多源搜索结果相关性，减少与关键词不匹配的模糊结果。

### 优化

- **搜索相关性**: 按关键词匹配度过滤和排序搜索结果，片名精确/包含匹配优先，明显无关结果不再展示
- **文档说明**: README 补充搜索相关性能力说明

## 5.5.56 — Source entries and protocol compatibility (2026-07-14)

补齐指定 TV 源入口，并确认播放器壳子已接入官方 FongMi 额外协议扩展能力。

### 新增 / 优化

- **指定源入口**: TV 源补充本地视频、手机推送、网络直播、哔哩直播、好看短剧和河马短剧
- **源配置校验**: 检查 `api.json` 格式、站点 key 重复、直播配置、`spider.jar` md5 和本地 `js/py` 引用
- **协议兼容**: README 标注 Force、JianPian、Thunder、TVBus 等 FongMi 协议扩展已接入

## 5.5.55 — FongMi api37 compatibility (2026-07-14)

跟进官方 FongMi api37 适配，提升目标 SDK 并收敛新版 Android 权限请求路径。

### 优化 / 兼容

- **Target SDK 37**: 将应用目标 SDK 提升到 37，保持与官方 FongMi api37 适配一致
- **权限请求适配**: 文件访问按 Android 版本与系统能力请求所有文件访问或存储权限，Android 13+ 才请求通知权限
- **构建工具链**: Gradle Wrapper 对齐官方参考版本，README 补齐六个 APK 构建与 Actions 发布说明

## 5.5.54 — WebHome and release safety audit fixes (2026-07-04)

修复代码审计中发现的 WebHome 信任边界、Native 请求、Release 签名和生命周期残余风险。

### 修复 / 加固

- **WebHome 文件信任**: `file://` 首页使用 canonical path 判断，避免路径前缀误判为受信任页面
- **Native SDK 权限**: 未受信任 WebView 页面仅保留最小可用 API，播放控制、页面导航、缓存、设备和配置等能力需要受信任来源
- **Native 请求防护**: `net.request` 拒绝本机、链路本地和局域网私有地址，避免受信任页面变成内网请求代理
- **Release 签名**: Release 构建缺少签名配置时直接失败，不再回退到 debug 签名
- **数据库迁移**: 移除 v1-v29 破坏性迁移声明，迁移失败时保留失败库副本后再重建
- **设备发现边界**: `/device` 仅允许本机和局域网访问，保留一键同步发现能力并减少公网枚举
- **生命周期观察者**: Leanback 和 Mobile 的播放/直播页改用 lifecycle-aware observer，减少手动解绑遗漏风险
- **Mobile 换源续播**: 手机端切换搜索来源前先保存当前历史，避免重置播放器后丢失续播进度
- **镜像同步可见性**: Release workflow 将 CNB 镜像和 TV 源同步结果写入 Actions summary

## 5.5.53 — Stability and release hardening (2026-07-04)

继续收敛播放状态一致性与发布安全细节，降低升级和发版过程中的数据与凭据风险。

### 修复 / 优化

- **Mobile 倍速保持**: 手机端每次播放器准备新媒体源后也会重新应用当前历史倍速，避免自动下一集后实际倍速和界面显示不一致
- **数据库恢复加固**: 数据库打开失败时先保留失败库文件副本，再重建数据库，减少排查和恢复成本
- **迁移范围收敛**: 破坏性迁移只保留给缺少旧 schema 的 v1-v29，v30 之后继续走显式迁移链
- **Release 日志脱敏**: GitHub Actions 不再打印包含签名配置的 `local.properties`
- **Release 说明**: 发布页自动带上当前版本的 changelog 摘要
- **仓库清洁**: 忽略本地 `*-apkapp.txt` 临时产物，避免误提交

## 5.5.52 — Playback speed continuity (2026-07-04)

修复 TV 端自动播放下一集后实际倍速恢复为 x1，但界面仍显示历史倍速的问题。

### 修复

- **倍速保持**: 每次播放器准备新媒体源后重新应用当前剧集历史倍速，保证自动下一集、手动换集和刷新播放时实际倍速与界面显示一致

## 5.5.51 — Playback continuity polish (2026-07-04)

修复 TV 端播放电视剧时，切换搜索到的其他来源后容易从头播放的问题，并放大播放页右上角时间显示。

### 修复 / 优化

- **换源续播**: 切换到搜索结果来源前先保存当前播放历史，避免新源记录丢失播放进度
- **播放页时钟**: 放大右上角时间字号，提升电视端观看距离下的可读性

## 5.5.46 — Hide selected source-side config entries (2026-06-13)

在保留用户自带来源整体内容的前提下，精确隐藏配置中心里指定的来源项，例如夸克网盘配置、UC网盘配置、百度网盘配置、天翼云盘配置、123云盘配置、阿里云盘配置、移动云盘配置、哔哩配置，以及综合配置里的“云盘排序”。

### 新增 / 优化

- **配置中心来源项精确屏蔽**: 只隐藏用户指定的配置中心来源项，不扩大到其他来源内容
- **家庭过滤兼容**: 继续保留可选家庭过滤，对首页/分类/搜索元数据做关键词屏蔽

## 5.5.44 — README cleanup and release polish (2026-06-13)

重构 README 结构，突出播放器壳子定位、导入/管理/同步路径与当前亮点，让用户更容易快速理解项目用途和边界。

### 新增 / 优化

- **README 重写**: 收紧为用户导向结构，拆分“这是什么 / 亮点 / 使用 / 文档 / 构建 / 更新说明”
- **版本文案统一**: README、CHANGELOG 与 release 语义进一步对齐

## 5.5.43 — Family filter for TV-style homepage content (2026-06-12)

新增壳子级家庭过滤能力，基于站点返回的元数据关键词屏蔽不适合电视客厅展示的分类、推荐卡片和搜索结果，并向 WebHome 页面暴露过滤策略供其协作隐藏内容。

### 新增 / 优化

- **家庭过滤开关**: 在增强功能页新增“家庭过滤”入口，可启用/禁用过滤并编辑关键词
- **关键词屏蔽**: 对首页、分类、搜索等原生内容流按关键词过滤分类、卡片和筛选项
- **WebHome 协作**: `fm.config()` 现在会暴露家庭过滤状态和关键词，方便页面侧自定义隐藏逻辑
- **同步 / 备份一致性**: 家庭过滤设置会跟随 settings 一起备份和同步

## 5.5.42 — Config center & sync continuity (2026-06-12)

继续强化播放器壳子的配置管理与同步连续性，补齐最近设备记忆和配置管理入口的独立页面能力。

### 新增 / 优化

- **独立配置管理入口**: 手机端与 TV 端开始引入独立的配置管理页面入口
- **配置中心深化**: 设置页的配置历史入口升级为更明确的配置管理入口
- **最近同步设备记忆**: 一键同步会记住上次设备，并在下次发现后优先选中
- **发版一致性**: 统一版本号、README 与 CHANGELOG 说明

## 5.5.41 — Shell UX Improvements (2026-06-12)

强化播放器壳子的配置导入、配置管理、同步文案与继续观看/收藏语义，确保用户自带 JSON 的导入、管理和迁移体验更清晰。

### 新增 / 优化

- **配置导入预检**: 导入 URL / 文件前先做基础校验，并显示配置摘要
- **失败不覆盖当前配置**: 导入失败时保留当前正在使用的配置
- **配置管理增强**: 历史配置列表显示当前项、来源类型与最后使用时间
- **删除影响提示**: 删除配置前提示会同步移除关联的历史与收藏
- **同步文案升级**: 同步项改为更贴近壳子用户理解的名称，成功提示带设备名
- **继续观看 / 收藏语义增强**: 历史页和收藏页标题更清晰，配置缺失时给出明确提示

## 5.5.40 — Shell UX Improvements (2026-06-12)

移除一批不再维护的外部集成能力，并恢复正式仓库构建配置。

### 移除

- **外部检测服务**: 删除不再维护的分享检测服务与相关本地 HTTP 端点
- **WebHome 扩展桥接**: 删除一批已下线的 Bridge 能力
- **附加同步/UI 入口**: 删除对应的路径管理、附加入口和相关界面逻辑

### 构建恢复

- **Chaquopy**: 恢复正式仓库 Python 3.10 配置
- **requirements**: 恢复 `ujson` 依赖，撤销本地临时打包修改

## 5.5.38 — Reverted experiment (2026-06-12)

回滚一次未继续保留的实验性功能。

### 变更

- 引入了一组后续未继续保留的实验性界面与同步逻辑
- 后续版本已整体移除

## 5.5.37 — Manual Update Flow (2026-06-11)

简化为手动下载更新：不再尝试 App 内下载安装，直接复制链接到浏览器下载。

### 改动

- **更新流程**: 检测到新版本后，确认按钮 → 复制下载链接到剪贴板 → 打开浏览器下载安装
- **移除**: 移除 App 内下载、FileProvider 安装、导出到 Downloads 等复杂逻辑

## 5.5.36 — Post-cleanup (2026-06-11)

清理镜像残留，更新 README 版本号。

## 5.5.35 — Remove Mirror (2026-06-11)

移除镜像同步。

## 5.5.33 — Fix DB Migration Crash (2026-06-11)

修复 5.5.32 数据库迁移导致闪退的问题。

### 修复

- **DB 初始化加固**: `AppDatabase.get()` 首次创建失败时自动删除旧数据库重新创建，避免迁移异常导致闪退

## 5.5.32 — EPG Reminder Persistence & CI Sync Warning (2026-06-11)

EPG 节目提醒持久化到数据库，设备重启后自动重建闹钟。

### 新增

- **EPG 持久化**: 节目提醒保存到 Room 数据库，重启和 BOOT_COMPLETED 时自动重建所有未过期的提醒闹钟

### 修复

- **清理死代码**: 删除未使用的 `CharsetDetectDataSource.java`

## 5.5.31 — Code Cleanup & CI Sync Robustness (2026-06-11)

代码整洁和 CI 稳定性改进。

### 修复

- **死代码清理**: 删除未使用的 `CharsetDetectDataSource.java`（v5.5.20 已从 `MediaSourceFactory` 移除引用）

## 5.5.30 — Fix Update Mirror Selection & Install (2026-06-11)

修复版本更新下载走 GitHub 慢和下载后不弹出安装的问题。

### 修复

- **镜像选择**: `isChina()` 新增系统语言/地区本地检测，不再仅依赖 `ip-api.com`（国内可能被墙导致默认走 GitHub）
- **安装确认**: 下载完成后改用 `ACTION_INSTALL_PACKAGE` 触发安装，并先弹出 Toast 提示
- **安装鲁棒**: 精简重复的 `FLAG_GRANT_READ_URI_PERMISSION`，Android N+ 走 FileProvider

## 5.5.29 — Fix Live TV URL Refresh on Error (2026-06-11)

修复电视直播源 token 过期后无法自动刷新的问题。

### 修复

- **直播错误重试**: `LiveActivity.onError` 播放失败时先调用 `mViewModel.getUrl(mChannel)` 从 Spider/DIYP 源重新获取新 URL（带新鲜 token），再进入自动换台流程
- **BAD_HTTP_STATUS 重试**: `ExoPlayerEngine` 把 HTTP 错误码加入可重试列表

## 5.5.28 — Fix Live TV BAD HTTP STATUS (2026-06-11)

修复电视直播显示 `Bad HTTP Status` 无法播放的问题。

### 修复

- **HTTP 错误重试**: `ExoPlayerEngine.handleError` 将 `ERROR_CODE_IO_BAD_HTTP_STATUS`（HTTP 605 等）加入可重试列表，不再直接判定为 FATAL，给直播源 token 刷新留一次机会

## 5.5.27 — Fix Update JSON Asset Path (2026-06-11)

修复点击版本更新后只显示“正在检测更新”但没有后续结果的问题。

### 修复

- **JSON 文件名**: 更新检查改为读取当前 ABI 对应的 `mobile-arm64_v8a.json` / `leanback-arm64_v8a.json`，不再请求不存在的 `mobile.json`
- **GitHub 路径**: GitHub Release assets 直接走 `latest/download/*.json`
- **失败提示**: 更新检查异常时显示 `Update check failed`，不再重复显示 `Checking for updates…`

## 5.5.26 — Fix WebHome Bridge Diagnostics Crash (2026-06-11)

修复同意更新源后 WebHome 调用 Bridge 时闪退的问题。

### 修复

- **WebView 线程安全**: `HomeWebBridge` 诊断不再从后台线程读取 `WebView.getUrl()`，改用 `HomeWebController` 在主线程维护的 origin 缓存

## 5.5.25 — Fix Update Source Version Detection (2026-06-11)

修复点击版本更新时误提示 `Already up to date` 的问题。

### 修复

- **更新源**: GitHub 更新地址改为当前仓库 Release assets，不再读取旧的外部 Release JSON
- **版本号**: CI 生成 JSON 时使用 `app/build.gradle` 的 `versionCode`，避免 `github.run_number` 小于 APK 版本号导致误判
- **Release assets**: GitHub Release 同时上传 APK 和 JSON，App 可直接读取 `latest/download/*.json`

## 5.5.24 — Dependency Trust & WebHome Security Controls (2026-06-11)

新增远程依赖加载确认、WebHome Bridge 诊断和服务端安全控制。

### 新增

- **远程 JAR 确认**: 远程 JAR 首次加载时显示 URL、hash、大小和配置源，用户确认后按配置源 + URL + hash 持久化授信
- **Python 依赖保护**: 继续强制远程 Python 依赖携带 `;sha256;` / `;md5;` 并下载后校验
- **WebHome Bridge 诊断**: 调试 Console 展示当前 origin、trusted 状态、Bridge 调用计数和最近拒绝记录
- **Token 轮换**: `/manage/security?resetToken=1` 可重置本次运行 token，并返回 token 前缀预览
- **IP allowlist**: `/manage/security?ipMode=all|lan|local` 支持服务端访问范围控制，默认 `all` 保持兼容

## 5.5.21 — IP-based Update Mirror & CI Sync (2026-06-11)

新增 IP 地理位置检测，自动选择最优更新源。

### 新增

- **GeoIP 检测**: 通过 `ip-api.com` 检测用户所在国家，自动选择最快的更新源
- **手动切换**: `Setting.putMirror()` 支持 `auto`/`github` 模式
- **CI 同步**: GitHub Actions 发布后自动推送 APK + JSON 到镜像仓库

## 5.5.20 — Fix Live Stream Loading Stuck (2026-06-11)

修复电视直播一直加载不显示画面的问题。

### 原因

P2-7 字幕编码检测 (`CharsetDetectDataSource`) 被错误地应用到所有媒体数据源，其缓冲读取逻辑在遇到小文件（<5MB）时会在流末尾重复返回已读数据，导致 ExoPlayer 的 HLS 解析器收到损坏的播放列表数据（`Input does not start with the #EXTM3U header`）。

### 修复

- 移除全局 `CharsetDetectDataSource.Factory` 包装器，恢复原始 `DefaultDataSource.Factory`

## 5.5.19 — Fix Live Stream HTTP Blocked (2026-06-11)

修复直播流 `Network Connection Failed`：`base-config cleartextTrafficPermitted` 恢复为 `true`，允许 HTTP 直播流。

### 原因

P0 安全提交将 `cleartextTrafficPermitted` 改为 `false`，意图是只允许 HTTPS。但绝大多数直播源使用 HTTP 协议且域名不固定，无法预先配置白名单。

## 5.5.18 — Build Fix (2026-06-11)

修复 CI 编译错误：`CharsetDetectDataSource.close()` 添加 `throws IOException` 声明。

## 5.5.17 — Backup & CustomCsp Storage Fix (2026-06-11)

修复备份/恢复和自定义 CSP 功能在 Android 11+ 上静默失败的问题。

### 修复

- **Path.tv()**: 备份文件从外部存储迁移到内部存储，无需 `MANAGE_EXTERNAL_STORAGE` 权限
- **CustomCspSetting**: CSP 配置目录从 `Path.root("TV/CustomCsp")` 迁移到 `Path.files()`

### 原因

v5.5.7 安全审计移除了 `MANAGE_EXTERNAL_STORAGE` 权限，v5.5.10 将 `hasFileAccess()` 在 Android 11+ 改为始终返回 `true`。但由于该权限实际未被授予，写入外部存储的操作静默失败，用户点击备份/恢复按钮后无任何效果。

## 5.5.16 — EventBus Annotation Processor Cleanup (2026-06-10)

移除 EventBus 注解处理器依赖，彻底解决 `No option eventBusIndex passed to annotation processor` 编译错误。

## 5.5.15 — CI Build Fix (2026-06-10)

修复 EventBus 注解处理器导致的编译错误。

### 修复

- **EpgParser**: 合并重复的 `getEpg()` 方法，XXE 安全校验统一走一个入口
- **EventBus**: `Startup.java` 改用 `EventBus.getDefault()`，移除 `eventBusIndex` 注解处理器参数

## 5.5.14 — P1/P2 Security & Stability Fixes (2026-06-10)

修复 14 个安全和稳定性问题。

## 5.5.13 — P0 Security Fixes (2026-06-10)

基于 v5.5.12 的 P0 级安全修复版本，修复 6 个严重安全漏洞。

### 修复

- **EPG XXE 防护**: EpgParser 拒绝 `<!DOCTYPE` 并启用 `FEATURE_SECURE_PROCESSING`
- **EPG GZIP 炸弹防护**: 添加解压大小限制
- **HomeWebBridge 缓存隔离**: WebView 缓存按 origin 隔离
- **MediaSourceFactory 缓存安全**: ExoPlayer 缓存配置加固
- **Room 迁移链完整性**: 数据库迁移 fallback 链修复
- **其他**: token 泄漏修复、Notify 安全加固、ReDoS 修复等

## 5.5.12 — Fix GitHub Release CI (2026-06-10)

修复 Release 创建 401 认证错误，改用 `gh release create` CLI。

## 5.5.11 — Release Signing (2026-06-10)

启用 release keystore 签名，用户可直接覆盖安装无需卸载。

## 5.5.10 — Storage Permission Loop Fix (2026-06-10)

修复每次启动都跳转「所有文件访问」权限页面但无法授权的问题。

### 修复

- **Setting.hasFileManager()**: 始终返回 `false`（v5.5.7 已移除 `MANAGE_EXTERNAL_STORAGE`，PermissionX 无法处理未声明的权限）
- **Setting.hasFileAccess()**: Android 11+ 直接返回 `true`（应用内部存储无需权限，文件选择器走 SAF）
- 仅在 Android 10 及以下请求旧版 `READ/WRITE_EXTERNAL_STORAGE` 权限

## 5.5.9 — Build Fix (2026-06-10)

修复 v5.5.8 编译错误。

### 修复

- **CacheManager**: 移除 `Path.mkdir()` 私有方法调用（`Path.exo()` 已自动创建目录）
- **EpgReminder**: `buildNotification()` 改为 `public static`，修复跨包访问权限

## 5.5.8 — Enhancements & Optimizations (2026-06-10)

基于 v5.5.7 安全审计修复后的功能增强和性能优化版本。

### 修复 (P0-P2)

- **OkHttp 懒加载竞态**: `dns()`/`responseInterceptor()` 等 6 个方法加 `synchronized` 保护
- **Server 端口泄漏**: `start()` 端口绑定失败时调用 `nano.stop()` 清理资源
- **ImgUtil.failed 有界集合**: `HashSet` 改为 `LinkedHashSet` 有界 LRU（最大 200），防止 OOM
- **Task 线程池动态计算**: 5/20 固定线程改为 `availableProcessors()` 动态适配 2-8 核 TV 设备
- **e.printStackTrace 替换**: 25+ 处替换为 `SpiderDebug.log(e)`，日志可统一收集
- **History 表索引**: 新增 `(cid, createTime)` 和 `(cid, vodName)` 复合索引，DB v35→v36
- **allowMainThreadQueries**: 保留但添加 TODO 注释（索引已缓解 ANR 风险），标注需迁移到后台线程
- **ServerAuth IP 修复**: `isLocal()` 检查改用 NanoHttpd 的 `remote-addr` 和 `x-forwarded-for`

### 增强 (E1-E8)

- **E1 自动清理过期历史**: `History.cleanExpired()` + `App.java` 启动时调用，删除 60 天前记录
- **E2 播放历史搜索**: `HistoryDao.search()` + `HistoryActivity` 搜索框，按关键字筛选历史
- **E3 默认播放速度**: `PlayerSetting` 新增默认速度选项，`PlayerManager` 读取并应用
- **E4 网络状态指示器**: `NetworkUtil.java` 监听网络变化、测速、弱网检测
- **E5 观看统计报告**: `HistoryDao.countSince()`/`totalDurationSince()` + `History.formatDuration()` 统计每日/周观看时长
- **E6 投屏设备历史**: `DeviceDao.findRecentDlna()` + `Device.touch()` 记录投屏设备快速重连
- **E7 EPG 节目提醒**: `EpgReminder` + `EpgReminderReceiver`，通过 AlarmManager 定时推送节目开播通知
- **E8 离线缓存管理**: `CacheManager` 查看/清理 ExoPlayer 视频缓存大小

## 5.5.7 — Security Audit & Hardening (2026-06-10)

基于 v5.5.3 的代码安全审计修复。修复 2 个严重漏洞、5 个高危漏洞、7 个中危漏洞。

### 严重 (CRITICAL)

- **C1**: `CustomWebView.onReceivedSslError` 移除 `handler.proceed()` 绕过，改为 `handler.cancel()` 拒绝无效证书，并记录 SSL 错误日志。
- **C2**: `/parse` 端点新增 token 认证保护；`jxs`/`url` 参数通过 `JsonPrimitive` 进行 JS 字符串转义，修复反射型 XSS。

### 高危 (HIGH)

- **H1**: `Path.create()` 移除 `Shell.exec("chmod 777 " + file)` 命令注入风险（Java API `setReadable/Writable/Executable` 已足够）。
- **H2**: `DriveMobileCrypto` 添加安全注释，标注硬编码密钥仅提供混淆保护。
- **H3**: `network_security_config.xml` 基础配置 `cleartextTrafficPermitted` 改为 `true`（影视源、直播流需HTTP）；配置源/JAR端点强制HTTPS；`WebViewUtil` 和 `CustomWebView` 的 `MIXED_CONTENT_ALWAYS_ALLOW` 改为 `MIXED_CONTENT_NEVER_ALLOW`。
- **H4**: `HomeWebController.isTrustedHomeUrl` 移除 `content://` 协议自动信任。
- **H5**: 远程扩展脚本完整性风险已标注（需后续版本增加哈希校验）。

### 中危 (MEDIUM)

- **M1**: `Manage.remoteUrl()` 新增 `isValidTarget()` 校验目标 URL 格式。
- **M2**: JAR 加载签名验证缺失已标注（需后续版本增加 RSA/ECDSA 校验）。
- **M4**: `ServerAuth.withToken()` 添加安全注释，说明 token 在 URL 中的泄漏风险及替代方案。
- **M6**: `HomeWebController` 和 `CustomWebView` 的 `shouldOverrideUrlLoading` 阻止 `intent://` 协议。

### 低危 (LOW)

- **L5**: `Local.unzip()` 新增 ZIP 炸弹防护：单条目 ≤100MB，总计 ≤500MB，最多 1000 个条目。

## 5.5.3 — Security Hardening

基于 5.5.2 的安全加固版本。修复了 13 个高/中危问题，但**未新增功能**。

### 网络与 TLS

- 移除 OkHttp 全局 `trustAllCertificates()` 和 `hostnameVerifier(() -> true)` 绕过，恢复系统证书校验。
- 新增 `app/src/main/res/xml/network_security_config.xml`：影视源保留 HTTP 兼容，配置源、更新源、远端 JAR 等关键端点（`github.com`、`*.githubusercontent.com`）强制 HTTPS。
- AndroidManifest 移除 `usesCleartextTraffic="true"` 与 `requestLegacyExternalStorage="true"`，改为引用 `networkSecurityConfig`。

### 远程 JAR 加载

- `JarLoader.parseJar()` 现在强制要求远程 JAR 带 `;sha256;<64位小写hex>` 或兼容的 `;md5;<32位hex>`。
- 不再允许从远程 URL 拉取 hash。
- 远程 JAR 下载后必须先校验，校验失败删除缓存并拒绝加载。
- `assets://` 和 `file://` 本地路径继续加载，但同样走 hash 校验。
- 新增 `Util.sha256(File)` 和 `Util.equalsSha256(File, String)`。

### 本地 HTTP 服务鉴权

- 新增 `app/.../server/ServerAuth.java`：进程内 token，通过 query / `X-Fongmi-Token` / `Authorization: Bearer ...` 校验。
- 受保护路径：`/manage/*`、`/file`、`/upload`、`/newFolder`、`/delFolder`、`/delFile`、`/debug/*`、`/cache`、`/action`、`/proxy`、`/webResource`、`/pan/check`。
- `127.0.0.1` 默认放行；LAN 访问必须带 token。
- `Server.getAddress(int)`、`ManageService.getLocalUrl()`/`getLanUrl()` 自动附带 `?token=...`。
- 管理页面（`assets/js/manage.js`、`assets/js/script.js`）自动注入 token。
- 远程管理转发 (`/manage/remote/*`) 从目标设备 URL 中提取并追加 token。
- `Nano.deviceInfo()` 收敛为只返回 `{uuid, name, ip, type}`，去除序列号、MAC 等指纹。

### 文件接口

- `Local.java` 新增 `safePath/safeFile/safeChild/safeName/unzip`：
  - 所有路径走 canonical path 校验。
  - 禁止 zip-slip 写出根目录。
  - 禁止删除根目录本身。
  - 上传文件名拒绝 `/` / `\` / `..` / 控制字符。

### 代理网关

- `/webResource` 拒绝 `loopback` / `link-local` / `site-local` / `any-local` 目标。
- `/webResource` CORS 不再无条件 `*` + `Allow-Credentials: true`。

### WebHome 权限分级

- `HomeWebController.isTrustedHomePage()` 区分本地 / 同源 / 第三方页面。
- `player.playUrl` 限制 URL scheme 为 `http(s)://`。
- `document-start` 注入脚本从 `Collections.singleton("*")` 收紧为 `Collections.singleton(originOf(homePage))`，未解析到 origin 时不注册。

### 日志脱敏

- `WebCall.requestInfo()` / `responseInfo()` / `bodyPreview()`：header / body 敏感字段替换为 `***`。
- `OkHttp.DebugEventListener.callStart()`：header 经 `redact()` 处理。
- `PlayerManager`、`ExoPlayerEngine`、`MediaSourceFactory`、`CustomWebView`：日志只打印 header 名称。
- `Action`、`Proxy`、`Nano`：参数日志改为只打印 key 集合。

### Manifest 权限收敛

| 权限 | 状态 | 替代行为 |
| --- | --- | --- |
| `MANAGE_EXTERNAL_STORAGE` | 已移除 | 文件管理 UI 在 Android 11+ 受限到 App 专属目录 + SAF 选定位置 |
| `REQUEST_INSTALL_PACKAGES` | 已移除 | `Updater` 通过 `MediaStore.Downloads`（Android 10+）或公共 Downloads（Android 9-）导出 APK，通知用户用文件管理器手动安装 |
| `usesCleartextTraffic` | 已移除 | 由 `network_security_config.xml` 精细化控制 |

### 兼容性提示

- `api.json` 中远程 JAR 必须补上 `;sha256;...` 段，否则站点不会加载（请向站源作者索取 hash）。
- 老设备（Android 10 及以下）继续通过 `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` 访问文件；Android 11+ 用户需要先用 SAF 选定根目录。
- 自更新流程变更为「下载 → 导出 Downloads → 通知用户」，自动跳转到系统安装器不再可用。
- `PlaybackService` 仍为 `exported="true"`（保持蓝牙/系统媒体控制集成）；如果不需要外部控制可改为 `exported="false"`。

---

## 5.5.2

初始 fork 起点，包含上游 WebHomeTV 5.5.2 全部功能。
