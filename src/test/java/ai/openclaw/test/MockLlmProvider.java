package ai.openclaw.test;

import ai.openclaw.agent.LlmProvider;
import ai.openclaw.session.Message;

import java.util.List;

public class MockLlmProvider implements LlmProvider {
    @Override
    public String complete(List<Message> messages, String model) {
        return "Mock response from OpenClaw";
    }

    @Override
    public String providerName() {
        return "mock";
    }
}
