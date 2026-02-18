package ai.openclaw.agent;

import ai.openclaw.config.Json;
import ai.openclaw.session.Message;
import ai.openclaw.tool.Tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AnthropicProvider implements LlmProvider {
    private final String apiKey;
    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private static final String API_URL = "https://api.anthropic.com/v1/messages";

    public AnthropicProvider(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        this.mapper = Json.mapper();
    }

    @Override
    public String complete(List<Message> messages, String model) throws IOException {
        LlmResponse response = completeWithTools(messages, model, List.of());
        return response.getTextContent();
    }

    @Override
    public LlmResponse completeWithTools(List<Message> messages, String model, List<Tool> tools) throws IOException {
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 4096);

        // Build messages array
        ArrayNode messagesArray = requestBody.putArray("messages");
        String systemPrompt = null;

        for (Message msg : messages) {
            if ("system".equals(msg.getRole())) {
                systemPrompt = msg.getContent();
            } else if ("tool_result".equals(msg.getRole())) {
                // Tool results use structured content format
                ObjectNode messageNode = messagesArray.addObject();
                messageNode.put("role", "user");
                ArrayNode contentArray = messageNode.putArray("content");
                ObjectNode toolResultBlock = contentArray.addObject();
                toolResultBlock.put("type", "tool_result");
                toolResultBlock.put("tool_use_id", msg.getToolUseId());
                toolResultBlock.put("content", msg.getContent());
                if (msg.isToolError()) {
                    toolResultBlock.put("is_error", true);
                }
            } else if ("assistant_tool_use".equals(msg.getRole())) {
                // Reconstruct the assistant message with tool_use content blocks
                ObjectNode messageNode = messagesArray.addObject();
                messageNode.put("role", "assistant");
                if (msg.getContentBlocks() != null) {
                    messageNode.set("content", msg.getContentBlocks());
                } else {
                    messageNode.put("content", msg.getContent());
                }
            } else {
                ObjectNode messageNode = messagesArray.addObject();
                messageNode.put("role", msg.getRole());
                messageNode.put("content", msg.getContent());
            }
        }

        if (systemPrompt != null) {
            requestBody.put("system", systemPrompt);
        }

        // Add tool definitions if provided
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = requestBody.putArray("tools");
            for (Tool tool : tools) {
                ObjectNode toolNode = toolsArray.addObject();
                toolNode.put("name", tool.name());
                toolNode.put("description", tool.description());
                toolNode.set("input_schema", tool.inputSchema());
            }
        }

        RequestBody body = RequestBody.create(
                mapper.writeValueAsString(requestBody),
                MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No body";
                throw new IOException("Anthropic API error: " + response.code() + " - " + errorBody);
            }

            JsonNode jsonResponse = mapper.readTree(response.body().byteStream());
            return parseResponse(jsonResponse);
        }
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
