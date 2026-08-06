# 后端设计文档 —— 数据与服务层

> 版本：v1.32 配套文档 · 2026-08
> 范围：本仓库无独立服务器，**后端**指客户端内的数据层与服务逻辑：网络抓取、解析、持久化、缓存、AI 翻译服务、后台同步、安全存储。
> 载体：`android-rss`（FeedLite，完整数据层）与 `android-compose`（demo 数据层，见 §8）。
> 配套文档：[前端设计文档](design-frontend.md)、[动效规范](../motion-tokens.md)。

---

## 1. 分层总览

```
┌──────────────────────────────────────────────────────────────┐
│ 状态层（ViewModel）      Home / ArticleList / ArticleDetail / Settings │
├──────────────────────────────────────────────────────────────┤
│ 服务层                   RssRepository · ArticleFetcher · Translator │
├──────────────────────────────────────────────────────────────┤
│ 持久化层                 ArticleStore · SubscriptionStore · ReadingStateStore │
│                          ReadingSettings · ThemeSettings · UpdateSettings · SecurePrefs │
├──────────────────────────────────────────────────────────────┤
│ 缓存层                   内存：ArticleCache(LRU) · RssRepository.cache · detailCache(LRU) │
│                          磁盘：FullTextCache · Translator 缓存 · CacheManager │
├──────────────────────────────────────────────────────────────┤
│ 基础设施                 FeedSyncWorker(WorkManager) · HttpUtil · UrlPolicy │
└──────────────────────────────────────────────────────────────┘
```

- 所有网络调用经 `withContext(Dispatchers.IO)`；正文解析经 `Dispatchers.Default`；主线程零磁盘/网络/大解析。
- 依赖注入：`AppContainer`（进程级单例，见 §2），无第三方 DI 框架。

---

## 2. 依赖容器（AppContainer）

```kotlin
class AppContainer(context: Context) {          // FeedLiteApp.container，Application.onCreate 创建一次
    val subscriptionStore = SubscriptionStore(appContext)
    val repository = RssRepository(appContext)
    val translationStore = TranslationStore(appContext)
    val translator = Translator(translationStore, File(filesDir, "translations"))
    val updateSettings = UpdateSettings(appContext)
    val fetcher = ArticleFetcher(FullTextCache(appContext))
    val readingState = ReadingStateStore(appContext)
    val themeSettings = ThemeSettings(appContext)
}
```

**动机**：此前依赖在 `MainActivity.onCreate` 逐个构造并 8 参透传，旋转/配置变更会孤儿化协程作用域、清空预取缓存。容器化后数据层与进程同寿。

---

## 3. 网络抓取（RssRepository / ArticleFetcher）

### 3.1 RssRepository —— 订阅源抓取 + 增量合并

```
fetchFeed(source, force):
  force=false 且内存缓存 < 5min TTL  → 直接返回缓存（避免频繁抓取）
  否则 → fetchBytes(url)（重试+大小上限） → RssParser.parse → 写入内存缓存
updateSource(source, force)  → fetchFeed + ArticleStore.merge  返回新增数
updateSources(sources, force) → UpdateResult(added: Map, failures: Set)
```

**v1.32 关键修复**：

1. **force 参数**：手动刷新 / 下拉刷新必须 `force=true`。此前 `refresh()` 走 `fetchFeed` 会被 5 分钟 TTL 挡住，用户按刷新「没反应」。
2. **失败追踪**：`updateSources` 单独返回失败源集合，首页据此显示真实「成功 X/Y」；全部失败时由调用方提示网络错误。
3. **重试退避**：瞬时 `IOException` 重试 2 次（1s / 2s 退避）；HTTP 非 2xx 视为确定性错误，不重试。
4. **响应上限**：`HttpUtil.readBounded(max)`，feed 10MB——防恶意/异常源 OOM。

### 3.2 RssParser —— 极简 RSS / Atom 解析（零依赖）

