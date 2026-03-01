package ai.openclaw.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import ai.openclaw.config.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionStore {
    private static final Logger logger = LoggerFactory.getLogger(SessionStore.class);
    private static final Duration SESSION_TTL = Duration.ofHours(24);
    private static final Duration EVICTION_INTERVAL = Duration.ofMinutes(15);

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
        loadExistingSessions();
        startEvictionThread();
    }

    private void loadExistingSessions() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(sessionsDir, "*.jsonl")) {
            int loaded = 0;
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                String sessionId = fileName.substring(0, fileName.length() - ".jsonl".length());
                try {
                    Session session = recoverSession(sessionId, file);
                    if (session != null) {
                        sessions.put(sessionId, session);
                        loaded++;
                    }
                } catch (Exception e) {
                    logger.warn("Failed to recover session from {}: {}", file, e.getMessage());
                }
            }
            if (loaded > 0) {
                logger.info("Recovered {} sessions from disk", loaded);
            }
        } catch (IOException e) {
            logger.warn("Failed to scan sessions directory: {}", e.getMessage());
        }
    }

    private Session recoverSession(String sessionId, Path file) throws IOException {
        Session session = new Session();
        session.setId(sessionId);
        session.setChannelType("recovered");
        session.setUserId("unknown");
        session.setMessages(new java.util.ArrayList<>());
        session.setCreatedAt(Instant.now());
        session.setLastActiveAt(Instant.now());

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    Message msg = mapper.readValue(line, Message.class);
                    session.getMessages().add(msg);
                    if (msg.getTimestamp() != null) {
                        session.setLastActiveAt(msg.getTimestamp());
                    }
                }
            }
        }

        if (session.getMessages().isEmpty()) {
            return null;
        }

        Message first = session.getMessages().get(0);
        if (first.getTimestamp() != null) {
            session.setCreatedAt(first.getTimestamp());
        }

        return session;
    }

    private void startEvictionThread() {
        Thread evictionThread = Thread.ofVirtual().name("session-eviction").start(() -> {
            while (true) {
                try {
                    Thread.sleep(EVICTION_INTERVAL.toMillis());
                    evictExpiredSessions();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private void evictExpiredSessions() {
        Instant cutoff = Instant.now().minus(SESSION_TTL);
        int evicted = 0;
        Iterator<Map.Entry<String, Session>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Session> entry = it.next();
            if (entry.getValue().getLastActiveAt().isBefore(cutoff)) {
                it.remove();
                evicted++;
            }
        }
        if (evicted > 0) {
            logger.info("Evicted {} expired sessions from memory (JSONL files retained on disk)", evicted);
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
