package ai.openclaw.tool;

import ai.openclaw.config.Json;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public class CodeExecutionToolTest {

    private final CodeExecutionTool tool = new CodeExecutionTool();

    @Test
    void testEchoCommand() {
        ObjectNode input = Json.mapper().createObjectNode();
        input.put("command", "echo hello world");

        ToolResult result = tool.execute(input);

        assertEquals(0, result.getExitCode());
        assertFalse(result.isError());
        assertEquals("hello world", result.getOutput());
    }

    @Test
    void testFailingCommand() {
        ObjectNode input = Json.mapper().createObjectNode();
        input.put("command", "exit 42");

        ToolResult result = tool.execute(input);

        assertEquals(42, result.getExitCode());
        assertTrue(result.isError());
    }

    @Test
    void testCommandWithStderr() {
        ObjectNode input = Json.mapper().createObjectNode();
        input.put("command", "echo error >&2");

        ToolResult result = tool.execute(input);

        assertEquals(0, result.getExitCode());
        assertTrue(result.getOutput().contains("error"));
    }

    @Test
    void testToolMetadata() {
        assertEquals("code_execution", tool.name());
        assertNotNull(tool.description());
        assertNotNull(tool.inputSchema());
        assertTrue(tool.inputSchema().has("properties"));
    }

    @Test
    void testTimeout() {
        CodeExecutionTool shortTimeoutTool = new CodeExecutionTool(2, Paths.get(System.getProperty("user.home")));
        ObjectNode input = Json.mapper().createObjectNode();
        // Use a command that outputs something then sleeps, so the reader can finish
        input.put("command", "echo started && sleep 30");

        ToolResult result = shortTimeoutTool.execute(input);

        assertTrue(result.isError());
        assertEquals(-1, result.getExitCode());
    }
}
