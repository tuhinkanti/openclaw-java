# OpenClaw Java MVP

An experimental Java conversion (MVP) of the OpenClaw Personal AI Assistant.

## Overview

This project implements a minimal viable version of OpenClaw in Java, focusing on:
- **WebSocket Gateway** (port 18789)
- **Anthropic-powered Agent Runtime**
- **Console-based Interaction Channel**
- **Session Management** with JSONL persistence

## Prerequisites

- **Java 21+** (virtual threads required)
- **Anthropic API Key** (`sk-ant-...`)

## Getting Started

1. **Build the project**:
   ```bash
   ./gradlew build
   ```

2. **Configure**:
   Create `~/.openclaw-java/config.json`:
   ```json
   {
     "gateway": { "port": 18789, "authToken": "test-token" },
     "agent": { "provider": "anthropic", "apiKey": "sk-ant-...", "model": "claude-sonnet-4-20250514" }
   }
   ```

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
   You must provide your Anthropic API key as an environment variable.
   ```bash
   docker run -it --rm \
     -e ANTHROPIC_API_KEY=sk-ant-... \
     -p 18789:18789 \
     openclaw-java
   ```

   The container runs as a non-root user (`openclaw`) for security.
   - Code execution is confined to `/home/openclaw/workspace`.
   - File access is restricted to the workspace directory.
   - Network access to internal/private IPs is blocked.
