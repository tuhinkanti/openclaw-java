package ai.openclaw.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenClawConfig {
    private GatewayConfig gateway;
    private AgentConfig agent;

    public GatewayConfig getGateway() {
        return gateway;
    }

    public void setGateway(GatewayConfig gateway) {
        this.gateway = gateway;
    }

    public AgentConfig getAgent() {
        return agent;
    }

    public void setAgent(AgentConfig agent) {
        this.agent = agent;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GatewayConfig {
        private int port = 18789;
        private String authToken;

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getAuthToken() {
            return authToken;
        }

        public void setAuthToken(String authToken) {
            this.authToken = authToken;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AgentConfig {
        private String provider = "anthropic";
        private String apiKey;
        private String model = "claude-sonnet-4-20250514";
        private String systemPrompt;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }
    }
}
