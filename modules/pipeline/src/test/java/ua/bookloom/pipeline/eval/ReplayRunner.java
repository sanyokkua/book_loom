package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.eval.LogReplayCorpus.Outcome;
import ua.bookloom.pipeline.eval.ReplayConfig.Mode;
import ua.bookloom.pipeline.eval.ReplayScoring.Scored;
import ua.bookloom.pipeline.run.PromptRequests.PreparedBatch;

/**
 * Sends the calls of a trace log to the model under test again and scores the new replies. {@code raw} sends every
 * logged request body as it went out, so a model, quantisation or server setting is compared on the very prompts of a
 * real run; {@code fast} sends the stratified draft selection of {@link ReplaySelection} rebuilt by the current
 * prompts, so a prompt change is compared on the very paragraphs of a real run. A row carries the call's number, kind,
 * shape and how its segments ended in the log, then the verdict of the new reply beside that of the logged one.
 */
@Slf4j
final class ReplayRunner {

    private final StageProbe probe;

    ReplayRunner(final ChatModel model) {
        this.probe = new StageProbe(Objects.requireNonNull(model, "model"));
    }

    List<StageRow> run(final LogReplayCorpus corpus, final ReplayConfig config) {
        Objects.requireNonNull(corpus, "corpus");
        Objects.requireNonNull(config, "config");
        final List<LoggedCall> chosen =
                config.mode() == Mode.RAW ? corpus.calls() : ReplaySelection.fast(corpus, config.target());
        final List<LoggedCall> calls =
                config.limit() > 0 && chosen.size() > config.limit() ? chosen.subList(0, config.limit()) : chosen;
        log.info(
                "Replay mode={} calls={} of {} logged",
                config.mode().word(),
                calls.size(),
                corpus.calls().size());
        return calls.stream().map(call -> row(corpus, call, config.mode())).toList();
    }

    private StageRow row(final LogReplayCorpus corpus, final LoggedCall call, final Mode mode) {
        final String id = String.format("%s-%03d-%s", mode.word(), call.order(), call.kind());
        final int mark = probe.mark();
        final Optional<Result<ChatResponse>> sent = send(call, mode);
        final StageProbe.Counts counts = probe.since(mark);
        if (sent.isEmpty()) {
            return new StageRow(
                    id,
                    false,
                    "no batch fits the window",
                    0,
                    0,
                    0,
                    call.sourceTexts().size());
        }
        if (sent.get().isErr()) {
            return StageRow.failed(id, sent.get().error().code(), counts);
        }
        final Scored now = ReplayScoring.score(call, sent.get().data().content());
        return StageRow.of(id, now.passed(), detail(corpus, call, now), counts, now.refused());
    }

    private Optional<Result<ChatResponse>> send(final LoggedCall call, final Mode mode) {
        if (mode == Mode.RAW) {
            return Optional.of(probe.chat(call.chatRequest()));
        }
        final EvalProject project = ReplayScoring.project(call);
        final ChatRequest request;
        if (call.isBatch()) {
            final Optional<PreparedBatch> prepared = project.batch();
            if (prepared.isEmpty()) {
                return Optional.empty();
            }
            request = project.batchRequest(prepared.get());
        } else {
            request = project.draftRequest(0);
        }
        return Optional.of(probe.calls(call.targetLanguage()).callAbout(CallKind.DRAFT, call.segmentIds(), request));
    }

    private static String detail(final LogReplayCorpus corpus, final LoggedCall call, final Scored now) {
        final Outcome ended = corpus.outcomeOf(call);
        final String logged = call.reply()
                .map(reply -> ReplayScoring.score(call, reply).passed() ? "ok" : "refused")
                .orElse("none");
        return String.format(
                "%s; segments %s; logged reply %s",
                now.detail(), ended.name().toLowerCase(java.util.Locale.ROOT), logged);
    }
}
