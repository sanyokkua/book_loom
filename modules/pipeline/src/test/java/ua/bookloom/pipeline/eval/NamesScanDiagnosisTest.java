package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.pipeline.SequenceJobs;
import ua.bookloom.pipeline.SequenceJobs.Names;
import ua.bookloom.pipeline.SequenceJobs.Prepared;
import ua.bookloom.pipeline.glossary.FrequencyScan;
import ua.bookloom.pipeline.glossary.NameCandidate;
import ua.bookloom.pipeline.glossary.StopWords;

/**
 * Diagnosis for 15i.N8, local-only: runs the deterministic glossary scan over each owner book of
 * {@code BOOKLOOM_CORPUS_DIR} (no model) and prints, for every gold character or keep name the scan leaves out, how the
 * book writes it and the step that dropped it. Writes {@code build/n8-diagnosis.txt} in the module; asserts only
 * that it ran.
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_CORPUS_DIR", matches = ".+")
class NamesScanDiagnosisTest {

    private static final int PROPOSAL_MIN = 3;

    @Test
    void scan_ownerBooks_reportsWhyEachGoldNameIsLeftOut() {
        final Path corpus = Path.of(System.getenv("BOOKLOOM_CORPUS_DIR"));
        final StringBuilder out = new StringBuilder();
        final List<Path> files = JudgementSuites.goldFiles(corpus);
        files.forEach(file -> diagnose(file, out));
        write(out);
        assertThat(files).isNotNull();
    }

    private static void write(final StringBuilder out) {
        try {
            final Path target = Path.of("build", "n8-diagnosis.txt");
            java.nio.file.Files.createDirectories(target.getParent());
            java.nio.file.Files.writeString(target, out);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static void diagnose(final Path file, final StringBuilder out) {
        final JudgementGold gold = JudgementGold.read(file);
        final Path book = file.getParent().resolve(gold.book());
        final Prepared prepared = SequenceJobs.importBook(
                book,
                SequenceJobs.brief(
                        gold.sourceLanguage(), gold.targetLanguage(), QualityDial.BALANCED, Narrator.unspecified()));
        final Names names = SequenceJobs.names(prepared);
        final String id = prepared.projectId();
        names.glossary().scan(id);
        final List<GlossaryEntry> glossary = names.glossary().entries(id).data();
        final Set<String> held = new HashSet<>();
        glossary.forEach(entry -> entry.names().forEach(name -> held.add(lower(name))));
        final List<NameCandidate> all =
                FrequencyScan.candidates(prepared.index(), prepared.index().story(), 1, gold.sourceLanguage());
        final String text = String.join(
                "\n", prepared.story().stream().map(s -> s.sourceInner()).toList());
        out.append("== ")
                .append(file.getFileName())
                .append(" glossary after scan: ")
                .append(glossary.size())
                .append('\n');
        Stream.concat(gold.characters().stream().map(JudgementGold.Person::term), gold.keep().stream())
                .distinct()
                .filter(name -> !held.contains(lower(name)))
                .forEach(name -> out.append(describe(name, all, text, gold.sourceLanguage()))
                        .append('\n'));
    }

    private static String describe(
            final String name, final List<NameCandidate> all, final String text, final String language) {
        final Optional<NameCandidate> exact =
                all.stream().filter(c -> lower(c.term()).equals(lower(name))).findFirst();
        final String head = name.substring(0, name.contains(" ") ? name.indexOf(' ') : name.length())
                .toLowerCase(Locale.ROOT);
        final long written = count(text, name, false);
        final long capitalised = count(text, name, true);
        final String reason;
        if (exact.isPresent()) {
            reason = exact.get().count() < PROPOSAL_MIN
                    ? "counted " + exact.get().count() + " (< " + PROPOSAL_MIN + "), dropped by the count floor"
                    : "proposed with count " + exact.get().count() + " (dropped later, not by the scan)";
        } else if (StopWords.of(language).contains(head) && name.contains(" ")) {
            reason = "stop-word head '" + head + "' never starts a name";
        } else if (name.matches(".*\\s[a-z].*")
                || name.contains(" and ")
                || name.contains("’s")
                || name.contains("'s")) {
            reason = "name holds a lower-case word, a joiner or a possessive";
        } else {
            final Optional<NameCandidate> inside = all.stream()
                    .filter(c -> c.term().contains(name) && !c.term().equals(name))
                    .findFirst();
            reason = inside.isPresent()
                    ? "dropped as a part of '" + inside.get().term() + "' (x"
                            + inside.get().count() + ")"
                    : "no candidate at all (alone, stop-word, lower-case share, junk or hygiene)";
        }
        return "  LOST " + name + ": written " + written + "x, capitalised " + capitalised + "x; " + reason;
    }

    private static long count(final String text, final String name, final boolean capital) {
        final Pattern pattern = Pattern.compile(
                "(?<![\\p{L}])" + Pattern.quote(name) + "(?![\\p{L}])", capital ? 0 : Pattern.CASE_INSENSITIVE);
        return pattern.matcher(text).results().count();
    }

    private static String lower(final String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
