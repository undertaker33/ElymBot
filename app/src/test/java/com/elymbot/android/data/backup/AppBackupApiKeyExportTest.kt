package com.elymbot.android.core.db.backup

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import com.elymbot.android.feature.settings.api.backup.AppBackupDataPort
import com.elymbot.android.feature.settings.api.backup.AppBackupExternalState
import com.elymbot.android.feature.voiceasset.api.TtsVoiceAssetPort
import com.elymbot.android.feature.voiceasset.api.VoiceAssetImportResult
import com.elymbot.android.feature.voiceasset.api.model.TtsVoiceReferenceAsset
import com.elymbot.android.model.BotProfile
import com.elymbot.android.model.ConfigProfile
import com.elymbot.android.model.PersonaProfile
import com.elymbot.android.model.ProviderCapability
import com.elymbot.android.model.ProviderProfile
import com.elymbot.android.model.ProviderType
import com.elymbot.android.model.chat.ConversationSession
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AppBackupApiKeyExportTest {
    @Test
    fun `full backup clears provider api keys by default`() = runBlocking {
        val repository = repositoryWithProviders(listOf(provider(apiKey = "real-secret-key")))

        val backup = repository.createBackup().getOrThrow()
        val source = repository.prepareImportFromBackup(backup.id).getOrThrow()
        val providerJson = source.manifest.modules.providers.records.single() as JSONObject

        assertEquals("", providerJson.getString("apiKey"))
    }

    @Test
    fun `full backup includes provider api keys when explicitly requested`() = runBlocking {
        val repository = repositoryWithProviders(listOf(provider(apiKey = "real-secret-key")))

        val backup = repository.createBackup(
            options = AppBackupCreateOptions(includeProviderApiKeys = true),
        ).getOrThrow()
        val source = repository.prepareImportFromBackup(backup.id).getOrThrow()
        val providerJson = source.manifest.modules.providers.records.single() as JSONObject

        assertEquals("real-secret-key", providerJson.getString("apiKey"))
    }

    @Test
    fun `provider module backup clears provider api keys by default`() = runBlocking {
        val repository = repositoryWithProviders(listOf(provider(apiKey = "real-secret-key")))

        val backup = repository.createModuleBackup(AppBackupModuleKind.PROVIDERS).getOrThrow()
        val source = repository.prepareModuleImportFromBackup(AppBackupModuleKind.PROVIDERS, backup.id).getOrThrow()
        val providerJson = source.manifest.modules.providers.records.single() as JSONObject

        assertEquals("", providerJson.getString("apiKey"))
    }

    @Test
    fun `provider module backup includes provider api keys when explicitly requested`() = runBlocking {
        val repository = repositoryWithProviders(listOf(provider(apiKey = "real-secret-key")))

        val backup = repository.createModuleBackup(
            module = AppBackupModuleKind.PROVIDERS,
            options = AppBackupCreateOptions(includeProviderApiKeys = true),
        ).getOrThrow()
        val source = repository.prepareModuleImportFromBackup(AppBackupModuleKind.PROVIDERS, backup.id).getOrThrow()
        val providerJson = source.manifest.modules.providers.records.single() as JSONObject

        assertEquals("real-secret-key", providerJson.getString("apiKey"))
    }

    private fun repositoryWithProviders(providers: List<ProviderProfile>): AppBackupRepository {
        val root = Files.createTempDirectory("app-backup-api-key-test").toFile()
        return AppBackupRepository(
            context = TestContext(root),
            dataPort = FakeBackupDataPort(providers),
            ttsVoiceAssetPort = FakeTtsVoiceAssetPort(),
        )
    }

    private fun provider(apiKey: String): ProviderProfile {
        return ProviderProfile(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1",
            model = "model",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = apiKey,
            capabilities = setOf(ProviderCapability.CHAT),
        )
    }
}

private class TestContext(
    private val root: File,
) : ContextWrapper(null) {
    override fun getApplicationContext(): Context = this

    override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }

    override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
}

private class FakeBackupDataPort(
    private val providers: List<ProviderProfile>,
) : AppBackupDataPort {
    override fun snapshotBots(): List<BotProfile> = emptyList()

    override fun snapshotProviders(): List<ProviderProfile> = providers

    override fun snapshotPersonas(): List<PersonaProfile> = emptyList()

    override fun snapshotConfigs(): List<ConfigProfile> = emptyList()

    override fun snapshotConversations(): List<ConversationSession> = emptyList()

    override fun snapshotExternalState(): AppBackupExternalState = AppBackupExternalState()

    override suspend fun restoreBots(profiles: List<BotProfile>, selectedBotId: String) = Unit

    override fun restoreProviders(profiles: List<ProviderProfile>) = Unit

    override fun restorePersonas(profiles: List<PersonaProfile>) = Unit

    override fun restoreConfigs(profiles: List<ConfigProfile>, selectedConfigId: String) = Unit

    override suspend fun restoreConversations(sessions: List<ConversationSession>) = Unit

    override fun restoreQqLoginState(quickLoginUin: String, savedAccounts: List<com.elymbot.android.model.SavedQqAccount>) = Unit
}

private class FakeTtsVoiceAssetPort : TtsVoiceAssetPort {
    override val assets: StateFlow<List<TtsVoiceReferenceAsset>> = MutableStateFlow(emptyList())

    override fun listVoiceChoicesFor(providerId: String?): List<Pair<String, String>> = emptyList()

    override fun importReferenceAudio(
        context: Context,
        sourceUri: Uri,
        name: String,
        assetId: String?,
    ): VoiceAssetImportResult {
        error("not used")
    }

    override fun saveProviderBinding(
        assetId: String,
        providerId: String,
        providerTypeName: String,
        model: String,
        voiceId: String,
        displayName: String,
    ) = Unit

    override fun renameBinding(assetId: String, bindingId: String, displayName: String) = Unit

    override fun clearReferenceAudio(assetId: String) = Unit

    override fun deleteReferenceClip(assetId: String, clipId: String) = Unit

    override fun deleteBinding(assetId: String, bindingId: String) = Unit

    override fun snapshotAssets(): List<TtsVoiceReferenceAsset> = emptyList()

    override fun restoreAssets(assets: List<TtsVoiceReferenceAsset>) = Unit
}
