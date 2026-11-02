package ua.bookloom.llm.profile;

import java.util.Optional;
import ua.bookloom.util.text.LineBreaks;

/**
 * A model file as a download cache names it, {@code owner_repo-GGUF_file}: the owner, the repository that ends in
 * {@code -GGUF}, and the file. Reading by hand stands in for the regular expression
 * {@code ([A-Za-z0-9.-]++)_([A-Za-z0-9._-]*?-GGUF)_(.++)}, which tries the repository end at every position.
 *
 * @param repo the owner and the repository, joined by a slash
 * @param file what follows the repository
 */
record CacheFileName(String repo, String file) {

    private static final String REPO_END = "-GGUF";

    /**
     * Reads a name.
     *
     * @param name the name without its extension; never null
     * @return the parts, or empty when the name has another shape
     */
    static Optional<CacheFileName> parse(final String name) {
        int ownerEnd = 0;
        while (ownerEnd < name.length() && isOwnerChar(name.charAt(ownerEnd))) {
            ownerEnd++;
        }
        if (ownerEnd == 0 || ownerEnd >= name.length() || name.charAt(ownerEnd) != '_') {
            return Optional.empty();
        }
        final int lastBreak = lastLineBreak(name);
        for (int at = ownerEnd + 1; at < name.length() && isRepoChar(name.charAt(at)); at++) {
            if (name.regionMatches(true, at, REPO_END, 0, REPO_END.length())) {
                final Optional<CacheFileName> parsed = fileAfter(name, ownerEnd, at + REPO_END.length(), lastBreak);
                if (parsed.isPresent()) {
                    return parsed;
                }
            }
        }
        return Optional.empty();
    }

    // The file after "_" at repoEnd, which must be non-empty and hold no line break.
    private static Optional<CacheFileName> fileAfter(
            final String name, final int ownerEnd, final int repoEnd, final int lastBreak) {
        if (repoEnd >= name.length()
                || name.charAt(repoEnd) != '_'
                || repoEnd + 1 >= name.length()
                || lastBreak > repoEnd) {
            return Optional.empty();
        }
        return Optional.of(new CacheFileName(
                name.substring(0, ownerEnd) + "/" + name.substring(ownerEnd + 1, repoEnd),
                name.substring(repoEnd + 1)));
    }

    private static int lastLineBreak(final String name) {
        int at = name.length() - 1;
        while (at >= 0 && !LineBreaks.isTerminator(name.charAt(at))) {
            at--;
        }
        return at;
    }

    private static boolean isOwnerChar(final char c) {
        return isAlphanumeric(c) || c == '.' || c == '-';
    }

    private static boolean isRepoChar(final char c) {
        return isOwnerChar(c) || c == '_';
    }

    private static boolean isAlphanumeric(final char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }
}
