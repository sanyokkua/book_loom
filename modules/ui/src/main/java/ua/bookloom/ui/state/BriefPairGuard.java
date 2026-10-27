package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ProjectChange;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Project;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.dialog.SamePairPrompt;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.notify.ErrorPresenter;

/**
 * Keeps the Book Brief from showing a language pair the project service refused: another translation of the book
 * already holds it. The brief shows the pair at once, as every change does, and when the service refuses the save the
 * two language fields go back to the pair last stored (the person's other edits stay), and the Same language pair
 * question is asked. Only "Use it with this name" keeps the new pair, by storing a second translation under a name of
 * the person's own; "Open that project" opens the other one and discards the edit; Keep and Esc store nothing. FX
 * thread only.
 */
@Slf4j
@Singleton
public final class BriefPairGuard implements BriefSaveOutcomes {

    private record Pair(@Nullable String source, @Nullable String target) {}

    private final CurrentProject current;
    private final BriefProject named;
    private final ProjectService projects;
    private final SamePairPrompt prompt;
    private final Provider<RowOpener> opener;
    private final Messages messages;
    private final LanguageNames languages;
    private final ErrorPresenter errors;
    private final OffThread calls;
    private Pair stored = new Pair(null, null);

    /**
     * Follows the open book's stored pair.
     *
     * @param current the open project, whose brief is restored on a refusal
     * @param named the stored name, read again once a pair is stored
     * @param projects the service the second translation is stored through
     * @param prompt the Same language pair question
     * @param opener what "Open that project" does; a provider because it reaches back to the brief
     * @param messages the catalogue the question is worded from
     * @param languages names languages in the interface language
     * @param errors where an unexpected failure is shown
     * @param executor the daemon executor the service runs on, never the FX thread
     */
    @Inject
    public BriefPairGuard(
            final CurrentProject current,
            final BriefProject named,
            final ProjectService projects,
            final SamePairPrompt prompt,
            final Provider<RowOpener> opener,
            final Messages messages,
            final LanguageNames languages,
            final ErrorPresenter errors,
            @BackgroundExecutor final ExecutorService executor) {
        this.current = Objects.requireNonNull(current, "current");
        this.named = Objects.requireNonNull(named, "named");
        this.projects = Objects.requireNonNull(projects, "projects");
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.opener = Objects.requireNonNull(opener, "opener");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.languages = Objects.requireNonNull(languages, "languages");
        this.errors = Objects.requireNonNull(errors, "errors");
        this.calls = new OffThread(Objects.requireNonNull(executor, "executor"));
        current.book().addListener((observed, was, now) -> reset());
        reset();
    }

    // The brief is replaced before the book is, so a listener on the book already sees the stored pair.
    private void reset() {
        final @Nullable BookBrief brief = current.brief().get();
        stored = brief == null ? new Pair(null, null) : pairOf(brief);
        log.debug("the stored pair is {} -> {}", stored.source(), stored.target());
    }

    private static Pair pairOf(final BookBrief brief) {
        return new Pair(brief.sourceLanguage(), brief.targetLanguage());
    }

    private boolean isOpen(final String projectId) {
        final @Nullable OpenedBook book = current.book().get();
        return book != null && book.projectId().equals(projectId);
    }

    @Override
    public void stored(final String projectId, final BookBrief brief) {
        if (!isOpen(projectId)) {
            return;
        }
        final Pair now = pairOf(brief);
        final boolean moved = !now.equals(stored);
        stored = now;
        log.debug("the pair {} -> {} is stored; the name is read again: {}", now.source(), now.target(), moved);
        if (moved) {
            named.refresh();
        }
    }

    @Override
    public UnaryOperator<BookBrief> refused(final String projectId, final BookBrief sent, final ProjectChange refusal) {
        if (!isOpen(projectId) || !(refusal instanceof ProjectChange.PairInUse inUse)) {
            log.debug(
                    "a refused brief save needs no question: {}",
                    refusal.getClass().getSimpleName());
            return BriefSaveOutcomes.super.refused(projectId, sent, refusal);
        }
        final Pair previous = stored;
        final Pair asked = pairOf(sent);
        if (asked.equals(previous)) {
            log.debug("the refused pair is the stored one; nothing to restore");
            return waiting -> waiting;
        }
        if (!pairOf(inUse.existing().brief()).equals(asked)) {
            return acceptedAnyway(inUse.existing(), asked);
        }
        log.info(
                "the pair {} -> {} is used by another translation; the brief goes back to {} -> {}",
                asked.source(),
                asked.target(),
                previous.source(),
                previous.target());
        final UnaryOperator<BookBrief> restore = brief -> brief.withLanguages(previous.source(), previous.target());
        final @Nullable BookBrief now = current.brief().get();
        if (now != null) {
            current.replaceBrief(restore.apply(now));
        }
        ask(projectId, inUse.existing(), asked, previous);
        return restore;
    }

