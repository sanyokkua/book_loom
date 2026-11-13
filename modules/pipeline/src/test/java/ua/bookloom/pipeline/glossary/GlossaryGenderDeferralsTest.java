package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.book;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.decided;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.project.OpenProjects;

/**
 * Every writer of the glossary leaves the same gender-changed deferrals (task 13.3): the person's edit, the names
 * pass and a glossary file. The book is invented; a likely gender is confirmed after segments naming the character
 * were decided.
 */
class GlossaryGenderDeferralsTest {

    private static final List<String> LINES =
            List.of("Ivar came in.", "Nobody else came.", "Ivar sat down.", "Then Ivar left.", "Ivar slept.");

    private Injector injector;

    @BeforeEach
    void setUp() {
        injector = Guice.createInjector(
                new DocumentModule(),
                new InMemoryPersistenceModule(),
                binder -> binder.bind(Clock.class).toInstance(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));
        injector.getInstance(OpenProjects.class).put(PROJECT, book(LINES, "Title"));
        injector.getInstance(SegmentRepository.class)
                .saveAll(
                        PROJECT,
                        List.of(
                                decided("ch1:0", "Івар зайшов."),
                                decided("ch1:1", "Ніхто більше не прийшов."),
                                decided("ch1:2", "Івар сів."),
                                decided("ch1:3", "Тоді Івар пішов."),
                                decided("ch1:4", "Івар спав.")));
    }

    private GlossaryEntry likelyIvar() {
        return GlossaryEntry.of("p1:ivar", PROJECT, "Ivar", null, TermType.CHARACTER, Gender.UNKNOWN, false)
                .withSuggestedGender(Gender.MALE);
    }

    private List<Deferral> open() {
        return Objects.requireNonNull(
                injector.getInstance(DeferralRepository.class).open(PROJECT).data());
    }

    private void assertFourGenderChanged() {
        assertThat(open())
                .extracting(Deferral::reason, Deferral::segmentId)
                .containsExactlyInAnyOrder(
                        tuple(DeferralReason.GENDER_CHANGED, "ch1:0"),
                        tuple(DeferralReason.GENDER_CHANGED, "ch1:2"),
                        tuple(DeferralReason.GENDER_CHANGED, "ch1:3"),
                        tuple(DeferralReason.GENDER_CHANGED, "ch1:4"));
    }

    @Test
    void update_likelyGenderConfirmed_recordsOneGenderChangedPerNamingSegment() {
        final GlossaryService service = injector.getInstance(GlossaryServiceImpl.class);
        service.add(likelyIvar());

        service.update(likelyIvar().withGender(Gender.MALE));

        assertFourGenderChanged();
    }

    @Test
    void update_likelyGenderStaysLikely_recordsNothing() {
        final GlossaryService service = injector.getInstance(GlossaryServiceImpl.class);
        service.add(likelyIvar());

        service.update(likelyIvar().withMeaning("a note"));

        assertThat(open()).isEmpty();
    }

    @Test
    void store_genderSetAfterDecidedSegment_recordsGenderChanged() {
        final GlossaryRepository glossary = injector.getInstance(GlossaryRepository.class);
        glossary.add(likelyIvar());
        final EntryChanges<GlossaryEntry> names = new EntryChanges<>(
                List.of(),
                List.of(),
                List.of(new EntryChanges.Change<>(likelyIvar(), likelyIvar().withGender(Gender.MALE), null)));

        final var stored = new NamesPassStore(
                        glossary,
                        injector.getInstance(LexiconRepository.class),
                        injector.getInstance(SegmentRepository.class),
                        injector.getInstance(DeferralRepository.class),
                        injector.getInstance(OpenProjects.class),
                        "uk")
                .store(PROJECT, names, new EntryChanges<LexiconEntry>(List.of(), List.of(), List.of()));

        assertThat(stored.isOk()).as("%s", stored.error()).isTrue();
        assertFourGenderChanged();
    }

    @Test
    void apply_genderChangedAfterDecidedSegment_recordsGenderChanged() {
        final GlossaryRepository glossary = injector.getInstance(GlossaryRepository.class);
        glossary.add(likelyIvar());

        final var applied = new GlossaryRowsImport(
                        glossary,
                        injector.getInstance(SegmentRepository.class),
                        injector.getInstance(DeferralRepository.class),
                        injector.getInstance(SummaryRepository.class),
                        injector.getInstance(OpenProjects.class),
                        "en",
                        "uk")
                .apply(PROJECT, List.of(new GlossaryCsv.Row(2, "Ivar", null, TermType.CHARACTER, Gender.MALE, false)));

        assertThat(applied.isOk()).as("%s", applied.error()).isTrue();
        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::genderOrigin)
                .containsExactly(Origin.USER);
        assertFourGenderChanged();
    }
}
