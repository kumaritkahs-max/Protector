# FileVault Pro — Android APK Full Feature Specification

## App Overview

**App Name:** FileVault Pro (working title)
**Platform:** Android (minSdk 26 / Android 8.0, targetSdk 34 / Android 14)
**Language:** Kotlin (100%)
**UI Framework:** Jetpack Compose (Material 3 / Material You)
**Architecture:** MVVM + Clean Architecture

---

## 1. Core Goals

The app will:

1. Perform a deep, continuous scan of the entire device storage — including hidden folders, SD cards, system directories, and app-private paths.
2. Store file metadata (not file copies) permanently in a local Room database.
3. Monitor for new or changed files in near real-time.
4. Display media and files in a rich, sortable, filterable UI across three main tabs.
5. Automatically sync newly discovered files to user-configured remote destinations (Email via SMTP, Telegram Bot API).
6. Run silently in the background even after the app is closed.
7. Store all user credentials and settings securely using Android Keystore / EncryptedSharedPreferences.

---

## 2. Full Feature List

### 2.1 Core Features

| # | Feature | Description |
|---|---------|-------------|
| 1 | Deep File Scan | Scans every accessible folder: DCIM, Pictures, Downloads, hidden folders (`.nomedia`), external SD cards, and system-accessible paths using MANAGE_EXTERNAL_STORAGE + MediaStore + File walking. |
| 2 | Metadata Catalog | Permanently stores path, filename, folder, size, MIME type, date modified, dimensions (images), duration (videos), orientation (EXIF), date first seen, hidden status. |
| 3 | Three-Tab UI | Photos tab (images), Videos tab (videos), Files tab (all other types). Each tab supports grid/list view toggle. |
| 4 | Real-Time Monitoring | ContentObserver on MediaStore + FileObserver on key directories for near-instant detection of new/changed/deleted files. |
| 5 | Background Service | Persistent background scanning via WorkManager PeriodicWorkRequest (every 15 min flexible window). Survives app close, doze mode, and device restarts. |
| 6 | Auto Sync — Email | SMTP-based email sync. Sends files as attachments to a configured recipient on a user-defined schedule. |
| 7 | Auto Sync — Telegram | Sends files to a Telegram chat via Bot API (`sendDocument`, `sendPhoto`, `sendVideo`). Configurable bot token + chat ID. |
| 8 | Sync History | Logs every sync attempt (timestamp, destination, file count, success/failure, error message). |
| 9 | Sync Scheduling | Per-destination sync interval: 1 hour, 6 hours, 12 hours, 24 hours, or manual only. |
| 10 | Credential Storage | All SMTP passwords, Telegram bot tokens, and other secrets stored in EncryptedSharedPreferences backed by Android Keystore. |
| 11 | Thumbnail Generation | On-demand thumbnails generated and cached in app's private cache directory. Uses Coil for display. |
| 12 | Sorting | Sort by: newest, oldest, largest, smallest, folder name (A–Z / Z–A), file type, date added to catalog. |
| 13 | Permission Handling | Requests MANAGE_EXTERNAL_STORAGE (All Files Access) with explanation screen. Gracefully falls back to MediaStore + SAF for users who deny. Handles READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_AUDIO on Android 13+. |
| 14 | Dark / Light Theme | Supports Material You dynamic color theming. User can also manually choose Dark, Light, or System Default. |
| 15 | Initial Scan Progress | Full-screen progress indicator on first scan showing scanned folder count and discovered file count. |

### 2.2 Additional / Recommended Features

