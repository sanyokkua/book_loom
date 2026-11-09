package ua.bookloom.pipeline.qa;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;

/**
 * The one reader of a finding's level (design D12): the run, the audit, the export and the desk all ask it, so "what
 * blocks" is the same everywhere. A level is derived whenever a finding is read and never stored; the stored finding
 * keeps only its check's wire name and its severity.
 *
 * <p>A high finding has its check's {@link CheckName#level()}. A medium finding is a repair, except that a soft check
 * with a confidence weight raises a failure outright at medium ({@link CheckResult#fail}), and that failure blocks
 * acceptance ("Block acceptance when a soft check fails outright"): weight above zero and level BLOCK read as BLOCK.
 * A low finding is a note. A finding no check raised (the an applied edit, the audit's prefixed copies, a
 * stored reply flag) is a note: it never decides acceptance.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CheckLevels {

    private static final Map<String, CheckName> BY_RAISER = Stream.of(CheckName.values())
            .collect(Collectors.toUnmodifiableMap(CheckName::raisedBy, Function.identity()));

    /**
     * The level of one finding.
     *
     * @param finding the non-null finding, stored or fresh
     * @return the finding's level; {@link CheckLevel#NOTE} for a finding no check raised
     */
    public static CheckLevel of(final QaFinding finding) {
        Objects.requireNonNull(finding, "finding");
        final CheckName check = BY_RAISER.get(finding.raisedBy());
        if (check == null) {
            return CheckLevel.NOTE;
        }
        return switch (finding.severity()) {
            case HIGH -> check.level();
            case MEDIUM -> isOutrightSoftFailure(check) ? CheckLevel.BLOCK : CheckLevel.REPAIR;
            case LOW -> CheckLevel.NOTE;
        };
    }

    /**
     * The severity a text check raises a finding at so that {@link #of} reads it back at {@code level}.
     *
     * @param level the non-null level the finding should carry
     * @return high for a block, medium for a repair, low for a note
     */
    public static Severity severityFor(final CheckLevel level) {
        Objects.requireNonNull(level, "level");
        return switch (level) {
            case BLOCK -> Severity.HIGH;
            case REPAIR -> Severity.MEDIUM;
            case NOTE -> Severity.LOW;
        };
    }

    private static boolean isOutrightSoftFailure(final CheckName check) {
        return check.weight() > 0.0 && check.level() == CheckLevel.BLOCK;
    }
}
