// ============================================================
// UI Playground · Web
// 演示：hover 预取 → 点击 View Transitions 共享元素转场 → 渐进式加载
// 与 android-compose 端共用同一套动效 token（见 motion-tokens.md）
// ============================================================

// ── 模拟数据层 ──────────────────────────────────────────────
const TOTAL = 20;
const SUBS = ['共享元素转场', '预测式返回', 'Paging 预载', '请求合并', '路由预取', '渐进式图片', 'stagger 列表', 'View Transitions'];

// 预取 Promise 表 + 已完成标记（对应 Android 端 PrefetchRouter + 请求合并）
const detailPromises = new Map();
const prefetchedDone = new Set();
let currentId = null;

const coverUrl = (id) => `https://picsum.photos/seed/article${id}/600/400`;
const coverColor = (id) => `hsl(${(id * 47) % 360} 22% 92%)`;
const makeArticle = (id) => ({ id, title: `示例文章 #${id}`, subtitle: SUBS[(id - 1) % SUBS.length], cover: coverUrl(id) });

function makeDetail(a) {
  let body = `这是第 ${a.id} 篇文章的正文。它演示了「hover 预取 + View Transitions 共享元素 + 渐进式图片」的完整链路。\n\n`;
  for (let p = 1; p <= 6; p++) {
    body += `段落 ${p}：当你在列表页 hover 这张卡片时，预取已提前发起；点击后 View Transitions 的 350ms 转场掩盖了剩余的加载时间，所以看起来总是「秒开」。\n\n`;
  }
  return { ...a, body, readTime: 2 + (a.id % 8) };
}

// ── DOM 引用 ────────────────────────────────────────────────
const listView = document.getElementById('list-view');
const detailView = document.getElementById('detail-view');
const cardsEl = document.getElementById('cards');
const hero = document.getElementById('detail-hero');
const badge = document.getElementById('prefetch-badge');
const skeleton = document.getElementById('detail-skeleton');
const titleEl = document.getElementById('detail-title');
const subEl = document.getElementById('detail-sub');
const bodyEl = document.getElementById('detail-body');

// ── 列表渲染（stagger 进入 + 预取绑定） ─────────────────────
function renderList() {
  cardsEl.innerHTML = '';
  for (let i = 0; i < TOTAL; i++) {
    const a = makeArticle(i + 1);

    const card = document.createElement('article');
    card.className = 'card';
    card.dataset.id = a.id;
    card.style.animationDelay = `${((a.id - 1) % 12) * 30}ms`; // stagger 错峰 30ms

    const img = document.createElement('img');
    img.className = 'card-cover';
    img.alt = a.title;
    img.loading = 'lazy';
    img.style.viewTransitionName = `cover-${a.id}`; // ★ 共享元素 key（与详情 hero 同名）
    img.style.background = coverColor(a.id);
    img.src = a.cover;
    img.onload = () => img.classList.add('loaded');

    const body = document.createElement('div');
    body.className = 'card-body';
    const h = document.createElement('h3');
    h.textContent = a.title;
    const p = document.createElement('p');
    p.textContent = a.subtitle;
    body.append(h, p);

    card.append(img, body);
    card.addEventListener('mouseenter', () => prefetchDetail(a.id)); // 桌面：hover 预取
    card.addEventListener('touchstart', () => prefetchDetail(a.id)); // 移动：触摸预取
    card.addEventListener('click', () => openDetail(a.id));
    cardsEl.appendChild(card);
  }
}

// ── 预取（与 Android PrefetchRouter 同职责：意图提前于渲染） ──
function prefetchDetail(id) {
  if (detailPromises.has(id)) return detailPromises.get(id);
  const p = new Promise((resolve) => {
    setTimeout(() => {
      prefetchedDone.add(id);
      resolve(makeDetail(makeArticle(id)));
    }, 250 + Math.random() * 350); // 模拟网络
  });
  detailPromises.set(id, p);
  return p;
}

// ── 详情视图 ────────────────────────────────────────────────
function showDetailShell(id) {
  currentId = id;
  listView.hidden = true;
  detailView.hidden = false;

  hero.hidden = false;
  hero.style.viewTransitionName = `cover-${id}`; // ★ 与列表卡片同名 → 自动位移缩放
  hero.classList.remove('loaded');
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
  const wasPrefetched = prefetchedDone.has(a.id); // 预取是否在点击前已完成

  skeleton.className = 'skeleton-block';
  skeleton.innerHTML = '';
  badge.hidden = !wasPrefetched; // 只有「点击前就预取完成」才显示徽章

  titleEl.textContent = a.title;
  subEl.textContent = `${a.subtitle} · 阅读约 ${a.readTime} 分钟`;
  bodyEl.textContent = a.body;

  hero.style.background = coverColor(a.id);
  hero.src = a.cover;
  hero.onload = () => hero.classList.add('loaded'); // 渐进式淡入
  if (hero.complete) hero.classList.add('loaded');
}

// ── 页面切换（View Transitions） ────────────────────────────
function openDetail(id) {
  if (!document.startViewTransition) { showDetailShell(id); finishOpen(id); return; }
  document.documentElement.classList.remove('backward');
  document.startViewTransition(() => showDetailShell(id));
  finishOpen(id);
}

async function finishOpen(id) {
  const detail = await prefetchDetail(id);
  if (currentId !== id) return; // 竞态保护：用户已返回
  renderDetail(detail);
}

function closeDetail() {
  if (!document.startViewTransition) { detailView.hidden = true; listView.hidden = false; return; }
  document.documentElement.classList.add('backward'); // 镜像方向：旧页右出、新页左入
  const t = document.startViewTransition(() => {
    detailView.hidden = true;
    listView.hidden = false;
    currentId = null;
  });
  t.finished.catch(() => {}).finally(() => document.documentElement.classList.remove('backward'));
}

document.getElementById('back-btn').addEventListener('click', closeDetail);

// ── 启动 ────────────────────────────────────────────────────
renderList();
