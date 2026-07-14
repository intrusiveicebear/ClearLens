package com.clearlens.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clearlens.app.data.PhotoRepository
import com.clearlens.app.model.FindingGroup
import com.clearlens.app.model.FindingType
import com.clearlens.app.model.ScanProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ClearLensUiState(
    val isScanning: Boolean = false,
    val hasScanned: Boolean = false,
    val progress: ScanProgress = ScanProgress(),
    val findings: List<FindingGroup> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val filter: FindingType? = null,
    val errorMessage: String? = null
) {
    val filteredFindings: List<FindingGroup>
        get() = if (filter == null) findings else findings.filter { it.type == filter }

    val selectedPhotos
        get() = findings.flatMap { it.photos }.distinctBy { it.id }.filter { it.id in selectedIds }

    val selectedBytes: Long
        get() = selectedPhotos.sumOf { it.sizeBytes }
}

class ClearLensViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PhotoRepository(application)
    private val preferences = application.getSharedPreferences("clearlens", 0)
    private val _state = MutableStateFlow(ClearLensUiState())
    val state: StateFlow<ClearLensUiState> = _state.asStateFlow()
    private var scanJob: Job? = null

    fun startScan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.value = ClearLensUiState(isScanning = true)
            try {
                val excluded = preferences.getStringSet("excluded_folders", emptySet()).orEmpty()
                val groups = repository.scan(excluded) { progress ->
                    _state.value = _state.value.copy(progress = progress)
                }
                val recommended = groups.flatMapTo(mutableSetOf()) { it.recommendedDeleteIds }
                _state.value = ClearLensUiState(
                    hasScanned = true,
                    findings = groups,
                    selectedIds = recommended,
                    progress = ScanProgress(1, 1, "Scan complete")
                )
            } catch (_: CancellationException) {
                _state.value = _state.value.copy(isScanning = false)
            } catch (error: Exception) {
                _state.value = ClearLensUiState(
                    hasScanned = true,
                    errorMessage = error.message ?: "ClearLens could not finish this scan."
                )
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
    }

    fun togglePhoto(id: Long) {
        val selected = _state.value.selectedIds.toMutableSet()
        if (!selected.add(id)) selected.remove(id)
        _state.value = _state.value.copy(selectedIds = selected)
    }

    fun selectGroup(group: FindingGroup, select: Boolean) {
        val selected = _state.value.selectedIds.toMutableSet()
        if (select) selected.addAll(group.photos.map { it.id }) else selected.removeAll(group.photos.map { it.id }.toSet())
        _state.value = _state.value.copy(selectedIds = selected)
    }

    fun setFilter(type: FindingType?) {
        _state.value = _state.value.copy(filter = type)
    }

    fun onTrashCompleted() {
        val removed = _state.value.selectedIds
        val updated = _state.value.findings.mapNotNull { group ->
            val remaining = group.photos.filterNot { it.id in removed }
            if (remaining.isEmpty()) null else group.copy(
                photos = remaining,
                recommendedDeleteIds = group.recommendedDeleteIds - removed
            )
        }
        _state.value = _state.value.copy(findings = updated, selectedIds = emptySet())
    }

    fun protectFolders(group: FindingGroup) {
        val folders = group.photos.map { it.relativePath }.filter { it.isNotBlank() }.toSet()
        if (folders.isEmpty()) return
        val existing = preferences.getStringSet("excluded_folders", emptySet()).orEmpty()
        preferences.edit().putStringSet("excluded_folders", existing + folders).apply()
        val protectedIds = _state.value.findings
            .flatMap { it.photos }
            .filter { photo -> folders.any { photo.relativePath.startsWith(it, ignoreCase = true) } }
            .mapTo(mutableSetOf()) { it.id }
        val remaining = _state.value.findings.mapNotNull { finding ->
            val photos = finding.photos.filterNot { it.id in protectedIds }
            if (photos.isEmpty()) null else finding.copy(
                photos = photos,
                recommendedDeleteIds = finding.recommendedDeleteIds - protectedIds
            )
        }
        _state.value = _state.value.copy(
            findings = remaining,
            selectedIds = _state.value.selectedIds - protectedIds
        )
    }
}
