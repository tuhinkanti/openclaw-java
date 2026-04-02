package ai.openclaw.tool;

/**
 * Result of a tool execution.
 */
public record ToolResult(String output, boolean error, int exitCode) {
    public static ToolResult success(String output) {
        return new ToolResult(output, false, 0);
    }

    public static ToolResult error(String output) {
        return new ToolResult(output, true, 1);
    }

    public String getOutput() {
        return output;
    }

    public boolean isError() {
        return error;
    }

    public int getExitCode() {
        return exitCode;
    }
}
