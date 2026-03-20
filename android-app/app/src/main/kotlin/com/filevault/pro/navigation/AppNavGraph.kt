package com.filevault.pro.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.filevault.pro.domain.model.FileType
import com.filevault.pro.presentation.screen.audioplayer.AudioPlayerScreen
import com.filevault.pro.presentation.screen.dashboard.DashboardScreen
import com.filevault.pro.presentation.screen.detail.FileDetailScreen
import com.filevault.pro.presentation.screen.duplicates.DuplicatesScreen
import com.filevault.pro.presentation.screen.fileviewer.UniversalFileViewerScreen
import com.filevault.pro.presentation.screen.files.FilesScreen
import com.filevault.pro.presentation.screen.folders.FolderBrowserScreen
import com.filevault.pro.presentation.screen.imageviewer.ImageViewerScreen
import com.filevault.pro.presentation.screen.onboarding.OnboardingScreen
import com.filevault.pro.presentation.screen.permission.PermissionScreen
import com.filevault.pro.presentation.screen.photos.PhotosScreen
import com.filevault.pro.presentation.screen.settings.SettingsScreen
import com.filevault.pro.presentation.screen.sync.SyncHistoryScreen
import com.filevault.pro.presentation.screen.sync.SyncProfilesScreen
import com.filevault.pro.presentation.screen.sync.AddSyncProfileScreen
import com.filevault.pro.presentation.screen.videos.VideosScreen
import com.filevault.pro.presentation.screen.videoplayer.VideoPlayerScreen
import com.filevault.pro.presentation.screen.crashlog.CrashLogScreen
import com.filevault.pro.presentation.screen.diagnostic.DiagnosticScreen
import com.filevault.pro.presentation.screen.notifications.NotificationCenterScreen
import java.net.URLEncoder

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    object Onboarding : Screen("onboarding", "Welcome")
    object Permission : Screen("permission", "Permissions")
    object Dashboard : Screen("dashboard", "Home", Icons.Default.Home)
    object Photos : Screen("photos", "Photos", Icons.Default.Photo)
    object Videos : Screen("videos", "Videos", Icons.Default.VideoLibrary)
    object Files : Screen("files", "Files", Icons.Default.FolderOpen)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object FileDetail : Screen("file_detail/{path}", "Detail") {
        fun createRoute(path: String) = "file_detail/${URLEncoder.encode(path, "UTF-8")}"
    }
    object ImageViewer : Screen("image_viewer/{path}", "Image Viewer") {
        fun createRoute(path: String) = "image_viewer/${URLEncoder.encode(path, "UTF-8")}"
    }
    object VideoPlayer : Screen("video_player/{path}", "Video Player") {
        fun createRoute(path: String) = "video_player/${URLEncoder.encode(path, "UTF-8")}"
    }
    object AudioPlayer : Screen("audio_player/{path}", "Audio Player") {
        fun createRoute(path: String) = "audio_player/${URLEncoder.encode(path, "UTF-8")}"
    }
    object FileViewer : Screen("file_viewer/{path}", "File Viewer") {
        fun createRoute(path: String) = "file_viewer/${URLEncoder.encode(path, "UTF-8")}"
    }
    object Folders : Screen("folders", "Folders")
    object Duplicates : Screen("duplicates", "Duplicates")
    object SyncProfiles : Screen("sync_profiles", "Sync Profiles")
    object AddSyncProfile : Screen("add_sync_profile?id={id}", "Add Profile") {
        fun createRoute(id: Long? = null) = if (id != null) "add_sync_profile?id=$id" else "add_sync_profile?id=-1"
    }
    object SyncHistory : Screen("sync_history/{profileId}", "Sync History") {
        fun createRoute(profileId: Long) = "sync_history/$profileId"
    }
    object Notifications : Screen("notifications", "Notifications")
    object CrashLog : Screen("crash_log", "Crash Logs")
    object Diagnostic : Screen("diagnostic", "Storage Diagnostic")
}

val bottomNavItems = listOf(Screen.Dashboard, Screen.Photos, Screen.Videos, Screen.Files, Screen.Settings)

fun openFileRoute(path: String, fileType: FileType): String {
    return when (fileType) {
        FileType.PHOTO -> Screen.ImageViewer.createRoute(path)
        FileType.VIDEO -> Screen.VideoPlayer.createRoute(path)
        FileType.AUDIO -> Screen.AudioPlayer.createRoute(path)
        FileType.DOCUMENT, FileType.ARCHIVE, FileType.APK, FileType.OTHER ->
            Screen.FileViewer.createRoute(path)
    }
}

