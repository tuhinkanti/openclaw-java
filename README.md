# OpenClaw Java MVP

An experimental Java conversion (MVP) of the OpenClaw Personal AI Assistant.

## Overview

This project implements a minimal viable version of OpenClaw in Java, focusing on:
- **WebSocket Gateway** (port 18789)
- **OpenAI-powered Agent Runtime** (Anthropic still supported)
- **Console-based Interaction Channel**
- **Session Management** with JSONL persistence

## Prerequisites

- **Java 25+** (virtual threads required)
- **OpenAI API Key** (`sk-...`) (default) or **Anthropic API Key** (`sk-ant-...`)

## Java 25 Preflight

Run this before building:
```bash
java -version
./gradlew -version
```

If Java 25 is missing, install it:
- macOS (Homebrew): `brew install openjdk@25`
- Ubuntu/Debian: `sudo apt install openjdk-25-jdk`
- Fedora/RHEL: `sudo dnf install java-25-openjdk-devel`
- Windows (winget): `winget install EclipseAdoptium.Temurin.25.JDK`

## Dependency Mirror (Codex/Proxy Environments)

If Maven Central is blocked by your environment proxy, set an internal mirror:
```bash
export MAVEN_MIRROR_URL=https://your-artifact-mirror.example.com/maven2
./gradlew build
```

The build always keeps `mavenCentral()` as fallback; mirror is used first when set.

## Getting Started

1. **Build the project**:
   ```bash
   ./gradlew build
   ```

2. **Configure**:
   Create `~/.openclaw-java/config.json` (either provider):
   
   OpenAI (default):
   ```json
   {
     "gateway": { "port": 18789, "authToken": "test-token" },
     "agent": { "provider": "openai", "apiKey": "sk-...", "model": "gpt-4o-mini" }
   }
   ```

   Anthropic:
   ```json
   {
     "gateway": { "port": 18789, "authToken": "test-token" },
     "agent": { "provider": "anthropic", "apiKey": "sk-ant-...", "model": "claude-sonnet-4-20250514" }
   }
   ```

   You can also set `LLM_PROVIDER=openai|anthropic` to override the provider at runtime.

3. **Run Gateway**:
   ```bash
   java -jar build/libs/openclaw-java.jar gateway
   ```

4. **Interact**:
   The gateway will launch an interactive console prompt. Type messages to chat with the agent.

## Project Structure

- `src/main/java/ai/openclaw/gateway` - WebSocket server
- `src/main/java/ai/openclaw/agent` - Agent logic loop & LLM provider
- `src/main/java/ai/openclaw/channel` - Console channel implementation
- `src/main/java/ai/openclaw/config` - Configuration loader
- `src/main/java/ai/openclaw/session` - Session storage

## Running with Docker

You can run the application in a Docker container for an isolated environment.

1. **Build the Docker image**:
   ```bash
   docker build -t openclaw-java .
   ```

2. **Run the container**:
   You must provide your API key as an environment variable (either provider).
   ```bash
   docker run -it --rm \
     -e OPENAI_API_KEY=sk-... \
     -p 18789:18789 \
     openclaw-java
   ```

   or Anthropic:
   ```bash
   docker run -it --rm \
     -e ANTHROPIC_API_KEY=sk-ant-... \
     -e LLM_PROVIDER=anthropic \
     -p 18789:18789 \
     openclaw-java
   ```

   The container runs as a non-root user (`openclaw`) for security.
   - Code execution is confined to `/home/openclaw/workspace`.
   - File access is restricted to the workspace directory.
   - Network access to internal/private IPs is blocked.
