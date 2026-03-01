# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
./gradlew build          # Build + run tests
./gradlew test           # Run all tests (JUnit 5 + Mockito)
./gradlew jar            # Build fat JAR at build/libs/openclaw-java.jar
./gradlew test --tests "ai.openclaw.tool.CodeExecutionToolTest"  # Run single test class
./gradlew test --tests "ai.openclaw.tool.CodeExecutionToolTest.testBlocksDangerousCommands"  # Single test method
```

Run the gateway: `java -jar build/libs/openclaw-java.jar gateway`

## Architecture

This is an MVP Java port of [OpenClaw](https://github.com/openclaw/openclaw), a personal AI assistant. Java 21+ required (virtual threads).

### Core Flow

`Main` → picocli CLI (`OpenClawCli`) → `GatewayCommand` starts all components:
1. **ConfigLoader** reads `~/.openclaw-java/config.json` (or env vars `ANTHROPIC_API_KEY`, `GATEWAY_PORT`, `GATEWAY_AUTH_TOKEN`)
2. **GatewayServer** — WebSocket server (Java-WebSocket lib) with JSON-RPC protocol and token-based auth
3. **RpcRouter** dispatches methods (`gateway.health`, `agent.send`) to handlers
4. **AgentExecutor** — agentic loop: sends messages to LLM, handles tool_use responses in a loop (max 10 iterations), persists all messages to session
5. **ConsoleChannel** — stdin/stdout interactive channel for local use

### Key Interfaces

- **`LlmProvider`** (`agent/`) — LLM backend interface with `complete()` and `completeWithTools()`. `AnthropicProvider` implements it using raw OkHttp calls to the Anthropic Messages API.
- **`Tool`** (`tool/`) — Agent tool interface: `name()`, `description()`, `inputSchema()` (JSON Schema), `execute(JsonNode)` → `ToolResult`. Implementations: `CodeExecutionTool`, `FileReadTool`, `FileWriteTool`, `WebSearchTool`.
- **`Channel`** (`channel/`) — Messaging channel interface. Only `ConsoleChannel` is implemented.

### Session/Message Model

`SessionStore` holds in-memory sessions with JSONL file persistence. `Message` supports multiple roles: `user`, `assistant`, `system`, `assistant_tool_use` (assistant messages with tool_use content blocks), and `tool_result` (tool responses with `toolUseId`). The `AnthropicProvider` merges consecutive `tool_result` messages into a single `user` message per the Anthropic API contract.

### Config

`OpenClawConfig` is a Jackson-deserialized POJO with nested `GatewayConfig` (port, authToken) and `AgentConfig` (provider, apiKey, model, systemPrompt). All config classes use `@JsonIgnoreProperties(ignoreUnknown = true)`.

### Dependencies

Jackson (JSON), picocli (CLI), Java-WebSocket (gateway), OkHttp (HTTP client for Anthropic API), Logback/SLF4J (logging). Shared `ObjectMapper` via `Json.mapper()`.

## Conventions

- Package root: `ai.openclaw`
- All JSON serialization goes through `ai.openclaw.config.Json.mapper()` singleton
- Tools define their own JSON Schema via `inputSchema()` method
- `CodeExecutionTool` has safety patterns: blocked patterns (dangerous commands) and warned patterns (risky commands)
- Gateway uses constant-time token comparison for auth
