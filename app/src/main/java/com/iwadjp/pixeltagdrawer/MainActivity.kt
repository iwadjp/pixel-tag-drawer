package com.iwadjp.pixeltagdrawer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PixelTagDrawerApp()
        }
    }
}

@Composable
fun PixelTagDrawerApp() {
    val context = LocalContext.current
    val colorScheme = dynamicLightColorScheme(context)

    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Pixel Tag Drawer",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Pixel Launcherを置き換えない、タグ付き補助ランチャーです。",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "v0.1: 起動可能アプリ一覧、タグ付け、未分類、Pinned Shortcutを検証します。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
