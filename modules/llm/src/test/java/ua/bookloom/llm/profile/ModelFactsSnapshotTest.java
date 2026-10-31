package ua.bookloom.llm.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ModelKind;

/** The GGUF-metadata and pipeline-tag readers over the owner's recorded snapshot (2026-10-28). */
class ModelFactsSnapshotTest {

    private static JsonNode snapshot;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in =
                ModelFactsSnapshotTest.class.getResourceAsStream("/ua/bookloom/llm/facts/model-facts-snapshot.json")) {
            snapshot = new ObjectMapper().readTree(in);
        }
    }

    @Test
    void snapshot_namesItsSource() {
        assertThat(snapshot.path("_source").asText()).contains("2026-10-28").contains("engine-local-snapshot");
    }

    @ParameterizedTest
    @CsvSource({
        "gemma4_e4b-mlx,gemma4",
        "muse-glimmer_30b-nvfp4-dflash,muse-glimmer",
        "gpt-oss_20b,gpt-oss",
        "qwen2.5_1.5b,qwen2",
        "qwen3.5_9b,qwen35"
    })
    void read_snapshotHeader_givesTheCanonicalArchitecture(final String model, final String architecture) {
        assertThat(new GgufMetadata().read(snapshot.path("gguf").path(model)).architecture())
                .isEqualTo(architecture);
    }

    @Test
    void read_headerWithBaseModel_readsItWithTheNameGrammar() {
        final GgufIdentity identity =
                new GgufMetadata().read(snapshot.path("gguf").path("qwen2.5_1.5b"));

        assertThat(identity.baseModels()).hasSize(1);
        assertThat(identity.baseModels().get(0).family()).isEqualTo("qwen");
        assertThat(identity.baseModels().get(0).version()).isEqualTo("2.5");
        assertThat(identity.baseModels().get(0).sizeB()).isEqualTo(1.5);
    }

    @Test
    void read_headerWithoutBaseModel_hasNone() {
        assertThat(new GgufMetadata()
                        .read(snapshot.path("gguf").path("gpt-oss_20b"))
                        .baseModels())
                .isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "bartowski/DeepSeek-R1-Distill-Qwen-7B-GGUF,LLM",
        "unsloth/Qwen3-VL-4B-Instruct-GGUF,LLM",
        "google/gemma-4-E4B-it-qat-q4_0-gguf,LLM",
        "nomic-ai/nomic-embed-text-v1.5-GGUF,EMBEDDING",
        "ggml-org/Qwen3-Reranker-0.6B-Q8_0-GGUF,RERANKER",
        "city96/FLUX.1-dev-gguf,IMAGE",
        "unsloth/LTX-2.3-GGUF,VIDEO"
    })
    void kind_snapshotTag_givesTheRepositorysKind(final String repository, final ModelKind kind) {
        final JsonNode tag = snapshot.path("pipelineTags").path(repository);

        assertThat(PipelineTags.kind(tag.asText(null))).contains(kind);
    }

    @Test
    void kind_repositoryWithNoTag_saysNothing() {
        final JsonNode tag = snapshot.path("pipelineTags").path("lmstudio-community/gpt-oss-20b-GGUF");

        assertThat(tag.isNull()).isTrue();
        assertThat(PipelineTags.kind(tag.asText(null))).isEqualTo(Optional.empty());
    }
}