- 平台 `XmlPullParser` 流式解析，`itemCount < 300` 防无限膨胀；解析异常**部分成功兜底**（坏条目不拖垮整源）。
- 封面图优先级：`enclosure(type=image) > media:thumbnail > media:content > 正文首图(srcset 优先)`；data URI 忽略。
- 图片 URL 规范化：`//host` → `https://host`，`/path` → 基于源域名补全。
- 协议相对/根相对/非法值处理；不渲染任何脚本（数据纯文本交给 UI 层）。
- **字符集自动识别**（v1.32）：`parser.setInput(stream, null)` 让解析器按 XML 声明解码，GBK/GB2312 中文源不再乱码。
- **稳定 key**（v1.32）：`guid` > `link` > 标题+时间哈希。此前用「位置索引」做 key，源重排/截断会让同一篇文章换 key → 重复入库、已读/收藏状态失联。

### 3.3 ArticleFetcher —— 全文抓取（readability-lite）

- 流程：读 `FullTextCache` → 抓取原文 HTML → `extractMainContent`（候选容器 id/class 关键词 → `<article>` → `<main>`，`<div>` 配对计数截取）→ 清理噪音（script/style/nav/footer/aside/iframe/表单 + 按 comment/extend/recommend/emoji/share 关键词配对移除容器）→ 图片 URL 相对路径补全 → 写缓存。
- 正文质量门槛：提取出的 `<p>` 文本合计 ≥ 200 字才算命中，否则返回空由详情页回退。
- 响应上限 5MB；带 Referer + 完整 UA（部分站点防盗链）。

---

## 4. 持久化层（SharedPreferences + JSON / Keystore）

| Store | 文件 | 内容 | 说明 |
|---|---|---|---|
| `ArticleStore` | `articles_v2` | 每源文章 JSON 数组 + 上次更新时间 | **merge 按源加锁**（v1.32，防并发丢更新）；上限 200 篇/源 |
| `SubscriptionStore` | `subscriptions` | 启用源 id 集合 + 自定义源 JSON | `addCustom` 校验 http(s) 协议并返回错误信息（v1.32） |
| `ReadingStateStore` | `reading_state_v2` | 已读 set / 收藏 map / 稍后再看 map / 阅读进度 | `version: StateFlow<Int>` 版本号流驱动各页刷新 |
| `ReadingSettings` | — | 字号/行高/字体 | 详情页与设置页共享 |
| `ThemeSettings` | — | 跟随系统/浅/深 | 进程级 `StateFlow` |
| `UpdateSettings` | `update` | 自动更新间隔（0=手动，6/12/24/48h） | 供前台 `needsUpdate` 与后台 Worker 共用 |
| `SecurePrefs` | `feedlite_secure` | **加密** KV（翻译 API Key） | AndroidKeyStore AES/GCM，见 §7 |

**序列化注意**：收藏/稍后再看持久化必须先把 `RssItem.toJson()` 成 `JSONObject` 再放入父对象——直接 `JSONObject(Map<String, RssItem>)` 会把对象序列化成字符串，读回时 `getJSONObject` 抛异常导致「收藏永远显示不出来」（v1.9 曾踩坑）。

---

## 5. 缓存体系

### 5.1 内存层

| 缓存 | 结构 | 上限 | 用途 |
|---|---|---|---|
| `ArticleCache.map` | `synchronizedMap(LinkedHashMap accessOrder=true)` | **100**（LRU，v1.32） | 列表点击 → 详情按 key 取文章对象（不进导航参数） |
| `ArticleCache.translations` | 同上 | 100 | 译文按文章 key 缓存 |
| `RssRepository.cache` | `ConcurrentHashMap` | 每源 1 条 + 5min TTL | 源内内存快照 |
| `detailCache`（demo） | synchronized LinkedHashMap accessOrder | 60（LRU） | 详情预取缓存，stale-while-revalidate |

### 5.2 磁盘层

