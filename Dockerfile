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

COPY --from=build /app/build/libs/*.jar /app/openclaw.jar

# Default gateway port
ENV GATEWAY_PORT=18789
EXPOSE ${GATEWAY_PORT}

ENTRYPOINT ["java", "-jar", "/app/openclaw.jar"]
CMD ["gateway"]
