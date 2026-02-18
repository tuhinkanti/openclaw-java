package ai.openclaw.tool;

import ai.openclaw.config.Json;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class FileWriteToolTest {

    @TempDir
    Path tempDir;

    private FileWriteTool tool() {
        return new FileWriteTool(tempDir);
    }

    @Test
    void testWriteNewFile() throws IOException {
        ObjectNode input = Json.mapper().createObjectNode();
        input.put("path", "output.txt");
        input.put("content", "Hello from test!");

        ToolResult result = tool().execute(input);

        assertFalse(result.isError());
        assertEquals("Hello from test!", Files.readString(tempDir.resolve("output.txt")));
    }

    @Test
    void testWriteCreatesDirectories() throws IOException {
        ObjectNode input = Json.mapper().createObjectNode();
        input.put("path", "sub/dir/output.txt");
        input.put("content", "Nested!");

        ToolResult result = tool().execute(input);

        assertFalse(result.isError());
        Path created = tempDir.resolve("sub/dir/output.txt");
        assertTrue(Files.exists(created));
        assertEquals("Nested!", Files.readString(created));
    }

    @Test
    void testOverwriteExistingFile() throws IOException {
        Path testFile = tempDir.resolve("existing.txt");
        Files.writeString(testFile, "old content");

        ObjectNode input = Json.mapper().createObjectNode();
        input.put("path", testFile.toString());
        input.put("content", "new content");

        ToolResult result = tool().execute(input);

        assertFalse(result.isError());
        assertEquals("new content", Files.readString(testFile));
    }

    @Test
    void testPathEscapeBlocked() {
        ObjectNode input = Json.mapper().createObjectNode();
        input.put("path", "../../../tmp/evil.txt");
        input.put("content", "pwned");

        ToolResult result = tool().execute(input);

        assertTrue(result.isError());
        assertTrue(result.getOutput().contains("Access denied"));
    }

    @Test
    void testAbsolutePathOutsideWorkspaceBlocked() {
        ObjectNode input = Json.mapper().createObjectNode();
        input.put("path", "/tmp/evil.txt");
        input.put("content", "pwned");

        ToolResult result = tool().execute(input);

        assertTrue(result.isError());
        assertTrue(result.getOutput().contains("Access denied"));
    }

    @Test
    void testToolMetadata() {
        FileWriteTool tool = tool();
        assertEquals("file_write", tool.name());
        assertNotNull(tool.description());
        assertNotNull(tool.inputSchema());
    }
}
