# UI Playground — 安卓 + Web 通用动效对照工程

> ## 📱 FeedLite「轻阅 RSS」已交付（Android 16 / v1.0）
> 本研究的**落地应用**：内置 8 个订阅源、先加载 5 篇 + 点击加载更多、
> 共享元素转场 / stagger / 渐进式图片，release 签名 APK 已构建。
> 详见 **[`android-rss/README.md`](android-rss/README.md)**。

一套演示「加载策略 + 页面切换效果 + 优先加载」的**双端对照 demo**。
两端共用同一份动效规范 `motion-tokens.md`，用不同引擎实现同一套效果：

| | Android | Web |
|---|---|---|
| 引擎 | Jetpack Compose | View Transitions API（零依赖） |
| 页面转场 | Navigation 2.8 + `AnimatedContentTransitionScope` | `document.startViewTransition` |
| 共享元素 | `SharedTransitionLayout` + `sharedElement()` | 同名 `view-transition-name` 自动配对 |
| 预取 | `PrefetchRouter`（点击瞬间拦截）+ 请求合并 | hover / touch 预取 |
| 分页预载 | Paging 3（`prefetchDistance` + `initialLoadSize`） | —（演示聚焦转场与预取） |
| 渐进式图片 | Coil `crossfade` + 模糊占位 | `img.onload` 淡入 + 占位色 |
| 骨架屏 | shimmer 脉冲 | CSS `shimmer` 动画 |

## 目录结构

```
UI-All/
├── motion-tokens.md              # ★ 唯一动效事实来源（时长/缓动/位移/遮罩四张表）
├── README.md
├── android-compose/              # Android 端（Android Studio 打开）
│   └── app/src/main/java/com/example/uiplayground/
│       ├── MotionTokens.kt       # token → Compose 参数
│       ├── data/                 # 模拟网络 / 请求合并 / 路由预取 / Paging
│       └── ui/                   # 列表、详情、导航编排
└── web-view-transitions/         # Web 端（浏览器直接打开 index.html）
    ├── index.html
    ├── styles.css                # token → CSS 变量 + view-transition 动画
    └── app.js                    # 数据 / 预取 / 转场 / 渐进加载
```

## 快速运行

### Android（android-compose/）

1. 用 **Android Studio**（Koala+ / Ladybug 均可）打开 `android-compose/` 目录；
2. 等 Gradle 同步完成（首次需下载依赖，需科学上网访问 Google/Maven Central）；
3. 连一台 Android 8.0+（minSdk 26）的真机或模拟器，点 Run。

**观察点：**
- 进入列表页：卡片 **stagger 逐项滑入**（每项错峰 30ms）；
- 滚动到底部附近：Paging 已在后台预载下一页，滚动**永远不断档**；
- **点击某张卡片**：封面图从卡片位置**平滑放大飞到详情页**（共享元素），
  同时路由拦截器已提前发出详情请求；
- 详情页：若转场结束前数据已就绪 → 顶部出现 **「预取命中 · 秒开」** 徽章；
  若没预取到 → 先看到 **shimmer 骨架屏**，随后大图从模糊占位**淡入**；
- 系统返回手势：水平转场动画与进入**镜像**（预测式返回的简化演示）。

> 想对比「有无预取」：注释掉 `AppNav.kt` 里的 `prefetchRouter.intercept(...)` 一行，
> 详情页将稳定出现骨架屏 + 加载延迟，转场完成时内容还没到。

### Web（web-view-transitions/）

无构建、无依赖，二选一：

```bash
# 方式一：直接双击 index.html 打开（同源限制对纯静态无影响）
# 方式二：起一个本地静态服务器（推荐，避免个别浏览器 file:// 限制）
cd web-view-transitions
npx serve .
```

用 **Chrome 111+ / Edge 111+ / Safari 18+ / Firefox 144+** 打开。

**观察点：**
- **hover** 任意卡片（移动端则触摸）：触发预取，`Console` 无输出但网络已发起；
- **点击卡片**：整个页面**右滑入切换**到详情，同时该卡片的封面图
  **从列表原位放大飞到详情头部**（View Transitions 共享元素，无需任何 JS 动画库）；
