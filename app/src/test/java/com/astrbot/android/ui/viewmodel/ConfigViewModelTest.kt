package com.astrbot.android.ui.viewmodel

import com.astrbot.android.MainDispatcherRule
import com.astrbot.android.di.ConfigViewModelDependencies
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.TtsVoiceReferenceAsset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConfigViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    /**
     * Task10 Phase3 – Task A contract:
     * ConfigViewModel.delete() must delegate to a single atomic entry point,
     * NOT chain delete() + replaceConfigBinding() from the presentation layer.
     */
    @Test
    fun delete_delegates_to_single_entry_point_not_separate_steps() = runTest(dispatcher) {
        val deps = FakeConfigDependencies()
        val viewModel = ConfigViewModel(deps)

        viewModel.delete("config-1")

        assertTrue("delete() must defer work to viewModelScope", deps.deleteConfigProfileIds.isEmpty())
        advanceUntilIdle()
        assertEquals("delete() must call deleteConfigProfile() once", listOf("config-1"), deps.deleteConfigProfileIds)
        assertTrue("delete() must NOT call delete() directly", deps.directDeleteIds.isEmpty())
        assertEquals("delete() must NOT call replaceConfigBinding() directly", 0, deps.replaceBindingCalls)
    }

    @Test
    fun deleteConfigProfile_receives_the_correct_profile_id() = runTest(dispatcher) {
        val deps = FakeConfigDependencies()
        val viewModel = ConfigViewModel(deps)

        viewModel.delete("my-special-config")

        assertTrue(deps.deleteConfigProfileIds.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf("my-special-config"), deps.deleteConfigProfileIds)
    }

    private class FakeConfigDependencies : ConfigViewModelDependencies {
        override val configProfiles: StateFlow<List<ConfigProfile>> =
            MutableStateFlow(listOf(ConfigProfile(id = "config-1"), ConfigProfile(id = "config-2")))
        override val selectedConfigProfileId: StateFlow<String> = MutableStateFlow("config-1")
        override val providers: StateFlow<List<ProviderProfile>> = MutableStateFlow(emptyList())
        override val bots: StateFlow<List<BotProfile>> = MutableStateFlow(emptyList())
        override val ttsVoiceAssets: StateFlow<List<TtsVoiceReferenceAsset>> = MutableStateFlow(emptyList())

        val deleteConfigProfileIds = mutableListOf<String>()
        val directDeleteIds = mutableListOf<String>()
        var replaceBindingCalls = 0

        override fun select(profileId: String) = Unit

        override fun save(profile: ConfigProfile) = Unit

        override fun create(): ConfigProfile = ConfigProfile(id = "new-config")

        override fun delete(profileId: String): String {
            directDeleteIds += profileId
            return configProfiles.value.firstOrNull { it.id != profileId }?.id ?: profileId
        }

        override fun replaceConfigBinding(deletedConfigId: String, fallbackConfigId: String) {
            replaceBindingCalls++
        }

        override suspend fun deleteConfigProfile(profileId: String) {
            deleteConfigProfileIds += profileId
        }

        override fun resolve(profileId: String): ConfigProfile {
            return configProfiles.value.firstOrNull { it.id == profileId } ?: ConfigProfile(id = profileId)
        }
    }
}
