package ai.openclaw.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigLoader {
    private static final Path CONFIG_PATH = Paths.get(System.getProperty("user.home"), ".openclaw-java", "config.json");

    public static OpenClawConfig load() throws IOException {
        File configFile = CONFIG_PATH.toFile();
        OpenClawConfig config;

        if (configFile.exists()) {
            config = Json.mapper().readValue(configFile, OpenClawConfig.class);
        } else {
            String apiKey = System.getenv("ANTHROPIC_API_KEY");
            if (apiKey == null || apiKey.isEmpty()) {
                apiKey = System.getenv("ANTHROPIC_AUTH_TOKEN");
            }
            if (apiKey != null && !apiKey.isEmpty()) {
                config = new OpenClawConfig();
                config.setGateway(new OpenClawConfig.GatewayConfig());
                config.setAgent(new OpenClawConfig.AgentConfig());
                config.getAgent().setApiKey(apiKey);
            } else {
                throw new IOException(
                        "Config file not found: " + CONFIG_PATH + " and ANTHROPIC_API_KEY/ANTHROPIC_AUTH_TOKEN env var is not set.");
            }
        }

        // Allow environment variables to override config
        // Support both ANTHROPIC_API_KEY and ANTHROPIC_AUTH_TOKEN (Salesforce LLM Gateway)
        String envKey = System.getenv("ANTHROPIC_API_KEY");
        if (envKey == null || envKey.isEmpty()) {
            envKey = System.getenv("ANTHROPIC_AUTH_TOKEN");
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

        // Support both ANTHROPIC_BASE_URL and ANTHROPIC_BEDROCK_BASE_URL (Salesforce LLM Gateway)
        String envBaseUrl = System.getenv("ANTHROPIC_BEDROCK_BASE_URL");
        if (envBaseUrl == null || envBaseUrl.isEmpty()) {
            envBaseUrl = System.getenv("ANTHROPIC_BASE_URL");
        }
        if (envBaseUrl != null && !envBaseUrl.isEmpty()) {
            if (config.getAgent() == null)
                config.setAgent(new OpenClawConfig.AgentConfig());
            config.getAgent().setBaseUrl(envBaseUrl);
        }

        String slackBotToken = System.getenv("SLACK_BOT_TOKEN");
        String slackAppToken = System.getenv("SLACK_APP_TOKEN");
        if (slackBotToken != null && !slackBotToken.isEmpty()) {
            if (config.getSlack() == null)
                config.setSlack(new OpenClawConfig.SlackConfig());
            config.getSlack().setBotToken(slackBotToken);
        }
        if (slackAppToken != null && !slackAppToken.isEmpty()) {
            if (config.getSlack() == null)
                config.setSlack(new OpenClawConfig.SlackConfig());
            config.getSlack().setAppToken(slackAppToken);
        }

        return config;
    }
}
