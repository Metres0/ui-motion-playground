// ============================================================
// UI Playground · Web
// 演示：hover 预取 → 点击 View Transitions 共享元素转场 → 渐进式加载
// 与 android-compose 端共用同一套动效 token（见 motion-tokens.md）
//
// 结构：
//   ① 纯函数区 —— 不依赖 DOM，可单元测试
//      （makeArticle / makeDetail / 预取簿记：意图记录、徽章判定、
//       LRU 容量封顶、可中止的模拟网络请求）
//   ② DOM 接线区 —— 渲染、预取调度、View Transitions、焦点与无障碍
// ============================================================

// ════════════════════════════════════════════════════════════
// ① 纯函数区（无 DOM，可单测）
// ════════════════════════════════════════════════════════════

const TOTAL = 20;
const SUBS = ['共享元素转场', '预测式返回', 'Paging 预载', '请求合并', '路由预取', '渐进式图片', 'stagger 列表', 'View Transitions'];
const CACHE_LIMIT = 30;          // 预取缓存/意图簿记上限（按“最近插入”封顶）
const FETCH_DELAY_MIN = 250;     // 模拟网络延迟区间（ms）
const FETCH_DELAY_RANGE = 350;
const TOUCH_PREFETCH_DELAY = 80; // 触摸预取延迟：区分点按与滚动
const TOUCH_MOVE_THRESHOLD = 10; // 位移超过该阈值视为滚动（px）

const coverUrl = (id) => `https://picsum.photos/seed/article${id}/600/400`;
const coverColor = (id) => `hsl(${(id * 47) % 360} 22% 92%)`;

function makeArticle(id) {
  return {
    id,
    title: `示例文章 #${id}`,
    subtitle: SUBS[(id - 1) % SUBS.length],
    cover: coverUrl(id),
  };
}

function makeDetail(a) {
  let body = `这是第 ${a.id} 篇文章的正文。它演示了「hover 预取 + View Transitions 共享元素 + 渐进式图片」的完整链路。\n\n`;
  for (let p = 1; p <= 6; p++) {
    body += `段落 ${p}：当你在列表页 hover 这张卡片时，预取已提前发起；点击后 View Transitions 的 350ms 转场掩盖了剩余的加载时间，所以看起来总是「秒开」。\n\n`;
  }
  return { ...a, body, readTime: 2 + (a.id % 8) };
}

// 预取簿记状态（状态注入，便于测试）
function createPrefetchState() {
  return {
    promises: new Map(), // id -> { promise, controller }
    done: new Map(),     // id -> true：已成功完成（簿记用）
    hovered: new Map(),  // id -> true：点击前有 hover/touch 意图
  };
}

// 容量封顶：超出上限时删除最旧条目（Map 迭代序 = 插入序）
function evictOverflow(map, limit) {
  while (map.size > limit) map.delete(map.keys().next().value);
}

// 记录意图（必须在发起预取之前调用）
function markIntent(state, id) {
  state.hovered.set(id, true);
  evictOverflow(state.hovered, CACHE_LIMIT);
}

// 徽章判定：只有「点击前有 hover/touch 意图」才算预取命中。
// 纯键盘 / 无 hover 的点击不会显示徽章。
function shouldShowBadge(state, id) {
  return state.hovered.has(id);
}

// 可中止的模拟网络请求（AbortController → 清除定时器 + reject AbortError）
function fetchDetail(id, signal) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(
      () => resolve(makeDetail(makeArticle(id))),
      FETCH_DELAY_MIN + Math.random() * FETCH_DELAY_RANGE
    );
    signal.addEventListener('abort', () => {
      clearTimeout(timer);
      reject(new DOMException('Aborted', 'AbortError'));
    });
  });
}

// ════════════════════════════════════════════════════════════
// ② DOM 接线区（依赖浏览器环境）
// ════════════════════════════════════════════════════════════

const state = createPrefetchState();

const listView = document.getElementById('list-view');
const detailView = document.getElementById('detail-view');
const cardsEl = document.getElementById('cards');
const hero = document.getElementById('detail-hero');
const badge = document.getElementById('prefetch-badge');
const skeleton = document.getElementById('detail-skeleton');
const titleEl = document.getElementById('detail-title');
const subEl = document.getElementById('detail-sub');
const bodyEl = document.getElementById('detail-body');

