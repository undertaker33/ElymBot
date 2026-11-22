package com.elymbot.android

import android.app.Application
import com.elymbot.android.data.ElymBotDataRepository
import com.elymbot.android.data.RoomHistoryStore
import com.elymbot.android.data.RoomModelServiceConnectionProvider
import com.elymbot.android.session.ElymBotSessionService
import com.elymbot.android.ui.ElymBotUiService
import com.elymbot.core.agent.ReActAgentRuntime
import com.elymbot.core.command.DefaultSlashCommandParser
import com.elymbot.core.context.ContextCompiler
import com.elymbot.core.model.OpenAiCompatibleModelGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ElymBotApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val uiService: ElymBotUiService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val repository = ElymBotDataRepository(applicationContext)
        val connectionProvider = RoomModelServiceConnectionProvider(repository)
        val modelGateway = OpenAiCompatibleModelGateway(connectionProvider)
        val historyStore = RoomHistoryStore(repository.database)
        val contextCompiler = ContextCompiler(historyStore, historyStore, modelGateway)
        val agentRuntime = ReActAgentRuntime(modelGateway, contextCompiler, historyStore)

        ElymBotSessionService(
            context = applicationContext,
            repository = repository,
            connectionProvider = connectionProvider,
            modelGateway = modelGateway,
            agentRuntime = agentRuntime,
            commandParser = DefaultSlashCommandParser(),
            scope = applicationScope,
        )
    }
}
