package com.swgllm;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.swgllm.feedback.FeedbackCaptureService;
import com.swgllm.feedback.FeedbackRating;
import com.swgllm.feedback.FeedbackRecord;
import com.swgllm.governance.GovernanceMetrics;
import com.swgllm.governance.GovernancePolicy;
import com.swgllm.ingest.HashingEmbeddingService;
import com.swgllm.ingest.IngestionReport;
import com.swgllm.ingest.IngestionService;
import com.swgllm.ingest.RetrievalService;
import com.swgllm.ingest.SearchResult;
import com.swgllm.pipeline.OfflineImprovementPipeline;
import com.swgllm.versioning.ArtifactVersions;
import com.swgllm.versioning.RolloutState;
import com.swgllm.versioning.SemanticVersion;
import com.swgllm.versioning.VersionRolloutManager;

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
    private final HashingEmbeddingService embeddingService = new HashingEmbeddingService(384);

    enum Mode {
        interactive,
        benchmark,
        ingest,
        retrieve,
        feedback,
        improve
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        log.info("Starting SWG-LLM in {} mode", mode);
        log.info("Using config file: {}", configPath);
        log.debug("HTTP client configured: {}", httpClient.connectionPool());

        VersionRolloutManager rolloutManager = new VersionRolloutManager();
        RolloutState rolloutState = rolloutManager.load(versionRegistryPath);
        log.info("Active versions prompt={} retriever={} model={} canaryModel={}",
                rolloutState.currentStable().promptTemplateVersion(),
                rolloutState.currentStable().retrieverVersion(),
                rolloutState.currentStable().modelVersion(),
                rolloutState.currentCanary().modelVersion());

        if (mode == Mode.benchmark) {
            runBenchmark();
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

        return 0;
    }

    private void runBenchmark() {
        long start = System.nanoTime();
        for (int i = 0; i < 1_000_000; i++) {
            Math.sqrt(i);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        log.info("Benchmark completed in {} ms", elapsedMs);
    }
}
