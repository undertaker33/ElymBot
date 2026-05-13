package com.astrbot.android.feature.config.data

import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.config.domain.model.ConfigProfile
import kotlinx.coroutines.flow.StateFlow

object FeatureConfigRepository {
    const val PREF_SELECTED_PROFILE_ID = "selected_config_profile_id"
    const val DEFAULT_CONFIG_ID = ConfigRepositoryPort.DEFAULT_CONFIG_ID

    @Volatile
    private var delegate: FeatureConfigRepositoryStore? = null

    internal fun installDelegate(store: FeatureConfigRepositoryStore) {
        delegate = store
    }

    private fun repository(): FeatureConfigRepositoryStore {
        return checkNotNull(delegate) {
            "FeatureConfigRepository test facade was accessed before a test installed FeatureConfigRepositoryStore."
        }
    }

    val profiles: StateFlow<List<ConfigProfile>>
        get() = repository().profiles

    val selectedProfileId: StateFlow<String>
        get() = repository().selectedProfileId

    fun select(profileId: String) = repository().select(profileId)

    fun save(profile: ConfigProfile): ConfigProfile = repository().save(profile)

    fun create(name: String = "New Config"): ConfigProfile = repository().create(name)

    fun delete(profileId: String): String = repository().delete(profileId)

    fun resolve(profileId: String): ConfigProfile = repository().resolve(profileId)

    fun resolveExistingId(profileId: String?): String = repository().resolveExistingId(profileId)

    fun snapshotProfiles(): List<ConfigProfile> = repository().snapshotProfiles()

    fun restoreProfiles(
        profiles: List<ConfigProfile>,
        selectedProfileId: String?,
    ) = repository().restoreProfiles(profiles, selectedProfileId)
}