- 详情页：预取完成的显示 **「预取命中 · 秒开」** 徽章 + 图片直接就位；
  未预取的显示 shimmer 骨架 + 图片加载完成淡入；
- **返回按钮**：页面**左滑回**列表，详情大图**飞回**它原本的卡片位置。

## 动效参数对照（token → 两端实现）

| Token | Android | Web |
|---|---|---|
| 进入 350ms | `tween(350, easing = Emphasized)` | `--dur-enter: 350ms` |
| 退出 90ms | `tween(90, easing = Emphasized)` | `--dur-exit: 90ms` |
| emphasized 曲线 | `CubicBezierEasing(0.2f, 0f, 0f, 1f)` | `cubic-bezier(0.2, 0, 0, 1)` |
| 页面位移 56px | `slideIntoContainer(SlideDirection.Left)` | `translateX(56px)` |
| stagger 30ms/项 | `delay((id % 12) * 30L)` | `animation-delay: ...ms` |
| 渐进淡入 300ms | Coil `crossfadeDuration(300)` | `transition: opacity 300ms` |

## 验证方法（量化「流畅」）

### Android — Macrobenchmark

1. 新建 `android-compose/app/src/androidTest/` 下的测试模块（参考官方 `baselineprofile` 模板）；
2. 用 `FrameTimingMetric` 断言转场期间帧间隔：
   ```kotlin
   @RunWith(AndroidJUnit4::class)
   class TransitionBenchmark {
       @get:Rule val rule = MacrobenchmarkRule()
       @Test fun openDetail() = rule.measure(
           packageName = "com.example.uiplayground",
           metrics = listOf(FrameTimingMetric()),
           compilationMode = CompilationMode.Full(),
           iterations = 10,
           startupMode = StartupMode.COLD,
       ) {
           pressStart()
           // 点击第二张卡片 → 等待详情渲染 → 返回
       }
   }
   ```
3. 指标目标：**转场期间 P95 帧间隔 ≤ 8.3ms（120Hz）或 ≤ 16.6ms（60Hz）**，
   无 `jank`；`startup` 冷启动 ≤ 500ms 为优。

### Web — DevTools Performance

1. 打开页面 → F12 → Performance → 点录制 → 点击卡片 → 停止；
2. 重点看：
   - **Main / Rendering 轨道**：View Transitions 期间是否出现长任务（>50ms）或布局抖动；
   - **Frames 轨道**：帧高应稳定在 60/120，不应出现大片红色掉帧区间；
   - Performance Insights 中的 `long tasks` 和 `forced reflow` 计数。
3. 附带检查：Network 面板确认点击详情时**没有第二次详情请求**（预取已命中），
   这是「请求合并 + 预取」生效的直接证据。

### 效果一致性对照表（验收用）

| 动作 | Android 预期 | Web 预期 |
|---|---|---|
| 进入详情 | 右入左出 350ms，封面放大 | 页面右滑入 350ms，封面放大 |
| 返回列表 | 左出到右 90ms，封面缩回 | 页面左滑回，封面飞回原位 |
| 预取命中 | 「预取命中 · 秒开」徽章 | 同左 |
| 未预取 | shimmer 骨架 → 大图淡入 | 同左 |
| 弱网下 | Paging 自动续页、无跳动 | — |

## 如何扩展（把 demo 变成你的工程）

1. 改效果：**只改 `motion-tokens.md`**，再同步 `MotionTokens.kt` / `styles.css` 的对应行；
2. 换真实接口：把 `ArticleApi` 的 `delay(...)` 换成 Retrofit/Ktor 调用，
   `PrefetchRouter.intercept` 换成真实路由框架（如 Navigation 的 Deep Link / Route）的拦截器；
3. 加请求合并：真实项目中把 `ArticleRepository` 的 Mutex+Deferred 下沉到
   OkHttp 拦截器或 `Flow<Result<T>>` 层，覆盖所有接口；
4. Web 端接框架：把 `app.js` 的 `startViewTransition` 逻辑平移到 React（`react@canary`
   已内置 view transitions 支持）或 Vue Router（`v4.5+` 集成），保留同名
   `view-transition-name` 即可。
