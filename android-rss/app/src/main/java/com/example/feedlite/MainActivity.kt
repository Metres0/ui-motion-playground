package com.example.feedlite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.feedlite.data.RssRepository
import com.example.feedlite.data.SubscriptionStore
import com.example.feedlite.data.TranslationStore
import com.example.feedlite.data.Translator
import com.example.feedlite.ui.AppNav
import com.example.feedlite.ui.theme.FeedLiteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val store = SubscriptionStore(applicationContext)
        val repository = RssRepository(applicationContext)
        val translationStore = TranslationStore(applicationContext)
        val translator = Translator(translationStore)

        setContent {
            FeedLiteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNav(
                        store = store,
                        repository = repository,
                        translator = translator,
                        translationStore = translationStore,
                    )
                }
            }
        }
    }
}
