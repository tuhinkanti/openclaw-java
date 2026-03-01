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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class AgentExecutor {
    private static final Logger logger = LoggerFactory.getLogger(AgentExecutor.class);
    private static final int CONTEXT_TOKEN_BUDGET = 180_000;
    private static final int CHARS_PER_TOKEN = 4;

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
        Session session = sessionStore.getSession(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        Message userMsg = new Message("user", userMessage);
        sessionStore.appendMessage(sessionId, userMsg);

        String responseText;
        try {
            responseText = runAgentLoop(sessionId, session);
        } catch (Exception e) {
            logger.error("Agent loop failed", e);
            responseText = "Error: " + e.getMessage();
        }

        Message assistantMsg = new Message("assistant", responseText);
        sessionStore.appendMessage(sessionId, assistantMsg);

        return responseText;
    }

    public String executeRalph(String sessionId, String taskPrompt) {
        String completionPromise = config.getAgent().getRalphCompletionPromise();
        int maxOuter = config.getAgent().getRalphMaxIterations();

        String response = execute(sessionId, taskPrompt);

        for (int i = 1; i < maxOuter; i++) {
            if (response.contains(completionPromise)) {
                logger.info("Ralph loop completed at iteration {} — completion promise found", i);
                return response;
            }

            logger.info("Ralph loop iteration {} — no completion promise, continuing", i + 1);
            String continuePrompt = "Continue working on the task. Review what you've done so far, "
                    + "check for errors or incomplete work, and keep going. "
                    + "Output '" + completionPromise + "' when the task is fully complete.";
            response = execute(sessionId, continuePrompt);
        }

        logger.warn("Ralph loop hit max iterations ({})", maxOuter);
        return response;
    }

    private String runAgentLoop(String sessionId, Session session) throws Exception {
        String model = config.getAgent().getModel();
        int maxIterations = config.getAgent().getMaxIterations();

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            List<Message> context = buildContext(session);

            LlmResponse response;
            if (!tools.isEmpty()) {
                response = llmProvider.completeWithTools(context, model, tools);
            } else {
                String text = llmProvider.complete(context, model);
                return text;
            }

            if (!response.hasToolUse()) {
                return response.getTextContent();
            }

            logger.info("Tool use requested (iteration {})", iteration + 1);

            ArrayNode contentBlocksJson = serializeContentBlocks(response.getContent());
            Message assistantToolMsg = Message.assistantToolUse(contentBlocksJson);
            sessionStore.appendMessage(sessionId, assistantToolMsg);

            List<LlmResponse.ContentBlock> toolBlocks = response.getToolUseBlocks();
            List<ToolResult> results;

            if (toolBlocks.size() == 1) {
                results = List.of(executeTool(toolBlocks.get(0)));
            } else {
                results = executeToolsInParallel(toolBlocks);
            }

            for (int i = 0; i < toolBlocks.size(); i++) {
                LlmResponse.ContentBlock block = toolBlocks.get(i);
                ToolResult result = results.get(i);
                Message toolResultMsg = Message.toolResult(
                        block.getToolUseId(),
                        result.getOutput(),
                        result.isError());
                sessionStore.appendMessage(sessionId, toolResultMsg);
            }
        }

        logger.warn("Agent loop hit max iterations ({})", maxIterations);
        return "I've reached the maximum number of tool use steps. Here's what I have so far — please try rephrasing your request if you need more.";
    }

    private List<Message> buildContext(Session session) {
        Message systemMsg = new Message("system", promptBuilder.build());
        List<Message> sessionMessages = session.getMessages();

        int systemTokens = estimateTokens(systemMsg.getContent());
        int budget = CONTEXT_TOKEN_BUDGET - systemTokens;

        List<Message> truncated = new ArrayList<>();
        int totalTokens = 0;

        for (int i = sessionMessages.size() - 1; i >= 0; i--) {
            Message msg = sessionMessages.get(i);
            int msgTokens = estimateTokens(msg.getContent());
            if (totalTokens + msgTokens > budget) {
                break;
            }
            truncated.add(0, msg);
            totalTokens += msgTokens;
        }

        if (truncated.size() < sessionMessages.size()) {
            int dropped = sessionMessages.size() - truncated.size();
            logger.info("Context truncation: dropped {} oldest messages to fit token budget", dropped);
            Message notice = new Message("user",
                    "[System: " + dropped + " earlier messages were truncated to fit context window]");
            truncated.add(0, notice);
        }

        List<Message> context = new ArrayList<>();
        context.add(systemMsg);
        context.addAll(truncated);
        return context;
    }

    private int estimateTokens(String text) {
        if (text == null) return 0;
        return text.length() / CHARS_PER_TOKEN;
    }

    private ToolResult executeTool(LlmResponse.ContentBlock block) {
        Tool tool = toolMap.get(block.getToolName());
        if (tool == null) {
            logger.warn("Unknown tool requested: {}", block.getToolName());
            return ToolResult.error("Unknown tool: " + block.getToolName());
        }
        try {
            logger.info("Executing tool: {} (id: {})", block.getToolName(), block.getToolUseId());
            return tool.execute(block.getToolInput());
        } catch (Exception e) {
            logger.error("Tool execution failed: {} — {}", block.getToolName(), e.getMessage(), e);
            return ToolResult.error("Tool execution failed: " + e.getMessage());
        }
    }

    private List<ToolResult> executeToolsInParallel(List<LlmResponse.ContentBlock> toolBlocks) {
        logger.info("Executing {} tools in parallel", toolBlocks.size());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<ToolResult>> futures = new ArrayList<>();
            for (LlmResponse.ContentBlock block : toolBlocks) {
                futures.add(executor.submit(() -> executeTool(block)));
            }

            List<ToolResult> results = new ArrayList<>();
            for (Future<ToolResult> future : futures) {
                try {
                    results.add(future.get(60, TimeUnit.SECONDS));
                } catch (Exception e) {
                    logger.error("Parallel tool execution failed: {}", e.getMessage(), e);
                    results.add(ToolResult.error("Tool execution timed out or failed: " + e.getMessage()));
                }
            }
            return results;
        }
    }

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
