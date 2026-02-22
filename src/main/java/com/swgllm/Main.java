package com.swgllm;

import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.OkHttpClient;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "swg-llm",
        mixinStandardHelpOptions = true,
        version = "swg-llm 0.1.0",
        description = "Starter CLI for SWG-LLM runtime integration.")
public class Main implements Callable<Integer> {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    @Option(names = { "-c", "--config" }, description = "Path to YAML config file", defaultValue = "src/main/resources/application.yml")
    String configPath;

    @Option(names = "--mode", description = "Execution mode: ${COMPLETION-CANDIDATES}", defaultValue = "interactive")
    Mode mode;

    private final OkHttpClient httpClient = new OkHttpClient();

    enum Mode {
        interactive,
        benchmark
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        log.info("Starting SWG-LLM in {} mode", mode);
        log.info("Using config file: {}", configPath);
        log.debug("HTTP client configured: {}", httpClient.connectionPool());

        if (mode == Mode.benchmark) {
            long start = System.nanoTime();
            // Placeholder benchmark workload.
            for (int i = 0; i < 1_000_000; i++) {
                Math.sqrt(i);
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.info("Benchmark completed in {} ms", elapsedMs);
        }

        return 0;
    }
}
