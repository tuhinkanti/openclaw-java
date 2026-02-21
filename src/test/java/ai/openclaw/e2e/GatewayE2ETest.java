package ai.openclaw.e2e;

import ai.openclaw.agent.AgentExecutor;
import ai.openclaw.config.OpenClawConfig;
import ai.openclaw.gateway.GatewayServer;
import ai.openclaw.gateway.RpcProtocol;
import ai.openclaw.gateway.RpcRouter;
import ai.openclaw.session.Session;
import ai.openclaw.session.SessionStore;
import ai.openclaw.test.MockLlmProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class GatewayE2ETest {
    private GatewayServer server;
    private int port;
    private SessionStore sessionStore;

    private static int findFreePort() throws Exception {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        port = findFreePort();

        OpenClawConfig config = new OpenClawConfig();
        OpenClawConfig.GatewayConfig gatewayConfig = new OpenClawConfig.GatewayConfig();
        gatewayConfig.setPort(port);
        gatewayConfig.setAuthToken("test-token");
        config.setGateway(gatewayConfig);

        OpenClawConfig.AgentConfig agentConfig = new OpenClawConfig.AgentConfig();
        agentConfig.setProvider("mock");
        agentConfig.setModel("test-model");
        config.setAgent(agentConfig);

        sessionStore = new SessionStore();
        MockLlmProvider llmProvider = new MockLlmProvider();
        AgentExecutor agentExecutor = new AgentExecutor(config, sessionStore, llmProvider);

        RpcRouter router = new RpcRouter();
        router.register("gateway.health", params -> {
            return new ObjectMapper().createObjectNode().put("status", "ok");
        });

        // Match GatewayCommand implementation
        router.register("agent.send", params -> {
            String sessionId = params.get("sessionId").asText();
            String message = params.get("message").asText();
            String response = agentExecutor.execute(sessionId, message);
            return new ObjectMapper().createObjectNode().put("response", response);
        });

        server = new GatewayServer(config, router);
        server.start();
        Thread.sleep(1000); // Wait for server to start
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void testAgentSend() throws Exception {
        // Create session first
        Session session = sessionStore.createSession("test", "user1");
        final String sessionId = session.getId();

        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        WebSocketClient client = new WebSocketClient(new URI("ws://127.0.0.1:" + port)) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                System.out.println("Client connected");
            }

            @Override
            public void onMessage(String message) {
                messages.offer(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                System.out.println("Client closed");
            }

            @Override
            public void onError(Exception ex) {
                ex.printStackTrace();
            }
        };

        final String authToken = "test-token"; // Use the known token directly or capture from config
        if (authToken != null) {
            client.addHeader("Authorization", "Bearer " + authToken);
        }
        assertTrue(client.connectBlocking(5, TimeUnit.SECONDS));

        // Send request
        ObjectMapper mapper = new ObjectMapper();
        RpcProtocol.RpcMessage request = new RpcProtocol.RpcMessage();
        request.setId("1");
        request.setMethod("agent.send");

        ObjectNode params = mapper.createObjectNode();
        params.put("sessionId", sessionId);
        params.put("message", "Hello");
        request.setParams(params);

        client.send(mapper.writeValueAsString(request));

        // Wait for response
        String responseJson = messages.poll(5, TimeUnit.SECONDS);
        assertNotNull(responseJson, "Response should not be null");

        RpcProtocol.RpcMessage response = mapper.readValue(responseJson, RpcProtocol.RpcMessage.class);
        assertEquals("1", response.getId());
        assertNotNull(response.getResult());
        assertEquals("Mock response from OpenClaw", response.getResult().get("response").asText());

        client.close();
    }
}
