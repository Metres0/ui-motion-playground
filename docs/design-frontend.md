# 前端设计文档 —— UI 与动效层

> 版本：v1.32 配套文档 · 2026-08
> 范围：android-rss / android-compose 的 Compose UI 层 + web-view-transitions 演示页 + 跨端动效 token 体系。
> 配套文档：[后端设计文档](design-backend.md)、[动效规范](../motion-tokens.md)、[工具链](../docs/dev-toolchain.md)。

---

## 1. 前端全景

本仓库没有独立渲染服务器，**前端**指所有面向用户的界面与交互层，分布在三个子项目中：

| 子项目 | 技术栈 | 角色 |
|---|---|---|
| `android-rss/` | Jetpack Compose + Material 3 | **产品端**（FeedLite）：首页聚合流 / 源列表 / 详情阅读 / 设置 |
| `android-compose/` | Jetpack Compose + Paging 3 | **教学 demo**：预取 + 请求合并 + 共享元素 + 分页预载 |
| `web-view-transitions/` | 原生 JS + View Transitions API（零依赖） | **Web 对照 demo**：同一套动效规范的另一种实现 |

三者共用同一份动效事实来源 `motion-tokens.md`（见第 2 节），界面形态不同但动效语义一致。

---

## 2. 动效 token 体系（跨端事实来源）

### 2.1 单一事实来源

`motion-tokens.md` 用四张表定义全部动效参数，是**唯一事实来源**：

1. **时长（Duration）**：`instant=0 / micro=100 / fast=180 / base=220 / enter=350 / exit=90 / expansive=500 / skeleton=600`（ms）。进出场永远不对称——进入慢（350ms，"被展开"的舒展感），退出快（90ms，"响应迅速"）。
2. **缓动（Easing）**：`standard / emphasized / decelerate / accelerate / spring`，两端严格一致的贝塞尔曲线；spring 仅限微交互，禁止用于整页转场。
3. **位移（Distance）**：`micro=4 / small=8 / page=56 / full=屏高`（dp/px）。页面级转场必须用 `space.page`，微交互位移 ≤ `space.small`。
4. **遮罩（Overlay）**：`scrim.light=0.12 / scrim.dark=0.4 / blur=24px`；模糊是两端最重的效果，动画进行中不得改变 blur 半径。

### 2.2 三端翻译与校验

```
motion-tokens.md
   │
   ├─▶ android-rss/MotionTokens.kt          ← object Duration/Easing/Space/Overlay
   ├─▶ android-compose/MotionTokens.kt      ← 与 rss 端逐值一致
   └─▶ web-view-transitions/styles.css      ← :root 中的 CSS 变量（--dur-*/--ease-*/--space-*/--overlay-*）
        └─ tools/verify-motion-tokens.ps1   ← 防漂移校验（gradlew verifyMotionTokens）
```

校验脚本扫描三份文件的 token 值并逐项比对，不匹配即失败退出。**改任何动效只改 `motion-tokens.md`，然后同步三处翻译并跑校验。**

### 2.3 关键速记函数

```kotlin
fun pageEnter() = tween<Float>(Duration.Enter, easing = Easing.Emphasized)  // 350ms
fun pageExit()  = tween<Float>(Duration.Exit,  easing = Easing.Emphasized)  // 90ms
fun micro()     = tween<Float>(Duration.Fast,  easing = Easing.Standard)    // 180ms
fun sheet()     = tween<Float>(Duration.Base + 80, easing = Easing.Emphasized)
```

---

## 3. Android Compose UI 层（android-rss 产品端）

### 3.1 依赖容器与界面入口

```
FeedLiteApp (Application)
  └─ AppContainer                       ← 进程级单例，配置变更不重建
       ├─ subscriptionStore / repository / translator / translationStore
       ├─ updateSettings / fetcher / readingState / themeSettings
       └─ MainActivity → AppNav(container) → NavHost
```

