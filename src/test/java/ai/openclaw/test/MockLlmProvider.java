package ai.openclaw.test;

import ai.openclaw.agent.LlmProvider;
import ai.openclaw.agent.LlmResponse;
import ai.openclaw.session.Message;
import ai.openclaw.tool.Tool;

import java.util.List;

public class MockLlmProvider implements LlmProvider {
    @Override
    public String complete(List<Message> messages, String model) {
        return "Mock response from OpenClaw";
    }

    @Override
    public LlmResponse completeWithTools(List<Message> messages, String model, List<Tool> tools) {
        return new LlmResponse("end_turn",
                List.of(LlmResponse.ContentBlock.text("Mock response from OpenClaw")));
    }

    @Override
    public String providerName() {
        return "mock";
    }
}
