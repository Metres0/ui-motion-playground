# FeedLite「轻阅 RSS」— 基于 Android 16 的 RSS 阅读器

把「通用动效 + 加载策略」研究成果落地的**可安装 RSS 应用**：
首页聚合流（分类分段）+ 侧边栏源管理 + AI 翻译 + 富文本排版；全程 Compose 动效
（共享元素转场 / stagger / 渐进式图片），针对 **Android 16（API 36）** 构建，**v1.12**。

---

## 一、功能清单

| 功能 | 说明 |
|---|---|
| 首页聚合流 | 并行抓取所有启用源，按「技术/AI/Go/商业/国际」**分类分段**展示 |
| 侧边栏导航 | 分类分组的源管理（开关/添加/删除）、转源帮助、设置、关于 |
| 内置 **13 个 RSS 源** | 新增 AI 类（量子位/OpenAI/HuggingFace/Google AI）与 Go 官方博客 |
| 自定义源 | 抽屉内对话框输入名称 + Feed 地址，可添加 / 删除 |
| **公众号 / 微博转源** | 侧边栏「转源帮助」：Wechat2RSS / RSSHub 部署与路由说明 |
| 先加载 5 篇 | 进入文章列表只展示前 5 篇（一次网络请求） |
| 点击加载更多 | 底部按钮每次追加 5 篇，直至「已加载全部」 |
| **富文本排版** | 标题/段落独立/列表/引用/代码块（深色块+等宽+复制），行内加粗/斜体/链接 |
| **正文图片** | 正文内的图片真实渲染（不再是占位文本），详情页大图 1280px 解码 |
| **阅读设置（文章内）** | 详情页「A」按钮 → 底部面板调字号/行高/字体，即时生效并持久化 |
| **增量更新** | 文章本地持久化，进入秒开；超过设定间隔才自动增量抓取（只加新文章）；可设手动/6h/12h/24h/48h |
| **全文抓取** | feed 只有摘要的源（少数派等）进入文章自动抓原始网页正文并应用内渲染（readability-lite） |
| **全文渲染净化** | 自动丢弃 Vue `data-v-` 残留标签与 `<!---->` 注释等噪音，正文干净 |
| **查看全文** | 正文区底部「查看全文」按钮一键跳转原始网站（全文抓取失败时的兜底） |
| **AI 翻译** | **译文替换原文显示，一键切换原文/译文**；代码块不参与翻译；设置页带「测试连接」 |
| **链接可点击** | 正文行内链接点击即打开浏览器；InfoQ 等纯链接源识别为空摘要并引导打开原文 |
| 图片优化 | 列表 360px / 详情 1280px 解码；Referer 防盗链拦截器；cleartext 兼容 |
| 错误兜底 | 加载失败显示原因 + 重试；单个源失败不影响其他源 |

## 二、v1.12 更新日志

1. **应用内品牌图标**：新增自绘渐变卡片 Logo（`ic_brand_logo`，与应用图标同源），
   应用到首页顶栏标题、侧边栏头部、关于对话框、首页/收藏/稍后再看空态——
   应用内不再只有 Material 默认图标，品牌感统一；
2. 功能图标（收藏 / 稍后再看 / 设置等）保持 Material 语义图标保证识别度。

## 二、v1.11 更新日志

1. **现代化 App 图标（Material You 风格）**：
   - 背景：品牌蓝 → 紫 **渐变** + 右上高光 / 底部柔光（玻璃质感）；
   - 前景：**层叠玻璃卡片**（半透明白背层 + 近实心白前层）+ 品牌蓝文本行 + 暖橙新内容提示点；
   - **Android 13+ 主题图标**（`monochrome`）：系统深色/浅色 / 动态取色桌面自动适配单色轮廓。

## 二、v1.10 更新日志

1. **OPML 导入 / 导出订阅**：设置 → 数据管理 →「导出订阅」保存 `feedlite_subscriptions.opml`；
   「导入订阅」读取其他阅读器的 OPML 文件，自动去重添加（与主流 RSS 阅读器互通）；
2. **清除已读标记**：设置 → 数据管理 →「清除全部已读标记」一键重置；
3. **翻译磁盘缓存**：译文按原文 SHA1 落盘（files/translations/），重复进入 / 离线直接命中秒显；
4. **图标体系重设计**：全新应用图标（品牌蓝圆角方块 + RSS 信号波纹 + 右下书签角标）；
   首页顶栏「稍后再看」角标图标（含挂起数量），点击直接进挂起列表；侧边栏收藏 / 稍后再看 /
   设置均配图标；