- `FullTextCache`：`files/fulltext/{md5(link)}.html`。**`put` 前确保目录存在**（v1.32：`CacheManager.clear()` 删目录后缓存不再永久失效）；内存快照用 `ConcurrentHashMap`（主线程与 IO 线程并发读写安全）。
- `Translator` 缓存：`files/translations/{sha1(text)}.txt`，离线命中直接秒显。
- `CacheManager`：统计总字节数（IO 线程执行）+ `clear()`（删除后重建空目录）。

### 5.3 离线能力

进入应用：读 SharedPreferences 文章缓存秒开；详情页：feed 摘要 + 全文缓存 + 译文缓存三重离线兜底。仅「增量更新」与「首抓全文」需要网络。

---

## 6. 服务流水线

### 6.1 AI 翻译流水线

```
ArticleDetailViewModel.translate()
  → ArticleCache.translations 命中？  → 直接显示
  → TranslationStore.isConfigured()? → 未配置提示去设置
  → 优先翻译全文（若已抓取），否则 feed 摘要
  → CodeBlockExtractor.extract()      ← <pre> 代码块挖出 → @@C0@@ 占位符
  → Translator.translate(placeholderText)  ← OpenAI 兼容 /chat/completions
  → CodeBlockExtractor.restore()      ← 占位符还原为 ```围栏代码```；被改写的 fallback 追加末尾
  → 写入 ArticleCache.translations + 替换原文显示（chips 切换）
```

- 翻译提示词强制「只输出译文、代码/URL/数字/`@@C数字@@` 原样保留」。
- `Translator` 磁盘缓存按原文 SHA-1 落盘，重复进入/离线直接命中。
- 响应上限 2MB；错误流包含 HTTP code + 截断的响应体。

### 6.2 后台自动同步（FeedSyncWorker + SyncScheduler）

- `PeriodicWorkRequestBuilder(intervalHours)` 按 `UpdateSettings` 调度；`interval=0`（手动）时取消。
- 约束：`NetworkType.CONNECTED`；退避：`EXPONENTIAL, 10min`。
- Worker 内：取启用源 → 只更新 `needsUpdate()` 的源 → 全部失败返回 `Result.retry()`（交给 WorkManager 指数退避），否则 `success`。
- 触发点：`FeedLiteApp.onCreate`（启动调度）+ 设置页间隔选择（立即重注册 `ExistingPeriodicWorkPolicy.UPDATE`）。

> 意义：此前「自动更新」只在打开应用时生效；现在升级为系统级调度，省电/后台受限由系统管理。

### 6.3 请求合并与预取（android-compose demo，可迁移模式）

```
ArticleRepository.getArticleDetail(id):
  detailCache 命中 → 直接返回
  inFlightDetail[id] ?: scope.async { api.getArticleDetail(id) }   ← Mutex 保护
  await 成功后写 detailCache；finally 移除 inFlightDetail
```

- **同 id 并发请求只发一次网络调用**（路由预取 + 页面自身请求合并）。
- 失败路径：在途项被清理，重试重新发起（单测锁定）。
- `markPrefetched(id)` 记录预取尝试；`wasPrefetched(id)` = 尝试过 **且** 已入缓存——徽章判定基于缓存事实，不依赖耗时猜测。
- 该模式可直接下沉到产品：真实项目把 Mutex+Deferred 合并层放到 OkHttp 拦截器或 `Flow<Result<T>>` 层，覆盖所有接口。

---

## 7. 安全设计（v1.32）

| 威胁 | 对策 |
|---|---|
| API Key 明文落盘 | `SecurePrefs`：AES-256/GCM 密钥存 AndroidKeyStore（不可导出），密文 JSON(iv+ct) 落盘；旧明文启动时自动迁移并清除残留 |
| 明文 HTTP 传输 Key | `network_security_config` 默认 `cleartextTrafficPermitted=false`，仅 `export.arxiv.org`（内置源唯一明文源）/`localhost`/`10.0.2.2` 放行 |
| 用户误配 `http://` 翻译端点 | `UrlPolicy.isAllowedTranslationBaseUrl`：仅 https；http 只放行本地/内网（localhost/127./192.168./10.） |
| 恶意 feed 注入危险链接 | `openWebLink` 只允许 http/https scheme + `startActivity` try/catch；自定义源 `addCustom` 校验协议 |
| 响应轰炸/OOM | `HttpUtil.readBounded`：feed 10MB / 全文 5MB / 翻译 2MB，超限抛 `ResponseTooLargeException` |
| SSRF（自定义源指到内网） | 个人阅读器场景低风险；`addCustom` 已做格式校验，后续可加 host 黑名单 |
| 明文图片/资源 | 图片全部走 Coil+OkHttp，域名级 cleartext 白名单仅覆盖已知明文源 |

