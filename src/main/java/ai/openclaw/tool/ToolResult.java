package ai.openclaw.tool;

/**
 * Result of a tool execution.
 */
public class ToolResult {
    private final String output;
    private final boolean isError;
    private final int exitCode;

    public ToolResult(String output, boolean isError, int exitCode) {
        this.output = output;
        this.isError = isError;
        this.exitCode = exitCode;
    }

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
        return isError;
    }

    public int getExitCode() {
        return exitCode;
    }
}
