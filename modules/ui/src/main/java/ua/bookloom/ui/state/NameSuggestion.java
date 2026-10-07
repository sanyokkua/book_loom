package ua.bookloom.ui.state;

import java.util.Locale;
import java.util.Objects;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.FileNameSuggestion;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The model's proposal of the translated book's file name: asks for it and, when it arrives, puts it in the destination
 * as the person's own choice, keeping the folder and the format suffix the destination already has. FX thread only.
 */
@Slf4j
public final class NameSuggestion {

    private static final String FB2_ZIP = ".fb2.zip";

    private final CurrentProject project;
    private final Messages messages;
    private final @Nullable SetupHelper setup;
    private final ExportViewModel destination;
    private final ReadOnlyBooleanWrapper suggesting = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyStringWrapper notice = new ReadOnlyStringWrapper("");
    private @Nullable String askedFor;

    NameSuggestion(
            final CurrentProject project,
            final Messages messages,
            final @Nullable SetupHelper setup,
            final ExportViewModel destination) {
        this.project = Objects.requireNonNull(project, "project");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.setup = setup;
        this.destination = Objects.requireNonNull(destination, "destination");
    }

    /** Whether a suggested file name is offered at all: a setup helper exists. */
    public boolean isOffered() {
        return setup != null;
    }

    /** Whether a suggestion is waiting for the model. */
    public ReadOnlyBooleanProperty suggesting() {
        return suggesting.getReadOnlyProperty();
    }

    /** What happened to the last suggestion, in words for the person; empty while nothing is to be said. */
    public ReadOnlyStringProperty notice() {
        return notice.getReadOnlyProperty();
    }

    /** Asks the model for the translated book's file name and puts it in the destination. */
    public void ask() {
        final OpenedBook book = project.book().get();
        if (setup == null || book == null || suggesting.get()) {
            return;
        }
        log.debug("a file name suggestion is asked for project {}", book.projectId());
        askedFor = destination.destination().get();
        notice.set("");
        suggesting.set(true);
        setup.run(
                        ActivityKind.SUGGEST_NAME,
                        model -> setup.assistant().suggestFileName(book.projectId(), model),
                        answer -> answered(book.projectId(), answer))
                .ifPresent(this::refused);
    }

    private void refused(final SetupHelper.Refusal refusal) {
        suggesting.set(false);
        notice.set(
                refusal == SetupHelper.Refusal.NO_MODEL
                        ? messages.get(MessageKey.EXPORT_SUGGEST_NAME_NO_MODEL)
                        : messages.get(MessageKey.ACTIVITY_BLOCKED, messages.get(ActivityKind.SUGGEST_NAME.label())));
    }

    private void answered(final String projectId, final Result<FileNameSuggestion> answer) {
        suggesting.set(false);
        final OpenedBook now = project.book().get();
        if (now == null || !now.projectId().equals(projectId)) {
            log.debug("the file name suggestion is dropped: the open book changed");
            return;
        }
        final AppError failure = answer.error();
        if (failure != null && failure.code() == ErrorCode.cancelled) {
            log.info("the file name suggestion was stopped by the person");
            return;
        }
        if (failure != null) {
            log.warn("the file name suggestion failed with {}", failure.code());
            notice.set(failure.message());
            return;
        }
        if (!Objects.equals(askedFor, destination.destination().get())) {
            log.debug("the file name suggestion is dropped: the person changed the destination while it waited");
            return;
        }
        final String stem = Objects.requireNonNull(answer.data(), "data").name();
        destination.destinationPath().ifPresent(current -> {
            log.debug("the suggested file name is taken as the destination");
            destination.editDestination(
                    current.resolveSibling(stem + suffixOf(current.getFileName().toString()))
                            .toString());
            notice.set(messages.get(MessageKey.EXPORT_SUGGEST_NAME_DONE));
        });
    }

    private static String suffixOf(final String fileName) {
        if (fileName.toLowerCase(Locale.ROOT).endsWith(FB2_ZIP)) {
            return FB2_ZIP;
        }
        final int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot) : "";
    }
}
