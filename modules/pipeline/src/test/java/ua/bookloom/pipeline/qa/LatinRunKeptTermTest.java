package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Narrator;

/**
 * A source run of Latin words that is a term the glossary keeps as written is no untranslated phrase (15i.T4): the
 * Latin-run gate leaves it alone whether the kept term is a locked rendering or an unlocked one, and still blocks any
 * other run the source has. The target texts here still hold the kept term, as the final audit and the consistency pass
 * read them.
 */
class LatinRunKeptTermTest {

    private static final String NELL_SOURCE = "Nell bought a lens from KESSEL OPTIK on her way home.";
    private static final String NELL_TARGET = "Нелл купила об'єктив у KESSEL OPTIK дорогою додому.";

    private static SoftCheckInput input(
            final String source,
            final String target,
            final List<LockedRendering> locked,
            final List<String> pairs,
            final List<String> terms) {
        return new SoftCheckInput(
                source,
                target,
                target,
                "en",
                "uk",
                ForeignPassagePolicy.TRANSLATE,
                NamePolicy.TRANSLITERATE,
                null,
                terms,
                locked,
                Narrator.unspecified(),
                pairs);
    }

    private static boolean latinRunBlocks(final SoftCheckInput input) {
        return QaEvaluator.evaluate(List.of(), input).hardGates().stream()
                .anyMatch(result -> result.check() == CheckName.LATIN_RUN && !result.passed());
    }

    // IF a locked brand that came back as itself counted as a left-over run, THEN the paragraph that holds it could
    // never be accepted, the exact failure of the brand and magazine paragraphs of the real run.
    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "a two-word brand|Nell bought a lens from KESSEL OPTIK on her way home.|Нелл купила об'єктив у KESSEL OPTIK дорогою додому.|KESSEL OPTIK",
                "a three-word title|On the train he read the latest Weekly Courier Digest from cover to cover.|У поїзді він прочитав свіжий Weekly Courier Digest від початку до кінця.|Weekly Courier Digest",
            })
    void evaluate_lockedTermKeptAsWritten_isNoLatinRun(
            final String name, final String source, final String target, final String term) {
        final SoftCheckInput locked =
                input(source, target, List.of(new LockedRendering(term, term)), List.of(), List.of(term));

        assertThat(latinRunBlocks(locked)).isFalse();
    }

    @Test
    void evaluate_unlockedTermWhoseRenderingIsItself_isNoLatinRun() {
        final SoftCheckInput unlocked = input(
                NELL_SOURCE, NELL_TARGET, List.of(), List.of("KESSEL OPTIK → KESSEL OPTIK"), List.of("KESSEL OPTIK"));

        assertThat(latinRunBlocks(unlocked)).isFalse();
    }

    // The same words with no kept term behind them are a copied phrase, as before.
    @Test
    void evaluate_sameRunWithNoKeptTerm_stillBlocks() {
        final SoftCheckInput unknown = input(NELL_SOURCE, NELL_TARGET, List.of(), List.of(), List.of());

        assertThat(latinRunBlocks(unknown)).isTrue();
    }

    // IF the exemption covered every Latin run in a paragraph that holds one kept term, THEN a copied English phrase
    // beside the brand would ship.
    @Test
    void evaluate_unrelatedRunBesideAKeptTerm_stillBlocks() {
        final SoftCheckInput beside = input(
                "He read the Daily Courier and bought a lens from KESSEL OPTIK.",
                "Він читав Daily Courier і купив об'єктив у KESSEL OPTIK.",
                List.of(new LockedRendering("KESSEL OPTIK", "KESSEL OPTIK")),
                List.of(),
                List.of("KESSEL OPTIK"));

        assertThat(latinRunBlocks(beside)).isTrue();
    }

    // A locked term rendered in Cyrillic is a translation, not a kept original: its Latin leftover is still a phrase.
    @Test
    void evaluate_lockedTermWithACyrillicRendering_doesNotExemptItsLatinSpelling() {
        final SoftCheckInput renderedElsewhere = input(
                NELL_SOURCE,
                NELL_TARGET,
                List.of(new LockedRendering("KESSEL OPTIK", "Кессел Оптік")),
                List.of(),
                List.of("KESSEL OPTIK"));

        assertThat(latinRunBlocks(renderedElsewhere)).isTrue();
    }
}
