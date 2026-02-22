package com.swgllm.ingest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexArtifactParser implements ArtifactParser {
    private final List<String> extensions;
    private final Pattern symbolPattern;

    public RegexArtifactParser(List<String> extensions, Pattern symbolPattern) {
        this.extensions = extensions;
        this.symbolPattern = symbolPattern;
    }

    @Override
    public boolean supports(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return extensions.stream().anyMatch(fileName::endsWith);
    }

    @Override
    public Optional<ParsedArtifact> parse(Path path) throws IOException {
        if (!supports(path)) {
            return Optional.empty();
        }
        String content = Files.readString(path);
        List<String> symbols = new ArrayList<>();
        Matcher matcher = symbolPattern.matcher(content);
        while (matcher.find()) {
            symbols.add(matcher.group(1));
        }
        return Optional.of(new ParsedArtifact(path, content, symbols));
    }
}
