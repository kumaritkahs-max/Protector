# FileVault Pro — Implemented Features Documentation

**Platform:** Android (minSdk 26 / targetSdk 35)  
**Language:** Kotlin (100%)  
**UI Toolkit:** Jetpack Compose + Material 3 / Material You  
**Architecture:** MVVM + Clean Architecture, Hilt DI, Room, WorkManager, DataStore  

---

## 1. File Scanning

### 1.1 MediaStore Scan
- Queries `MediaStore.Images.Media`, `MediaStore.Video.Media`, and `MediaStore.Audio.Media` content providers.
- Retrieves: absolute path, file name, size, MIME type, date added, date modified, and resolution (width × height for media).
- Extracts camera make/model, orientation, and GPS presence via `ExifInterface` for photos.
- Extracts duration, width, and height via `MediaMetadataRetriever` for videos.
- Upserts results into the Room `file_entries` table using `INSERT OR REPLACE`.
- Implemented in `FileRepositoryImpl.performMediaStoreScan()`.

### 1.2 File System Walk
- Recursively walks all accessible storage roots (internal + external SD cards via `Environment.getExternalStorageDirectory()`).
- Skips folders listed in the `excluded_folders` Room table.
- Skips hidden files/directories (name starting with `.`), and checks every ancestor to catch nested hidden paths.
- For each discovered file, calls `FileUtils.getMimeType()` to classify by extension, then `FileUtils.getExifData()` / `FileUtils.getVideoMetadata()` for enriched metadata.
- Reports progress via `onProgress(folder, count)` callback so the foreground service can update the notification.
- Implemented in `FileRepositoryImpl.performFileSystemWalk()`.

### 1.3 MD5 Hash Computation
- Computes MD5 checksum using a 1 MB streaming buffer (no full file load into memory).
- Runs on `Dispatchers.IO`.
- Stored in `file_entries.md5_hash` for duplicate detection.
- Implemented in `FileUtils.computeMd5()`.

### 1.4 Foreground Service Scan (`ScanForegroundService`)
- Starts as a foreground service (Android O+ uses `startForegroundService`).
- Posts a persistent notification with a "Stop" action button and a tap-to-open intent.
- Updates notification text live as scan progresses (MediaStore phase → FS walk phase → completion).
- Supports three actions:
  - `ACTION_START` — full scan + monitoring.
  - `ACTION_START_MONITORING` — monitoring only (no initial full scan).
  - `ACTION_STOP` — stops monitoring and the service.
- Registers `MediaStoreObserver` for real-time monitoring after scan completes.
- Records `lastScanAt` and `initialScanDone` in DataStore.
- Implemented in `ScanForegroundService`.

### 1.5 WorkManager Periodic Scan (`ScanWorker`)
- Schedules a `PeriodicWorkRequest` with a 15-minute repeat interval.
- Runs only when the device has `NetworkType.NOT_REQUIRED` (no network needed).
- Calls `performMediaStoreScan()` and `performFileSystemWalk()` inside a coroutine.
- Enqueued uniquely as `"periodic_file_scan"` with `KEEP` policy so duplicate enqueues are ignored.
- Implemented in `ScanWorker`.

### 1.6 Boot Receiver (`BootReceiver`)
- Registered for `BOOT_COMPLETED` and `QUICKBOOT_POWERON` intents.
- On boot, re-enqueues the periodic scan worker and (optionally) re-starts monitoring service.
- Implemented in `BootReceiver`.

---

## 2. Real-Time File Monitoring (`MediaStoreObserver`)

### 2.1 ContentObserver
- Registers observers on:
  - `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`
  - `MediaStore.Video.Media.EXTERNAL_CONTENT_URI`
  - `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI`
- All observers use `descendantsIncluded = true` so sub-folder changes are captured.

### 2.2 FileObserver
- Registers `LegacyFileObserver` (extends `FileObserver`) on 8 key directories:
  - `DCIM`, `Pictures`, `Downloads`, `WhatsApp/Media`, `Telegram`, `Movies`, `Music`, `Documents`
