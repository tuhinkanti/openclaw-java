package ai.openclaw.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * A message in a session conversation.
 * Supports plain text messages and tool-related messages.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Message {
    private String role;
    private String content;
    private Instant timestamp;

    // Tool-use fields
    private String toolUseId;
    private boolean toolError;
    private JsonNode contentBlocks; // For assistant messages that contain tool_use blocks

    public Message() {
    }

    public Message(String role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = Instant.now();
    }

    public Message(String role, String content, Instant timestamp) {
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
    }

    /** Create a tool_result message. */
    public static Message toolResult(String toolUseId, String content, boolean isError) {
        Message msg = new Message("tool_result", content);
        msg.toolUseId = toolUseId;
        msg.toolError = isError;
        return msg;
    }

    /**
     * Create an assistant message that includes tool_use content blocks (for replay
     * to API).
     */
    public static Message assistantToolUse(JsonNode contentBlocks) {
        Message msg = new Message("assistant_tool_use", null);
        msg.contentBlocks = contentBlocks;
        return msg;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getToolUseId() {
        return toolUseId;
    }

    public void setToolUseId(String toolUseId) {
        this.toolUseId = toolUseId;
    }

    public boolean isToolError() {
        return toolError;
    }

    public void setToolError(boolean toolError) {
        this.toolError = toolError;
    }

    public JsonNode getContentBlocks() {
        return contentBlocks;
    }

    public void setContentBlocks(JsonNode contentBlocks) {
        this.contentBlocks = contentBlocks;
    }
}
