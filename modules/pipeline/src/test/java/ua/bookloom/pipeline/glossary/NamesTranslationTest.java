package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.google.inject.Guice;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TargetState;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Suggestion;

/**
 * What the names pass does once the suggestions are in (design D4, D3): a scanned form that fits two new names joins
 * one of them when both suggested targets agree, and a suggestion never replaces a target of a higher origin. All
 * names are invented.
 */
class NamesTranslationTest {

    private final GlossaryRepository glossary = Guice.createInjector(
                    new DocumentModule(), new InMemoryPersistenceModule())
            .getInstance(GlossaryRepository.class);

    private void hold(final String term, @Nullable final String suggested) {
        final GlossaryEntry entry =
                GlossaryEntry.of(GlossaryIds.of("p1", term), "p1", term, null, TermType.CHARACTER, Gender.MALE, false);
        glossary.add(suggested == null ? entry : entry.withSuggestedTarget(suggested));
    }

    private List<GlossaryEntry> held() {
        return Objects.requireNonNull(glossary.all("p1").data(), "held");
    }

    // A target the book proved, held in the suggested state: a shape only an automatic writer of the book's origin
    // makes.
    private static GlossaryEntry proved() {
        return new GlossaryEntry(
                GlossaryIds.of("p1", "Velk"),
                "p1",
                "Velk",
                List.of(),
                false,
                "Велк",
                TargetState.SUGGESTED,
                Origin.BOOK,
                TermType.CHARACTER,
                null,
                Origin.USER,
                Gender.MALE,
                Origin.USER,
                false,
                false,
                null,
                null,
                null);
    }

    private GlossaryEntry entryOf(final String term) {
        return held().stream()
                .filter(entry -> entry.term().equals(term))
                .findFirst()
                .orElseThrow();
    }

    // The scan kept `Градовского` apart, since it fits both new names; their suggestions agree, so it joins the name
    // of the class more of the glossary's names decline by: the adjective surname.
    @Test
    void collapse_twoNewNamesSameTarget_foldsTheForm() {
        hold("Градовский", "Градовський");
        hold("Градовской", "Градовський");
        hold("Градовского", null);

        final Result<Integer> collapsed = FormCollapse.collapse(glossary, "p1", "ru");

        assertThat(collapsed.data()).isEqualTo(1);
        assertThat(held())
                .extracting(GlossaryEntry::term, GlossaryEntry::forms)
                .containsExactlyInAnyOrder(tuple("Градовский", List.of("Градовского")), tuple("Градовской", List.of()));
    }

    // Different targets are different people: the form stays an entry of its own.
    @Test
    void collapse_twoNewNamesDifferentTargets_leavesTheForm() {
        hold("Градовский", "Градовський");
        hold("Градовской", "Градовськой");
        hold("Градовского", null);

        final Result<Integer> collapsed = FormCollapse.collapse(glossary, "p1", "ru");

        assertThat(collapsed.data()).isZero();
        assertThat(held()).extracting(GlossaryEntry::term).contains("Градовского");
    }

    // A competitor the glossary does not hold decides nothing.
    @Test
    void collapse_competitorMissing_leavesTheForm() {
        hold("Градовский", "Градовський");
        hold("Градовского", null);

        assertThat(FormCollapse.collapse(glossary, "p1", "ru").data()).isZero();
    }

    // IF a likely suggestion replaced a target the book proved, THEN a guess would overwrite a fact.
    @Test
    void apply_suggestionOverATargetOfHigherOrigin_isDropped() {
        final GlossaryEntry proved = proved();
        glossary.add(proved);
        hold("Hale", null);

        final Result<GlossaryReviewReport> report = ReviewCommit.apply(
                glossary,
                "p1",
                new ReviewCommit.Answers(
                        List.of(),
                        List.of(),
                        List.of(new Suggestion(proved, "Вельк"), new Suggestion(entryOf("Hale"), "Гейл"))),
                "en",
                List.of());

        assertThat(report.isOk()).as("%s", report.error()).isTrue();
        assertThat(held())
                .extracting(GlossaryEntry::term, GlossaryEntry::target, GlossaryEntry::targetOrigin)
                .containsExactlyInAnyOrder(tuple("Velk", "Велк", Origin.BOOK), tuple("Hale", "Гейл", Origin.LIKELY));
    }
}
