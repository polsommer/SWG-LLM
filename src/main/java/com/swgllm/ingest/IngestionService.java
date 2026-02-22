package com.swgllm.ingest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class IngestionService {
    private final List<ArtifactParser> parsers;
    private final Chunker chunker;
    private final EmbeddingService embeddingService;
    private final IngestionStateStore stateStore;
    private final GitMetadataReader gitMetadataReader;

    public IngestionService(EmbeddingService embeddingService) {
        this.parsers = defaultParsers();
        this.chunker = new Chunker(80, 12);
        this.embeddingService = embeddingService;
        this.stateStore = new IngestionStateStore();
        this.gitMetadataReader = new GitMetadataReader();
    }

    public IngestionReport ingest(Path repoPath, Path indexPath, Path statePath) throws IOException {
        LocalJsonVectorIndex index = LocalJsonVectorIndex.load(indexPath);
        Map<String, String> previousState = stateStore.load(statePath);
        if (index.requiresReembedding(embeddingService.version())) {
            previousState = new java.util.HashMap<>();
            index.clear();
        }
        Map<String, String> newState = new java.util.HashMap<>();

        String commitHash = gitMetadataReader.commitHash(repoPath);
        String versionTag = gitMetadataReader.versionTag(repoPath);

        List<Path> files = Files.walk(repoPath)
                .filter(Files::isRegularFile)
                .filter(this::isSupported)
                .toList();

        int processed = 0;
        int skipped = 0;

        for (Path file : files) {
            String relative = repoPath.relativize(file).toString();
            String fingerprint = IngestionStateStore.fingerprint(file);
            newState.put(relative, fingerprint);
            if (fingerprint.equals(previousState.get(relative))) {
                skipped++;
                continue;
            }

            index.removeBySourcePath(relative);
            ParsedArtifact artifact = parse(file).orElse(null);
            if (artifact == null) {
                continue;
            }

            ParsedArtifact relativeArtifact = new ParsedArtifact(Path.of(relative), artifact.content(), artifact.symbols());
            List<DocumentChunk> chunks = chunker.chunk(relativeArtifact, commitHash, versionTag);
            for (DocumentChunk chunk : chunks) {
                index.upsert(chunk, embeddingService.embed(chunk.text()), embeddingService.version());
            }
            processed++;
        }

        for (String previousFile : previousState.keySet()) {
            if (!newState.containsKey(previousFile)) {
                index.removeBySourcePath(previousFile);
            }
        }

        index.save(indexPath);
        stateStore.save(statePath, newState);

        return new IngestionReport(processed, skipped, files.size(), commitHash, versionTag);
    }

    private boolean isSupported(Path path) {
        return parsers.stream().anyMatch(parser -> parser.supports(path));
    }

    private java.util.Optional<ParsedArtifact> parse(Path path) throws IOException {
        for (ArtifactParser parser : parsers) {
            if (parser.supports(path)) {
                return parser.parse(path);
            }
        }
        return java.util.Optional.empty();
    }

    private List<ArtifactParser> defaultParsers() {
        List<ArtifactParser> all = new ArrayList<>();
        all.add(new RegexArtifactParser(List.of(".java"), Pattern.compile("(?:class|interface|enum|record|void|public|private|protected)\\s+([A-Za-z_][A-Za-z0-9_]*)")));
        all.add(new RegexArtifactParser(List.of(".kt", ".kts"), Pattern.compile("(?:class|object|interface|fun|val|var)\\s+([A-Za-z_][A-Za-z0-9_]*)")));
        all.add(new RegexArtifactParser(List.of(".cpp", ".cc", ".hpp", ".h"), Pattern.compile("(?:class|struct|enum|namespace|void|int|float|double|bool|string)\\s+([A-Za-z_][A-Za-z0-9_]*)")));
        all.add(new RegexArtifactParser(List.of(".lua"), Pattern.compile("(?:function|local\\s+function|table\\.insert)\\s+([A-Za-z_][A-Za-z0-9_:.-]*)")));
        all.add(new RegexArtifactParser(List.of(".md", ".markdown"), Pattern.compile("^#+\\s+(.+)$", Pattern.MULTILINE)));
        all.add(new RegexArtifactParser(List.of(".yml", ".yaml", ".properties", ".conf", ".json", ".xml", ".gradle", ".kts"), Pattern.compile("^\\s*([A-Za-z0-9_.-]+)\\s*[:=]", Pattern.MULTILINE)));
        all.add(new RegexArtifactParser(List.of(".sql"), Pattern.compile("(?i)\\b(?:create\\s+(?:table|view|function)|alter\\s+table|insert\\s+into)\\s+([A-Za-z_][A-Za-z0-9_.]*)")));
        all.add(new RegexArtifactParser(List.of(".sh", ".bash", ".zsh", ".ps1", ".bat"), Pattern.compile("(?m)^\\s*(?:function\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\(|\\{)")));
        all.add(new RegexArtifactParser(List.of(".iff", ".tre", ".tab", ".stf"), Pattern.compile("(?m)^\\s*([A-Za-z_][A-Za-z0-9_.-]+)\\s*(?:=|:)")));
        return all;
    }
}
