# Motion Tokens — 通用动效规范（Android Compose ↔ Web 共用）

> 这份文档是双端 demo 的**唯一动效事实来源**。`android-compose/` 和 `web-view-transitions/`
> 两端各自把下面四张表翻译成 Compose 动画参数 / CSS 变量。改动效只改这里，两端同步。

---

## 1. 时长（Duration Scale）

| Token | 值 | 典型用途 |
|---|---|---|
| `duration.instant` | 0 ms | 即时状态切换 |
| `duration.micro` | 100 ms | 触摸反馈（ripple）、hover 高亮 |
| `duration.fast` | 180 ms | 小元素淡入淡出、按钮按下释放 |
| `duration.base` | 220 ms | 常规 UI 状态变化、tooltip |
| `duration.enter` | 350 ms | **页面进入**（列表→详情） |
| `duration.exit` | 90 ms | **页面退出**（离开当前页，必须比进入快） |
| `duration.expansive` | 500 ms | 大图放大、全屏沉浸转场 |

**规则：** 进出场永远不对称。进入 = `enter`（350ms，有"被展开"的舒展感），
退出 = `exit`（90ms，让返回/关闭显得"响应迅速"）。

---

## 2. 缓动（Easing）——两端严格一致

| Token | Compose（Kotlin） | Web（CSS / Motion） | 特性 |
|---|---|---|---|
| `easing.standard` | `FastOutSlowInEasing` | `cubic-bezier(0.4, 0, 0.2, 1)` | 通用标准曲线 |
| `easing.emphasized` | `CubicBezierEasing(0.2f, 0f, 0f, 1f)` | `cubic-bezier(0.2, 0, 0, 1)` | Material 3 强调曲线，**页面转场首选** |
| `easing.decelerate` | `LinearOutSlowInEasing` | `cubic-bezier(0, 0, 0.2, 1)` | 入场减速，物体"落地" |
| `easing.accelerate` | `FastOutLinearInEasing` | `cubic-bezier(0.4, 0, 1, 1)` | 出场加速，物体"离去" |
| `easing.spring` | `spring(dampingRatio = 0.8f, stiffness = 400f)` | `cubic-bezier(0.34, 1.56, 0.64, 1)` | 弹性回弹（仅用于微交互，**禁止**用于整页转场） |

> 为什么不能随便用 ease-in-out？Material 设计规范要求进入用减速（decelerate）、
> 离开用加速（accelerate）。两端共用同一组曲线，手感才能一致。

---

## 3. 位移（Distance Scale）

| Token | Android | Web | 典型用途 |
|---|---|---|---|
| `space.micro` | 4 dp | 4 px | 图标微位移 |
| `space.small` | 8 dp | 8 px | 卡片 hover、触摸回弹 |
| `space.page` | 56 dp | 56 px | 页面级水平推入（列表→详情） |
| `space.full` | 屏高 | 100vh | 底部弹层、全屏沉浸 |

页面级转场位移规则：
- **水平推入**（列表→详情）：进入页从右往左 `+space.page` 起始；返回时反向。
- **垂直弹层**：从底部 `space.full` 滑入。
- 微交互位移永远 ≤ `space.small`，避免"飘"。

---

## 4. 遮罩 / 模糊（Overlay）

| Token | Android | Web | 用途 |
|---|---|---|---|
| `overlay.none` | — | — | 平级页面切换（列表↔详情） |
| `overlay.scrim.light` | 黑色 alpha 0.12 | `rgba(0,0,0,0.12)` | 按压反馈 |
| `overlay.scrim.dark` | 黑色 alpha 0.4 | `rgba(0,0,0,0.4)` | 弹层背景 |
| `overlay.blur` | `Modifier.blur(24.dp)` / RenderEffect | `backdrop-filter: blur(24px)` | 毛玻璃、播放器页 |

**性能警告：** 模糊是两端最重的效果，只允许用在少数层级（弹层、沉浸页），
且**动画进行中不得改变 blur 半径**（会引起重栅格化）。

---

## 5. 标准效果组合表（实现时直接查这张表）

| 效果 | 时长 | 缓动 | 位移 | 遮罩 | Android 实现 | Web 实现 |
|---|---|---|---|---|---|---|
| 淡入淡出 | enter/exit | emphasized | — | none | `fadeIn` / `fadeOut` | View Transitions 默认 cross-fade |
| 水平推入（列表→详情） | 进 350 / 出 90 | emphasized | `space.page` | none | `slideIntoContainer` | `::view-transition-group` transform |
| 垂直弹层 | 300 | emphasized | `space.full` | scrim.dark | `slideInVertically` | `translateY` + backdrop |
| 共享元素（列表→大图） | 350 | emphasized | 自动计算 | none | `SharedTransitionLayout` + `sharedElement` | 同名 `view-transition-name` 自动位移缩放 |
| 容器变换 | 300 | emphasized | — | none | `AnimatedContent` + `SizeTransform` | `::view-transition-old/new(root)` |
| 预测式返回 | 跟手 | decelerate | `space.full` | scrim.light | `predictiveBackHandler` | 浏览器 `pageswap`/`pagereveal` |
| 列表逐项进入 (stagger) | fast + 40ms/index | standard | `space.small` | — | `Animatable` + `delay(index * 40)` | Motion `staggerChildren` |
| 弹簧回弹（按压） | spring | spring | `space.small` | scrim.light | `spring(0.8f)` | `cubic-bezier(0.34,1.56,0.64,1)` |
| 渐进式图片 | 300 crossfade | standard | — | — | Coil `crossfade(300)` | `opacity` 过渡 + BlurHash |

---

## 6. 落地映射（两端同一条 token 链）

```
motion-tokens.md
   │
   ├─▶ android-compose/MotionTokens.kt        ← 编译期常量，Compose 动画直接引用
   │       └─ MotionTokens.Compose: easing / duration 对象
   │
   └─▶ web-view-transitions/styles.css        ← CSS 自定义属性
           └─ :root { --dur-enter: 350ms; --ease-emphasized: cubic-bezier(...); ... }
```

验证方法见 `README.md` 第五节。
