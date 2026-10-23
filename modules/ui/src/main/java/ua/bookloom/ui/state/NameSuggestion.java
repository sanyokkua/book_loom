package ua.bookloom.ui.state;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookProfile;
import ua.bookloom.api.pipeline.FileNameSuggestion;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The model's proposal of the translated book's file name: asks for it and holds the answer in the short progress
 * dialog until the person presses "Use this name", which puts it in the destination as the person's own choice, keeping
 * the folder and the format suffix the destination already has; Cancel keeps the current name. A title edit asks the
 * model nothing: it only says the title changed. FX thread only.
 */
@Slf4j
public final class NameSuggestion {

    private static final String FB2_ZIP = ".fb2.zip";
    private static final LanguageNames LANGUAGES = new LanguageNames();

    private final CurrentProject project;
    private final Messages messages;
    private final @Nullable SetupHelper setup;
    private final ExportViewModel destination;
    private final ReadOnlyBooleanWrapper suggesting = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyStringWrapper notice = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper authorKept = new ReadOnlyBooleanWrapper(false);
    private @Nullable String askedFor;
    private boolean applying;
    // Whether the destination still holds a name this class put there, and not one the person typed or chose.
    private boolean nameIsSuggested;

    NameSuggestion(
            final CurrentProject project,
            final Messages messages,
            final @Nullable SetupHelper setup,
            final ExportViewModel destination) {
        this.project = Objects.requireNonNull(project, "project");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.setup = setup;
        this.destination = Objects.requireNonNull(destination, "destination");
        destination.destination().addListener((observed, was, now) -> {
            if (applying) {
                return;
            }
            nameIsSuggested = false;
            if (authorKept.get()) {
                log.debug("the destination was edited; the note about the author's alphabet is dropped");
                authorKept.set(false);
            }
        });
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

    /**
     * Whether the name last put in the destination kept the author's name in the source alphabet (a Latin name in a
     * Ukrainian title), so the person may want to translate it; false again once the destination is edited.
     */
    public ReadOnlyBooleanProperty authorKept() {
        return authorKept.getReadOnlyProperty();
    }

    /**
     * Says, after the title or author was edited, that a name the model suggested may no longer match; the model is
     * asked again only when the person presses Suggest a name. A name the person chose is left without a word.
     */
    public void afterTitleEdit() {
        log.debug("title edited: the destination holds a suggested name {}", nameIsSuggested);
        if (nameIsSuggested) {
            notice.set(messages.get(MessageKey.EXPORT_SUGGEST_NAME_TITLE_CHANGED));
        }
    }

    /** Asks the model for the translated book's file name and offers it in the dialog. */
    public void ask() {
        final OpenedBook book = project.book().get();
        if (setup == null || book == null || suggesting.get()) {
            return;
        }
        log.debug("a file name suggestion is asked for project {}", book.projectId());
        askedFor = destination.destination().get();
        notice.set("");
        authorKept.set(false);
        suggesting.set(true);
        setup.runForOneStep(
                        ActivityKind.SUGGEST_NAME,
                        (model, progress) -> setup.assistant().suggestFileName(book.projectId(), model, progress),
                        answer -> answered(book.projectId(), answer),
                        messages.get(MessageKey.EXPORT_SUGGEST_NAME_STEP),
                        sentOf(book),
                        this::answerOf)
                .ifPresent(this::refused);
    }

    // What the model was given, in plain words: the title, the language the name is written in and the file format.
    private String sentOf(final OpenedBook book) {
        final BookProfile profile = book.profile();
        final String title = profile == null || profile.title() == null ? "" : profile.title();
        // The open book holds the brief it was opened with; the target the person chose since lives on the project.
        final BookBrief brief = project.brief().get();
        final String target = brief == null ? null : brief.targetLanguage();
        final String format = String.valueOf(book.inspection().format());
        return messages.get(
                MessageKey.EXPORT_SUGGEST_NAME_SENT,
                title,
                target == null ? "" : LANGUAGES.nameOf(target, messages.locale()),
                format);
    }

    private String answerOf(final Result<FileNameSuggestion> answer) {
        final FileNameSuggestion suggestion = answer.data();
        return suggestion == null ? "" : messages.get(MessageKey.EXPORT_SUGGEST_NAME_HOW, suggestion.name());
    }

    private void refused(final SetupHelper.Refusal refusal) {
        log.debug("the file name suggestion was refused: {}", refusal);
        suggesting.set(false);
        notice.set(
                switch (refusal) {
                    case NO_MODEL -> messages.get(MessageKey.EXPORT_SUGGEST_NAME_NO_MODEL);
                    case BUSY ->
                        messages.get(MessageKey.ACTIVITY_BLOCKED, messages.get(ActivityKind.SUGGEST_NAME.label()));
                    case HIDDEN_WORK -> "";
                });
    }

    private Optional<ResultOffer> answered(final String projectId, final Result<FileNameSuggestion> answer) {
        suggesting.set(false);
        final OpenedBook now = project.book().get();
        if (now == null || !now.projectId().equals(projectId)) {
            log.debug("the file name suggestion is dropped: the open book changed");
            return Optional.empty();
        }
        final AppError failure = answer.error();
        if (failure != null && failure.code() == ErrorCode.cancelled) {
            log.info("the file name suggestion was stopped by the person");
            return Optional.empty();
        }
        if (failure != null) {
            log.warn("the file name suggestion failed with {}", failure.code());
            notice.set(failure.message());
            return Optional.empty();
        }
        if (!Objects.equals(askedFor, destination.destination().get())) {
            log.debug("the file name suggestion is dropped: the person changed the destination while it waited");
            return Optional.empty();
        }
        final FileNameSuggestion suggestion = Objects.requireNonNull(answer.data(), "data");
        log.debug("the file name suggestion is offered for the person to use");
        return Optional.of(new ResultOffer(
                MessageKey.EXPORT_SUGGEST_NAME_USE,
                true,
                () -> destination.destinationPath().ifPresent(current -> take(current, suggestion)),
                () -> log.info("the suggested file name was not used: the current name stays")));
    }

    private void take(final java.nio.file.Path current, final FileNameSuggestion suggestion) {
        log.debug(
                "the suggested file name is taken as the destination, author kept in the source alphabet: {}",
                suggestion.authorKeptInSourceScript());
        applying = true;
        try {
            destination.editDestination(current.resolveSibling(
                            suggestion.name() + suffixOf(current.getFileName().toString()))
                    .toString());
        } finally {
            applying = false;
        }
        nameIsSuggested = true;
        notice.set(messages.get(MessageKey.EXPORT_SUGGEST_NAME_DONE));
        authorKept.set(suggestion.authorKeptInSourceScript());
    }

    private static String suffixOf(final String fileName) {
        if (fileName.toLowerCase(Locale.ROOT).endsWith(FB2_ZIP)) {
            return FB2_ZIP;
        }
        final int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot) : "";
    }
}
