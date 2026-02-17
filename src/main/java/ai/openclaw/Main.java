package ai.openclaw;

import ai.openclaw.cli.OpenClawCli;
import picocli.CommandLine;

public class Main {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new OpenClawCli()).execute(args);
        System.exit(exitCode);
    }
}
