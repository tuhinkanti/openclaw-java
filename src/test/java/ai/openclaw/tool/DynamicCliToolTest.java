package ai.openclaw.tool;

import ai.openclaw.config.Json;
import ai.openclaw.config.OpenClawConfig.CustomToolConfig;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DynamicCliToolTest {

    @Test
    void testBasicProperties() {
        CustomToolConfig config = new CustomToolConfig();
        config.setName("test_tool");
        config.setDescription("A test tool");
        config.setCommandTemplate(List.of("echo", "test"));
        config.setTimeoutSeconds(5);

        ObjectNode schema = Json.mapper().createObjectNode();
        schema.put("type", "object");
        config.setInputSchema(schema);

        DynamicCliTool tool = new DynamicCliTool(config, Paths.get(System.getProperty("java.io.tmpdir")));

        assertEquals("test_tool", tool.name());
        assertEquals("A test tool", tool.description());
        assertEquals(schema, tool.inputSchema());
    }

    @Test
    void testCommandSubstitution() {
        CustomToolConfig config = new CustomToolConfig();
        config.setName("echo_tool");
        config.setDescription("Echoes input");
        config.setCommandTemplate(List.of("echo", "Hello", "{{name}}", "from", "{{place}}"));

        DynamicCliTool tool = new DynamicCliTool(config, Paths.get(System.getProperty("java.io.tmpdir")));

        ObjectNode input = Json.mapper().createObjectNode();
        input.put("name", "Alice");
        input.put("place", "Wonderland");

        ToolResult result = tool.execute(input);

        assertFalse(result.isError());
        assertEquals(0, result.getExitCode());
        assertEquals("Hello Alice from Wonderland", result.getOutput());
    }

    @Test
    void testCommandFailure() {
        CustomToolConfig config = new CustomToolConfig();
        config.setName("fail_tool");
        config.setDescription("Fails on purpose");
        config.setCommandTemplate(List.of("sh", "-c", "exit 42"));

        DynamicCliTool tool = new DynamicCliTool(config, Paths.get(System.getProperty("java.io.tmpdir")));

        ObjectNode input = Json.mapper().createObjectNode();
        ToolResult result = tool.execute(input);

        assertTrue(result.isError());
        assertEquals(42, result.getExitCode());
    }

    @Test
    void testMissingParameterIgnored() {
        CustomToolConfig config = new CustomToolConfig();
        config.setName("echo_tool");
        config.setDescription("Echoes input");
        // If {{place}} isn't provided, it should just remain the literal template
        // string
        config.setCommandTemplate(List.of("echo", "Hello", "{{name}}", "from", "{{place}}"));

        DynamicCliTool tool = new DynamicCliTool(config, Paths.get(System.getProperty("java.io.tmpdir")));

        ObjectNode input = Json.mapper().createObjectNode();
        input.put("name", "Bob");
        // missing 'place'

        ToolResult result = tool.execute(input);

        assertFalse(result.isError());
        assertEquals(0, result.getExitCode());
        assertEquals("Hello Bob from {{place}}", result.getOutput());
    }
}
