package ai.openclaw.tool;

import ai.openclaw.config.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlackPostTool implements Tool {
    private static final Logger logger = LoggerFactory.getLogger(SlackPostTool.class);
    private final MethodsClient slack;

    public SlackPostTool(String botToken) {
        this.slack = Slack.getInstance().methods(botToken);
    }

    @Override
    public String name() {
        return "slack_post";
    }

    @Override
    public String description() {
        return "Posts a message to a Slack channel or user. " +
                "Use a channel name like '#general' or a channel/user ID like 'C0AHETX8PRU' or 'U05M9Q0KCLD'. " +
                "To DM a user, use their user ID.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = Json.mapper().createObjectNode();
        schema.put("type", "object");

        ObjectNode props = schema.putObject("properties");

        ObjectNode channel = props.putObject("channel");
        channel.put("type", "string");
        channel.put("description", "Channel name (e.g. '#general') or ID (e.g. 'C0AHETX8PRU'), or user ID for a DM");

        ObjectNode text = props.putObject("text");
        text.put("type", "string");
        text.put("description", "The message text to post");

        schema.putArray("required").add("channel").add("text");
        return schema;
    }

    @Override
    public ToolResult execute(JsonNode input) {
        String channel = input.get("channel").asText();
        String text = input.get("text").asText();
        logger.info("Posting Slack message to {}: {}", channel, text);

        try {
            ChatPostMessageResponse response = slack.chatPostMessage(r -> r
                    .channel(channel)
                    .text(text));

            if (response.isOk()) {
                return ToolResult.success("Message posted to " + channel + " (ts: " + response.getTs() + ")");
            } else {
                return ToolResult.error("Slack API error: " + response.getError());
            }
        } catch (Exception e) {
            logger.error("Failed to post Slack message", e);
            return ToolResult.error("Failed to post message: " + e.getMessage());
        }
    }
}
