package ai.openclaw.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.openclaw.config.Json;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import ai.openclaw.config.OpenClawConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GatewayServer extends WebSocketServer {
    private static final Logger logger = LoggerFactory.getLogger(GatewayServer.class);
    private final OpenClawConfig config;
    private final ObjectMapper mapper = Json.mapper();
    private final Map<String, WebSocket> clients = new ConcurrentHashMap<>();
    private final RpcRouter router;

    public GatewayServer(OpenClawConfig config, RpcRouter router) {
        super(new InetSocketAddress("127.0.0.1", config.getGateway().getPort()));
        this.config = config;
        this.router = router;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        logger.info("New connection from {}", conn.getRemoteSocketAddress());
        // Simple auth check could be added here
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        logger.info("Closed connection: {}", conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            RpcProtocol.RpcMessage request = mapper.readValue(message, RpcProtocol.RpcMessage.class);

            if (request.getMethod() != null) {
                // It's a request
                try {
                    JsonNode result = router.route(request.getMethod(), request.getParams());
                    if (result == null) {
                        sendError(conn, request.getId(), -32601, "Method not found: " + request.getMethod());
                        return;
                    }
                    RpcProtocol.RpcMessage response = new RpcProtocol.RpcMessage();
                    response.setId(request.getId());
                    response.setResult(result);
                    conn.send(mapper.writeValueAsString(response));
                } catch (Exception e) {
                    logger.error("Error processing request", e);
                    sendError(conn, request.getId(), -32603, "Internal error: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing message", e);
        }
    }

    private void sendError(WebSocket conn, String id, int code, String message) {
        try {
            RpcProtocol.RpcMessage response = new RpcProtocol.RpcMessage();
            response.setId(id);
            response.setError(new RpcProtocol.RpcError(code, message));
            conn.send(mapper.writeValueAsString(response));
        } catch (Exception e) {
            logger.error("Failed to send error response", e);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        logger.error("WebSocket server error", ex);
    }

    @Override
    public void onStart() {
        logger.info("Gateway server started on port {}", getPort());
    }
}
