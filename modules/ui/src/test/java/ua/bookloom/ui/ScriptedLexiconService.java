package ua.bookloom.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.LexiconService;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.TermType;

/**
 * A hand-written {@link LexiconService} over a list the test fills: every call is recorded, an edit, a promotion and a
 * removal change the list as the real service would, and a scan adds what the test said it would find. A call needs no
 * scripting, so a screen that merely shows the card is answered an empty list.
 */
public final class ScriptedLexiconService implements LexiconService {

    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final List<LexiconEntry> held = new CopyOnWriteArrayList<>();
    private final List<LexiconEntry> scanFinds = new CopyOnWriteArrayList<>();
    private final List<LexiconEntry> suggestions = new CopyOnWriteArrayList<>();
    private final List<LexiconEntry> modelFinds = new CopyOnWriteArrayList<>();
    private final List<String> reviewDrops = new CopyOnWriteArrayList<>();
    private volatile @Nullable AppError modelScanFailure;

    /** Puts entries in the list the service holds. */
    public void holds(final LexiconEntry... entries) {
        held.addAll(List.of(entries));
    }

    /** Names what the next scan adds. */
    public void willFind(final LexiconEntry... entries) {
        scanFinds.addAll(List.of(entries));
    }

    /** Names the entries, with their suggested renderings, the next suggestion answers with. */
    public void willSuggest(final LexiconEntry... entries) {
        suggestions.addAll(List.of(entries));
    }

    /** Names what the next model scan adds. */
    public void modelWillFind(final LexiconEntry... entries) {
        modelFinds.addAll(List.of(entries));
    }

    /** Makes the next model scan fail with this error after the deterministic scan has already stored its finds. */
    public void modelScanWillFail(final AppError failure) {
        modelScanFailure = failure;
    }

    /** Names the terms the next model review drops. */
    public void reviewWillDrop(final String... terms) {
        reviewDrops.addAll(List.of(terms));
    }

    /** Every call as {@code method(arguments)}, in order. */
    public List<String> calls() {
        return List.copyOf(calls);
    }

    /** The entries the service holds now. */
    public List<LexiconEntry> now() {
        return List.copyOf(held);
    }

    @Override
    public Result<List<LexiconEntry>> entries(final String projectId) {
        calls.add("entries(" + projectId + ")");
        return Result.ok(List.copyOf(held));
    }

    @Override
    public Result<EntryChanges<LexiconEntry>> scan(final String projectId) {
        calls.add("scan(" + projectId + ")");
        final List<LexiconEntry> before = List.copyOf(held);
        held.addAll(scanFinds);
        scanFinds.clear();
        return changedFrom(before);
    }

    @Override
    public Result<EntryChanges<LexiconEntry>> scanWithModel(
            final String projectId, final ChatModel model, final Consumer<JobEvent> progress) {
        calls.add("scanWithModel(" + projectId + ")");
        final AppError failure = modelScanFailure;
        if (failure != null) {
            modelScanFailure = null;
            return Result.err(failure);
        }
        final List<LexiconEntry> before = List.copyOf(held);
        held.addAll(modelFinds);
        modelFinds.clear();
        return changedFrom(before);
    }

    @Override
    public Result<EntryChanges<LexiconEntry>> review(
            final String projectId, final ChatModel model, final Consumer<JobEvent> progress) {
        calls.add("review(" + projectId + ")");
        final List<LexiconEntry> before = List.copyOf(held);
        held.removeIf(entry -> reviewDrops.contains(entry.term()));
        reviewDrops.clear();
        return changedFrom(before);
    }

    @Override
    public Result<EntryChanges<LexiconEntry>> suggest(
            final String projectId, final ChatModel model, final Consumer<JobEvent> progress) {
        calls.add("suggest(" + projectId + ")");
        final List<LexiconEntry> before = List.copyOf(held);
        held.clear();
        held.addAll(suggestions);
        return changedFrom(before);
    }

    @Override
    public Result<LexiconEntry> restore(final LexiconEntry entry) {
        calls.add("restore(" + entry.term() + ")");
        held.removeIf(now -> LexiconEntry.keyOf(now.term()).equals(LexiconEntry.keyOf(entry.term())));
        held.add(entry);
        return Result.ok(entry);
    }

    private Result<EntryChanges<LexiconEntry>> changedFrom(final List<LexiconEntry> before) {
        return Result.ok(EntryChanges.between(
                before, List.copyOf(held), entry -> LexiconEntry.keyOf(entry.term()), entry -> null));
    }

    @Override
    public Result<LexiconEntry> add(final String projectId, final String term) {
        calls.add("add(" + term + ")");
        final LexiconEntry entry = LexiconEntry.of(projectId, term);
        held.add(entry);
        return Result.ok(entry);
    }

    @Override
    public Result<LexiconEntry> edit(final String projectId, final String term, @Nullable final String rendering) {
        calls.add("edit(" + term + ", " + rendering + ")");
        final LexiconEntry before = find(term);
        final LexiconEntry after = before.withChosen(rendering);
        held.set(held.indexOf(before), after);
        return Result.ok(after);
    }

    @Override
    public Result<GlossaryEntry> promote(final String projectId, final String term) {
        calls.add("promote(" + term + ")");
        final LexiconEntry entry = find(term);
        held.remove(entry);
        return Result.ok(new GlossaryEntry(
                projectId + ":" + term,
                projectId,
                entry.term(),
                entry.established().orElse(null),
                TermType.TERM,
                Gender.UNKNOWN,
                false));
    }

    @Override
    public Result<Boolean> remove(final String projectId, final String term) {
        calls.add("remove(" + term + ")");
        return Result.ok(held.remove(find(term)));
    }

    private LexiconEntry find(final String term) {
        final String key = LexiconEntry.keyOf(term);
        return Objects.requireNonNull(
                new ArrayList<>(held)
                        .stream()
                                .filter(entry ->
                                        LexiconEntry.keyOf(entry.term()).equals(key))
                                .findFirst()
                                .orElse(null),
                "no entry for " + term);
    }
}
