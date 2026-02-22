package com.swgllm.ingest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public interface ArtifactParser {
    boolean supports(Path path);

    Optional<ParsedArtifact> parse(Path path) throws IOException;
}
