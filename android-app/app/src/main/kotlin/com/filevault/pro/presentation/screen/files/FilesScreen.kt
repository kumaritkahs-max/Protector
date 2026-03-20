package com.filevault.pro.presentation.screen.files

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import com.filevault.pro.domain.model.FileEntry
import com.filevault.pro.domain.model.FileType
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(
    viewModel: FilesViewModel = hiltViewModel(),
    onFileClick: (String) -> Unit,
    onFolderBrowse: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val files by viewModel.files.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val selectedTypes by viewModel.selectedTypes.collectAsState()
    val isGroupByFolder by viewModel.isGroupByFolder.collectAsState()
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
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${selectedPaths.size} files"))
    }

    fun zipAndShare() {
        scope.launch(Dispatchers.IO) {
            isBusy = true
            try {
                val zipFile = File(context.cacheDir, "files_export_${System.currentTimeMillis()}.zip")
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
                        else Text("Files", fontWeight = FontWeight.Bold)
                    },
                    actions = {
                        if (selectedPaths.isNotEmpty()) {
                            IconButton(onClick = { selectedPaths = files.map { it.path }.toSet() }) {
                                Icon(Icons.Default.SelectAll, "Select All")
                            }
                            IconButton(onClick = { selectedPaths = emptySet() }) {
                                Icon(Icons.Default.Close, "Deselect")
                            }
                        } else {
                            IconButton(onClick = onFolderBrowse) { Icon(Icons.Default.FolderOpen, "Browse folders") }
                            IconButton(onClick = viewModel::toggleGroupByFolder) {
                                Icon(if (isGroupByFolder) Icons.Default.List else Icons.Default.AccountTree, null)
                            }
                            IconButton(onClick = { showSortSheet = true }) { Icon(Icons.Default.Sort, null) }
                        }
                    }
                )
                SearchBar(searchQuery, viewModel::setSearchQuery, "Search files…")
                TypeFilterChips(selectedTypes, viewModel::toggleTypeFilter)
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
            if (files.isEmpty()) EmptyState("No files found.\nRun a scan to catalog your device.", Icons.Default.InsertDriveFile)
            else {
                if (isGroupByFolder) {
                    GroupedFileList(files = files, selectedPaths = selectedPaths,
                        onFileClick = onFileClick, onLongClick = { selectedPaths = selectedPaths + it })
                } else {
                    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(files, key = { it.path }) { file ->
                            FileListItem(
                                file = file,
                                isSelected = file.path in selectedPaths,
                                onClick = {
                                    if (selectedPaths.isNotEmpty()) {
                                        selectedPaths = if (file.path in selectedPaths)
                                            selectedPaths - file.path else selectedPaths + file.path
                                    } else {
                                        if (file.fileType == FileType.AUDIO || file.fileType == FileType.VIDEO) {
                                            MediaQueue.set(file.path, files.filter { it.fileType == file.fileType }.map { it.path })
                                        }
                                        onFileClick(file.path)
                                    }
                                },
                                onLongClick = { selectedPaths = selectedPaths + file.path }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove from Catalog") },
            text = { Text("Remove ${selectedPaths.size} file(s) from the FileVault catalog? The actual files on your device will NOT be deleted.") },
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

@Composable
private fun TypeFilterChips(selectedTypes: Set<FileType>, onToggle: (FileType) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(FileType.values()) { type ->
            val selected = type in selectedTypes
            FilterChip(
                selected = selected,
                onClick = { onToggle(type) },
                label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp) },
                leadingIcon = {
                    if (selected) Icon(Icons.Default.Check, null, Modifier.size(14.dp))
                }
            )
        }
    }
}

@Composable
private fun GroupedFileList(
    files: List<FileEntry>,
    selectedPaths: Set<String>,
    onFileClick: (String) -> Unit,
    onLongClick: (String) -> Unit
) {
    val grouped = files.groupBy { it.folderName }
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        grouped.forEach { (folderName, folderFiles) ->
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(folderName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("${folderFiles.size}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    }
                }
            }
            items(folderFiles, key = { it.path }) { file ->
                FileListItem(
                    file = file,
                    isSelected = file.path in selectedPaths,
                    indent = true,
                    onClick = {
                        if (selectedPaths.isNotEmpty()) onLongClick(file.path) // toggle in multi-select
                        else onFileClick(file.path)
                    },
                    onLongClick = { onLongClick(file.path) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: FileEntry,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    indent: Boolean = false
) {
    ListItem(
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = if (indent) 16.dp else 0.dp)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(0.35f)
                else Color.Transparent
            ),
        headlineContent = {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Text(
                "${FileUtils.formatSize(file.sizeBytes)} · ${formatDate(file.lastModified)} · ${file.folderName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(0.2f)
                        else fileTypeColor(file.fileType).copy(0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp))
                } else {
                    Text(
                        file.name.substringAfterLast(".").uppercase().take(3),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = fileTypeColor(file.fileType)
                    )
                }
            }
        },
        trailingContent = {
            if (file.lastSyncedAt != null) {
                Icon(Icons.Default.CloudDone, null, modifier = Modifier.size(16.dp),
                    tint = Color(0xFF00AA44))
            }
        }
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f),
        modifier = Modifier.padding(horizontal = 16.dp))
}

private fun fileTypeColor(type: FileType) = when (type) {
    FileType.PHOTO -> Color(0xFF1848C4)
    FileType.VIDEO -> Color(0xFF8B009C)
    FileType.AUDIO -> Color(0xFF006A4E)
    FileType.DOCUMENT -> Color(0xFFB94E00)
    FileType.ARCHIVE -> Color(0xFF6B4226)
    FileType.APK -> Color(0xFF007A5E)
    FileType.OTHER -> Color(0xFF555555)
}

private fun formatDate(ms: Long): String =
    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(ms))
