package com.filevault.pro.presentation.screen.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToSyncProfiles: () -> Unit,
    onNavigateToDuplicates: () -> Unit,
    onNavigateToFolders: () -> Unit,
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToCrashLog: () -> Unit = {},
    onNavigateToDiagnostic: () -> Unit = {}
) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsState()
    val showHidden by viewModel.showHiddenFiles.collectAsState()
    val appLockEnabled by viewModel.appLockEnabled.collectAsState()
    val scanInterval by viewModel.scanIntervalMinutes.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showExportDialog by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importCatalog(context, uri)
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            scope.launch { snackbarHostState.showSnackbar(it) }
            viewModel.clearStatus()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { scaffoldPadding ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.padding(scaffoldPadding)
        ) {
            item {
                TopAppBar(
                    title = { Text("Settings", fontWeight = FontWeight.Bold) }
                )
            }

            item { SettingsGroup("Sync") }
            item {
                SettingsItem(
                    icon = Icons.Default.Sync,
                    title = "Sync Profiles",
                    subtitle = "Configure Telegram and Email sync",
                    onClick = onNavigateToSyncProfiles
                )
            }

            item { SettingsGroup("Scanning") }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.Visibility,
                    title = "Show Hidden Files",
                    subtitle = "Include files in hidden folders",
                    checked = showHidden,
                    onCheckedChange = viewModel::setShowHiddenFiles
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Timer,
                    title = "Scan Interval",
                    subtitle = "Every $scanInterval minutes",
                    onClick = { }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.FolderOff,
                    title = "Excluded Folders",
                    subtitle = "Manage folders to skip during scan",
                    onClick = onNavigateToFolders
                )
            }

            item { SettingsGroup("Tools") }
            item {
                SettingsItem(
                    icon = Icons.Default.CompareArrows,
                    title = "Duplicate Finder",
                    subtitle = "Find and manage duplicate files",
                    onClick = onNavigateToDuplicates
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "Notification Center",
                    subtitle = "View scan and sync activity log",
                    onClick = onNavigateToNotifications
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.FileDownload,
                    title = "Export Catalog",
                    subtitle = "Export as CSV or JSON",
                    onClick = { showExportDialog = true }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.FileUpload,
                    title = "Import Catalog",
                    subtitle = "Import from CSV or JSON file",
                    onClick = {
                        importLauncher.launch(arrayOf("text/csv", "application/json", "text/*", "application/octet-stream"))
                    }
                )
            }

            item { SettingsGroup("Appearance") }
            item {
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = "Theme",
                    subtitle = themeMode,
                    onClick = { viewModel.cycleTheme() }
                )
            }

            item { SettingsGroup("Security") }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.Lock,
                    title = "App Lock",
                    subtitle = "Require PIN or biometric to open",
                    checked = appLockEnabled,
                    onCheckedChange = viewModel::setAppLockEnabled
                )
            }

            item { SettingsGroup("Diagnostic") }
            item {
                SettingsItem(
                    icon = Icons.Default.Science,
                    title = "Storage Diagnostic",
                    subtitle = "Debug why files aren't being fetched — shows permission state, storage roots, MediaStore counts, and file walk results",
                    onClick = onNavigateToDiagnostic
                )
            }

            item { SettingsGroup("System") }
            item {
                SettingsItem(
                    icon = Icons.Default.BatteryFull,
                    title = "Battery Optimization",
                    subtitle = "Exempt app for reliable background work",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        }
                    }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.AppSettingsAlt,
                    title = "App Permissions",
                    subtitle = "Manage all app permissions",
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.BugReport,
                    title = "Crash Logs",
                    subtitle = "View crash reports from previous sessions",
                    onClick = onNavigateToCrashLog
                )
            }

            item { SettingsGroup("About") }
            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Version",
                    subtitle = "1.0.0",
                    onClick = {}
                )
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Catalog", fontWeight = FontWeight.Bold) },
            text = { Text("Choose the format for your catalog export.") },
            confirmButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    viewModel.exportCatalogJson(context)
                }) { Text("JSON") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    viewModel.exportCatalogCsv(context)
                }) { Text("CSV") }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun SettingsGroup(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(0.5f)) },
        leadingContent = {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            }
        },
        trailingContent = trailing ?: { Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.3f)) }
    )
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsItem(
        icon = icon, title = title, subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) }
    )
}
