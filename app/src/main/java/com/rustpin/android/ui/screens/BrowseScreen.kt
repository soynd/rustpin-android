package com.rustpin.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rustpin.android.ui.AppViewModel

/** Browse: rounded search pill + modern settings gear, title-free masonry feed below. */
@Composable
fun BrowseScreen(vm: AppViewModel, onOpenSettings: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(vm.searchFocusTick) {
        if (vm.searchFocusTick > 0) focusRequester.requestFocus()
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = vm.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                placeholder = { Text("Search...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (vm.query.isNotEmpty()) {
                        IconButton(onClick = vm::clearQuery) { Icon(Icons.Default.Close, contentDescription = "Clear") }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.searchNow() }),
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = onOpenSettings, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(24.dp))
            }
        }
        if (vm.error != null) ErrorRow(vm)
        FeedGrid(vm)
    }
}

@Composable
private fun ErrorRow(vm: AppViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(vm.error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = vm::searchNow) { Text("Retry") }
    }
}

@Composable
private fun FeedGrid(vm: AppViewModel) {
    if (vm.pins.isEmpty()) {
        if (vm.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else Spacer(Modifier.fillMaxSize())
        return
    }
    val grid = rememberLazyStaggeredGridState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= vm.pins.size - 6
        }
    }
    LaunchedEffect(shouldLoadMore, vm.pins.size, vm.bookmarks.size) {
        if (shouldLoadMore && vm.pins.isNotEmpty()) vm.loadMore()
    }
    LazyVerticalStaggeredGrid(
        state = grid,
        columns = StaggeredGridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
    ) {
        items(vm.pins.size, key = { vm.pins[it].id.ifEmpty { "idx-$it" } }) { i ->
            val p = vm.pins[i]
            PinTile(url = if (vm.settings.hdPreview) p.preview else p.thumb, desc = p.title, onOpen = { vm.open(p) })
        }
        if (vm.loading) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}

/** Title-free tile: just the picture, rounded corners. */
@Composable
fun PinTile(url: String, desc: String, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(ctx).data(url).crossfade(true).allowHardware(true).build(),
        contentDescription = desc,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen),
        contentScale = ContentScale.FillWidth,
    )
}
