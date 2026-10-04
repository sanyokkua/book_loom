package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.pipeline.checks.TextChecks;

/**
 * The per-language mini-corpora the matrix runs, held to what a model-free run can prove: each language has draft
 * cases whose markers are valid patterns, and reviewer cases with a clean and a defective candidate each, where the
 * deterministic text checks flag no clean candidate.
 */
class LanguageCorpusTest {

    @ParameterizedTest
    @MethodSource("ua.bookloom.pipeline.prompt.V1Languages#languages")
    void corpus_everyV1Language_hasDraftAndReviewerCases(final String tag) {
        final LanguageCorpus corpus = EvalCorpus.language(tag);

        assertThat(corpus.targetLanguage()).isEqualTo(tag);
        assertThat(corpus.sourceLanguage()).isNotBlank().isNotEqualTo(tag);
        assertThat(corpus.drafts()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(corpus.reviews()).anyMatch(DefectCase::defective).anyMatch(review -> !review.defective());
    }

    @ParameterizedTest
    @MethodSource("ua.bookloom.pipeline.prompt.V1Languages#languages")
    void corpus_draftMarkers_areValidPatternsAndCaseNamesAreUnique(final String tag) {
        final LanguageCorpus corpus = EvalCorpus.language(tag);

        assertThat(corpus.drafts())
                .allSatisfy(draft ->
                        assertThat(Pattern.compile(draft.expect().marker())).isNotNull());
        assertThat(corpus.drafts().stream().map(EvalCase.Draft::name)).doesNotHaveDuplicates();
        assertThat(corpus.drafts()).allSatisfy(draft -> assertThat(draft.name()).startsWith(tag + "-"));
    }

    @ParameterizedTest
    @MethodSource("ua.bookloom.pipeline.prompt.V1Languages#languages")
    void corpus_cleanCandidates_areFlaggedByNoDeterministicCheck(final String tag) {
        final LanguageCorpus corpus = EvalCorpus.language(tag);

        assertThat(corpus.reviews().stream().filter(review -> !review.defective()))
                .allSatisfy(review -> assertThat(
                                TextChecks.run(review.source(), review.candidate(), corpus.sourceLanguage(), tag))
                        .as(review.id())
                        .isEmpty());
    }
}
