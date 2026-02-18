package ai.openclaw.tool;

import ai.openclaw.config.Json;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WebSearchToolTest {

    private final WebSearchTool tool = new WebSearchTool();

    @Test
    void testStripHtml() {
        String html = "<html><head><title>Test</title></head><body><h1>Hello</h1><p>World</p></body></html>";
        String result = WebSearchTool.stripHtml(html);
        assertTrue(result.contains("Hello"));
        assertTrue(result.contains("World"));
        assertFalse(result.contains("<h1>"));
    }

    @Test
    void testStripHtmlWithScript() {
        String html = "<html><script>alert('xss')</script><body>Content</body></html>";
        String result = WebSearchTool.stripHtml(html);
        assertTrue(result.contains("Content"));
        assertFalse(result.contains("alert"));
    }

    @Test
    void testInvalidUrl() {
        ObjectNode input = Json.mapper().createObjectNode();
        input.put("url", "not-a-url");

        ToolResult result = tool.execute(input);
        assertTrue(result.isError());
    }

    @Test
    void testToolMetadata() {
        assertEquals("web_fetch", tool.name());
        assertNotNull(tool.description());
        assertNotNull(tool.inputSchema());
    }

    // --- SSRF protection tests ---

    @Test
    void testBlocksLoopback() {
        assertNotNull(tool.validateUrl("http://127.0.0.1/secret"));
        assertNotNull(tool.validateUrl("http://127.0.0.1:8080/admin"));
    }

    @Test
    void testBlocksLocalhostByName() {
        // localhost resolves to 127.0.0.1 — must be blocked
        assertNotNull(tool.validateUrl("http://localhost/"));
        assertNotNull(tool.validateUrl("http://localhost:8080/admin"));
    }

    @Test
    void testBlocksCloudMetadata() {
        // 169.254.169.254 is link-local — blocked by isLinkLocalAddress()
        assertNotNull(tool.validateUrl("http://169.254.169.254/latest/meta-data/"));
    }

    @Test
    void testBlocksPrivateRanges() {
        assertNotNull(tool.validateUrl("http://192.168.1.1/"));
        assertNotNull(tool.validateUrl("http://10.0.0.1/"));
        assertNotNull(tool.validateUrl("http://172.16.0.1/"));
    }

    @Test
    void testBlocksNonHttpScheme() {
        assertNotNull(tool.validateUrl("ftp://example.com/file"));
        assertNotNull(tool.validateUrl("file:///etc/passwd"));
    }

    @Test
    void testAllowsPublicUrl() {
        // Public internet addresses should pass validation
        assertNull(tool.validateUrl("https://example.com/page"));
        assertNull(tool.validateUrl("http://api.github.com/repos"));
    }
}