**正面确认**：全文渲染全走 Compose 文本（无 WebView、无 JS 执行面）；HTML 实体解码做了代理区/越界 code point 过滤（`HtmlText.decodeEntity`），畸形实体不崩应用；OPML 用平台 `XmlPullParser`（无 XXE）。

---

## 8. android-compose demo 数据层

- `ArticleApi`（interface）+ `FakeArticleApi`（60 条数据、随机延迟、picsum 图）——接口化后 JVM 单测可注入 Fake。
- `ArticlePagingSource`：key 为**数据偏移量**（`start = key ?: 0`，`nextKey = start + data.size`），修复 initialLoadSize=40 / pageSize=20 下的区间重叠。
- `PrefetchRouter` + `ArticleRepository`：见 §6.3。

---

## 9. 测试策略

### 9.1 android-rss（JVM 单测，24 个，无 Robolectric）

| 测试类 | 覆盖点 |
|---|---|
| `RssParserTest` | 字段提取 / guid 稳定 key（顺序颠倒不变） / link key / 封面优先级 / 协议相对图补全 / **GBK 中文不乱码** / 300 条上限 |
| `HtmlTextTest` | 标签剥离 / 实体解码 / **代理区与越界 code point 不崩溃** / 段落换行 / 噪音过滤 / 摘要截断 |
| `CodeBlockExtractorTest` | 占位符提取还原 / 被改写占位符回退追加 / HTML 实体还原 |
| `UrlPolicyTest` | https 放行 / http 拒绝 / 本地与内网放行 / 非 http 拒绝 |

> 解析器注入：`RssParser.parseWith(parser, stream, source)` 生产用 `Xml.newPullParser()`，测试用 `org.kxml2.io.KXmlParser`（kxml2 自带 xmlpull API，`testImplementation("net.sf.kxml:kxml2:2.3.0")`）。

### 9.2 android-compose（JVM 单测，2 个）

| 测试类 | 覆盖点 |
|---|---|
| `ArticlePagingSourceTest` | Refresh(40) 与 Append(20) **id 不重叠** / 全 60 条可达 / 越界 Append 空页+nextKey=null（旧页码实现会失败） |
| `ArticleRepositoryTest` | 并发同 id 只发一次网络 / 失败后在途项清理、重试重新发起 / 成功后缓存命中不再请求 |

### 9.3 运行

```powershell
cd android-rss && .\gradlew.bat testDebugUnitTest verifyMotionTokens
cd android-compose && .\gradlew.bat testDebugUnitTest
```

---

## 10. 数据层演进方向

1. **响应式存储**：`ReadingStateStore.version` tick 模式 → `Flow` 驱动的 store，订阅/收藏变更自动广播，删掉各页 `LaunchedEffect(version)` 手动重载与 `refreshTick` hack。
2. **缓存淘汰**：`FullTextCache`/`Translator` 磁盘缓存按访问时间 LRU 淘汰，`CacheManager` 加 eviction（当前只有全清）。
3. **真实网络层替换**：`HttpURLConnection` → OkHttp（已在图片链路引入），超时/重试/拦截统一。
4. **后台同步增强**：per-source 连续失败计数 + 通知（新文章 N 篇）；WorkManager 前置校验源可达性。
5. **key 迁移**：`RssParser` 已切换稳定 key，历史脏数据可在下次启动用旧 key 映射清洗。
