package ua.bookloom.pipeline.heal;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.TestBooks;
import ua.bookloom.pipeline.TestDocuments;
import ua.bookloom.pipeline.WhitespaceRestoration;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.judge.JudgeCall;
import ua.bookloom.pipeline.judge.JudgeReplyParser;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * Real collaborators and small builders shared by the {@code QualityLoopTest} theme classes: a real
 * {@link DocumentPort} (real placeholder gate), a real {@link QualityLoop} wired over the real self-heal calls, and
 * a {@link DraftOutcome.Drafted} builder that replays what the draft step answers under design D3 rule 5: a reply
 * restored through the placeholder gate, or one whose markup still fails it and carries the gate's markup finding.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class QualityLoopFixtures {

    /** Restores every candidate unchanged: the masked form is the candidate and so is the restored text. */
    static final GateFunction PASSTHROUGH_GATE =
            (segment, maskedReply) -> new GateResult.Restored(maskedReply, maskedReply);

    /** Refuses every candidate as a broken placeholder rule. */
    static final GateFunction ALWAYS_FAILS_PLACEHOLDER = (segment, maskedReply) -> new GateResult.GateFailed(
            new QaFinding("markup", Severity.HIGH, "bad", "placeholder"),
            AppError.of(ErrorCode.validation, "Placeholder mismatch", "bad"));

    /** Fails every candidate with an error that ends the step rather than wasting the round. */
    static final GateFunction ALWAYS_INTERNAL_ERROR = (segment, maskedReply) ->
            new GateResult.StepError(AppError.of(ErrorCode.internal, "Gate exploded", "boom"));

    static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);

    static QualityLoop loop() {
        final ObjectMapper mapper = new ObjectMapper();
        final PromptTemplates templates = new PromptTemplates();
        final DraftReplyParser draftReplyParser = new DraftReplyParser(mapper);
        return new QualityLoop(
                new JudgeCall(templates, new JudgeReplyParser(mapper)),
                new DirectedFix(templates, draftReplyParser),
                new ReflectImprove(templates, draftReplyParser, mapper),
                new Polish(templates, draftReplyParser));
    }

    static LoopSettings settings(final ReviewMode mode, final QualityDial dial) {
        return new LoopSettings(mode, DialParameters.of(dial), FRAME, NamePolicy.TRANSLITERATE, List.of());
    }

    /** A one-paragraph Markdown segment, parsed by the real document module so its placeholder map is genuine. */
    static Segment markdownSegment(final Path destination, final String markedSource) {
        final DocumentPort documents = TestDocuments.documents();
        final Path path = TestBooks.markdown(destination, markedSource);
        final Document document = Objects.requireNonNull(documents.open(path).data(), "opened document");
        return document.units().getFirst().segments().getFirst();
    }

    /**
     * Builds a {@link DraftOutcome.Drafted} the way the draft step does: restores {@code replyContent}'s trimmed
     * text into the segment's own whitespace, then runs it through the real placeholder gate.
     */
    static DraftOutcome.Drafted drafted(
            final Segment segment, final DocumentPort documents, final String replyContent) {
        final String maskedReply = WhitespaceRestoration.restore(segment.masked(), replyContent.strip());
        return switch (GateFunction.of(documents, BookFormat.MARKDOWN).restore(segment, maskedReply)) {
            case GateResult.Restored restored ->
                new DraftOutcome.Drafted(
                        segment,
                        segment.masked(),
                        List.of(),
                        maskedReply,
                        restored.maskedForm(),
                        restored.restored(),
                        null);
            case GateResult.GateFailed failed ->
                new DraftOutcome.Drafted(
                        segment, segment.masked(), List.of(), maskedReply, null, null, failed.finding());
            case GateResult.StepError stepError ->
                throw new IllegalStateException("fixture segment failed the gate unexpectedly: "
                        + stepError.error().code());
        };
    }

    static DraftOutcome.FlaggedAtOnce flaggedAtOnce(final Segment segment, final AppError error) {
        return new DraftOutcome.FlaggedAtOnce(segment, segment.masked(), List.of(), error);
    }
}
