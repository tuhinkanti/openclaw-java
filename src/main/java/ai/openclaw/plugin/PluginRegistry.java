package ai.openclaw.plugin;

import ai.openclaw.config.OpenClawConfig;
import ai.openclaw.tool.Tool;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Stream;

/**
 * Resolves configured plugins into agent tools via ServiceLoader.
 */
public class PluginRegistry {
    private static final Path DEFAULT_PLUGIN_DIR = Paths.get(System.getProperty("user.home"), ".openclaw-java", "plugins");

    private final List<ServiceLoader.Provider<ToolPlugin>> providers;
    private final HashMap<String, ToolPlugin> cache = new HashMap<>();

    public PluginRegistry() {
        this(loadPlugins());
    }

    PluginRegistry(List<ServiceLoader.Provider<ToolPlugin>> providers) {
        this.providers = List.copyOf(providers);
    }

    public List<Tool> createTools(OpenClawConfig config) {
        return config.getPlugins().stream()
                .filter(OpenClawConfig.PluginConfig::isEnabled)
                .flatMap(pluginConfig -> requirePlugin(pluginConfig).createTools(pluginConfig).stream())
                .toList();
    }

    private ToolPlugin requirePlugin(OpenClawConfig.PluginConfig config) {
        var type = config.getType();
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Plugin entry is missing required field: type");
        }

        var cached = cache.get(type);
        if (cached != null) {
            return cached;
        }

        for (var provider : providers) {
            if (!type.equals(pluginType(provider.type()))) {
                continue;
            }

            try {
                var plugin = provider.get();
                cache.put(type, plugin);
                return plugin;
            } catch (RuntimeException e) {
                throw new IllegalStateException("Failed to load plugin type: " + type, e);
            }
        }

        throw new IllegalArgumentException("Unknown plugin type: " + type);
    }

    private static List<ServiceLoader.Provider<ToolPlugin>> loadPlugins() {
        var loader = ServiceLoader.load(ToolPlugin.class, pluginClassLoader());
        return loader.stream().toList();
    }

    private static ClassLoader pluginClassLoader() {
        var externalJars = externalPluginJars();
        if (externalJars.isEmpty()) {
            return PluginRegistry.class.getClassLoader();
        }
        return new URLClassLoader(externalJars.toArray(URL[]::new), PluginRegistry.class.getClassLoader());
    }

    private static List<URL> externalPluginJars() {
        if (!Files.isDirectory(DEFAULT_PLUGIN_DIR)) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(DEFAULT_PLUGIN_DIR)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .map(PluginRegistry::toUrl)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read plugin directory: " + DEFAULT_PLUGIN_DIR, e);
        }
    }

    private static URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (IOException e) {
            throw new IllegalStateException("Invalid plugin jar path: " + path, e);
        }
    }

    private static String pluginType(Class<? extends ToolPlugin> pluginClass) {
        var annotation = pluginClass.getAnnotation(PluginType.class);
        return annotation == null ? null : annotation.value();
    }
}