- 依赖一律来自容器，**不 8 参透传**；旋转/字体缩放等配置变更时预取缓存与在途请求不丢。
- `MainActivity`：沉浸式全屏（隐藏状态栏，下滑临时唤出）+ 主题模式（跟随系统/浅/深）注入。

### 3.2 导航编排（AppNav）

- **底部导航**：首页 / 收藏 / 稍后再看 / 源 / 设置 五个 Tab；详情页自动隐藏，首页滚动下拉时隐藏（`showHomeBar` + 节流滚动方向监听）。
- **路由**：`home`、`articles/{sourceId}`、`article/{itemKey}`、`starred`、`later`、`sources`、`settings`。
- **转场统一**：全局 `enterTransition/exitTransition/popEnter/popExit` 用 fade 收尾；页面级用收敛后的两个扩展函数：

```kotlin
private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideEnter() =
    slideIntoContainer(towards = Left, animationSpec = tween(Enter, emphasized)) + fadeIn(pageEnter())
private fun ...slideExit() = slideOutOfContainer(towards = Right, animationSpec = tween(Exit, emphasized)) + fadeOut(pageExit())
```

> v1.32 之前这 5 段 lambda 在文件里复制粘贴了 5 遍，现已收敛为两个函数，杜绝语义漂移。

### 3.3 首页聚合流（HomeScreen / HomeViewModel）

- **状态机**：`Loading → Success(entries, loadedCount, enabledCount, failedCount, updating)`。
- **加载策略**：先读所有启用源本地缓存秒开 → 后台只更新「无缓存或超间隔」的源 → `updateSources` 返回 `UpdateResult(added, failures)`，`failedCount` 驱动真实统计「成功 X/Y」，不再虚报。
- **缓存读取在 `Dispatchers.IO`**：20 源 × 200 篇的 JSON 解析不占主线程。
- **下拉刷新**：`PullToRefreshBox` → `refresh()` 强制全部源 `force=true` 增量抓取（绕过内存 TTL）。
- **卡片**：源名 + 相对时间 + 未读圆点 + 标题（已读降灰）；stagger 进入（`(index % 10) * 30ms` 错峰 + 位移淡入）。
- **共享元素**：封面 `thumb_{key}` 与详情页大图配对，转场自动位移缩放。

### 3.4 单源列表（ArticleListScreen / ArticleListViewModel）

- **状态机**：`Loading / Success(items, visibleCount, updateError) / Error`。
- **UI 增量渲染**：「先 5 篇，加载更多每次 +5」（RSS 无服务端分页，一次抓全量、按需渲染，低端机首屏只布局 5 张卡）。
- **错误不吞**（v1.32 修复）：刷新失败时——有缓存保留列表 + **顶部 errorContainer 横幅**；无缓存进 `Error` 态 + 重试按钮。`Error` 状态不再被 `Success` 无条件覆盖。
- **强制刷新**：顶栏刷新按钮走 `updateSource(force=true)`。

### 3.5 详情页（ArticleDetailScreen / ArticleDetailViewModel）

- **顶栏**（阅读软件式，沉浸）：「返回 + 阅读设置(Aa) + 收藏 + 稍后再看」，底部栏自动隐藏。
- **正文渲染优先级**：
  1. 全文抓取结果（`HtmlBlocks.parse` 在 `Dispatchers.Default` 后台解析，长文不卡主线程）；
  2. 有译文且开启「译文」时替换原文（chips 一键切换）；
  3. 回退 feed 摘要。
