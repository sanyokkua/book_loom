package ua.bookloom.ui.state;

import java.util.Objects;
import javafx.beans.property.ObjectProperty;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.BatchStarted;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * What a model action of Names &amp; style reports while it waits on the model: the live call feed, the count of
 * requests sent (a retry is the same request) and the line that words them, or the batch of suggestions under way.
 * Split from {@link GlossaryModelRuns}, which decides when an action starts, stops and ends. FX thread only.
 */
@Slf4j
final class RunProgress {

    private final Messages messages;
    private final ObjectProperty<@Nullable GlossaryNotice> notice;
    private @Nullable CallFeed feed;
    private int requests;
    private @Nullable BatchStarted suggesting;

    RunProgress(final Messages messages, final ObjectProperty<@Nullable GlossaryNotice> notice) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.notice = Objects.requireNonNull(notice, "notice");
    }

    /** Starts counting from nothing for a new action, whose calls go to {@code newFeed}. */
    void begin(final @Nullable CallFeed newFeed) {
        requests = 0;
        suggesting = null;
        feed = newFeed;
    }

    int requests() {
        return requests;
    }

    void accept(final JobEvent event, final ActivityTracker.@Nullable Handle handle) {
        final CallFeed shown = feed;
        if (shown != null) {
            shown.accept(event);
        }
        if (event instanceof BatchStarted batch) {
            suggesting = batch;
            log.debug("suggestion batch {} of {} is out", batch.batch(), batch.batches());
            line(suggestingLine(batch));
            return;
        }
        if (event instanceof ModelCallStarted started) {
            requestOut(started, handle);
        }
    }

    private void requestOut(final ModelCallStarted started, final ActivityTracker.@Nullable Handle handle) {
        if (started.attempt() == 1) {
            requests++;
            if (handle != null) {
                handle.requests(requests);
            }
        }
        log.debug("model request {} attempt {} of {} is out", requests, started.attempt(), started.maxAttempts());
        final BatchStarted batch = suggesting;
        line(
                started.kind() == CallKind.SUGGEST_TARGETS && batch != null
                        ? suggestingLine(batch)
                        : messages.get(
                                MessageKey.NAMES_STYLE_MODEL_PROGRESS,
                                requests,
                                started.attempt(),
                                started.maxAttempts()));
    }

    private String suggestingLine(final BatchStarted batch) {
        return messages.get(MessageKey.NAMES_STYLE_SUGGESTING, batch.batch(), batch.batches());
    }

    private void line(final String text) {
        notice.set(new GlossaryNotice(GlossaryNotice.Level.INFO, text));
    }
}
