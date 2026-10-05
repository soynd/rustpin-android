package com.rustpin.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rustpin.android.model.Pin
import com.rustpin.android.ui.AppViewModel

/**
 * Detail as one web page: everything (hero, actions, similar grid) scrolls
 * together in a single column. Tap the wallpaper for an immersive viewer;
 * tap again (or back) to close it.
 */
@Composable
fun DetailScreen(vm: AppViewModel, pin: Pin, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scroll = rememberScrollState()
    BackHandler(enabled = vm.fullscreen) { vm.toggleFullscreen() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().verticalScroll(scroll)) {
        Box(Modifier.fillMaxWidth()) {
            val tapSrc = remember { MutableInteractionSource() }
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(pin.preview.ifEmpty { pin.orig }).crossfade(true).allowHardware(true).build(),
                contentDescription = pin.title,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(20.dp))
                    .clickable(interactionSource = tapSrc, indication = null, onClick = vm::toggleFullscreen),
                contentScale = ContentScale.FillWidth,
            )
            IconButton(onClick = onBack, modifier = Modifier.padding(20.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SaveButton(vm, pin, modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = {
                    val u = pin.pageUrl.ifEmpty { pin.orig }
                    if (u.isNotEmpty()) ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
                },
                modifier = Modifier.height(48.dp),
            ) { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Open") }
        }
        if (vm.activeStage == "error") {
            Spacer(Modifier.height(8.dp))
            Text(vm.activeError.ifEmpty { "Save failed." }, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("More like this", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(6.dp))
        when {
            vm.similarLoading && vm.similar.isEmpty() ->
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            vm.similarError.isNotEmpty() && vm.similar.isEmpty() ->
                Text(vm.similarError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            vm.similar.isEmpty() -> Spacer(Modifier.height(1.dp))
            else -> Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // chunk into 2-col rows inside the page scroll (no nested scrolling)
                val rows = vm.similar.chunked(2)
                for (row in rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (s in row) {
                            Box(Modifier.weight(1f)) {
                                PinTile(url = s.thumb.ifEmpty { s.preview }, desc = s.title, onOpen = { vm.openSimilarAsMain(s) })
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (vm.similarLoading) Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    // immersive viewer: overlay (not Dialog) so the activity BackHandler owns back/gesture.
    // Same back-arrow icon as the detail screen. Tap anywhere to close.
    if (vm.fullscreen) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = vm::toggleFullscreen),
            contentAlignment = Alignment.Center) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(pin.orig.ifEmpty { pin.preview }).crossfade(true).allowHardware(false).build(),
                contentDescription = pin.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            IconButton(onClick = vm::toggleFullscreen, modifier = Modifier.align(Alignment.TopStart).padding(20.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        }
    }
}

/** Save button with the download shown on the button itself as a progress bar. */
@Composable
private fun SaveButton(vm: AppViewModel, pin: Pin, modifier: Modifier = Modifier) {
    val busy = vm.activeJobId != null && vm.activeStage != "done" && vm.activeStage != "error" && vm.activeStage.isNotEmpty()
    val done = vm.activeJobId != null && vm.activeStage == "done"
    Box(modifier.height(48.dp).clip(RoundedCornerShape(24.dp))) {
        Button(
            onClick = { if (!busy) vm.download(pin) },
            modifier = Modifier.fillMaxSize(),
            enabled = !busy,
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            when {
                done -> { Icon(Icons.Default.Check, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Saved") }
                busy -> {
                    val pct = (vm.activeProgress * 100).toInt().coerceIn(0, 100)
                    if (vm.activeProgress > 0.01f) Text(pct.toString() + "%")
                    else { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(6.dp)); Text(vm.activeStage + "...") }
                }
                else -> { Icon(Icons.Default.Download, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Save") }
            }
        }
        // progress fill drawn over the button while downloading
        if (busy && vm.activeProgress > 0.01f) {
            LinearProgressIndicator(
                progress = { vm.activeProgress.coerceIn(0f, 1f) },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
            )
        }
    }
}
