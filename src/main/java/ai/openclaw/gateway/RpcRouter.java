package ai.openclaw.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class RpcRouter {
    private static final Logger logger = LoggerFactory.getLogger(RpcRouter.class);
    private final Map<String, Function<JsonNode, JsonNode>> routes = new HashMap<>();

    public void register(String method, Function<JsonNode, JsonNode> handler) {
        routes.put(method, handler);
    }

    /**
     * Routes a method call to the registered handler.
     * 
     * @return the handler result, or null if the method is not registered
     */
    public JsonNode route(String method, JsonNode params) {
        Function<JsonNode, JsonNode> handler = routes.get(method);
        if (handler != null) {
            return handler.apply(params);
        }
        logger.warn("Method not found: {}", method);
        return null;
    }
}
