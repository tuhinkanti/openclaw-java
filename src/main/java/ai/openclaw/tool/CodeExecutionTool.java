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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Tool that runs shell commands via ProcessBuilder.
 * Commands run as the current OS user with a configurable timeout.
 * Includes a safety layer that blocks dangerous commands and warns on risky
 * ones.
 */
public class CodeExecutionTool implements Tool {
    private static final Logger logger = LoggerFactory.getLogger(CodeExecutionTool.class);
    private static final int MAX_OUTPUT_CHARS = 8192;

    /**
     * Commands that are always blocked — too dangerous even with user confirmation.
     */
    private static final List<Pattern> DEFAULT_BLOCKED_PATTERNS = List.of(
            // Recursive delete on root or home
            Pattern.compile("\\brm\\s+-[^\\s]*r[^\\s]*\\s+[/~]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\brm\\s+-[^\\s]*r[^\\s]*\\s+\\*"),
            // Filesystem/device destruction
            Pattern.compile("\\bmkfs\\b"),
            Pattern.compile("\\bdd\\s+.*of\\s*=\\s*/dev/"),
            // Permission/ownership on root paths
            Pattern.compile("\\bchmod\\s+777\\s+/"),
            Pattern.compile("\\bchown\\s+.*\\s+/"),
            // Overwrite system config
            Pattern.compile(">\\s*/etc/"),
            // Remote code execution
            Pattern.compile("\\bcurl\\s+.*\\|\\s*sh"),
            Pattern.compile("\\bwget\\s+.*\\|\\s*sh"),
            // System control
            Pattern.compile("\\b(shutdown|reboot|halt|poweroff)\\b"),
            Pattern.compile("\\bkill\\s+-9\\s+1\\b"),
            // Absolute path reads outside workspace (cat, head, tail, less, more, vi, nano)
            Pattern.compile("\\b(cat|head|tail|less|more|vi|nano|vim)\\s+/(?!home/[^/]+/workspace)"),
            // SSRF: cloud metadata endpoints and internal networks
            Pattern.compile("\\b(curl|wget)\\s+[^|]*169\\.254\\."),
            Pattern.compile("\\b(curl|wget)\\s+[^|]*127\\.0\\.0\\."),
            Pattern.compile("\\b(curl|wget)\\s+[^|]*localhost"),
            Pattern.compile("\\b(curl|wget)\\s+[^|]*\\[::1\\]"),
            Pattern.compile("\\b(curl|wget)\\s+[^|]*10\\."),
            Pattern.compile("\\b(curl|wget)\\s+[^|]*172\\.(1[6-9]|2[0-9]|3[01])\\."),
            Pattern.compile("\\b(curl|wget)\\s+[^|]*192\\.168\\."),
            // Symlink creation (can bypass workspace path checks)
            Pattern.compile("\\bln\\s+-[^\\s]*s"));

    /** Commands that are allowed but logged at WARN level. */
    private static final List<Pattern> DEFAULT_WARNED_PATTERNS = List.of(
            Pattern.compile("\\brm\\b"),
            Pattern.compile("\\bmv\\b"),
            Pattern.compile("\\bchmod\\b"),
            Pattern.compile("\\bchown\\b"),
            Pattern.compile("\\bcurl\\b"),
            Pattern.compile("\\bwget\\b"),
            Pattern.compile("\\bsudo\\b"),
            Pattern.compile("\\bpip\\s+install"),
            Pattern.compile("\\bnpm\\s+install"),
            Pattern.compile("\\bapt(-get)?\\s+install"));

    private final long timeoutSeconds;
    private final Path workingDirectory;
    private final List<Pattern> blockedPatterns;
    private final List<Pattern> warnedPatterns;

    public CodeExecutionTool() {
        this(30, Paths.get(System.getProperty("user.home"), "workspace"));
    }

    public CodeExecutionTool(long timeoutSeconds, Path workingDirectory) {
        this(timeoutSeconds, workingDirectory, DEFAULT_BLOCKED_PATTERNS, DEFAULT_WARNED_PATTERNS);
    }

    public CodeExecutionTool(long timeoutSeconds, Path workingDirectory,
            List<Pattern> blockedPatterns, List<Pattern> warnedPatterns) {
        this.timeoutSeconds = timeoutSeconds;
        this.workingDirectory = workingDirectory;
        this.blockedPatterns = blockedPatterns;
        this.warnedPatterns = warnedPatterns;
    }

    @Override
    public String name() {
        return "code_execution";
    }

    @Override
    public String description() {
        return "Runs a shell command on the user's machine and returns the output. " +
                "Use this to execute code, run scripts, list files, or perform any command-line task. " +
                "Dangerous commands (rm -rf /, mkfs, dd to devices, etc.) are blocked. " +
                "Risky commands (rm, mv, chmod, curl, etc.) are allowed but logged.";
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

        // Safety check: block dangerous commands
        String blockReason = checkBlocked(command);
        if (blockReason != null) {
            logger.error("BLOCKED dangerous command: {} (reason: {})", command, blockReason);
            return ToolResult.error("Command blocked for safety: " + blockReason +
                    ". This command pattern is not allowed.");
        }

        // Safety check: warn on risky commands
        checkWarned(command);

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

            long startNanos = System.nanoTime();
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                readerThread.join(1000); // Give reader a moment to flush
                return new ToolResult(
                        output.toString().trim() + "\n[TIMEOUT: Command exceeded " + timeoutSeconds + "s limit]",
                        true, -1);
            }

            // Wait for reader to finish, bounded by remaining timeout budget.
            // If the command spawned background children that inherited stdout,
            // the stream won't reach EOF until they exit — we must not block forever.
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            long remainingMs = Math.max(0, TimeUnit.SECONDS.toMillis(timeoutSeconds) - elapsedMs);
            readerThread.join(remainingMs + 2000); // +2s grace for stream flush
            if (readerThread.isAlive()) {
                readerThread.interrupt();
                logger.warn(
                        "Reader thread still alive after timeout budget — background child may have inherited stdout");
            }

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

    /**
     * Check if a command matches any blocked pattern.
     * Returns the reason string if blocked, null if allowed.
     */
    String checkBlocked(String command) {
        for (Pattern pattern : blockedPatterns) {
            if (pattern.matcher(command).find()) {
                return "matches blocked pattern: " + pattern.pattern();
            }
        }
        return null;
    }

    /**
     * Log a warning if the command matches any risky pattern.
     */
    private void checkWarned(String command) {
        for (Pattern pattern : warnedPatterns) {
            if (pattern.matcher(command).find()) {
                logger.warn("Risky command detected ({}): {}", pattern.pattern(), command);
                return; // Only warn once per command
            }
        }
    }
}
