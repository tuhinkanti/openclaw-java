package ai.openclaw.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import ai.openclaw.config.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionStore {
    private static final Logger logger = LoggerFactory.getLogger(SessionStore.class);
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = Json.mapper();
    private final Path sessionsDir;

    public SessionStore() {
        this.sessionsDir = Paths.get(System.getProperty("user.home"), ".openclaw-java", "sessions");
        try {
            Files.createDirectories(sessionsDir);
        } catch (IOException e) {
            logger.error("Failed to create sessions directory", e);
        }
    }

    public Session createSession(String channelType, String userId) {
        Session session = new Session(channelType, userId);
        sessions.put(session.getId(), session);
        return session;
    }

    public Session getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public void appendMessage(String sessionId, Message message) {
        Session session = sessions.get(sessionId);
        if (session != null) {
            session.addMessage(message);
            persistTurn(session, message);
        }
    }

    private void persistTurn(Session session, Message message) {
        Path filePath = sessionsDir.resolve(session.getId() + ".jsonl");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath.toFile(), true))) {
            writer.write(mapper.writeValueAsString(message));
            writer.newLine();
        } catch (IOException e) {
            logger.error("Failed to persist message for session " + session.getId(), e);
        }
    }
}
