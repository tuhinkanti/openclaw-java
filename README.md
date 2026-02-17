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
