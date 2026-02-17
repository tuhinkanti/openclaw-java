package ai.openclaw.cli;

import ai.openclaw.agent.AgentExecutor;
import ai.openclaw.agent.AnthropicProvider;
import ai.openclaw.channel.console.ConsoleChannel;
import ai.openclaw.config.ConfigLoader;
import ai.openclaw.config.OpenClawConfig;
import ai.openclaw.gateway.GatewayServer;
import ai.openclaw.gateway.RpcRouter;
import ai.openclaw.session.SessionStore;
import picocli.CommandLine.Command;

import java.util.concurrent.CountDownLatch;

@Command(name = "gateway", description = "Starts the Gateway WebSocket server")
public class GatewayCommand implements Runnable {

    @Override
    public void run() {
        try {
            // 1. Load Config
            System.out.println("Reading config from ~/.openclaw-java/config.json");
            OpenClawConfig config = ConfigLoader.load();

            // 2. Initialize Components
            SessionStore sessionStore = new SessionStore();
            AnthropicProvider llmProvider = new AnthropicProvider(config.getAgent().getApiKey());
            AgentExecutor agentExecutor = new AgentExecutor(config, sessionStore, llmProvider);

            // 3. Setup RPC Router
            RpcRouter router = new RpcRouter();
            router.register("gateway.health", params -> {
                // simple health check
                return new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("status", "ok");
            });
            router.register("agent.send", params -> {
                // handle remote send
                String sessionId = params.get("sessionId").asText();
                String message = params.get("message").asText();
                String response = agentExecutor.execute(sessionId, message);
                return new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("response", response);
            });

            // 4. Start Gateway Server
            GatewayServer server = new GatewayServer(config, router);
            server.start();
            System.out.println("Gateway listening on port " + config.getGateway().getPort());

            // 5. Start Console Channel
            ConsoleChannel console = new ConsoleChannel(agentExecutor, sessionStore);
            console.start();

            // Keep alive
            new CountDownLatch(1).await();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
