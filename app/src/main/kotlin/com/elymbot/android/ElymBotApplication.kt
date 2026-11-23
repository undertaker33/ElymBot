package com.elymbot.android

import android.app.Application
import com.elymbot.android.data.ElymBotDataRepository
import com.elymbot.android.data.RoomHistoryStore
import com.elymbot.android.data.RoomModelServiceConnectionProvider
import com.elymbot.android.runtime.pluginpackage.AppHostManagementUiService
import com.elymbot.android.runtime.pluginpackage.OfficialAppUpdateService
import com.elymbot.android.runtime.pluginpackage.OfficialPluginDistributionConfiguration
import com.elymbot.android.runtime.pluginpackage.OfficialPluginDistributionConfigurationLoad
import com.elymbot.android.runtime.pluginpackage.OfficialPluginDistributionRepository
import com.elymbot.android.runtime.pluginpackage.PluginRuntimeManager
import com.elymbot.android.session.ScheduledTaskHost
import com.elymbot.android.session.ScheduledTaskSystemScheduler
import com.elymbot.android.session.AppToolAccessPolicy
import com.elymbot.android.session.ElymBotSessionService
import com.elymbot.android.ui.ElymBotUiService
import com.elymbot.core.agent.ReActAgentRuntime
import com.elymbot.core.command.DefaultSlashCommandParser
import com.elymbot.core.context.ContextCompiler
import com.elymbot.core.contract.ToolRegistration
import com.elymbot.core.model.OpenAiCompatibleModelGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ElymBotApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pluginRuntimeManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PluginRuntimeManager(applicationContext)
    }
    private val pluginDistributionConfigurationLoad by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OfficialPluginDistributionConfiguration.load(applicationContext)
    }
    private val pluginDistributionRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val configuration = when (val load = pluginDistributionConfigurationLoad) {
            is OfficialPluginDistributionConfigurationLoad.Ready -> load.configuration
            OfficialPluginDistributionConfigurationLoad.NotConfigured,
            is OfficialPluginDistributionConfigurationLoad.Invalid -> null
        }
        OfficialPluginDistributionRepository(applicationContext, configuration)
    }
    private val appHostManagementUiService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppHostManagementUiService(
            context = applicationContext,
            runtimeManager = pluginRuntimeManager,
            configurationLoad = pluginDistributionConfigurationLoad,
            distributionRepository = pluginDistributionRepository,
            appUpdateService = OfficialAppUpdateService(applicationContext),
            scope = applicationScope,
        )
    }

    val uiService: ElymBotUiService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val repository = ElymBotDataRepository(
            context = applicationContext,
            beforeInitialize = {
                appHostManagementUiService.restorePlugins()
            },
        )
        val connectionProvider = RoomModelServiceConnectionProvider(repository)
        val modelGateway = OpenAiCompatibleModelGateway(connectionProvider)
        val historyStore = RoomHistoryStore(repository.database)
        val contextCompiler = ContextCompiler(historyStore, historyStore, modelGateway)
        val agentRuntime = ReActAgentRuntime(modelGateway, contextCompiler, historyStore)
        val scheduledTaskHost = ScheduledTaskHost(
            repository = repository,
            pluginRuntimeManager = pluginRuntimeManager,
            systemScheduler = ScheduledTaskSystemScheduler(applicationContext),
        )

        ElymBotSessionService(
            context = applicationContext,
            repository = repository,
            connectionProvider = connectionProvider,
            modelGateway = modelGateway,
            agentRuntime = agentRuntime,
            commandParser = DefaultSlashCommandParser(),
            scope = applicationScope,
            scheduledTaskHost = scheduledTaskHost,
            appHostManagement = appHostManagementUiService,
            toolRegistrations = {
                pluginRuntimeManager.activeRegistrations() + ToolRegistration(appHostManagementUiService.catalogTool)
            },
            toolAccessPolicy = AppToolAccessPolicy(pluginRuntimeManager),
        )
    }

    internal suspend fun processScheduledTaskWake(): Boolean =
        (uiService as? ElymBotSessionService)?.processScheduledTaskWake() ?: false
}
