package com.filevault.pro.presentation.screen.videos

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.filevault.pro.domain.model.FileEntry
import com.filevault.pro.presentation.screen.photos.EmptyState
import com.filevault.pro.presentation.screen.photos.MultiSelectActionBar
import com.filevault.pro.presentation.screen.photos.SearchBar
import com.filevault.pro.presentation.screen.photos.SortBottomSheet
import com.filevault.pro.util.FileUtils
import com.filevault.pro.util.MediaQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Composable
fun VideosScreen(
    onFileClick: (String) -> Unit
) {
    val viewModel: VideosViewModel = hiltViewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val videos by viewModel.videos.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    var showSortSheet by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val folder = DocumentFile.fromTreeUri(context, uri) ?: return@launch
                selectedPaths.forEach { path ->
                    val src = File(path)
                    if (src.exists()) {
                        val mime = FileUtils.getMimeType(src).ifBlank { "video/*" }
                        val dest = folder.createFile(mime, src.name) ?: return@forEach
                        context.contentResolver.openOutputStream(dest.uri)?.use { out ->
                            src.inputStream().use { it.copyTo(out) }
                        }
                    }
                }
                withContext(Dispatchers.Main) { selectedPaths = emptySet() }
            }
        }
    }

    fun shareSelected() {
        val uris = selectedPaths.mapNotNull { path ->
            runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
            }.getOrNull()
        }
        if (uris.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "video/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${selectedPaths.size} videos"))
    }

    fun zipAndShare() {
        scope.launch(Dispatchers.IO) {
            isBusy = true
            try {
                val zipFile = File(context.cacheDir, "videos_export_${System.currentTimeMillis()}.zip")
                ZipOutputStream(zipFile.outputStream().buffered()).use { zos ->
                    selectedPaths.forEach { path ->
                        val f = File(path)
                        if (f.exists()) {
                            zos.putNextEntry(ZipEntry(f.name))
                            f.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(intent, "Share ZIP"))
                }
            } finally {
                isBusy = false
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (selectedPaths.isNotEmpty()) Text("${selectedPaths.size} selected", fontWeight = FontWeight.SemiBold)
                        else Text("Videos", fontWeight = FontWeight.Bold)
                    },
                    actions = {
                        if (selectedPaths.isNotEmpty()) {
                            IconButton(onClick = { selectedPaths = videos.map { it.path }.toSet() }) {
                                Icon(Icons.Default.SelectAll, "Select All")
                            }
                            IconButton(onClick = { selectedPaths = emptySet() }) { Icon(Icons.Default.Close, null) }
                        } else {
                            IconButton(onClick = { showSortSheet = true }) { Icon(Icons.Default.Sort, null) }
                        }
                    }
                )
                SearchBar(searchQuery, viewModel::setSearchQuery, "Search videos…")
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${videos.size} videos", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                }
            }
        },
        bottomBar = {
            if (selectedPaths.isNotEmpty()) {
                MultiSelectActionBar(
                    selectedCount = selectedPaths.size,
                    isBusy = isBusy,
                    onShare = ::shareSelected,
                    onZip = ::zipAndShare,
                    onSaveToFolder = { folderPickerLauncher.launch(null) },
                    onDeleteFromApp = { showDeleteConfirm = true },
                    onClearSelection = { selectedPaths = emptySet() }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (videos.isEmpty()) EmptyState("No videos found.\nRun a scan to catalog your device.", Icons.Default.VideoLibrary)
            else LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(videos, key = { it.path }) { video ->
                    VideoGridItem(
                        file = video,
                        isSelected = video.path in selectedPaths,
                        onClick = {
                            if (selectedPaths.isNotEmpty()) {
                                selectedPaths = if (video.path in selectedPaths)
                                    selectedPaths - video.path else selectedPaths + video.path
                            } else {
                                MediaQueue.set(video.path, videos.map { it.path })
                                onFileClick(video.path)
                            }
                        },
                        onLongClick = { selectedPaths = selectedPaths + video.path }
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove from Catalog") },
            text = { Text("Remove ${selectedPaths.size} video(s) from the FileVault catalog? The actual files on your device will NOT be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.markDeletedFromApp(selectedPaths)
                    selectedPaths = emptySet()
                    showDeleteConfirm = false
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showSortSheet) {
        SortBottomSheet(sortOrder, viewModel::setSortOrder) { showSortSheet = false }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoGridItem(
    file: FileEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AsyncImage(
            model = File(file.path),
            contentDescription = file.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))

        Box(
            modifier = Modifier.size(36.dp).align(Alignment.Center)
                .clip(CircleShape).background(Color.Black.copy(0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }

        if (file.durationMs != null) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(0.65f)
            ) {
                Text(
                    FileUtils.formatDuration(file.durationMs),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium
                )
            }
        }

        Text(
            file.name.substringBeforeLast("."),
            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
            color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
        )

        if (isSelected) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}