5. **首页挂起入口**：顶栏书签角标实时显示挂起文章数（>99 显示 99+），点击进入稍后再看列表；
6. **新增 3 个内置源**（共 19 个）：CNBeta 中文业界资讯、Hacker News、arXiv AI 论文；
7. **源搜索**：侧边栏顶部搜索框，可按源名 / 描述 / 分类实时过滤。

## 二、v1.9 更新日志

1. **修复收藏不生效（关键）**：根因是持久化序列化错误——`JSONObject(Map<String,RssItem>)`
   会把每个 RssItem 序列化成字符串，读回时解析失败返回空。改为先 `toJson()` 再存入，
   并换用新数据文件 `reading_state_v2` 丢弃旧损坏数据；收藏/稍后再看现在即时可见；
2. **无缩略图不显示色块**：首页 / 列表 / 收藏 / 稍后再看卡片，文章没有封面图时
   **不再渲染色块占位，直接放标题**（更清爽）；详情页无封面也直接进入标题；
3. **阅读进度记忆**：进入已读过的文章自动恢复到上次滚动位置，离开自动保存；
4. **稍后再看（挂起）**：详情页顶栏**书签按钮**挂起/取消，侧边栏「稍后再看」入口 →
   挂起列表页（书签小图标 + 首字母徽标），点击进入阅读，版本流自动刷新；
5. **小图标**：新增书签/稍后再看等小图标，侧边栏各入口均带图标。

## 二、v1.8 更新日志

1. **脏标签二次兜底**：解析入口全局剥离 `data-v-xxx` 残留碎片；行内文本过滤
   `class="..."` / `data-v-` 等**属性式残留文本**（InfoQ `_preview-wrap_xxx` 等一并拦截）；
2. **修复收藏列表不刷新**：`ReadingStateStore` 增加版本号流，收藏页 / 首页 / 列表
   collect 版本号自动刷新——详情页收藏/取消收藏后返回，列表即时更新；
3. **首页排版改版**：分类头部改为「分类名 + 右侧篇数」；卡片改为
   **源名 + 相对时间（x 分钟/小时/天前）同排 + 加粗标题 + 未读圆点**，更清爽；
4. **深色模式**：设置 → 外观 → **跟随系统 / 浅色 / 深色**三选，全局即时生效；
5. **收藏导出**：收藏页顶栏「导出」→ 系统文件选择器保存 **feedlite_favorites.json**；
6. **全文离线缓存**：抓取到的全文 HTML 落盘（files/fulltext/），离线重开文章直接显示缓存全文。

## 二、v1.7 更新日志

1. **根治 data-v 脏标签残留**：所有标签匹配改为**引号感知正则**（跳过引号内的 `>`），
   彻底杜绝 `data-v-...>` 之类属性残留被当文本输出；解析前先剥离 `<!-- -->` 注释；
   emoji/图标图片自动过滤不渲染；
2. **全文噪音清理**：ArticleFetcher 按 `comment/extend/related/recommend/suggest/emoji/share`
   关键词**配对移除评论、相关推荐、表情容器**，少数派文章不再带出评论区和推荐；
3. **正文图片点击看大图**：正文图片自由比例渲染（不再强制 3:2 裁切），**点击弹出全屏大图**（Dialog 适配）；
4. **已读 / 收藏**：
   - 进入详情自动标记已读；首页与列表**未读小圆点**、已读标题变灰变轻；
   - 详情页顶栏**星标收藏**按钮（☆/★），侧边栏「我的收藏」入口 → 收藏列表页（共享元素转场）；
5. **阮一峰换源**：源端仅输出 3 篇，替换为 **Solidot 奇客**（20+ 篇、更新频繁），
   并新增 **IT之家 / 爱范儿**（默认关闭，可启用）。

## 二、v1.6 更新日志

1. **修复全文脏文本**：HtmlBlocks 行内解析丢弃所有未知标签与 `<!-- -->` 注释，
   彻底消除少数派等 Vue 站全文里的 `data-v-c065c440>!---->` 之类残留；
   块级将 `div/section/article/figure` 作为段落分隔边界，正文分段更合理；
2. **修复全文图片**：实测 sspai 等 CDN **无 Referer 返回 403**——新增 `ImageContext`
   记录当前文章域名，Coil 拦截器优先用文章域作 Referer（进入详情设置、离开清空），
   全文图片可正常加载；`ArticleFetcher` 同时补全相对路径图片 URL；