| # | Feature | Description |
|---|---------|-------------|
| 16 | Search | Full-text search by filename, folder path, file extension, or any metadata field. Real-time search with debounce. |
| 17 | Filter by Type | Quick filter chips: Images, Videos, Documents (PDF, Word, etc.), Audio, Archives (ZIP/RAR), APKs, Others. |
| 18 | Filter by Folder | Browse or select specific folders to show/hide in catalog. |
| 19 | Date Range Filter | Filter files by date modified range (calendar picker). |
| 20 | Folder Browser | Hierarchical folder view showing folder name, file count, total size, and thumbnail preview. |
| 21 | Duplicate Finder | Groups files by content hash (MD5 or SHA-256) to detect and display duplicate files. User can choose which copies to delete (soft-delete from catalog). |
| 22 | File Detail Screen | Full-screen detail view: thumbnail/preview, all metadata displayed, path, share button, open-with button, sync status. |
| 23 | Batch Selection | Multi-select files with long-press. Batch actions: mark for sync, delete from catalog, share, export metadata. |
| 24 | Manual Sync Trigger | "Sync Now" button per sync destination in settings. Also a global "Sync All" option. |
| 25 | Multiple Sync Profiles | Unlimited sync destinations. Each profile has: name, type (Email/Telegram), credentials, schedule, file type scope, active/inactive toggle. |
| 26 | Sync Scope per Profile | Per-profile filter: sync all files, only photos, only videos, only documents, custom file type list. |
| 27 | Export Catalog | Export entire catalog (or filtered view) as CSV or JSON to device storage or share intent. |
| 28 | Import Catalog | Import a previously exported catalog JSON/CSV as a backup restore. |
| 29 | File Recovery Hint | If a synced file is deleted from device but still in catalog, show a "File missing — available in sync destination" warning badge. |
| 30 | Advanced EXIF Filters | Filter images by: camera model (from EXIF), GPS location present, resolution range, focal length. |
| 31 | Stats Dashboard | Home screen card showing: total files scanned, storage used, last scan time, last sync time, sync success rate. |
| 32 | Root Support | If device is rooted, optionally use root access to scan /data/data and other system-restricted paths. |
| 33 | Exclude Folders | User can add folders to an exclusion list — those directories will be skipped during scans. |
| 34 | Notification Center | In-app notification log for: scan completions, sync completions, errors, new large-file detections. |
| 35 | System Notification | Android status-bar notification for: background scan running, sync in progress, sync errors. |
| 36 | Widget | Home screen widget showing: total file count, last scan time, quick "Sync Now" button. |
| 37 | Cloud Metadata Backup | Option to backup the catalog database (metadata only, not files) to a user-provided WebDAV or self-hosted server. |
| 38 | Offline Mode | Full catalog browsing available without network. Sync queued when network reconnects. |
| 39 | Battery Optimization Hint | Detect if app is in battery optimization (doze) and guide user to exempt it for reliable background work. |
| 40 | App Lock (PIN/Biometric) | Optional PIN or biometric lock on app open to protect sensitive file catalog. |
| 41 | Per-File Sync Ignore | Mark individual files as "never sync" — excluded from all sync operations. |
| 42 | Material You Dynamic Color | Full Material 3 dynamic color support — app theme adapts to device wallpaper on Android 12+. |
| 43 | Adaptive Icons | Full adaptive icon set with foreground/background layers for Android 8+. |
| 44 | Landscape Support | Full landscape layout support on phones and tablets. |
| 45 | Accessibility | Content descriptions on all interactive elements. Minimum touch target sizes. Support for system font scaling. |

---

## 3. Technical Architecture

### 3.1 Language & Build

- **Language:** Kotlin 1.9+
- **Build System:** Gradle (Kotlin DSL)
- **minSdk:** 26 (Android 8.0)
- **targetSdk:** 34 (Android 14)
- **Compile SDK:** 34

### 3.2 Key Libraries & Dependencies

```kotlin
// build.gradle.kts (app)

dependencies {
    // UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.navigation:navigation-compose:2.7.x")
    implementation("androidx.activity:activity-compose:1.8.x")

    // Lifecycle / ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.x")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.x")

    // Dependency Injection
    implementation("com.google.dagger:hilt-android:2.x")
    kapt("com.google.dagger:hilt-android-compiler:2.x")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.x")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.x")
    implementation("androidx.room:room-ktx:2.6.x")
    kapt("androidx.room:room-compiler:2.6.x")

    // DataStore / Encrypted Prefs
    implementation("androidx.datastore:datastore-preferences:1.0.x")
    implementation("androidx.security:security-crypto:1.1.x") // EncryptedSharedPreferences

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.x")
    implementation("androidx.hilt:hilt-work:1.1.x")

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.x")
    implementation("io.coil-kt:coil-video:2.x")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.x")
    implementation("com.squareup.retrofit2:converter-gson:2.x")
    implementation("com.squareup.okhttp3:okhttp:4.x")
    implementation("com.squareup.okhttp3:logging-interceptor:4.x")

    // Email (SMTP)
    implementation("com.sun.mail:android-mail:1.6.x")
    implementation("com.sun.mail:android-activation:1.6.x")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.x")

    // Paging (optional, for large catalogs)
    implementation("androidx.paging:paging-runtime-ktx:3.2.x")
    implementation("androidx.paging:paging-compose:3.2.x")

    // Glance (Widget)
    implementation("androidx.glance:glance-appwidget:1.0.x")
    implementation("androidx.glance:glance-material3:1.0.x")

    // Biometric
    implementation("androidx.biometric:biometric:1.2.x")

    // Splash Screen
    implementation("androidx.core:core-splashscreen:1.0.x")
}
```

### 3.3 Module / Layer Structure

