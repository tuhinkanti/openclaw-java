package ai.openclaw.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenClawConfig {
    private GatewayConfig gateway;
    private AgentConfig agent;
    private SlackConfig slack;

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

    public SlackConfig getSlack() {
        return slack;
    }

    public void setSlack(SlackConfig slack) {
        this.slack = slack;
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
    public static class SlackConfig {
        private String botToken;
        private String appToken;

        public String getBotToken() {
            return botToken;
        }

        public void setBotToken(String botToken) {
            this.botToken = botToken;
        }

        public String getAppToken() {
            return appToken;
        }

        public void setAppToken(String appToken) {
            this.appToken = appToken;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AgentConfig {
        private String provider = "anthropic";
        private String apiKey;
        private String model = "claude-sonnet-4-20250514";
        private String baseUrl;
        private String systemPrompt;
        private int maxTokens = 4096;
        private int maxIterations = 10;
        private int llmTimeoutSeconds = 120;
        private int retryMaxAttempts = 3;
        private long retryInitialDelayMs = 1000;
        private boolean ralphMode = false;
        private int ralphMaxIterations = 50;
        private String ralphCompletionPromise = "TASK_COMPLETE";

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

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public int getMaxIterations() {
            return maxIterations;
        }

        public void setMaxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
        }

        public int getLlmTimeoutSeconds() {
            return llmTimeoutSeconds;
        }

        public void setLlmTimeoutSeconds(int llmTimeoutSeconds) {
            this.llmTimeoutSeconds = llmTimeoutSeconds;
        }

        public int getRetryMaxAttempts() {
            return retryMaxAttempts;
        }

        public void setRetryMaxAttempts(int retryMaxAttempts) {
            this.retryMaxAttempts = retryMaxAttempts;
        }

        public long getRetryInitialDelayMs() {
            return retryInitialDelayMs;
        }

        public void setRetryInitialDelayMs(long retryInitialDelayMs) {
            this.retryInitialDelayMs = retryInitialDelayMs;
        }

        public boolean isRalphMode() {
            return ralphMode;
        }

        public void setRalphMode(boolean ralphMode) {
            this.ralphMode = ralphMode;
        }

        public int getRalphMaxIterations() {
            return ralphMaxIterations;
        }

        public void setRalphMaxIterations(int ralphMaxIterations) {
            this.ralphMaxIterations = ralphMaxIterations;
        }

        public String getRalphCompletionPromise() {
            return ralphCompletionPromise;
        }

        public void setRalphCompletionPromise(String ralphCompletionPromise) {
            this.ralphCompletionPromise = ralphCompletionPromise;
        }
    }
}
