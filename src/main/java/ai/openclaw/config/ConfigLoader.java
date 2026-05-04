package ai.openclaw.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigLoader {
    private static final Path CONFIG_PATH = Paths.get(System.getProperty("user.home"), ".openclaw-java", "config.json");
    private static final String DEFAULT_PROVIDER = "openai";
    private static final String DEFAULT_OPENAI_MODEL = "gpt-4o-mini";
    private static final String DEFAULT_ANTHROPIC_MODEL = "claude-sonnet-4-20250514";


    public static OpenClawConfig load() throws IOException {
        File configFile = CONFIG_PATH.toFile();
        OpenClawConfig config;

        if (configFile.exists()) {
            config = Json.mapper().readValue(configFile, OpenClawConfig.class);
        } else {
            String apiKey = System.getenv("OPENAI_API_KEY");
            if (apiKey == null || apiKey.isEmpty()) {
                apiKey = System.getenv("ANTHROPIC_API_KEY");
            }
            if (apiKey != null && !apiKey.isEmpty()) {
                config = new OpenClawConfig();
                config.setGateway(new OpenClawConfig.GatewayConfig());
                config.setAgent(new OpenClawConfig.AgentConfig());
                config.getAgent().setApiKey(apiKey);
            } else {
                throw new IOException(
                        "Config file not found: " + CONFIG_PATH + " and OPENAI_API_KEY/ANTHROPIC_API_KEY env vars are not set.");
            }
        }

        // Allow environment variables to override config
        String envKey = System.getenv("OPENAI_API_KEY");
        if (envKey == null || envKey.isEmpty()) {
            envKey = System.getenv("ANTHROPIC_API_KEY");
        }
        if (envKey != null && !envKey.isEmpty()) {
            if (config.getAgent() == null)
                config.setAgent(new OpenClawConfig.AgentConfig());
            config.getAgent().setApiKey(envKey);
        }

        String envPort = System.getenv("GATEWAY_PORT");
        if (envPort != null && !envPort.isEmpty()) {
            if (config.getGateway() == null)
                config.setGateway(new OpenClawConfig.GatewayConfig());
            try {
                config.getGateway().setPort(Integer.parseInt(envPort));
            } catch (NumberFormatException ignored) {
            }
        }

        String envToken = System.getenv("GATEWAY_AUTH_TOKEN");
        if (envToken != null && !envToken.isEmpty()) {
            if (config.getGateway() == null)
                config.setGateway(new OpenClawConfig.GatewayConfig());
            config.getGateway().setAuthToken(envToken);
        }

        applyAgentDefaults(config);
        return config;
    }

    private static void applyAgentDefaults(OpenClawConfig config) {
        if (config.getAgent() == null) {
            config.setAgent(new OpenClawConfig.AgentConfig());
        }

        String envProvider = System.getenv("LLM_PROVIDER");
        if (envProvider != null && !envProvider.isEmpty()) {
            config.getAgent().setProvider(envProvider.toLowerCase());
        }

        if (config.getAgent().getProvider() == null || config.getAgent().getProvider().isEmpty()) {
            config.getAgent().setProvider(DEFAULT_PROVIDER);
        }

        if (config.getAgent().getModel() == null || config.getAgent().getModel().isEmpty()) {
            if ("anthropic".equalsIgnoreCase(config.getAgent().getProvider())) {
                config.getAgent().setModel(DEFAULT_ANTHROPIC_MODEL);
            } else {
                config.getAgent().setModel(DEFAULT_OPENAI_MODEL);
            }
        }
    }
}
