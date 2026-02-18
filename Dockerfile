FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
# Download dependencies first (cached layer)
RUN gradle dependencies --no-daemon || true
COPY src ./src
RUN gradle jar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install bash for CodeExecutionTool
RUN apk add --no-cache bash curl

# Create a non-root user with a dedicated workspace for tool execution
RUN addgroup -S openclaw && adduser -S openclaw -G openclaw \
    && mkdir -p /home/openclaw/workspace \
    && chown -R openclaw:openclaw /home/openclaw

COPY --from=build /app/build/libs/*.jar /app/openclaw.jar

# Default gateway port
ENV GATEWAY_PORT=18789
EXPOSE ${GATEWAY_PORT}

# Run as non-root user
USER openclaw
ENV HOME=/home/openclaw

ENTRYPOINT ["java", "-jar", "/app/openclaw.jar"]
CMD ["gateway"]
