package ai.openclaw.agent;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured response from the LLM that can contain text and/or tool-use
 * requests.
 */
public class LlmResponse {
    private final String stopReason;
    private final List<ContentBlock> content;

    public LlmResponse(String stopReason, List<ContentBlock> content) {
        this.stopReason = stopReason;
        this.content = content;
    }

    public String getStopReason() {
        return stopReason;
    }

    public List<ContentBlock> getContent() {
        return content;
    }

    /** Returns true if the LLM wants to use one or more tools. */
    public boolean hasToolUse() {
        return "tool_use".equals(stopReason);
    }

    /** Extracts all text blocks concatenated into a single string. */
    public String getTextContent() {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : content) {
            if ("text".equals(block.getType()) && block.getText() != null) {
                if (sb.length() > 0)
                    sb.append("\n");
                sb.append(block.getText());
            }
        }
        return sb.toString();
    }

    /** Returns only the tool-use content blocks. */
    public List<ContentBlock> getToolUseBlocks() {
        List<ContentBlock> toolBlocks = new ArrayList<>();
        for (ContentBlock block : content) {
            if ("tool_use".equals(block.getType())) {
                toolBlocks.add(block);
            }
        }
        return toolBlocks;
    }

    /**
     * A single content block in the LLM response — either text or tool_use.
     */
    public static class ContentBlock {
        private final String type;
        private final String text;
        private final String toolUseId;
        private final String toolName;
        private final JsonNode toolInput;

        private ContentBlock(String type, String text, String toolUseId, String toolName, JsonNode toolInput) {
            this.type = type;
            this.text = text;
            this.toolUseId = toolUseId;
            this.toolName = toolName;
            this.toolInput = toolInput;
        }

        public static ContentBlock text(String text) {
            return new ContentBlock("text", text, null, null, null);
        }

        public static ContentBlock toolUse(String toolUseId, String toolName, JsonNode toolInput) {
            return new ContentBlock("tool_use", null, toolUseId, toolName, toolInput);
        }

        public String getType() {
            return type;
        }

        public String getText() {
            return text;
        }

        public String getToolUseId() {
            return toolUseId;
        }

        public String getToolName() {
            return toolName;
        }

        public JsonNode getToolInput() {
            return toolInput;
        }
    }
}
