package ai.openclaw.tool;

import ai.openclaw.config.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Tool that fetches content from a URL and returns it as text.
 * HTML tags are stripped to return readable content.
 */
public class WebSearchTool implements Tool {
    private static final Logger logger = LoggerFactory.getLogger(WebSearchTool.class);
    private static final int MAX_OUTPUT_CHARS = 8192;
    private final OkHttpClient client;

    public WebSearchTool() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
    }

    @Override
    public String name() {
        return "web_fetch";
    }

    @Override
    public String description() {
        return "Fetches the content of a web page at the given URL and returns the text. " +
                "HTML tags are stripped. Use this to read documentation, API references, articles, or any public web page.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = Json.mapper().createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");
        ObjectNode url = properties.putObject("url");
        url.put("type", "string");
        url.put("description", "The URL to fetch content from");

        schema.putArray("required").add("url");
        return schema;
    }

    @Override
    public ToolResult execute(JsonNode input) {
        String url = input.get("url").asText();
        logger.info("Fetching URL: {}", url);

        try {
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "OpenClaw/0.1 (AI Assistant)")
                    .get()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return ToolResult.error("HTTP " + response.code() + ": " + response.message());
                }

                String body = response.body() != null ? response.body().string() : "";

                // Strip HTML tags for readability
                String text = stripHtml(body);

                if (text.length() > MAX_OUTPUT_CHARS) {
                    text = text.substring(0, MAX_OUTPUT_CHARS) + "\n[TRUNCATED]";
                }

                return ToolResult.success(text);
            }

        } catch (Exception e) {
            logger.error("Failed to fetch URL: {}", url, e);
            return ToolResult.error("Failed to fetch URL: " + e.getMessage());
        }
    }

    /** Simple HTML tag stripping — removes tags and collapses whitespace. */
    static String stripHtml(String html) {
        // Remove script and style blocks entirely
        String text = html.replaceAll("(?is)<script.*?</script>", " ");
        text = text.replaceAll("(?is)<style.*?</style>", " ");
        // Remove HTML tags
        text = text.replaceAll("<[^>]+>", " ");
        // Decode common entities
        text = text.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ");
        // Collapse whitespace
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }
}