```
app/
├── di/                         # Hilt modules
│   ├── DatabaseModule.kt
│   ├── NetworkModule.kt
│   ├── RepositoryModule.kt
│   └── WorkerModule.kt
│
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt      # Room database definition
│   │   ├── dao/
│   │   │   ├── FileEntryDao.kt
│   │   │   ├── SyncProfileDao.kt
│   │   │   └── SyncHistoryDao.kt
│   │   └── entity/
│   │       ├── FileEntryEntity.kt
│   │       ├── SyncProfileEntity.kt
│   │       └── SyncHistoryEntity.kt
│   ├── remote/
│   │   ├── telegram/
│   │   │   ├── TelegramApiService.kt
│   │   │   └── TelegramSyncImpl.kt
│   │   └── email/
│   │       └── EmailSyncImpl.kt
│   ├── repository/
│   │   ├── FileRepositoryImpl.kt
│   │   ├── SyncProfileRepositoryImpl.kt
│   │   └── SyncHistoryRepositoryImpl.kt
│   └── preferences/
│       ├── AppPreferences.kt   # DataStore
│       └── EncryptedPrefs.kt   # EncryptedSharedPreferences
│
├── domain/
│   ├── model/
│   │   ├── FileEntry.kt        # Domain model (not Entity)
│   │   ├── SyncProfile.kt
│   │   ├── SyncHistory.kt
│   │   └── FileType.kt         # Enum: PHOTO, VIDEO, AUDIO, DOCUMENT, ARCHIVE, APK, OTHER
│   ├── repository/
│   │   ├── FileRepository.kt
│   │   ├── SyncProfileRepository.kt
│   │   └── SyncHistoryRepository.kt
│   └── usecase/
│       ├── scan/
│       │   ├── PerformInitialScanUseCase.kt
│       │   ├── PerformIncrementalScanUseCase.kt
│       │   └── ScanDirectoryUseCase.kt
│       ├── sync/
│       │   ├── SyncFilesUseCase.kt
│       │   ├── SyncToTelegramUseCase.kt
│       │   └── SyncToEmailUseCase.kt
│       ├── catalog/
│       │   ├── GetFilesUseCase.kt
│       │   ├── SearchFilesUseCase.kt
│       │   ├── GetFolderTreeUseCase.kt
│       │   └── FindDuplicatesUseCase.kt
│       └── export/
│           ├── ExportCatalogCsvUseCase.kt
│           └── ExportCatalogJsonUseCase.kt
│
├── presentation/
│   ├── MainActivity.kt
│   ├── navigation/
│   │   ├── AppNavGraph.kt
│   │   └── BottomNavItem.kt
│   ├── screen/
│   │   ├── photos/
│   │   │   ├── PhotosScreen.kt
│   │   │   └── PhotosViewModel.kt
│   │   ├── videos/
│   │   │   ├── VideosScreen.kt
│   │   │   └── VideosViewModel.kt
│   │   ├── files/
│   │   │   ├── FilesScreen.kt
│   │   │   └── FilesViewModel.kt
│   │   ├── detail/
│   │   │   ├── FileDetailScreen.kt
│   │   │   └── FileDetailViewModel.kt
│   │   ├── folders/
│   │   │   ├── FolderBrowserScreen.kt
│   │   │   └── FolderBrowserViewModel.kt
│   │   ├── duplicates/
│   │   │   ├── DuplicatesScreen.kt
│   │   │   └── DuplicatesViewModel.kt
│   │   ├── settings/
│   │   │   ├── SettingsScreen.kt
│   │   │   └── SettingsViewModel.kt
│   │   ├── sync/
│   │   │   ├── SyncProfilesScreen.kt
│   │   │   ├── AddSyncProfileScreen.kt
│   │   │   ├── SyncHistoryScreen.kt
│   │   │   └── SyncViewModel.kt
│   │   ├── dashboard/
│   │   │   ├── DashboardScreen.kt
│   │   │   └── DashboardViewModel.kt
│   │   └── permission/
│   │       └── PermissionScreen.kt
│   └── component/             # Reusable Composables
│       ├── FileGrid.kt
│       ├── FileListItem.kt
│       ├── SortBottomSheet.kt
│       ├── FilterChipRow.kt
│       ├── ThumbnailImage.kt
│       └── StatsCard.kt
│
├── worker/
│   ├── ScanWorker.kt           # CoroutineWorker for periodic scanning
│   └── SyncWorker.kt           # CoroutineWorker for periodic syncing
│
├── observer/
│   ├── MediaStoreObserver.kt   # ContentObserver on MediaStore
│   └── FileSystemObserver.kt   # FileObserver on key directories
│
├── widget/
│   └── FileVaultWidget.kt      # Glance App Widget
│
└── util/
    ├── FileUtils.kt
    ├── MimeTypeUtils.kt
    ├── HashUtils.kt            # MD5/SHA-256 for duplicate detection
    ├── ExifUtils.kt
    ├── FormatUtils.kt          # File size, duration formatting
    └── PermissionUtils.kt
```

