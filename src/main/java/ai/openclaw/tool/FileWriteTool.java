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
 * Tool that writes content to a file.
 * Confined to a workspace directory for security.
 */
public class FileWriteTool implements Tool {
    private static final Logger logger = LoggerFactory.getLogger(FileWriteTool.class);
    private final Path workspaceRoot;

    public FileWriteTool() {
        this(Paths.get(System.getProperty("user.home"), "workspace"));
    }

    public FileWriteTool(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "file_write";
    }

    @Override
    public String description() {
        return "Writes content to a file at the given path. Creates the file and parent directories if they don't exist. "
                +
                "Overwrites existing content. Paths are relative to the workspace directory (" + workspaceRoot + ").";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = Json.mapper().createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode path = properties.putObject("path");
        path.put("type", "string");
        path.put("description", "Path to the file to write (relative to workspace, or absolute within workspace)");

        ObjectNode content = properties.putObject("content");
        content.put("type", "string");
        content.put("description", "The content to write to the file");

        schema.putArray("required").add("path").add("content");
        return schema;
    }

    @Override
    public ToolResult execute(JsonNode input) {
        String filePath = input.get("path").asText();
        String content = input.get("content").asText();
        logger.info("Writing file: {} ({} chars)", filePath, content.length());

        try {
            Path path = resolvePath(filePath);
            if (path == null) {
                return ToolResult.error("Access denied: path is outside the workspace (" + workspaceRoot + ")");
            }

            // Create parent directories if needed
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            Files.writeString(path, content);
            return ToolResult.success("Successfully wrote " + content.length() + " chars to " + path);

        } catch (IOException e) {
            logger.error("Failed to write file: {}", filePath, e);
            return ToolResult.error("Failed to write file: " + e.getMessage());
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
