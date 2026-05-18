package com.astrbot.android.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.astrbot.android.data.db.AppPreferenceDao
import com.astrbot.android.data.db.AppPreferenceEntity
import com.astrbot.android.data.db.SavedQqAccountDao
import com.astrbot.android.data.db.SavedQqAccountEntity
import com.astrbot.android.feature.qq.data.NapCatLoginLocalStore
import com.astrbot.android.feature.qq.data.NapCatLoginRepository
import com.astrbot.android.feature.qq.data.NapCatLoginService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

internal object NapCatLoginTestFixtures {
    val loginService: NapCatLoginService = NapCatLoginService()
    val repository: NapCatLoginRepository = NapCatLoginRepository(
        loginService = loginService,
        localStore = NapCatLoginLocalStore(
            appContext = TestContext,
            appPreferenceDao = InMemoryAppPreferenceDao(),
            savedQqAccountDao = InMemorySavedQqAccountDao(),
        ),
    )

    fun reset() {
        loginService.resetForTests()
        repository.resetQrRefreshGuardsForTests()
    }
}

private object TestContext : ContextWrapper(null) {
    private val root = java.io.File(System.getProperty("java.io.tmpdir"), "elymbot-napcat-login-tests")
        .apply { mkdirs() }
    private val preferences = mutableMapOf<String, SharedPreferences>()

    override fun getApplicationContext(): Context = this

    override fun getFilesDir(): java.io.File = root

    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
        return preferences.getOrPut(name.orEmpty()) { InMemorySharedPreferences() }
    }
}

private class InMemoryAppPreferenceDao : AppPreferenceDao {
    private val values = linkedMapOf<String, String>()

    override fun observeValue(key: String) = flowOf(values[key])

    override suspend fun getValue(key: String): String? = values[key]

    override suspend fun upsert(entity: AppPreferenceEntity) {
        values[entity.key] = entity.value
    }
}

private class InMemorySavedQqAccountDao : SavedQqAccountDao {
    private val accounts = linkedMapOf<String, SavedQqAccountEntity>()
    private val flow = MutableStateFlow<List<SavedQqAccountEntity>>(emptyList())

    override fun observeAccounts() = flow

    override suspend fun listAccounts(): List<SavedQqAccountEntity> = accounts.values.toList()

    override suspend fun upsertAll(entities: List<SavedQqAccountEntity>) {
        entities.forEach { entity -> accounts[entity.uin] = entity }
        refresh()
    }

    override suspend fun deleteMissing(uins: List<String>) {
        accounts.keys.toList().filterNot(uins::contains).forEach(accounts::remove)
        refresh()
    }

    override suspend fun clearAll() {
        accounts.clear()
        refresh()
    }

    private fun refresh() {
        flow.value = accounts.values.toList()
    }
}

private class InMemorySharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val staged = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { staged[key.orEmpty()] = value }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor =
            apply { staged[key.orEmpty()] = values?.toSet() }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { staged[key.orEmpty()] = value }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { staged[key.orEmpty()] = value }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { staged[key.orEmpty()] = value }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { staged[key.orEmpty()] = value }
        override fun remove(key: String?): SharedPreferences.Editor = apply { staged[key.orEmpty()] = null }
        override fun clear(): SharedPreferences.Editor = apply { clear = true }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            if (clear) values.clear()
            staged.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
        }
    }
}
