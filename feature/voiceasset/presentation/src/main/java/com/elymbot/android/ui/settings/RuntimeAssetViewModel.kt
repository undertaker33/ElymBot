package com.elymbot.android.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elymbot.android.feature.voiceasset.api.RuntimeAssetPort
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RuntimeAssetViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val runtimeAssetPort: RuntimeAssetPort,
) : ViewModel() {
    val state = runtimeAssetPort.state

    fun refresh() {
        runtimeAssetPort.refresh(appContext)
    }

    fun downloadAsset(assetId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runtimeAssetPort.downloadAsset(appContext, assetId)
        }
    }

    fun clearAsset(assetId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runtimeAssetPort.clearAsset(appContext, assetId)
        }
    }

    fun downloadOnDeviceTtsModel(modelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runtimeAssetPort.downloadOnDeviceTtsModel(appContext, modelId)
        }
    }

    fun clearOnDeviceTtsModel(modelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runtimeAssetPort.clearOnDeviceTtsModel(appContext, modelId)
        }
    }
}
