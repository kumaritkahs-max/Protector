package com.filevault.pro.presentation.screen.dashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.filevault.pro.domain.model.CatalogStats
import com.filevault.pro.domain.model.FileEntry
import com.filevault.pro.domain.model.FileType
import com.filevault.pro.domain.model.SyncProfile
import com.filevault.pro.util.FileUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateToPhotos: () -> Unit,
    onNavigateToVideos: () -> Unit,
    onNavigateToFiles: () -> Unit,
    onNavigateToSync: () -> Unit
) {
    val stats by viewModel.stats.collectAsState()
    val syncProfiles by viewModel.syncProfiles.collectAsState()
    val recentFiles by viewModel.recentFiles.collectAsState()
    val lastScanAt by viewModel.lastScanAt.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanProgressCount by viewModel.scanProgressCount.collectAsState()
    val scanStage by viewModel.scanStage.collectAsState()

    val context = LocalContext.current
    val hasMediaAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
    val hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            HeaderSection(
                isScanning = isScanning,
                scanProgressCount = scanProgressCount,
                scanStage = scanStage,
                onScanNow = viewModel::triggerScan
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
        item {
            if (stats != null) StatsGridSection(stats!!, onNavigateToPhotos, onNavigateToVideos, onNavigateToFiles)
            else Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
        item {
            ScanStatusCard(lastScanAt = lastScanAt, isScanning = isScanning, onScanNow = viewModel::triggerScan)
        }
        item { Spacer(Modifier.height(16.dp)) }
        item {
            if (!isScanning && stats != null && stats.totalFiles == 0) {
                ScanTroubleshootCard(
                    hasMediaAccess = hasMediaAccess,
                    hasAllFilesAccess = hasAllFilesAccess,
                    onOpenPermissions = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                )
                Spacer(Modifier.height(16.dp))
            }
        }
        if (syncProfiles.isNotEmpty()) {
            item { SyncStatusSection(syncProfiles = syncProfiles, onManageSync = onNavigateToSync) }
            item { Spacer(Modifier.height(16.dp)) }
        }
        if (recentFiles.isNotEmpty()) {
            item {
                SectionTitle("Recent Files", onSeeAll = onNavigateToFiles)
            }
            item {
                RecentFilesRow(files = recentFiles.take(15))
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
        item {
            QuickActionsSection(onNavigateToSync = onNavigateToSync)
        }
    }
}

@Composable
private fun HeaderSection(
    isScanning: Boolean,
    scanProgressCount: Int?,
    scanStage: String?,
    onScanNow: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        Color.Transparent
                    )
                )
            )
            .padding(24.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "FileVault Pro",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Your complete file catalog",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                AnimatedContent(targetState = isScanning) { scanning ->
                    if (scanning) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                    } else {
                        IconButton(
                            onClick = onScanNow,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(Icons.Default.Refresh, "Scan now",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }

            if (isScanning) {
                Spacer(Modifier.height(16.dp))

                if (scanProgressCount != null) {
                    Text(
                        "Scanned ${scanProgressCount} files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(8.dp))
                    val progress = (scanProgressCount / 10000f).coerceAtMost(1f)
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (!scanStage.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        scanStage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsGridSection(
    stats: CatalogStats,
    onPhotos: () -> Unit,
    onVideos: () -> Unit,
    onFiles: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Photos",
                value = stats.totalPhotos.formatCount(),
                icon = Icons.Default.Photo,
                color = Color(0xFF1848C4),
                onClick = onPhotos
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Videos",
                value = stats.totalVideos.formatCount(),
                icon = Icons.Default.VideoLibrary,
                color = Color(0xFF8B009C),
                onClick = onVideos
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Audio",
                value = stats.totalAudio.formatCount(),
                icon = Icons.Default.AudioFile,
                color = Color(0xFF006A4E),
                onClick = onFiles
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Documents",
                value = stats.totalDocuments.formatCount(),
                icon = Icons.Default.Description,
                color = Color(0xFFB94E00),
                onClick = onFiles
            )
        }
        Spacer(Modifier.height(12.dp))
        StorageCard(totalSizeBytes = stats.totalSizeBytes, totalFiles = stats.totalFiles)
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
            Text(title, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun StorageCard(totalSizeBytes: Long, totalFiles: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Catalog Size", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("${totalFiles.formatCount()} files · ${FileUtils.formatSize(totalSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun ScanStatusCard(lastScanAt: Long?, isScanning: Boolean, onScanNow: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (isScanning) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                else Icon(Icons.Default.Scanner, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(if (isScanning) "Scanning…" else "Last Scan",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (lastScanAt != null) formatTimeAgo(lastScanAt) else "Never",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (!isScanning) {
                TextButton(onClick = onScanNow) { Text("Scan Now") }
            }
        }
    }
}

@Composable
private fun ScanTroubleshootCard(
    hasMediaAccess: Boolean,
    hasAllFilesAccess: Boolean,
    onOpenPermissions: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("No files found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Scan completed but didn\'t find any files. This can happen if the app doesn\'t have access to your storage.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(12.dp))
            if (!hasMediaAccess) {
                Text("• Grant media access (photos/videos/audio)", style = MaterialTheme.typography.bodySmall)
            }
            if (!hasAllFilesAccess) {
                Text("• Grant All Files Access (required for full device scan)", style = MaterialTheme.typography.bodySmall)
            }
            if (hasMediaAccess && hasAllFilesAccess) {
                Text("• If you still see 0 files, try running the scan again or restarting the app.",
                    style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenPermissions, modifier = Modifier.fillMaxWidth()) {
                Text("Open Permissions")
            }
        }
    }
}

@Composable
private fun SyncStatusSection(syncProfiles: List<SyncProfile>, onManageSync: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SectionTitle("Sync Profiles", onSeeAll = onManageSync)
        Spacer(Modifier.height(8.dp))
        syncProfiles.take(3).forEach { profile ->
            SyncProfileCard(profile)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SyncProfileCard(profile: SyncProfile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (profile.isActive) MaterialTheme.colorScheme.primary.copy(0.15f)
                               else MaterialTheme.colorScheme.outline.copy(0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (profile.type.name == "TELEGRAM") Icons.Default.Send else Icons.Default.Email,
                    null,
                    tint = if (profile.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(
                    if (profile.isActive) "Active · Every ${profile.intervalHours}h" else "Inactive",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Surface(
                shape = CircleShape,
                color = if (profile.isActive) MaterialTheme.colorScheme.primary.copy(0.12f)
                        else MaterialTheme.colorScheme.outline.copy(0.1f)
            ) {
                Text(
                    if (profile.isActive) "Active" else "Off",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (profile.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun RecentFilesRow(files: List<FileEntry>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(files) { file ->
            RecentFileThumb(file)
        }
    }
}

@Composable
private fun RecentFileThumb(file: FileEntry) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (file.fileType == FileType.PHOTO || file.fileType == FileType.VIDEO) {
            AsyncImage(
                model = File(file.path),
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(file.name.takeLast(3).uppercase(), style = MaterialTheme.typography.labelSmall)
            }
        }
        if (file.fileType == FileType.VIDEO) {
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .clip(CircleShape).background(Color.Black.copy(0.6f)).size(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun QuickActionsSection(onNavigateToSync: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SectionTitle("Quick Actions")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickActionChip(Icons.Default.Sync, "Sync Now", onClick = onNavigateToSync, modifier = Modifier.weight(1f))
            QuickActionChip(Icons.Default.CompareArrows, "Duplicates", onClick = {}, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickActionChip(Icons.Default.IosShare, "Export CSV", onClick = {}, modifier = Modifier.weight(1f))
            QuickActionChip(Icons.Default.FolderOpen, "Browse", onClick = {}, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickActionChip(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(
        modifier = modifier.clickable(onClick = onClick).height(52.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SectionTitle(title: String, onSeeAll: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) { Text("See all →", style = MaterialTheme.typography.labelMedium) }
        }
    }
}

private fun Int.formatCount(): String = when {
    this >= 1_000_000 -> "%.1fM".format(this / 1_000_000.0)
    this >= 1_000 -> "%.1fK".format(this / 1_000.0)
    else -> this.toString()
}

private fun formatTimeAgo(time: Long): String {
    val diff = System.currentTimeMillis() - time
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000} min ago"
        diff < 86400_000 -> "${diff / 3600_000} hr ago"
        else -> SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(time))
    }
}
