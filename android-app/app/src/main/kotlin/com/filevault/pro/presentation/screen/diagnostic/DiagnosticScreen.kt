package com.filevault.pro.presentation.screen.diagnostic

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.filevault.pro.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class DiagnosticCheck(
    val name: String,
    val status: DiagnosticStatus,
    val detail: String,
    val fix: String? = null
)

enum class DiagnosticStatus { PASS, WARN, FAIL }

private val DiagnosticStatus.color: Color
    get() = when (this) {
        DiagnosticStatus.PASS -> Color(0xFF2E7D32)
        DiagnosticStatus.WARN -> Color(0xFFE65100)
        DiagnosticStatus.FAIL -> Color(0xFFC62828)
    }

private val DiagnosticStatus.icon: ImageVector
    get() = when (this) {
        DiagnosticStatus.PASS -> Icons.Default.CheckCircle
        DiagnosticStatus.WARN -> Icons.Default.Warning
        DiagnosticStatus.FAIL -> Icons.Default.Cancel
    }

@Composable
fun DiagnosticScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var checks by remember { mutableStateOf<List<DiagnosticCheck>>(emptyList()) }
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var hasRun by remember { mutableStateOf(false) }

    fun runDiagnostics() {
        scope.launch {
            isRunning = true
            hasRun = true
            val results = mutableListOf<DiagnosticCheck>()
            val log = mutableListOf<String>()

            fun addLog(msg: String) {
                log.add(msg)
                logLines = log.toList()
            }

            fun addCheck(check: DiagnosticCheck) {
                results.add(check)
                checks = results.toList()
            }

            addLog("=== FileVault Pro Diagnostic ===")
            addLog("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            addLog("${Build.MANUFACTURER} ${Build.MODEL}")
            addLog("")

            // 1 — MANAGE_EXTERNAL_STORAGE
            addLog("[1] MANAGE_EXTERNAL_STORAGE...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val granted = Environment.isExternalStorageManager()
                addLog("    isExternalStorageManager = $granted")
                addCheck(DiagnosticCheck(
                    name = "All Files Access (MANAGE_EXTERNAL_STORAGE)",
                    status = if (granted) DiagnosticStatus.PASS else DiagnosticStatus.FAIL,
                    detail = if (granted) "Granted — full file system walk enabled" else "NOT granted at runtime. Even if the permission screen passed, you MUST also enable this in system settings.",
                    fix = if (!granted) "Settings → Apps → FileVaultPro → Permissions → Files and media → 'Allow management of all files'" else null
                ))
            } else {
                addCheck(DiagnosticCheck("All Files Access", DiagnosticStatus.PASS, "Not required below Android 11"))
            }

            // 2 — Media permissions
            addLog("[2] Media permissions...")
            val mediaPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(
                    Manifest.permission.READ_MEDIA_IMAGES to "READ_MEDIA_IMAGES",
                    Manifest.permission.READ_MEDIA_VIDEO  to "READ_MEDIA_VIDEO",
                    Manifest.permission.READ_MEDIA_AUDIO  to "READ_MEDIA_AUDIO"
                )
            } else {
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE to "READ_EXTERNAL_STORAGE")
            }
            var allMedia = true
            val mediaSb = StringBuilder()
            for ((perm, label) in mediaPerms) {
                val ok = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
                if (!ok) allMedia = false
                addLog("    $label = $ok")
                mediaSb.appendLine("$label: ${if (ok) "✓" else "✗ DENIED"}")
            }
            addCheck(DiagnosticCheck(
                name = "Media Permissions",
                status = if (allMedia) DiagnosticStatus.PASS else DiagnosticStatus.FAIL,
                detail = mediaSb.trimEnd().toString(),
                fix = if (!allMedia) "Return to Permission screen and grant Media Permissions" else null
            ))

            // 3 — Storage root discovery
            addLog("[3] Storage roots...")
            val roots = withContext(Dispatchers.IO) { FileUtils.getExternalStorageRoots(context) }
            val rootSb = StringBuilder()
            for (r in roots) {
                val readable = r.canRead()
                val totalMb = r.totalSpace / 1_048_576
                val freeMb  = r.freeSpace  / 1_048_576
                addLog("    ${r.absolutePath}  readable=$readable  total=${totalMb}MB  free=${freeMb}MB")
                rootSb.appendLine(r.absolutePath)
                rootSb.appendLine("  readable=$readable  ${totalMb}MB total  ${freeMb}MB free")
            }
            addCheck(DiagnosticCheck(
                name = "Storage Root Discovery (${roots.size} found)",
                status = when {
                    roots.isEmpty() -> DiagnosticStatus.FAIL
                    roots.any { it.canRead() } -> DiagnosticStatus.PASS
                    else -> DiagnosticStatus.WARN
                },
                detail = if (roots.isEmpty()) "No roots found" else rootSb.trimEnd().toString(),
                fix = if (roots.isEmpty()) "Grant MANAGE_EXTERNAL_STORAGE and ensure storage is mounted" else if (!roots.any { it.canRead() }) "Roots found but none are readable — MANAGE_EXTERNAL_STORAGE may not be active" else null
            ))

            // 4 — Primary storage state
            addLog("[4] External storage state...")
            val extState = Environment.getExternalStorageState()
            val extDir   = Environment.getExternalStorageDirectory()
            addLog("    state=$extState  path=${extDir.absolutePath}  readable=${extDir.canRead()}")
            addCheck(DiagnosticCheck(
                name = "Primary External Storage",
                status = when {
                    extState == Environment.MEDIA_MOUNTED && extDir.canRead() -> DiagnosticStatus.PASS
                    extState == Environment.MEDIA_MOUNTED -> DiagnosticStatus.WARN
                    else -> DiagnosticStatus.FAIL
                },
                detail = "State: $extState\nPath: ${extDir.absolutePath}\nReadable: ${extDir.canRead()}",
                fix = if (extState != Environment.MEDIA_MOUNTED) "External storage is not mounted (state=$extState)" else if (!extDir.canRead()) "Storage mounted but not readable — grant MANAGE_EXTERNAL_STORAGE" else null
            ))

            // 5 — MediaStore queries
            addLog("[5] MediaStore queries...")
            var msTotal = 0; var msImages = 0; var msVideos = 0; var msAudio = 0; var msError: String? = null
            withContext(Dispatchers.IO) {
                try {
                    fun qCount(uri: android.net.Uri, sel: String? = null): Int {
                        val c = context.contentResolver.query(uri, arrayOf(android.provider.BaseColumns._ID), sel, null, null)
                        val n = c?.count ?: -1; c?.close(); return n
                    }
                    msTotal  = qCount(MediaStore.Files.getContentUri("external"), "${MediaStore.Files.FileColumns.SIZE} > 0")
                    msImages = qCount(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    msVideos = qCount(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                    msAudio  = qCount(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
                } catch (e: Exception) {
                    msTotal = -2; msError = e.message
                }
            }
            addLog("    total=$msTotal  images=$msImages  videos=$msVideos  audio=$msAudio  err=$msError")
            addCheck(DiagnosticCheck(
                name = "MediaStore Query",
                status = when {
                    msError != null || msTotal < 0 -> DiagnosticStatus.FAIL
                    msTotal == 0 -> DiagnosticStatus.WARN
                    else -> DiagnosticStatus.PASS
                },
                detail = when {
                    msError != null -> "Exception: $msError"
                    msTotal == -1 -> "Cursor is null — media permissions not granted"
                    msTotal == 0 -> "MediaStore returned 0 rows\nImages=$msImages  Videos=$msVideos  Audio=$msAudio\nTry opening Gallery/Photos to trigger MediaStore indexing."
                    else -> "Total (non-empty files): $msTotal\nImages: $msImages  Videos: $msVideos  Audio: $msAudio"
                },
                fix = when {
                    msError != null || msTotal < 0 -> "Grant media permissions and re-run"
                    msTotal == 0 -> "Open the Gallery app, take a photo, or copy a file, then re-run diagnostic"
                    else -> null
                }
            ))

            // 6 — File system walk (capped at 2000)
            addLog("[6] File system walk (cap 2000)...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                addLog("    SKIPPED — MANAGE_EXTERNAL_STORAGE not granted")
                addCheck(DiagnosticCheck(
                    name = "File System Walk",
                    status = DiagnosticStatus.WARN,
                    detail = "Skipped — MANAGE_EXTERNAL_STORAGE not granted",
                    fix = "Grant 'All Files Access' then re-run"
                ))
            } else {
                val walkData = withContext(Dispatchers.IO) {
                    var fileCount = 0; var badDirs = 0
                    val samples = mutableListOf<String>()
                    try {
                        for (root in roots) {
                            if (!root.canRead()) { badDirs++; continue }
                            root.walkTopDown()
                                .onEnter { d -> d.canRead().also { ok -> if (!ok) badDirs++ } }
                                .filter { it.isFile && it.length() > 0 }
                                .take(2000 - fileCount)
                                .forEach { f ->
                                    fileCount++
                                    if (samples.size < 5) samples.add(f.absolutePath)
                                }
                        }
                    } catch (e: Exception) { addLog("    walk exception: ${e.message}") }
                    Triple(fileCount, badDirs, samples)
                }
                val (fc, bad, samples) = walkData
                addLog("    files=$fc  unreadable_dirs=$bad")
                val detail = "Files found (cap 2000): $fc\nUnreadable dirs: $bad" +
                    if (samples.isNotEmpty()) "\nSamples:\n" + samples.joinToString("\n") else ""
                addCheck(DiagnosticCheck(
                    name = "File System Walk",
                    status = when {
                        fc > 0 -> DiagnosticStatus.PASS
                        bad > 0 -> DiagnosticStatus.WARN
                        else -> DiagnosticStatus.FAIL
                    },
                    detail = detail,
                    fix = when {
                        fc == 0 && bad > 0 -> "Dirs found but not readable — confirm MANAGE_EXTERNAL_STORAGE is truly active"
                        fc == 0 -> "No files found via direct walk — device storage may be empty or all content is in MediaStore only"
                        else -> null
                    }
                ))
            }

            // 7 — Known directories
            addLog("[7] Common directories...")
            val knownDirs = listOf("DCIM", "Pictures", "Movies", "Download", "Music", "Documents", "WhatsApp/Media", "Telegram")
            val dirSb = StringBuilder()
            var anyDirFound = false
            for (name in knownDirs) {
                val dir = File(Environment.getExternalStorageDirectory(), name)
                val readable = dir.canRead()
                val items = if (readable) withContext(Dispatchers.IO) { dir.listFiles()?.size ?: 0 } else 0
                addLog("    $name: exists=${dir.exists()}  readable=$readable  items=$items")
                dirSb.appendLine("$name: exists=${dir.exists()}  readable=$readable  items=$items")
                if (dir.exists() && readable && items > 0) anyDirFound = true
            }
            addCheck(DiagnosticCheck(
                name = "Common Media Directories",
                status = if (anyDirFound) DiagnosticStatus.PASS else DiagnosticStatus.WARN,
                detail = dirSb.trimEnd().toString(),
                fix = if (!anyDirFound) "All standard directories are empty or unreadable. Confirm files exist and MANAGE_EXTERNAL_STORAGE is granted." else null
            ))

            addLog("")
            val failures = results.count { it.status == DiagnosticStatus.FAIL }
            val warnings = results.count { it.status == DiagnosticStatus.WARN }
            addLog("=== Done — Failures: $failures  Warnings: $warnings ===")

            logLines = log.toList()
            checks = results.toList()
            isRunning = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage Diagnostic", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Storage Diagnostic Tool", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Checks every layer — permissions, storage roots, MediaStore, and file system walk — and tells you exactly what's broken and how to fix it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { if (!isRunning) runDiagnostics() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isRunning
                        ) {
                            if (isRunning) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text("Running…")
                            } else {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (hasRun) "Run Again" else "Run Diagnostic")
                            }
                        }
                    }
                }
            }

            if (!hasRun) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Tap 'Run Diagnostic' to identify why files aren't loading.", color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    }
                }
            }

            if (isRunning && checks.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator()
                            Text("Checking storage…")
                        }
                    }
                }
            }

            items(checks) { check -> DiagnosticCheckCard(check) }

            if (logLines.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Raw Log", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(6.dp))
                            logLines.forEach { line ->
                                Text(line, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun DiagnosticCheckCard(check: DiagnosticCheck) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = check.status.color.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(check.status.color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(check.status.icon, null, tint = check.status.color, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(check.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                check.detail,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
            if (check.fix != null) {
                Spacer(Modifier.height(8.dp))
                Surface(color = check.status.color.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Build, null, tint = check.status.color, modifier = Modifier.size(14.dp).padding(top = 1.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Fix: ${check.fix}", style = MaterialTheme.typography.bodySmall, color = check.status.color, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