- Watches for: `CREATE`, `CLOSE_WRITE`, `DELETE`, `MOVED_TO`, `MOVED_FROM`.
- Only registers on directories that physically exist on the device.

### 2.3 Debounced Incremental Scan
- Both observers funnel into `triggerDebouncedScan()`.
- Uses a `Job` + `delay(2000 ms)` debounce: rapid successive changes trigger only one scan.
- Runs `performMediaStoreScan()` incrementally (not a full FS walk) for speed.

### 2.4 Lifecycle
- `register()` / `unregister()` guarded by `isRegistered` flag to prevent double registration.
- Called by `ScanForegroundService` and cleaned up in `onDestroy`.

---

## 3. Room Database Schema

### 3.1 `file_entries` Table (`FileEntryEntity`)
| Column | Type | Description |
|--------|------|-------------|
| path | TEXT PK | Absolute file path (primary key) |
| name | TEXT | File name |
| size | INTEGER | Size in bytes |
| mime_type | TEXT | MIME type string |
| file_type | TEXT | Enum: PHOTO, VIDEO, AUDIO, DOCUMENT, ARCHIVE, APK, OTHER |
| date_modified | INTEGER | Epoch ms from file system |
| date_added | INTEGER | Epoch ms when first cataloged |
| md5_hash | TEXT? | MD5 checksum (nullable — computed lazily) |
| width | INTEGER? | Pixel width (photos/videos) |
| height | INTEGER? | Pixel height (photos/videos) |
| duration_ms | INTEGER? | Duration in ms (audio/video) |
| camera_make | TEXT? | EXIF camera manufacturer |
| camera_model | TEXT? | EXIF camera model |
| has_gps | INTEGER | 1 if GPS EXIF tag present |
| orientation | INTEGER | EXIF orientation value |
| is_sync_ignored | INTEGER | 1 if excluded from all sync operations |
| last_synced_at | INTEGER? | Epoch ms of last successful sync |
| is_deleted | INTEGER | 1 if file no longer exists on disk |

Indexes: `file_type`, `date_modified`, `md5_hash`, composite `(file_type, is_deleted)`.

### 3.2 `sync_profiles` Table (`SyncProfileEntity`)
| Column | Type | Description |
|--------|------|-------------|
| id | INTEGER PK (autoincrement) | |
| name | TEXT | User-visible profile name |
| type | TEXT | Enum: TELEGRAM, EMAIL |
| telegram_bot_token | TEXT? | Bot token (stored in EncryptedSharedPreferences at runtime; entity holds reference key) |
| telegram_chat_id | TEXT? | Destination chat/channel ID |
| email_host | TEXT? | SMTP server hostname |
| email_port | INTEGER | SMTP port (default 587) |
| email_username | TEXT? | SMTP login |
| email_password | TEXT? | SMTP password (encrypted) |
| email_to | TEXT? | Recipient address |
| email_from | TEXT? | Sender address |
| is_active | INTEGER | 1 if profile is scheduled |
| interval_hours | INTEGER | Sync frequency (0 = manual only) |
| filter_types | TEXT | JSON-encoded list of FileType to include |
| filter_since_days | INTEGER | Only sync files modified within N days (0 = all) |
| last_sync_at | INTEGER? | Epoch ms of last run |
| created_at | INTEGER | Creation epoch ms |

### 3.3 `sync_history` Table (`SyncHistoryEntity`)
| Column | Type | Description |
|--------|------|-------------|
| id | INTEGER PK (autoincrement) | |
| profile_id | INTEGER FK | References sync_profiles.id |
| started_at | INTEGER | Start epoch ms |
| completed_at | INTEGER? | End epoch ms |
| files_synced | INTEGER | Count of successfully sent files |
| files_failed | INTEGER | Count of failed files |
| status | TEXT | Enum: SUCCESS, PARTIAL, FAILED, IN_PROGRESS |
| error_message | TEXT? | Top-level error description |

### 3.4 `excluded_folders` Table (`ExcludedFolderEntity`)
| Column | Type | Description |
|--------|------|-------------|
| path | TEXT PK | Absolute folder path |
| added_at | INTEGER | Epoch ms when added |

