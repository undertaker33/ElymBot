package com.astrbot.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.material3.MaterialTheme
import com.astrbot.android.di.ConfigViewModelDependencies
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.TtsVoiceReferenceAsset
import com.astrbot.android.ui.screen.ConfigScreen
import com.astrbot.android.ui.viewmodel.ConfigViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test

class ConfigScreenSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun configScreenRendersExistingProfileAndAddAction() {
        val dependencies = FakeConfigViewModelDependencies()

        composeRule.setContent {
            MaterialTheme {
                ConfigScreen(
                    selectedConfigIds = emptySet(),
                    onSelectedConfigIdsChange = {},
                    onOpenProfile = {},
                    configViewModel = ConfigViewModel(dependencies),
                )
            }
        }

        composeRule.onNodeWithTag("config-search-field").assertIsDisplayed()
        composeRule.onNodeWithTag("config-add-fab").assertIsDisplayed()
    }

    @Test
    fun configScreenCreatesProfileFromFab() {
        val dependencies = FakeConfigViewModelDependencies()

        composeRule.setContent {
            MaterialTheme {
                ConfigScreen(
                    selectedConfigIds = emptySet(),
                    onSelectedConfigIdsChange = {},
                    onOpenProfile = {},
                    configViewModel = ConfigViewModel(dependencies),
                )
            }
        }

        composeRule.onNodeWithTag("config-add-fab").performClick()

        composeRule.onNodeWithText("Created Config 2", useUnmergedTree = true).assertIsDisplayed()
    }
}

private class FakeConfigViewModelDependencies : ConfigViewModelDependencies {
    private val profilesState = MutableStateFlow(
        listOf(
            ConfigProfile(
                id = "config-1",
                name = "Smoke Config",
                defaultChatProviderId = "provider-1",
            ),
        ),
    )
    private val selectedIdState = MutableStateFlow("config-1")
    private val providersState = MutableStateFlow(
        listOf(
            ProviderProfile(
                id = "provider-1",
                name = "Smoke Provider",
                baseUrl = "https://example.com",
                model = "demo",
                providerType = ProviderType.OPENAI_COMPATIBLE,
                apiKey = "key",
                capabilities = setOf(ProviderCapability.CHAT),
            ),
        ),
    )
    private val botsState = MutableStateFlow(listOf(BotProfile(configProfileId = "config-1")))
    private val voiceAssetsState = MutableStateFlow<List<TtsVoiceReferenceAsset>>(emptyList())

    override val configProfiles: StateFlow<List<ConfigProfile>> = profilesState
    override val selectedConfigProfileId: StateFlow<String> = selectedIdState
    override val providers: StateFlow<List<ProviderProfile>> = providersState
    override val bots: StateFlow<List<BotProfile>> = botsState
    override val ttsVoiceAssets: StateFlow<List<TtsVoiceReferenceAsset>> = voiceAssetsState

    override fun select(profileId: String) {
        selectedIdState.value = profileId
    }

    override fun save(profile: ConfigProfile) {
        profilesState.value = profilesState.value.map { existing ->
            if (existing.id == profile.id) profile else existing
        }
    }

    override fun create(): ConfigProfile {
        val created = ConfigProfile(
            id = "config-${profilesState.value.size + 1}",
            name = "Created Config ${profilesState.value.size + 1}",
            defaultChatProviderId = "provider-1",
        )
        profilesState.value = profilesState.value + created
        selectedIdState.value = created.id
        return created
    }

    override fun delete(profileId: String): String {
        profilesState.value = profilesState.value.filterNot { it.id == profileId }
        val fallback = profilesState.value.firstOrNull()?.id.orEmpty()
        selectedIdState.value = fallback
        return fallback
    }

    override fun replaceConfigBinding(deletedConfigId: String, fallbackConfigId: String) = Unit

    override fun resolve(profileId: String): ConfigProfile {
        return profilesState.value.first { it.id == profileId }
    }
}
