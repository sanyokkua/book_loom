package ua.bookloom.pipeline;

import java.time.Clock;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.pipeline.project.SegmentLocators;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.StepAnnouncer;
import ua.bookloom.pipeline.run.JobModelCalls;
import ua.bookloom.pipeline.run.RunWindow;

/**
 * Builds a run's one call seam: every model call of the run goes through a cancellable model that Pause and Stop can
 * abort in flight, announces itself and its steps, and is named by the segments it carries.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
final class RunModelCalls {

    static ModelCalls of(
            final ChatModel model,
            final RunWiring.Live live,
            final Clock clock,
            final Document document,
            final RunWindow runWindow,
            final CallFrame frame) {
        log.debug(
                "Run model calls target={} units={}",
                frame.targetLanguage(),
                document.units().size());
        return new JobModelCalls(
                        onSent -> new CancellableChatModel(model, live.control(), onSent, live.watchdog()),
                        live.emit(),
                        clock,
                        frame.targetLanguage(),
                        runWindow::current,
                        SegmentLocators.of(document))
                .withSteps(StepAnnouncer.over(live.emit()))
                .inRun();
    }
}
