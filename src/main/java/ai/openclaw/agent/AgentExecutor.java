package ai.openclaw.agent;

import ai.openclaw.config.Json;
import ai.openclaw.config.OpenClawConfig;
import ai.openclaw.session.Message;
import ai.openclaw.session.Session;
import ai.openclaw.session.SessionStore;
import ai.openclaw.tool.Tool;
import ai.openclaw.tool.ToolResult;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgentExecutor {
    private static final Logger logger = LoggerFactory.getLogger(AgentExecutor.class);
    private static final int MAX_TOOL_ITERATIONS = 10;

    private final OpenClawConfig config;
    private final SessionStore sessionStore;
    private final LlmProvider llmProvider;
    private final SystemPromptBuilder promptBuilder;
    private final List<Tool> tools;
    private final Map<String, Tool> toolMap;

    public AgentExecutor(OpenClawConfig config, SessionStore sessionStore, LlmProvider llmProvider) {
        this(config, sessionStore, llmProvider, List.of());
    }

    public AgentExecutor(OpenClawConfig config, SessionStore sessionStore, LlmProvider llmProvider, List<Tool> tools) {
        this.config = config;
        this.sessionStore = sessionStore;
        this.llmProvider = llmProvider;
        this.promptBuilder = new SystemPromptBuilder(config);
        this.tools = tools;
        this.toolMap = new HashMap<>();
        for (Tool tool : tools) {
            this.toolMap.put(tool.name(), tool);
        }
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

        // 3. Run the agentic loop
        String responseText;
        try {
            responseText = runAgentLoop(session);
        } catch (Exception e) {
            logger.error("Agent loop failed", e);
            responseText = "Error: " + e.getMessage();
        }

        // 4. Append final Assistant Message
        Message assistantMsg = new Message("assistant", responseText);
        sessionStore.appendMessage(sessionId, assistantMsg);

        return responseText;
    }

    private String runAgentLoop(Session session) throws Exception {
        String model = config.getAgent().getModel();

        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            // Build context from session history
            List<Message> context = new ArrayList<>();
            context.add(new Message("system", promptBuilder.build()));
            context.addAll(session.getMessages());

            // Call LLM with tools
            LlmResponse response;
            if (!tools.isEmpty()) {
                response = llmProvider.completeWithTools(context, model, tools);
            } else {
                String text = llmProvider.complete(context, model);
                return text;
            }

            if (!response.hasToolUse()) {
                // No tool use — return the text content
                return response.getTextContent();
            }

            // The LLM wants to use tools
            logger.info("Tool use requested (iteration {})", iteration + 1);

            // Store the assistant's response (with tool_use blocks) in the session
            // so it can be replayed in the next API call
            ArrayNode contentBlocksJson = serializeContentBlocks(response.getContent());
            Message assistantToolMsg = Message.assistantToolUse(contentBlocksJson);
            session.addMessage(assistantToolMsg);

            // Execute each requested tool and add results to session
            for (LlmResponse.ContentBlock block : response.getToolUseBlocks()) {
                Tool tool = toolMap.get(block.getToolName());
                ToolResult result;
                if (tool != null) {
                    logger.info("Executing tool: {} (id: {})", block.getToolName(), block.getToolUseId());
                    result = tool.execute(block.getToolInput());
                } else {
                    logger.warn("Unknown tool requested: {}", block.getToolName());
                    result = ToolResult.error("Unknown tool: " + block.getToolName());
                }

                Message toolResultMsg = Message.toolResult(
                        block.getToolUseId(),
                        result.getOutput(),
                        result.isError());
                session.addMessage(toolResultMsg);
            }

            // Loop back — the next iteration will include the tool results in context
        }

        logger.warn("Agent loop hit max iterations ({})", MAX_TOOL_ITERATIONS);
        return "I've reached the maximum number of tool use steps. Here's what I have so far — please try rephrasing your request if you need more.";
    }

    /** Serialize content blocks back to the JSON format Anthropic expects. */
    private ArrayNode serializeContentBlocks(List<LlmResponse.ContentBlock> blocks) {
        ArrayNode array = Json.mapper().createArrayNode();
        for (LlmResponse.ContentBlock block : blocks) {
            ObjectNode node = array.addObject();
            if ("text".equals(block.getType())) {
                node.put("type", "text");
                node.put("text", block.getText());
            } else if ("tool_use".equals(block.getType())) {
                node.put("type", "tool_use");
                node.put("id", block.getToolUseId());
                node.put("name", block.getToolName());
                node.set("input", block.getToolInput());
            }
        }
        return array;
    }
}
