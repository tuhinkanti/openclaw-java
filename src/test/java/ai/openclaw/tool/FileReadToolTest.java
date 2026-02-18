package ai.openclaw.tool;

import ai.openclaw.config.Json;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class FileReadToolTest {

    private final FileReadTool tool = new FileReadTool();

    @Test
    void testReadExistingFile(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Hello, World!");

        ObjectNode input = Json.mapper().createObjectNode();
        input.put("path", testFile.toString());

        ToolResult result = tool.execute(input);

        assertFalse(result.isError());
        assertEquals("Hello, World!", result.getOutput());
    }

    @Test
    void testReadMissingFile() {
        ObjectNode input = Json.mapper().createObjectNode();
        input.put("path", "/nonexistent/file.txt");

        ToolResult result = tool.execute(input);

        assertTrue(result.isError());
        assertTrue(result.getOutput().contains("File not found"));
    }

    @Test
    void testReadDirectory(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve("a.txt"));
        Files.createFile(tempDir.resolve("b.txt"));

        ObjectNode input = Json.mapper().createObjectNode();
        input.put("path", tempDir.toString());

        ToolResult result = tool.execute(input);

        assertFalse(result.isError());
        assertTrue(result.getOutput().contains("a.txt"));
        assertTrue(result.getOutput().contains("b.txt"));
    }

    @Test
    void testToolMetadata() {
        assertEquals("file_read", tool.name());
        assertNotNull(tool.description());
        assertNotNull(tool.inputSchema());
    }
}