3. **阮一峰「只有 3 篇」说明**：该源 atom.xml **本身只输出最近 3 期周刊**（实测 74,986 字节 =
   第 404/405/406 期），属源端限制而非应用问题；手动刷新可获取最新一篇。

## 二、v1.5 更新日志

1. **应用内全文抓取**：新增 `ArticleFetcher`（readability-lite）——当 feed 正文过短
   （少数派等仅摘要源）时，进入文章自动抓取原始网页，按候选容器（`article-body` /
   `article-content` / `post-content` / `entry-content` / `markdown-body` / `rich_media_content` /
   `topic_content` / `<article>` / `<main>`）定位正文，div 配对截取并清理噪音，**应用内直接渲染全文**；
2. **「查看全文」按钮**：正文区底部固定显示，一键跳转原始网站；全文抓取失败时
   自动回退到该按钮（并可重试加载全文）；
3. **翻译增强**：全文抓取成功时优先翻译全文而非摘要。

## 二、v1.4 更新日志

1. **增量更新机制**：新增 `ArticleStore` 本地持久化 + `UpdateSettings` 更新策略；
   - 进入应用/进入源：**先读本地缓存秒开**，不再每次全量刷新；
   - 超过设定间隔（默认 24h，可选手动/6h/12h/24h/48h）才在后台增量抓取；
   - 增量 = 按文章 key 去重，**只插入新文章**，旧文章零重复请求；
   - 顶栏刷新按钮 = 手动强制增量更新；删除订阅时清理缓存；
2. **翻译替换原文**：翻译完成后正文区域直接显示译文（替换原文），
   顶部「原文 / 译文」chips **一键切换**，译文缓存保留；
3. **InfoQ 处理**：诊断确认 InfoQ feed **不提供图片与正文**（description 仅"点击查看原文"），
   新增 `HtmlText.hasMeaningfulContent` 识别空摘要——卡片不再显示噪音文本，
   详情页显示"该源未提供正文摘要" + 「打开原文链接」按钮；
4. **链接优化**：正文行内链接渲染为 primary 色 + 下划线，**点击直接打开浏览器**
   （ClickableText + URL annotation）；首页/列表摘要过滤纯链接噪音。

## 二、v1.3 更新日志

1. **正文图片真实渲染**：重写 `HtmlBlocks` 为正则级扫描，正文（含段落内嵌）的 `<img>`
   拆分为独立图片块，由 `ProgressiveImage` 真实加载显示——修复「阮一峰 405 期等正文图片消失」；
2. **段落独立排版**：每个 `<p>` 独立成段、`<h2>` 等标题分级加粗，正文不再挤成一大段；
3. **阅读设置移入文章**：详情页顶栏新增「A」按钮 → 底部面板调字号/行高/字体，
   拖动即时生效并自动保存，设置页保留同款设置（两者共享同一配置）；
4. **翻译测试连接**：设置页「测试连接」按钮用当前配置发一条真实翻译请求，
   成功/失败即时反馈——key、Base URL、网络问题一目了然。

## 二、v1.2 更新日志

1. **图片修复**：全局 Coil OkHttp 拦截器自动携带 `Referer` + 完整 UA，解决
   theverge/部分中文站图片防盗链；`usesCleartextTraffic` 兼容 http 图片源；
   解析器支持 `srcset`、`media:thumbnail`、enclosure(image) 提取，data URI 忽略；
2. **排版优化**：新增 `HtmlBlocks` 轻量 HTML→Compose 渲染器（标题/段落/有序无序列表/
   引用/分隔线/代码块），详情页从纯文本升级为富文本；代码块深色背景 + 等宽字体 +
   一键复制；
3. **阅读设置**：设置页新增字号/行高/字体三组调节，即时应用于详情页；
4. **翻译保护代码块**：`CodeBlockExtractor` 在翻译前提取 `<pre>` 代码块为占位符，
   译文还原代码原文，**代码永不参与翻译**；
5. **新增 5 个源**（共 13 个）：量子位（AI，默认开）、OpenAI News、Hugging Face、
   Google AI、Go 官方博客（默认开）；
6. **源分类**：技术 / AI / Go / 商业 / 国际 / 自定义，首页按分类分段、侧边栏按分类分组；
7. **公众号/微博转源**：应用内「转源帮助」对话框 + 本文档第八章，说明
   Wechat2RSS（自建）与 RSSHub 路由的接入方式。