---

## 4. Sync — Telegram (`TelegramSyncImpl`)

- Uses Retrofit + OkHttp with `sendDocument` multipart endpoint of the Telegram Bot API.
- Retrieves the profile's bot token and chat ID from `EncryptedSharedPreferences`.
- **50 MB file limit**: files larger than 50 MB are skipped and counted as failed.
- **Per-file sync**: each file is uploaded individually as a `sendDocument` call.
- Respects `isSyncIgnored` flag — skipped files are not counted as failed.
- Applies profile-level filters (file type list, `filterSinceDays`).
- On completion, calls `fileRepository.markSynced(paths, syncedAt)` and `syncRepository.updateLastSyncAt`.
- Sync history row is created before sending (`IN_PROGRESS`) and updated after (`SUCCESS / PARTIAL / FAILED`).

---

## 5. Sync — Email SMTP (`EmailSyncImpl`)

- Uses `android-mail` (JavaMail port for Android).
- Connects via SMTP with STARTTLS (configurable host, port, username, password).
- **10 MB batch limit**: groups files into email batches so that attachments ≤ 10 MB per message.
- Subject line includes batch number and file count.
- Body contains a summary table of file names and sizes.
- Files larger than 10 MB are sent as individual emails (one per oversized file).
- Same filtering, sync-ignore, and history-logging logic as Telegram sync.

---

## 6. SyncWorker (`SyncWorker`)

- Runs as a `CoroutineWorker` via WorkManager.
- Input data: `KEY_PROFILE_ID` (Long).
- Loads the profile from Room; if not found or inactive, returns `Result.success()` early.
- Delegates to `TelegramSyncImpl` or `EmailSyncImpl` based on `profile.type`.
- Handles exceptions and marks the history row as `FAILED` on error.
- Returns `Result.retry()` on transient failures, `Result.success()` otherwise.

---

## 7. UI Screens

### 7.1 Onboarding (`OnboardingScreen`)
- 4-page `HorizontalPager` with animated pill page indicators.
- Pages: Deep File Scanner → Smart Catalog → Auto Sync → Advanced UI.
- Each page has a gradient icon circle, title, subtitle (in accent color), and description.
- Skip button on page 1; Back + Next on middle pages; Get Started on last page.
- Shown only on first launch (controlled by `appPreferences.onboardingDone`).

### 7.2 Permission Screen (`PermissionScreen`)
- Requests `READ_MEDIA_IMAGES` + `READ_MEDIA_VIDEO` + `READ_MEDIA_AUDIO` (Android 13+) or `READ_EXTERNAL_STORAGE` (Android 12−).
- Requests `MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` via `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` deep link.
- Requests `POST_NOTIFICATIONS` (optional).
- Each permission shown as a status card (granted = green check / not granted = outline circle).
- Auto-advances when all required permissions are granted.
- "I've granted permissions, continue →" manual fallback button.

### 7.3 Dashboard (`DashboardScreen`)
- Stats cards: Total Files, Photos, Videos, Audio, Documents, Total Size, Last Scan time.
- Quick action buttons: Start Full Scan, Start Monitoring.
- Real-time stats from `FileRepository.getStats()` (Room Flow).
- Battery optimization hint card if `isIgnoringBatteryOptimizations` is false.

### 7.4 Photos Screen (`PhotosScreen`)
- Adaptive grid/list toggle.
- Grid column count: 2, 3, or 4 (persisted per user preference).
- Search bar with 300 ms debounce.
- Sort bottom sheet: Date Modified, Date Added, Name, Size (asc/desc).
- Filter bottom sheet: by date range and file size range.
- Long-press for multi-select; bulk "Set Sync Ignore" action.
- Each item shows thumbnail (Coil), file name, size, and modification date.

### 7.5 Videos Screen (`VideosScreen`)
- Same grid/list, search, sort, and filter capabilities as Photos.
- Shows video duration badge on thumbnails.