---

## 4. Database Schema (Room)

### 4.1 FileEntry Table

```kotlin
@Entity(
    tableName = "file_entries",
    indices = [
        Index("folder_path"),
        Index("last_modified"),
        Index("file_type"),
        Index("date_added"),
        Index("mime_type"),
        Index("content_hash")
    ]
)
data class FileEntryEntity(
    @PrimaryKey val path: String,           // Absolute file path (unique key)
    val name: String,                       // Filename with extension
    val folderPath: String,                 // Parent directory path
    val folderName: String,                 // Parent directory name only
    val sizeBytes: Long,                    // File size in bytes
    val lastModified: Long,                 // Epoch ms from file system
    val mimeType: String,                   // e.g. "image/jpeg"
    val fileType: String,                   // Enum name: PHOTO, VIDEO, AUDIO, DOCUMENT, ARCHIVE, APK, OTHER
    val width: Int?,                        // Image/video width in px
    val height: Int?,                       // Image/video height in px
    val durationMs: Long?,                  // Video/audio duration in ms
    val orientation: Int?,                  // EXIF orientation (images)
    val cameraMake: String?,                // EXIF camera make
    val cameraModel: String?,               // EXIF camera model
    val hasGps: Boolean,                    // EXIF GPS data present
    val dateTaken: Long?,                   // EXIF date taken (epoch ms)
    val dateAdded: Long,                    // Epoch ms when first cataloged
    val isHidden: Boolean,                  // File or parent folder starts with '.'
    val contentHash: String?,               // MD5 hash (populated lazily for duplicate detection)
    val thumbnailCachePath: String?,        // Path to cached thumbnail in app cache dir
    val isSyncIgnored: Boolean,             // User marked "never sync"
    val lastSyncedAt: Long?,               // Epoch ms of last successful sync
    val isDeletedFromDevice: Boolean        // True if file no longer exists on disk
)
```

### 4.2 SyncProfile Table

```kotlin
@Entity(tableName = "sync_profiles")
data class SyncProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                       // User-given profile name
    val type: String,                       // "EMAIL" or "TELEGRAM"
    val isActive: Boolean,
    val intervalHours: Int,                 // 0 = manual only, else 1/6/12/24
    val fileTypeScope: String,              // JSON array of FileType names, or "ALL"
    val lastSyncAt: Long?,

    // Email-specific (null if Telegram)
    val smtpHost: String?,
    val smtpPort: Int?,
    val smtpUsername: String?,
    val smtpPasswordKey: String?,           // Key into EncryptedSharedPreferences
    val emailRecipient: String?,
    val emailSubjectTemplate: String?,

    // Telegram-specific (null if Email)
    val telegramBotTokenKey: String?,       // Key into EncryptedSharedPreferences
    val telegramChatId: String?,
    val telegramCaptionTemplate: String?,

    val createdAt: Long
)
```

### 4.3 SyncHistory Table

```kotlin
@Entity(tableName = "sync_history")
data class SyncHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,                    // FK to SyncProfile
    val profileName: String,               // Snapshot of name at time of sync
    val startedAt: Long,
    val completedAt: Long?,
    val filesSynced: Int,
    val filesFailed: Int,
    val status: String,                    // "SUCCESS", "PARTIAL", "FAILED", "IN_PROGRESS"
    val errorMessage: String?
)
```

### 4.4 ExcludedFolder Table

```kotlin
@Entity(tableName = "excluded_folders")
data class ExcludedFolderEntity(
    @PrimaryKey val folderPath: String,
    val addedAt: Long
)
```

---

## 5. Scanning Mechanism — Detailed

### 5.1 Permission Strategy

**Android 13+ (API 33+):**
- `READ_MEDIA_IMAGES`
- `READ_MEDIA_VIDEO`
- `READ_MEDIA_AUDIO`
- `MANAGE_EXTERNAL_STORAGE` (All Files Access — requires special intent)

**Android 10–12:**
- `READ_EXTERNAL_STORAGE`
- `MANAGE_EXTERNAL_STORAGE`

**Android 8–9:**
- `READ_EXTERNAL_STORAGE`
- `WRITE_EXTERNAL_STORAGE`

**Fallback (if MANAGE_EXTERNAL_STORAGE denied):**
- MediaStore queries only (covers most user-visible files)
- SAF DocumentsContract for user-selected folders

### 5.2 Initial Deep Scan Algorithm

