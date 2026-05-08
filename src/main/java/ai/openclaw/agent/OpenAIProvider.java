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
import java.util.List;
import java.util.concurrent.TimeUnit;

public class OpenAIProvider implements LlmProvider {
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    private final String apiKey;
    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public OpenAIProvider(String apiKey) {
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

        ArrayNode messagesArray = requestBody.putArray("messages");
        for (Message msg : messages) {
            if ("assistant_tool_use".equals(msg.getRole()) || "tool_result".equals(msg.getRole())) {
                continue;
            }
            ObjectNode messageNode = messagesArray.addObject();
            messageNode.put("role", msg.getRole());
            messageNode.put("content", msg.getContent());
        }

        RequestBody body = RequestBody.create(mapper.writeValueAsString(requestBody), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("content-type", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No body";
                throw new IOException("OpenAI API error: " + response.code() + " - " + errorBody);
            }

            JsonNode jsonResponse = mapper.readTree(response.body().byteStream());
            JsonNode choices = jsonResponse.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                throw new IOException("OpenAI API error: empty choices");
            }
            return choices.get(0).get("message").get("content").asText();
        }
    }

    @Override
    public LlmResponse completeWithTools(List<Message> messages, String model, List<Tool> tools) throws IOException {
        return new LlmResponse("end_turn", List.of(LlmResponse.ContentBlock.text(complete(messages, model))));
    }

    @Override
    public String providerName() {
        return "openai";
    }
}
