package ai.openclaw.tool;

import ai.openclaw.config.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Tool that runs shell commands via ProcessBuilder.
 * Commands run as the current OS user with a configurable timeout.
 */
public class CodeExecutionTool implements Tool {
    private static final Logger logger = LoggerFactory.getLogger(CodeExecutionTool.class);
    private static final int MAX_OUTPUT_CHARS = 8192;
    private final long timeoutSeconds;
    private final Path workingDirectory;

    public CodeExecutionTool() {
        this(30, Paths.get(System.getProperty("user.home"), "workspace"));
    }

    public CodeExecutionTool(long timeoutSeconds, Path workingDirectory) {
        this.timeoutSeconds = timeoutSeconds;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public String name() {
        return "code_execution";
    }

    @Override
    public String description() {
        return "Runs a shell command on the user's machine and returns the output. " +
                "Use this to execute code, run scripts, list files, or perform any command-line task. " +
                "Always confirm with the user before running destructive commands (rm, mv, etc.).";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = Json.mapper().createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");
        ObjectNode command = properties.putObject("command");
        command.put("type", "string");
        command.put("description", "The shell command to execute");

        schema.putArray("required").add("command");
        return schema;
    }

    @Override
    public ToolResult execute(JsonNode input) {
        String command = input.get("command").asText();
        logger.info("Executing command: {}", command);

        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
            pb.directory(workingDirectory.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Read output in a separate thread so we don't block on the stream
            StringBuffer output = new StringBuffer();
            Thread readerThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (output.length() < MAX_OUTPUT_CHARS) {
                            output.append(line).append("\n");
                        }
                    }
                } catch (IOException e) {
                    // Process was destroyed, expected during timeout
                }
            });
            readerThread.setDaemon(true);
            readerThread.start();

            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                readerThread.join(1000); // Give reader a moment to flush
                return new ToolResult(
                        output.toString().trim() + "\n[TIMEOUT: Command exceeded " + timeoutSeconds + "s limit]",
                        true, -1);
            }

            readerThread.join(); // Wait for reader to finish (process has exited, stream will close)

            int exitCode = process.exitValue();
            String result = output.toString().trim();
            if (result.length() > MAX_OUTPUT_CHARS) {
                result = result.substring(0, MAX_OUTPUT_CHARS) + "\n[OUTPUT TRUNCATED]";
            }

            logger.info("Command exited with code {}", exitCode);
            return new ToolResult(result, exitCode != 0, exitCode);

        } catch (Exception e) {
            logger.error("Failed to execute command: {}", command, e);
            return ToolResult.error("Failed to execute command: " + e.getMessage());
        }
    }
}