## 三、技术栈（Android 16）

| 项 | 值 |
|---|---|
| compileSdk / targetSdk | **36（Android 16）** |
| minSdk | 26（Android 8.0） |
| 版本 | versionName 1.12 / versionCode 13 |
| UI | Jetpack Compose（BOM 2024.10.01）+ Material 3 |
| 导航 | Navigation Compose 2.8 + SharedTransitionLayout 共享元素 |
| 解析 | 平台内置 XmlPullParser（RSS 2.0 + Atom，零依赖） |
| 图片 | Coil 2.7（渐进式淡入 300ms） |
| 网络 | HttpURLConnection（超时 10s/15s，UA，自动重定向） |
| 构建 | AGP 8.9.1 / Gradle 8.11.1 / Kotlin 2.0.21 / JDK 17 |

## 四、架构分层

```
┌──────────────────────────────────────────────────────┐
│ UI 层（Compose）                                       │
│  HomeScreen          首页聚合流 + ModalNavigationDrawer│
│  DrawerContent       侧边栏：源管理 / 设置 / 关于         │
│  ArticleListScreen   单源列表：先5篇 + 加载更多 + stagger │
│  ArticleDetailScreen 共享元素大图 + 正文 + 翻译区块       │
│  SettingsScreen      翻译服务配置（服务商/URL/Key/模型）  │
│  AppNav  路由编排 + 进出场 token + 共享元素 scope         │
├──────────────────────────────────────────────────────┤
│ 状态层（ViewModel）                                     │
│  HomeViewModel        聚合流 + 源管理状态               │
│  ArticleListViewModel Loading/Success/Error 状态机     │
│  ArticleDetailViewModel 翻译状态机（Idle/译中/完成/错误）│
│  SettingsViewModel    翻译配置读写                      │
├──────────────────────────────────────────────────────┤
│ 数据层（data/）                                         │
│  SubscriptionStore    订阅持久化（JSON in Prefs）       │
│  TranslationStore     翻译 API 配置持久化               │
│  RssRepository        抓取 + 5min 内存缓存 + 刷新       │
│  RssParser            XmlPullParser（RSS/Atom，加固）   │
│  Translator           OpenAI 兼容 /chat/completions    │
│  HtmlText             富文本 → 纯文本（安全解码）        │
│  FeedCatalog          内置源目录                        │
└──────────────────────────────────────────────────────┘
```

### 数据流

```
打开 App（home 路由）
  └─ HomeViewModel.load()
       ├─ 并行抓取所有启用源（个别失败被吞掉，只影响计数）
       └─ 合并为 FeedEntry 列表 → 首页聚合流
点击某篇文章
  ├─ ArticleCache.put(item.key, item)
  └─ navigate article/{itemKey} → 共享元素转场
        └─ ArticleDetailViewModel.translate(正文)
             ├─ 命中缓存？→ 直接显示
             ├─ 未配置 Key？→ 提示去设置
             └─ 调用 {baseUrl}/chat/completions → 译文显示

点「加载更多」→ visibleCount += 5 → 界面追加渲染
点「刷新」    → repository.refresh()（清缓存）→ 重新抓取
```

> 设计取舍：RSS 不支持服务端分页，一次只能拿全量 feed。「先 5 篇」是
> **UI 增量渲染策略**（一次网络请求、按需呈现），首屏只有 5 张卡片要布局/解码，
> 低端机也流畅；其余条目零成本待命。

## 五、动效设计（token 全复用）

| 效果 | 实现 | token |
|---|---|---|
| 页面进入（订阅→列表→详情） | `slideIntoContainer(Left)` + fadeIn | 350ms + emphasized |
| 页面退出 / 返回 | `slideOutOfContainer(Right)` + fadeOut | 90ms + emphasized |
| 列表 stagger | `delay(index % 10 * 30ms)` → 位移+淡入 | 30ms/项 + standard |
| 共享元素 | 封面 `thumb_{key}` | 350ms + emphasized |
| 渐进式图片 | 模糊色块 → Coil crossfade(300) | 300ms + standard |
| 代码块复制反馈 | 系统触摸反馈 | — |

详细 token 表见仓库根目录 **`motion-tokens.md`**（唯一事实来源）。

## 六、内置订阅源（13 个 · 按分类）

