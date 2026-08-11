package ua.bookloom.document.epub;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Resolves an OPF manifest {@code href} against the OPF's own location within the archive. Zip entry names always
 * use {@code /} regardless of host OS, so this works in plain strings rather than {@link java.nio.file.Path},
 * which would apply the platform separator.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OpfPaths {

    /**
     * Returns the directory a {@code href} relative to the OPF must be resolved against.
     *
     * @param opfPath the OPF's own path within the archive
     * @return {@code opfPath}'s directory, including the trailing {@code /}; empty if the OPF is at the archive
     *     root
     */
    static String parentOf(String opfPath) {
        final int slash = opfPath.lastIndexOf('/');
        return slash < 0 ? "" : opfPath.substring(0, slash + 1);
    }

    /**
     * Resolves a manifest item's {@code href} to its archive-absolute entry name.
     *
     * @param opfDir the OPF's directory, as returned by {@link #parentOf}
     * @param href a manifest item's {@code href}, possibly carrying a {@code #fragment}
     * @return {@code href} resolved to an archive-absolute entry name
     */
    static String resolve(String opfDir, String href) {
        final String withoutFragment = stripFragment(href);
        return withoutFragment.startsWith("/") ? withoutFragment.substring(1) : opfDir + withoutFragment;
    }

    private static String stripFragment(String href) {
        final int hash = href.indexOf('#');
        return hash < 0 ? href : href.substring(0, hash);
    }
}
