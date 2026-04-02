package ai.openclaw.plugin;

import ai.openclaw.config.Json;
import ai.openclaw.config.OpenClawConfig;
import ai.openclaw.tool.Tool;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PluginRegistryTest {

    @Test
    void rejectsUnknownPluginType() {
        var registry = new PluginRegistry(List.of());
        var config = new OpenClawConfig();

        var plugin = new OpenClawConfig.PluginConfig();
        plugin.setType("unknown");
        config.setPlugins(List.of(plugin));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> registry.createTools(config));
        assertEquals("Unknown plugin type: unknown", error.getMessage());
    }

    @Test
    void doesNotInstantiateUnconfiguredProviders() {
        var registry = new PluginRegistry(List.of(
                provider(ExplodingPlugin.class, () -> {
                    throw new AssertionError("should not instantiate unrelated provider");
                }),
                provider(TestPlugin.class, TestPlugin::new)));
        var config = new OpenClawConfig();

        var plugin = new OpenClawConfig.PluginConfig();
        plugin.setType("test");
        config.setPlugins(List.of(plugin));

        assertEquals(List.of(), registry.createTools(config));
    }

    @Test
    void createsQmdToolWhenCommandLooksValid() throws IOException {
        var registry = new PluginRegistry(List.of(provider(QmdPlugin.class, QmdPlugin::new)));
        var config = new OpenClawConfig();

        var plugin = new OpenClawConfig.PluginConfig();
        plugin.setType("qmd");

        ObjectNode pluginConfig = Json.mapper().createObjectNode();
        pluginConfig.put("command", fakeQmd().toString());
        plugin.setConfig(pluginConfig);
        config.setPlugins(List.of(plugin));

        List<Tool> tools = registry.createTools(config);

        assertEquals(1, tools.size());
        assertEquals("qmd_memory", tools.get(0).name());
    }

    @Test
    void rejectsNonQmdCommand() {
        var registry = new PluginRegistry(List.of(provider(QmdPlugin.class, QmdPlugin::new)));
        var config = new OpenClawConfig();

        var plugin = new OpenClawConfig.PluginConfig();
        plugin.setType("qmd");

        ObjectNode pluginConfig = Json.mapper().createObjectNode();
        pluginConfig.put("command", "sh");
        plugin.setConfig(pluginConfig);
        config.setPlugins(List.of(plugin));

        var error = assertThrows(IllegalArgumentException.class, () -> registry.createTools(config));
        assertEquals("QMD plugin requires a working qmd command: sh", error.getMessage());
    }

    private static ServiceLoader.Provider<ToolPlugin> provider(
            Class<? extends ToolPlugin> type, Supplier<ToolPlugin> supplier) {
        return new ServiceLoader.Provider<>() {
            @Override
            public Class<? extends ToolPlugin> type() {
                return type;
            }

            @Override
            public ToolPlugin get() {
                return supplier.get();
            }
        };
    }

    private static Path fakeQmd() throws IOException {
        var script = Files.createTempFile("fake-qmd", ".sh");
        Files.writeString(script, "#!/bin/sh\nif [ \"$1\" = \"--help\" ]; then\n  echo 'qmd help'\n  exit 0\nfi\necho 'qmd'\n");
        script.toFile().setExecutable(true);
        return script;
    }

    @PluginType("test")
    static final class TestPlugin implements ToolPlugin {
        @Override
        public List<Tool> createTools(OpenClawConfig.PluginConfig pluginConfig) {
            return List.of();
        }
    }

    @PluginType("other")
    static final class ExplodingPlugin implements ToolPlugin {
        @Override
        public List<Tool> createTools(OpenClawConfig.PluginConfig pluginConfig) {
            return List.of();
        }
    }
}