```
1. On first launch (or user-triggered rescan):
   a. Show progress screen with real-time counters.
   b. Launch CoroutineScope(Dispatchers.IO).
   c. Phase 1 — MediaStore Query:
      - Query MediaStore.Images.Media.EXTERNAL_CONTENT_URI for all images.
      - Query MediaStore.Video.Media.EXTERNAL_CONTENT_URI for all videos.
      - Query MediaStore.Audio.Media.EXTERNAL_CONTENT_URI for audio.
      - Query MediaStore.Files.getContentUri("external") for all files.
      - Insert/upsert all results into Room DB.
   d. Phase 2 — File System Walk (if MANAGE_EXTERNAL_STORAGE granted):
      - Walk /storage/emulated/0/ recursively.
      - Walk all paths from Environment.getExternalStorageDirectory().
      - Walk external SD card paths via Context.getExternalFilesDirs(null).
      - For each file not already in DB, collect metadata and insert.
      - For each directory, check against ExcludedFolders table — skip if excluded.
   e. Phase 3 — Metadata Enrichment (async, lower priority):
      - For each image: read ExifInterface for width, height, orientation,
        camera make/model, GPS, date taken.
      - For each video: use MediaMetadataRetriever for duration, width, height.
      - Update DB records with enriched metadata.
   f. Mark scan complete in DataStore. Store last scan timestamp.
```

### 5.3 Incremental Scan (WorkManager — every 15 minutes)

```
1. Read lastScanTimestamp from DataStore.
2. Query MediaStore for files where DATE_MODIFIED > lastScanTimestamp.
3. For new/modified files: upsert into DB.
4. For MANAGE_EXTERNAL_STORAGE path:
   - Walk monitored directories for files newer than lastScanTimestamp.
5. Check DB for paths that no longer exist on disk:
   - Mark isDeletedFromDevice = true (do not remove from DB).
6. Update lastScanTimestamp.
7. Trigger ContentObserver de-registration and re-registration if needed.
```

### 5.4 Real-Time Detection

**ContentObserver (MediaStore):**
- Register on `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`
- Register on `MediaStore.Video.Media.EXTERNAL_CONTENT_URI`
- On change notification: query specific URI and upsert to DB immediately.

**FileObserver (File System):**
- Register on DCIM/, Pictures/, Downloads/, WhatsApp/Media/, and any folder user adds.
- Events: CREATE, CLOSE_WRITE, DELETE, MOVED_TO, MOVED_FROM.
- On CREATE/CLOSE_WRITE: collect metadata and upsert.
- On DELETE: mark isDeletedFromDevice = true.
- Note: FileObserver is not recursive. Register on each direct subfolder too.

---

## 6. Sync Module — Detailed

### 6.1 Telegram Sync

