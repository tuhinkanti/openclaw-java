package ai.openclaw.agent;

import ai.openclaw.session.Message;
import ai.openclaw.tool.Tool;
import java.util.List;

public interface LlmProvider {
    /** Simple text-only completion (no tools). */
    String complete(List<Message> messages, String model) throws Exception;

    /**
     * Completion with tool definitions — returns structured response with possible
     * tool_use blocks.
     */
    LlmResponse completeWithTools(List<Message> messages, String model, List<Tool> tools) throws Exception;

    String providerName();
}
