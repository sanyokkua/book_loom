package ua.bookloom.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.BookRelocation;
import ua.bookloom.api.pipeline.ProjectChange;
import ua.bookloom.api.project.BookBrief;

/**
 * What {@link ScriptedProjectService} answers to rename, translate-into, second-translation and relocate calls, and a
 * record of each; unscripted answers are an {@code internal} error.
 */
public final class ScriptedProjectEdits {

    /** One {@link ScriptedProjectService#rename} call. */
    public record RenameCall(String projectId, @Nullable String name) {}

    /** One {@link ScriptedProjectService#translateInto} call. */
    public record TranslateCall(
            String projectId, String target, @Nullable String name) {}

    /** One {@link ScriptedProjectService#makeSecondTranslation} call. */
    public record SecondCall(String projectId, BookBrief brief, String name) {}

    /** One {@link ScriptedProjectService#relocate} call. */
    public record RelocateCall(String projectId, Path chosen) {}

    private final List<RenameCall> renames = new CopyOnWriteArrayList<>();
    private final List<TranslateCall> translations = new CopyOnWriteArrayList<>();
    private final List<SecondCall> seconds = new CopyOnWriteArrayList<>();
    private final List<RelocateCall> relocations = new CopyOnWriteArrayList<>();
    private volatile Result<ProjectChange> renameAnswer = ScriptedProjects.notScripted();
    private volatile Result<ProjectChange> translateAnswer = ScriptedProjects.notScripted();
    private volatile Result<ProjectChange> secondAnswer = ScriptedProjects.notScripted();
    private volatile Result<BookRelocation> relocateAnswer = ScriptedProjects.notScripted();

    Result<ProjectChange> rename(final String projectId, final @Nullable String name) {
        renames.add(new RenameCall(Objects.requireNonNull(projectId, "projectId"), name));
        return renameAnswer;
    }

    Result<ProjectChange> translateInto(final String projectId, final String target, final @Nullable String name) {
        translations.add(new TranslateCall(
                Objects.requireNonNull(projectId, "projectId"), Objects.requireNonNull(target, "target"), name));
        return translateAnswer;
    }

    Result<ProjectChange> makeSecondTranslation(final String projectId, final BookBrief brief, final String name) {
        seconds.add(new SecondCall(
                Objects.requireNonNull(projectId, "projectId"),
                Objects.requireNonNull(brief, "brief"),
                Objects.requireNonNull(name, "name")));
        return secondAnswer;
    }

    Result<BookRelocation> relocate(final String projectId, final Path chosen) {
        relocations.add(new RelocateCall(
                Objects.requireNonNull(projectId, "projectId"), Objects.requireNonNull(chosen, "chosen")));
        return relocateAnswer;
    }

    /** From now on answers every rename with {@code answer}. */
    public void onRename(final Result<ProjectChange> answer) {
        renameAnswer = Objects.requireNonNull(answer, "answer");
    }

    /** From now on answers every translate-into with {@code answer}. */
    public void onTranslateInto(final Result<ProjectChange> answer) {
        translateAnswer = Objects.requireNonNull(answer, "answer");
    }

    /** From now on answers every second translation with {@code answer}. */
    public void onMakeSecondTranslation(final Result<ProjectChange> answer) {
        secondAnswer = Objects.requireNonNull(answer, "answer");
    }

    /** From now on answers every relocate with {@code answer}. */
    public void onRelocate(final Result<BookRelocation> answer) {
        relocateAnswer = Objects.requireNonNull(answer, "answer");
    }

    /** Every rename asked, in call order. */
    public List<RenameCall> renames() {
        return List.copyOf(renames);
    }

    /** Every translate-into asked, in call order. */
    public List<TranslateCall> translations() {
        return List.copyOf(translations);
    }

    /** Every second translation asked, in call order. */
    public List<SecondCall> secondTranslations() {
        return List.copyOf(seconds);
    }

    /** Every relocate asked, in call order. */
    public List<RelocateCall> relocations() {
        return List.copyOf(relocations);
    }
}
