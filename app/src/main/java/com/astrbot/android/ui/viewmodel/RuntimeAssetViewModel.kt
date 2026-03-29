package com.astrbot.android.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.android.di.DefaultRuntimeAssetViewModelDependencies
import com.astrbot.android.di.RuntimeAssetViewModelDependencies
import com.astrbot.android.model.RuntimeAssetState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RuntimeAssetViewModel(
    application: Application,
    private val dependencies: RuntimeAssetViewModelDependencies = DefaultRuntimeAssetViewModelDependencies(application),
) : ViewModel() {
    val state: StateFlow<RuntimeAssetState> = dependencies.state

    fun refresh() {
        dependencies.refresh()
    }

    fun downloadAsset(assetId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dependencies.downloadAsset(assetId)
        }
    }

    fun clearAsset(assetId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dependencies.clearAsset(assetId)
        }
    }

    fun downloadOnDeviceTtsModel(modelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dependencies.downloadOnDeviceTtsModel(modelId)
        }
    }

    fun clearOnDeviceTtsModel(modelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dependencies.clearOnDeviceTtsModel(modelId)
        }
    }
}
