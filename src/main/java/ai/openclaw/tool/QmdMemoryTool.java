package ai.openclaw.tool;

import ai.openclaw.config.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Read-only memory/search tool backed by the qmd CLI.
 */
public class QmdMemoryTool implements Tool {
    private static final Logger logger = LoggerFactory.getLogger(QmdMemoryTool.class);
    private static final int MAX_OUTPUT_CHARS = 32768;
    private static final JsonNode INPUT_SCHEMA = buildSchema();

    private final String command;
    private final long timeoutSeconds;
    private final Path workingDirectory;

    public QmdMemoryTool(String command, long timeoutSeconds, Path workingDirectory) {
        this.command = command;
        this.timeoutSeconds = timeoutSeconds;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public String name() {
        return "qmd_memory";
    }

    @Override
    public String description() {
        return "Searches and reads the user's QMD markdown memory index. " +
                "Read-only operations only: query, search, vector search, get, multi-get, ls, status, and context inspection.";
    }

    @Override
    public JsonNode inputSchema() {
        return INPUT_SCHEMA;
    }

    @Override
    public ToolResult execute(JsonNode input) {
        List<String> commandLine;
        try {
            commandLine = buildCommand(input);
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        }

        logger.info("Executing QMD memory command: {}", String.join(" ", commandLine));

        try {
            return run(commandLine);
        } catch (Exception e) {
            logger.error("Failed to execute QMD command", e);
            return ToolResult.error("Failed to execute qmd_memory: " + e.getMessage());
        }
    }

    List<String> buildCommand(JsonNode input) {
        var operation = requiredText(input, "operation");
        var commandLine = new ArrayList<String>();
        commandLine.add(command);

        return switch (operation) {
            case "query", "search", "vsearch" -> searchCommand(commandLine, input, operation);
            case "get" -> getCommand(commandLine, input);
            case "multi_get" -> multiGetCommand(commandLine, input);
            case "ls" -> lsCommand(commandLine, input);
            case "status" -> subcommand(commandLine, "status");
            case "context_list" -> subcommand(commandLine, "context", "list");
            case "context_check" -> subcommand(commandLine, "context", "check");
            default -> throw new IllegalArgumentException("Unsupported qmd_memory operation: " + operation);
        };
    }

    public static boolean isCommandAvailable(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }

        var explicitPath = Path.of(command);
        if (command.contains(File.separator)) {
            return Files.isExecutable(explicitPath);
        }

        var pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return false;
        }