**API:** Telegram Bot API (https://api.telegram.org/bot{TOKEN}/)

**Endpoints used:**
- `sendPhoto` — for images
- `sendVideo` — for videos
- `sendDocument` — for all other files
- `sendAudio` — for audio files

**Request:** Multipart form data with file binary + caption.

**Limits:**
- Max file size via Bot API: 50 MB per file.
- For larger files: warn user, skip file, log in sync history.
- Rate limit: 30 messages per second per bot (implement delay/queue).

**Flow:**
```
1. Query DB for files where lastSyncedAt IS NULL (or < profile.lastSyncAt)
   AND isSyncIgnored = false
   AND isDeletedFromDevice = false
   AND fileType matches profile.fileTypeScope.
2. For each file:
   a. Verify file still exists on disk.
   b. Check file size < 50 MB.
   c. Call appropriate Telegram endpoint.
   d. On success: update lastSyncedAt, increment filesSynced.
   e. On failure: increment filesFailed, log error.
3. Update SyncHistory record.
4. Update profile.lastSyncAt.
```

### 6.2 Email Sync (SMTP)

**Library:** android-mail (Jakarta Mail port for Android)

**Configuration per profile:**
- SMTP Host (e.g., smtp.gmail.com)
- SMTP Port (465 for SSL, 587 for STARTTLS)
- Username
- Password (stored in EncryptedSharedPreferences)
- Recipient address
- Subject template (supports tokens: `{date}`, `{filecount}`, `{app_name}`)
- SSL/TLS toggle

**Batching Strategy:**
- Group files into email batches of max 10 MB total attachment size.
- Send one email per batch with multiple attachments.
- Include metadata summary in email body (filename, size, date, path).

**Flow:**
```
1. Query DB for unsynced files matching scope.
2. Group into attachment batches by cumulative size.
3. For each batch:
   a. Create MimeMessage with attachments.
   b. Send via JavaMail Session.
   c. On success: mark files as synced.
   d. On failure: retry up to 3 times with exponential backoff.
4. Update SyncHistory.
```

### 6.3 Sync Worker (WorkManager)

```kotlin
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncFilesUseCase: SyncFilesUseCase
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val profileId = inputData.getLong("profile_id", -1)
            syncFilesUseCase(profileId)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry()
            else Result.failure()
        }
    }
}
```

**Scheduling:**
```kotlin
fun scheduleSyncWorker(profileId: Long, intervalHours: Int) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val request = PeriodicWorkRequestBuilder<SyncWorker>(
        intervalHours.toLong(), TimeUnit.HOURS,
        15, TimeUnit.MINUTES  // flex interval
    )
        .setConstraints(constraints)
        .setInputData(workDataOf("profile_id" to profileId))
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "sync_profile_$profileId",
        ExistingPeriodicWorkPolicy.UPDATE,
        request
    )
}
```

---

## 7. UI / UX Specification

### 7.1 Navigation Structure

```
Bottom Navigation:
├── Dashboard (Home icon) — Stats overview
├── Photos (Image icon)
├── Videos (Play icon)
├── Files (Folder icon)
└── Settings (Gear icon)

Settings Sub-navigation:
├── Sync Profiles
│   ├── List of profiles
│   └── Add/Edit Profile (drawer/full screen)
├── Sync History
├── Scan Settings
│   ├── Excluded Folders
│   ├── Scan interval override
│   └── Root access toggle
├── Appearance
│   ├── Theme (Light/Dark/System)
│   └── Grid column count
├── Export / Import
│   ├── Export CSV
│   ├── Export JSON
│   └── Import Catalog
├── Security
│   ├── App Lock (PIN / Biometric)
│   └── Clear credentials
└── About
    ├── App version
    ├── Database stats
    └── Contact/Support link
```

### 7.2 Photos Tab

- **Default view:** Grid, 3 columns (user-adjustable to 2, 4)
- **Grid item:** Thumbnail + filename overlay (on long press) + sync status badge (✓ synced, ⏳ pending, ✗ failed)
- **Tap:** Opens FileDetailScreen with full-screen photo preview + zoom/pan
- **Long press:** Enters multi-select mode
- **Top bar:** Search icon, Sort icon, Filter icon, View toggle (grid/list)
- **Sort sheet:** Bottom sheet with sort options (newest, oldest, largest, smallest, folder A-Z, date taken)
- **Filter sheet:** File type chips, folder picker, date range, camera model (EXIF), has GPS toggle

### 7.3 Videos Tab

- **Default view:** Grid, 2 columns (wider thumbnails)
- **Grid item:** Video thumbnail + duration badge (bottom-right) + play icon overlay + file size
- **Tap:** Opens FileDetailScreen with video metadata. "Open With" button launches system video player.
- **Long press:** Multi-select

### 7.4 Files Tab

- **Default view:** List (each item = icon + name + size + date)
- **File type icon:** Color-coded by category (PDF = red, ZIP = orange, APK = teal, etc.)
- **Group by folder:** Toggle to group list by parent folder
- **Tap:** FileDetailScreen (no preview for non-media) — metadata only + Open With button

### 7.5 Dashboard Screen

```
┌─────────────────────────────────┐
│  FileVault Pro                  │
├─────────────────────────────────┤
│  [Stats Row]                    │
│  📷 12,400 Photos               │
│  🎬 890 Videos                  │
│  📄 3,201 Files                 │
├─────────────────────────────────┤
│  [Storage Overview]             │
│  Media occupies 45.2 GB         │
│  Progress bar vs total storage  │
├─────────────────────────────────┤
│  [Last Scan]                    │
│  2 minutes ago · 16,491 files   │
│  [Scan Now] button              │
├─────────────────────────────────┤
│  [Sync Status]                  │
│  Telegram: 16,200 synced ✓      │
│  Email: 3 failed ✗ [Retry]      │
│  [Sync All Now] button          │
├─────────────────────────────────┤
│  [Recent Files]                 │
│  Horizontal scroll of 10 latest │
└─────────────────────────────────┘
```

### 7.6 File Detail Screen

```
┌─────────────────────────────────┐
│  ← Back                [⋮ Menu]│
├─────────────────────────────────┤
│                                 │
│   [Thumbnail / Photo Preview]   │
│         (zoomable/pannable)     │
│                                 │
├─────────────────────────────────┤
│  filename.jpg                   │
│  📁 /storage/.../DCIM/Camera/   │
│                                 │
│  SIZE        DATE MODIFIED      │
│  4.2 MB      Jan 15, 2025       │
│                                 │
│  DIMENSIONS  TYPE               │
│  4000×3000   image/jpeg         │
│                                 │
│  CAMERA      DATE TAKEN         │
│  Samsung S23 Jan 15, 2025 14:22 │
│                                 │
│  SYNC STATUS                    │
│  Synced to Telegram ✓           │
│  Not synced to Email            │
│                                 │
│  [Open With]  [Share]  [Ignore] │
└─────────────────────────────────┘
```

### 7.7 Add Sync Profile Screen

**Email profile:**
```
Profile Name: [text field]
Type: Email ●  Telegram ○

SMTP Server: [text field]       Port: [text field]
Username:    [text field]
Password:    [password field]
Recipient:   [text field]
Subject:     [text field]       {date} {filecount} tokens shown

Sync every: [dropdown: Manual / 1h / 6h / 12h / 24h]
File types: [All] [Photos] [Videos] [Docs] [Custom...]

[Test Connection]   [Save Profile]
```

**Telegram profile:**
```
Profile Name: [text field]
Type: Email ○  Telegram ●

Bot Token:  [text field]
Chat ID:    [text field]
Caption:    [text field]       {filename} {date} tokens shown

Sync every: [dropdown]
File types: [checkboxes]

[Test Bot Token]   [Save Profile]
```

---

## 8. Permissions in AndroidManifest.xml

```xml
<!-- Storage -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="29" />
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
    tools:ignore="ScopedStorage" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />

<!-- Network -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Background -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />

<!-- Notifications (Android 13+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Biometric -->
<uses-permission android:name="android.permission.USE_BIOMETRIC" />
<uses-permission android:name="android.permission.USE_FINGERPRINT" />

<!-- Wake Lock (for background workers) -->
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

---

## 9. Background Work Details

### 9.1 WorkManager Setup (Application class)

```kotlin
@HiltAndroidApp
class FileVaultApp : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        scheduleScanWorker()
    }

    private fun scheduleScanWorker() {
        val request = PeriodicWorkRequestBuilder<ScanWorker>(
            15, TimeUnit.MINUTES, 5, TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "file_scan",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
```

### 9.2 Boot Receiver

```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Re-schedule workers after device restart
            WorkManager.getInstance(context).apply {
                // Scan worker
                // All active sync profile workers
            }
        }
    }
}
```

### 9.3 Foreground Service for Initial Scan

The initial deep scan (potentially scanning 50,000+ files) will run as a foreground service with a persistent notification showing progress, to prevent Android from killing it.

```
Notification: "FileVault Pro is scanning your device"
Progress: "Scanned 12,400 of ~50,000 files..."
Action button: "Cancel"
```

---

## 10. Security Details

### 10.1 Credential Storage Flow

```kotlin
// Writing a credential
fun saveCredential(key: String, value: String) {
    val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "filevault_secure_prefs",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    encryptedPrefs.edit().putString(key, value).apply()
}
```

- Credentials never stored in plain text.
- Keys in EncryptedSharedPreferences are opaque random UUIDs (not the profile field name).
- SyncProfileEntity stores only the key reference, not the actual value.
- Biometric authentication required to view/edit credentials in settings (if app lock enabled).

### 10.2 App Lock

- User sets a 4–6 digit PIN or enables biometric.
- PIN stored as bcrypt hash in EncryptedSharedPreferences.
- BiometricPrompt API used for fingerprint/face ID.
- App locks after configurable timeout (1 min, 5 min, 30 min, or on each open).

---

## 11. Duplicate Finder

### 11.1 Algorithm

```
1. User navigates to Duplicates screen (Settings > Tools > Duplicates).
2. Show warning: "This process may take a while for large catalogs."
3. Launch background computation:
   a. Group all FileEntry records by sizeBytes (fast pre-filter).
   b. For each group with size > 1 AND same file size:
      - Compute MD5 hash of file content in chunks (1 MB buffers).
      - Store contentHash in DB.
   c. Group by contentHash — any group with count > 1 is a duplicate set.
