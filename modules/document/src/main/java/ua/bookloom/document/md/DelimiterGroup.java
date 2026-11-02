package ua.bookloom.document.md;

import java.util.Arrays;
import java.util.Objects;

/**
 * One actionable construct: either the delimiter position(s) {@code MarkdownEscaper.applyGroup} would insert a backslash
 * before ({@code deletion == false}), or the character position(s) it would delete instead ({@code deletion ==
 * true} — the one neutralisation a hard line break needs, since no backslash spelling removes one). The
 * construct's simple class name — the same name {@code MarkdownStructureCheck.constructMultiset} counts — is
 * carried alongside so a group can be checked against the source's own count of that construct type before it
 * is acted on.
 *
 * @param constructName the node's simple class name (e.g. {@code "Emphasis"}, {@code "ListItem"}); never null
 * @param positions the position(s) within the candidate text this group would act on; never null
 * @param deletion {@code true} if {@code positions} are deleted, {@code false} if a backslash is inserted
 *     before each
 */
@SuppressWarnings(
        "ArrayRecordComponent") // read through the accessor only; equality reads the content, toString the length.
record DelimiterGroup(String constructName, int[] positions, boolean deletion) {
    @Override
    public boolean equals(final Object other) {
        return other instanceof DelimiterGroup(var oConstructName, var oPositions, var oDeletion)
                && Objects.equals(constructName, oConstructName)
                && Arrays.equals(positions, oPositions)
                && deletion == oDeletion;
    }

    @Override
    public int hashCode() {
        return Objects.hash(constructName, Arrays.hashCode(positions), deletion);
    }

    @Override
    public String toString() {
        return "DelimiterGroup[constructName=" + constructName + ", positions=" + positions.length + ", deletion="
                + deletion + "]";
    }
}
