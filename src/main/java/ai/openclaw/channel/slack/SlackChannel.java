package ai.openclaw.channel.slack;

import ai.openclaw.agent.AgentExecutor;
import ai.openclaw.channel.Channel;
import ai.openclaw.config.OpenClawConfig;
import ai.openclaw.session.Session;
import ai.openclaw.session.SessionStore;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.model.event.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SlackChannel implements Channel {
    private static final Logger logger = LoggerFactory.getLogger(SlackChannel.class);
    private final AgentExecutor agentExecutor;
    private final SessionStore sessionStore;
    private final OpenClawConfig config;
    private final Map<String, Session> userSessions = new ConcurrentHashMap<>();
    private SocketModeApp socketModeApp;
    private volatile boolean running = false;

    public SlackChannel(AgentExecutor agentExecutor, SessionStore sessionStore, OpenClawConfig config) {
        this.agentExecutor = agentExecutor;
        this.sessionStore = sessionStore;
        this.config = config;
    }

    @Override
    public String getChannelType() {
        return "slack";
    }

    @Override
    public void start() {
        Thread t = new Thread(this, "slack-channel");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void stop() {
        running = false;
        if (socketModeApp != null) {
            try {
                socketModeApp.close();
            } catch (Exception e) {
                logger.error("Error closing Slack Socket Mode app", e);
            }
        }
    }

    @Override
    public void sendMessage(String sessionId, String text) {
        // Messages are sent inline in the event handler via ctx.say()
        // This method exists to satisfy the Channel interface
        logger.debug("sendMessage called for session {}", sessionId);
    }

    @Override
    public void run() {
        running = true;
        try {
            OpenClawConfig.SlackConfig slackConfig = config.getSlack();
            AppConfig appConfig = AppConfig.builder()
                    .singleTeamBotToken(slackConfig.getBotToken())
                    .build();
            App app = new App(appConfig);

            app.event(MessageEvent.class, (payload, ctx) -> {
                MessageEvent event = payload.getEvent();
                String userId = event.getUser();
                String text = event.getText();
                String channelId = event.getChannel();

                // Ignore bot/system messages:
                // - subtype is set on bot_message, message_changed, message_deleted, etc.
                // - bot_id is always set when a bot (including ourselves) posted the message
                if (event.getSubtype() != null || event.getBotId() != null) {
                    logger.debug("Ignoring bot/system message (subtype={}, botId={})",
                            event.getSubtype(), event.getBotId());
                    return ctx.ack();
                }

                // Ignore messages with no user or empty text
                if (userId == null || text == null || text.isBlank()) {
                    return ctx.ack();
                }

                // Ignore messages from the bot's own user account
                if (userId.equals(ctx.getBotUserId())) {
                    return ctx.ack();
                }

                // Strip bot mention tags (e.g. "<@U0A6H41J538> hello" -> "hello")
                String cleanText = text.replaceAll("<@[A-Z0-9]+>\\s*", "").trim();
                if (cleanText.isEmpty()) {
                    return ctx.ack();
                }

                logger.info("Slack message from user {} in channel {}: {}", userId, channelId, cleanText);

                try {
                    Session session = getOrCreateSession(userId);
                    String response = agentExecutor.execute(session.getId(), cleanText);
                    ctx.client().chatPostMessage(r -> r
                            .channel(channelId)
                            .text(response));
                } catch (Exception e) {
                    logger.error("Error processing Slack message from user {}", userId, e);
                    ctx.say("Sorry, I encountered an error processing your message.");
                }

                return ctx.ack();
            });

            socketModeApp = new SocketModeApp(slackConfig.getAppToken(), app);
            logger.info("Starting Slack channel in Socket Mode");
            System.out.println("Slack channel connected");
            socketModeApp.start();
        } catch (Exception e) {
            logger.error("Failed to start Slack channel", e);
            System.err.println("Failed to start Slack channel: " + e.getMessage());
        }
    }

    private Session getOrCreateSession(String slackUserId) {
        String sessionKey = "slack:" + slackUserId;
        return userSessions.computeIfAbsent(sessionKey,
                key -> sessionStore.createSession("slack", slackUserId));
    }
}
