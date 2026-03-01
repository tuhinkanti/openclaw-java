FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
RUN ./gradlew dependencies --no-daemon || true
COPY src ./src
RUN ./gradlew jar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install bash for CodeExecutionTool
RUN apk add --no-cache bash curl github-cli

# Create a non-root user with a dedicated workspace for tool execution
RUN addgroup -S openclaw && adduser -S openclaw -G openclaw \
    && mkdir -p /home/openclaw/workspace \
    && chown -R openclaw:openclaw /home/openclaw

COPY --from=build /app/build/libs/*.jar /app/openclaw.jar
COPY entrypoint.sh /app/entrypoint.sh

# Default gateway port
ENV GATEWAY_PORT=18789
EXPOSE ${GATEWAY_PORT}

# Run as non-root user (entrypoint needs root briefly to import certs, then drops)
ENV HOME=/home/openclaw

ENTRYPOINT ["/bin/bash", "/app/entrypoint.sh"]
CMD ["gateway"]
