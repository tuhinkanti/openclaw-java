package ai.openclaw.tool;

import ai.openclaw.config.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Tool that reads the contents of a file.
 */
public class FileReadTool implements Tool {
    private static final Logger logger = LoggerFactory.getLogger(FileReadTool.class);
    private static final int MAX_OUTPUT_CHARS = 8192;

    @Override
    public String name() {
        return "file_read";
    }

    @Override
    public String description() {
        return "Reads the contents of a file at the given path and returns it as text. " +
                "Use this to inspect source code, config files, logs, or any text file.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = Json.mapper().createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");
        ObjectNode path = properties.putObject("path");
        path.put("type", "string");
        path.put("description", "Absolute path to the file to read");

        schema.putArray("required").add("path");
        return schema;
    }

    @Override
    public ToolResult execute(JsonNode input) {
        String filePath = input.get("path").asText();
        logger.info("Reading file: {}", filePath);

        try {
            Path path = Paths.get(filePath);

            if (!Files.exists(path)) {
                return ToolResult.error("File not found: " + filePath);
            }

            if (Files.isDirectory(path)) {
                // List directory contents instead
                StringBuilder sb = new StringBuilder();
                sb.append("Directory listing for: ").append(filePath).append("\n");
                try (var entries = Files.list(path)) {
                    entries.sorted().forEach(p -> {
                        String type = Files.isDirectory(p) ? "[DIR]  " : "[FILE] ";
                        sb.append(type).append(p.getFileName()).append("\n");
                    });
                }
                String result = sb.toString();
                if (result.length() > MAX_OUTPUT_CHARS) {
                    result = result.substring(0, MAX_OUTPUT_CHARS) + "\n[OUTPUT TRUNCATED]";
                }
                return ToolResult.success(result);
            }

            long size = Files.size(path);
            if (size > MAX_OUTPUT_CHARS * 2) {
                // Read partial for large files
                String content = Files.readString(path);
                return ToolResult.success(
                        content.substring(0, MAX_OUTPUT_CHARS) +
                                "\n[TRUNCATED: file is " + size + " bytes, showing first " + MAX_OUTPUT_CHARS
                                + " chars]");
            }

            String content = Files.readString(path);
            return ToolResult.success(content);

        } catch (IOException e) {
            logger.error("Failed to read file: {}", filePath, e);
            return ToolResult.error("Failed to read file: " + e.getMessage());
        }
    }
}
