package ai.openclaw.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Session {
    private String id;
    private String channelType;
    private String userId;
    private List<Message> messages;
    private Instant createdAt;
    private Instant lastActiveAt;

    public Session() {
    }

    public Session(String channelType, String userId) {
        this.id = UUID.randomUUID().toString();
        this.channelType = channelType;
        this.userId = userId;
        this.messages = new ArrayList<>();
        this.createdAt = Instant.now();
        this.lastActiveAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getChannelType() {
        return channelType;
    }

    public void setChannelType(String channelType) {
        this.channelType = channelType;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(Instant lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public void addMessage(Message message) {
        this.messages.add(message);
        this.lastActiveAt = Instant.now();
    }
}
