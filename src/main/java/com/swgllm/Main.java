package com.swgllm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.swgllm.feedback.FeedbackCaptureService;
import com.swgllm.feedback.FeedbackRating;
import com.swgllm.feedback.FeedbackRecord;
import com.swgllm.governance.GovernanceMetrics;
import com.swgllm.governance.GovernancePolicy;
import com.swgllm.ingest.EmbeddingService;
import com.swgllm.ingest.EmbeddingServices;
import com.swgllm.ingest.IngestionReport;
import com.swgllm.ingest.IngestionService;
import com.swgllm.ingest.RetrievalService;
import com.swgllm.ingest.SearchResult;
import com.swgllm.inference.CpuInferenceEngine;
import com.swgllm.inference.InferenceContext;
import com.swgllm.inference.InferenceEngine;
import com.swgllm.inference.IntelIgpuInferenceEngine;
import com.swgllm.pipeline.OfflineImprovementPipeline;
import com.swgllm.runtime.AppConfig;
import com.swgllm.runtime.IntelGpuCapabilityProbe;
import com.swgllm.runtime.RuntimeProfileResolver;
import com.swgllm.versioning.ArtifactVersions;
import com.swgllm.versioning.RolloutState;
import com.swgllm.versioning.SemanticVersion;
import com.swgllm.versioning.VersionRolloutManager;

