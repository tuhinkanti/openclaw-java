package ai.openclaw.agent;

import ai.openclaw.config.OpenClawConfig;

public class LlmProviderFactory {

    public static LlmProvider create(OpenClawConfig.AgentConfig agentConfig) {
        return switch (agentConfig.getProvider()) {
            case "anthropic" -> new AnthropicProvider(agentConfig);
            default -> throw new IllegalArgumentException("Unknown LLM provider: " + agentConfig.getProvider());
        };
    }
}