4. Display duplicate sets:
   - Each set shows all files with same content.
   - Highlight "original" (oldest dateAdded) vs duplicates.
   - User taps to select which copy to remove from catalog.
   - NOTE: App does not delete actual files. It removes from catalog only.
     (User can optionally trigger system delete via DocumentsContract.)
```

---

## 12. Export / Import Catalog

### 12.1 CSV Export Format

```
path,name,folder_path,folder_name,size_bytes,last_modified,mime_type,file_type,width,height,duration_ms,date_added,is_hidden,last_synced_at,is_deleted
/storage/.../IMG_001.jpg,IMG_001.jpg,/storage/.../DCIM/Camera,Camera,4294967,1705320000000,image/jpeg,PHOTO,4000,3000,,1705320100000,false,1705400000000,false
...
```

### 12.2 JSON Export Format

```json
{
  "export_version": "1",
  "exported_at": "2025-01-15T14:22:00Z",
  "total_files": 16491,
  "files": [
    {
      "path": "/storage/.../IMG_001.jpg",
      "name": "IMG_001.jpg",
      "folder_path": "/storage/.../DCIM/Camera",
      "folder_name": "Camera",
      "size_bytes": 4294967,
      "last_modified": 1705320000000,
      "mime_type": "image/jpeg",
      "file_type": "PHOTO",
      "width": 4000,
      "height": 3000,
      "duration_ms": null,
      "date_added": 1705320100000,
      "is_hidden": false,
      "last_synced_at": 1705400000000,
      "is_deleted": false
    }
  ]
}
```

---

## 13. Home Screen Widget (Glance)

```kotlin
class FileVaultWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Column(
                modifier = GlanceModifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            ) {
                Text("FileVault Pro", style = TextStyle(fontWeight = FontWeight.Bold))
                Text("📷 12,400  🎬 890  📄 3,201")
                Text("Last scan: 2 min ago")
                Button("Sync Now", onClick = actionRunCallback<SyncNowCallback>())
            }
        }
    }
}
```

---

## 14. Build Variants

| Variant | Purpose |
|---------|---------|
| `debug` | Development. Logging enabled. Allow cleartext traffic. |
| `release` | Production APK. ProGuard/R8 minification + obfuscation. Signed with release keystore. |
| `releaseTest` | Like release but with debug logging for QA. |

**ProGuard rules needed for:**
- Room (keep DAOs)
- Hilt (keep generated components)
- Retrofit (keep model classes)
- android-mail (javax.mail keep rules)

---

## 15. Implementation Roadmap (Phase Order)

| Phase | Tasks | Estimated Effort |
|-------|-------|-----------------|
| Phase 1 | Project setup, Gradle config, Hilt, Room schema, basic permissions screen | 2 days |
| Phase 2 | MediaStore scanner, File walker, initial scan progress UI | 3 days |
| Phase 3 | Photos/Videos/Files tabs with grid/list, sorting, thumbnail loading | 3 days |
| Phase 4 | WorkManager periodic scan, ContentObserver, FileObserver | 2 days |
| Phase 5 | Sync module: Telegram integration, Email/SMTP integration | 4 days |
| Phase 6 | Sync profile settings UI, sync history screen | 2 days |
| Phase 7 | Search, filters, folder browser, file detail screen | 3 days |
| Phase 8 | Dashboard screen, stats computation | 1 day |
| Phase 9 | Duplicate finder, export/import catalog | 2 days |
| Phase 10 | App lock (PIN + biometric), credential management UI | 2 days |
| Phase 11 | Home screen widget, boot receiver, notifications | 1 day |
| Phase 12 | Dark/light theme, Material You, responsive layout | 1 day |
| Phase 13 | ProGuard config, release build, signing, testing on multiple devices | 2 days |
| **Total** | | **~28 days** |

---

## 16. Minimum Device Compatibility

| Requirement | Minimum |
|------------|---------|
| Android version | 8.0 (API 26) |
| RAM | 2 GB recommended |
| Storage for app | ~50 MB app + Room DB size scales with catalog |
| Network | WiFi/Mobile for sync (offline scanning fully supported) |
| Permissions | MANAGE_EXTERNAL_STORAGE for full scan; degrades gracefully without it |

---

## 17. Known Challenges & Mitigations Summary

| Challenge | Mitigation |
|-----------|------------|
| Battery drain | WorkManager flex intervals, ContentObserver for real-time (no polling), batch DB writes |
| Android 10+ Scoped Storage | MANAGE_EXTERNAL_STORAGE + MediaStore fallback + SAF for user folders |
| 50,000+ files initial scan | Chunked inserts, foreground service, progress UI, incremental approach |
| File deletions | ContentObserver DELETE events + periodic staleness check in scan worker |
| Sync failures / network issues | Retry with exponential backoff, sync history log, partial success handling |
| Credential security | Android Keystore + EncryptedSharedPreferences, never plain-text |
| Telegram 50 MB file limit | Pre-check size, skip oversized files, log in history with user notification |
| Email attachment size limits | Batch attachments by cumulative size limit, multiple emails per sync |
| FileObserver not recursive | Register on each immediate subdirectory, rely on WorkManager for deeper paths |
| Doze mode killing workers | Request battery optimization exemption, use setExpedited for critical updates |

---

*Document generated: March 18, 2026*
*Specification version: 1.0*
*Ready for full Android APK development using Kotlin + Jetpack Compose*