        for (var pathEntry : pathEnv.split(File.pathSeparator)) {
            var candidate = Path.of(pathEntry, command);
            if (Files.isExecutable(candidate)) {
                return true;
            }
        }
        return false;
    }

    public static boolean looksLikeQmd(String command, Path workingDirectory) {
        try {
            var process = new ProcessBuilder(command, "--help")
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true)
                    .start();

            var output = new StringBuffer();
            var readerThread = new Thread(() -> readOutput(process, output));
            readerThread.setDaemon(true);
            readerThread.start();

            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                readerThread.join(500);
                return false;
            }

            readerThread.join(1000);
            var help = output.toString().toLowerCase();
            return process.exitValue() == 0 && help.contains("qmd");
        } catch (Exception e) {
            return false;
        }
    }

    private void appendSearchOptions(List<String> commandLine, JsonNode input) {
        if (input != null && input.has("collections") && input.get("collections").isArray()) {
            for (var collection : input.get("collections")) {
                if (collection.isTextual() && !collection.asText().isBlank()) {
                    commandLine.add("-c");
                    commandLine.add(collection.asText());
                }
            }
        }

        appendIntOption(commandLine, "-n", input, "limit");
        appendDecimalOption(commandLine, "--min-score", input, "min_score");
        appendFlag(commandLine, "--full", input, "full");
        appendFlag(commandLine, "--line-numbers", input, "line_numbers");
    }

    private static void appendTextArg(List<String> commandLine, JsonNode input, String field) {
        if (input != null && input.has(field) && input.get(field).isTextual() && !input.get(field).asText().isBlank()) {
            commandLine.add(input.get(field).asText());
        }
    }

    private ToolResult run(List<String> commandLine) throws Exception {
        var process = new ProcessBuilder(commandLine)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .start();

        var output = new StringBuffer();
        var readerThread = new Thread(() -> readOutput(process, output));
        readerThread.setDaemon(true);
        readerThread.start();

        var startNanos = System.nanoTime();
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            readerThread.join(1000);
            return new ToolResult(
                    truncate(output.toString().trim()) + "\n[TIMEOUT: Command exceeded " + timeoutSeconds + "s limit]",
                    true, -1);
        }

        var elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        var remainingMs = Math.max(0, TimeUnit.SECONDS.toMillis(timeoutSeconds) - elapsedMs);
        readerThread.join(remainingMs + 2000);
        if (readerThread.isAlive()) {
            readerThread.interrupt();
        }

        var exitCode = process.exitValue();
        return new ToolResult(truncate(output.toString().trim()), exitCode != 0, exitCode);
    }

    private List<String> searchCommand(List<String> commandLine, JsonNode input, String operation) {
        commandLine.add(operation);
        appendSearchOptions(commandLine, input);
        commandLine.add(requiredText(input, "query"));
        commandLine.add("--json");
        return commandLine;
    }

    private List<String> getCommand(List<String> commandLine, JsonNode input) {
        commandLine.add("get");
        commandLine.add(requiredText(input, "target"));
        appendIntOption(commandLine, "--from", input, "from_line");
        appendIntOption(commandLine, "-l", input, "max_lines");
        appendFlag(commandLine, "--line-numbers", input, "line_numbers");
        return commandLine;
    }

    private List<String> multiGetCommand(List<String> commandLine, JsonNode input) {
        commandLine.add("multi-get");
        commandLine.add(requiredText(input, "target"));
        appendIntOption(commandLine, "-l", input, "max_lines");
        appendIntOption(commandLine, "--max-bytes", input, "max_bytes");
        commandLine.add("--json");
        return commandLine;
    }

    private List<String> lsCommand(List<String> commandLine, JsonNode input) {
        commandLine.add("ls");
        appendTextArg(commandLine, input, "target");
        return commandLine;
    }

    private static List<String> subcommand(List<String> commandLine, String... parts) {
        for (var part : parts) {
            commandLine.add(part);
        }
        return commandLine;
    }

    private static void appendFlag(List<String> commandLine, String flag, JsonNode input, String field) {
        if (input != null && input.path(field).asBoolean(false)) {
            commandLine.add(flag);
        }
    }

    private static void appendIntOption(List<String> commandLine, String flag, JsonNode input, String field) {
        if (input != null && input.has(field) && input.get(field).canConvertToInt()) {
            commandLine.add(flag);
            commandLine.add(String.valueOf(input.get(field).asInt()));
        }
    }

    private static void appendDecimalOption(List<String> commandLine, String flag, JsonNode input, String field) {
        if (input != null && input.has(field) && input.get(field).isNumber()) {
            commandLine.add(flag);
            commandLine.add(input.get(field).asText());
        }
    }

    private static String requiredText(JsonNode input, String field) {
        if (input == null) {
            throw new IllegalArgumentException("Missing input");
        }

        var value = input.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("qmd_memory requires a non-empty string field: " + field);
        }
        return value.asText();
    }

    private static JsonNode buildSchema() {
        var schema = Json.mapper().createObjectNode();
        schema.put("type", "object");

        var properties = schema.putObject("properties");
        enumProperty(properties, "operation",
                "QMD action: query, search, vsearch, get, multi_get, ls, status, context_list, or context_check",
                "query", "search", "vsearch", "get", "multi_get", "ls", "status", "context_list", "context_check");
        stringProperty(properties, "query", "Search query for query/search/vsearch operations");
        stringProperty(properties, "target", "Path, docid, or pattern for get/multi_get/ls");
        arrayProperty(properties, "collections", "Optional collection filters for query/search/vsearch");
        typedProperty(properties, "limit", "integer", "Maximum number of search results to return");
        typedProperty(properties, "min_score", "number", "Minimum score threshold for query/search/vsearch");
        typedProperty(properties, "full", "boolean", "Return full document bodies for query/search/vsearch");
        typedProperty(properties, "line_numbers", "boolean", "Include line numbers in output where supported");
        typedProperty(properties, "from_line", "integer", "Starting line for get");
        typedProperty(properties, "max_lines", "integer", "Line cap for get/multi_get");
        typedProperty(properties, "max_bytes", "integer", "Byte cap for multi_get");

        schema.putArray("required").add("operation");
        return schema;
    }

    private static void stringProperty(ObjectNode properties, String name, String description) {
        typedProperty(properties, name, "string", description);
    }

    private static void arrayProperty(ObjectNode properties, String name, String description) {
        var property = properties.putObject(name);
        property.put("type", "array");
        property.put("description", description);
        property.putObject("items").put("type", "string");
    }

    private static void enumProperty(ObjectNode properties, String name, String description, String... values) {
        var property = properties.putObject(name);
        property.put("type", "string");
        property.put("description", description);
        var enumValues = property.putArray("enum");
        for (var value : values) {
            enumValues.add(value);
        }
    }

    private static void typedProperty(ObjectNode properties, String name, String type, String description) {
        var property = properties.putObject(name);
        property.put("type", type);
        property.put("description", description);
    }

    private static void readOutput(Process process, StringBuffer output) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() < MAX_OUTPUT_CHARS) {
                    output.append(line).append("\n");
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static String truncate(String output) {
        if (output.length() <= MAX_OUTPUT_CHARS) {
            return output;
        }
        return output.substring(0, MAX_OUTPUT_CHARS) + "\n[OUTPUT TRUNCATED]";
    }
}
