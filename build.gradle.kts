plugins {
    application
    java
    // Optional for fat-jar packaging when needed:
    // id("com.github.johnrengelman.shadow") version "8.1.1"
}

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

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

    reports {
        html.required = false
        junitXml.required = true
    }

    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
        showStandardStreams = true
    }
}

tasks.register("testFailureDiagnostics") {
    group = "verification"
    description = "Emits concise diagnostics for JUnit XML failures from build/test-results/test/*.xml."

    val testResultsDir = layout.buildDirectory.dir("test-results/test")

    doLast {
        val dir = testResultsDir.get().asFile
        val xmlFiles = dir.listFiles { file -> file.isFile && file.extension == "xml" }
            ?.sortedBy { it.name }
            .orEmpty()

        if (xmlFiles.isEmpty()) {
            logger.lifecycle("No JUnit XML files found under ${dir.path}; skipping failure diagnostics.")
            return@doLast
        }

        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        var failuresFound = 0

        xmlFiles.forEach { xml ->
            val doc = parser.parse(xml)
            val testCases = doc.getElementsByTagName("testcase")

            for (i in 0 until testCases.length) {
                val testCase = testCases.item(i)
                val children = testCase.childNodes

                for (j in 0 until children.length) {
                    val child = children.item(j)
                    if (child.nodeName != "failure" && child.nodeName != "error") {
                        continue
                    }

                    failuresFound++
                    val className = testCase.attributes?.getNamedItem("classname")?.nodeValue ?: "<unknown class>"
                    val methodName = testCase.attributes?.getNamedItem("name")?.nodeValue ?: "<unknown method>"
                    val fqTestName = "$className.$methodName"

                    val messageAttr = child.attributes?.getNamedItem("message")?.nodeValue?.trim().orEmpty()
                    val details = child.textContent?.trim().orEmpty()
                    val message = messageAttr.ifBlank {
                        details.lineSequence().firstOrNull { it.isNotBlank() } ?: "<no message>"
                    }

                    val firstProjectFrame = details
                        .lineSequence()
                        .map { it.trim() }
                        .filter { it.startsWith("at ") }
                        .mapNotNull { frame ->
                            val methodStart = frame.removePrefix("at ").substringBefore("(")
                            val classNameInFrame = methodStart.substringBeforeLast('.', missingDelimiterValue = "")
                            if (classNameInFrame.isBlank()) {
                                return@mapNotNull null
                            }

                            val candidates = listOf(
                                File(projectDir, "src/main/java/${classNameInFrame.substringBefore('$').replace('.', '/')}.java"),
                                File(projectDir, "src/test/java/${classNameInFrame.substringBefore('$').replace('.', '/')}.java")
                            )
                            val source = candidates.firstOrNull { it.exists() } ?: return@mapNotNull null
                            "$frame [$source]"
                        }
                        .firstOrNull()
                        ?: "<no project frame in src/main or src/test>"

                    logger.lifecycle("\n==== JUnit failure ====")
                    logger.lifecycle("test: $fqTestName")
                    logger.lifecycle("message: $message")
                    logger.lifecycle("firstProjectFrame: $firstProjectFrame")
                    logger.lifecycle("raw ${child.nodeName} content:\n$details")
                }
            }
        }

        if (failuresFound == 0) {
            logger.lifecycle("No failing <testcase> entries found in JUnit XML files.")
        }
    }
}

tasks.withType<Test>().configureEach {
    finalizedBy("testFailureDiagnostics")
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
