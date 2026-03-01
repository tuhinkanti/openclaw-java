plugins {
    application
    id("java")
}

group = "ai.openclaw"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral() {
        content {
            excludeGroup("com.slack.api")
            excludeGroup("javax.websocket")
            excludeGroup("org.glassfish.tyrus.bundles")
            excludeGroup("com.google.code.gson")
        }
    }
    // Ivy repo for deps whose POMs contain DOCTYPE declarations (incompatible with Gradle 9 XML parsing)
    ivy {
        url = uri("https://repo.maven.apache.org/maven2")
        patternLayout {
            artifact("[organisation]/[module]/[revision]/[artifact]-[revision].[ext]")
            setM2compatible(true)
        }
        metadataSources {
            artifact()
        }
        content {
            includeGroup("com.slack.api")
            includeGroup("javax.websocket")
            includeGroup("org.glassfish.tyrus.bundles")
            includeGroup("com.google.code.gson")
        }
    }
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")
    implementation("info.picocli:picocli:4.7.5")
    implementation("org.java-websocket:Java-WebSocket:1.5.7")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("ch.qos.logback:logback-classic:1.5.3")

    // Slack Bolt (Socket Mode) - resolved via Ivy repo (no POM parsing)
    implementation("com.slack.api:bolt-socket-mode:1.44.2")
    implementation("com.slack.api:slack-api-model:1.44.2")
    implementation("com.slack.api:slack-api-client:1.44.2")
    implementation("com.slack.api:bolt:1.44.2")
    implementation("com.slack.api:slack-app-backend:1.44.2")
    implementation("com.slack.api:slack-api-model-kotlin-extension:1.44.2")
    implementation("javax.websocket:javax.websocket-api:1.1")
    implementation("org.glassfish.tyrus.bundles:tyrus-standalone-client:1.20")
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("ai.openclaw.Main")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "ai.openclaw.Main"
    }
    // Create a fat JAR with all dependencies bundled
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
