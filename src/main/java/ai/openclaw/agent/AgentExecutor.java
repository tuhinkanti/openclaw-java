package ai.openclaw.agent;

import ai.openclaw.config.OpenClawConfig;
import ai.openclaw.session.Message;
import ai.openclaw.session.Session;
import ai.openclaw.session.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class AgentExecutor {
    private static final Logger logger = LoggerFactory.getLogger(AgentExecutor.class);
    private final OpenClawConfig config;
    private final SessionStore sessionStore;
    private final LlmProvider llmProvider;
    private final SystemPromptBuilder promptBuilder;

    public AgentExecutor(OpenClawConfig config, SessionStore sessionStore, LlmProvider llmProvider) {
        this.config = config;
        this.sessionStore = sessionStore;
        this.llmProvider = llmProvider;
        this.promptBuilder = new SystemPromptBuilder(config);
    }

    public String execute(String sessionId, String userMessage) {
        // 1. Get Session
        Session session = sessionStore.getSession(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        // 2. Append User Message
        Message userMsg = new Message("user", userMessage);
        sessionStore.appendMessage(sessionId, userMsg);

        // 3. Build Context
        List<Message> context = new ArrayList<>();
        // System Prompt
        context.add(new Message("system", promptBuilder.build()));
        // History
        context.addAll(session.getMessages());

        // 4. Call LLM
        String responseText;
        try {
            String model = config.getAgent().getModel();
            responseText = llmProvider.complete(context, model);
        } catch (Exception e) {
            logger.error("LLM Provider failed", e);
            responseText = "Error: " + e.getMessage();
        }

        // 5. Append Assistant Message
        Message assistantMsg = new Message("assistant", responseText);
        sessionStore.appendMessage(sessionId, assistantMsg);

        return responseText;
    }
}