- **全文抓取**：feed 正文过短（<300 字或无实质内容）时，先读 `FullTextCache`（IO 线程）再网络抓取。
- **渲染块模型**（`HtmlBlocks`）：段落 / 标题分级 / 有序无序列表 / 引用 / 分隔线 / 代码块（深色底 + 等宽 + 复制）/ 图片（自由比例 + 点击全屏 Dialog）。行内 span 支持加粗/斜体/行内代码/链接。
- **链接安全**（v1.32）：`openWebLink()` 只放行 http/https，`javascript:`/`intent:`/畸形 URI 直接丢弃，且 `startActivity` 包 try/catch——恶意 feed 不再能崩阅读器。
- **阅读设置面板**：字号（85–140%）/ 行高（1.2–2.0）/ 字体（无衬线/衬线/等宽），即时生效并持久化到 `ReadingSettings`。v1.30 误删的「Aa」入口在 v1.32 回归。
- **阅读进度**：进入恢复上次滚动位置（带重试轮询补偿布局时序），离开 `DisposableEffect` 保存。
- **收藏/稍后再看**：本地 `remember` 即时反馈 + `ReadingStateStore` 持久化 + 版本号流驱动其它页面刷新。

### 3.6 收藏 / 稍后再看 / 设置 / 源管理

- 三页共用同一套顶部安全区处理（`displayCutout` + `statusBars` 双 padding），与底部导航解耦。
- `SettingsScreen`：翻译服务配置（服务商模板/Base URL/Key/模型/目标语言）、阅读设置、主题、自动更新间隔（选择即重新调度 WorkManager）、OPML 导入导出、清除已读、**缓存统计在 IO 线程**（不卡主线程）。
- `SourceManageScreen`：搜索 + 分类分组 + 每源开关/进入/删除 + 添加订阅源（`addCustom` 返回错误信息，协议校验失败用 Toast 提示）。

### 3.7 渐进式图片（ProgressiveImage）

```kotlin
fun ProgressiveImage(url, seed, contentDescription, decodeWidth = 360, modifier)
```

- **模糊占位**：按 `seed` 生成确定性柔和色块（`seed * 47` 色相）。
- **尺寸受限解码**：列表 360px / 详情 1280px（`ImageRequest.size(Size(w, w))`），大图不再全分辨率解码。
- **错误态**（v1.32 补齐）：Coil `onError` 时放弃原图层并取消模糊，显示纯色占位，不再永久停留在模糊块。
- **防盗链**：`FeedLiteApp` 的 OkHttp 拦截器按 `ImageContext.articleRefererHost` 优先用文章域、否则用图片域带 `Referer` + 完整 UA。

### 3.8 Android Compose 教学 demo（android-compose）

对照「研究报告」的加载策略演示：

| 机制 | 实现 | 关键点 |
|---|---|---|
| 分页预载 | Paging 3 `initialLoadSize=40 / pageSize=20 / prefetchDistance=5` | key 用**数据偏移量**而非页码（v1.32 修复重叠区间） |
| 路由预取 | `PrefetchRouter.intercept` 在点击瞬间发起详情请求 | 预取失败静默，页面请求兜底 |
| 请求合并 | `ArticleRepository` `Mutex + inFlightDetail(ConcurrentHashMap<Deferred>)` | 同 id 并发只发一次网络请求 |
| 共享元素 | `SharedTransitionLayout` + 同名 `cover_{id}` key | 列表↔详情自动位移缩放 |
| 徽章判定 | `wasPrefetched(id)`（发起过预取 **且** 已入缓存） | v1.32 替换 `elapsed<80ms` 启发式 |
| stagger | `animatedIds` 集合保证**只播一次** | 滚动回看不再重放动画 |

---

## 4. Web 演示（web-view-transitions）

### 4.1 文件结构

```
index.html   两个 <main>（list-view / detail-view）通过 hidden 切换；CSP meta；noscript 兜底；aria-live
styles.css   :root 动效 token + 配色变量；暗色模式（prefers-color-scheme）；prefers-reduced-motion
app.js       ① 纯函数区（无 DOM，可单测） + ② DOM 接线区
```

### 4.2 纯函数区（可单测）

