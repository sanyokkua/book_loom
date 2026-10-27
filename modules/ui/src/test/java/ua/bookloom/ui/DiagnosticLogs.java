package ua.bookloom.ui;

import java.nio.file.Path;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Builds the {@link DiagnosticLog} values the tests need. */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DiagnosticLogs {

    /** A log in {@code directory} with no marker of its own and no launch value. */
    public static DiagnosticLog of(final Path directory, final boolean detailed) {
        return new DiagnosticLog(directory, detailed, directory.resolveSibling("detailed-log.on"), null, false);
    }
}
