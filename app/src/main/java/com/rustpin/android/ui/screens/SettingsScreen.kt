package com.rustpin.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rustpin.android.store.Accent
import com.rustpin.android.ui.AppViewModel

/** Settings: accent colour + credits. Dark mode is fixed. */
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Accent colour", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(Accent.values()) { a ->
                    val selected = vm.settings.accent == a
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(a.color)
                            .clickable { vm.setAccent(a.key) }
                            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape) else Modifier),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Made by kiwi", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedButton(onClick = {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/soynd/rustpin")))
            }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("GitHub") }
        }
    }
}
