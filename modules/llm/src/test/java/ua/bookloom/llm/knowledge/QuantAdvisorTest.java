package ua.bookloom.llm.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ModelIdentity;
import ua.bookloom.api.llm.ModelKind;
import ua.bookloom.api.llm.QuantAdvice;
import ua.bookloom.api.llm.QuantTier;
import ua.bookloom.api.llm.SizeClass;
import ua.bookloom.llm.profile.ModelIdentityParser;

class QuantAdvisorTest {

    private final QuantAdvisor advisor = new QuantAdvisor(new ModelKnowledgeFiles());
    private final ModelIdentityParser parser = new ModelIdentityParser();

    private static ModelIdentity identity(
            final @Nullable Double sizeB, final @Nullable String label, final List<String> variants) {
        return new ModelIdentity(
                "test", null, null, sizeB, null, null, variants, ModelKind.LLM, null, null, label, List.of());
    }

    private static ModelFacts facts(final @Nullable String quant, final @Nullable String parameterSize) {
        return new ModelFacts(
                null,
                null,
                null,
                quant,
                parameterSize,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                false);
    }

    @ParameterizedTest
    @CsvSource({
        "4,    Q4_K_M,    '',    ACCEPTABLE",
        "24,   IQ4_NL,    '',    GOOD",
        "24,   Q3_K_M,    '',    ACCEPTABLE",
        "9,    Q4_0,      '',    POOR",
        "9,    Q4_0,      qat,   GOOD",
        "12,   UD-Q2_K_XL,'',    POOR",
        "4,    UD-IQ2_XS, '',    UNUSABLE",
        "4,    Q4_0_4_4,  '',    REMOVED",
        "4,    Q9_X,      '',    UNMEASURED",
        "4,    Q4_K_XL,   '',    ACCEPTABLE",
        "24,   Q2_K_XL,   '',    POOR",
    })
    void advise_nameTokenAndSize_givesTier(
            final double sizeB, final String label, final String variant, final QuantTier expected) {
        final List<String> variants = variant.isEmpty() ? List.of() : List.of(variant);

        final QuantAdvice advice = advisor.advise(identity(sizeB, label, variants), null);

        assertThat(advice.tier()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "'glm-4.7-flash:UD-Q4_K_XL',  Q4_K_M, GOOD",
        "'gemma4:e4b-mlx',            nvfp4,  ACCEPTABLE",
        "'gpt-oss:20b',               MXFP4,  GOOD",
    })
    void advise_namedModelWithFacts_givesTier(final String name, final String factsQuant, final QuantTier expected) {
        final QuantAdvice advice = advisor.advise(parser.parse(name).identity(), facts(factsQuant, null));

        assertThat(advice.tier()).isEqualTo(expected);
    }

    @Test
    void advise_sevenBillionWithNoTokenAnywhere_givesNoTokenAndNoTier() {
        final QuantAdvice advice = advisor.advise(identity(7.0, null, List.of()), facts(null, null));

        assertThat(advice.token()).isEmpty();
        assertThat(advice.tier()).isNull();
        assertThat(advice.source()).isEqualTo("none");
    }

    @Test
    void advise_unknownSize_givesTokenAndBitClassOnly() {
        final QuantAdvice advice = advisor.advise(identity(null, "Q4_K_M", List.of()), null);

        assertThat(advice.token()).isEqualTo("Q4_K_M");
        assertThat(advice.bitClass()).isEqualTo("4-bit");
        assertThat(advice.sizeClass()).isNull();
        assertThat(advice.tier()).isNull();
    }

    @Test
    void advise_unknownSizeAndFlatRow_givesTheSingleTier() {
        assertThat(advisor.advise(identity(null, "Q4_0_4_4", List.of()), null).tier())
                .isEqualTo(QuantTier.REMOVED);
    }

    @Test
    void advise_dynamicLabelWithFactsBase_flagsDynamicAndTakesFactsRow() {
        final QuantAdvice advice =
                advisor.advise(parser.parse("glm-4.7-flash:UD-Q4_K_XL").identity(), facts("Q4_K_M", "29.9B"));

        assertThat(advice.dynamic()).isTrue();
        assertThat(advice.baseType()).isEqualTo("Q4_K");
        assertThat(advice.bitClass()).isEqualTo("4-bit");
        assertThat(advice.sizeClass()).isEqualTo(SizeClass.MOE);
        assertThat(advice.source()).isEqualTo("facts");
        assertThat(advice.token()).isEqualTo("UD-Q4_K_XL");
    }

    @Test
    void advise_factsDisagreeWithName_factsWin() {
        final QuantAdvice advice = advisor.advise(identity(24.0, "Q8_0", List.of()), facts("Q3_K_M", null));

        assertThat(advice.bitClass()).isEqualTo("3-bit");
        assertThat(advice.tier()).isEqualTo(QuantTier.ACCEPTABLE);
        assertThat(advice.source()).isEqualTo("facts");
    }

    @Test
    void advise_unrecognisedFactsQuant_doesNotHideNameRow() {
        final QuantAdvice advice = advisor.advise(identity(24.0, "Q3_K_M", List.of()), facts("weird", null));

        assertThat(advice.tier()).isEqualTo(QuantTier.ACCEPTABLE);
        assertThat(advice.source()).isEqualTo("name");
    }

    @Test
    void advise_factsParameterSizeWithoutNameSize_decidesTheBand() {
        final QuantAdvice advice = advisor.advise(identity(null, "Q4_K_M", List.of()), facts(null, "32.3B"));

        assertThat(advice.sizeClass()).isEqualTo(SizeClass.LARGE);
        assertThat(advice.tier()).isEqualTo(QuantTier.GOOD);
    }

    @Test
    void advise_qatNameOnQ4_0_flagsQatAndImatrix() {
        final QuantAdvice advice = advisor.advise(identity(9.0, "Q4_0", List.of("imatrix", "qat")), null);

        assertThat(advice.qat()).isTrue();
        assertThat(advice.imatrix()).isTrue();
        assertThat(advice.dynamic()).isFalse();
    }

    @Test
    void advise_nameWithQatWord_parserFlagsQat() {
        assertThat(parser.parse("gemma3:12b-it-qat").identity().variants()).contains("qat");
    }

    @Test
    void advise_knowledgeFilesMissing_returnsTokenOnlyAdvice(@TempDir final Path dir) {
        final QuantAdvisor broken =
                new QuantAdvisor(new ModelKnowledgeFiles(dir.resolve("none.csv"), dir.resolve("none2.csv")));

        final QuantAdvice advice = broken.advise(identity(9.0, "Q4_K_M", List.of()), null);

        assertThat(advice.token()).isEqualTo("Q4_K_M");
        assertThat(advice.tier()).isNull();
    }
}
