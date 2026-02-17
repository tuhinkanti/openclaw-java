# OpenClaw → Java: Minimal Viable Conversion Plan

## Background

[OpenClaw](https://github.com/openclaw/openclaw) is a **personal AI assistant** written in TypeScript (~83.7%) that runs on your own devices. It uses a hub-and-spoke architecture:

- **Gateway** — WebSocket server (port 18789) as the central control plane
- **Agent Runtime** — Orchestrates AI turns via LLM APIs (Anthropic, OpenAI, etc.)
- **Channels** — Connects to messaging platforms (WhatsApp, Telegram, Slack, Discord, Signal, iMessage, etc.)
- **Tools** — Exposes file I/O, exec, browser, memory, web search, etc. to agents
- **Memory** — Semantic search over workspace files via SQLite + vector embeddings
- **Sessions** — JSONL transcript persistence with per-user/channel isolation
- **Configuration** — Zod-validated JSON5 with hot-reload
- **Sandbox** — Docker-based isolation for tool execution
- **Extensions** — npm plugin model for additional channels
- **CLI** — Command-line interface (`openclaw onboard`, `openclaw gateway`, `openclaw agent`, etc.)
- **Native Apps** — macOS (Swift), iOS, Android companion apps

The full system is ~600+ source files. This plan targets the **absolute minimum** that compiles, runs, and demonstrates core functionality.

---

## MVP Scope Definition

> [!IMPORTANT]
> We are **NOT** converting the entire project. The MVP targets the smallest subset that proves the architecture works end-to-end in Java.

### What's IN scope (MVP)

| Subsystem | MVP Scope |
|-----------|----------|
| **Configuration** | Load a JSON config file with basic schema validation |
| **Gateway** | WebSocket server on port 18789 with health check RPC |
| **Agent Runtime** | Extensible `LlmProvider` interface, **Anthropic** as default implementation |
| **Sessions** | In-memory session store with JSONL file persistence |
| **CLI** | `openclaw-java gateway` and `openclaw-java send` commands |
| **Channel** | **Console channel** (stdin/stdout) — zero external deps for testing |

### What's OUT of scope (deferred)

- ❌ All messaging channels (WhatsApp, Telegram, Discord, Slack, Signal, iMessage, etc.)
- ❌ Memory/vector search system
- ❌ Sandbox/Docker isolation
- ❌ Tool system (file I/O, exec, browser, canvas, cron, etc.)
- ❌ Extension/plugin loader
- ❌ Hot-reload configuration
- ❌ Multi-agent routing
- ❌ Native apps (macOS/iOS/Android)
- ❌ WebChat / Control UI
- ❌ Voice Wake / Talk Mode
- ❌ Tailscale / remote access
- ❌ DM pairing / security model (use simple token auth)
- ❌ Media pipeline (images, audio, video)

---

## Technology Mapping

| TypeScript | Java Equivalent |
|------------|-----------------|
| Node.js runtime | JDK 21+ (virtual threads) |
| `ws` (WebSocket) | [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket) or Jetty WebSocket |
| Zod (schema validation) | Jackson + manual validation |
| JSON5 config | Jackson with `json5` module or Gson |
| Anthropic SDK | [anthropic-sdk-java](https://github.com/anthropics/anthropic-sdk-java) or raw HTTP via OkHttp |
| JSONL file I/O | Jackson `ObjectMapper` + `BufferedWriter` |
| `commander` (CLI) | [picocli](https://picocli.info/) |
| `pino` (logging) | SLF4J + Logback |
| `vitest` (testing) | JUnit 5 + Mockito |
| pnpm / npm | Gradle (Kotlin DSL) |

---

## Proposed Changes

### Phase 1: Project Skeleton & Configuration

#### [NEW] `build.gradle.kts`
Gradle project with dependencies:
- `com.fasterxml.jackson.core:jackson-databind` (JSON parsing)
- `info.picocli:picocli` (CLI)
- `org.java-websocket:Java-WebSocket` (WebSocket server)
- `com.squareup.okhttp3:okhttp` (HTTP client for LLM APIs)
- `ch.qos.logback:logback-classic` (logging)
- `org.junit.jupiter:junit-jupiter` (testing)

#### [NEW] `src/main/java/ai/openclaw/config/OpenClawConfig.java`
Maps to: `src/config/zod-schema.ts` + `src/config/config.ts`

POJO with Jackson annotations representing the minimal config:
```java
public class OpenClawConfig {
    private GatewayConfig gateway;    // port, bind, auth token
    private AgentConfig agent;        // provider ("anthropic"), API key, model, system prompt
}
```

#### [NEW] `src/main/java/ai/openclaw/config/ConfigLoader.java`
Maps to: `src/config/io.ts` + `src/config/validation.ts`

Loads `~/.openclaw-java/config.json`, validates required fields, returns `OpenClawConfig`. Project lives at `~/github/openclaw-java/`.

---

### Phase 2: Gateway WebSocket Server

#### [NEW] `src/main/java/ai/openclaw/gateway/GatewayServer.java`
Maps to: `src/gateway/server.ts`

WebSocket server using Java-WebSocket library:
- Binds to `127.0.0.1:18789`
- Accepts JSON-RPC style messages
- Implements: `gateway.health`, `gateway.status`, `agent.send`
- Token-based auth on connection

#### [NEW] `src/main/java/ai/openclaw/gateway/RpcProtocol.java`
Maps to: `src/gateway/protocol.ts`

Defines the RPC message envelope:
```java
public class RpcMessage {
    String id;
    String method;
    JsonNode params;
}
public class RpcResponse {
    String id;
    JsonNode result;
    RpcError error;
}
```

#### [NEW] `src/main/java/ai/openclaw/gateway/RpcRouter.java`
Maps to: `src/gateway/router.ts`

Routes incoming RPC method calls to handler functions.

---

### Phase 3: Agent Runtime

#### [NEW] `src/main/java/ai/openclaw/agent/AgentExecutor.java`
Maps to: `src/agents/agent-pi.ts`

Core agent turn execution:
1. Receives a user message
2. Constructs messages array (system prompt + session history + user message)
3. Calls `LlmProvider` for a completion
4. Returns assistant response text
5. Appends turn to session

#### [NEW] `src/main/java/ai/openclaw/agent/SystemPromptBuilder.java`
Maps to: `src/agents/system-prompt.ts`

Reads `IDENTITY.md` from workspace directory, builds system prompt string. Falls back to a default prompt if file doesn't exist.

#### [NEW] `src/main/java/ai/openclaw/agent/LlmProvider.java`
Extensible interface for LLM backends:
```java
public interface LlmProvider {
    String complete(List<Message> messages, String model);
    String providerName();
}
```

#### [NEW] `src/main/java/ai/openclaw/agent/AnthropicProvider.java`
Maps to: Integration with `pi-ai` SDK

Default `LlmProvider` implementation using Anthropic Messages API:
- `POST https://api.anthropic.com/v1/messages`
- Supports model selection (default: `claude-sonnet-4-20250514`)
- Uses OkHttp for HTTP, Jackson for JSON
- Returns parsed response text

> [!TIP]
> To add OpenAI or other providers later, just implement `LlmProvider` and register in config.

---

### Phase 4: Session Management

#### [NEW] `src/main/java/ai/openclaw/session/SessionStore.java`
Maps to: `src/config/sessions/store.ts`

- In-memory `Map<String, Session>` keyed by session ID
- Each `Session` holds a `List<Message>` (role + content)
- Appends to JSONL file at `~/.openclaw-java/sessions/<sessionId>.jsonl`
- Loads existing JSONL on startup

#### [NEW] `src/main/java/ai/openclaw/session/Session.java`
Maps to: Session concept in `src/config/sessions.ts`

```java
public class Session {
    String id;
    String channelType; // "telegram", "cli"
    String userId;
    List<Message> messages;
    Instant createdAt;
    Instant lastActiveAt;
}
```

#### [NEW] `src/main/java/ai/openclaw/session/Message.java`
Simple record:
```java
public record Message(String role, String content, Instant timestamp) {}
```

---

### Phase 5: Console Channel

#### [NEW] `src/main/java/ai/openclaw/channel/Channel.java`
Maps to: Channel interface pattern from `src/channels/`

```java
public interface Channel {
    void start();
    void stop();
    void sendMessage(String sessionId, String text);
    String getChannelType();
}
```

#### [NEW] `src/main/java/ai/openclaw/channel/console/ConsoleChannel.java`
Stdin/stdout interactive channel for local testing:
- Reads user input from `System.in` in a loop
- Creates a single session (`console-default`)
- Calls `AgentExecutor` for a response
- Prints assistant reply to `System.out`
- Runs on a daemon thread, exits on `quit`/`exit`

---

### Phase 6: CLI

#### [NEW] `src/main/java/ai/openclaw/cli/OpenClawCli.java`
Maps to: `src/cli/program.ts`

picocli-based CLI with subcommands:
- `gateway` — starts the Gateway + Console channel
- `send --message "text"` — sends a message to the agent via WebSocket RPC and prints response

#### [NEW] `src/main/java/ai/openclaw/cli/GatewayCommand.java`
Starts `GatewayServer`, loads config, initializes `SessionStore`, starts `ConsoleChannel`.

#### [NEW] `src/main/java/ai/openclaw/cli/SendCommand.java`
Maps to: `src/cli/agent-cli.ts`

Connects to Gateway WebSocket, sends `agent.send` RPC, prints response.

---

### Phase 7: Application Entry Point

#### [NEW] `src/main/java/ai/openclaw/Main.java`
Maps to: `src/index.ts`

Entry point that delegates to `OpenClawCli`.

---

## Complete File Listing

```
openclaw-java/
├── build.gradle.kts
├── settings.gradle.kts
├── src/
│   ├── main/
│   │   ├── java/ai/openclaw/
│   │   │   ├── Main.java
│   │   │   ├── config/
│   │   │   │   ├── OpenClawConfig.java
│   │   │   │   ├── GatewayConfig.java
│   │   │   │   ├── AgentConfig.java
│   │   │   │   └── ConfigLoader.java
│   │   │   ├── gateway/
│   │   │   │   ├── GatewayServer.java
│   │   │   │   ├── RpcProtocol.java
│   │   │   │   └── RpcRouter.java
│   │   │   ├── agent/
│   │   │   │   ├── AgentExecutor.java
│   │   │   │   ├── SystemPromptBuilder.java
│   │   │   │   ├── LlmProvider.java
│   │   │   │   └── AnthropicProvider.java
│   │   │   ├── session/
│   │   │   │   ├── Session.java
│   │   │   │   ├── Message.java
│   │   │   │   └── SessionStore.java
│   │   │   ├── channel/
│   │   │   │   ├── Channel.java
│   │   │   │   └── console/
│   │   │   │       └── ConsoleChannel.java
│   │   │   └── cli/
│   │   │       ├── OpenClawCli.java
│   │   │       ├── GatewayCommand.java
│   │   │       └── SendCommand.java
│   │   └── resources/
│   │       └── logback.xml
│   └── test/
│       └── java/ai/openclaw/
│           ├── config/
│           │   └── ConfigLoaderTest.java
│           ├── gateway/
│           │   ├── RpcProtocolTest.java
│           │   └── GatewayServerTest.java
│           ├── agent/
│           │   └── AgentExecutorTest.java
│           └── session/
│               └── SessionStoreTest.java
└── README.md
```

**Total: ~20 production files, ~5 test files** — project at `~/github/openclaw-java/`

---

## Mapping Summary: TypeScript → Java

| Original TS File/Module | Java Equivalent | Notes |
|--------------------------|-----------------|-------|
| `src/index.ts` | `Main.java` | Entry point |
| `src/config/zod-schema.ts` | `OpenClawConfig.java` + sub-configs | POJOs replace Zod |
| `src/config/io.ts` | `ConfigLoader.java` | No hot-reload in MVP |
| `src/gateway/server.ts` | `GatewayServer.java` | Java-WebSocket |
| `src/gateway/protocol.ts` | `RpcProtocol.java` | JSON-RPC envelope |
| `src/gateway/router.ts` | `RpcRouter.java` | Method dispatch |
| `src/agents/agent-pi.ts` | `AgentExecutor.java` | No tools, no sandbox |
| `src/agents/system-prompt.ts` | `SystemPromptBuilder.java` | Reads IDENTITY.md |
| pi-ai SDK (Anthropic) | `LlmProvider.java` + `AnthropicProvider.java` | Extensible interface |
| `src/config/sessions/store.ts` | `SessionStore.java` | JSONL persistence |
| `src/channels/` (all) | `ConsoleChannel.java` | Stdin/stdout for MVP |
| `src/cli/program.ts` | `OpenClawCli.java` | picocli |
| `src/cli/agent-cli.ts` | `SendCommand.java` | WebSocket client |

---

## Verification Plan

### Automated Tests (JUnit 5)

Run all tests:
```bash
cd openclaw-java && ./gradlew test
```

| Test File | What It Verifies |
|-----------|-----------------|
| `ConfigLoaderTest.java` | Loads valid JSON config, rejects missing required fields, handles defaults |
| `RpcProtocolTest.java` | Serializes/deserializes RPC messages correctly |
| `GatewayServerTest.java` | Server starts on configured port, accepts WebSocket connections, responds to `gateway.health` |
| `AgentExecutorTest.java` | Constructs correct message array, mocks LLM client, returns response |
| `SessionStoreTest.java` | Creates sessions, appends messages, persists to JSONL, reloads from file |

### Manual Verification

> [!TIP]
> These steps assume you have an OpenAI API key and a Telegram bot token.

1. **Build the project**:
   ```bash
   cd openclaw-java && ./gradlew build
   ```
   ✅ Should compile without errors.

2. **Create config file** at `~/.openclaw-java/config.json`:
   ```json
   {
     "gateway": { "port": 18789, "authToken": "test-token" },
     "agent": { "provider": "anthropic", "apiKey": "sk-ant-...", "model": "claude-sonnet-4-20250514" }
   }
   ```

3. **Start the gateway**:
   ```bash
   java -jar build/libs/openclaw-java.jar gateway
   ```
   ✅ Should print "Gateway listening on ws://127.0.0.1:18789" and open the console channel prompt.

4. **Chat in the console**: Type a message and press Enter.
   ✅ Should print an AI-generated response from Claude.

5. **Send via CLI** (in another terminal):
   ```bash
   java -jar build/libs/openclaw-java.jar send --message "Hello, who are you?"
   ```
   ✅ Should connect to Gateway via WebSocket and print the response.

---

## Decisions (Resolved)

- ✅ **LLM Provider**: Anthropic as default, with extensible `LlmProvider` interface for future providers
- ✅ **Project location**: `~/github/openclaw-java/`
- ✅ **Java version**: JDK 21+ (virtual threads)
- ✅ **Channel**: Console (stdin/stdout) for MVP — no external messaging deps needed
