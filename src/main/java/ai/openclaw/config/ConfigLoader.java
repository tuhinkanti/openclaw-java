package ai.openclaw.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigLoader {
    private static final Path CONFIG_PATH = Paths.get(System.getProperty("user.home"), ".openclaw-java", "config.json");

    public static OpenClawConfig load() throws IOException {
        File configFile = CONFIG_PATH.toFile();
        if (!configFile.exists()) {
            throw new IOException("Config file not found: " + CONFIG_PATH);
        }
        return Json.mapper().readValue(configFile, OpenClawConfig.class);
    }
}
