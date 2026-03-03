package ai.openclaw.agent;

import ai.openclaw.config.Json;
import ai.openclaw.config.OpenClawConfig;
import ai.openclaw.session.Message;
import ai.openclaw.tool.Tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AnthropicProvider implements LlmProvider {
    private static final Logger logger = LoggerFactory.getLogger(AnthropicProvider.class);
    private static final String DEFAULT_API_URL = "https://api.anthropic.com/v1/messages";
    private static final long MAX_RETRY_DELAY_MS = 30_000;

    private final OpenClawConfig.AgentConfig agentConfig;
    private final String apiKey;
    private final String baseUrl;
    private final boolean bedrockMode;
    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public AnthropicProvider(OpenClawConfig.AgentConfig agentConfig) {
        this.agentConfig = agentConfig;
        this.apiKey = agentConfig.getApiKey();
        String rawBaseUrl = agentConfig.getBaseUrl();
        this.baseUrl = rawBaseUrl != null ? rawBaseUrl.replaceAll("/+$", "") : null;
        this.bedrockMode = rawBaseUrl != null && !rawBaseUrl.isEmpty()
                && !rawBaseUrl.contains("api.anthropic.com");
        int timeout = agentConfig.getLlmTimeoutSeconds();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .build();
        this.mapper = Json.mapper();
        logger.info("AnthropicProvider initialized (bedrockMode={}, baseUrl={}, timeout={}s)",
                bedrockMode, this.baseUrl, timeout);
    }

    private String resolveUrl(String model) {
        if (!bedrockMode) {
            return DEFAULT_API_URL;
        }
        return baseUrl + "/model/" + model + "/invoke";
    }

    @Override
    public String complete(List<Message> messages, String model) throws IOException {
        LlmResponse response = completeWithTools(messages, model, List.of());
        return response.getTextContent();
    }

    @Override
    public LlmResponse completeWithTools(List<Message> messages, String model, List<Tool> tools) throws IOException {
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("max_tokens", agentConfig.getMaxTokens());

        if (bedrockMode) {
            requestBody.put("anthropic_version", "bedrock-2023-05-31");
        } else {
            requestBody.put("model", model);
        }

        ArrayNode messagesArray = requestBody.putArray("messages");
        String systemPrompt = null;
        ArrayNode lastToolResultContentArray = null;

        for (Message msg : messages) {
            if ("system".equals(msg.getRole())) {
                systemPrompt = msg.getContent();
                lastToolResultContentArray = null;
            } else if ("tool_result".equals(msg.getRole())) {
                ArrayNode contentArray;
                if (lastToolResultContentArray != null) {
                    contentArray = lastToolResultContentArray;
                } else {
                    ObjectNode messageNode = messagesArray.addObject();
                    messageNode.put("role", "user");
                    contentArray = messageNode.putArray("content");
                    lastToolResultContentArray = contentArray;
                }
                ObjectNode toolResultBlock = contentArray.addObject();
                toolResultBlock.put("type", "tool_result");
                toolResultBlock.put("tool_use_id", msg.getToolUseId());
                toolResultBlock.put("content", msg.getContent());
                if (msg.isToolError()) {
                    toolResultBlock.put("is_error", true);
                }
            } else if ("assistant_tool_use".equals(msg.getRole())) {
                ObjectNode messageNode = messagesArray.addObject();
                messageNode.put("role", "assistant");
                if (msg.getContentBlocks() != null) {
                    messageNode.set("content", msg.getContentBlocks());
                } else {
                    messageNode.put("content", msg.getContent());
                }
                lastToolResultContentArray = null;
            } else {
                ObjectNode messageNode = messagesArray.addObject();
                messageNode.put("role", msg.getRole());
                messageNode.put("content", msg.getContent());
                lastToolResultContentArray = null;
            }
        }

        if (systemPrompt != null) {
            requestBody.put("system", systemPrompt);
        }

        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = requestBody.putArray("tools");
            for (Tool tool : tools) {
                ObjectNode toolNode = toolsArray.addObject();
                toolNode.put("name", tool.name());
                toolNode.put("description", tool.description());
                toolNode.set("input_schema", tool.inputSchema());
            }
        }

        String url = resolveUrl(model);
        logger.debug("Calling LLM at {} (bedrockMode={})", url, bedrockMode);

        RequestBody body = RequestBody.create(
                mapper.writeValueAsString(requestBody),
                MediaType.parse("application/json"));

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .addHeader("content-type", "application/json")
                .post(body);

        if (bedrockMode) {
            requestBuilder.addHeader("x-api-key", apiKey);
        } else {
            requestBuilder.addHeader("x-api-key", apiKey);
            requestBuilder.addHeader("anthropic-version", "2023-06-01");
        }

        Request request = requestBuilder.build();
        String responseBody = executeWithRetry(request);
        logger.debug("Anthropic API response: {}", responseBody);
        JsonNode jsonResponse = mapper.readTree(responseBody);
        return parseResponse(jsonResponse);
    }

    private String executeWithRetry(Request request) throws IOException {
        int maxAttempts = agentConfig.getRetryMaxAttempts();
        long delayMs = agentConfig.getRetryInitialDelayMs();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try (Response response = client.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : null;

                if (response.isSuccessful()) {
                    return body;
                }

                int code = response.code();
                boolean retryable = code == 429 || code >= 500;

                if (!retryable || attempt == maxAttempts) {
                    throw new IOException("Anthropic API error: " + code + " - " +
                            (body != null ? body : "No body"));
                }

                logger.warn("Retryable API error (HTTP {}), attempt {}/{}, retrying in {}ms",
                        code, attempt, maxAttempts, delayMs);
            } catch (IOException e) {
                if (attempt == maxAttempts) {
                    throw e;
                }
                logger.warn("IO error on attempt {}/{}: {}, retrying in {}ms",
                        attempt, maxAttempts, e.getMessage(), delayMs);
            }

            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("Retry interrupted", ie);
            }
            delayMs = Math.min(delayMs * 2, MAX_RETRY_DELAY_MS);
        }

        throw new IOException("Exhausted retries");
    }

    private LlmResponse parseResponse(JsonNode jsonResponse) {
        String stopReason = jsonResponse.has("stop_reason")
                ? jsonResponse.get("stop_reason").asText()
                : "end_turn";

        List<LlmResponse.ContentBlock> blocks = new ArrayList<>();
        JsonNode contentArray = jsonResponse.get("content");
        if (contentArray != null && contentArray.isArray()) {
            for (JsonNode block : contentArray) {
                String type = block.get("type").asText();
                if ("text".equals(type)) {
                    blocks.add(LlmResponse.ContentBlock.text(block.get("text").asText()));
                } else if ("tool_use".equals(type)) {
                    blocks.add(LlmResponse.ContentBlock.toolUse(
                            block.get("id").asText(),
                            block.get("name").asText(),
                            block.get("input")));
                }
            }
        }

        return new LlmResponse(stopReason, blocks);
    }

    @Override
    public String providerName() {
        return "anthropic";
    }
}
