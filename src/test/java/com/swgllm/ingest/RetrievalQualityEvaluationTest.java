package com.swgllm.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RetrievalQualityEvaluationTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPrioritizeSwgLuaAndDatatableContentForKnownQueries() throws Exception {
        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo.resolve("scripts"));
        Files.createDirectories(repo.resolve("datatables"));
        Files.createDirectories(repo.resolve("docs"));

        Files.writeString(repo.resolve("scripts/jedi_unlock.lua"), """
                function unlock_jedi(player)
                  local village_phase = getVillagePhase(player)
                  if village_phase >= 4 then
                    grantFSQuest(player)
                  end
                end
                """);

        Files.writeString(repo.resolve("datatables/space_loot.tab"), """
                template\tmin_level\tmax_level
                object/weapon/ranged/pistol/shared_pistol_dl44.iff\t30\t90
                """);

        Files.writeString(repo.resolve("docs/readme.md"), "General deployment notes");

        Path index = tempDir.resolve("vector-index.json");
        Path state = tempDir.resolve("state.json");
        EmbeddingService embeddingService = new LocalModelEmbeddingService(384);
        new IngestionService(embeddingService).ingest(repo, index, state);

        RetrievalService retrieval = new RetrievalService(embeddingService);
        List<SearchResult> jediResults = retrieval.retrieve(index, "how does jedi village unlock work", 3);
        assertTrue(jediResults.getFirst().chunk().metadata().sourcePath().contains("jedi_unlock.lua"));

        List<SearchResult> lootResults = retrieval.retrieve(index, "dl44 loot datatable", 3);
        assertTrue(lootResults.getFirst().chunk().metadata().sourcePath().contains("space_loot.tab"));
    }

    @Test
    void shouldForceReembedWhenEmbeddingVersionChanges() throws Exception {
        Path repo = tempDir.resolve("versioned-repo");
        Files.createDirectories(repo);
        Files.writeString(repo.resolve("Example.java"), "public class Example { void go() {} }");

        Path index = tempDir.resolve("versioned-index.json");
        Path state = tempDir.resolve("versioned-state.json");

        IngestionService firstPass = new IngestionService(new HashingEmbeddingService(64));
        IngestionReport firstReport = firstPass.ingest(repo, index, state);
        assertEquals(1, firstReport.processedFiles());

        IngestionService secondPass = new IngestionService(new LocalModelEmbeddingService(64));
        IngestionReport secondReport = secondPass.ingest(repo, index, state);
        assertEquals(1, secondReport.processedFiles());

        LocalJsonVectorIndex loaded = LocalJsonVectorIndex.load(index);
        assertTrue(loaded.requiresReembedding("some-other-version"));
        assertTrue(loaded.search(new LocalModelEmbeddingService(64).embed("example"), 1).size() == 1);
    }
}
