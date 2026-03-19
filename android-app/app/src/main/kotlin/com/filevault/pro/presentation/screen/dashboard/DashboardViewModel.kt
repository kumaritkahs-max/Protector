package com.filevault.pro.presentation.screen.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filevault.pro.data.preferences.AppPreferences
import com.filevault.pro.domain.model.CatalogStats
import com.filevault.pro.domain.model.FileEntry
import com.filevault.pro.domain.model.FileFilter
import com.filevault.pro.domain.model.SortField
import com.filevault.pro.domain.model.SortOrder
import com.filevault.pro.domain.model.SyncProfile
import com.filevault.pro.domain.repository.FileRepository
import com.filevault.pro.domain.repository.SyncRepository
import com.filevault.pro.service.ScanForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    val initialScanDone: StateFlow<Boolean> = appPreferences.initialScanDone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isScanning: StateFlow<Boolean> = fileRepository.isScanRunning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val scanProgressCount: StateFlow<Int?> = fileRepository.scanSavedCount
        .map<Int, Int?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val scanStage: StateFlow<String?> = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            val done = appPreferences.initialScanDone.first()
            if (!done) {
                triggerInitialScan()
            }
        }
    }

    fun triggerScan() {
        ScanForegroundService.startScan(context)
    }

    fun triggerInitialScan() {
        ScanForegroundService.startScan(context)
    }
}