let currentId = null;
let lastOpenedCard = null; // 记录来源卡片：关闭详情后恢复焦点
let transitionGen = 0;     // 转场代际：防止旧 finally 误删 backward

let touchMoved = false;
let touchStartX = 0;
let touchStartY = 0;
let touchPrefetchTimer = null;

// ── 预取（与 Android PrefetchRouter 同职责：意图提前于渲染） ──
function prefetchDetail(id) {
  const cached = state.promises.get(id);
  if (cached) return cached.promise;

  const controller = new AbortController();
  const promise = fetchDetail(id, controller.signal)
    .then((detail) => {
      state.done.set(id, true);
      evictOverflow(state.done, CACHE_LIMIT);
      return detail;
    })
    .catch((err) => {
      // 失败/被中止的请求都不缓存，下次可重新发起
      state.promises.delete(id);
      throw err;
    });

  state.promises.set(id, { promise, controller });
  evictOverflow(state.promises, CACHE_LIMIT);
  return promise;
}

// 点击落点确定后，中止其他仍在进行的预取
function abortOtherPrefetches(id) {
  for (const [pid, entry] of state.promises) {
    if (pid !== id) {
      entry.promise.catch(() => {}); // 吞掉被中止请求的 rejection，避免 unhandled
      entry.controller.abort();
    }
  }
}

// ── 列表渲染（stagger 进入 + 预取 / 键盘 / 触摸绑定） ────────
function renderList() {
  cardsEl.innerHTML = '';
  for (let i = 0; i < TOTAL; i++) {
    const a = makeArticle(i + 1);

    const card = document.createElement('article');
    card.className = 'card';
    card.dataset.id = a.id;
    card.tabIndex = 0;
    card.setAttribute('role', 'button');
    card.setAttribute('aria-label', a.title);
    card.style.animationDelay = `${((a.id - 1) % 12) * 30}ms`; // stagger 错峰 30ms

    const img = document.createElement('img');
    img.className = 'card-cover';
    img.alt = a.title;
    img.loading = 'lazy';
    img.style.viewTransitionName = `cover-${a.id}`; // ★ 共享元素 key（与详情 hero 同名）
    img.style.background = coverColor(a.id);
    img.src = a.cover;
    img.onload = () => img.classList.add('loaded');
    img.onerror = () => {
      if (img.classList.contains('error')) return;
      img.classList.add('error');   // 占位色回退
      img.removeAttribute('src');   // 去掉裂图，露出背景占位色
    };

    const body = document.createElement('div');
    body.className = 'card-body';
    const h = document.createElement('h3');
    h.textContent = a.title;
    const p = document.createElement('p');
    p.textContent = a.subtitle;
    body.append(h, p);

    card.append(img, body);

    // 桌面：hover 先记意图，再发起预取
    card.addEventListener('mouseenter', () => {
      markIntent(state, a.id);
      prefetchDetail(a.id);
    });

    // 移动：延迟 80ms 再预取；滚动（位移>阈值）时取消 → 滚动不预取、点按保留
    card.addEventListener('touchstart', (e) => {
      touchMoved = false;
      const t = e.touches[0];
      touchStartX = t.clientX;
      touchStartY = t.clientY;
      clearTimeout(touchPrefetchTimer);
      touchPrefetchTimer = setTimeout(() => {
        touchPrefetchTimer = null;
        if (!touchMoved) {
          markIntent(state, a.id);
          prefetchDetail(a.id);
        }
      }, TOUCH_PREFETCH_DELAY);
    }, { passive: true });

    card.addEventListener('click', () => openCard(card, a.id));
    // 键盘：Enter / Space 打开
    card.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        openCard(card, a.id);
      }
    });

    cardsEl.appendChild(card);
  }
}

// ── 详情视图 ────────────────────────────────────────────────
function showDetailShell(id) {
  currentId = id;
  listView.hidden = true;
  detailView.hidden = false;

  hero.hidden = false;
  hero.style.viewTransitionName = `cover-${id}`; // ★ 与列表卡片同名 → 自动位移缩放
  hero.classList.remove('loaded', 'error');
  hero.style.background = coverColor(id);
  hero.removeAttribute('src'); // 渐进式：先占位色，加载完成再淡入

  titleEl.textContent = '';
  subEl.textContent = '';
  bodyEl.textContent = '';
  badge.hidden = true;

  skeleton.className = 'skeleton-block visible';
  skeleton.innerHTML = `
    <div class="skeleton hero"></div>
    <div class="skeleton title" style="margin-top:16px"></div>
    <div class="skeleton line" style="width:40%"></div>
    <div class="skeleton line" style="margin-top:16px"></div>
    <div class="skeleton line"></div>
    <div class="skeleton line"></div>`;
}

