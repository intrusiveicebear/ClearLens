package com.clearlens.app.ui

import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clearlens.app.ClearLensUiState
import com.clearlens.app.model.FindingGroup
import com.clearlens.app.model.FindingType
import com.clearlens.app.model.PhotoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClearLensApp(
    hasPermission: Boolean,
    state: ClearLensUiState,
    requestPermission: () -> Unit,
    startScan: () -> Unit,
    cancelScan: () -> Unit,
    togglePhoto: (Long) -> Unit,
    selectGroup: (FindingGroup, Boolean) -> Unit,
    protectFolders: (FindingGroup) -> Unit,
    setFilter: (FindingType?) -> Unit,
    moveSelectedToTrash: () -> Unit
) {
    var showTrashConfirmation by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("ClearLens", fontWeight = FontWeight.Bold)
                            Text("Private photo cleanup", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    if (hasPermission && state.hasScanned && !state.isScanning) {
                        TextButton(onClick = startScan) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Rescan")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (hasPermission && state.hasScanned && state.selectedIds.isNotEmpty()) {
                SelectionBar(
                    count = state.selectedPhotos.size,
                    bytes = state.selectedBytes,
                    onTrash = { showTrashConfirmation = true }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !hasPermission -> PermissionScreen(requestPermission)
                state.isScanning -> ScanningScreen(state, cancelScan)
                !state.hasScanned -> WelcomeScreen(startScan)
                else -> ResultsScreen(state, togglePhoto, selectGroup, protectFolders, setFilter, startScan)
            }
        }
    }

    if (showTrashConfirmation) {
        AlertDialog(
            onDismissRequest = { showTrashConfirmation = false },
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
            title = { Text("Move ${state.selectedPhotos.size} photos to trash?") },
            text = {
                Text("This will recover about ${formatBytes(state.selectedBytes)}. Android will show one more confirmation, and the photos remain recoverable from trash for a limited time.")
            },
            confirmButton = {
                Button(onClick = {
                    showTrashConfirmation = false
                    moveSelectedToTrash()
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showTrashConfirmation = false }) { Text("Review again") } }
        )
    }
}

@Composable
private fun PermissionScreen(requestPermission: () -> Unit) {
    CenteredPage {
        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text("Let ClearLens inspect your gallery", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Photo access is needed to find duplicate, similar, blurry, dark, and accidental images. Analysis stays entirely on your device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text("No uploads") }, leadingIcon = { Icon(Icons.Default.Lock, null) })
            AssistChip(onClick = {}, label = { Text("You approve deletion") }, leadingIcon = { Icon(Icons.Default.Shield, null) })
        }
        Spacer(Modifier.height(28.dp))
        Button(onClick = requestPermission, modifier = Modifier.fillMaxWidth()) { Text("Allow photo access") }
    }
}

@Composable
private fun WelcomeScreen(startScan: () -> Unit) {
    CenteredPage {
        Icon(Icons.Default.ImageSearch, contentDescription = null, modifier = Modifier.size(76.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(22.dp))
        Text("Ready to find gallery clutter", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "ClearLens compares photo contents—not only filenames—and suggests what may be safe to remove. Nothing is deleted automatically.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = startScan, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.ImageSearch, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Scan my photos")
        }
    }
}

@Composable
private fun ScanningScreen(state: ClearLensUiState, cancelScan: () -> Unit) {
    CenteredPage {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp))
        Text("Looking for clutter…", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(state.progress.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        LinearProgressIndicator(progress = { state.progress.fraction }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        if (state.progress.total > 0) Text("${state.progress.current} / ${state.progress.total}", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = cancelScan) { Text("Cancel") }
    }
}

@Composable
private fun ResultsScreen(
    state: ClearLensUiState,
    togglePhoto: (Long) -> Unit,
    selectGroup: (FindingGroup, Boolean) -> Unit,
    protectFolders: (FindingGroup) -> Unit,
    setFilter: (FindingType?) -> Unit,
    startScan: () -> Unit
) {
    if (state.findings.isEmpty()) {
        CenteredPage {
            Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(20.dp))
            Text("Your gallery looks tidy", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(state.errorMessage ?: "No likely duplicates or low-quality accidents were found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Button(onClick = startScan) { Text("Scan again") }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SummaryCard(state)
            Spacer(Modifier.height(14.dp))
            FilterRow(state, setFilter)
        }
        items(state.filteredFindings, key = { it.id }) { group ->
            FindingCard(group, state.selectedIds, togglePhoto, selectGroup, protectFolders)
        }
    }
}

@Composable
private fun SummaryCard(state: ClearLensUiState) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${state.findings.size} cleanup suggestions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${state.findings.flatMap { it.photos }.distinctBy { it.id }.size} photos to review", color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun FilterRow(state: ClearLensUiState, setFilter: (FindingType?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { FilterChip(selected = state.filter == null, onClick = { setFilter(null) }, label = { Text("All") }) }
        items(FindingType.entries) { type ->
            val count = state.findings.count { it.type == type }
            if (count > 0) FilterChip(
                selected = state.filter == type,
                onClick = { setFilter(type) },
                label = { Text("${type.label} $count") }
            )
        }
    }
}

@Composable
private fun FindingCard(
    group: FindingGroup,
    selectedIds: Set<Long>,
    togglePhoto: (Long) -> Unit,
    selectGroup: (FindingGroup, Boolean) -> Unit,
    protectFolders: (FindingGroup) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(group.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(group.type.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                val allSelected = group.photos.all { it.id in selectedIds }
                TextButton(onClick = { selectGroup(group, !allSelected) }) { Text(if (allSelected) "Clear" else "Select all") }
            }
            Text(group.explanation, modifier = Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (group.photos.any { it.relativePath.isNotBlank() }) {
                TextButton(
                    onClick = { protectFolders(group) },
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(if (group.photos.map { it.relativePath }.distinct().size > 1) "Protect these folders" else "Protect this folder")
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(group.photos, key = { it.id }) { photo ->
                    PhotoTile(
                        photo = photo,
                        selected = photo.id in selectedIds,
                        isKeeper = group.photos.size > 1 && photo.id == group.photos.first().id,
                        onClick = { togglePhoto(photo.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoTile(photo: PhotoItem, selected: Boolean, isKeeper: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(132.dp).clickable(onClick = onClick)) {
        Box {
            GalleryThumbnail(photo.uri, Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(14.dp)))
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() },
                modifier = Modifier.align(Alignment.TopEnd).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), CircleShape)
            )
            if (isKeeper && !selected) {
                Text(
                    "KEEP",
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(7.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(photo.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
        Text(
            "${formatBytes(photo.sizeBytes)} • ${formatDate(photo.dateTaken)}",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GalleryThumbnail(uri: Uri, modifier: Modifier = Modifier) {
    val resolver = LocalContext.current.contentResolver
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            try { resolver.loadThumbnail(uri, Size(320, 320), null) } catch (_: Exception) { null }
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (bitmap == null) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
        else androidx.compose.foundation.Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun SelectionBar(count: Int, bytes: Long, onTrash: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("$count selected", fontWeight = FontWeight.Bold)
            Text("Recover about ${formatBytes(bytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(
            onClick = onTrash,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.DeleteOutline, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Move to trash")
        }
    }
}

@Composable
private fun CenteredPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatDate(timestamp: Long): String = if (timestamp <= 0L) "Unknown date" else
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