- `makeArticle / makeDetail / coverUrl / coverColor`：数据构造。
- `createPrefetchState()`：预取簿记 `{ promises, done, hovered }`。
- `markIntent(state, id)`：**点击前**先记录 hover/touch 意图。
- `shouldShowBadge(state, id)`：徽章 = 意图命中（纯键盘/无 hover 的点击不显示）。
- `fetchDetail(id, signal)`：可中止的模拟网络请求（AbortController → 清定时器 + reject AbortError）。
- `evictOverflow(map, limit)`：LRU 式容量封顶（CACHE_LIMIT=30）。

### 4.3 预取与竞态

- **hover 预取**（桌面）与**触摸预取**（移动）都收敛到 `prefetchDetail`。
- **触摸防误触**：`touchstart` 后延迟 80ms 再预取；`touchmove` 位移 >10px 判定为滚动并取消定时器——滚动不触发请求风暴，点按保留预取与徽章。
- **取消原语**：点击落点确定后 `abortOtherPrefetches(id)` 中止其它在途预取；失败/被中止的 promise 从表中删除，下次可重新发起。
- **竞态保护**：`finishOpen` 中 `if (currentId !== id) return` 丢弃过期渲染；`transitionGen` 代际计数防止旧 `finally` 误删 `backward` 类。

### 4.4 View Transitions 实现

- 列表卡片封面与详情 hero 用**同名** `view-transition-name: cover-{id}`，浏览器自动完成位移缩放。
- 方向镜像：返回时给 `<html>` 加 `backward` 类，`::view-transition-group(*)` 的 keyframes 反向。
- 无 `startViewTransition` 支持时降级为直接切换（`showDetailShell` + `finishOpen`）。

### 4.5 无障碍与健壮性

- 卡片 `tabindex=0` + `role=button` + `aria-label` + Enter/Space 打开。
- 转场焦点管理：打开聚焦 `#detail-title`（tabindex=-1），关闭 `restoreFocus()` 回到来源卡片。
- `aria-live="polite"` 覆盖徽章与骨架屏区域。
- 图片 `onerror` 回退占位色（卡片与 hero 均处理，含缓存失效检查）。
- `prefers-reduced-motion` 下停用转场与 stagger；暗色模式用 `prefers-color-scheme` 换配色变量。
- CSP：`default-src 'self'; img-src 'self' https://picsum.photos data:; style-src 'self' 'unsafe-inline'; script-src 'self'`（内联 style 来自 JS 注入的骨架屏）。

> 已知取舍：CSP 在个别严格浏览器下可能拦截 `file://` 同目录资源；若演示打不开，移除 CSP meta 即可（正式部署建议保留并由服务器下发）。

---

## 5. 跨端一致性

| 关注点 | Android | Web |
|---|---|---|
| 动效参数 | `MotionTokens.kt` 常量 | `:root` CSS 变量 |
| 页面转场 | `slideEnter()/slideExit()` | `document.startViewTransition` |
| 共享元素 | `sharedElement(thumb_{key})` | 同名 `view-transition-name` |
| stagger | `delay((id % 12) * 30L)` | `animation-delay: ((id-1)%12)*30ms` |
| 预取命中 | `wasPrefetched()`（缓存事实） | `shouldShowBadge()`（意图记录） |
| 骨架屏 | shimmer 600ms 脉冲 | `calc(var(--dur-skeleton) * 2)` |

**防漂移**：任何一端改动了效参数，跑一次 `powershell -File tools/verify-motion-tokens.ps1`（或 `gradlew verifyMotionTokens`），不一致即报错。

---

## 6. 前端可扩展方向

1. **无障碍深化**：正文语义化（Heading 层级合并到 TalkBack 导航）、大字体适配（当前正文用 sp 已随系统缩放，自定义字号是独立维度）。
2. **i18n**：全部中文字符串进 `strings.xml`（含复数「N 篇」）。
3. **Compose demo 产品化**：模拟 API 换真实接口后，`wasPrefetched` 语义不变（缓存事实判定），只需替换数据源。
4. **Web demo 接框架**：`app.js` 纯函数区可平移为 TypeScript 模块；`startViewTransition` 逻辑可移植到 React/Vue Router（保留同名 `view-transition-name`）。
