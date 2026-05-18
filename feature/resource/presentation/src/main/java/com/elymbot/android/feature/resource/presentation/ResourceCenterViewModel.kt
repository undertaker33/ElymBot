package com.elymbot.android.feature.resource.presentation

import androidx.lifecycle.ViewModel
import com.elymbot.android.feature.resource.domain.ResourceCenterPort
import com.elymbot.android.feature.resource.domain.ResourceCompatibilityUseCase
import com.elymbot.android.model.ResourceCenterItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class ResourceCenterViewModel @Inject constructor(
    port: ResourceCenterPort,
    compatibilityUseCase: ResourceCompatibilityUseCase,
) : ViewModel() {
    val resources: StateFlow<List<ResourceCenterItem>> = port.resources

    val controller = ResourceCenterPresentationController(
        port = port,
        compatibilityUseCase = compatibilityUseCase,
    )
}
