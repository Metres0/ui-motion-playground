package com.example.feedlite.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.feedlite.R
import com.example.feedlite.data.CacheManager
import com.example.feedlite.data.Opml
import com.example.feedlite.data.ReadingSettings
import com.example.feedlite.data.ReadingStateStore
import com.example.feedlite.data.SubscriptionStore
import com.example.feedlite.data.ThemeSettings
import com.example.feedlite.data.TranslationStore
import com.example.feedlite.data.Translator
import com.example.feedlite.data.UpdateSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    subscriptionStore: SubscriptionStore,
    readingState: ReadingStateStore,
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
    var importMsg by remember { mutableStateOf<String?>(null) }

    var showAbout by remember { mutableStateOf(false) }

    // ★ v1.34：新文章通知权限（Android 13+ 运行时申请；授权后开启开关）
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setNotifyEnabled(true)
        } else {
            viewModel.setNotifyEnabled(false)
            android.widget.Toast.makeText(context, context.getString(R.string.settings_perm_denied), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // ★ OPML 导入/导出 launcher（须在 UI 引用前声明）
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/xml")
    ) { uri ->
        if (uri != null) {
            try {
                val sources = subscriptionStore.allSources()
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(Opml.export(sources).toByteArray(Charsets.UTF_8))
                }
                android.widget.Toast.makeText(context, context.getString(R.string.settings_exported, sources.size), android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, context.getString(R.string.settings_export_failed, e.message), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                val list = Opml.parse(text)
                var added = 0
                var invalid = 0
                list.forEach { (title, url) ->
                    if (subscriptionStore.allSources().none { it.url == url }) {
                        if (subscriptionStore.addCustom(title, url) == null) added++ else invalid++
                    }
                }
                importMsg = context.getString(R.string.settings_imported, added) +
                    if (invalid > 0) context.getString(R.string.settings_imported_invalid, invalid) else ""
            } catch (e: Exception) {
                importMsg = context.getString(R.string.settings_import_failed, e.message)
            }
        }
    }

    // ★ v1.25：去掉「设置」大标题；v1.29 安全区覆盖挖孔/圆角
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.displayCutout)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(Modifier.padding(16.dp)) {

            // ★ v1.30：订阅源管理已独立为底部 Tab「源」，设置页不再保留入口
            Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = themeMode == ThemeSettings.MODE_SYSTEM,
                    onClick = { themeSettings.setMode(ThemeSettings.MODE_SYSTEM) },
                    label = { Text(stringResource(R.string.settings_theme_system)) },
                )
                FilterChip(
                    selected = themeMode == ThemeSettings.MODE_LIGHT,
                    onClick = { themeSettings.setMode(ThemeSettings.MODE_LIGHT) },
                    label = { Text(stringResource(R.string.settings_theme_light)) },
                )
                FilterChip(
                    selected = themeMode == ThemeSettings.MODE_DARK,
                    onClick = { themeSettings.setMode(ThemeSettings.MODE_DARK) },
                    label = { Text(stringResource(R.string.settings_theme_dark)) },
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ════════ 翻译服务 ════════
            Text(stringResource(R.string.settings_translation_service), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_translation_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            Text(stringResource(R.string.settings_provider), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
                label = { Text(stringResource(R.string.settings_model)) },
                placeholder = { Text("deepseek-chat") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = config.apiKey,
                onValueChange = { v -> viewModel.update { it.copy(apiKey = v) } },
                label = { Text(stringResource(R.string.settings_api_key)) },
                supportingText = { Text(stringResource(R.string.settings_api_key_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = config.targetLang,
                onValueChange = { v -> viewModel.update { it.copy(targetLang = v) } },
                label = { Text(stringResource(R.string.settings_target_lang)) },
                placeholder = { Text("中文") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ════════ 阅读设置 ════════
            Text(stringResource(R.string.settings_reading), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_reading_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            Text(stringResource(R.string.settings_font_size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Slider(
                value = reading.fontSizeScale,
                onValueChange = { v -> viewModel.updateReading { it.copy(fontSizeScale = v) } },
                valueRange = 0.85f..1.4f,
                steps = 10,
            )
            Text(
                stringResource(R.string.settings_font_size_value, (reading.fontSizeScale * 100).roundToInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Text(stringResource(R.string.settings_line_height), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Slider(
                value = reading.lineHeightScale,
                onValueChange = { v -> viewModel.updateReading { it.copy(lineHeightScale = v) } },
                valueRange = 1.2f..2.0f,
                steps = 8,
            )
            Text(
                stringResource(R.string.settings_line_height_value, (reading.lineHeightScale * 100).roundToInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Text(stringResource(R.string.settings_font), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = reading.fontFamily == ReadingSettings.FONT_SANS,
                    onClick = { viewModel.updateReading { it.copy(fontFamily = ReadingSettings.FONT_SANS) } },
                    label = { Text(stringResource(R.string.settings_font_sans)) },
                )
                FilterChip(
                    selected = reading.fontFamily == ReadingSettings.FONT_SERIF,
                    onClick = { viewModel.updateReading { it.copy(fontFamily = ReadingSettings.FONT_SERIF) } },
                    label = { Text(stringResource(R.string.settings_font_serif)) },
                )
                FilterChip(
                    selected = reading.fontFamily == ReadingSettings.FONT_MONO,
                    onClick = { viewModel.updateReading { it.copy(fontFamily = ReadingSettings.FONT_MONO) } },
                    label = { Text(stringResource(R.string.settings_font_mono)) },
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ════════ 更新策略 ════════
            Text(stringResource(R.string.settings_update_strategy), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_update_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            Text(stringResource(R.string.settings_update_interval), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UpdateSettings.OPTIONS.forEach { h ->
                    val label = when (h) {
                        0 -> context.getString(R.string.settings_update_manual)
                        else -> context.getString(R.string.settings_update_hours, h)
                    }
                    FilterChip(
                        selected = updateCfg.intervalHours == h,
                        onClick = { viewModel.setInterval(h) },
                        label = { Text(label) },
                    )
                }
            }

            // ★ v1.34：新文章通知开关
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_notify_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.settings_notify_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = updateCfg.notifyEnabled,
                    onCheckedChange = { checked ->
                        // Android 13+ 首次开启需申请通知权限；被拒则保持关闭
                        if (checked && Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setNotifyEnabled(checked)
                        }
                    },
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        error = viewModel.saveAll()
                        if (error == null) {
                            saved = true
                            // ★ v1.34：设置落盘后才按新间隔（重新）调度后台同步（0=手动则取消）
                            com.example.feedlite.data.SyncScheduler.schedule(context)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.settings_save_all)) }
                // ★ 测试连接：用当前配置发翻译请求验证
                OutlinedButton(
                    onClick = { viewModel.testConnection({}) },
                    enabled = testState !is SettingsViewModel.TestState.Testing,
                    modifier = Modifier.weight(1f),
                ) {
                    if (testState is SettingsViewModel.TestState.Testing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_testing))
                    } else {
                        Text(stringResource(R.string.settings_test_connection))
                    }
                }
            }

            when (val t = testState) {
                is SettingsViewModel.TestState.Success -> {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.settings_test_ok, t.reply.take(40)), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                is SettingsViewModel.TestState.Fail -> {
                    Spacer(Modifier.height(8.dp))
                    Text("✗ ${t.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                else -> Unit
            }

            if (saved && error == null) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_saved), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text("✗ $error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ════════ 数据管理 ════════
            Text(stringResource(R.string.settings_data), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 导出 OPML
                OutlinedButton(
                    onClick = { exportLauncher.launch("feedlite_subscriptions.opml") },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.settings_export_opml))
                }
                // 导入 OPML
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("text/xml", "text/plain", "*/*")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.settings_import_opml))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_opml_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { readingState.clearRead() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.settings_clear_read))
            }
            importMsg?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            // ★ v1.25：缓存管理（v1.32：统计在 IO 线程算，不卡主线程）
            Spacer(Modifier.height(16.dp))
            val cacheManager = remember { CacheManager(context) }
            var cacheSize by remember { mutableStateOf("…") }
            val cacheScope = rememberCoroutineScope()
            LaunchedEffect(cacheManager) {
                cacheSize = withContext(Dispatchers.IO) { cacheManager.sizeText() }
            }
            OutlinedButton(
                onClick = {
                    cacheManager.clear() // 内部会重建目录，缓存写入不失效
                    android.widget.Toast.makeText(context, context.getString(R.string.settings_cache_cleared), android.widget.Toast.LENGTH_SHORT).show()
                    cacheScope.launch {
                        cacheSize = withContext(Dispatchers.IO) { cacheManager.sizeText() }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.settings_clear_cache, cacheSize))
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { showAbout = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.settings_about))
            }

            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.settings_security_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }

    // ★ v1.24：订阅源对话框已迁入「订阅源管理」二级页；此处仅保留「关于」
    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

/** 关于对话框。 */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Image(
                painter = androidx.compose.ui.res.painterResource(com.example.feedlite.R.drawable.ic_brand_logo),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
        },
        title = { Text(stringResource(R.string.about_title, com.example.feedlite.BuildConfig.VERSION_NAME)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.about_subtitle))
                Text(stringResource(R.string.about_sources), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.about_features), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(stringResource(R.string.about_ok)) }
        },
    )
}
