package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import javafx.stage.Window;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.BookRelocation;
import ua.bookloom.api.pipeline.ProjectChange;
import ua.bookloom.api.pipeline.ProjectOpening;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.Project;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.dialog.ConfirmPrompt;
import ua.bookloom.ui.dialog.FindBookPrompt;
import ua.bookloom.ui.dialog.RenamePrompt;
import ua.bookloom.ui.dialog.SamePairPrompt;
import ua.bookloom.ui.dialog.TranslateIntoPrompt;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.notify.ErrorPresenter;

/**
 * The Projects screen's row actions behind their dialogs: each asks the person, then the project service off the FX
 * thread, and shows what the service answered. The name rules are built from a fresh listing each time, so a name
 * another action took meanwhile is still caught (the service's own answer is the last word).
 */
@Slf4j
@Singleton
public final class ProjectActionsImpl implements ProjectActions {

    private final ProjectService projects;
    private final RenamePrompt renamePrompt;
    private final SamePairPrompt samePairPrompt;
    private final TranslateIntoPrompt translatePrompt;
    private final FindBookPrompt findBookPrompt;
    private final ConfirmPrompt confirm;
    private final FileChooserPort chooser;
    private final Provider<RowOpener> opener;
    private final Provider<ProjectsViewModel> list;
    private final Provider<ImportViewModel> importer;
    private final CurrentProject current;
    private final Messages messages;
    private final LanguageNames languages;
    private final ErrorPresenter errors;
    private final OffThread calls;

