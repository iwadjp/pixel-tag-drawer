package com.iwadjp.pixeltagdrawer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iwadjp.pixeltagdrawer.model.LauncherApp
import com.iwadjp.pixeltagdrawer.ui.AppListViewModel

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
            AppListScreen()
        }
    }
}

@Composable
fun AppListScreen(viewModel: AppListViewModel = viewModel()) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Pixel Tag Drawer",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Pixel Launcherを置き換えない、タグ付き補助ランチャーです。" +
                        "以下は起動可能アプリ一覧。タップで起動します。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        message?.let { msg ->
            item {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        item {
            Text(
                text = "${apps.size} 件",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }

        items(
            items = apps,
            key = { "${it.packageName}/${it.className}" },
        ) { app ->
            AppRow(app = app, onClick = { viewModel.launch(app) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun AppRow(app: LauncherApp, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppIcon(app)
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppIcon(app: LauncherApp) {
    val icon = app.icon
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
    } else {
        // アイコン取得失敗時のプレースホルダー: ラベル先頭1文字
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = app.label.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
