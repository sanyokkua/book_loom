package ua.bookloom.pipeline.proofread;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.project.ChapterSummary;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.glossary.GlossaryServiceImpl;

/**
 * A pinned or locked target the person renames reaches the kept chapter summaries (design D10, "Store"): whole word
 * for a locked entry, form by form for a pinned one, never for a suggestion, and with no model call.
 */
class SummarySweepTest {

    private static final String PROJECT = "p1";

    private final Injector injector = Guice.createInjector(
            new DocumentModule(),
            new InMemoryPersistenceModule(),
            binder -> binder.bind(Clock.class).toInstance(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));
    private final SummaryRepository summaries = injector.getInstance(SummaryRepository.class);

    private static ChapterSummary summary(final String chapterKey, final String text) {
        return new ChapterSummary(
                PROJECT,
                chapterKey,
                chapterKey.substring(0, chapterKey.indexOf(':')),
                0,
                12,
                text,
                "hash-" + chapterKey);
    }

    private static GlossaryEntry pinned(final String term, final String target) {
        return GlossaryEntry.of(PROJECT + ":" + term, PROJECT, term, target, TermType.CHARACTER, Gender.MALE, false);
    }

    private static GlossaryEntry locked(final String term, final String target) {
        return GlossaryEntry.of(PROJECT + ":" + term, PROJECT, term, target, TermType.CHARACTER, Gender.MALE, true);
    }

    private List<ChapterSummary> stored() {
        return Objects.requireNonNull(summaries.list(PROJECT).data(), "summaries");
    }

    @Test
    void rename_pinnedTarget_swappedInSummary() {
        summaries.save(summary("ch1:0", "Мірона зустріли біля вежі. Мірон мовчав."));
        summaries.save(summary("ch2:0", "Ніхто не прийшов."));

        final int changed = SummarySweep.swap(summaries, pinned("Мирон", "Мірон"), pinned("Мирон", "Мирослав"), "uk");

        assertThat(changed).isEqualTo(1);
        assertThat(stored())
                .containsExactly(
                        summary("ch1:0", "Мирослава зустріли біля вежі. Мирослав мовчав."),
                        summary("ch2:0", "Ніхто не прийшов."));
    }

    @Test
    void rename_lockedTarget_wholeWordSwapped() {
        summaries.save(summary("ch1:0", "Алєксєй прийшов, а Алєксєйко лишився."));

        SummarySweep.swap(summaries, locked("Алексей", "Алєксєй"), locked("Алексей", "Олексій"), "uk");

        assertThat(stored()).containsExactly(summary("ch1:0", "Олексій прийшов, а Алєксєйко лишився."));
    }

    @Test
    void rename_suggestedTarget_untouched() {
        summaries.save(summary("ch1:0", "Мірон мовчав."));
        final GlossaryEntry suggested = pinned("Мирон", "Мірон").withSuggestedTarget("Мирослав");

        final int changed = SummarySweep.swap(summaries, pinned("Мирон", "Мірон"), suggested, "uk");

        assertThat(changed).isZero();
        assertThat(stored()).containsExactly(summary("ch1:0", "Мірон мовчав."));
    }

    @Test
    void update_lockedTargetRenamedByThePerson_summarySwapped() {
        final GlossaryServiceImpl glossary = injector.getInstance(GlossaryServiceImpl.class);
        glossary.add(locked("Алексей", "Алєксєй"));
        summaries.save(summary("ch1:0", "Алєксєй прийшов."));

        glossary.update(locked("Алексей", "Олексій"));

        assertThat(stored()).containsExactly(summary("ch1:0", "Олексій прийшов."));
    }
}
