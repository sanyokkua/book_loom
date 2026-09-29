package ua.bookloom.pipeline.revision;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.glossary.GlossaryIds;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.review.ReviewFixtures;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/**
 * A two-chapter EPUB, English to Ukrainian, opened over the in-memory stores through the review desk's fixture, with a
 * scripted model and the steps that leave deferrals behind as the application does: a glossary change recorded through
 * {@link DeferralRegister#termChanged} and a character of unknown gender through {@link DeferralRegister#unknownGender}.
 */
final class RevisionBook {

    static final String HALE_LEFT = "ch01.xhtml:3";
    static final String HALE_CAME = "ch01.xhtml:5";
    static final String SAM_MET_HALE = "ch01.xhtml:7";
    static final String SAM_DOOR = "ch01.xhtml:9";
    static final String SAM_LEFT = "ch02.xhtml:6";
    static final String DOOR_MASKED = "Сем відчинив ⟦g0⟧старі⟦g1⟧ двері.";
    static final String DOOR_PLAIN = "Сем відчинив <em>старі</em> двері.";
    static final String DOOR_REVISED = "Сем відчинила ⟦g0⟧старі⟦g1⟧ двері.";
    static final String DOOR_REVISED_PLAIN = "Сем відчинила <em>старі</em> двері.";

    private final Desk desk;
    private final ScriptedChatModel model = new ScriptedChatModel();

    private RevisionBook(final Desk desk) {
        this.desk = desk;
    }

    static RevisionBook open(final Path directory) {
        return new RevisionBook(ReviewFixtures.epubChapters(directory, List.of(chapter(1, 10), chapter(2, 7))));
    }

    Desk desk() {
        return desk;
    }

    ScriptedChatModel model() {
        return model;
    }

    /** Runs the pass over the book, with the scripted model or, when {@code withModel} is false, with none. */
    Result<ConsistencyReport> run(final boolean withModel) {
        final ModelCalls calls = (kind, segmentId, request) -> model.chat(request);
        return runWith(withModel ? calls : null);
    }

    /** Runs the pass with every revision call sent through {@code calls}, or with no model when it is null. */
    Result<ConsistencyReport> runWith(@Nullable final ModelCalls calls) {
        final ConsistencyPass pass = new ConsistencyPass(
                desk.documents(),
                desk.openProjects(),
                desk.projects(),
                desk.segments(),
                desk.deferrals(),
                desk.glossary(),
                new PromptTemplates(),
                new ObjectMapper());
        return pass.run(desk.projectId(), calls);
    }

    GlossaryEntry character(
            final String term, @Nullable final String target, final Gender gender, final boolean locked) {
        return new GlossaryEntry(
                GlossaryIds.of(desk.projectId(), term),
                desk.projectId(),
                term,
                target,
                TermType.CHARACTER,
                gender,
                locked);
    }

    GlossaryEntry add(final GlossaryEntry entry) {
        return ok(desk.glossary().add(entry));
    }

    void glossaryUpdate(final GlossaryEntry entry) {
        ok(desk.glossary().update(entry));
    }

    /** Changes an entry as the glossary service does: stores it, then records the TERM deferrals it leaves. */
    void change(final GlossaryEntry before, final GlossaryEntry after) {
        glossaryUpdate(after);
        DeferralRegister.termChanged(before, after, ok(desk.segments().all(desk.projectId())))
                .forEach(this::recordDeferral);
    }

    void recordUnknownGender(final String segmentId) {
        DeferralRegister.unknownGender(
                        desk.projectId(), segment(segmentId), ok(desk.glossary().all(desk.projectId())))
                .forEach(this::recordDeferral);
    }

    void recordDeferral(final Deferral deferral) {
        ok(desk.deferrals().add(deferral));
    }

    void decide(final String segmentId, final String plain, final String masked) {
        ReviewFixtures.update(
                desk,
                segmentId,
                record -> record.withStatus(SegmentStatus.ACCEPTED).withMachineTarget(plain, masked));
    }

    /** Stores the segment as the person's edit, as a saved edit leaves it: REVISED with the edit in both forms. */
    void edit(final String segmentId, final String plain, final String masked) {
        ReviewFixtures.update(
                desk,
                segmentId,
                record -> record.withStatus(SegmentStatus.REVISED)
                        .withMachineTarget(plain, masked)
                        .withUserTarget(plain, masked));
    }

    SegmentRecord stored(final String segmentId) {
        return ReviewFixtures.stored(desk, segmentId);
    }

    List<Deferral> openDeferrals() {
        return ok(desk.deferrals().open(desk.projectId()));
    }

    String userMessage(final int request) {
        return model.requests().get(request).messages().stream()
                .filter(message -> message.role() == ChatRole.USER)
                .map(ChatMessage::content)
                .findFirst()
                .orElseThrow();
    }

    static Result<ChatResponse> reply(final String target) {
        return Result.ok(new ChatResponse("{\"target\":\"" + target + "\"}", FinishReason.STOP));
    }

    static <T> T ok(final Result<T> result) {
        return Objects.requireNonNull(result.data(), () -> "expected ok, got " + result.error());
    }

    private Segment segment(final String segmentId) {
        return Objects.requireNonNull(desk.openProjects().get(desk.projectId()), "open book").units().stream()
                .flatMap(unit -> unit.segments().stream())
                .filter(candidate -> candidate.id().equals(segmentId))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> chapter(final int number, final int paragraphs) {
        return IntStream.range(0, paragraphs)
                .mapToObj(index -> paragraph(number, index))
                .toList();
    }

    private static String paragraph(final int chapter, final int index) {
        return switch (chapter + ":" + index) {
            case "1:3" -> "Hale went away.";
            case "1:5" -> "Hale came in.";
            case "1:7" -> "Sam met Hale.";
            case "1:9" -> "Sam opened the <em>old</em> door.";
            case "2:6" -> "Sam went away.";
            default -> "Chapter " + chapter + " line " + index + ".";
        };
    }
}
