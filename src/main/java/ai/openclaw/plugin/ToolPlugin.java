package ai.openclaw.plugin;

import ai.openclaw.config.OpenClawConfig;
import ai.openclaw.tool.Tool;

import java.util.List;

/**
 * A typed extension point that can register one or more tools from config.
 */
public interface ToolPlugin {
    List<Tool> createTools(OpenClawConfig.PluginConfig pluginConfig);
}
