package ua.bookloom.pipeline;

import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.glossary.GlossaryIds;
import ua.bookloom.pipeline.proofread.DeferralRegister;
import ua.bookloom.pipeline.proofread.Mark;
import ua.bookloom.pipeline.proofread.MarkList;

/** The stored state a Proofread stage test sets up before a run and reads after it, over a job test's project. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProofreadProjects {

    /** The mark list as the desk would read it from the stored state after the run. */
    static List<Mark> marks(final TestProject project) {
        final BookBrief brief =
                ok(project.stores().projects().find(project.id())).orElseThrow().brief();
        return MarkList.of(
                new MarkList.Book(
                        brief,
                        Objects.requireNonNull(project.stores().openProjects().get(project.id()), "open book"),
                        ok(project.stores().glossary().all(project.id())),
                        ok(project.stores().lexicon().all(project.id()))),
                ok(project.stores().segments().all(project.id())),
                openDeferrals(project));
    }

    static GlossaryEntry character(
            final TestProject project, final String term, final String target, final Gender gender) {
        return GlossaryEntry.of(
                GlossaryIds.of(project.id(), term), project.id(), term, target, TermType.CHARACTER, gender, false);
    }

    static GlossaryEntry locked(final TestProject project, final String term, final String target) {
        return GlossaryEntry.of(
                GlossaryIds.of(project.id(), term), project.id(), term, target, TermType.CHARACTER, Gender.MALE, true);
    }

    static void decide(final TestProject project, final String segmentId, final String target) {
        update(
                project,
                segmentId,
                record -> record.withStatus(SegmentStatus.ACCEPTED).withMachineTarget(target, target));
    }

    static void decideWith(
            final TestProject project, final String segmentId, final String target, final QaFinding finding) {
        update(
                project,
                segmentId,
                record -> record.withStatus(SegmentStatus.ACCEPTED)
                        .withMachineTarget(target, target)
                        .withFindings(List.of(finding)));
    }

    static void decideAll(final TestProject project, final int count, final IntFunction<String> target) {
        IntStream.range(0, count).forEach(line -> decide(project, "Book.md:" + line, target.apply(line)));
    }

    static String lines(final int count, final IntFunction<String> line) {
        return IntStream.range(0, count).mapToObj(line).collect(Collectors.joining("\n\n"));
    }

    static void update(final TestProject project, final String segmentId, final UnaryOperator<SegmentRecord> change) {
        ok(project.stores().segments().update(project.id(), segmentId, change));
    }

    static void recordUnknownGender(
            final TestProject project, final String segmentId, final List<GlossaryEntry> glossary) {
        DeferralRegister.unknownGender(project.id(), segment(project, segmentId), glossary)
                .forEach(deferral -> ok(project.deferrals().add(deferral)));
    }

    static List<SegmentRecord> decided(final TestProject project) {
        return ok(project.stores().segments().all(project.id())).stream()
                .filter(record -> record.status() != SegmentStatus.PENDING)
                .toList();
    }

    static List<Deferral> openDeferrals(final TestProject project) {
        return ok(project.deferrals().open(project.id()));
    }

    static Segment segment(final TestProject project, final String segmentId) {
        return Objects.requireNonNull(project.stores().openProjects().get(project.id()), "open book").units().stream()
                .flatMap(unit -> unit.segments().stream())
                .filter(candidate -> candidate.id().equals(segmentId))
                .findFirst()
                .orElseThrow();
    }

    static <T> T ok(final Result<T> result) {
        return Objects.requireNonNull(result.data(), () -> "expected ok, got " + result.error());
    }
}
