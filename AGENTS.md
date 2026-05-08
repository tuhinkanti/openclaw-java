# AGENTS.md

## Goal
Keep this project runnable with the fewest code changes possible.

## Defaults for Codex runs
- Prefer `openai` provider.
- Prefer environment variables over local file edits.
- Use `OPENAI_API_KEY` for auth.

## Quick run
1. Preflight: `java -version && ./gradlew -version` (must show Java 21)
2. Build: `./gradlew build`
3. Run gateway: `OPENAI_API_KEY=your_key java -jar build/libs/openclaw-java.jar gateway`
4. (Optional) Send one message from another terminal: `OPENAI_API_KEY=your_key java -jar build/libs/openclaw-java.jar send -m "hello"`

## Codex proxy tip
- If dependency downloads fail with 403, set `MAVEN_MIRROR_URL` to an internal Maven mirror.

## Change policy
- Lowest lines of code first.
- Avoid broad refactors unless required to restore runnability.