import okhttp3.OkHttpClient;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

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

    @Parameters(index = "0", arity = "0..1", description = "Optional shortcut command (e.g. chat)")
    String commandAlias;

    @Option(names = "--runtime-profile", description = "Runtime profile from config (cpu-low-memory, intel-igpu)")
    String runtimeProfile;

    @Option(names = "--enable-auto-learn", description = "Capture chat turns as positive feedback for the offline learning pipeline", defaultValue = "true")
    boolean enableAutoLearn;

    @Option(names = "--repo-path", description = "Path to a local clone of swg-main", defaultValue = "../swg-main")
    Path repoPath;

    @Option(names = "--index-path", description = "Path for local vector index JSON", defaultValue = ".swgllm/vector-index.json")
    Path indexPath;

    @Option(names = "--state-path", description = "Path for incremental ingestion state", defaultValue = ".swgllm/index-state.json")
    Path statePath;

    @Option(names = "--version-registry-path", description = "Path for model/retriever/prompt version registry", defaultValue = ".swgllm/version-registry.json")
    Path versionRegistryPath;

    @Option(names = "--feedback-path", description = "Path for anonymized feedback logs", defaultValue = ".swgllm/feedback-log.json")
    Path feedbackPath;

    @Option(names = "--dataset-path", description = "Path for generated training/eval dataset", defaultValue = ".swgllm/training-dataset.json")
    Path datasetPath;

    @Option(names = "--adapter-dir", description = "Path where adapter artifacts are generated", defaultValue = ".swgllm/adapters")
    Path adapterDir;

    @Option(names = "--query", description = "Query text used in retrieve mode")
    String query;

    @Option(names = "--top-k", description = "Top results to return", defaultValue = "5")
    int topK;

    @Option(names = "--rating", description = "Feedback rating for feedback mode: ${COMPLETION-CANDIDATES}")
    FeedbackRating rating;

    @Option(names = "--prompt", description = "Prompt text for feedback capture")
    String prompt;

    @Option(names = "--response", description = "Response text for feedback capture")
    String response;

    @Option(names = "--corrected-answer", description = "Corrected answer for supervised improvement")
    String correctedAnswer;

    @Option(names = "--approved-for-training", description = "Set true to use feedback in offline improvement pipeline", defaultValue = "false")
    boolean approvedForTraining;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final EmbeddingService embeddingService = EmbeddingServices.fromEnvironment(httpClient);
    private final IntelGpuCapabilityProbe capabilityProbe = new IntelGpuCapabilityProbe();
    private int inferenceTimeoutMs = 30_000;

    enum Mode {
        interactive,
        benchmark,
        ingest,
        retrieve,
        feedback,
        improve,
        chat
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if ("chat".equalsIgnoreCase(commandAlias)) {
            mode = Mode.chat;
        }

        AppConfig config = loadConfig(Path.of(configPath));
        RuntimeProfileResolver.ResolvedProfile resolvedProfile = new RuntimeProfileResolver(capabilityProbe)
                .resolve(config.getRuntime(), runtimeProfile);
        inferenceTimeoutMs = config.getRuntime().getTimeoutMs();

        log.info("Starting SWG-LLM in {} mode", mode);
        log.info("Using config file: {}", configPath);
        log.info("Runtime requestedProfile={} activeProfile={} model={} backend={} contextWindowTokens={} retrievalChunks={} fallbackCause={}",
                resolvedProfile.requestedProfile(),
                resolvedProfile.profileName(),
                resolvedProfile.model(),
                resolvedProfile.backend(),
                resolvedProfile.contextWindowTokens(),
                resolvedProfile.retrievalChunks(),
                resolvedProfile.fallbackCause().isBlank() ? "none" : resolvedProfile.fallbackCause());
        log.debug("HTTP client configured: {}", httpClient.connectionPool());

        VersionRolloutManager rolloutManager = new VersionRolloutManager();
        RolloutState rolloutState = rolloutManager.load(versionRegistryPath);
        log.info("Active versions prompt={} retriever={} model={} canaryModel={}",
                rolloutState.currentStable().promptTemplateVersion(),
                rolloutState.currentStable().retrieverVersion(),
                rolloutState.currentStable().modelVersion(),
                rolloutState.currentCanary().modelVersion());

        if (mode == Mode.benchmark) {
            runBenchmark(resolvedProfile);
        }
        if (mode == Mode.ingest) {
            IngestionService ingestionService = new IngestionService(embeddingService);
            IngestionReport report = ingestionService.ingest(repoPath, indexPath, statePath);
            log.info("Indexed repo: processed={}, skipped={}, total={}, commit={}, tag={}",
                    report.processedFiles(),
                    report.skippedFiles(),
                    report.totalFiles(),
                    report.commitHash(),
                    report.versionTag());
        }
        if (mode == Mode.retrieve) {
            if (query == null || query.isBlank()) {
                log.error("--query is required in retrieve mode");
                return 2;
            }
            RetrievalService retrievalService = new RetrievalService(embeddingService);
            List<SearchResult> results = retrievalService.retrieve(indexPath, query, topK);
            for (int i = 0; i < results.size(); i++) {
                SearchResult result = results.get(i);
                log.info("Result #{} semantic={} rerank={} symbols={} citation={}",
                        i + 1,
                        String.format("%.4f", result.score()),
                        String.format("%.4f", result.rerankScore()),
                        result.chunk().metadata().symbols(),
                        result.citationSnippet());
            }
        }
        if (mode == Mode.chat) {
            runChat(resolvedProfile);
        }
        if (mode == Mode.feedback) {
            if (rating == null || prompt == null || response == null) {
                log.error("--rating, --prompt, and --response are required in feedback mode");
                return 2;
            }
            FeedbackCaptureService feedbackCaptureService = new FeedbackCaptureService();
            FeedbackRecord record = feedbackCaptureService.capture(
                    feedbackPath,
                    rating,
                    prompt,
                    response,
                    correctedAnswer,
                    approvedForTraining);
            log.info("Captured feedback requestId={} rating={} approvedForTraining={}",
                    record.requestId(),
                    record.rating(),
                    record.approvedForTraining());
        }
        if (mode == Mode.improve) {
            runImprovementPipeline();
        }

        return 0;
    }

    private AppConfig loadConfig(Path config) throws IOException {
        if (!Files.exists(config)) {
            return new AppConfig();
        }
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return mapper.readValue(config.toFile(), AppConfig.class);
    }

    private void runChat(RuntimeProfileResolver.ResolvedProfile profile) throws IOException, InterruptedException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        RetrievalService retrievalService = new RetrievalService(embeddingService);
        List<String> conversation = new ArrayList<>();
        List<SearchResult> lastSources = List.of();

        System.out.println("SWG-LLM chat ready. End multiline input with a single '.' line. Type /help for commands.");
        while (true) {
            System.out.print("you> ");
            System.out.flush();
            String promptInput = readMultilinePrompt(reader);
            if (promptInput == null || "/exit".equals(promptInput) || "/quit".equals(promptInput)) {
                break;
            }
            if (promptInput.isBlank()) {
                continue;
            }
            if ("/help".equals(promptInput)) {
                System.out.println("Commands: /help, /reset, /context, /source, /improve, /exit. End multiline with '.'");
                continue;
            }
            if ("/reset".equals(promptInput)) {
                conversation.clear();
                lastSources = List.of();
                System.out.println("Context reset.");
                continue;
            }
            if ("/context".equals(promptInput)) {
                System.out.printf("Turns in memory: %d, contextWindowTokens cap: %d%n",
                        conversation.size(),
                        profile.contextWindowTokens());
                continue;
            }
            if ("/source".equals(promptInput)) {
                if (lastSources.isEmpty()) {
                    System.out.println("No retrieval sources from current session yet.");
                } else {
                    for (int i = 0; i < lastSources.size(); i++) {
                        System.out.printf("[%d] %s%n", i + 1, lastSources.get(i).citationSnippet());
                    }
                }
                continue;
            }
            if ("/improve".equals(promptInput)) {
                runImprovementPipeline();
                continue;
            }

            long retrievalStart = System.nanoTime();
            int boundedK = Math.max(1, Math.min(profile.retrievalChunks(), 6));
            lastSources = Files.exists(indexPath)
                    ? retrievalService.retrieve(indexPath, promptInput, boundedK)
                    : List.of();
            long generationStart = System.nanoTime();
            String fullPrompt = buildPrompt(promptInput, conversation, lastSources, profile);
            String response = runInferenceWithTimeout(fullPrompt, conversation, lastSources, profile);
            long firstTokenLatencyMs = (System.nanoTime() - generationStart) / 1_000_000;
            streamTokens(response);
            long elapsedNs = System.nanoTime() - generationStart;
            int tokenCount = response.split("\\s+").length;
            double tokensPerSecond = tokenCount * 1_000_000_000d / Math.max(1L, elapsedNs);
            long usedMemMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);

            log.info("chat.telemetry backend={} fallbackCause={} tokens={} tokensPerSec={} firstTokenLatencyMs={} retrievalMs={} memoryUsedMb={}",
                    profile.backend(),
                    profile.fallbackCause().isBlank() ? "none" : profile.fallbackCause(),
                    tokenCount,
                    String.format(Locale.ROOT, "%.2f", tokensPerSecond),
                    firstTokenLatencyMs,
                    (System.nanoTime() - retrievalStart) / 1_000_000,
                    usedMemMb);

            conversation.add("user: " + promptInput);
            conversation.add("assistant: " + response);
            trimConversation(conversation, profile.contextWindowTokens());
            captureAutoFeedback(promptInput, response, lastSources);
        }
    }

    private void captureAutoFeedback(String promptInput, String response, List<SearchResult> lastSources) {
        if (!enableAutoLearn) {
            return;
        }
        FeedbackCaptureService feedbackCaptureService = new FeedbackCaptureService();
        String corrected = lastSources.isEmpty()
                ? response
                : lastSources.get(0).citationSnippet();
        try {
            FeedbackRecord feedback = feedbackCaptureService.capture(
                    feedbackPath,
                    FeedbackRating.THUMBS_UP,
                    promptInput,
                    response,
                    corrected,
                    true);
            log.debug("Auto-feedback captured requestId={} approvedForTraining={}",
                    feedback.requestId(),
                    feedback.approvedForTraining());
        } catch (IOException e) {
            log.warn("Unable to persist auto-feedback to {}", feedbackPath, e);
        }
    }

    private static String readMultilinePrompt(BufferedReader reader) throws IOException {
        StringBuilder builder = new StringBuilder();
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                return null;
            }
            if (".".equals(line)) {
                break;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line);
            if (builder.toString().startsWith("/")) {
                break;
            }
        }
        return builder.toString().trim();
    }

    static InferenceEngine selectInferenceEngine(RuntimeProfileResolver.ResolvedProfile profile) {
        if ("intel-igpu".equals(profile.backend())) {
            return new IntelIgpuInferenceEngine();
        }
        return new CpuInferenceEngine();
    }

    static String buildPrompt(
            String userPrompt,
            List<String> conversation,
            List<SearchResult> sources,
            RuntimeProfileResolver.ResolvedProfile profile) {
        StringBuilder builder = new StringBuilder();
        builder.append("System instruction:\n")
                .append("You are SWG-LLM. Answer with grounded, concise guidance using provided snippets when relevant. ")
                .append("If snippets are missing, state that ingestion may be required.\n\n")
                .append("Runtime profile: ")
                .append(profile.profileName())
                .append(" | Model: ")
                .append(profile.model())
                .append(" | Backend: ")
                .append(profile.backend())
                .append("\n\nConversation history:\n");

        if (conversation.isEmpty()) {
            builder.append("(none)\n");
        } else {
            for (String turn : conversation) {
                builder.append(turn).append("\n");
            }
        }

        builder.append("\nRetrieved snippets:\n");
        if (sources.isEmpty()) {
            builder.append("(none)\n");
        } else {
            for (int i = 0; i < sources.size(); i++) {
                builder.append("[")
                        .append(i + 1)
                        .append("] ")
                        .append(sources.get(i).citationSnippet())
                        .append("\n");
            }
        }

        builder.append("\nCurrent user request:\n")
                .append(userPrompt)
                .append("\n");
        return builder.toString();
    }

    private String runInferenceWithTimeout(
            String fullPrompt,
            List<String> conversation,
            List<SearchResult> sources,
            RuntimeProfileResolver.ResolvedProfile profile) {
        InferenceEngine inferenceEngine = selectInferenceEngine(profile);
        InferenceContext context = new InferenceContext(conversation, sources);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> future = executor.submit(() -> inferenceEngine.generate(fullPrompt, context, profile));
            return future.get(inferenceTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("Inference timed out after {} ms", inferenceTimeoutMs);
            return "I could not complete generation within the configured timeout of " + inferenceTimeoutMs
                    + "ms. Please retry with a shorter prompt.";
        } catch (ExecutionException e) {
            log.error("Inference failed", e.getCause());
            return "I hit an inference error while generating the response.";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Generation interrupted.";
        } finally {
            executor.shutdownNow();
        }
    }

    private static void streamTokens(String response) throws InterruptedException {
        System.out.print("assistant> ");
        String[] tokens = response.split("\\s+");
        for (String token : tokens) {
            System.out.print(token + " ");
            System.out.flush();
            Thread.sleep(8);
        }
        System.out.println();
    }

    private static void trimConversation(List<String> conversation, int contextWindowTokens) {
        while (estimateTokenCount(conversation) > contextWindowTokens && conversation.size() > 2) {
            conversation.remove(0);
            conversation.remove(0);
        }
    }

    private static int estimateTokenCount(List<String> conversation) {
        return conversation.stream()
                .mapToInt(turn -> turn.split("\\s+").length)
                .sum();
    }

    private void runBenchmark(RuntimeProfileResolver.ResolvedProfile profile) {
        if ("intel-igpu".equals(profile.requestedProfile())) {
            IntelGpuCapabilityProbe.CapabilityReport report = capabilityProbe.probeUbuntu2204();
            log.info("intel-igpu capability report available={} reason={} checks={}",
                    report.available(),
                    report.reason().isBlank() ? "none" : report.reason(),
                    report.checks());
        }

        long start = System.nanoTime();
        for (int i = 0; i < 1_000_000; i++) {
            Math.sqrt(i);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        log.info("Benchmark completed in {} ms", elapsedMs);
    }

    private void runImprovementPipeline() throws IOException {
        OfflineImprovementPipeline pipeline = new OfflineImprovementPipeline();
        ArtifactVersions candidate = new ArtifactVersions(
                SemanticVersion.parse("0.2.0"),
                SemanticVersion.parse("0.2.0"),
                SemanticVersion.parse("0.2.0"));
        GovernanceMetrics metrics = new GovernanceMetrics(0.04, 0.01, 0.91);
        GovernancePolicy policy = new GovernancePolicy(0.05, 0.02, 0.90);
        OfflineImprovementPipeline.PipelineResult result = pipeline.run(
                feedbackPath,
                datasetPath,
                adapterDir,
                versionRegistryPath,
                candidate,
                metrics,
                policy);
        log.info("Improvement pipeline completed examples={} adapter={} governancePassed={}",
                result.trainingExamples(),
                result.adapterArtifact(),
                result.governanceEvaluation().passed());
    }

}
