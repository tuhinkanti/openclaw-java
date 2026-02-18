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
}
