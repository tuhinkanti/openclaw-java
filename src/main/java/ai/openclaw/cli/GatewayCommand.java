package ai.openclaw.cli;

import ai.openclaw.agent.AgentExecutor;
import ai.openclaw.agent.LlmProviderFactory;
import ai.openclaw.channel.console.ConsoleChannel;
import ai.openclaw.channel.slack.SlackChannel;
import ai.openclaw.config.ConfigLoader;
import ai.openclaw.config.Json;
import ai.openclaw.config.OpenClawConfig;
import ai.openclaw.gateway.GatewayServer;
import ai.openclaw.gateway.RpcRouter;
import ai.openclaw.plugin.PluginRegistry;
import ai.openclaw.session.SessionStore;
import ai.openclaw.tool.CodeExecutionTool;
import ai.openclaw.tool.DynamicCliTool;
import ai.openclaw.tool.FileReadTool;
import ai.openclaw.tool.FileWriteTool;
import ai.openclaw.tool.SlackPostTool;
import ai.openclaw.tool.Tool;
import ai.openclaw.tool.WebSearchTool;
import picocli.CommandLine.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@Command(name = "gateway", description = "Starts the Gateway WebSocket server")
public class GatewayCommand implements Runnable {

    @Override
    public void run() {
        try {
            System.out.println("Reading config from ~/.openclaw-java/config.json or Environment Variables");
            var config = ConfigLoader.load();
            var sessionStore = new SessionStore();
            var llmProvider = LlmProviderFactory.create(config.getAgent());
            var agentExecutor = new AgentExecutor(config, sessionStore, llmProvider, createTools(config));
            var router = createRouter(agentExecutor);
            var server = new GatewayServer(config, router);

            server.start();
            System.out.println("Gateway listening on port " + config.getGateway().getPort());

            new ConsoleChannel(agentExecutor, sessionStore, config).start();
            startSlackChannelIfConfigured(config, agentExecutor, sessionStore);

            new CountDownLatch(1).await();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private List<Tool> createTools(OpenClawConfig config) {
        var tools = new ArrayList<Tool>();
        tools.add(new CodeExecutionTool());
        tools.add(new FileReadTool());
        tools.add(new FileWriteTool());
        tools.add(new WebSearchTool());

        if (hasText(config.getSlack() == null ? null : config.getSlack().getBotToken())) {
            tools.add(new SlackPostTool(config.getSlack().getBotToken()));
        }

        for (var pluginTool : new PluginRegistry().createTools(config)) {
            tools.add(pluginTool);
            System.out.println("Registered plugin tool: " + pluginTool.name());
        }

        for (var toolConfig : config.getAgent().getCustomTools()) {
            tools.add(new DynamicCliTool(toolConfig));
            System.out.println("Registered custom tool: " + toolConfig.getName());
        }

        return tools;
    }

    private RpcRouter createRouter(AgentExecutor agentExecutor) {
        var router = new RpcRouter();
        router.register("gateway.health", params -> Json.mapper().createObjectNode().put("status", "ok"));
        router.register("agent.send", params -> response(agentExecutor.execute(
                params.get("sessionId").asText(),
                params.get("message").asText())));
        router.register("agent.ralph", params -> response(agentExecutor.executeRalph(
                params.get("sessionId").asText(),
                params.get("message").asText())));
        return router;
    }

    private void startSlackChannelIfConfigured(
            OpenClawConfig config, AgentExecutor agentExecutor, SessionStore sessionStore) {
        if (!hasText(config.getSlack() == null ? null : config.getSlack().getBotToken())
                || !hasText(config.getSlack() == null ? null : config.getSlack().getAppToken())) {
            return;
        }

        new SlackChannel(agentExecutor, sessionStore, config).start();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode response(String text) {
        return Json.mapper().createObjectNode().put("response", text);
    }
}
