# FeedLite「轻阅 RSS」— 基于 Android 16 的 RSS 阅读器

一个把前文「通用动效 + 加载策略」研究成果落地的**可安装 RSS 应用**：
内置订阅源 → 先加载 5 篇 → 点击加载更多；全程 Compose 动效（共享元素转场 / stagger /
渐进式图片），针对 **Android 16（API 36）** 构建，v1.0。

---

## 一、功能清单

| 功能 | 说明 |
|---|---|
| 内置 8 个 RSS 源 | 默认启用 4 个国内可达源，其余可一键启用 |
| 订阅开关 | 每源一个 Switch，状态持久化（SharedPreferences） |
| 自定义源 | 对话框输入名称 + Feed 地址，可添加 / 删除 |
| 先加载 5 篇 | 进入文章列表只展示前 5 篇（一次网络请求） |
| 点击加载更多 | 底部按钮每次追加 5 篇，直至「已加载全部」 |
| 手动刷新 | 顶栏刷新按钮强制重新抓取（绕过缓存） |
| 文章详情 | 大图、标题、作者、日期、正文（HTML→纯文本）、浏览器打开 |
| 错误兜底 | 加载失败显示原因 + 重试，不影响其他源 |

## 二、技术栈（Android 16）

| 项 | 值 |
|---|---|
| compileSdk / targetSdk | **36（Android 16）** |
| minSdk | 26（Android 8.0） |
| 版本 | versionName 1.0 / versionCode 1 |
| UI | Jetpack Compose（BOM 2024.10.01）+ Material 3 |
| 导航 | Navigation Compose 2.8 + SharedTransitionLayout 共享元素 |
| 解析 | 平台内置 XmlPullParser（RSS 2.0 + Atom，零依赖） |
| 图片 | Coil 2.7（渐进式淡入 300ms） |
| 网络 | HttpURLConnection（超时 10s/15s，UA，自动重定向） |
| 构建 | AGP 8.9.1 / Gradle 8.11.1 / Kotlin 2.0.21 / JDK 17 |

## 三、架构分层

```
┌─────────────────────────────────────────────────┐
│ UI 层（Compose）                                  │
│  FeedListScreen  订阅管理（Switch/添加/删除）        │
│  ArticleListScreen 先5篇 + 加载更多 + 刷新 + stagger │
│  ArticleDetailScreen 共享元素大图 + 正文            │
│  AppNav  路由编排 + 进出场 token + 共享元素 scope    │
├─────────────────────────────────────────────────┤
│ 状态层（ViewModel）                                │
│  FeedListViewModel  订阅状态                     │
│  ArticleListViewModel Loading/Success/Error 状态机 │
├─────────────────────────────────────────────────┤
│ 数据层（data/）                                    │
│  SubscriptionStore  订阅持久化（JSON in Prefs）    │
│  RssRepository      抓取 + 5min 内存缓存 + 刷新    │
│  RssParser          XmlPullParser 解析 RSS/Atom   │
│  HtmlText           富文本 → 纯文本 + 摘要          │
│  FeedCatalog        内置源目录                     │
└─────────────────────────────────────────────────┘
```

### 数据流（先 5 篇的实现路径）

```
用户点开订阅源
  └─ ArticleListViewModel.load()
       ├─ RssRepository.fetchFeed(source)
       │    ├─ 命中 5 分钟内存缓存？→ 直接返回
       │    └─ 未命中 → HttpURLConnection 抓取 → RssParser 解析全部条目
       └─ ArticleListUiState.Success(feed, visibleCount = 5)   ← 先展示 5 篇
点「加载更多」→ loadMore() → visibleCount += 5 → 界面追加渲染
点「刷新」    → repository.refresh()（清缓存）→ load() 重新抓取
```

> 设计取舍：RSS 协议不支持服务端分页，一次只能拿全量 feed。
> 因此「先 5 篇」是 **UI 增量渲染策略**（一次网络请求、按需呈现），
> 首屏只有 5 张卡片要布局/解码图片，低端机也流畅；其余条目零成本待命。

## 四、动效设计（token 全复用）

| 效果 | 实现 | token |
|---|---|---|
| 页面进入（订阅→列表→详情） | `slideIntoContainer(Left)` + fadeIn | 350ms + emphasized |
| 页面退出 / 返回 | `slideOutOfContainer(Right)` + fadeOut | 90ms + emphasized |
| 列表 stagger | `delay(index % 10 * 30ms)` → 位移+淡入 | 30ms/项 + standard |
| 共享元素 | 封面 `thumb_{key}` / 源徽章 `source_{id}` | 350ms + emphasized |
| 渐进式图片 | 模糊色块 → Coil crossfade(300) | 300ms + standard |
| 微交互 | Switch 原生 / 按钮按压 | spring |

详细 token 表见仓库根目录 **`motion-tokens.md`**（唯一事实来源）。

## 五、内置订阅源

| 源 | 默认启用 | 类型 |
|---|---|---|
| 阮一峰的网络日志 | ✅ | Atom |
| 少数派 sspai | ✅ | RSS 2.0 |
| V2EX | ✅ | RSS 2.0 |
| InfoQ 中文 | ✅ | RSS 2.0 |
| 36氪 | ⬜ | RSS 2.0 |
| The Verge | ⬜ | RSS 2.0 |
| BBC News | ⬜ | RSS 2.0 |
| NASA Breaking News | ⬜ | RSS 2.0 |

> 可达性因网络环境而异；抓取失败会在列表页显示原因并可重试，
> 不影响其他源。若全部失败请检查网络/代理。

## 六、构建与签名

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

## 七、安装与验证

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

## 八、已知限制

- RSS 正文仅展示 description/summary 的纯文本（未抓取全文页）；
- 封面图依赖源提供的 enclosure/media 或正文首图，缺图显示柔和色块；
- 无离线缓存（内存缓存重启即失）；刷新是手动触发；
- minSdk 26，Android 8.0 以下不可安装（有意为之，聚焦 Android 16 目标）。
