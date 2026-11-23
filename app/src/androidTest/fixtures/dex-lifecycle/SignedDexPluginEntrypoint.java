package com.elymbot.testfixture;

import com.elymbot.core.contract.ModelToolCall;
import com.elymbot.core.contract.PluginEntrypoint;
import com.elymbot.core.contract.PluginHandle;
import com.elymbot.core.contract.PluginHostContext;
import com.elymbot.core.contract.ToolDefinition;
import com.elymbot.core.contract.ToolExecutionContext;
import com.elymbot.core.contract.ToolExecutionResult;
import com.elymbot.core.contract.ToolProgress;
import com.elymbot.core.contract.ToolResultStatus;
import com.elymbot.core.contract.ToolScope;
import com.elymbot.core.contract.ToolSource;
import java.util.Collections;
import java.util.List;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.jvm.functions.Function2;

public final class SignedDexPluginEntrypoint implements PluginEntrypoint {
    private static final String PREFIX = "elymbot.test.dex.";

    @Override
    public PluginHandle prepare(PluginHostContext context) {
        String attemptsKey = PREFIX + "prepare-attempts";
        int attempts = Integer.parseInt(System.getProperty(attemptsKey, "0"));
        System.setProperty(attemptsKey, Integer.toString(attempts + 1));
        if (Boolean.getBoolean(PREFIX + "fail-prepare")) {
            throw new IllegalStateException("Synthetic preparation failure");
        }

        String instanceId = context.getInstallationIdentity().getInstanceId();
        System.setProperty(PREFIX + "prepared-instance", instanceId);
        System.setProperty(PREFIX + "data-directory", context.getDataDirectory().getAbsolutePath());
        return new ProbeHandle(instanceId);
    }

    private static final class ProbeHandle implements PluginHandle {
        private final String instanceId;
        private final List<ToolSource> sources = Collections.singletonList(new ProbeTool());

        private ProbeHandle(String instanceId) {
            this.instanceId = instanceId;
        }

        @Override
        public List<ToolSource> getToolSources() {
            return sources;
        }

        @Override
        public void close() {
            System.setProperty(PREFIX + "closed-instance", instanceId);
        }
    }

    private static final class ProbeTool implements ToolSource {
        private final ToolDefinition definition = new ToolDefinition(
                "synthetic.runtime-tool",
                "synthetic_runtime_tool",
                "A deterministic test tool",
                "{\"type\":\"object\"}",
                Collections.singleton(ToolScope.MAIN_SESSION));

        @Override
        public ToolDefinition getDefinition() {
            return definition;
        }

        @Override
        public Object execute(
                ModelToolCall call,
                ToolExecutionContext context,
                Function2<? super ToolProgress, ? super Continuation<? super Unit>, ? extends Object> onProgress,
                Continuation<? super ToolExecutionResult> continuation) {
            return new ToolExecutionResult(call.getCallId(), ToolResultStatus.SUCCESS, "fixture");
        }
    }
}
