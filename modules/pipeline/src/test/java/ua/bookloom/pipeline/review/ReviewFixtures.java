package ua.bookloom.pipeline.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookInspector;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.AlsoTranslate;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.Severity;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.TestBooks;
import ua.bookloom.pipeline.heal.QualityLoop;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.pipeline.project.ProjectServiceImpl;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * A real book imported through {@code ProjectServiceImpl} over the in-memory stores, with the review desk's parts
 * built directly over them. Every record starts PENDING, as an import stores it; a test decides the ones it looks at.
 * Public so backward revision's tests apply a proposal through the same real desk.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReviewFixtures {

    static final String FLAGGED_ID = "ch05.xhtml:11";
    static final String NAMES_ID = "ch07.xhtml:39";
    static final String SCRIPT_ID = "ch09.xhtml:2";
    static final String DOOR_ID = "ch07.xhtml:40";
    static final String FOREIGN_ID = "ch11.xhtml:1";
    static final String FOREIGN_SOURCE = "She whispered <i xml:lang=\"fr\">au revoir</i> and left.";
    static final String RAIN_SOURCE = "The rain stopped only in the evening.";
    static final String MONSTER_SOURCE = "The monster met me at midnight.";
    static final String MONSTER_TARGET = "Чудовисько зустріло мене опівночі.";

    private static final Instant RUN_START = Instant.parse("2026-01-01T00:00:00Z");
    private static final int CHAPTERS = 11;
    private static final int PARAGRAPHS = 42;

    /** Everything a test of the parts reads and writes, all over one set of stores. */
    public record Desk(
            String projectId,
            SegmentActions actions,
            ReviewQueries queries,
            SegmentRepository segments,
            DeferralRepository deferrals,
            ProjectRepository projects,
            OpenProjects openProjects,
            DocumentPort documents,
            RunRepository runs,
            GlossaryRepository glossary) {

        /** Queries over the same stores whose opened books are forgotten. */
        ReviewQueries queriesWithoutOpenBook() {
            return new ReviewQueries(new OpenProjects(), projects, segments, deferrals);
        }

        /** The retry over the same stores, deciding with {@code mode}'s threshold. */
        RetryDraft retryDraft(final ReviewMode mode) {
            return new RetryDraft(
                    documents,
                    openProjects,
                    projects,
                    segments,
                    runs,
                    new PromptTemplates(),
                    new ObjectMapper(),
                    Guice.createInjector().getInstance(QualityLoop.class),
                    mode);
        }

        /** The port over the real parts and the same stores. */
        public ReviewDeskImpl reviewDesk(final ReviewMode mode) {
            return new ReviewDeskImpl(actions, queries, retryDraft(mode), projects, segments);
        }
    }

    /**
     * Eleven chapters {@code ch01.xhtml}…{@code ch11.xhtml} of 42 paragraphs each, English to Ukrainian, holding the
     * monster, the door and the French phrase at the ids the constants name.
     */
    static Desk epub(final Path directory) {
        return epub(directory, brief(AlsoTranslate.defaults()));
    }

    static Desk epub(final Path directory, final BookBrief brief) {
        final List<String> names = IntStream.rangeClosed(1, CHAPTERS)
                .mapToObj(number -> String.format("ch%02d.xhtml", number))
                .toList();
        final List<List<String>> paragraphs = IntStream.rangeClosed(1, CHAPTERS)
                .mapToObj(ReviewFixtures::chapter)
                .toList();
        return open(TestBooks.epubAtRoot(directory.resolve("Book.epub"), names, paragraphs), brief);
    }

    /** An EPUB of two chapters whose navigation document lists seven labels, with the given switches. */
    static Desk epubWithNavigation(final Path directory, final BookBrief brief) {
        final List<String> labels = IntStream.rangeClosed(1, 7)
                .mapToObj(number -> "Label " + number)
                .toList();
        final List<List<String>> spine = List.of(List.of("One.", "Two."), List.of("Three."));
        return open(TestBooks.epubWithNavigation(directory.resolve("Book.epub"), spine, labels), brief);
    }

    /** An EPUB of {@code ch01.xhtml}, {@code ch02.xhtml}… holding {@code chapters}, English to Ukrainian. */
    public static Desk epubChapters(final Path directory, final List<List<String>> chapters) {
        final List<String> names = IntStream.rangeClosed(1, chapters.size())
                .mapToObj(number -> String.format("ch%02d.xhtml", number))
                .toList();
        return open(
                TestBooks.epubAtRoot(directory.resolve("Book.epub"), names, chapters), brief(AlsoTranslate.defaults()));
    }

    /** An EPUB of one chapter holding {@code paragraphs}, English to Ukrainian. */
    static Desk epubOf(final Path directory, final List<String> paragraphs) {
        return open(
                TestBooks.epub(directory.resolve("Book.epub"), List.of(paragraphs), "en"),
                brief(AlsoTranslate.defaults()));
    }

    /** A Markdown book of five paragraphs whose fifth, {@code Book.md:4}, has an emphasised word. */
    static Desk markdown(final Path directory) {
        return markdown(directory, "One.\n\nTwo.\n\nThree.\n\nFour.\n\nHe opened the *old* door.\n");
    }

    /** A Markdown book of {@code text}, whose paragraphs read {@code Book.md:0}, {@code Book.md:1}…. */
    static Desk markdown(final Path directory, final String text) {
        return open(TestBooks.markdown(directory.resolve("Book.md"), text), brief(AlsoTranslate.defaults()));
    }

    static BookBrief brief(final AlsoTranslate alsoTranslate) {
        final BookBrief defaults = BookBrief.defaults("en");
        return new BookBrief(
                "en",
                "uk",
                defaults.genre(),
                defaults.register(),
                defaults.voiceEra(),
                defaults.audience(),
                defaults.names(),
                defaults.foreignPassages(),
                defaults.footnotes(),
                defaults.units(),
                defaults.balance(),
                alsoTranslate,
                QualityDial.BALANCED);
    }

    /** Stores {@code id} as FLAGGED with the given machine target (both forms), or none when null. */
    static void flag(final Desk desk, final String id, @Nullable final String machineTarget) {
        update(
                desk,
                id,
                record -> record.withStatus(SegmentStatus.FLAGGED).withMachineTarget(machineTarget, machineTarget));
    }

    /** Stores {@code id} as ACCEPTED with the given machine target (both forms). */
    public static void accept(final Desk desk, final String id, final String machineTarget) {
        update(
                desk,
                id,
                record -> record.withStatus(SegmentStatus.ACCEPTED).withMachineTarget(machineTarget, machineTarget));
    }

    public static void update(final Desk desk, final String id, final UnaryOperator<SegmentRecord> change) {
        Objects.requireNonNull(
                desk.segments().update(desk.projectId(), id, change).data(), "updated " + id);
    }

    public static SegmentRecord stored(final Desk desk, final String id) {
        return Objects.requireNonNull(desk.segments().find(desk.projectId(), id).data(), "find " + id)
                .orElseThrow();
    }

    /** Stores {@code id} with the snapshot of what its first draft saw. */
    static void withContext(final Desk desk, final String id, final ContextSnapshot context) {
        update(
                desk,
                id,
                record -> new SegmentRecord(
                        record.projectId(),
                        record.segmentId(),
                        record.unitId(),
                        record.ord(),
                        record.kind(),
                        record.status(),
                        record.machineTarget(),
                        record.maskedMachineTarget(),
                        record.userTarget(),
                        record.maskedUserTarget(),
                        record.confidence(),
                        record.judgeScore(),
                        record.findings(),
                        record.path(),
                        record.repairRounds(),
                        record.reviewed(),
                        context));
    }

    /** Records the project's latest run in {@code state}; an ended run ends when it started. */
    static void latestRun(final Desk desk, final JobState state) {
        final boolean ended = state != JobState.RUNNING && state != JobState.PAUSED;
        Objects.requireNonNull(
                desk.runs()
                        .save(new RunRecord(
                                "run-1", desk.projectId(), RUN_START, ended ? RUN_START : null, state, 0, 0))
                        .data(),
                "saved run");
    }

    static QaFinding finding(final String kind, final String raisedBy) {
        return new QaFinding(kind, Severity.MEDIUM, "note", raisedBy);
    }

    private static List<String> chapter(final int number) {
        return IntStream.range(0, PARAGRAPHS)
                .mapToObj(index -> paragraph(number, index))
                .toList();
    }

    private static String paragraph(final int chapter, final int index) {
        return switch (chapter + ":" + index) {
            case "2:3" -> RAIN_SOURCE;
            case "5:11" -> MONSTER_SOURCE;
            case "7:40" -> "He opened the <em>old</em> door.";
            case "8:5" -> "He went away.";
            case "11:1" -> FOREIGN_SOURCE;
            default -> "Chapter " + chapter + " line " + index + ".";
        };
    }

    private static Desk open(final Path book, final BookBrief brief) {
        final Injector injector = Guice.createInjector(new DocumentModule(), new PersistenceModule());
        final DocumentPort documents = injector.getInstance(DocumentPort.class);
        final OpenProjects openProjects = injector.getInstance(OpenProjects.class);
        final ProjectRepository projects = injector.getInstance(ProjectRepository.class);
        final SegmentRepository segments = injector.getInstance(SegmentRepository.class);
        final DeferralRepository deferrals = injector.getInstance(DeferralRepository.class);
        final ProjectServiceImpl service = new ProjectServiceImpl(
                injector.getInstance(BookInspector.class), documents, projects, segments, openProjects);
        final ImportedBook imported =
                Objects.requireNonNull(service.importBook(book).data(), "imported book");
        final String id = Objects.requireNonNull(imported.projectId(), "project id");
        service.updateBrief(id, brief);
        return new Desk(
                id,
                new SegmentActions(documents, openProjects, projects, segments, deferrals),
                new ReviewQueries(openProjects, projects, segments, deferrals),
                segments,
                deferrals,
                projects,
                openProjects,
                documents,
                injector.getInstance(RunRepository.class),
                injector.getInstance(GlossaryRepository.class));
    }
}