| 分类 | 源 | 默认启用 |
|---|---|---|
| 技术 | 阮一峰的网络日志 | ✅ |
| 技术 | 少数派 sspai | ✅ |
| 技术 | V2EX | ✅ |
| 技术 | InfoQ 中文 | ✅ |
| AI | 量子位 | ✅ |
| AI | Hugging Face Blog | ⬜ |
| AI | OpenAI News | ⬜ |
| AI | Google AI Blog | ⬜ |
| Go | Go 官方博客 | ✅ |
| 商业 | 36氪 | ⬜ |
| 国际 | The Verge | ⬜ |
| 国际 | BBC News | ⬜ |
| 国际 | NASA Breaking News | ⬜ |

> 可达性因网络环境而异；抓取失败会在列表页显示原因并可重试，
> 不影响其他源。若全部失败请检查网络/代理。

## 七、公众号 / 微博 转 RSS

RSS 协议本身不含公众号/微博内容，需要中间服务把它们转成标准 feed，再把地址
粘贴到「添加订阅源」。

### 微信公众号
1. **Wechat2RSS**（wechat2rss.xlab.app，开源 [ttttmr/wechat2rss](https://github.com/ttttmr/wechat2rss)）：
   自建服务后，把公众号链接（mp.weixin.qq.com/...）提交，获得对应 RSS 地址；
2. **RSSHub**（github.com/DIYgod/RSSHub）`/wechat/` 相关路由，公共实例可能不稳定，建议自建；
3. 把得到的 feed 地址粘贴到本应用的「添加订阅源」。

### 微博
1. 自建 **RSSHub**，使用路由 `/weibo/user/{uid}`；
2. `uid` 是微博用户数字 ID（个人主页 URL 中可见）；
3. 公共实例可能限流/失效（RSSHub issue 中微博路由偶发 432/受限），自建最稳。

> 应用内侧边栏 → 「公众号 / 微博转源帮助」内置了上述说明。

## 八、构建与签名

### 前置条件（本机已配好）

| 工具 | 位置 |
|---|---|
| JDK 17 (Temurin) | `C:\dev\jdk-17.0.20+8` |
| Gradle 8.11.1 | `C:\dev\gradle-8.11.1` |
| Android SDK 36 | `C:\Android\sdk`（platforms/android-36, build-tools/36.0.0, platform-tools） |

环境变量已写入用户级：`JAVA_HOME`、`ANDROID_HOME`、`ANDROID_SDK_ROOT`，PATH 含 `%JAVA_HOME%\bin` 与 gradle bin。

### 构建 APK

```powershell
cd C:\Users\Administrator\Desktop\UI-All\android-rss
.\gradlew.bat assembleRelease
# 产物：app\build\outputs\apk\release\app-release.apk
```

### 签名信息

本地演示 keystore 位于 `android-rss/keystore/release.keystore`（**不入库**）。
构建时通过环境变量注入密码，避免明文入库：

```powershell
$env:FEEDLITE_STORE_PASS = "你的store密码"
$env:FEEDLITE_KEY_PASS  = "你的key密码"
```

> 仓库内 `app/build.gradle.kts` 保留了本机演示用的默认密码，
> 仅当本地存在 keystore 文件时才生效；**正式发布请更换 keystore 并改走环境变量**。

## 九、安装与验证

```powershell
# 方式一：直接拷贝 app-release.apk 到手机安装（需允许未知来源）
# 方式二：USB 连接后
C:\Android\sdk\platform-tools\adb.exe install -r app\build\outputs\apk\release\app-release.apk
```

验证清单：
1. 打开 App → 订阅源页 4 个默认启用，卡片 stagger 滑入；
2. 点「少数派」→ 文章列表只显示 **5 篇** → 底部「加载更多（5/N）」；
3. 点加载更多 → 追加 5 篇，直到「已加载全部 N 篇」；
4. 点一篇文章 → 封面从卡片位置**平滑放大**到详情页（共享元素）；
5. 系统返回 → 详情/列表镜像退场；
6. 顶栏刷新 → 重新抓取（可先在飞行模式验证错误态与重试）；
7. 添加自定义源（如 `https://feeds.bbci.co.uk/news/rss.xml`）→ 出现在列表并可进入；
8. 杀掉进程重开 → 订阅状态保留。

## 十、已知限制

- RSS 正文仅展示 description/summary 的纯文本（未抓取全文页）；
- 封面图依赖源提供的 enclosure/media 或正文首图，缺图显示柔和色块；
- 无离线缓存（内存缓存重启即失）；刷新是手动触发；
- minSdk 26，Android 8.0 以下不可安装（有意为之，聚焦 Android 16 目标）。
