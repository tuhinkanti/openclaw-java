package ai.openclaw.tool;

import ai.openclaw.config.OpenClawConfig.CustomToolConfig;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A tool that dynamically executes a CLI command based on configuration.
 * Command arguments are substituted from the input JSON parameters.
 */
public class DynamicCliTool implements Tool {
    private static final Logger logger = LoggerFactory.getLogger(DynamicCliTool.class);
    private static final int MAX_OUTPUT_CHARS = 8192;

    private final CustomToolConfig config;
    private final Path workingDirectory;

    public DynamicCliTool(CustomToolConfig config) {
        this(config, Paths.get(System.getProperty("user.home"), "workspace"));
    }

    public DynamicCliTool(CustomToolConfig config, Path workingDirectory) {
        this.config = config;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public String name() {
        return config.getName();
    }

    @Override
    public String description() {
        return config.getDescription();
    }

    @Override
    public JsonNode inputSchema() {
        return config.getInputSchema();
    }

    @Override
    public ToolResult execute(JsonNode input) {
        logger.info("Executing custom tool: {}", name());

        List<String> command = new ArrayList<>();

        // Build the command by substituting {{param_name}} with values from input
        for (String argTemplate : config.getCommandTemplate()) {
            String arg = argTemplate;
            if (arg.contains("{{") && arg.contains("}}")) {
                int start = arg.indexOf("{{");
                int end = arg.indexOf("}}", start);
                if (end > start) {
                    String paramName = arg.substring(start + 2, end).trim();
                    JsonNode paramValue = input.get(paramName);
                    if (paramValue != null) {
                        String valueStr = paramValue.isTextual() ? paramValue.asText() : paramValue.toString();
                        arg = arg.replace("{{" + paramName + "}}", valueStr);
                    }
                }
            }
            command.add(arg);
        }

        try {
            logger.debug("Resolved command: {}", String.join(" ", command));
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workingDirectory.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

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
                    // Process was destroyed
                }
            });
            readerThread.setDaemon(true);
            readerThread.start();

            long timeoutSeconds = config.getTimeoutSeconds() > 0 ? config.getTimeoutSeconds() : 30;
            long startNanos = System.nanoTime();
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                readerThread.join(1000);
                return new ToolResult(
                        output.toString().trim() + "\n[TIMEOUT: Command exceeded " + timeoutSeconds + "s limit]",
                        true, -1);
            }

            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            long remainingMs = Math.max(0, TimeUnit.SECONDS.toMillis(timeoutSeconds) - elapsedMs);
            readerThread.join(remainingMs + 2000);

            if (readerThread.isAlive()) {
                readerThread.interrupt();
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();
            if (result.length() > MAX_OUTPUT_CHARS) {
                result = result.substring(0, MAX_OUTPUT_CHARS) + "\n[OUTPUT TRUNCATED]";
            }

            logger.info("Custom tool '{}' exited with code {}", name(), exitCode);
            return new ToolResult(result, exitCode != 0, exitCode);

        } catch (Exception e) {
            logger.error("Failed to execute custom tool: {}", name(), e);
            return ToolResult.error("Failed to execute tool: " + e.getMessage());
        }
    }
}
