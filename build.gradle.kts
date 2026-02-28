plugins {
    application
    java
    // Optional for fat-jar packaging when needed:
    // id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.swgllm"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("info.picocli:picocli:4.7.6")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.2")

    implementation("org.slf4j:slf4j-api:2.0.16")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.16")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

application {
    mainClass = "com.swgllm.Main"
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    jvmArgs = listOf("-Dfile.encoding=UTF-8")
}

tasks.register<JavaExec>("benchmark") {
    group = "verification"
    description = "Runs a quick local benchmark mode for startup and API connectivity checks."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
    args("--mode", "benchmark")
    standardInput = System.`in`
}
