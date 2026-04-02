package ai.openclaw.plugin;

import ai.openclaw.config.OpenClawConfig;
import ai.openclaw.tool.QmdMemoryTool;
import ai.openclaw.tool.Tool;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Registers the read-only QMD memory tool.
 */
@PluginType("qmd")
public class QmdPlugin implements ToolPlugin {
    @Override
    public List<Tool> createTools(OpenClawConfig.PluginConfig pluginConfig) {
        var config = pluginConfig.getConfig();
        var command = text(config, "command", "qmd");
        var timeoutSeconds = intValue(config, "timeoutSeconds", 20);
        Path workingDirectory = Paths.get(text(config, "workingDirectory", System.getProperty("user.dir")));

        if (!QmdMemoryTool.isCommandAvailable(command) || !QmdMemoryTool.looksLikeQmd(command, workingDirectory)) {
            throw new IllegalArgumentException("QMD plugin requires a working qmd command: " + command);
        }

        return List.of(new QmdMemoryTool(command, timeoutSeconds, workingDirectory));
    }

    private static String text(JsonNode config, String field, String defaultValue) {
        if (config == null || config.isNull()) {
            return defaultValue;
        }
        JsonNode value = config.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return defaultValue;
        }
        return value.asText();
    }

    private static int intValue(JsonNode config, String field, int defaultValue) {
        if (config == null || config.isNull()) {
            return defaultValue;
        }
        JsonNode value = config.get(field);
        if (value == null || !value.canConvertToInt()) {
            return defaultValue;
        }
        return value.asInt();
    }
}
