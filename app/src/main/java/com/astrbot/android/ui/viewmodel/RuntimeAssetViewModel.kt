package com.astrbot.android.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.android.data.RuntimeAssetRepository
import com.astrbot.android.di.hilt.RuntimeAssetStateFlow
import com.astrbot.android.model.RuntimeAssetState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RuntimeAssetViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    @RuntimeAssetStateFlow val state: StateFlow<RuntimeAssetState>,
) : ViewModel() {

    fun refresh() {
        RuntimeAssetRepository.refresh(appContext)
    }

    fun downloadAsset(assetId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            RuntimeAssetRepository.downloadAsset(appContext, assetId)
        }
    }

    fun clearAsset(assetId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            RuntimeAssetRepository.clearAsset(appContext, assetId)
        }
    }

    fun downloadOnDeviceTtsModel(modelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            RuntimeAssetRepository.downloadOnDeviceTtsModel(appContext, modelId)
        }
    }

    fun clearOnDeviceTtsModel(modelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            RuntimeAssetRepository.clearOnDeviceTtsModel(appContext, modelId)
        }
    }
}
