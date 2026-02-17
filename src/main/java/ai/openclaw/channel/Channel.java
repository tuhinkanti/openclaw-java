package ai.openclaw.channel;

public interface Channel extends Runnable {
    String getChannelType();

    void start();

    void stop();

    void sendMessage(String sessionId, String text);
}
