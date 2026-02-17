package ai.openclaw.channel.console;

import ai.openclaw.channel.Channel;
import ai.openclaw.agent.AgentExecutor;
import ai.openclaw.session.Session;
import ai.openclaw.session.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

public class ConsoleChannel implements Channel {
    private static final Logger logger = LoggerFactory.getLogger(ConsoleChannel.class);
    private final AgentExecutor agentExecutor;
    private final SessionStore sessionStore;
    private boolean running = false;
    private Session defaultSession;

    public ConsoleChannel(AgentExecutor agentExecutor, SessionStore sessionStore) {
        this.agentExecutor = agentExecutor;
        this.sessionStore = sessionStore;
        this.defaultSession = sessionStore.createSession("console", "local-user");
    }

    @Override
    public String getChannelType() {
        return "console";
    }

    @Override
    public void start() {
        // Run in separate thread
        Thread t = new Thread(this, "console-channel");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public void sendMessage(String sessionId, String text) {
        System.out.println("🤖 Assistant: " + text);
    }

    @Override
    public void run() {
        running = true;
        Scanner scanner = new Scanner(System.in);
        System.out.println("🦞 OpenClaw Java MVP - Console Channel");
        System.out.println("Type 'exit' to quit.");
        System.out.println("Session ID: " + defaultSession.getId());
        System.out.println("----------------------------------------");

        while (running && scanner.hasNextLine()) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                running = false;
                System.exit(0);
                break;
            }

            if (!input.isEmpty()) {
                try {
                    String response = agentExecutor.execute(defaultSession.getId(), input);
                    sendMessage(defaultSession.getId(), response);
                } catch (Exception e) {
                    logger.error("Error executing agent", e);
                    System.err.println("Error: " + e.getMessage());
                }
            }
        }
    }
}
