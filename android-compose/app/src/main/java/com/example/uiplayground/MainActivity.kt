package com.example.uiplayground

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.uiplayground.data.ArticleApi
import com.example.uiplayground.data.ArticleRepository
import com.example.uiplayground.data.PrefetchRouter
import com.example.uiplayground.ui.AppNav
import com.example.uiplayground.ui.theme.UIPlaygroundTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 组装数据层（真实项目中用 DI 容器）
        val repository = ArticleRepository(ArticleApi())

        setContent {
            UIPlaygroundTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNav(
                        repository = repository,
                        prefetchRouter = PrefetchRouter(repository),
                    )
                }
            }
        }
    }
}
