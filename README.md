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
     "agent": { "provider": "anthropic", "apiKey": "sk-ant-...", "model": "claude-sonnet-4-20250514" },
     "plugins": [
       {
         "type": "qmd",
         "config": {
           "command": "qmd",
           "workingDirectory": "/Users/you/github",
           "timeoutSeconds": 20
         }
       }
     ]
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

## Plugins

Plugins are typed integrations that register one or more tools from config.

- `qmd` adds a read-only `qmd_memory` tool backed by the `qmd` CLI.
- Configure plugins under top-level `plugins`.
- Drop external plugin jars into `~/.openclaw-java/plugins`; discovery uses Java `ServiceLoader`.
- If a plugin is explicitly configured and its dependency is missing, gateway startup fails fast with a clear error.

The `qmd_memory` tool supports these operations:
- `query`
- `search`
- `vsearch`
- `get`
- `multi_get`
- `ls`
- `status`
- `context_list`
- `context_check`

For simple command wrappers that do not need a dedicated plugin class, keep using `agent.customTools`.

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
