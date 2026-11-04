package ua.bookloom.llm.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ListKind;
import ua.bookloom.api.llm.QuantTier;
import ua.bookloom.api.llm.SizeClass;

class ModelKnowledgeFilesTest {

    private static final String QUANT_HEADER = "token,family,bpw,needsImatrix,small,mid,large,moe,confidence,source";
    private static final String LANG_HEADER = "key,matchKind,pattern,listKind,codes,source,evidence";
    private static final String VERSION = "# data-version: 2026-10-28\n";

    @TempDir
    Path dir;

    private KnowledgeTables bundled() {
        final Result<KnowledgeTables> result = new ModelKnowledgeFiles().load();
        assertThat(result.isOk()).as("%s", result.error()).isTrue();
        return dataOf(result);
    }

    private static AppError errorOf(Result<?> result) {
        return Objects.requireNonNull(result.error());
    }

    private static KnowledgeTables dataOf(Result<KnowledgeTables> result) {
        return Objects.requireNonNull(result.data());
    }

    private String bundledText(String name) throws IOException {
        try (InputStream in = ModelKnowledgeFiles.class.getResourceAsStream("/ua/bookloom/llm/knowledge/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Result<KnowledgeTables> loadWith(String quantText, String languageText) throws IOException {
        final Path quant = Files.writeString(dir.resolve("quant-tiers.csv"), quantText);
        final Path lang = Files.writeString(dir.resolve("declared-languages.csv"), languageText);
        return new ModelKnowledgeFiles(quant, lang).load();
    }

    private static final String GOOD_QUANT =
            VERSION + QUANT_HEADER + "\nQ8_0,legacy,8.5,no,EXCELLENT,EXCELLENT,EXCELLENT,EXCELLENT,high,x\n";
    private static final String GOOD_LANG =
            VERSION + LANG_HEADER + "\nllama-3.1,PREFIX,llama-3.1,COMPLETE,en;de,card,\n";

    @Test
    void load_bundledFiles_returnsDataVersion20261028() {
        final KnowledgeTables tables = bundled();

        assertThat(tables.dataVersion()).isEqualTo("2026-10-28");
        assertThat(tables.quantRow("Q4_K_M").orElseThrow().tier(SizeClass.MID)).contains(QuantTier.ACCEPTABLE);
    }

    @Test
    void load_goodTempFiles_parses() throws IOException {
        assertThat(loadWith(GOOD_QUANT, GOOD_LANG).isOk()).isTrue();
    }

    @Test
    void load_rowWithFiveColumnsOfSix_returnsInternalError() throws IOException {
        final String badQuant = bundledText("quant-tiers.csv") + "Q9_9,k,1,no,GOOD,GOOD,GOOD,GOOD,high\n";
        final Result<KnowledgeTables> quant = loadWith(badQuant, bundledText("declared-languages.csv"));
        final String badLang = bundledText("declared-languages.csv") + "x,PREFIX,x,NONE,\n";
        final Result<KnowledgeTables> lang = loadWith(bundledText("quant-tiers.csv"), badLang);

        assertThat(errorOf(quant).code()).isEqualTo(ErrorCode.internal);
        assertThat(errorOf(quant).message()).contains("quant-tiers.csv").contains("row 56");
        assertThat(errorOf(lang).code()).isEqualTo(ErrorCode.internal);
        assertThat(errorOf(lang).message()).contains("declared-languages.csv").contains("row 90");
    }

    @Test
    void load_glimmerAndBolt_haveNoneRows() {
        final KnowledgeTables tables = bundled();

        assertThat(tables.languageRow("muse-glimmer").orElseThrow().listKind()).isEqualTo(ListKind.NONE);
        assertThat(tables.languageRow("nemotron-3.5-bolt").orElseThrow().listKind())
                .isEqualTo(ListKind.NONE);
        assertThat(tables.languageRow("nemotron-3.5-bolt").orElseThrow().matchKind())
                .isEqualTo(MatchKind.EXACT);
    }

    @Test
    void load_missingDataVersionLine_returnsInternalError() throws IOException {
        final Result<KnowledgeTables> result = loadWith(QUANT_HEADER + "\n", GOOD_LANG);

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.internal);
        assertThat(errorOf(result).message()).contains("quant-tiers.csv").contains("data-version");
    }

    @Test
    void load_unknownTierValue_returnsInternalError() throws IOException {
        final String quant = VERSION + QUANT_HEADER + "\nQ8_0,legacy,8.5,no,SPLENDID,GOOD,GOOD,GOOD,high,x\n";

        final Result<KnowledgeTables> result = loadWith(quant, GOOD_LANG);

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.internal);
        assertThat(errorOf(result).message()).contains("quant-tiers.csv").contains("row 3");
    }

    @Test
    void load_duplicateKey_returnsInternalError() throws IOException {
        final String dup = GOOD_LANG + "llama-3.1,PREFIX,llama-3.1,NONE,,card,\n";

        assertThat(errorOf(loadWith(GOOD_QUANT, dup)).code()).isEqualTo(ErrorCode.internal);
    }

    @Test
    void load_quotedCommaField_parses() throws IOException {
        final String lang = VERSION + LANG_HEADER + "\nk,EXACT,k,NONE,,\"card, with \"\"comma\"\"\",\n";

        final Result<KnowledgeTables> result = loadWith(GOOD_QUANT, lang);

        assertThat(dataOf(result).languageRow("k").orElseThrow().source()).isEqualTo("card, with \"comma\"");
    }

    @ParameterizedTest
    @CsvSource({"uk,false", "ru,true", "zh,true"})
    void load_qwen25_isCompleteWithoutUk(String code, boolean listed) {
        final LanguageRow row = bundled().languageRow("qwen2.5").orElseThrow();

        assertThat(row.listKind()).isEqualTo(ListKind.COMPLETE);
        assertThat(row.codes().contains(code)).isEqualTo(listed);
    }

    @Test
    void load_mamayLm_isPositiveOnlyWithRunEvidence() {
        final LanguageRow mamay = bundled().languageRow("mamaylm").orElseThrow();
        final LanguageRow lapa = bundled().languageRow("lapa").orElseThrow();

        assertThat(mamay.listKind()).isEqualTo(ListKind.POSITIVE_ONLY);
        assertThat(mamay.codes()).containsExactlyInAnyOrder("uk", "en");
        assertThat(mamay.evidence()).contains("BOOKLOOM-RUN:ru>uk");
        assertThat(lapa.listKind()).isEqualTo(ListKind.POSITIVE_ONLY);
        assertThat(lapa.evidence()).doesNotContain("BOOKLOOM-RUN");
    }

    @Test
    void load_qwen3_isPositiveOnlyWithRussianUkrainianAndEnglish() {
        final LanguageRow qwen3 = bundled().languageRow("qwen3").orElseThrow();

        assertThat(qwen3.listKind()).isEqualTo(ListKind.POSITIVE_ONLY);
        assertThat(qwen3.codes()).containsExactlyInAnyOrder("ru", "uk", "en");
    }

    @Test
    void load_gemma4_carriesTheBookLoomEnToUkRunAndNvfp4MeasurementOnE4b() {
        final LanguageRow gemma4 = bundled().languageRow("gemma4").orElseThrow();

        assertThat(gemma4.listKind()).isEqualTo(ListKind.NONE);
        assertThat(gemma4.evidence()).contains("BOOKLOOM-RUN:en>uk@e4b").contains("BOOKLOOM-QUANT:nvfp4@e4b");
    }
}