### 7.6 Files Screen (`FilesScreen`)
- Lists all file types in a flat list.
- File type chip filter row (All / PHOTO / VIDEO / AUDIO / DOCUMENT / ARCHIVE / APK / OTHER).
- Same search, sort, multi-select as Photos.

### 7.7 File Detail Screen (`FileDetailScreen`)
- Full path, name, size, MIME type, type, date modified, date added.
- Photo-specific: width × height, orientation, camera make/model, GPS flag, EXIF dateTime.
- Video-specific: duration (formatted H:MM:SS), width × height.
- MD5 hash (computed on demand).
- Sync status: ignored flag toggle, last synced timestamp.
- Share and Open With actions using `FileProvider`.

### 7.8 Folder Browser (`FolderBrowserScreen`)
- Displays current directory path as a breadcrumb.
- Lists sub-directories and files in the current folder.
- Navigate into sub-directories; back button navigates up.
- File count and total size shown per folder.

### 7.9 Duplicates Screen (`DuplicatesScreen`)
- Groups files sharing the same MD5 hash.
- Shows group header: hash (truncated), count, total wasted space.
- Lists each duplicate's path, size, and date.
- Tap a duplicate to open its File Detail screen.

### 7.10 Sync Profiles Screen (`SyncProfilesScreen`)
- Lists all sync profiles as cards.
- Card shows: type icon (Telegram blue / Email red), name, type, interval, last sync time.
- Toggle switch for active/inactive.
- Actions per card: Sync Now (triggers one-time WorkManager request), View History, Edit, Delete.
- Delete confirmation dialog.
- FAB "Add Profile" navigates to Add/Edit screen.

### 7.11 Add / Edit Sync Profile (`AddSyncProfileScreen`)
- Profile name, type selector (Telegram / Email).
- **Telegram fields:** Bot Token, Chat ID.
- **Email fields:** SMTP Host, Port, Username, Password, From address, To address.
- Sync interval selector (Manual, 1h, 3h, 6h, 12h, 24h).
- File type multi-select filter (which file types to include in sync).
- "Since N days" filter (sync only recently modified files).
- Save validates required fields before writing to Room.
- If profile is active and interval > 0, schedules a periodic WorkManager sync.

### 7.12 Sync History Screen (`SyncHistoryScreen`)
- Lists sync run history for a given profile.
- Each card: status icon + color (green SUCCESS / orange PARTIAL / red FAILED / blue IN_PROGRESS), start timestamp, files synced count, files failed count, error message (if any).

### 7.13 Settings Screen (`SettingsScreen`)
- **Display:** Grid columns for Photos / Videos (2 / 3 / 4).
- **Theme:** Dark mode toggle.
- **App Lock:** Lock type selector (None / PIN / Biometric). PIN setup entry (SHA-256 hashed, stored in EncryptedSharedPreferences).
- **Scan:** Manual scan trigger, monitoring toggle.
- **Excluded Folders:** List + add/remove via `FolderBrowserScreen`.
- **Export:** "Export Catalog to CSV" — writes all file_entries to a timestamped CSV in `Downloads/FileVaultPro/`.
- **Sync:** WorkManager periodic scan enable/disable.
- **Battery:** Shortcut to battery optimization exemption settings.

### 7.14 Notification Center (`NotificationCenterScreen`)
- Displays in-app notifications from `NotificationStore` (in-memory singleton).
- Notification types: SCAN_COMPLETE, SYNC_COMPLETE, NEW_FILES, DUPLICATE_FOUND.
- Dismiss individual notification; "Clear All" button.
- Badge count shown on Dashboard.

### 7.15 App Lock Screen (`AppLockScreen`)
- 4-dot PIN indicator row.
- Custom 3×4 PIN pad with backspace key.
- Biometric fingerprint button (shown when lock type = BIOMETRIC).
- Validates entered PIN against SHA-256 hash stored in EncryptedSharedPreferences.
- Lockout counter: after 5 failed attempts, shows "Too many attempts" message.
- `LaunchedEffect` auto-triggers biometric prompt on screen entry when lock type is BIOMETRIC.

---

## 8. Home Screen Widget (`FileVaultWidget`)

