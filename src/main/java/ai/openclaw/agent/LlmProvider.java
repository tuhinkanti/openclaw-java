package ai.openclaw.agent;

import ai.openclaw.session.Message;
import java.util.List;

public interface LlmProvider {
    String complete(List<Message> messages, String model) throws Exception;

    String providerName();
}
