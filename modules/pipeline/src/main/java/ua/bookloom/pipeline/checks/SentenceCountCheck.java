package ua.bookloom.pipeline.checks;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.prompt.LanguageRules;
import ua.bookloom.util.lang.Languages;
import ua.bookloom.util.lang.Script;

/**
 * Whether the target kept every sentence of the source. A paragraph that loses its last sentence keeps a normal length
 * ratio and word count, so only counting sentences sees it: the target must have at least as many sentences as the
 * source has significant ones (three words or more), because a translator may join a one-word exclamation to its
 * neighbour but does not drop a statement. A speech closed by a quote and a dash attribution is a sentence end in the
 * target even without the source's full stop, when the language file's dialogue convention says so (15j.J9).
 * Blocking, so the segment is repaired before any reviewer reads it.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SentenceCountCheck {

    private static final Set<Script> COUNTED = Set.of(Script.LATIN, Script.CYRILLIC, Script.GREEK);
    private static final int MIN_SOURCE_SENTENCES = 2;
    private static final int SPAN_CHARS = 40;
    private static final Pattern SOURCE_QUOTE_STOP = Pattern.compile("[.!?…]+[”\"»](?=\\s|$)");
    private static final Pattern UNSPACED_SCRIPT =
            Pattern.compile("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}\\p{IsThai}\\p{IsLao}\\p{IsKhmer}]");

    // Only the alphabets whose sentences end in . ! ? are counted: a text in another script ends its sentences with
    // marks
    // this check does not know, and its sentences would all read as one.
    private static boolean isCounted(@Nullable final String sourceLanguage, final String targetLanguage) {
        final Optional<Script> target = Languages.scriptOf(targetLanguage);
        final Optional<Script> source = sourceLanguage == null ? Optional.empty() : Languages.scriptOf(sourceLanguage);
        return target.filter(COUNTED::contains).isPresent()
                && source.map(COUNTED::contains).orElse(true);
    }

    // A quote the source closed with a mark and the target closed with a comma or nothing before a dash attribution
    // lost that mark to the target's dialogue convention: each such quote still ends a sentence. The boundaries are
    // counted on each side, not paired, and never exceed the number of quotes the source closed with a mark.
    private static int dialogueBoundaries(final String source, final String target, final String targetLanguage) {
        final Optional<Pattern> convention = LanguageRules.bundled().pattern(targetLanguage, "dialogueAttribution");
        if (convention.isEmpty()) {
            return 0;
        }
        final int inTarget = (int) convention.get().matcher(target).results().count();
        final int inSource = (int) SOURCE_QUOTE_STOP.matcher(source).results().count();
        final int boundaries = Math.min(inTarget, inSource);
        log.debug("Dialogue attribution boundaries target={} source={} counted={}", inTarget, inSource, boundaries);
        return boundaries;
    }

    static Optional<CheckFinding> find(
            final String source,
            final String target,
            @Nullable final String sourceLanguage,
            final String targetLanguage) {
        if (!isCounted(sourceLanguage, targetLanguage)
                || UNSPACED_SCRIPT.matcher(source).find()
                || UNSPACED_SCRIPT.matcher(target).find()) {
            return Optional.empty();
        }
        final List<String> significant = Sentences.significant(source);
        if (significant.size() < MIN_SOURCE_SENTENCES) {
            return Optional.empty();
        }
        final int kept = Sentences.countLenient(target) + dialogueBoundaries(source, target, targetLanguage);
        log.debug("Sentence count source={} target={}", significant.size(), kept);
        if (kept >= significant.size()) {
            return Optional.empty();
        }
        final String tail = significant.get(significant.size() - 1);
        final int end = target.length();
        final int start = Math.max(0, end - Math.min(end, SPAN_CHARS));
        return Optional.of(new CheckFinding(
                FindingKind.SENTENCE_MISSING,
                new TextSpan(start, end, target.substring(start)),
                "The source has " + significant.size() + " sentences and the translation only " + kept
                        + ": a sentence is missing, probably the last one (\"" + tail + "\"). Translate every"
                        + " sentence of the source.",
                true));
    }
}
