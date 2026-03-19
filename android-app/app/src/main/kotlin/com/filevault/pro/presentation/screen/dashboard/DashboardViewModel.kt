package com.filevault.pro.presentation.screen.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.filevault.pro.data.preferences.AppPreferences
import com.filevault.pro.domain.model.CatalogStats
import com.filevault.pro.domain.model.FileEntry
import com.filevault.pro.domain.model.FileFilter
import com.filevault.pro.domain.model.SortField
import com.filevault.pro.domain.model.SortOrder
import com.filevault.pro.domain.model.SyncProfile
import com.filevault.pro.domain.repository.FileRepository
import com.filevault.pro.domain.repository.SyncRepository
import com.filevault.pro.worker.ScanWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.asFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileRepository: FileRepository,
    private val syncRepository: SyncRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    companion object {
        private const val SCAN_WORK_TAG = "manual_scan"
    }

    val stats: StateFlow<CatalogStats?> = fileRepository.getStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val syncProfiles: StateFlow<List<SyncProfile>> = syncRepository.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentFiles: StateFlow<List<FileEntry>> = fileRepository.getAllFiles(
        SortOrder(SortField.DATE_ADDED, false),
        FileFilter()
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lastScanAt: StateFlow<Long?> = appPreferences.lastScanAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgressCount = MutableStateFlow<Int?>(null)
    val scanProgressCount: StateFlow<Int?> = _scanProgressCount.asStateFlow()

    private val _scanStage = MutableStateFlow<String?>(null)
    val scanStage: StateFlow<String?> = _scanStage.asStateFlow()

    init {
        WorkManager.getInstance(context)
            .getWorkInfosByTagLiveData(SCAN_WORK_TAG)
            .asFlow()
            .map { workInfos ->
                workInfos.firstOrNull { it.state == androidx.work.WorkInfo.State.RUNNING ||
                        it.state == androidx.work.WorkInfo.State.ENQUEUED }
                    ?: workInfos.maxByOrNull { it.runAttemptCount }
            }
            .distinctUntilChangedBy { it?.id }
            .onEach { workInfo ->
                val running = workInfo?.state?.isFinished == false
                _isScanning.value = running
                _scanProgressCount.value = workInfo?.progress?.getInt(ScanWorker.KEY_PROGRESS_COUNT, 0)
                _scanStage.value = workInfo?.progress?.getString(ScanWorker.KEY_PROGRESS_STAGE)
            }
            .launchIn(viewModelScope)
    }

    fun triggerScan() {
        viewModelScope.launch {
            _isScanning.value = true
            val request = OneTimeWorkRequestBuilder<ScanWorker>()
                .setInputData(workDataOf(ScanWorker.KEY_IS_INITIAL to false))
                .addTag(SCAN_WORK_TAG)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    fun triggerInitialScan() {
        viewModelScope.launch {
            _isScanning.value = true
            val request = OneTimeWorkRequestBuilder<ScanWorker>()
                .setInputData(workDataOf(ScanWorker.KEY_IS_INITIAL to true))
                .addTag("initial_scan")
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
