package com.example.feedlite.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.feedlite.data.TranslationStore

/**
 * 设置页：翻译服务配置。
 * - 服务商模板：DeepSeek / MiMo / 自定义（OpenAI 兼容）
 * - API Key 仅存本机（SharedPreferences）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    store: TranslationStore,
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(store) } }
    )
    val config by viewModel.config.collectAsState()
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
            Text("翻译服务", style = MaterialTheme.typography.titleMedium)
            Text(
                "在文章详情页可将正文翻译为目标语言。密钥仅保存在本机。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            // 服务商模板
            Text("服务商", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.example.feedlite.data.TranslationStore.PROVIDERS.forEach { p ->
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

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    error = viewModel.save()
                    if (error == null) saved = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存配置") }

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
                "安全提示：当前版本将 API Key 明文存于本机，仅供个人使用；" +
                    "正式发布请改用 Android Keystore 加密存储。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
