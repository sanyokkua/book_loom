package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;

/** Which segments count as a kept foreign passage, for catalogued and uncatalogued languages alike. */
class ForeignMarkingTest {

    private static final String ENGLISH_TEXT = "He opened the old door.";

    private static SoftCheckInput input(
            @Nullable String sourceLanguage, @Nullable String declaredLanguage, ForeignPassagePolicy policy) {
        return SoftCheckFixtures.scriptEcho(
                ENGLISH_TEXT,
                ENGLISH_TEXT,
                sourceLanguage,
                "uk",
                NamePolicy.TRANSLITERATE,
                policy,
                declaredLanguage,
                List.of());
    }

    @ParameterizedTest
    @CsvSource({"en,la", "la,en", "la,haw", "ar,en", "en,la-VA"})
    void isMarked_keepAndDeclaredLanguageDiffers_isTrue(String source, String declared) {
        assertThat(ForeignMarking.isMarked(input(source, declared, ForeignPassagePolicy.KEEP)))
                .isTrue();
    }

    @ParameterizedTest
    @CsvSource({"la,la-VA", "la,LA", "en,en-US", "la,"})
    void isMarked_keepAndDeclaredLanguageSame_isFalse(String source, @Nullable String declared) {
        assertThat(ForeignMarking.isMarked(input(source, declared, ForeignPassagePolicy.KEEP)))
                .isFalse();
    }

    @ParameterizedTest
    @CsvSource({"xx,la", "xx,", "xx,en"})
    void isMarked_keepAndSourceNotRecognized_isFalse(String source, @Nullable String declared) {
        assertThat(ForeignMarking.isMarked(input(source, declared, ForeignPassagePolicy.KEEP)))
                .isFalse();
    }

    @ParameterizedTest
    @CsvSource({"en,la", "la,en"})
    void isMarked_policyTranslate_isFalse(String source, String declared) {
        assertThat(ForeignMarking.isMarked(input(source, declared, ForeignPassagePolicy.TRANSLATE)))
                .isFalse();
    }

    @ParameterizedTest
    @CsvSource({"la,", "ar,"})
    void isMarked_uncataloguedSourceWithNoDeclaredLanguage_skipsTheScriptBranch(
            String source, @Nullable String declared) {
        assertThat(ForeignMarking.isMarked(input(source, declared, ForeignPassagePolicy.KEEP)))
                .isFalse();
    }

    @ParameterizedTest
    @CsvSource({"en,la,true", "en,en-US,false", "en,,false"})
    void isMarked_overStoredSegmentDeclaringALanguage_appliesTheSameRuleAsTheChecks(
            String source, @Nullable String declared, boolean expected) {
        assertThat(ForeignMarking.isMarked(segment(ENGLISH_TEXT, declared), source, ForeignPassagePolicy.KEEP))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"Привіт світе.,true", "He opened the ⟦g0⟧old⟦g1⟧ door.,false"})
    void isMarked_overStoredSegmentWrittenInAnotherScript_readsTheDisplayText(String masked, boolean expected) {
        assertThat(ForeignMarking.isMarked(segment(masked, null), "en", ForeignPassagePolicy.KEEP))
                .isEqualTo(expected);
    }

    private static Segment segment(String masked, @Nullable String declaredLanguage) {
        return new Segment(
                "Book.md:0",
                "Book.md",
                0,
                SegmentKind.PARAGRAPH,
                masked,
                masked,
                Map.of(),
                "h",
                null,
                null,
                new ByteSpanAnchor(0, masked.length()),
                null,
                SegmentStatus.PENDING,
                0.0,
                declaredLanguage);
    }
}
