package ai.openclaw.agent;

import ai.openclaw.config.OpenClawConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SystemPromptBuilder {
    private static final Logger logger = LoggerFactory.getLogger(SystemPromptBuilder.class);
    private final OpenClawConfig config;
    private static final String DEFAULT_PROMPT = "You are OpenClaw, a helpful AI assistant. " +
            "You answer concisely and accurately. " +
            "You have a code_execution tool that can run shell commands on the user's machine. " +
            "Use it when the user asks you to run code, scripts, check files, or perform any command-line task. " +
            "Always confirm with the user before running destructive commands (rm, mv to overwrite, etc.).";

    public SystemPromptBuilder(OpenClawConfig config) {
        this.config = config;
    }

    public String build() {
        // 1. Try config override
        if (config.getAgent() != null && config.getAgent().getSystemPrompt() != null) {
            return config.getAgent().getSystemPrompt();
        }

        // 2. Try IDENTITY.md in workspace
        // Assume workspace is ~/.openclaw-java/workspace for MVP
        Path identityPath = Paths.get(System.getProperty("user.home"), ".openclaw-java", "workspace", "IDENTITY.md");
        if (Files.exists(identityPath)) {
            try {
                return Files.readString(identityPath);
            } catch (IOException e) {
                logger.warn("Failed to read IDENTITY.md, falling back to default", e);
            }
        }

        // 3. Default
        return DEFAULT_PROMPT;
    }
}
