package com.clearlens.app

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clearlens.app.ui.ClearLensApp
import com.clearlens.app.ui.ClearLensTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ClearLensViewModel by viewModels()
    private var hasPermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    private val trashLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onTrashCompleted()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasPermission = checkPhotoPermission()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            ClearLensTheme {
                ClearLensApp(
                    hasPermission = hasPermission,
                    state = state,
                    requestPermission = { permissionLauncher.launch(photoPermission()) },
                    startScan = viewModel::startScan,
                    cancelScan = viewModel::cancelScan,
                    togglePhoto = viewModel::togglePhoto,
                    selectGroup = viewModel::selectGroup,
                    protectFolders = viewModel::protectFolders,
                    setFilter = viewModel::setFilter,
                    moveSelectedToTrash = ::moveSelectedToTrash
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasPermission = checkPhotoPermission()
    }

    private fun moveSelectedToTrash() {
        val uris = viewModel.state.value.selectedPhotos.map { it.uri }
        if (uris.isEmpty()) return
        try {
            val pendingIntent: PendingIntent = MediaStore.createTrashRequest(contentResolver, uris, true)
            trashLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        } catch (_: IntentSender.SendIntentException) {
            // Android's confirmation sheet could not be opened; nothing is deleted.
        }
    }

    private fun photoPermission(): String = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    private fun checkPhotoPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, photoPermission()) == PackageManager.PERMISSION_GRANTED
}