function renderDetail(a) {
  const wasPrefetched = shouldShowBadge(state, a.id); // 只有意图命中的预取才显示徽章

  skeleton.className = 'skeleton-block';
  skeleton.innerHTML = '';
  badge.hidden = !wasPrefetched;

  titleEl.textContent = a.title;
  subEl.textContent = `${a.subtitle} · 阅读约 ${a.readTime} 分钟`;
  bodyEl.textContent = a.body;

  hero.classList.remove('error');
  hero.style.background = coverColor(a.id);
  hero.src = a.cover;
  hero.onload = () => {
    hero.classList.remove('error');
    hero.classList.add('loaded'); // 渐进式淡入
  };
  hero.onerror = () => {
    if (hero.classList.contains('error')) return;
    hero.classList.add('error'); // 占位色回退
    hero.classList.remove('loaded');
    hero.removeAttribute('src');
  };
  if (hero.complete && hero.naturalWidth > 0) hero.classList.add('loaded');
  else if (hero.complete) hero.classList.add('error'); // 缓存里也是失败的图
}

// ── 页面切换（View Transitions） ────────────────────────────
function focusDetail() {
  titleEl.focus({ preventScroll: true }); // 详情标题可聚焦（index.html 已加 tabindex="-1"）
}

function openCard(card, id) {
  lastOpenedCard = card; // 记录来源卡片，供关闭后恢复焦点
  openDetail(id);
}

function openDetail(id) {
  abortOtherPrefetches(id); // 点击落点确定：中止其他进行中的预取

  if (!document.startViewTransition) {
    showDetailShell(id);
    focusDetail();
    finishOpen(id);
    return;
  }

  transitionGen += 1;
  document.documentElement.classList.remove('backward');
  document.startViewTransition(() => showDetailShell(id));
  focusDetail();
  finishOpen(id);
}

async function finishOpen(id) {
  let detail;
  try {
    detail = await prefetchDetail(id);
  } catch (err) {
    if (err && err.name === 'AbortError') return; // 预取被中止：静默返回
    throw err;
  }
  if (currentId !== id) return; // 竞态保护：用户已返回/已切换
  renderDetail(detail);
}

function restoreFocus() {
  if (lastOpenedCard) lastOpenedCard.focus();
}

function closeDetail() {
  if (!document.startViewTransition) {
    detailView.hidden = true;
    listView.hidden = false;
    currentId = null;
    restoreFocus();
    return;
  }

  transitionGen += 1;
  const myGen = transitionGen;
  document.documentElement.classList.add('backward'); // 镜像方向：旧页右出、新页左入
  const t = document.startViewTransition(() => {
    detailView.hidden = true;
    listView.hidden = false;
    currentId = null;
  });
  // 竞态保护：只有本转场仍是最新一代时才移除 backward（避免新转场已被误伤）
  t.finished.catch(() => {}).finally(() => {
    if (transitionGen === myGen) document.documentElement.classList.remove('backward');
    restoreFocus();
  });
}

document.getElementById('back-btn').addEventListener('click', closeDetail);

// 触摸滚动识别：位移超过阈值即取消延迟预取（防止滚动时请求突发）
document.addEventListener('touchmove', (e) => {
  if (touchPrefetchTimer && e.touches.length) {
    const t = e.touches[0];
    if (Math.hypot(t.clientX - touchStartX, t.clientY - touchStartY) > TOUCH_MOVE_THRESHOLD) {
      touchMoved = true;
      clearTimeout(touchPrefetchTimer);
      touchPrefetchTimer = null;
    }
  }
}, { passive: true });

document.addEventListener('touchend', () => {
  if (touchMoved) {
    clearTimeout(touchPrefetchTimer);
    touchPrefetchTimer = null;
  }
  touchMoved = false;
}, { passive: true });

// ── 启动 ────────────────────────────────────────────────────
renderList();