- Built with Jetpack Glance (`GlanceAppWidget`).
- Shows: total file count, photo count, video count — all read from a DataStore snapshot.
- Tapping the widget opens `MainActivity` directly.
- Widget receiver (`FileVaultWidgetReceiver`) declared in `AndroidManifest.xml` with `AppWidgetProviderInfo` metadata.
- Refreshes on scan completion via `GlanceAppWidgetManager.requestPinAppWidget`.

---

## 9. CSV Catalog Export

- Triggered from Settings screen.
- Writes to: `Environment.DIRECTORY_DOWNLOADS / FileVaultPro / catalog_<timestamp>.csv`.
- Columns: `path, name, size_bytes, mime_type, file_type, date_modified, date_added, width, height, duration_ms, camera_make, camera_model, has_gps, md5_hash, is_sync_ignored, last_synced_at`.
- Header row included.
- File opened via a share intent after writing.

---

## 10. Duplicate Detection

- `FileRepository.getDuplicates()` queries Room for all non-deleted files with a non-null `md5_hash`.
- Groups by MD5; any group with count ≥ 2 is a `DuplicateGroup`.
- Returns list of `DuplicateGroup(hash, files, totalWastedBytes)`.
- `totalWastedBytes` = `(count − 1) × fileSize` (largest file excluded from "wasted" calculation).

---

## 11. Metadata Extraction

### EXIF (Photos)
Extracted via `androidx.exifinterface`:
- `TAG_IMAGE_WIDTH`, `TAG_IMAGE_LENGTH` (resolution)
- `TAG_ORIENTATION`
- `TAG_MAKE`, `TAG_MODEL` (camera)
- `TAG_GPS_LATITUDE` (presence indicates GPS)
- `TAG_DATETIME_ORIGINAL` / `TAG_DATETIME`

### Video
Extracted via `MediaMetadataRetriever`:
- `METADATA_KEY_DURATION`
- `METADATA_KEY_VIDEO_WIDTH`, `METADATA_KEY_VIDEO_HEIGHT`

### MIME Type
- Primary: `MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)`
- Fallbacks for APK, JSON, XML, Markdown, and binary files.

---

## 12. Security

- **PIN hashing:** SHA-256 via `java.security.MessageDigest`. Raw PIN is never stored.
- **Encrypted storage:** Credentials (SMTP password, Telegram bot token) stored via `EncryptedSharedPreferences` (AES-256 GCM via Android Keystore).
- **DataStore:** Non-sensitive preferences (theme, grid columns, onboarding state) stored in Proto DataStore.
- **Biometric:** `BiometricPrompt` API with `BiometricManager.Authenticators.BIOMETRIC_WEAK` check before triggering.

---

## 13. Architecture & Dependency Injection

### Layer Structure
```
presentation/          ← Compose screens + ViewModels
domain/
  model/               ← Pure Kotlin data models (FileEntry, SyncProfile, …)
  repository/          ← Repository interfaces
data/
  local/
    entity/            ← Room @Entity + mapper extensions
    dao/               ← Room @Dao interfaces
  repository/          ← Repository implementations
  preferences/         ← AppPreferences (DataStore)
  sync/                ← TelegramSyncImpl, EmailSyncImpl
di/                    ← Hilt @Module classes
observer/              ← MediaStoreObserver
service/               ← ScanForegroundService
worker/                ← ScanWorker, SyncWorker
widget/                ← GlanceAppWidget
util/                  ← FileUtils, NotificationStore
```

### Hilt Modules
- `DatabaseModule` — provides `AppDatabase`, all DAOs.
- `RepositoryModule` — binds `FileRepositoryImpl → FileRepository`, `SyncRepositoryImpl → SyncRepository`.
- `NetworkModule` — provides Retrofit instance for Telegram API, OkHttpClient.
- `PreferencesModule` — provides `AppPreferences`, `EncryptedSharedPreferences`.

