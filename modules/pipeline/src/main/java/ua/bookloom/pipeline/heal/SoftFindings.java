package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.qa.CheckName;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * Which soft findings a directed fix can mend: a narrator's word of the wrong gender, a woman's name written with a
 * man's verb or declension, a word with a letter outside the target alphabet, a number the translation changed. Such
 * a finding never refuses a segment, it earns one fix call ({@link SoftFix}).
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SoftFindings {

    private static final Set<String> FIXABLE = Set.of(
            CheckName.GENDER.raisedBy(),
            CheckName.NAME_GENDER.raisedBy(),
            CheckName.ALPHABET.raisedBy(),
            CheckName.NUMBER.raisedBy());

    /**
     * Whether a directed fix can mend a finding.
     *
     * @param finding the non-null finding
     * @return {@code true} if the finding is one of the fixable soft kinds, {@code false} otherwise
     */
    public static boolean isFixable(final QaFinding finding) {
        Objects.requireNonNull(finding, "finding");
        return FIXABLE.contains(finding.raisedBy());
    }

    /**
     * The findings of an evaluation that a directed fix can mend.
     *
     * @param qa the non-null evaluation
     * @return the fixable findings in order; never null, empty when there is none
     */
    public static List<QaFinding> fixableIn(final QaResult qa) {
        Objects.requireNonNull(qa, "qa");
        return qa.findings().stream().filter(SoftFindings::isFixable).toList();
    }
}
