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

    @TempDir
    Path tempDir;

    private FileReadTool tool() {
        return new FileReadTool(tempDir);
    }

    @Test
    void testReadExistingFile() throws IOException {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Hello, World!");

        ObjectNode input = Json.mapper().createObjectNode();
        input.put("path", testFile.toString());

        ToolResult result = tool().execute(input);

        assertFalse(result.isError());
        assertEquals("Hello, World!", result.getOutput());
    }

    @Test
    void testReadRelativePath() throws IOException {
        Path testFile = tempDir.resolve("relative.txt");
        Files.writeString(testFile, "Relative content");

        ObjectNode input = Json.mapper().createObjectNode();
        input.put("path", "relative.txt");

        ToolResult result = tool().execute(input);

        assertFalse(result.isError());
        assertEquals("Relative content", result.getOutput());
    }

    @Test
    void testReadMissingFile() {
        ObjectNode input = Json.mapper().createObjectNode();
        input.put("path", "nonexistent.txt");

        ToolResult result = tool().execute(input);

        assertTrue(result.isError());
        assertTrue(result.getOutput().contains("File not found"));
    }

    @Test
    void testReadDirectory() throws IOException {
        Files.createFile(tempDir.resolve("a.txt"));
        Files.createFile(tempDir.resolve("b.txt"));

        ObjectNode input = Json.mapper().createObjectNode();
        input.put("path", tempDir.toString());

        ToolResult result = tool().execute(input);

        assertFalse(result.isError());
        assertTrue(result.getOutput().contains("a.txt"));
        assertTrue(result.getOutput().contains("b.txt"));
    }

    @Test
    void testPathEscapeBlocked() {
        ObjectNode input = Json.mapper().createObjectNode();
        input.put("path", "../../../etc/passwd");

        ToolResult result = tool().execute(input);

        assertTrue(result.isError());
        assertTrue(result.getOutput().contains("Access denied"));
    }

    @Test
    void testAbsolutePathOutsideWorkspaceBlocked() {
        ObjectNode input = Json.mapper().createObjectNode();
        input.put("path", "/etc/passwd");

        ToolResult result = tool().execute(input);

        assertTrue(result.isError());
        assertTrue(result.getOutput().contains("Access denied"));
    }

    @Test
    void testToolMetadata() {
        FileReadTool tool = tool();
        assertEquals("file_read", tool.name());
        assertNotNull(tool.description());
        assertNotNull(tool.inputSchema());
    }
}
