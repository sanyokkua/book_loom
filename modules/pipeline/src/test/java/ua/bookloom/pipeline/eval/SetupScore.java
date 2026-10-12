package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.pipeline.eval.StageCases.SetupCase;

/**
 * The setup suite's per-field score against the cases' gold (15i.E3): how many cases got the genre (by the gold genre
 * class), the register, the narrator and the narrator's gender right, and how many knowledge answers were right, split
 * into the invented and no-title books (the answer must be {@code unknown}) and the classics (it must be
 * {@code known}). A knowledge answer that was not asked (null, until 15i.B2) is not counted, so that field reads
 * {@code n/a}. A case whose brief failed counts as wrong in every brief field.
 *
 * @param fields the scored fields, in {@link #NAMES} order
 */
record SetupScore(List<Field> fields) {

    /** The fields, in report order. */
    static final List<String> NAMES =
            List.of("genre", "register", "narrator", "narratorGender", "knowledge.invented", "knowledge.classic");

    /** Copies the fields. */
    SetupScore {
        fields = List.copyOf(fields);
    }

    /**
     * One scored field.
     *
     * @param name one of {@link #NAMES}
     * @param right the cases that matched the gold
     * @param total the cases scored
     */
    record Field(String name, int right, int total) {

        /** The share right; 0.0 when nothing was scored. */
        double share() {
            return total == 0 ? 0.0 : (double) right / total;
        }
    }

    /**
     * What the model answered for one case.
     *
     * @param setupCase the case, with its gold
     * @param brief the brief suggestion, or null when the step failed
     * @param knowledge {@code known} or {@code unknown}, or null when the knowledge question was not asked
     */
    record Observed(
            SetupCase setupCase,
            @Nullable BriefSuggestion brief,
            @Nullable String knowledge) {

        /** Rejects a missing case. */
        Observed {
            Objects.requireNonNull(setupCase, "setupCase");
        }
    }

    /** Scores the observed answers against each case's gold. */
    static SetupScore of(final List<Observed> observed) {
        Objects.requireNonNull(observed, "observed");
        final List<Field> fields = List.of(
                briefField(
                        "genre", observed, (gold, brief) -> JudgementGold.isOfClass(gold.genreClass(), brief.genre())),
                briefField("register", observed, (gold, brief) -> brief.register() == gold.register()),
                briefField("narrator", observed, (gold, brief) -> brief.narrator() == gold.narrator()),
                briefField(
                        "narratorGender", observed, (gold, brief) -> brief.narratorGender() == gold.narratorGender()),
                knowledgeField("knowledge.invented", observed, false),
                knowledgeField("knowledge.classic", observed, true));
        return new SetupScore(fields);
    }

    private static Field briefField(
            final String name, final List<Observed> observed, final BiPredicate<SetupCase, BriefSuggestion> matches) {
        final int right = (int) observed.stream()
                .filter(one -> one.brief() != null && matches.test(one.setupCase(), one.brief()))
                .count();
        return new Field(name, right, observed.size());
    }

    private static Field knowledgeField(final String name, final List<Observed> observed, final boolean classic) {
        final List<Observed> asked = observed.stream()
                .filter(one -> one.setupCase().classic() == classic && one.knowledge() != null)
                .toList();
        final int right = (int) asked.stream()
                .filter(one -> one.setupCase().expectKnowledge().equals(one.knowledge()))
                .count();
        return new Field(name, right, asked.size());
    }

    /** One field by name. */
    Field field(final String name) {
        return fields.stream()
                .filter(field -> field.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no field " + name));
    }

    /** The score as one line, {@code name right/total} per field and {@code name n/a} when nothing was scored. */
    String line() {
        return fields.stream()
                .map(field -> field.name() + (field.total() == 0 ? " n/a" : " " + field.right() + "/" + field.total()))
                .collect(Collectors.joining(" "));
    }

    /** The score as a JSON object, {@code name: share} per field, null where nothing was scored. */
    String json() {
        return fields.stream()
                .map(field -> "\"" + field.name() + "\":"
                        + (field.total() == 0 ? "null" : String.format(Locale.ROOT, "%.3f", field.share())))
                .collect(Collectors.joining(",", "{", "}"));
    }
}