fun openFileRouteByPath(path: String): String {
    val ext = path.substringAfterLast('.', "").lowercase()
    val type = com.filevault.pro.domain.model.FileType.fromExtension(ext)
    return openFileRoute(path, type)
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any { it.route == currentDestination?.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { screen.icon?.let { Icon(it, contentDescription = screen.title) } },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Permission.route,
            modifier = Modifier.padding(padding),
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300))
            },
            exitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300))
            },
            popEnterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300))
            },
            popExitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300))
            }
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(onComplete = {
                    navController.navigate(Screen.Permission.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                })
            }
            composable(Screen.Permission.route) {
                PermissionScreen(onPermissionsGranted = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Permission.route) { inclusive = true }
                    }
                })
            }
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToPhotos      = { navController.navigate(Screen.Photos.route) },
                    onNavigateToVideos      = { navController.navigate(Screen.Videos.route) },
                    onNavigateToFiles       = { navController.navigate(Screen.Files.route) },
                    onNavigateToSync        = { navController.navigate(Screen.SyncProfiles.route) },
                    onNavigateToBrowse      = { navController.navigate(Screen.Folders.route) },
                    onNavigateToDuplicates  = { navController.navigate(Screen.Duplicates.route) }
                )
            }
            composable(Screen.Photos.route) {
                PhotosScreen(onFileClick = { path ->
                    navController.navigate(Screen.ImageViewer.createRoute(path))
                })
            }
            composable(Screen.Videos.route) {
                VideosScreen(onFileClick = { path ->
                    navController.navigate(Screen.VideoPlayer.createRoute(path))
                })
            }
            composable(Screen.Files.route) {
                FilesScreen(
                    onFileClick = { path ->
                        navController.navigate(openFileRouteByPath(path))
                    },
                    onFolderBrowse = { navController.navigate(Screen.Folders.route) }
                )
            }
            composable(
                route = Screen.FileDetail.route,
                arguments = listOf(navArgument("path") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedPath = backStackEntry.arguments?.getString("path") ?: ""
                val path = java.net.URLDecoder.decode(encodedPath, "UTF-8")
                FileDetailScreen(path = path, onBack = { navController.popBackStack() })
            }
            composable(
                route = Screen.ImageViewer.route,
                arguments = listOf(navArgument("path") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedPath = backStackEntry.arguments?.getString("path") ?: ""
                val path = java.net.URLDecoder.decode(encodedPath, "UTF-8")
                ImageViewerScreen(path = path, onBack = { navController.popBackStack() })
            }
            composable(
                route = Screen.VideoPlayer.route,
                arguments = listOf(navArgument("path") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedPath = backStackEntry.arguments?.getString("path") ?: ""
                val path = java.net.URLDecoder.decode(encodedPath, "UTF-8")
                VideoPlayerScreen(path = path, onBack = { navController.popBackStack() })
            }
            composable(
                route = Screen.AudioPlayer.route,
                arguments = listOf(navArgument("path") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedPath = backStackEntry.arguments?.getString("path") ?: ""
                val path = java.net.URLDecoder.decode(encodedPath, "UTF-8")
                AudioPlayerScreen(path = path, onBack = { navController.popBackStack() })
            }
            composable(
                route = Screen.FileViewer.route,
                arguments = listOf(navArgument("path") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedPath = backStackEntry.arguments?.getString("path") ?: ""
                val path = java.net.URLDecoder.decode(encodedPath, "UTF-8")
                UniversalFileViewerScreen(path = path, onBack = { navController.popBackStack() })
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToSyncProfiles  = { navController.navigate(Screen.SyncProfiles.route) },
                    onNavigateToDuplicates    = { navController.navigate(Screen.Duplicates.route) },
                    onNavigateToFolders       = { navController.navigate(Screen.Folders.route) },
                    onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                    onNavigateToCrashLog      = { navController.navigate(Screen.CrashLog.route) },
                    onNavigateToDiagnostic    = { navController.navigate(Screen.Diagnostic.route) }
                )
            }
            composable(Screen.Folders.route) {
                FolderBrowserScreen(
                    onFileClick = { path ->
                        navController.navigate(openFileRouteByPath(path))
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Duplicates.route) {
                DuplicatesScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.SyncProfiles.route) {
                SyncProfilesScreen(
                    onAddProfile  = { navController.navigate(Screen.AddSyncProfile.createRoute()) },
                    onEditProfile = { id -> navController.navigate(Screen.AddSyncProfile.createRoute(id)) },
                    onViewHistory = { id -> navController.navigate(Screen.SyncHistory.createRoute(id)) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.AddSyncProfile.route,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("id") ?: -1L
                AddSyncProfileScreen(
                    profileId = if (id == -1L) null else id,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.SyncHistory.route,
                arguments = listOf(navArgument("profileId") { type = NavType.LongType })
            ) { backStackEntry ->
                val profileId = backStackEntry.arguments?.getLong("profileId") ?: 0L
                SyncHistoryScreen(profileId = profileId, onBack = { navController.popBackStack() })
            }
            composable(Screen.Notifications.route) {
                NotificationCenterScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.CrashLog.route) {
                CrashLogScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Diagnostic.route) {
                DiagnosticScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
