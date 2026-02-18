package ai.openclaw.tool;

import ai.openclaw.config.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

/**
 * Tool that fetches content from a URL and returns it as text.
 * HTML tags are stripped to return readable content.
 * SSRF protection: resolves hostnames and rejects private/link-local IP ranges.
 */
public class WebSearchTool implements Tool {
    private static final Logger logger = LoggerFactory.getLogger(WebSearchTool.class);
    private static final int MAX_OUTPUT_CHARS = 8192;
    private final OkHttpClient client;

    public WebSearchTool() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .followRedirects(false) // Don't follow redirects — validate each hop
                .build();
    }

    @Override
    public String name() {
        return "web_fetch";
    }

    @Override
    public String description() {
        return "Fetches the content of a public web page at the given URL and returns the text. " +
                "HTML tags are stripped. Use this to read documentation, API references, articles, or any public web page. "
                +
                "Private/internal network addresses are blocked.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = Json.mapper().createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");
        ObjectNode url = properties.putObject("url");
        url.put("type", "string");
        url.put("description", "The URL to fetch content from (must be a public internet address)");

        schema.putArray("required").add("url");
        return schema;
    }

    @Override
    public ToolResult execute(JsonNode input) {
        String url = input.get("url").asText();
        logger.info("Fetching URL: {}", url);

        // SSRF protection: validate the URL before making the request
        String ssrfError = validateUrl(url);
        if (ssrfError != null) {
            logger.warn("SSRF attempt blocked: {} ({})", url, ssrfError);
            return ToolResult.error("URL blocked: " + ssrfError);
        }

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

    /**
     * Validates a URL for SSRF safety by resolving the hostname and checking
     * that it does not point to a private, loopback, or link-local address.
     *
     * @return null if the URL is safe, or an error message string if it should be
     *         blocked
     */
    String validateUrl(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return "invalid URL: " + e.getMessage();
        }

        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return "only http and https schemes are allowed (got: " + scheme + ")";
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return "URL has no host";
        }

        // Resolve hostname to IP addresses and check each one
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (Exception e) {
            return "could not resolve host: " + host;
        }

        for (InetAddress addr : addresses) {
            if (addr.isLoopbackAddress()) {
                return "loopback address is not allowed: " + addr.getHostAddress();
            }
            if (addr.isSiteLocalAddress()) {
                return "private/site-local address is not allowed: " + addr.getHostAddress();
            }
            if (addr.isLinkLocalAddress()) {
                return "link-local address is not allowed (e.g. cloud metadata): " + addr.getHostAddress();
            }
            if (addr.isAnyLocalAddress()) {
                return "wildcard address is not allowed: " + addr.getHostAddress();
            }
            if (addr.isMulticastAddress()) {
                return "multicast address is not allowed: " + addr.getHostAddress();
            }
        }

        return null; // URL is safe
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