    /**
     * Receives the collaborators the injector owns; the opener, the list and the importer are providers because they
     * hold this object themselves.
     *
     * @param projects the service every action goes through
     * @param renamePrompt the Rename card
     * @param samePairPrompt the Same language pair card
     * @param translatePrompt the Translate into another language card
     * @param findBookPrompt the Find the book card
     * @param confirm the question asked before a removal
     * @param chooser the file dialog that finds a moved book
     * @param opener what opens a project after a dialog
     * @param list the Projects list to refresh after a change
     * @param importer the Import screen's state, reset when its project is removed
     * @param current the open project, closed when it is removed
     * @param messages the catalogue the refusals and language names come from
     * @param languages names languages in the interface language
     * @param errors where an unexpected failure is shown
     * @param executor the daemon executor the service runs on, never the FX thread
     */
    @Inject
    public ProjectActionsImpl(
            final ProjectService projects,
            final RenamePrompt renamePrompt,
            final SamePairPrompt samePairPrompt,
            final TranslateIntoPrompt translatePrompt,
            final FindBookPrompt findBookPrompt,
            final ConfirmPrompt confirm,
            final FileChooserPort chooser,
            final Provider<RowOpener> opener,
            final Provider<ProjectsViewModel> list,
            final Provider<ImportViewModel> importer,
            final CurrentProject current,
            final Messages messages,
            final LanguageNames languages,
            final ErrorPresenter errors,
            @BackgroundExecutor final ExecutorService executor) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.renamePrompt = Objects.requireNonNull(renamePrompt, "renamePrompt");
        this.samePairPrompt = Objects.requireNonNull(samePairPrompt, "samePairPrompt");
        this.translatePrompt = Objects.requireNonNull(translatePrompt, "translatePrompt");
        this.findBookPrompt = Objects.requireNonNull(findBookPrompt, "findBookPrompt");
        this.confirm = Objects.requireNonNull(confirm, "confirm");
        this.chooser = Objects.requireNonNull(chooser, "chooser");
        this.opener = Objects.requireNonNull(opener, "opener");
        this.list = Objects.requireNonNull(list, "list");
        this.importer = Objects.requireNonNull(importer, "importer");
        this.current = Objects.requireNonNull(current, "current");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.languages = Objects.requireNonNull(languages, "languages");
        this.errors = Objects.requireNonNull(errors, "errors");
        this.calls = new OffThread(Objects.requireNonNull(executor, "executor"));
    }

    @Override
    public void rename(final ProjectRow row) {
        Objects.requireNonNull(row, "row");
        askRename(row, null, null, () -> {});
    }

    @Override
    public void renameOpen(final String projectId, final Runnable renamed) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(renamed, "renamed");
        calls.call("find the open project", projects::list, result -> {
            final @Nullable List<ProjectSummary> listed = dataOrPresent(result);
            if (listed == null) {
                return;
            }
            listed.stream()
                    .filter(summary -> summary.project().id().equals(projectId))
                    .findFirst()
                    .ifPresentOrElse(
                            summary -> askRename(ProjectRows.rowOf(summary, projectId), null, null, renamed),
                            () -> log.warn("the open project {} is not stored; nothing to rename", projectId));
        });
    }

    private void askRename(
            final ProjectRow row, final @Nullable String error, final @Nullable String typed, final Runnable renamed) {
        final Project own = row.summary().project();
        withBook(own.contentHash(), book -> {
            final List<Project> siblings =
                    book.stream().filter(p -> !p.id().equals(own.id())).toList();
            log.debug("rename dialog for project={} siblings={}", own.id(), siblings.size());
            renamePrompt.ask(
                    row,
                    ProjectNameRules.forProject(siblings, own),
                    error,
                    typed,
                    text -> applyRename(row, text, renamed),
                    () -> remove(row),
                    () -> log.debug("rename of project={} cancelled", own.id()));
        });
    }

    private void applyRename(final ProjectRow row, final @Nullable String text, final Runnable renamed) {
        final String id = row.summary().project().id();
        log.info("project rename requested project={} automatic={}", id, text == null);
        calls.call("rename a project", () -> projects.rename(id, text), result -> {
            final ProjectChange change = dataOrPresent(result);
            switch (change) {
                case null -> log.debug("rename of project={} failed", id);
                case ProjectChange.Saved _ -> {
                    list.get().refresh();
                    renamed.run();
                }
                case ProjectChange.NameInUse _ -> {
                    log.warn("rename refused project={} clash=NameInUse", id);
                    askRename(row, messages.get(MessageKey.DIALOG_PROJECT_NAME_ERROR_USED), text, renamed);
                }
                case ProjectChange.PairInUse _ -> {
                    log.warn("rename refused project={} clash=PairInUse", id);
                    askRename(row, null, null, renamed);
                }
            }
        });
    }

    @Override
    public void remove(final ProjectRow row) {
        Objects.requireNonNull(row, "row");
        final Project project = row.summary().project();
        confirm.ask(
                ConfirmPrompt.Question.REMOVE_PROJECT,
                List.of(project.name()),
                List.of(),
                () -> applyRemove(project.id()));
    }

    private void applyRemove(final String id) {
        log.info("project removal confirmed project={}", id);
        calls.call("remove a project", () -> projects.remove(id), result -> {
            if (dataOrPresent(result) == null) {
                log.warn("project removal failed project={}", id);
                return;
            }
            log.info("project removed project={}", id);
            final @Nullable OpenedBook open = current.book().get();
            if (open != null && open.projectId().equals(id)) {
                current.clear();
                importer.get().showEmpty();
            }
            list.get().refresh();
        });
    }

    @Override
    public void translateInto(final BookGroup group) {
        Objects.requireNonNull(group, "group");
        withBook(
                group.contentHash(),
                book -> translatePrompt.ask(
                        group,
                        ProjectNameRules.forBook(book),
                        (target, name) -> createTranslation(group, target, name),
                        () -> log.debug("translate into another language cancelled book={}", group.contentHash())));
    }

    private void createTranslation(final BookGroup group, final String target, final @Nullable String name) {
        final String sourceId = group.rows().getFirst().summary().project().id();
        log.info("another translation requested book={} target={}", group.contentHash(), target);
        calls.call(
                "translate a book into another language",
                () -> projects.translateInto(sourceId, target, name),
                result -> {
                    final ProjectChange change = dataOrPresent(result);
                    switch (change) {
                        case null -> log.debug("translation of book={} failed", group.contentHash());
                        case ProjectChange.Saved saved -> openNew(group, saved.project());
                        case ProjectChange.PairInUse inUse -> askSamePair(group, target, inUse.existing());
                        case ProjectChange.NameInUse _ -> {
                            log.warn("translation refused book={} clash=NameInUse", group.contentHash());
                            translateInto(group);
                        }
                    }
                });
    }

    private void openNew(final BookGroup group, final Project created) {
        final ProjectRow row = new ProjectRow(
                new ProjectSummary(created, 0, group.totalSegments(), null, null),
                0,
                ProjectStatus.NOT_STARTED,
                false,
                ViewNames.BOOK_BRIEF,
                "");
        log.debug("opening the new project {} at the brief", created.id());
        opener.get().open(row);
    }

    private void askSamePair(final BookGroup group, final String target, final Project existing) {
        withBook(group.contentHash(), book -> {
            final ProjectRow row = group.rows().stream()
                    .filter(r -> r.summary().project().id().equals(existing.id()))
                    .findFirst()
                    .orElseGet(() -> new ProjectRow(
                            new ProjectSummary(existing, 0, group.totalSegments(), null, null),
                            0,
                            ProjectStatus.NOT_STARTED,
                            false,
                            ViewNames.BOOK_BRIEF,
                            ""));
            samePairPrompt.ask(
                    row,
                    languages.nameOf(target, messages.locale()),
                    messages.get(MessageKey.COMMON_CANCEL),
                    ProjectNameRules.forSecond(book, existing, target),
                    () -> {
                        log.debug("Same language pair answered with open project={}", existing.id());
                        opener.get().open(row);
                    },
                    name -> {
                        log.debug("Same language pair answered with a second translation project={}", existing.id());
                        createTranslation(group, target, name);
                    },
                    () -> log.debug("Same language pair answered with cancel"));
        });
    }

    @Override
    public void findBook(final ProjectRow row, final ProjectOpening opening) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(opening, "opening");
        askFindBook(row, opening, null);
    }

    private void askFindBook(final ProjectRow row, final ProjectOpening opening, final @Nullable String error) {
        findBookPrompt.ask(
                row,
                opening,
                error,
                () -> choose(row, opening),
                () -> log.debug(
                        "find the book cancelled project={}",
                        row.summary().project().id()));
    }

    private void choose(final ProjectRow row, final ProjectOpening opening) {
        final String id = row.summary().project().id();
        final var picked = chooser.chooseToOpen(owner(), FileChooserPort.FileKind.BOOK);
        if (picked.isEmpty()) {
            log.debug("no book file chosen for project={}", id);
            askFindBook(row, opening, null);
            return;
        }
        log.info("book file chosen for project={}", id);
        log.trace("chosen book file {}", picked.get());
        calls.call("find a project's book", () -> projects.relocate(id, picked.get()), result -> {
            final BookRelocation answer = dataOrPresent(result);
            switch (answer) {
                case null -> log.debug("finding the book of project={} failed", id);
                case BookRelocation.Relocated _ -> opener.get().open(row);
                case BookRelocation.DifferentBook _ -> {
                    log.warn("book file refused project={}: different book", id);
                    askFindBook(row, opening, messages.get(MessageKey.DIALOG_FIND_BOOK_DIFFERENT));
                }
            }
        });
    }

    private static @Nullable Window owner() {
        final List<Window> showing = Window.getWindows();
        return showing.stream()
                .filter(Window::isFocused)
                .findFirst()
                .orElse(showing.isEmpty() ? null : showing.getFirst());
    }

    private void withBook(final String hash, final Consumer<List<Project>> next) {
        calls.call("list the projects of a book", projects::list, result -> {
            final List<ProjectSummary> listed = dataOrPresent(result);
            if (listed != null) {
                next.accept(listed.stream()
                        .map(ProjectSummary::project)
                        .filter(p -> p.contentHash().equals(hash))
                        .toList());
            }
        });
    }

    private <T> @Nullable T dataOrPresent(final Result<T> result) {
        final AppError failure = result.error();
        if (failure != null) {
            errors.present(failure);
            return null;
        }
        return result.data();
    }
}
