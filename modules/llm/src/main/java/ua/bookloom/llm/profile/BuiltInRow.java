package ua.bookloom.llm.profile;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ReasoningControl;

/**
 * One built-in row: the names it speaks for, what it says about them, and why, in words that stand without the
 * research the rows were taken from.
 *
 * @param pattern the parsed names the row speaks for; non-null
 * @param row the request half and the verdict; non-null
 * @param evidence the rule in plain words: how the model's thinking is switched, its template and the reason for any
 *     quirk; non-blank
 * @param examples ids that must land on this row, for the tests; never null
 */
public record BuiltInRow(IdentityPattern pattern, ProfileRow row, String evidence, List<String> examples) {

    private static final int SMALL_BATCH = 4;
    private static final int LARGE_BATCH = 8;

    public BuiltInRow {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(evidence, "evidence");
        examples = List.copyOf(Objects.requireNonNull(examples, "examples"));
        if (evidence.isBlank()) {
            throw new IllegalArgumentException("a row needs its evidence: " + row.name());
        }
    }

    /**
     * Starts a row from the default window and a native system role: batch 4 for a small tier and 8 for a large one.
     *
     * @param name the row's name; non-null
     * @param pattern the names it speaks for; non-null
     * @param reasoning how its models' thinking is switched; non-null
     * @param verdict how BookLoom rates the models; non-null
     * @param evidence the rule in plain words; non-blank
     * @param examples ids that land on the row; non-null
     * @return the row, to be adjusted with the {@code with…} methods
     */
    public static BuiltInRow of(
            final String name,
            final IdentityPattern pattern,
            final ReasoningControl reasoning,
            final String verdict,
            final String evidence,
            final String... examples) {
        final ModelProfile defaults = ModelProfile.defaults();
        final ModelProfile.Tier tier = pattern.isLarge() ? ModelProfile.Tier.LARGE : ModelProfile.Tier.SMALL;
        final ProfileRow base = new ProfileRow(
                name,
                reasoning,
                Set.of(),
                ModelProfile.SystemRole.NATIVE,
                Set.of(),
                defaults.context().hardCap(),
                defaults.context().outputMax(),
                0,
                tier == ModelProfile.Tier.LARGE ? LARGE_BATCH : SMALL_BATCH,
                tier,
                verdict);
        return new BuiltInRow(pattern, base, evidence, List.of(examples));
    }

    /** The system text is folded into the first user message. */
    public BuiltInRow folded() {
        return with(new ProfileRow(
                row.name(),
                row.reasoning(),
                row.markupFilters(),
                ModelProfile.SystemRole.FOLD,
                row.quirks(),
                row.hardCap(),
                row.outputMax(),
                row.templateOverhead(),
                row.batchSize(),
                row.tier(),
                row.verdict()));
    }

    /** Adds template quirks. */
    public BuiltInRow quirks(final ModelProfile.Quirk first, final ModelProfile.Quirk... more) {
        final Set<ModelProfile.Quirk> all = EnumSet.of(first, more);
        return with(new ProfileRow(
                row.name(),
                row.reasoning(),
                row.markupFilters(),
                row.systemRole(),
                all,
                row.hardCap(),
                row.outputMax(),
                row.templateOverhead(),
                row.batchSize(),
                row.tier(),
                row.verdict()));
    }

    /** Adds reply markup filters. */
    public BuiltInRow filters(final ModelProfile.MarkupFilter first, final ModelProfile.MarkupFilter... more) {
        final Set<ModelProfile.MarkupFilter> all = EnumSet.of(first, more);
        return with(new ProfileRow(
                row.name(),
                row.reasoning(),
                all,
                row.systemRole(),
                row.quirks(),
                row.hardCap(),
                row.outputMax(),
                row.templateOverhead(),
                row.batchSize(),
                row.tier(),
                row.verdict()));
    }

    /** Sets the most tokens the models handle well. */
    public BuiltInRow hardCap(final int tokens) {
        return with(new ProfileRow(
                row.name(),
                row.reasoning(),
                row.markupFilters(),
                row.systemRole(),
                row.quirks(),
                tokens,
                Math.min(row.outputMax(), tokens),
                row.templateOverhead(),
                row.batchSize(),
                row.tier(),
                row.verdict()));
    }

    /** Sets the tokens the chat template adds to every request. */
    public BuiltInRow templateOverhead(final int tokens) {
        return with(new ProfileRow(
                row.name(),
                row.reasoning(),
                row.markupFilters(),
                row.systemRole(),
                row.quirks(),
                row.hardCap(),
                row.outputMax(),
                tokens,
                row.batchSize(),
                row.tier(),
                row.verdict()));
    }

    /** Sets the batch and the size class, for a row whose size does not decide them. */
    public BuiltInRow batch(final int segments, final ModelProfile.Tier tier) {
        return with(new ProfileRow(
                row.name(),
                row.reasoning(),
                row.markupFilters(),
                row.systemRole(),
                row.quirks(),
                row.hardCap(),
                row.outputMax(),
                row.templateOverhead(),
                segments,
                tier,
                row.verdict()));
    }

    /** Sets the batch only. */
    public BuiltInRow batch(final int segments) {
        return batch(segments, row.tier());
    }

    private BuiltInRow with(final ProfileRow changed) {
        return new BuiltInRow(pattern, changed, evidence, examples);
    }
}
