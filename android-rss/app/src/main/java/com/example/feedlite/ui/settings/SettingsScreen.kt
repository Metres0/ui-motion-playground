package com.example.feedlite.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.feedlite.data.ReadingSettings
import com.example.feedlite.data.ThemeSettings
import com.example.feedlite.data.TranslationStore
import com.example.feedlite.data.Translator
import com.example.feedlite.data.UpdateSettings
import kotlin.math.roundToInt

/**
 * 设置页：
 * - 翻译服务（服务商/URL/Key/模型/目标语言）
 * - 阅读设置（字号 / 行高 / 字体）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    store: TranslationStore,
    translator: Translator,
    updateSettings: UpdateSettings,
    themeSettings: ThemeSettings,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(store, ReadingSettings(context), translator, updateSettings) }
        }
    )
    val config by viewModel.config.collectAsState()
    val reading by viewModel.readingConfig.collectAsState()
    val updateCfg by viewModel.updateConfig.collectAsState()
    val testState by viewModel.testState.collectAsState()
    val themeMode by themeSettings.mode.collectAsState()
    var saved by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {

            // ════════ 外观 ════════
            Text("外观", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Text("主题", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = themeMode == ThemeSettings.MODE_SYSTEM,
                    onClick = { themeSettings.setMode(ThemeSettings.MODE_SYSTEM) },
                    label = { Text("跟随系统") },
                )
                FilterChip(
                    selected = themeMode == ThemeSettings.MODE_LIGHT,
                    onClick = { themeSettings.setMode(ThemeSettings.MODE_LIGHT) },
                    label = { Text("浅色") },
                )
                FilterChip(
                    selected = themeMode == ThemeSettings.MODE_DARK,
                    onClick = { themeSettings.setMode(ThemeSettings.MODE_DARK) },
                    label = { Text("深色") },
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ════════ 翻译服务 ════════
            Text("翻译服务", style = MaterialTheme.typography.titleMedium)
            Text(
                "在文章详情页可将正文翻译为目标语言。密钥仅保存在本机。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            Text("服务商", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TranslationStore.PROVIDERS.forEach { p ->
                    FilterChip(
                        selected = config.provider == p,
                        onClick = { viewModel.applyTemplate(p) },
                        label = { Text(p) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = config.baseUrl,
                onValueChange = { v -> viewModel.update { it.copy(baseUrl = v) } },
                label = { Text("Base URL") },
                placeholder = { Text("https://api.deepseek.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = config.model,
                onValueChange = { v -> viewModel.update { it.copy(model = v) } },
                label = { Text("模型") },
                placeholder = { Text("deepseek-chat") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = config.apiKey,
                onValueChange = { v -> viewModel.update { it.copy(apiKey = v) } },
                label = { Text("API Key") },
                supportingText = { Text("不会上传，仅用于翻译请求") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = config.targetLang,
                onValueChange = { v -> viewModel.update { it.copy(targetLang = v) } },
                label = { Text("目标语言") },
                placeholder = { Text("中文") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ════════ 阅读设置 ════════
            Text("阅读设置", style = MaterialTheme.typography.titleMedium)
            Text(
                "应用于文章详情页的正文排版。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            Text("字号", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Slider(
                value = reading.fontSizeScale,
                onValueChange = { v -> viewModel.updateReading { it.copy(fontSizeScale = v) } },
                valueRange = 0.85f..1.4f,
                steps = 10,
            )
            Text(
                "当前 ${(reading.fontSizeScale * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Text("行高", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Slider(
                value = reading.lineHeightScale,
                onValueChange = { v -> viewModel.updateReading { it.copy(lineHeightScale = v) } },
                valueRange = 1.2f..2.0f,
                steps = 8,
            )
            Text(
                "当前 ${(reading.lineHeightScale * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Text("字体", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = reading.fontFamily == ReadingSettings.FONT_SANS,
                    onClick = { viewModel.updateReading { it.copy(fontFamily = ReadingSettings.FONT_SANS) } },
                    label = { Text("无衬线") },
                )
                FilterChip(
                    selected = reading.fontFamily == ReadingSettings.FONT_SERIF,
                    onClick = { viewModel.updateReading { it.copy(fontFamily = ReadingSettings.FONT_SERIF) } },
                    label = { Text("衬线") },
                )
                FilterChip(
                    selected = reading.fontFamily == ReadingSettings.FONT_MONO,
                    onClick = { viewModel.updateReading { it.copy(fontFamily = ReadingSettings.FONT_MONO) } },
                    label = { Text("等宽") },
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ════════ 更新策略 ════════
            Text("更新策略", style = MaterialTheme.typography.titleMedium)
            Text(
                "进入应用直接读缓存秒开，超过间隔才自动增量抓取新文章；手动刷新可随时强制更新。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            Text("自动更新间隔", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UpdateSettings.OPTIONS.forEach { h ->
                    val label = when (h) {
                        0 -> "手动"
                        else -> "${h}h"
                    }
                    FilterChip(
                        selected = updateCfg.intervalHours == h,
                        onClick = { viewModel.setInterval(h) },
                        label = { Text(label) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        error = viewModel.saveAll()
                        if (error == null) saved = true
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("保存全部设置") }
                // ★ 测试连接：用当前配置发翻译请求验证
                OutlinedButton(
                    onClick = { viewModel.testConnection({}) },
                    enabled = testState !is SettingsViewModel.TestState.Testing,
                    modifier = Modifier.weight(1f),
                ) {
                    if (testState is SettingsViewModel.TestState.Testing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("测试中…")
                    } else {
                        Text("测试连接")
                    }
                }
            }

            when (val t = testState) {
                is SettingsViewModel.TestState.Success -> {
                    Spacer(Modifier.height(8.dp))
                    Text("✓ 连接成功，返回：${t.reply.take(40)}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                is SettingsViewModel.TestState.Fail -> {
                    Spacer(Modifier.height(8.dp))
                    Text("✗ ${t.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                else -> Unit
            }

            if (saved && error == null) {
                Spacer(Modifier.height(8.dp))
                Text("✓ 已保存", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text("✗ $error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "安全提示：API Key 明文存于本机，仅供个人使用；正式发布请改用 Keystore 加密存储。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
