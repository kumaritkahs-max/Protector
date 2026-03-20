package com.filevault.pro.presentation.screen.photos

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.filevault.pro.domain.model.SortField
import com.filevault.pro.domain.model.SortOrder
import com.filevault.pro.util.FileUtils
import com.filevault.pro.util.MediaQueue
import com.filevault.pro.util.gridScrollbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotosScreen(
    viewModel: PhotosViewModel = hiltViewModel(),
    onFileClick: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photos by viewModel.photos.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()

    var showSortSheet by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    val isMultiSelect = selectedPaths.isNotEmpty()

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
                        val mime = FileUtils.getMimeType(src).ifBlank { "*/*" }
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
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${selectedPaths.size} photos"))
    }

    fun zipAndShare() {
        scope.launch(Dispatchers.IO) {
            isBusy = true
            try {
                val zipFile = File(context.cacheDir, "photos_export_${System.currentTimeMillis()}.zip")
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
                        if (isMultiSelect) Text("${selectedPaths.size} selected", fontWeight = FontWeight.SemiBold)
                        else Text("Photos", fontWeight = FontWeight.Bold)
                    },
                    actions = {
                        if (isMultiSelect) {
                            IconButton(onClick = { selectedPaths = photos.map { it.path }.toSet() }) {
                                Icon(Icons.Default.SelectAll, "Select All")
                            }
                            IconButton(onClick = { selectedPaths = emptySet() }) {
                                Icon(Icons.Default.Close, "Deselect")
                            }
                        } else {
                            IconButton(onClick = { showSortSheet = true }) {
                                Icon(Icons.Default.Sort, "Sort")
                            }
                            IconButton(onClick = viewModel::toggleView) {
                                Icon(if (isGridView) Icons.Default.ViewList else Icons.Default.GridView, "Toggle view")
                            }
                        }
                    }
                )
                SearchBar(
                    query = searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    placeholder = "Search photos…"
                )
                GridColumnsRow(
                    columns = gridColumns,
                    onChange = viewModel::setGridColumns,
                    itemCount = photos.size
                )
            }
        },
        bottomBar = {
            if (isMultiSelect) {
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
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (photos.isEmpty()) {
                EmptyState(message = "No photos found.\nRun a scan to catalog your device.", icon = Icons.Default.Photo)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(photos, key = { it.path }) { photo ->
                        PhotoGridItem(
                            file = photo,
                            isSelected = photo.path in selectedPaths,
                            onClick = {
                                if (isMultiSelect) {
                                    selectedPaths = if (photo.path in selectedPaths)
                                        selectedPaths - photo.path
                                    else selectedPaths + photo.path
                                } else {
                                    MediaQueue.set(photo.path, photos.map { it.path })
                                    onFileClick(photo.path)
                                }
                            },
                            onLongClick = {
                                selectedPaths = selectedPaths + photo.path
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove from Catalog") },
            text = { Text("Remove ${selectedPaths.size} photo(s) from the FileVault catalog? The actual files on your device will NOT be deleted.") },
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
        SortBottomSheet(
            currentSort = sortOrder,
            onSortSelected = { viewModel.setSortOrder(it) },
            onDismiss = { showSortSheet = false }
        )
    }
}

@Composable
fun MultiSelectActionBar(
    selectedCount: Int,
    isBusy: Boolean,
    onShare: () -> Unit,
    onZip: () -> Unit,
    onSaveToFolder: () -> Unit,
    onDeleteFromApp: () -> Unit,
    onClearSelection: () -> Unit
) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        if (isBusy) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            return@BottomAppBar
        }
        IconButton(onClick = onShare) {
            Icon(Icons.Default.Share, "Share", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onZip) {
            Icon(Icons.Default.Archive, "Zip & Share", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onSaveToFolder) {
            Icon(Icons.Default.SaveAlt, "Save to Folder", tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onDeleteFromApp) {
            Icon(Icons.Default.DeleteOutline, "Remove from Catalog", tint = MaterialTheme.colorScheme.error)
        }
        IconButton(onClick = onClearSelection) {
            Icon(Icons.Default.Close, "Deselect")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoGridItem(
    file: FileEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = File(file.path),
            contentDescription = file.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (file.isDeletedFromDevice) {
            Box(
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                    .size(16.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error.copy(0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.DeleteOutline, null, tint = Color.White, modifier = Modifier.size(10.dp))
            }
        }

        if (file.lastSyncedAt != null) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                    .size(16.dp).clip(CircleShape).background(Color(0xFF00AA44).copy(0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Done, null, tint = Color.White, modifier = Modifier.size(10.dp))
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
        leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, null, Modifier.size(18.dp))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.8f),
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(0.5f)
        )
    )
}

@Composable
fun GridColumnsRow(columns: Int, onChange: (Int) -> Unit, itemCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$itemCount items", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.5f), modifier = Modifier.weight(1f))
        listOf(2, 3, 4).forEach { count ->
            IconButton(
                onClick = { onChange(count) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    when (count) {
                        2 -> Icons.Default.GridView
                        3 -> Icons.Default.Apps
                        else -> Icons.Default.GridOn
                    },
                    null,
                    modifier = Modifier.size(18.dp),
                    tint = if (columns == count) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurface.copy(0.4f)
                )
            }
        }
    }
}

@Composable
fun SortBottomSheet(
    currentSort: SortOrder,
    onSortSelected: (SortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Sort By", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            listOf(
                SortField.DATE_MODIFIED to "Date Modified",
                SortField.DATE_ADDED to "Date Added",
                SortField.DATE_TAKEN to "Date Taken",
                SortField.NAME to "Name",
                SortField.SIZE to "Size",
                SortField.FOLDER to "Folder"
            ).forEach { (field, label) ->
                ListItem(
                    headlineContent = { Text(label) },
                    leadingContent = {
                        if (currentSort.field == field) {
                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    trailingContent = {
                        if (currentSort.field == field) {
                            IconButton(onClick = {
                                onSortSelected(currentSort.copy(ascending = !currentSort.ascending))
                            }) {
                                Icon(
                                    if (currentSort.ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                    null
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .let { m ->
                            if (currentSort.field == field)
                                m.background(MaterialTheme.colorScheme.primaryContainer.copy(0.4f))
                            else m
                        }
                        .clickable {
                            if (currentSort.field == field) {
                                onSortSelected(currentSort.copy(ascending = !currentSort.ascending))
                            } else {
                                onSortSelected(SortOrder(field, false))
                            }
                        }
                )
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.4f))
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun EmptyState(message: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(0.2f))
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
