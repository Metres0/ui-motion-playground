package com.example.feedlite.ui.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.feedlite.data.FeedCategory
import com.example.feedlite.data.FeedSource
import com.example.feedlite.data.SubscriptionStore

/**
 * 订阅源管理（v1.24）——设置页的二级子页。
 * 搜索 / 分类分组 / 每源开关·进入·删除 / 添加 / 公众号微博转源帮助。
 */
@Composable
fun SourceManageScreen(
    store: SubscriptionStore,
    onOpenSource: (FeedSource) -> Unit,
    onSubscriptionChanged: () -> Unit,
    onBack: () -> Unit,
) {
    var sources by remember { mutableStateOf(store.allSources()) }
    var enabled by remember { mutableStateOf(store.enabledIds()) }
    var search by remember { mutableStateOf("") }
    var showAddSource by remember { mutableStateOf(false) }
    var showConvertHelp by remember { mutableStateOf(false) }
    fun refresh() {
        sources = store.allSources()
        enabled = store.enabledIds()
    }

    Column(Modifier.fillMaxSize()) {
        // 浮动返回行 + 小标题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(24.dp))
            }
            Text("订阅源管理", style = MaterialTheme.typography.titleMedium)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "点击源进入文章；开关控制是否订阅",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            // 搜索
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("搜索源 / 分类…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))

            // 分类分组
            val kw = search.trim()
            FeedCategory.ORDER.forEach { cat ->
                val list = sources.filter { it.category == cat }.filter {
                    kw.isEmpty() || it.title.contains(kw, true) || it.description.contains(kw, true) || cat.contains(kw, true)
                }
                if (list.isNotEmpty()) {
                    Text(
                        cat,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                    list.forEach { source ->
                        SourceRow(
                            source = source,
                            checked = source.id in enabled,
                            onToggle = { on ->
                                store.setEnabled(source.id, on)
                                refresh(); onSubscriptionChanged()
                            },
                            onClick = { onOpenSource(source) },
                            onDelete = {
                                store.removeCustom(source.id)
                                refresh(); onSubscriptionChanged()
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showAddSource = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("添加订阅源")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showConvertHelp = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("公众号 / 微博转源帮助")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAddSource) {
        AddSourceDialog(
            onConfirm = { title, url ->
                store.addCustom(title, url)
                refresh(); onSubscriptionChanged()
            },
            onDismiss = { showAddSource = false },
        )
    }
    if (showConvertHelp) {
        ConvertHelpDialog(onDismiss = { showConvertHelp = false })
    }
}

/** 订阅源行：点击进入、Switch 管理启用、自定义源可删除。 */
@Composable
private fun SourceRow(
    source: FeedSource,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = source.initial,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(source.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (source.id.startsWith("custom_")) {
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除 ${source.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

/** 添加订阅源对话框。 */
@Composable
private fun AddSourceDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.RssFeed, contentDescription = null) },
        title = { Text("添加 RSS 源") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("名称（如：我的博客）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Feed 地址（如：example.com/feed）") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && url.isNotBlank(),
                onClick = {
                    onConfirm(title.trim(), url.trim())
                    onDismiss()
                },
            ) { Text("添加") }
        },
        dismissButton = {
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "取消") }
        },
    )
}

/** 转源帮助对话框：公众号 / 微博 → RSS 的路径。 */
@Composable
private fun ConvertHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.HelpOutline, contentDescription = null) },
        title = { Text("公众号 / 微博 转 RSS") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("微信公众号", style = MaterialTheme.typography.titleSmall)
                Text(
                    "1. 自建 Wechat2RSS（wechat2rss.xlab.app，需一台服务器）；\n" +
                        "2. 或使用 RSSHub 的 /wechat/ 相关路由；\n" +
                        "3. 得到 feed 地址后，通过「添加订阅源」填入即可。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                Text("微博", style = MaterialTheme.typography.titleSmall)
                Text(
                    "1. 使用 RSSHub 路由 /weibo/user/{uid}；\n" +
                        "2. uid 为微博用户数字 ID（可在个人主页 URL 中查看）；\n" +
                        "3. 公共实例可能限流，建议自建 RSSHub（github.com/DIYgod/RSSHub）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("知道了") }
        },
    )
}
