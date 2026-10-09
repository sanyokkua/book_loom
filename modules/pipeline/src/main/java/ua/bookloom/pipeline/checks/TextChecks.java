package ua.bookloom.pipeline.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.prompt.LanguageRules;
import ua.bookloom.util.lang.Languages;

/**
 * The deterministic text checks that need no model, run on one segment's display texts — a defect found here is a
 * fact, not an opinion, so a blocking finding fails the segment before any reviewer is asked. Blocking: a word that
 * mixes alphabets, a quote pair the source balanced and the target did not, a paragraph left in the source language.
 * A glossary name swapped for another one and a run of source Latin words left in a target of another script block too. Soft: a doubled word, a spacing artefact, a changed number, a glossary name lost or spelled another way, a listed foreign word. The length check stays beside the other soft checks in
 * {@code qa}, where its band and its blend weight live.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TextChecks {

    /**
     * Runs every deterministic text check on one segment.
     *
     * @param source the source's display text
     * @param target the candidate's display text, with kept names and protected tokens already out
     * @param sourceLanguage the source language tag, or {@code null} when none is declared; the checks that need it
     *     are skipped
     * @param targetLanguage the target language tag
     * @return the findings, blocking ones first within each check's order; empty when the text is clean or has
     *     nothing but digits and locators (an ISBN or a web address is nobody's language); never null
     */
    public static List<CheckFinding> run(
            final String source,
            final String target,
            @Nullable final String sourceLanguage,
            final String targetLanguage) {
        return run(source, target, sourceLanguage, targetLanguage, List.of());
    }

    /**
     * Runs every deterministic text check on one segment, with the glossary pairs the vocative check holds the target to.
     *
     * @param source the source's display text
     * @param target the candidate's display text, with kept names and protected tokens already out
     * @param sourceLanguage the source language tag, or {@code null} when none is declared
     * @param targetLanguage the target language tag
     * @param glossaryPairs {@code term → target} lines of the glossary names the segment holds
     * @return the findings; empty when the text is clean; never null
     */
    public static List<CheckFinding> run(
            final String source,
            final String target,
            @Nullable final String sourceLanguage,
            final String targetLanguage,
            final List<String> glossaryPairs) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        Objects.requireNonNull(glossaryPairs, "glossaryPairs");
        if (NonProse.isLocatorOnly(target)) {
            log.debug("Text checks skipped: the target is only digits and locators");
            return List.of();
        }
        return runOn(source, target, sourceLanguage, targetLanguage, glossaryPairs);
    }

    // The identifiers are overwritten by filler first: no language or script check may hold them against the text.
    private static List<CheckFinding> runOn(
            final String original,
            final String originalTarget,
            @Nullable final String sourceLanguage,
            final String targetLanguage,
            final List<String> glossaryPairs) {
        final String source = Identifiers.masked(original);
        final String target = Identifiers.masked(originalTarget);
        final List<CheckFinding> findings = new ArrayList<>(IdentifierCheck.find(original, originalTarget));
        ResidueCheck.find(source, target, targetLanguage).ifPresent(findings::add);
        findings.addAll(ScriptPurityCheck.find(target, Languages.scriptOf(targetLanguage)));
        QuoteBalanceCheck.find(source, target, sourceLanguage, targetLanguage).ifPresent(findings::add);
        findings.addAll(LanguageIdentityCheck.find(target, sourceLanguage, targetLanguage));
        if (findings.stream().noneMatch(finding -> finding.kind() == FindingKind.LEFTOVER_LANGUAGE)) {
            findings.addAll(LatinRunCheck.find(source, target, Languages.scriptOf(targetLanguage)));
        }
        findings.addAll(ForeignWordCheck.find(source, target, targetLanguage));
        findings.addAll(DuplicateWordCheck.find(source, target));
        findings.addAll(SpacingCheck.find(source, target));
        SentenceCountCheck.find(source, target, sourceLanguage, targetLanguage).ifPresent(findings::add);
        findings.addAll(glossaryNameChecks(source, target, sourceLanguage, targetLanguage, glossaryPairs));
        findings.addAll(NumberCheck.find(source, target));
        if (!findings.isEmpty()) {
            report(findings);
        }
        return List.copyOf(findings);
    }

    // The vocative and the swap check hold the target to the glossary's renderings with the same stem rule.
    private static List<CheckFinding> glossaryNameChecks(
            final String source,
            final String target,
            @Nullable final String sourceLanguage,
            final String targetLanguage,
            final List<String> glossaryPairs) {
        final List<String> alternations = LanguageRules.bundled().stemAlternations(targetLanguage);
        final List<CheckFinding> found =
                new ArrayList<>(VocativeCheck.find(source, target, glossaryPairs, alternations, sourceLanguage));
        found.addAll(NameSwapCheck.find(source, target, glossaryPairs, alternations, sourceLanguage));
        found.addAll(NameLossCheck.find(source, target, glossaryPairs, sourceLanguage, targetLanguage));
        return found;
    }

    /**
     * How many words a display text writes twice in a row.
     *
     * @param display the display text; never null
     * @return the number of doubled words, zero when there is none
     */
    public static int doubledWords(final String display) {
        return DuplicateWordCheck.find("", display).size();
    }

    private static void report(final List<CheckFinding> findings) {
        log.debug("Text checks found {} finding(s)", findings.size());
        for (final CheckFinding finding : findings) {
            if (finding.blocking()) {
                log.warn(
                        "Text check {} blocks acceptance at {}..{}",
                        finding.kind(),
                        finding.span().start(),
                        finding.span().end());
            }
            log.trace("Text check {} span: {}", finding.kind(), finding.span().text());
        }
    }
}
