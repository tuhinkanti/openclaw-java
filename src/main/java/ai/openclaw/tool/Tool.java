package ai.openclaw.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Interface that all agent tools implement.
 */
public interface Tool {
    /** Unique name for this tool (sent to the LLM). */
    String name();

    /** Human-readable description of what this tool does. */
    String description();

    /** JSON Schema describing the expected input parameters. */
    JsonNode inputSchema();

    /** Execute the tool with the given input and return the result. */
    ToolResult execute(JsonNode input);
}
