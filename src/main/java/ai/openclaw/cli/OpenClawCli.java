package ai.openclaw.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "openclaw-java", mixinStandardHelpOptions = true, version = "1.0", description = "OpenClaw Java MVP", subcommands = {
        GatewayCommand.class, SendCommand.class })
public class OpenClawCli implements Runnable {

    @Override
    public void run() {
        System.out.println("Use 'gateway' to start the server or 'send' to send a message.");
    }
}
