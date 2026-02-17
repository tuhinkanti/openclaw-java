package ai.openclaw.cli;

import ai.openclaw.config.ConfigLoader;
import ai.openclaw.config.OpenClawConfig;
import ai.openclaw.gateway.RpcProtocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Command(name = "send", description = "Sends a message to the agent via WebSocket")
public class SendCommand implements Runnable {

    @Option(names = { "-m", "--message" }, description = "Message text to send", required = true)
    private String message;

    @Override
    public void run() {
        try {
            OpenClawConfig config = ConfigLoader.load();
            String uri = "ws://127.0.0.1:" + config.getGateway().getPort();
            CountDownLatch latch = new CountDownLatch(1);
            ObjectMapper mapper = new ObjectMapper();

            WebSocketClient client = new WebSocketClient(new URI(uri)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    try {
                        RpcProtocol.RpcMessage request = new RpcProtocol.RpcMessage();
                        request.setId(UUID.randomUUID().toString());
                        request.setMethod("agent.send");

                        ObjectNode params = mapper.createObjectNode();
                        params.put("sessionId", "cli-session");
                        params.put("message", message);
                        request.setParams(params);

                        send(mapper.writeValueAsString(request));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onMessage(String message) {
                    try {
                        RpcProtocol.RpcMessage response = mapper.readValue(message, RpcProtocol.RpcMessage.class);
                        if (response.getResult() != null) {
                            System.out.println("🤖 Assistant: " + response.getResult().get("response").asText());
                        } else if (response.getError() != null) {
                            System.err.println("Error: " + response.getError().getMessage());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                        close();
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    latch.countDown();
                }

                @Override
                public void onError(Exception ex) {
                    ex.printStackTrace();
                    latch.countDown();
                }
            };

            client.connect();
            latch.await(30, TimeUnit.SECONDS);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
