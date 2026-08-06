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
import com.example.uiplayground.ui.AppNav
import com.example.uiplayground.ui.theme.UIPlaygroundTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 数据层由 Application 级容器持有（与进程同寿），配置变更不丢预取缓存
        val container = (applicationContext as PlaygroundApplication).container

        setContent {
            UIPlaygroundTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNav(
                        repository = container.repository,
                        prefetchRouter = container.prefetchRouter,
                    )
                }
            }
        }
    }
}