    // A refusal that names a project not holding the pair is no clash; the choice stands as stored.
    private UnaryOperator<BookBrief> acceptedAnyway(final Project existing, final Pair asked) {
        log.warn("project {} was named as holding a pair it does not hold; the choice stands", existing.id());
        stored = asked;
        return waiting -> waiting;
    }

    // The question is built from a fresh listing, so a name taken meanwhile is still caught.
    private void ask(final String projectId, final Project existing, final Pair asked, final Pair previous) {
        calls.call("list the projects of a book", projects::list, result -> {
            final @Nullable List<ProjectSummary> listed = result.data();
            if (listed == null || !isOpen(projectId)) {
                return;
            }
            final List<Project> book = listed.stream()
                    .map(ProjectSummary::project)
                    .filter(project -> project.contentHash().equals(existing.contentHash()))
                    .toList();
            final ProjectRow row = listed.stream()
                    .filter(summary -> summary.project().id().equals(existing.id()))
                    .findFirst()
                    .map(summary -> ProjectRows.rowOf(summary, projectId))
                    .orElseGet(() -> ProjectRows.rowOf(new ProjectSummary(existing, 0, 0, null, null), projectId));
            final boolean targetChanged = !Objects.equals(asked.target(), previous.target());
            prompt.ask(
                    row,
                    nameOf(targetChanged ? asked.target() : asked.source()),
                    keepName(targetChanged ? previous.target() : previous.source(), targetChanged),
                    ProjectNameRules.forSecond(book, existing, Objects.requireNonNullElse(asked.target(), "")),
                    () -> opener.get().open(row),
                    name -> makeSecond(projectId, existing, asked, previous, name),
                    () -> log.debug("Same language pair kept the previous pair"));
        });
    }

    private String nameOf(final @Nullable String tag) {
        return tag == null ? messages.get(MessageKey.BRIEF_SAME_PAIR_AUTO) : languages.nameOf(tag, messages.locale());
    }

    // An empty target before has no name to keep, so the button says it keeps the choice as it is.
    private String keepName(final @Nullable String previousTag, final boolean isTarget) {
        if (previousTag == null && isTarget) {
            return messages.get(MessageKey.BRIEF_SAME_PAIR_KEEP_PREVIOUS);
        }
        return messages.get(MessageKey.BRIEF_SAME_PAIR_KEEP, nameOf(previousTag));
    }

    private void makeSecond(
            final String projectId, final Project existing, final Pair asked, final Pair previous, final String name) {
        final @Nullable BookBrief now = current.brief().get();
        if (now == null || !isOpen(projectId)) {
            return;
        }
        final BookBrief second = now.withLanguages(asked.source(), asked.target());
        log.info("a second translation is stored for project {} under a name of the person's own", projectId);
        calls.call(
                "make a second translation", () -> projects.makeSecondTranslation(projectId, second, name), result -> {
                    final @Nullable ProjectChange change = result.data();
                    if (result.error() != null) {
                        errors.present(Objects.requireNonNull(result.error()));
                    } else if (change instanceof ProjectChange.Saved) {
                        keep(projectId, asked);
                    } else if (change instanceof ProjectChange.NameInUse) {
                        log.warn("the name for the second translation is used; asking again");
                        ask(projectId, existing, asked, previous);
                    } else {
                        log.warn(
                                "the second translation was refused: {}",
                                change == null ? "nothing" : change.getClass().getSimpleName());
                    }
                });
    }

    // The new pair is now the stored one, and the person's other edits made meanwhile stay.
    private void keep(final String projectId, final Pair asked) {
        final @Nullable BookBrief now = current.brief().get();
        if (now != null && isOpen(projectId)) {
            current.replaceBrief(now.withLanguages(asked.source(), asked.target()));
        }
        stored = asked;
        named.refresh();
    }
}
