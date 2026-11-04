package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.qa.CheckLevel;
import ua.bookloom.pipeline.qa.CheckLevels;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * Which findings a directed fix can mend: those at {@link CheckLevel#REPAIR} level ({@link CheckLevels#of}) — a
 * narrator's word of the wrong gender, a woman's name written with a man's verb or declension, a number the
 * translation changed, a glossary name the translation lost or spelled another way, a word of another language the
 * language file replaces, one speech that mixes the informal and the formal "you". Such a finding never refuses a
 * segment, it earns one fix call ({@link SoftFix}).
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SoftFindings {

    /**
     * Whether a directed fix can mend a finding.
     *
     * @param finding the non-null finding
     * @return {@code true} if the finding's level is {@link CheckLevel#REPAIR}, {@code false} otherwise
     */
    public static boolean isFixable(final QaFinding finding) {
        Objects.requireNonNull(finding, "finding");
        return CheckLevels.of(finding) == CheckLevel.REPAIR;
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
