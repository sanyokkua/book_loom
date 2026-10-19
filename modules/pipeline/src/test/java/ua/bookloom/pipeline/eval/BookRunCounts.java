package ua.bookloom.pipeline.eval;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.pipeline.ConsistencySummary;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.GenderWait;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.checks.Identifiers;
import ua.bookloom.pipeline.eval.BookRunSummary.Characters;
import ua.bookloom.pipeline.eval.BookRunSummary.Written;
import ua.bookloom.pipeline.prompt.LanguageRules;

/**
 * The deterministic counts of a whole-book run, taken from the exported file read back against its source with the
 * production detectors ({@link RecallDetectors}) and from the stored glossary, so no model has a say in them.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BookRunCounts {

    // Targets written in letters other than Latin, where every word of Latin letters is one the model left behind.
    private static final Set<String> NON_LATIN_TARGETS =
            Set.of("uk", "ru", "be", "bg", "sr", "mk", "el", "ka", "hy", "he", "ar", "fa");
    private static final Pattern TOKEN = Pattern.compile("⟦[^⟧]*⟧");
    private static final Pattern LATIN_WORD = Pattern.compile("\\p{IsLatin}+(?:['’-]\\p{IsLatin}+)*");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}]+(?:['’ʼ-][\\p{L}\\p{M}]+)*");

    /** Reads one export back and counts what is left in it. */
    static Written written(
            final DocumentPort documents,
            final Path source,
            final Path exported,
            final ExportReport report,
            final String sourceLanguage,
            final String targetLanguage,
            final Narrator narrator) {
        Objects.requireNonNull(report, "report");
        log.debug("Counting {} against {}", exported, source);
        final Path csv = report.sideFiles().stream()
                .filter(file -> file.getFileName().toString().endsWith(".glossary.csv"))
                .findFirst()
                .orElse(null);
        final RecallBook book = new RecallBook(
                exported.getFileName().toString(),
                exported.getParent(),
                source,
                exported,
                csv,
                sourceLanguage,
                targetLanguage,
                narrator);
        final RecallBookRun run = RecallDetectors.run(book, documents);
        final Written counts = tally(run, exported, report, targetLanguage);
        log.info("Counted {}: {}", exported.getFileName(), counts);
        return counts;
    }

    private static Written tally(
            final RecallBookRun run, final Path exported, final ExportReport report, final String targetLanguage) {
        final String forbidden = LanguageRules.bundled().forbiddenLetters(targetLanguage);
        int latin = 0;
        int alphabet = 0;
        final Map<String, Integer> detectors = new TreeMap<>();
        for (final RecallSegment segment : run.segments()) {
            final String text =
                    Identifiers.masked(TOKEN.matcher(segment.target()).replaceAll(""));
            latin += isLatinTarget(targetLanguage) ? 0 : count(LATIN_WORD, text, "");
            alphabet += forbidden.isEmpty() ? 0 : count(WORD, text, forbidden);
            segment.fired().forEach(name -> detectors.merge(name, 1, Integer::sum));
        }
        return new Written(
                exported.getFileName().toString(),
                run.segments().size(),
                run.unaligned(),
                latin,
                alphabet,
                report.flaggedWritten(),
                awaiting(report.consistency()),
                detectors);
    }

    /** The glossary's characters by how their gender is known. */
    static Characters characters(final List<GlossaryEntry> glossary) {
        int verified = 0;
        int suggested = 0;
        int none = 0;
        for (final GlossaryEntry entry : glossary) {
            if (entry.type() != TermType.CHARACTER) {
                continue;
            }
            if (entry.hasConfirmedGender()) {
                verified++;
            } else if (entry.gender() == Gender.UNKNOWN) {
                none++;
            } else {
                suggested++;
            }
        }
        return new Characters(verified, suggested, none);
    }

    private static boolean isLatinTarget(final String targetLanguage) {
        final String primary = targetLanguage.toLowerCase(Locale.ROOT).split("[-_]", 2)[0];
        return !NON_LATIN_TARGETS.contains(primary);
    }

    private static int awaiting(final ConsistencySummary summary) {
        return summary.awaitingGender().stream().mapToInt(GenderWait::segments).sum();
    }

    // Words matching the pattern that hold one of the given letters; every match counts when none are given.
    private static int count(final Pattern pattern, final String text, final String letters) {
        int words = 0;
        final Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            final String word = matcher.group().toLowerCase(Locale.ROOT);
            if (letters.isEmpty() || word.codePoints().anyMatch(point -> letters.indexOf(point) >= 0)) {
                words++;
            }
        }
        return words;
    }
}
