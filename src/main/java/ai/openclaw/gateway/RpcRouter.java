package ai.openclaw.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class RpcRouter {
    private static final Logger logger = LoggerFactory.getLogger(RpcRouter.class);
    private final Map<String, Function<JsonNode, JsonNode>> routes = new HashMap<>();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public void register(String method, Function<JsonNode, JsonNode> handler) {
        routes.put(method, handler);
    }

    public JsonNode route(String method, JsonNode params) {
        Function<JsonNode, JsonNode> handler = routes.get(method);
        if (handler != null) {
            return handler.apply(params);
        } else {
            logger.warn("Method not found: {}", method);
            ObjectNode error = mapper.createObjectNode();
            error.put("error", "Method not found: " + method);
            return error;
        }
    }
}
