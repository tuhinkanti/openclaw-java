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
        String remoteAddress = conn.getRemoteSocketAddress().toString();
        logger.info("New connection from {}", remoteAddress);

        String expectedToken = config.getGateway().getAuthToken();
        if (expectedToken == null || expectedToken.isEmpty()) {
            logger.warn("No auth token configured! Accepting connection from {}", remoteAddress);
            clients.put(remoteAddress, conn);
            return;
        }

        String providedToken = extractToken(handshake);
        if (providedToken == null || !constantTimeEquals(expectedToken, providedToken)) {
            logger.warn("Unauthorized connection attempt from {}", remoteAddress);
            // Close with policy violation code (1008) or normal code (1000) with reason
            conn.close(1008, "Unauthorized");
            return;
        }

        logger.info("Authenticated connection from {}", remoteAddress);
        clients.put(remoteAddress, conn);
    }

    private String extractToken(ClientHandshake handshake) {
        // 1. Check Authorization header
        String authHeader = handshake.getFieldValue("Authorization");
        if (authHeader != null && authHeader.toLowerCase().startsWith("bearer ")) {
            return authHeader.substring(7).trim();
        }

        // 2. Check query parameter
        String descriptor = handshake.getResourceDescriptor();
        if (descriptor.contains("?token=") || descriptor.contains("&token=")) {
            int index = descriptor.indexOf("token=");
            String token = descriptor.substring(index + 6);
            int end = token.indexOf('&');
            if (end != -1) {
                token = token.substring(0, end);
            }
            return token;
        }
        }
        return null;
    }

    /** Constant-time string comparison to prevent timing attacks. */
    private boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String remoteAddress = conn.getRemoteSocketAddress().toString();
        logger.info("Closed connection: {}", remoteAddress);
        clients.remove(remoteAddress);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        if (!clients.containsValue(conn)) {
            logger.warn("Message from unauthenticated connection, ignoring");
            return;
        }

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