### ViewModels (all `@HiltViewModel`)
- `DashboardViewModel` — stats, scan trigger, battery state.
- `PhotosViewModel` — photos flow, sort/filter/search, grid columns preference.
- `VideosViewModel` — same as Photos but filtered to VIDEO type.
- `FilesViewModel` — all files, type chip filter, sort/filter/search.
- `FileDetailViewModel` — single file detail, MD5 computation, sync-ignore toggle.
- `FolderBrowserViewModel` — directory navigation state.
- `DuplicatesViewModel` — duplicate groups flow.
- `SyncViewModel` — profiles CRUD, history, WorkManager scheduling, syncNow.
- `SettingsViewModel` — preferences read/write, scan triggers, excluded folders.
- `NotificationViewModel` — in-app notification list.

---

## 14. Navigation (`AppNavGraph`)

Routes defined as sealed class `Screen`:
- `Onboarding`, `Permission`, `Dashboard`, `Photos`, `Videos`, `Files`
- `FileDetail(path)`, `FolderBrowser(path)`, `Duplicates`
- `SyncProfiles`, `AddSyncProfile`, `EditSyncProfile(id)`, `SyncHistory(profileId)`
- `Settings`, `NotificationCenter`

Navigation handles:
- Initial route based on onboarding and permission state.
- App lock gate: if `appLockType != NONE`, shows `AppLockScreen` before `Dashboard`.
- Deep-link back-stack management.

---

## 15. Permissions Declared (`AndroidManifest.xml`)

| Permission | Purpose |
|---|---|
| `READ_MEDIA_IMAGES` | Read photos (Android 13+) |
| `READ_MEDIA_VIDEO` | Read videos (Android 13+) |
| `READ_MEDIA_AUDIO` | Read audio (Android 13+) |
| `READ_EXTERNAL_STORAGE` | Read storage (Android 12−) |
| `MANAGE_EXTERNAL_STORAGE` | All-files access for complete FS walk |
| `WRITE_EXTERNAL_STORAGE` | CSV export (Android 9−) |
| `INTERNET` | Telegram / SMTP sync |
| `FOREGROUND_SERVICE` | ScanForegroundService |
| `FOREGROUND_SERVICE_DATA_SYNC` | Android 14+ foreground service type |
| `RECEIVE_BOOT_COMPLETED` | Re-schedule workers on boot |
| `POST_NOTIFICATIONS` | Android 13+ notification permission |
| `USE_BIOMETRIC` | Biometric authentication |
| `USE_FINGERPRINT` | Fingerprint (legacy) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Battery optimization exemption hint |

---

## 16. Third-Party Dependencies

| Library | Purpose |
|---|---|
| Jetpack Compose BOM | UI framework |
| Material 3 | Design system |
| Hilt | Dependency injection |
| Room | Local database |
| WorkManager | Background task scheduling |
| DataStore (Proto) | Preferences storage |
| Navigation Compose | Screen navigation |
| EncryptedSharedPreferences | Secure key-value store |
| Biometric | Biometric auth |
| ExifInterface | EXIF metadata reading |
| Coil Compose | Image loading / thumbnails |
| Glance (Widget) | Home screen widget |
| Retrofit + OkHttp | Telegram Bot API HTTP client |
| android-mail (JavaMail) | SMTP email sending |
| Accompanist Permissions | Compose permission helpers |
| Kotlinx Coroutines | Async/Flow |
| Kotlinx Serialization | JSON encoding for sync filter types |

---

## 17. Known Limitations / Not Yet Implemented

The following items from the original 45-feature spec are noted as out of scope for this version:

| Feature | Status |
|---|---|
| Cloud metadata backup (WebDAV / Nextcloud) | Not implemented |
| Root file access (su / Shizuku) | Not implemented |
| JSON catalog export | Not implemented (CSV only) |
| Catalog import from JSON/CSV | Not implemented |
| NotificationStore persistence across restarts | In-memory only; cleared on app restart |
| Thumbnail caching to disk (beyond Coil defaults) | Uses Coil default cache |
| Multi-account Telegram (multiple bot tokens per profile) | Single bot token per profile |

---

*Documentation generated March 18, 2026. Reflects the full codebase as audited.*
