package com.example.feedlite.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.feedlite.data.ReadingSettings
import com.example.feedlite.data.RssItem
import com.example.feedlite.data.Translator
import com.example.feedlite.data.TranslationStore
import com.example.feedlite.ui.ArticleCache
import com.example.feedlite.ui.components.ProgressiveImage
import com.example.feedlite.ui.reader.HtmlBlocks
import kotlin.math.roundToInt

/**
 * 文章详情页（v1.3）：
 * - 正文排版渲染：标题/段落独立/列表/引用/**正文图片真实显示**/代码块(深色+复制)；
 * - **文章内阅读设置面板**：TopAppBar「A」按钮 → BottomSheet 调字号/行高/字体，即时生效；
 * - 翻译：代码块不参与翻译。
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.ArticleDetailScreen(
    itemKey: String,
    translator: Translator,
    store: TranslationStore,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
) {
    val item = remember(itemKey) { ArticleCache.get(itemKey) }
    val context = LocalContext.current

    // ★ 阅读设置：状态化 + 面板即时更新 + 持久化
    var reading by remember { mutableStateOf(ReadingSettings(context).load()) }
    val readingStore = remember { ReadingSettings(context) }
    var showReadingPanel by remember { mutableStateOf(false) }

    val viewModel: ArticleDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ArticleDetailViewModel(itemKey, translator, store) }
        }
    )
    val translation by viewModel.translation.collectAsState()

    val bodyFont = when (reading.fontFamily) {
        ReadingSettings.FONT_SERIF -> FontFamily.Serif
        ReadingSettings.FONT_MONO -> FontFamily.Monospace
        else -> FontFamily.SansSerif
    }
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = bodyFont,
        fontSize = (16 * reading.fontSizeScale).sp,
        lineHeight = (26 * reading.lineHeightScale).sp,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // ★ 阅读设置（文章内）
                    IconButton(onClick = { showReadingPanel = true }) {
                        Icon(Icons.Default.FormatSize, contentDescription = "阅读设置")
                    }
                    IconButton(onClick = { viewModel.translate() }) {
                        Icon(Icons.Default.Translate, contentDescription = "翻译全文")
                    }
                    if (item != null && item.link.isNotBlank()) {
                        IconButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link)))
                        }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "在浏览器打开")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (item == null) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("文章不存在，请返回重试", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

        val blocks = remember(item.descriptionHtml) { HtmlBlocks.parse(item.descriptionHtml) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ProgressiveImage(
                url = item.imageUrl,
                seed = item.key.hashCode(),
                contentDescription = item.title,
                decodeWidth = 1280,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 2f)
                    .sharedElement(
                        state = rememberSharedContentState(key = "thumb_${item.key}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                    ),
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(16.dp))
                Text(item.title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = listOf(item.author, item.pubDate).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(16.dp))

                // ── 排版正文（含正文图片） ─────────────────
                if (blocks.isEmpty()) {
                    Text(
                        "（该源未提供正文摘要）",
                        style = bodyStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val primary = MaterialTheme.colorScheme.primary
                    SelectionContainer {
                        Column {
                            blocks.forEach { block ->
                                BlockView(block, bodyStyle, primary)
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                }

                // ── 翻译区块 ─────────────────────────────
                TranslationSection(
                    state = translation,
                    onRetry = { viewModel.translate() },
                )

                Spacer(Modifier.height(24.dp))
                if (item.link.isNotBlank()) {
                    Text(
                        text = "原文链接：${item.link}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // ── 阅读设置面板 ───────────────────────────────
    if (showReadingPanel) {
        ReadingSettingsPanel(
            reading = reading,
            onUpdate = { new ->
                reading = new
                readingStore.save(new) // 即时持久化
            },
            onDismiss = { showReadingPanel = false },
        )
    }
}

/** 单个排版块的渲染。 */
@Composable
private fun BlockView(
    block: HtmlBlocks.Block,
    bodyStyle: androidx.compose.ui.text.TextStyle,
    primaryColor: Color,
) {
    when (block) {
        is HtmlBlocks.Block.Paragraph -> Text(
            spansToAnnotated(block.spans, bodyStyle, primaryColor),
            style = bodyStyle,
        )

        is HtmlBlocks.Block.Heading -> {
            val size = when (block.level) {
                1 -> 24f; 2 -> 22f; 3 -> 20f; 4 -> 18f; else -> 16f
            }
            Text(
                spansToAnnotated(block.spans, bodyStyle, primaryColor),
                style = bodyStyle.copy(
                    fontSize = (size * (bodyStyle.fontSize.value / 16f)).sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }

        is HtmlBlocks.Block.UnorderedList -> Column {
            block.items.forEach { spans ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text("•  ", style = bodyStyle, color = primaryColor)
                    Text(spansToAnnotated(spans, bodyStyle, primaryColor), style = bodyStyle, modifier = Modifier.weight(1f))
                }
            }
        }

        is HtmlBlocks.Block.OrderedList -> Column {
            block.items.forEachIndexed { idx, spans ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text("${idx + 1}.  ", style = bodyStyle, color = primaryColor)
                    Text(spansToAnnotated(spans, bodyStyle, primaryColor), style = bodyStyle, modifier = Modifier.weight(1f))
                }
            }
        }

        is HtmlBlocks.Block.Quote -> Text(
            spansToAnnotated(block.spans, bodyStyle, primaryColor),
            style = bodyStyle.copy(
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(12.dp),
        )

        HtmlBlocks.Block.Divider -> HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        is HtmlBlocks.Block.CodeBlock -> CodeBlockView(block)

        // ★ 正文图片：真实渲染
        is HtmlBlocks.Block.Image -> ProgressiveImage(
            url = block.url,
            seed = block.url.hashCode(),
            contentDescription = block.alt.ifBlank { null },
            decodeWidth = 1280,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 2f)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        )
    }
}

/** 代码块：深色背景 + 等宽字体 + 水平滚动 + 复制。 */
@Composable
private fun CodeBlockView(block: HtmlBlocks.Block.CodeBlock) {
    val clipboard = LocalClipboardManager.current
    val codeColor = Color(0xFF1E1E2E)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(codeColor),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = block.language ?: "code",
                color = Color(0xFF8A8A9A),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 12.dp),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { clipboard.setText(AnnotatedString(block.code)) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "复制代码",
                    tint = Color(0xFF8A8A9A),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            text = block.code,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = Color(0xFFE0E0E8),
            modifier = Modifier
                .fillMaxWidth()
                .background(codeColor)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

/** 行内 Span → AnnotatedString。 */
private fun spansToAnnotated(
    spans: List<HtmlBlocks.Span>,
    base: androidx.compose.ui.text.TextStyle,
    primaryColor: Color,
): AnnotatedString = buildAnnotatedString {
    for (s in spans) {
        val span = SpanStyle(
            fontWeight = if (s.bold) FontWeight.Bold else null,
            fontStyle = if (s.italic) FontStyle.Italic else null,
            fontFamily = if (s.code) FontFamily.Monospace else null,
            color = if (s.link != null) primaryColor else Color.Unspecified,
        )
        withStyle(span) { append(s.text) }
    }
}

/** ★ 阅读设置面板（文章内 AA 按钮弹出）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingSettingsPanel(
    reading: ReadingSettings.ReadingConfig,
    onUpdate: (ReadingSettings.ReadingConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("阅读设置", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))

            Text("字号  ${(reading.fontSizeScale * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Slider(
                value = reading.fontSizeScale,
                onValueChange = { onUpdate(reading.copy(fontSizeScale = it)) },
                valueRange = 0.85f..1.4f,
                steps = 10,
            )
            Spacer(Modifier.height(8.dp))

            Text("行高  ${(reading.lineHeightScale * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Slider(
                value = reading.lineHeightScale,
                onValueChange = { onUpdate(reading.copy(lineHeightScale = it)) },
                valueRange = 1.2f..2.0f,
                steps = 8,
            )
            Spacer(Modifier.height(8.dp))

            Text("字体", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = reading.fontFamily == ReadingSettings.FONT_SANS,
                    onClick = { onUpdate(reading.copy(fontFamily = ReadingSettings.FONT_SANS)) },
                    label = { Text("无衬线") },
                )
                FilterChip(
                    selected = reading.fontFamily == ReadingSettings.FONT_SERIF,
                    onClick = { onUpdate(reading.copy(fontFamily = ReadingSettings.FONT_SERIF)) },
                    label = { Text("衬线") },
                )
                FilterChip(
                    selected = reading.fontFamily == ReadingSettings.FONT_MONO,
                    onClick = { onUpdate(reading.copy(fontFamily = ReadingSettings.FONT_MONO)) },
                    label = { Text("等宽") },
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                "设置即时生效并自动保存，对阅读全文生效",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("完成")
            }
        }
    }
}

/** 译文区块。 */
@Composable
private fun TranslationSection(
    state: TranslationUiState,
    onRetry: () -> Unit,
) {
    when (state) {
        TranslationUiState.Idle -> {
            Spacer(Modifier.height(20.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Icon(
                    Icons.Default.Translate,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "点击右上角「翻译」翻译全文（代码块保留原文）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        TranslationUiState.Translating -> {
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(Modifier.padding(bottom = 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在翻译…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        is TranslationUiState.Done -> {
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(Modifier.padding(bottom = 16.dp))
            Text("译文", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                Text(
                    text = state.text,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 26.sp,
                )
            }
        }

        is TranslationUiState.Error -> {
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(Modifier.padding(bottom = 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }
    }
}
