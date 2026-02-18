package ai.openclaw.tool;

import ai.openclaw.config.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Tool that reads the contents of a file.
 * Confined to a workspace directory for security.
 */
public class FileReadTool implements Tool {
    private static final Logger logger = LoggerFactory.getLogger(FileReadTool.class);
    private static final int MAX_OUTPUT_CHARS = 8192;
    private final Path workspaceRoot;

    public FileReadTool() {
        this(Paths.get(System.getProperty("user.home"), "workspace"));
    }

    public FileReadTool(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "file_read";
    }

    @Override
    public String description() {
        return "Reads the contents of a file at the given path and returns it as text. " +
                "Paths are relative to the workspace directory (" + workspaceRoot + "). " +
                "Use this to inspect source code, config files, logs, or any text file.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = Json.mapper().createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");
        ObjectNode path = properties.putObject("path");
        path.put("type", "string");
        path.put("description", "Path to the file to read (relative to workspace, or absolute within workspace)");

        schema.putArray("required").add("path");
        return schema;
    }

    @Override
    public ToolResult execute(JsonNode input) {
        String filePath = input.get("path").asText();
        logger.info("Reading file: {}", filePath);

        try {
            Path path = resolvePath(filePath);
            if (path == null) {
                return ToolResult.error("Access denied: path is outside the workspace (" + workspaceRoot + ")");
            }

            if (!Files.exists(path)) {
                return ToolResult.error("File not found: " + filePath);
            }

            if (Files.isDirectory(path)) {
                // List directory contents instead
                StringBuilder sb = new StringBuilder();
                sb.append("Directory listing for: ").append(path).append("\n");
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
                // Read only the first MAX_OUTPUT_CHARS using a bounded reader
                char[] buffer = new char[MAX_OUTPUT_CHARS];
                int charsRead;
                try (BufferedReader reader = Files.newBufferedReader(path)) {
                    charsRead = reader.read(buffer, 0, MAX_OUTPUT_CHARS);
                }
                if (charsRead <= 0) {
                    return ToolResult.success("");
                }
                return ToolResult.success(
                        new String(buffer, 0, charsRead) +
                                "\n[TRUNCATED: file is " + size + " bytes, showing first " + charsRead + " chars]");
            }

            String content = Files.readString(path);
            return ToolResult.success(content);

        } catch (IOException e) {
            logger.error("Failed to read file: {}", filePath, e);
            return ToolResult.error("Failed to read file: " + e.getMessage());
        }
    }

    /**
     * Resolves a path ensuring it stays within the workspace root.
     * Returns null if the resolved path escapes the workspace.
     */
    private Path resolvePath(String filePath) {
        Path resolved = Paths.get(filePath);
        if (!resolved.isAbsolute()) {
            resolved = workspaceRoot.resolve(filePath);
        }
        resolved = resolved.toAbsolutePath().normalize();
        if (!resolved.startsWith(workspaceRoot)) {
            logger.warn("Path escape attempt: {} resolved to {} (workspace: {})", filePath, resolved, workspaceRoot);
            return null;
        }
        return resolved;
    }
}
