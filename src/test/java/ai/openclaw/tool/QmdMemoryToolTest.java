package ai.openclaw.tool;

import ai.openclaw.config.Json;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QmdMemoryToolTest {

    @Test
    void buildsQueryCommandWithStructuredOptions() {
        QmdMemoryTool tool = new QmdMemoryTool("qmd", 20, Paths.get(System.getProperty("java.io.tmpdir")));

        ObjectNode input = Json.mapper().createObjectNode();
        input.put("operation", "query");
        input.put("query", "how auth works");
        input.put("limit", 7);
        input.put("min_score", 0.35);
        input.put("full", true);
        input.put("line_numbers", true);
        ArrayNode collections = input.putArray("collections");
        collections.add("docs");
        collections.add("notes");

        List<String> command = tool.buildCommand(input);

        assertEquals(List.of(
                "qmd",
                "query",
                "-c",
                "docs",
                "-c",
                "notes",
                "-n",
                "7",
                "--min-score",
                "0.35",
                "--full",
                "--line-numbers",
                "how auth works",
                "--json"), command);
    }

    @Test
    void buildsGetCommandWithLineSlice() {
        QmdMemoryTool tool = new QmdMemoryTool("qmd", 20, Paths.get(System.getProperty("java.io.tmpdir")));

        ObjectNode input = Json.mapper().createObjectNode();
        input.put("operation", "get");
        input.put("target", "#abc123");
        input.put("from_line", 10);
        input.put("max_lines", 25);
        input.put("line_numbers", true);

        assertEquals(List.of("qmd", "get", "#abc123", "--from", "10", "-l", "25", "--line-numbers"),
                tool.buildCommand(input));
    }

    @Test
    void rejectsUnsupportedOperation() {
        QmdMemoryTool tool = new QmdMemoryTool("qmd", 20, Paths.get(System.getProperty("java.io.tmpdir")));

        ObjectNode input = Json.mapper().createObjectNode();
        input.put("operation", "update");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> tool.buildCommand(input));
        assertTrue(error.getMessage().contains("Unsupported"));
    }

    @Test
    void reportsMissingCommand() {
        assertFalse(QmdMemoryTool.isCommandAvailable("definitely-not-a-real-command"));
    }
}
