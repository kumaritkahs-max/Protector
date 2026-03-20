package com.filevault.pro.presentation.screen.folders

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filevault.pro.domain.model.FileEntry
import com.filevault.pro.domain.model.FileType
import com.filevault.pro.domain.model.FolderInfo
import com.filevault.pro.domain.repository.FileRepository
import com.filevault.pro.util.FileUtils
import com.filevault.pro.util.MediaQueue
import com.filevault.pro.util.simpleScrollbar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed class BrowseItem {
    data class Folder(val info: FolderInfo) : BrowseItem()
    data class FileItem(val entry: FileEntry) : BrowseItem()
}

@HiltViewModel
class FolderBrowserViewModel @Inject constructor(
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _currentPath = MutableStateFlow<String?>(null)
    val currentPath: StateFlow<String?> = _currentPath

    private val _items = MutableStateFlow<List<BrowseItem>>(emptyList())
    val items: StateFlow<List<BrowseItem>> = _items

    private val _pathStack = MutableStateFlow<List<String>>(emptyList())
    val pathStack: StateFlow<List<String>> = _pathStack

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    val folders: StateFlow<List<FolderInfo>> = fileRepository.getFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            folders.collect { folderList ->
                if (_currentPath.value == null) {
                    _items.value = folderList.map { BrowseItem.Folder(it) }
                    _isLoading.value = false
                }
            }
        }
    }

    fun navigateIntoFolder(folderPath: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _pathStack.value = _pathStack.value + (folderPath)
            _currentPath.value = folderPath
            loadFolderContents(folderPath)
        }
    }

    fun navigateUp(): Boolean {
        val stack = _pathStack.value
        return if (stack.isEmpty()) {
            false
        } else {
            val newStack = stack.dropLast(1)
            _pathStack.value = newStack
            val parentPath = newStack.lastOrNull()
            _currentPath.value = parentPath
            viewModelScope.launch {
                _isLoading.value = true
                if (parentPath == null) {
                    val folderList = folders.value
                    _items.value = folderList.map { BrowseItem.Folder(it) }
                    _isLoading.value = false
                } else {
                    loadFolderContents(parentPath)
                }
            }
            true
        }
    }

    private suspend fun loadFolderContents(folderPath: String) {
        val dir = File(folderPath)
        if (!dir.exists() || !dir.isDirectory) {
            _isLoading.value = false
            return
        }

        val fsItems = dir.listFiles()?.sortedWith(
            compareBy({ !it.isDirectory }, { it.name.lowercase() })
        ) ?: emptyList()

        val browseItems = fsItems.map { f ->
            if (f.isDirectory) {
                val subCount = f.listFiles()?.size ?: 0
                BrowseItem.Folder(
                    FolderInfo(
                        path = f.absolutePath,
                        name = f.name,
                        fileCount = subCount,
                        totalSizeBytes = 0L,
                        lastModified = f.lastModified()
                    )
                )
            } else {
                val ext = f.extension.lowercase()
                val fileType = FileType.fromExtension(ext)
                BrowseItem.FileItem(
                    FileEntry(
                        path = f.absolutePath,
                        name = f.name,
                        folderPath = f.parent ?: "",
                        folderName = f.parentFile?.name ?: "",
                        sizeBytes = f.length(),
                        lastModified = f.lastModified(),
                        mimeType = getMime(ext),
                        fileType = fileType,
                        dateAdded = f.lastModified()
                    )
                )
            }
        }

        _items.value = browseItems
        _isLoading.value = false
    }
}

private fun getMime(ext: String) = when (ext) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "mp4" -> "video/mp4"
    "mkv" -> "video/x-matroska"
    "mp3" -> "audio/mpeg"
    "wav" -> "audio/wav"
    "flac" -> "audio/flac"
    "pdf" -> "application/pdf"
    "json" -> "application/json"
    "txt", "md", "log" -> "text/plain"
    "xml" -> "application/xml"
    "zip" -> "application/zip"
    "apk" -> "application/vnd.android.package-archive"
    else -> "*/*"
}

@Composable
fun FolderBrowserScreen(
    viewModel: FolderBrowserViewModel = hiltViewModel(),
    onFileClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val items by viewModel.items.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    val pathStack by viewModel.pathStack.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    BackHandler(enabled = pathStack.isNotEmpty()) {
        viewModel.navigateUp()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (currentPath == null) "Browse" else File(currentPath!!).name,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (currentPath != null) {
                            Text(
                                currentPath!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!viewModel.navigateUp()) onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FolderOpen, null, Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.2f))
                            Spacer(Modifier.height(12.dp))
                            Text("Empty folder", color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                        }
                    }
                }
                else -> {
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(items, key = {
                            when (it) {
                                is BrowseItem.Folder -> "folder:${it.info.path}"
                                is BrowseItem.FileItem -> "file:${it.entry.path}"
                            }
                        }) { item ->
                            when (item) {
                                is BrowseItem.Folder -> {
                                    FolderItemRow(
                                        folder = item.info,
                                        onClick = { viewModel.navigateIntoFolder(item.info.path) }
                                    )
                                }
                                is BrowseItem.FileItem -> {
                                    FileItemRow(
                                        file = item.entry,
                                        onClick = { onFileClick(item.entry.path) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderItemRow(folder: FolderInfo, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(folder.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                "${folder.fileCount} items",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
            )
        },
        leadingContent = {
            Icon(Icons.Default.Folder, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = { Icon(Icons.Default.ChevronRight, null) }
    )
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f)
    )
}

@Composable
private fun FileItemRow(file: FileEntry, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Row {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = fileTypeColor(file.fileType).copy(0.15f)
                ) {
                    Text(
                        file.fileType.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = fileTypeColor(file.fileType),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    FileUtils.formatSize(file.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                )
            }
        },
        leadingContent = {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    fileTypeIcon(file.fileType),
                    null,
                    tint = fileTypeColor(file.fileType),
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        trailingContent = {
            Icon(Icons.Default.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.3f))
        }
    )
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(0.2f)
    )
}

private fun fileTypeIcon(type: FileType): ImageVector = when (type) {
    FileType.PHOTO -> Icons.Default.Image
    FileType.VIDEO -> Icons.Default.VideoFile
    FileType.AUDIO -> Icons.Default.AudioFile
    FileType.DOCUMENT -> Icons.Default.Description
    FileType.ARCHIVE -> Icons.Default.FolderZip
    FileType.APK -> Icons.Default.Android
    FileType.OTHER -> Icons.Default.InsertDriveFile
}

private fun fileTypeColor(type: FileType) = when (type) {
    FileType.PHOTO -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
    FileType.VIDEO -> androidx.compose.ui.graphics.Color(0xFF2196F3)
    FileType.AUDIO -> androidx.compose.ui.graphics.Color(0xFF9C27B0)
    FileType.DOCUMENT -> androidx.compose.ui.graphics.Color(0xFFFF9800)
    FileType.ARCHIVE -> androidx.compose.ui.graphics.Color(0xFF795548)
    FileType.APK -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
    FileType.OTHER -> androidx.compose.ui.graphics.Color(0xFF607D8B)
}
