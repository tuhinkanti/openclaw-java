package ai.openclaw.cli;

import ai.openclaw.agent.AgentExecutor;
import ai.openclaw.agent.LlmProvider;
import ai.openclaw.agent.LlmProviderFactory;
import ai.openclaw.channel.console.ConsoleChannel;
import ai.openclaw.channel.slack.SlackChannel;
import ai.openclaw.config.ConfigLoader;
import ai.openclaw.config.Json;
import ai.openclaw.config.OpenClawConfig;
import ai.openclaw.gateway.GatewayServer;
import ai.openclaw.gateway.RpcRouter;
import ai.openclaw.session.SessionStore;
import ai.openclaw.tool.CodeExecutionTool;
import ai.openclaw.tool.FileReadTool;
import ai.openclaw.tool.FileWriteTool;
import ai.openclaw.tool.SlackPostTool;
import ai.openclaw.tool.Tool;
import ai.openclaw.tool.WebSearchTool;
import picocli.CommandLine.Command;

import java.util.List;
import java.util.concurrent.CountDownLatch;

@Command(name = "gateway", description = "Starts the Gateway WebSocket server")
public class GatewayCommand implements Runnable {

    @Override
    public void run() {
        try {
            // 1. Load Config
            System.out.println("Reading config from ~/.openclaw-java/config.json or Environment Variables");
            OpenClawConfig config = ConfigLoader.load();

            // 2. Initialize Components
            SessionStore sessionStore = new SessionStore();
            LlmProvider llmProvider = LlmProviderFactory.create(config.getAgent());

            // Register tools
            List<Tool> tools;
            if (config.getSlack() != null
                    && config.getSlack().getBotToken() != null
                    && !config.getSlack().getBotToken().isEmpty()) {
                tools = List.of(
                        new CodeExecutionTool(),
                        new FileReadTool(),
                        new FileWriteTool(),
                        new WebSearchTool(),
                        new SlackPostTool(config.getSlack().getBotToken()));
            } else {
                tools = List.of(
                        new CodeExecutionTool(),
                        new FileReadTool(),
                        new FileWriteTool(),
                        new WebSearchTool());
            }

            AgentExecutor agentExecutor = new AgentExecutor(config, sessionStore, llmProvider, tools);

            // 3. Setup RPC Router
            RpcRouter router = new RpcRouter();
            router.register("gateway.health", params -> {
                return Json.mapper().createObjectNode().put("status", "ok");
            });
            router.register("agent.send", params -> {
                String sessionId = params.get("sessionId").asText();
                String message = params.get("message").asText();
                String response = agentExecutor.execute(sessionId, message);
                return Json.mapper().createObjectNode().put("response", response);
            });
            router.register("agent.ralph", params -> {
                String sessionId = params.get("sessionId").asText();
                String message = params.get("message").asText();
                String response = agentExecutor.executeRalph(sessionId, message);
                return Json.mapper().createObjectNode().put("response", response);
            });

            // 4. Start Gateway Server
            GatewayServer server = new GatewayServer(config, router);
            server.start();
            System.out.println("Gateway listening on port " + config.getGateway().getPort());

            // 5. Start Console Channel
            ConsoleChannel console = new ConsoleChannel(agentExecutor, sessionStore, config);
            console.start();

            // 6. Start Slack Channel (if configured)
            if (config.getSlack() != null
                    && config.getSlack().getBotToken() != null && !config.getSlack().getBotToken().isEmpty()
                    && config.getSlack().getAppToken() != null && !config.getSlack().getAppToken().isEmpty()) {
                SlackChannel slackChannel = new SlackChannel(agentExecutor, sessionStore, config);
                slackChannel.start();
            }

            // Keep alive
            new CountDownLatch(1).await();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
