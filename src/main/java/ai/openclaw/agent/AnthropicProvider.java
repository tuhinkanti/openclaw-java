package ai.openclaw.agent;

import ai.openclaw.session.Message;
import ai.openclaw.config.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.IOException;
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
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 4096);

        ArrayNode messagesArray = requestBody.putArray("messages");
        String systemPrompt = null;

        for (Message msg : messages) {
            if ("system".equals(msg.getRole())) {
                systemPrompt = msg.getContent();
            } else {
                ObjectNode messageNode = messagesArray.addObject();
                messageNode.put("role", msg.getRole());
                messageNode.put("content", msg.getContent());
            }
        }

        if (systemPrompt != null) {
            requestBody.put("system", systemPrompt);
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
            return jsonResponse.get("content").get(0).get("text").asText();
        }
    }

    @Override
    public String providerName() {
        return "anthropic";
    }
}
