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

    private final FileWriteTool tool = new FileWriteTool();

    @Test
    void testWriteNewFile(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("output.txt");

        ObjectNode input = Json.mapper().createObjectNode();
        input.put("path", testFile.toString());
        input.put("content", "Hello from test!");

        ToolResult result = tool.execute(input);

        assertFalse(result.isError());
        assertEquals("Hello from test!", Files.readString(testFile));
    }

    @Test
    void testWriteCreatesDirectories(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("sub/dir/output.txt");

        ObjectNode input = Json.mapper().createObjectNode();
        input.put("path", testFile.toString());
        input.put("content", "Nested!");

        ToolResult result = tool.execute(input);

        assertFalse(result.isError());
        assertTrue(Files.exists(testFile));
        assertEquals("Nested!", Files.readString(testFile));
    }

    @Test
    void testOverwriteExistingFile(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("existing.txt");
        Files.writeString(testFile, "old content");

        ObjectNode input = Json.mapper().createObjectNode();
        input.put("path", testFile.toString());
        input.put("content", "new content");

        ToolResult result = tool.execute(input);

        assertFalse(result.isError());
        assertEquals("new content", Files.readString(testFile));
    }

    @Test
    void testToolMetadata() {
        assertEquals("file_write", tool.name());
        assertNotNull(tool.description());
        assertNotNull(tool.inputSchema());
    }
}
